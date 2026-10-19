package de.barkau.fotomail

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

/**
 * Einstellungen der App. Das Mail-Passwort liegt verschlüsselt
 * (Android-Keystore), alles andere in normalen App-Einstellungen.
 */
class Prefs(context: Context) {

    private val app = context.applicationContext
    private val plain: SharedPreferences =
        app.getSharedPreferences("fotomail", Context.MODE_PRIVATE)

    private val secure: SharedPreferences by lazy {
        try {
            createSecure()
        } catch (ignored: Exception) {
            // Schlüssel passt nicht mehr (z. B. nach Gerätewechsel) – Passwort muss neu eingegeben werden.
            app.deleteSharedPreferences(SECURE_FILE)
            createSecure()
        }
    }

    private fun createSecure(): SharedPreferences {
        val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)
        return EncryptedSharedPreferences.create(
            SECURE_FILE,
            masterKey,
            app,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    var recipient: String
        get() = plain.getString(KEY_RECIPIENT, "") ?: ""
        set(v) = plain.edit().putString(KEY_RECIPIENT, v.trim()).apply()

    var host: String
        get() = plain.getString(KEY_HOST, "smtp.gmail.com") ?: "smtp.gmail.com"
        set(v) = plain.edit().putString(KEY_HOST, v.trim()).apply()

    var port: Int
        get() = plain.getInt(KEY_PORT, 465)
        set(v) = plain.edit().putInt(KEY_PORT, v).apply()

    var user: String
        get() = plain.getString(KEY_USER, "") ?: ""
        set(v) = plain.edit().putString(KEY_USER, v.trim()).apply()

    var password: String
        get() = try {
            secure.getString(KEY_PASSWORD, "") ?: ""
        } catch (ignored: Exception) {
            "" // Entschlüsselung nicht möglich – Passwort muss neu eingegeben werden.
        }
        set(v) = secure.edit().putString(KEY_PASSWORD, v).apply()

    /** Minuten Ruhe nach dem letzten Foto, bevor gesendet wird. */
    var waitMinutes: Int
        get() = plain.getInt(KEY_WAIT, 3)
        set(v) = plain.edit().putInt(KEY_WAIT, v.coerceIn(1, 60)).apply()

    var highRes: Boolean
        get() = plain.getBoolean(KEY_HIGHRES, false)
        set(v) = plain.edit().putBoolean(KEY_HIGHRES, v).apply()

    var enabled: Boolean
        get() = plain.getBoolean(KEY_ENABLED, false)
        set(v) = plain.edit().putBoolean(KEY_ENABLED, v).apply()

    /** ID des zuletzt verarbeiteten Fotos in der Galerie (-1 = noch nie gestartet). */
    var lastSentId: Long
        get() = plain.getLong(KEY_LAST_ID, -1L)
        set(v) {
            plain.edit().putLong(KEY_LAST_ID, v).commit()
        }

    var lastStatus: String
        get() = plain.getString(KEY_STATUS, "") ?: ""
        set(v) = plain.edit().putString(KEY_STATUS, v).apply()

    val isConfigured: Boolean
        get() = recipient.isNotBlank() && host.isNotBlank() && user.isNotBlank() &&
                password.isNotBlank() && port in 1..65535

    companion object {
        private const val SECURE_FILE = "fotomail_secure"
        private const val KEY_RECIPIENT = "recipient"
        private const val KEY_HOST = "host"
        private const val KEY_PORT = "port"
        private const val KEY_USER = "user"
        private const val KEY_PASSWORD = "password"
        private const val KEY_WAIT = "wait_minutes"
        private const val KEY_HIGHRES = "high_res"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_LAST_ID = "last_sent_id"
        private const val KEY_STATUS = "last_status"
    }
}
