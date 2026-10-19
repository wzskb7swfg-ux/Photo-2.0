package de.barkau.fotomail

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.MediaStore
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

/**
 * Hält die Überwachung am Laufen (sichtbar als Daueranzeige).
 * Sobald ein neues Foto auftaucht, wird die Wartezeit (neu) gestartet.
 */
class PhotoWatchService : Service() {

    private val handler = Handler(Looper.getMainLooper())
    private var observer: ContentObserver? = null
    private val check = Runnable { onMediaChanged() }

    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannels(this)
        val type = if (Build.VERSION.SDK_INT >= 34)
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        try {
            ServiceCompat.startForeground(
                this, Notifications.WATCH_ID, Notifications.watching(this, null), type
            )
        } catch (ignored: Exception) {
            // Android verweigert den Start gerade (z. B. Neustart aus dem Hintergrund).
            stopSelf()
            return
        }
        running = true

        if (!PhotoRepository.hasPermission(this)) {
            stopSelf()
            return
        }

        val prefs = Prefs(this)
        if (prefs.lastSentId < 0) prefs.lastSentId = PhotoRepository.maxId(this)

        val obs = object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                // Viele Änderungsmeldungen pro Foto – kurz sammeln, dann einmal prüfen.
                handler.removeCallbacks(check)
                handler.postDelayed(check, 1500)
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, obs
        )
        observer = obs
        onMediaChanged() // Nachholen, falls Fotos entstanden sind, während die App nicht lief
    }

    private fun onMediaChanged() {
        val prefs = Prefs(this)
        val count = try {
            PhotoRepository.countNew(this, prefs.lastSentId)
        } catch (ignored: Exception) {
            0 // z. B. Zugriff entzogen – kein Absturz der Überwachung
        }
        if (count > 0) {
            SendScheduler.debounce(this, prefs.waitMinutes)
            val word = if (count == 1) "neues Foto" else "neue Fotos"
            Notifications.updateWatching(
                this, "$count $word – Versand nach ${prefs.waitMinutes} Min. Pause"
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onDestroy() {
        handler.removeCallbacks(check)
        observer?.let { contentResolver.unregisterContentObserver(it) }
        observer = null
        running = false
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        @Volatile
        var running: Boolean = false
            private set

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context, Intent(context, PhotoWatchService::class.java)
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, PhotoWatchService::class.java))
        }
    }
}
