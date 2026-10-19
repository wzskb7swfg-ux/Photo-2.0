package de.barkau.fotomail

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import javax.mail.AuthenticationFailedException

class MainActivity : Activity() {

    private lateinit var prefs: Prefs

    private lateinit var recipient: EditText
    private lateinit var host: EditText
    private lateinit var port: EditText
    private lateinit var user: EditText
    private lateinit var password: EditText
    private lateinit var wait: EditText
    private lateinit var highRes: Switch
    private lateinit var enable: Switch
    private lateinit var status: TextView
    private lateinit var hint: TextView
    private lateinit var battery: Button

    private var updatingSwitch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        prefs = Prefs(this)
        Notifications.ensureChannels(this)

        recipient = findViewById(R.id.recipient)
        host = findViewById(R.id.host)
        port = findViewById(R.id.port)
        user = findViewById(R.id.user)
        password = findViewById(R.id.password)
        wait = findViewById(R.id.wait)
        highRes = findViewById(R.id.highRes)
        enable = findViewById(R.id.enable)
        status = findViewById(R.id.status)
        hint = findViewById(R.id.hint)
        battery = findViewById(R.id.battery)

        recipient.setText(prefs.recipient)
        host.setText(prefs.host)
        port.setText(prefs.port.toString())
        user.setText(prefs.user)
        wait.setText(prefs.waitMinutes.toString())
        highRes.isChecked = prefs.highRes
        if (prefs.password.isNotEmpty()) {
            password.hint = "gespeichert – nur zum Ändern ausfüllen"
        }

        findViewById<Button>(R.id.save).setOnClickListener {
            if (save()) toast("Gespeichert")
        }
        findViewById<Button>(R.id.test).setOnClickListener { sendTest() }
        findViewById<Button>(R.id.sendNow).setOnClickListener {
            if (!prefs.isConfigured) {
                toast("Bitte zuerst die E-Mail-Einstellungen ausfüllen.")
            } else if (!prefs.enabled) {
                toast("Bitte zuerst „Automatischer Versand“ einschalten.")
            } else {
                SendScheduler.sendNow(this)
                toast("Versand angestoßen – neue Fotos werden gesendet.")
            }
        }
        battery.setOnClickListener { requestBatteryExemption() }
        findViewById<Button>(R.id.woundPhoto).setOnClickListener {
            startActivity(Intent(this, de.barkau.fotomail.wound.WoundActivity::class.java))
        }

        enable.setOnCheckedChangeListener { _, checked ->
            if (updatingSwitch) return@setOnCheckedChangeListener
            if (checked) tryEnable() else disable()
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        updatingSwitch = true
        enable.isChecked = prefs.enabled && PhotoWatchService.running
        updatingSwitch = false

        val lines = mutableListOf<String>()
        lines += if (PhotoWatchService.running) "● Überwachung läuft" else "○ Überwachung aus"
        if (prefs.lastStatus.isNotBlank()) lines += prefs.lastStatus
        status.text = lines.joinToString("\n")

        val pm = getSystemService(PowerManager::class.java)
        battery.visibility =
            if (pm.isIgnoringBatteryOptimizations(packageName)) View.GONE else View.VISIBLE

        hint.visibility = if (PhotoRepository.hasPermission(this)) View.GONE else View.VISIBLE
    }

    private fun save(): Boolean {
        val p = port.text.toString().trim().toIntOrNull()
        val w = wait.text.toString().trim().toIntOrNull()
        if (recipient.text.isBlank() || !recipient.text.contains("@")) {
            recipient.error = "Bitte gültige E-Mail-Adresse"; return false
        }
        if (user.text.isBlank() || !user.text.contains("@")) {
            user.error = "Bitte gültige E-Mail-Adresse"; return false
        }
        if (host.text.isBlank()) { host.error = "Pflichtfeld"; return false }
        if (p == null || p !in 1..65535) { port.error = "z. B. 465"; return false }
        if (w == null || w !in 1..60) { wait.error = "1 bis 60 Minuten"; return false }

        prefs.recipient = recipient.text.toString()
        prefs.host = host.text.toString()
        prefs.port = p
        prefs.user = user.text.toString()
        prefs.waitMinutes = w
        prefs.highRes = highRes.isChecked
        val pw = password.text.toString()
        if (pw.isNotEmpty()) {
            prefs.password = pw
            password.setText("")
            password.hint = "gespeichert – nur zum Ändern ausfüllen"
        }
        if (prefs.password.isEmpty()) {
            password.error = "App-Passwort fehlt"; return false
        }
        return true
    }

    private fun sendTest() {
        if (!save()) return
        status.text = "Test-Mail wird gesendet …"
        Thread {
            val msg = try {
                MailSender.send(
                    prefs,
                    "FotoMail – Test",
                    "Die Einstellungen funktionieren. Ab jetzt kommen deine Fotos automatisch an.",
                    emptyList()
                )
                "Test-Mail gesendet ✓ – schau in dein Postfach."
            } catch (e: AuthenticationFailedException) {
                "Anmeldung abgelehnt. Bei Gmail: App-Passwort verwenden, nicht das normale Passwort."
            } catch (e: Exception) {
                "Fehler: ${e.message ?: e.javaClass.simpleName}"
            }
            runOnUiThread {
                prefs.lastStatus = msg
                refresh()
            }
        }.start()
    }

    private fun tryEnable() {
        if (!save()) {
            setSwitch(false); return
        }
        val needed = mutableListOf<String>()
        if (!PhotoRepository.hasPermission(this)) {
            needed += PhotoRepository.photoPermission()
            if (Build.VERSION.SDK_INT >= 34) needed += Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) needed += Manifest.permission.POST_NOTIFICATIONS

        if (needed.isEmpty()) startWatching()
        else requestPermissions(needed.toTypedArray(), REQ_PERMS)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != REQ_PERMS) return
        if (PhotoRepository.hasPermission(this)) {
            startWatching()
        } else {
            setSwitch(false)
            toast("Ohne Zugriff auf Fotos kann die App nichts senden. Bitte „Alle zulassen“ wählen.")
            refresh()
        }
    }

    private fun startWatching() {
        // Fotos, die bei ausgeschaltetem Versand entstanden sind, werden NICHT nachträglich gesendet.
        if (prefs.lastSentId < 0 || !prefs.enabled) prefs.lastSentId = PhotoRepository.maxId(this)
        prefs.enabled = true
        PhotoWatchService.start(this)
        toast("Überwachung gestartet – ab jetzt gemachte Fotos werden gesendet.")
        status.postDelayed({ refresh() }, 500)
    }

    private fun disable() {
        prefs.enabled = false
        SendScheduler.cancelPending(this)
        PhotoWatchService.stop(this)
        status.postDelayed({ refresh() }, 300)
    }

    private fun setSwitch(on: Boolean) {
        updatingSwitch = true
        enable.isChecked = on
        updatingSwitch = false
    }

    @Suppress("BatteryLife")
    private fun requestBatteryExemption() {
        try {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
            )
        } catch (ignored: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            } catch (alsoIgnored: Exception) {
                toast("Bitte in den Android-Einstellungen die Akku-Optimierung für FotoMail ausschalten.")
            }
        }
    }

    private fun toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_LONG).show()

    companion object {
        private const val REQ_PERMS = 42
    }
}
