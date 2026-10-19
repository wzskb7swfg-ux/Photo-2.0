package de.barkau.fotomail

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.mail.AuthenticationFailedException

object SendScheduler {

    private const val DEBOUNCE = "fotomail_debounce"
    private const val SEND = "fotomail_send"

    /**
     * Startet die Wartezeit neu. Jedes weitere Foto verschiebt den Versand,
     * erst nach [minutes] Minuten Ruhe wird gesendet.
     */
    fun debounce(context: Context, minutes: Int) {
        val req = OneTimeWorkRequestBuilder<DebounceWorker>()
            .setInitialDelay(minutes.toLong(), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(DEBOUNCE, ExistingWorkPolicy.REPLACE, req)
    }

    /** Versand sofort anstoßen (wartet automatisch, bis Internet da ist). */
    fun sendNow(context: Context) {
        val req = OneTimeWorkRequestBuilder<SendWorker>()
            .setConstraints(
                Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(SEND, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }

    fun cancelPending(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(DEBOUNCE)
    }
}

/** Läuft nach der Ruhezeit und reiht nur den eigentlichen Versand ein. */
class DebounceWorker(ctx: Context, params: WorkerParameters) : Worker(ctx, params) {
    override fun doWork(): Result {
        SendScheduler.sendNow(applicationContext)
        return Result.success()
    }
}

/** Sammelt alle neuen Kamerafotos, entfernt Metadaten und verschickt sie. */
class SendWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.GERMANY)
    private val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val prefs = Prefs(ctx)

        // Ausgeschaltet = nichts versenden, auch keine noch wartenden Aufträge.
        if (!prefs.enabled) return@withContext Result.success()
        if (!prefs.isConfigured) {
            prefs.lastStatus = "Nicht gesendet: E-Mail-Einstellungen unvollständig."
            return@withContext Result.failure()
        }
        if (!PhotoRepository.hasPermission(ctx)) {
            prefs.lastStatus = "Nicht gesendet: Zugriff auf Fotos fehlt."
            Notifications.problem(ctx, "Der Zugriff auf Fotos wurde entzogen. Bitte App öffnen.")
            return@withContext Result.failure()
        }
        if (prefs.lastSentId < 0) {
            prefs.lastSentId = PhotoRepository.maxId(ctx)
            return@withContext Result.success()
        }

        val photos = PhotoRepository.newPhotos(ctx, prefs.lastSentId, MAX_PER_RUN)
        if (photos.isEmpty()) return@withContext Result.success()

        val outDir = File(ctx.cacheDir, "outgoing")
        outDir.deleteRecursively()
        outDir.mkdirs()

        try {
            val maxEdge = if (prefs.highRes) 4000 else 2560
            val ready = mutableListOf<Pair<Photo, File>>()
            val failed = mutableListOf<Photo>()
            for (p in photos) {
                try {
                    ready += p to ImageSanitizer.toCleanJpeg(ctx, p, outDir, maxEdge)
                } catch (e: Exception) {
                    failed += p
                } catch (e: OutOfMemoryError) {
                    failed += p
                }
            }

            // Ein Foto ist evtl. noch nicht fertig gespeichert – lieber später nochmal.
            if (failed.isNotEmpty() && runAttemptCount < 3) {
                return@withContext Result.retry()
            }
            if (ready.isEmpty()) {
                prefs.lastSentId = photos.maxOf { it.id }
                prefs.lastStatus = "Fehler: ${failed.size} Foto(s) nicht lesbar, übersprungen."
                Notifications.problem(ctx, prefs.lastStatus)
                return@withContext Result.success()
            }

            val chunks = chunkBySize(ready, MAX_MAIL_BYTES)
            chunks.forEachIndexed { index, chunk ->
                val first = chunk.minOf { it.first.takenMs }
                val last = chunk.maxOf { it.first.takenMs }
                var subject = "Fotos ${dateFormat.format(Date(first))}, " +
                        "${timeFormat.format(Date(first))}–${timeFormat.format(Date(last))} " +
                        "(${chunk.size} ${if (chunk.size == 1) "Bild" else "Bilder"})"
                if (chunks.size > 1) subject += " – Teil ${index + 1}/${chunks.size}"

                val body = buildString {
                    appendLine("Automatisch gesendet von FotoMail.")
                    appendLine()
                    chunk.forEach { (p, f) ->
                        appendLine("• ${f.name}  (aufgenommen ${timeFormat.format(Date(p.takenMs))} Uhr)")
                    }
                    appendLine()
                    appendLine("Versteckte Bilddaten (Standort, Gerät) wurden vor dem Versand entfernt.")
                    if (failed.isNotEmpty() && index == chunks.lastIndex) {
                        appendLine()
                        appendLine("Nicht lesbar und übersprungen: ${failed.joinToString { it.name }}")
                    }
                }

                MailSender.send(prefs, subject, body, chunk.map { it.second })
                prefs.lastSentId = chunk.maxOf { it.first.id }
            }
            prefs.lastSentId = maxOf(prefs.lastSentId, photos.maxOf { it.id })

            val count = ready.size
            val now = timeFormat.format(Date())
            prefs.lastStatus = "Zuletzt gesendet: $count ${if (count == 1) "Foto" else "Fotos"} um $now Uhr"
            Notifications.updateWatching(ctx, prefs.lastStatus)

            // Mehr als in einen Durchgang passt? Gleich weitermachen.
            if (photos.size >= MAX_PER_RUN) SendScheduler.sendNow(ctx)
            Result.success()
        } catch (e: AuthenticationFailedException) {
            prefs.lastStatus = "Anmeldung beim Mailserver abgelehnt – App-Passwort prüfen."
            Notifications.problem(ctx, prefs.lastStatus)
            Result.failure()
        } catch (e: Exception) {
            if (runAttemptCount < 8) {
                prefs.lastStatus = "Versand wird wiederholt (${e.javaClass.simpleName})"
                Result.retry()
            } else {
                prefs.lastStatus = "Versand fehlgeschlagen: ${e.message ?: e.javaClass.simpleName}"
                Notifications.problem(ctx, prefs.lastStatus + " – „Jetzt senden“ in der App versucht es erneut.")
                Result.failure()
            }
        } finally {
            outDir.deleteRecursively()
        }
    }

    private fun chunkBySize(
        items: List<Pair<Photo, File>>, maxBytes: Long
    ): List<List<Pair<Photo, File>>> {
        val result = mutableListOf<MutableList<Pair<Photo, File>>>()
        var current = mutableListOf<Pair<Photo, File>>()
        var size = 0L
        for (item in items) {
            val len = item.second.length()
            if (current.isNotEmpty() && size + len > maxBytes) {
                result += current
                current = mutableListOf()
                size = 0L
            }
            current += item
            size += len
        }
        if (current.isNotEmpty()) result += current
        return result
    }

    companion object {
        private const val MAX_PER_RUN = 60
        // Gmail erlaubt 25 MB pro Mail inkl. Kodierung (+33 %) – 15 MB Nutzlast ist sicher.
        private const val MAX_MAIL_BYTES = 15L * 1024 * 1024
    }
}
