package de.barkau.fotomail.wound

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputFilter
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import de.barkau.fotomail.Prefs

/**
 * Start der Wundfoto-Aufnahme: Pseudonym, Lokalisation und Wundnummer wählen.
 */
class WoundActivity : Activity() {

    private lateinit var pseudoSpinner: Spinner
    private lateinit var locSpinner: Spinner
    private lateinit var noSpinner: Spinner
    private lateinit var ghostSwitch: Switch
    private lateinit var ghostInfo: TextView
    private lateinit var mailWarning: TextView
    private lateinit var outboxInfo: TextView

    private var pseudonyms: List<String> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Ui.SURFACE

        val root = Ui.vbox(this)
        val p = Ui.dp(this, 16f)
        root.setPadding(p, p, p, p)
        root.setBackgroundColor(Ui.SURFACE)

        root.add(Ui.text(this, "Wundfoto", 28f, Ui.INK, true))
        root.add(
            Ui.text(
                this,
                "Foto mit Messung – wird nur mit Pseudonym verschickt und nicht in der Galerie gespeichert.",
                14f, Ui.MUTED
            ), 2f
        )

        mailWarning = Ui.text(this, "", 14f, Ui.WARN, true)
        root.add(mailWarning, 10f)
        outboxInfo = Ui.text(this, "", 14f, Ui.INK, true)
        outboxInfo.background = Ui.rounded(this, Ui.SOFT, 0, 10f)
        outboxInfo.setPadding(Ui.dp(this, 12f), Ui.dp(this, 10f), Ui.dp(this, 12f), Ui.dp(this, 10f))
        outboxInfo.setOnClickListener {
            WoundOutbox.schedule(this)
            toast("Versand angestoßen – wird gesendet, sobald Internet da ist.")
        }
        root.add(outboxInfo, 8f)

        // --- Pseudonym
        val c1 = Ui.card(this)
        c1.add(Ui.text(this, "Pseudonym", 13f, Ui.MUTED))
        val row = Ui.hbox(this)
        pseudoSpinner = Spinner(this)
        row.addView(pseudoSpinner, Ui.weight(this, 1f, 8f))
        val addBtn = Ui.button(this, "+ Neu", false) { askNewPseudonym() }
        row.addView(addBtn, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        c1.add(row, 4f)
        c1.add(
            Ui.text(
                this,
                "Pseudonym aus eurer Liste – keine echten Namen, Initialen oder Geburtsdaten. " +
                        "Die Zuordnung zum Klienten steht nur in der Liste im Büro.",
                12f, Ui.MUTED
            ), 6f
        )
        val manage = Ui.text(this, "Pseudonym entfernen …", 13f, Ui.ACCENT, true)
        manage.setOnClickListener { askRemovePseudonym() }
        c1.add(manage, 8f)
        root.add(c1, 14f)

        // --- Wunde
        val c2 = Ui.card(this)
        c2.add(Ui.text(this, "Lokalisation", 13f, Ui.MUTED))
        locSpinner = Spinner(this)
        locSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, WoundStore.LOCATIONS)
        c2.add(locSpinner, 4f)
        c2.add(Ui.text(this, "Wunde Nr. (bei mehreren Wunden an derselben Stelle)", 13f, Ui.MUTED), 12f)
        noSpinner = Spinner(this)
        noSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, (1..5).map { "Wunde $it" })
        c2.add(noSpinner, 4f)
        ghostSwitch = Switch(this).apply {
            text = "Letztes Foto halbtransparent einblenden"
            setTextColor(Ui.INK)
            isChecked = WoundStore.ghostEnabled(this@WoundActivity)
            setOnCheckedChangeListener { _, on ->
                WoundStore.setGhostEnabled(this@WoundActivity, on)
                updateGhostInfo()
            }
        }
        c2.add(ghostSwitch, 12f)
        ghostInfo = Ui.text(this, "", 12f, Ui.MUTED)
        c2.add(ghostInfo, 2f)
        root.add(c2, 12f)

        // --- Hinweise
        val c3 = Ui.card(this)
        c3.background = Ui.rounded(this, Ui.SOFT, 0, 14f)
        c3.add(Ui.text(this, "Für eine Messung", 14f, Ui.INK, true))
        c3.add(
            Ui.text(
                this,
                "• Papier-Messstreifen direkt neben die Wunde legen (nicht in die Wunde).\n" +
                        "• Handy senkrecht über die Wunde halten, ca. 20–30 cm Abstand.\n" +
                        "• Gleichmäßiges Licht, kein Blitz-Spiegeln.\n" +
                        "• Kein Gesicht, keine Tattoos, Schmuck oder Papiere im Bild.\n" +
                        "• Fotodokumentation nur nach eurem Hausstandard (Einwilligung/Aufklärung).",
                13f, Ui.MUTED
            ), 4f
        )
        root.add(c3, 12f)

        root.add(Ui.button(this, "Kamera öffnen", true) { openCamera() }, 18f)

        val c4 = Ui.text(this, "Gespeicherte Vergleichsbilder löschen", 13f, Ui.MUTED)
        c4.setOnClickListener { askDeleteGhosts() }
        root.add(c4, 18f)

        val scroll = ScrollView(this)
        scroll.setBackgroundColor(Ui.SURFACE)
        scroll.fitsSystemWindows = true
        scroll.addView(root)
        setContentView(scroll)

        val listener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (parent === pseudoSpinner) restoreLocation()
                updateGhostInfo()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        pseudoSpinner.onItemSelectedListener = listener
        locSpinner.onItemSelectedListener = listener
        noSpinner.onItemSelectedListener = listener

        reloadPseudonyms(WoundStore.lastPseudonym(this))

        WoundStore.purgeOldGhosts(this)
        WoundStore.clearCaptures(this)
        WoundOutbox.cleanupTemp(this)

        // Damit Versandprobleme auch ohne „Automatischer Versand“ angezeigt werden
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        val prefs = Prefs(this)
        mailWarning.visibility = if (prefs.isConfigured) View.GONE else View.VISIBLE
        mailWarning.text = "E-Mail ist noch nicht eingerichtet – bitte zuerst auf der Startseite " +
                "Empfänger, Absender und Passwort eintragen."
        val n = WoundOutbox.pending(this).size
        outboxInfo.visibility = if (n > 0) View.VISIBLE else View.GONE
        outboxInfo.text = "$n Wundfoto(s) warten auf Versand – tippen für „Erneut senden“"
        updateGhostInfo()
    }

    private fun selectedPseudonym(): String? =
        pseudonyms.getOrNull(pseudoSpinner.selectedItemPosition)

    private fun selectedLocation(): String =
        WoundStore.LOCATIONS[locSpinner.selectedItemPosition.coerceAtLeast(0)]

    private fun selectedNo(): Int = noSpinner.selectedItemPosition.coerceAtLeast(0) + 1

    private fun reloadPseudonyms(select: String?) {
        pseudonyms = WoundStore.pseudonyms(this)
        val items = if (pseudonyms.isEmpty()) listOf("– bitte „+ Neu“ –") else pseudonyms
        pseudoSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, items)
        val idx = pseudonyms.indexOf(select)
        if (idx >= 0) pseudoSpinner.setSelection(idx)
        restoreLocation()
        updateGhostInfo()
    }

    private fun restoreLocation() {
        val p = selectedPseudonym() ?: return
        val loc = WoundStore.lastLocation(this, p)
        val i = WoundStore.LOCATIONS.indexOf(loc)
        if (i >= 0) locSpinner.setSelection(i)
    }

    private fun updateGhostInfo() {
        val p = selectedPseudonym()
        ghostInfo.text = when {
            !ghostSwitch.isChecked -> "Aus."
            p == null -> ""
            WoundStore.hasGhost(this, p, selectedLocation(), selectedNo()) ->
                "Vorheriges Foto vorhanden – hilft, aus gleichem Winkel und Abstand zu fotografieren."
            else -> "Noch kein vorheriges Foto für diese Wunde."
        }
    }

    private fun askNewPseudonym() {
        val input = Ui.field(this, "Pseudonym(e)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE)
        input.isSingleLine = false
        input.minLines = 3
        input.maxLines = 8
        input.filters = arrayOf(InputFilter.LengthFilter(2000))
        val box = Ui.vbox(this)
        val pad = Ui.dp(this, 20f)
        box.setPadding(pad, Ui.dp(this, 8f), pad, 0)
        box.add(input)
        box.add(
            Ui.text(
                this,
                "Eins pro Zeile – du kannst auch deine ganze Liste auf einmal einfügen. " +
                        "Bis 20 Zeichen: Buchstaben, Ziffern, Leerzeichen, - _ . – keine echten Namen.",
                12f, Ui.MUTED
            ), 6f
        )
        AlertDialog.Builder(this)
            .setTitle("Pseudonyme hinzufügen")
            .setView(box)
            .setPositiveButton("Speichern") { _, _ ->
                val lines = input.text.toString().split('\n', ';', ',').map { it.trim() }.filter { it.isNotEmpty() }
                val nameLike = lines.filter { l -> l.none { it.isDigit() } }
                if (nameLike.isEmpty()) {
                    savePseudonyms(lines)
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Sieht nach Namen aus")
                        .setMessage(
                            "Diese Einträge enthalten keine Ziffer:\n\n" + nameLike.take(10).joinToString("\n") +
                                    (if (nameLike.size > 10) "\n…" else "") +
                                    "\n\nSind das wirklich Pseudonyme aus eurer Liste und keine echten Namen?"
                        )
                        .setPositiveButton("Ja, Pseudonyme") { _, _ -> savePseudonyms(lines) }
                        .setNegativeButton("Nur die mit Ziffer") { _, _ -> savePseudonyms(lines - nameLike.toSet()) }
                        .show()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun savePseudonyms(lines: List<String>) {
        val added = WoundStore.addPseudonyms(this, lines)
        val skipped = lines.size - added.size
        reloadPseudonyms(added.firstOrNull())
        toast(
            "${added.size} übernommen" +
                    if (skipped > 0) ", $skipped übersprungen (doppelt, zu lang oder unerlaubte Zeichen)." else "."
        )
    }

    private fun askRemovePseudonym() {
        if (pseudonyms.isEmpty()) return
        AlertDialog.Builder(this)
            .setTitle("Pseudonym entfernen")
            .setItems(pseudonyms.toTypedArray()) { _, which ->
                val p = pseudonyms[which]
                AlertDialog.Builder(this)
                    .setMessage("„$p“ und das gespeicherte Vergleichsbild entfernen?")
                    .setPositiveButton("Entfernen") { _, _ ->
                        WoundStore.removePseudonym(this, p)
                        reloadPseudonyms(null)
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
            .setNegativeButton("Schließen", null)
            .show()
    }

    private fun askDeleteGhosts() {
        val n = WoundStore.ghostCount(this)
        AlertDialog.Builder(this)
            .setTitle("Vergleichsbilder löschen")
            .setMessage("Auf diesem Handy sind $n verkleinerte Vergleichsbilder gespeichert (nur in der App). Alle löschen?")
            .setPositiveButton("Löschen") { _, _ ->
                WoundStore.deleteAllGhosts(this)
                updateGhostInfo()
                toast("Gelöscht.")
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun openCamera() {
        val p = selectedPseudonym()
        if (p == null) {
            toast("Bitte zuerst ein Pseudonym anlegen.")
            return
        }
        if (!Prefs(this).isConfigured) {
            toast("Bitte zuerst die E-Mail-Einstellungen auf der Startseite ausfüllen.")
            return
        }
        val loc = selectedLocation()
        WoundStore.setLastPseudonym(this, p)
        WoundStore.setLastLocation(this, p, loc)
        startActivity(
            Intent(this, WoundCameraActivity::class.java)
                .putExtra(EXTRA_PSEUDONYM, p)
                .putExtra(EXTRA_LOCATION, loc)
                .putExtra(EXTRA_WOUND_NO, selectedNo())
                .putExtra(EXTRA_GHOST, ghostSwitch.isChecked)
        )
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_PSEUDONYM = "pseudonym"
        const val EXTRA_LOCATION = "location"
        const val EXTRA_WOUND_NO = "wound_no"
        const val EXTRA_GHOST = "ghost"
        const val EXTRA_PHOTO = "photo"
        const val EXTRA_TAKEN_AT = "taken_at"
        const val EXTRA_FOCUS_DIOPTERS = "focus_diopters"
        const val EXTRA_FOCUS_CAL = "focus_cal"
        const val EXTRA_FOCAL_MM = "focal_mm"
        const val EXTRA_SENSOR_MM = "sensor_mm"
        private const val REQ_NOTIFICATIONS = 11
    }
}
