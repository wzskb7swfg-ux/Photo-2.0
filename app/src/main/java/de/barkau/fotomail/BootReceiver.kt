package de.barkau.fotomail

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Startet die Überwachung nach einem Neustart oder Update automatisch wieder. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED && action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val prefs = Prefs(context)
        if (prefs.enabled && prefs.isConfigured && PhotoRepository.hasPermission(context)) {
            try {
                PhotoWatchService.start(context)
            } catch (ignored: Exception) {
                // Android erlaubt den Start gerade nicht – beim nächsten Öffnen der App läuft sie wieder.
            }
        }
    }
}
