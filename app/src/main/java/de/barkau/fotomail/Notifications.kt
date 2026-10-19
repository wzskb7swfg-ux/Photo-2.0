package de.barkau.fotomail

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object Notifications {

    private const val CH_WATCH = "watch"
    private const val CH_INFO = "info"
    const val WATCH_ID = 1
    private const val INFO_ID = 2

    fun ensureChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_WATCH, "Foto-Überwachung", NotificationManager.IMPORTANCE_LOW)
                .apply { setShowBadge(false) }
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_INFO, "Probleme beim Versand", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

    fun watching(context: Context, text: String?): Notification =
        NotificationCompat.Builder(context, CH_WATCH)
            .setSmallIcon(R.drawable.ic_stat_fotomail)
            .setContentTitle("FotoMail aktiv")
            .setContentText(text ?: "Wartet auf neue Fotos")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp(context))
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()

    /** Aktualisiert die Daueranzeige – nur solange die Überwachung wirklich läuft. */
    fun updateWatching(context: Context, text: String?) {
        if (!PhotoWatchService.running) return
        try {
            context.getSystemService(NotificationManager::class.java)
                .notify(WATCH_ID, watching(context, text))
        } catch (ignored: SecurityException) {
        }
    }

    fun problem(context: Context, text: String) {
        ensureChannels(context)
        val n = NotificationCompat.Builder(context, CH_INFO)
            .setSmallIcon(R.drawable.ic_stat_fotomail)
            .setContentTitle("FotoMail: Versand fehlgeschlagen")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(context))
            .build()
        try {
            context.getSystemService(NotificationManager::class.java).notify(INFO_ID, n)
        } catch (ignored: SecurityException) {
        }
    }
}
