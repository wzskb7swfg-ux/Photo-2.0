package de.barkau.fotomail.wound

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import de.barkau.fotomail.MailSender
import de.barkau.fotomail.Notifications
import de.barkau.fotomail.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit
import javax.mail.AuthenticationFailedException

/**
 * Postausgang für Wundfotos im geschützten App-Speicher.
 * Jede Aufnahme ist ein eigener Ordner mit Bildern, Betreff und Text.
 * Nach erfolgreichem Versand wird der Ordner gelöscht – auf dem Handy bleibt nichts zurück
 * (außer dem verkleinerten Vergleichsbild, falls eingeschaltet).
 */
object WoundOutbox {

    private const val WORK = "fotomail_wound_send"
    private const val PREFIX_READY = "item_"
    private const val PREFIX_TEMP = "tmp_"

    private fun dir(ctx: Context): File = File(ctx.filesDir, "postausgang").apply { mkdirs() }

    /** Neuer, noch unfertiger Eintrag. Erst [commit] macht ihn versandbereit. */
    fun begin(ctx: Context): File =
        File(dir(ctx), PREFIX_TEMP + System.currentTimeMillis()).apply { mkdirs() }

    fun commit(ctx: Context, temp: File, subject: String, body: String) {
        File(temp, "betreff.txt").writeText(subject)
        File(temp, "text.txt").writeText(body)
        val ready = File(dir(ctx), temp.name.replaceFirst(PREFIX_TEMP, PREFIX_READY))
        if (!temp.renameTo(ready)) throw java.io.IOException("Postausgang nicht beschreibbar")
        schedule(ctx)
    }

    fun pending(ctx: Context): List<File> =
        dir(ctx).listFiles()?.filter { it.isDirectory && it.name.startsWith(PREFIX_READY) }
            ?.sortedBy { it.name } ?: emptyList()

    /** Reste abgebrochener Aufnahmen entfernen. */
    fun cleanupTemp(ctx: Context) {
        dir(ctx).listFiles()?.filter { it.name.startsWith(PREFIX_TEMP) }?.forEach { it.deleteRecursively() }
    }

    fun schedule(ctx: Context) {
        val req = OneTimeWorkRequestBuilder<WoundSendWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(WORK, ExistingWorkPolicy.APPEND_OR_REPLACE, req)
    }
}

/** Verschickt alle Wundfotos aus dem Postausgang, jeweils als eigene E-Mail. */
class WoundSendWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val prefs = Prefs(ctx)
        if (!prefs.isConfigured) {
            Notifications.problem(ctx, "Wundfotos warten im Postausgang: E-Mail-Einstellungen unvollständig.")
            return@withContext Result.failure()
        }
        val items = WoundOutbox.pending(ctx)
        for (item in items) {
            val files = item.listFiles()?.filter { it.name.endsWith(".jpg") }?.sortedBy { it.name } ?: emptyList()
            val subject = File(item, "betreff.txt").takeIf { it.exists() }?.readText() ?: "Wundfoto"
            val body = File(item, "text.txt").takeIf { it.exists() }?.readText() ?: ""
            if (files.isEmpty()) {
                item.deleteRecursively()
                continue
            }
            try {
                MailSender.send(prefs, subject, body, files)
                item.deleteRecursively()
            } catch (e: AuthenticationFailedException) {
                Notifications.problem(ctx, "Wundfoto nicht gesendet: Anmeldung beim Mailserver abgelehnt – Passwort prüfen.")
                return@withContext Result.failure()
            } catch (e: Exception) {
                return@withContext if (runAttemptCount < 10) Result.retry() else {
                    Notifications.problem(
                        ctx, "Wundfoto nicht gesendet (${e.message ?: e.javaClass.simpleName}). " +
                                "Es bleibt im Postausgang – in der App „Erneut senden“ antippen."
                    )
                    Result.failure()
                }
            }
        }
        Result.success()
    }
}
