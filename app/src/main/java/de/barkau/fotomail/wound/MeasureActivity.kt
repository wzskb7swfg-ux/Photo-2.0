package de.barkau.fotomail.wound

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.PointF
import android.os.Bundle
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import de.barkau.fotomail.Prefs
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.hypot

/**
 * Vermessung des Wundfotos in vier Schritten:
 * 1. Maßstab am Messstreifen, 2. Länge, 3. Breite, 4. Tiefe/Bemerkung und Senden.
 */
class MeasureActivity : Activity() {

    private lateinit var photoFile: File
    private lateinit var pseudonym: String
    private lateinit var location: String
    private var woundNo = 1
    private var takenAt = Date()

    private var bitmap: Bitmap? = null
    private var measure: MeasureView? = null
    private lateinit var root: LinearLayout
    private lateinit var header: TextView
    private lateinit var value: TextView
    private lateinit var panel: LinearLayout

    // Schritt 4
    private var depthField: EditText? = null
    private var noteField: EditText? = null
    private var originalSwitch: Switch? = null
    private var withoutScale = false
    private var estimated = false
    private var sending = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val path = intent.getStringExtra(WoundActivity.EXTRA_PHOTO)
        pseudonym = intent.getStringExtra(WoundActivity.EXTRA_PSEUDONYM) ?: ""
        location = intent.getStringExtra(WoundActivity.EXTRA_LOCATION) ?: ""
        woundNo = intent.getIntExtra(WoundActivity.EXTRA_WOUND_NO, 1)
        if (path == null || pseudonym.isEmpty() || !File(path).exists()) {
            Toast.makeText(this, "Das Foto ist nicht mehr vorhanden. Bitte neu aufnehmen.", Toast.LENGTH_LONG).show()
            finish(); return
        }
        photoFile = File(path)
        takenAt = Date(intent.getLongExtra(WoundActivity.EXTRA_TAKEN_AT, System.currentTimeMillis()))
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)

        root = Ui.vbox(this)
        root.setBackgroundColor(Color.BLACK)

        header = Ui.text(this, "Foto wird geladen …", 16f, Color.WHITE, true).apply {
            val p = Ui.dp(this@MeasureActivity, 12f)
            setPadding(p, p, p, Ui.dp(this@MeasureActivity, 4f))
        }
        root.addView(header)
        value = Ui.text(this, "", 14f, Color.argb(220, 255, 255, 255)).apply {
            val p = Ui.dp(this@MeasureActivity, 12f)
            setPadding(p, 0, p, Ui.dp(this@MeasureActivity, 8f))
        }
        root.addView(value)

        val loading = ProgressBar(this)
        root.addView(loading, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        panel = Ui.vbox(this).apply {
            setBackgroundColor(Ui.SURFACE)
            val p = Ui.dp(this@MeasureActivity, 14f)
            setPadding(p, p, p, p)
        }
        root.addView(panel)
        setContentView(root)
        applySystemBarPadding(root, header, panel)

        Thread {
            val bmp = try {
                WoundRenderer.load(photoFile)
            } catch (e: Throwable) {
                null
            }
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                if (bmp == null) {
                    Toast.makeText(this, "Foto konnte nicht geladen werden.", Toast.LENGTH_LONG).show()
                    finish(); return@runOnUiThread
                }
                bitmap = bmp
                val mv = MeasureView(this, bmp)
                mv.onChange = { updateValue() }
                measure = mv
                val idx = root.indexOfChild(loading)
                root.removeView(loading)
                root.addView(mv, idx, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
                showStep(MeasureView.Step.CAL)
            }
        }.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!sending) {
            // Abgebrochen: Foto nicht auf dem Handy liegen lassen
            if (::photoFile.isInitialized && isFinishing) photoFile.delete()
        }
        // Während des Sendens nutzt der Hintergrund-Thread die Bitmap noch
        if (!sending) bitmap?.recycle()
        bitmap = null
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (sending) return
        val mv = measure
        if (mv == null) { super.onBackPressed(); return }
        when (mv.step) {
            MeasureView.Step.CAL -> confirmDiscard()
            MeasureView.Step.LEN -> showStep(MeasureView.Step.CAL)
            MeasureView.Step.WID -> showStep(MeasureView.Step.LEN)
            MeasureView.Step.DONE -> if (withoutScale) showStep(MeasureView.Step.CAL) else showStep(MeasureView.Step.WID)
        }
    }

    private fun confirmDiscard() {
        AlertDialog.Builder(this)
            .setTitle("Foto verwerfen?")
            .setMessage("Das Foto wird gelöscht und nicht gesendet.")
            .setPositiveButton("Verwerfen") { _, _ -> finish() }
            .setNegativeButton("Weiter messen", null)
            .show()
    }

    // ------------------------------------------------------------------ Schritte

    private fun showStep(step: MeasureView.Step) {
        val mv = measure ?: return
        mv.step = step
        panel.removeAllViews()
        when (step) {
            MeasureView.Step.CAL -> stepCalibrate(mv)
            MeasureView.Step.LEN -> stepLine(
                "2/4 · Länge",
                "Blaue Punkte an die Wundränder ziehen: Kopf–Fuß-Richtung (12–6 Uhr), an der längsten Stelle.",
                MeasureView.Step.CAL, MeasureView.Step.WID
            )
            MeasureView.Step.WID -> stepLine(
                "3/4 · Breite",
                "Gelbe Punkte an die Wundränder ziehen: rechtwinklig zur Länge (3–9 Uhr), an der breitesten Stelle.",
                MeasureView.Step.LEN, MeasureView.Step.DONE
            )
            MeasureView.Step.DONE -> stepDetails()
        }
        updateValue()
    }

    /**
     * Grober Maßstab ohne Messstreifen: Lochkamera-Modell aus Fokusabstand,
     * Brennweite und Sensorgröße. Nur wenn die Kamera den Fokusabstand kalibriert liefert.
     */
    private fun estimatePxPerCm(bmp: Bitmap): Float {
        val diopters = intent.getFloatExtra(WoundActivity.EXTRA_FOCUS_DIOPTERS, -1f)
        val calibration = intent.getIntExtra(WoundActivity.EXTRA_FOCUS_CAL, -1)
        val focal = intent.getFloatExtra(WoundActivity.EXTRA_FOCAL_MM, 0f)
        val sensor = intent.getFloatExtra(WoundActivity.EXTRA_SENSOR_MM, 0f)
        // 0 = UNCALIBRATED: Werte sind dann keine echten Abstände
        if (calibration < 1 || diopters !in 0.5f..25f || focal <= 0f || sensor <= 0f) return 0f
        val distanceMm = 1000f / diopters
        val mmPerPx = sensor / kotlin.math.max(bmp.width, bmp.height) * distanceMm / focal
        return if (mmPerPx > 0f) 10f / mmPerPx else 0f
    }

    private fun stepCalibrate(mv: MeasureView) {
        withoutScale = false
        estimated = false
        mv.fixedPxPerCm = 0f
        header.text = "1/4 · Maßstab"
        panel.add(
            Ui.text(
                this,
                "Die zwei weißen Punkte auf zwei Striche des Messstreifens ziehen und den Abstand wählen. " +
                        "Mit zwei Fingern zoomen.",
                14f, Ui.INK
            )
        )
        val chips = Ui.hbox(this)
        val options = listOf(1f, 2f, 5f, 10f)
        if (mv.calCm <= 0f) mv.calCm = 5f
        val chipViews = mutableListOf<TextView>()
        fun refreshChips() {
            chipViews.forEachIndexed { i, t ->
                val on = options[i] == mv.calCm
                t.background = Ui.rounded(this, if (on) Ui.ACCENT else Ui.WHITE, Ui.ACCENT_LIGHT, 18f)
                t.setTextColor(if (on) Ui.WHITE else Ui.INK)
            }
        }
        for (o in options) {
            val t = Ui.text(this, "${o.toInt()} cm", 15f, Ui.INK, true).apply {
                gravity = Gravity.CENTER
                val p = Ui.dp(this@MeasureActivity, 10f)
                setPadding(p, p, p, p)
                setOnClickListener { mv.calCm = o; refreshChips() }
            }
            chipViews += t
            chips.addView(t, Ui.weight(this, 1f, 6f))
        }
        refreshChips()
        panel.add(chips, 10f)

        val buttons = Ui.hbox(this)
        val est = bitmap?.let { estimatePxPerCm(it) } ?: 0f
        if (est > 0f) {
            panel.add(Ui.button(this, "Kein Streifen? Grob schätzen", false) {
                estimated = true
                mv.fixedPxPerCm = est
                showStep(MeasureView.Step.LEN)
            }, 10f)
            panel.add(
                Ui.text(
                    this,
                    "Schätzung über den Abstand der Kamera – nur grob (±20–50 %, Fläche entsprechend mehr). " +
                            "Wunde in die Bildmitte, damit der Autofokus darauf sitzt. Mit Messstreifen ist es genauer.",
                    12f, Ui.MUTED
                ), 4f
            )
        } else {
            panel.add(
                Ui.text(
                    this,
                    "Grobe Schätzung ohne Messstreifen ist mit diesem Foto nicht möglich " +
                            "(die Kamera meldet keinen verlässlichen Abstand).",
                    12f, Ui.MUTED
                ), 8f
            )
        }

        buttons.addView(Ui.button(this, "Ohne Messung", false) {
            withoutScale = true
            mv.calCm = 0f
            showStep(MeasureView.Step.DONE)
        }, Ui.weight(this, 1f, 8f))
        buttons.addView(Ui.button(this, "Weiter", true) {
            if (mv.pxPerCm() <= 0f || MeasureView.Step.CAL !in mv.touched) {
                toast("Bitte die beiden weißen Punkte auf zwei Striche des Messstreifens ziehen.")
            } else {
                showStep(MeasureView.Step.LEN)
            }
        }, Ui.weight(this, 1f))
        panel.add(buttons, 12f)
    }

    private fun stepLine(title: String, text: String, back: MeasureView.Step, next: MeasureView.Step) {
        header.text = title
        panel.add(Ui.text(this, text, 14f, Ui.INK))
        val buttons = Ui.hbox(this)
        buttons.addView(Ui.button(this, "Zurück", false) { showStep(back) }, Ui.weight(this, 1f, 8f))
        buttons.addView(Ui.button(this, "Weiter", true) {
            val mv = measure
            if (mv != null && mv.step !in mv.touched) {
                toast("Bitte die Punkte an die Wundränder ziehen – die Vorgabe ist kein Messwert.")
            } else {
                showStep(next)
            }
        }, Ui.weight(this, 1f))
        panel.add(buttons, 12f)
    }

    private fun stepDetails() {
        header.text = "4/4 · Tiefe und Senden"
        depthField = Ui.field(this, "Tiefe in cm (leer = keine Angabe)", InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL)
        panel.add(depthField!!)
        val quick = Ui.hbox(this)
        for (v in listOf("0,1", "0,3", "0,5", "1", "2")) {
            val t = Ui.text(this, v, 15f, Ui.INK, true).apply {
                gravity = Gravity.CENTER
                background = Ui.rounded(this@MeasureActivity, Ui.WHITE, Ui.ACCENT_LIGHT, 16f)
                val p = Ui.dp(this@MeasureActivity, 8f)
                setPadding(p, p, p, p)
                setOnClickListener { depthField?.setText(v) }
            }
            quick.addView(t, Ui.weight(this, 1f, 6f))
        }
        panel.add(quick, 6f)
        panel.add(
            Ui.text(
                this,
                "Tiefe grob schätzen oder mit Watteträger messen (0,1 = oberflächlich). " +
                        "Aus dem Foto allein lässt sie sich nicht bestimmen.",
                12f, Ui.MUTED
            ), 4f
        )
        noteField = Ui.field(this, "Bemerkung (optional, keine Namen)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES)
        noteField!!.filters = arrayOf(InputFilter.LengthFilter(80))
        panel.add(noteField!!, 10f)
        originalSwitch = Switch(this).apply {
            text = "Unbearbeitetes Foto zusätzlich senden"
            setTextColor(Ui.INK)
            isChecked = WoundStore.sendOriginal(this@MeasureActivity)
            setOnCheckedChangeListener { _, on -> WoundStore.setSendOriginal(this@MeasureActivity, on) }
        }
        panel.add(originalSwitch!!, 8f)

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) = updateValue()
        }
        depthField!!.addTextChangedListener(watcher)

        val buttons = Ui.hbox(this)
        buttons.addView(Ui.button(this, "Zurück", false) { onBackPressedCompat() }, Ui.weight(this, 1f, 8f))
        buttons.addView(Ui.button(this, "Senden", true) { send() }, Ui.weight(this, 1f))
        panel.add(buttons, 12f)
    }

    private fun onBackPressedCompat() {
        if (withoutScale) showStep(MeasureView.Step.CAL) else showStep(MeasureView.Step.WID)
    }

    // ------------------------------------------------------------------ Anzeige

    private fun parseDepth(): Float? {
        val raw = depthField?.text?.toString()?.trim()?.replace(',', '.') ?: return null
        if (raw.isEmpty()) return null
        return raw.toFloatOrNull()
    }

    private fun updateValue() {
        val mv = measure ?: return
        val parts = mutableListOf<String>()
        if (mv.pxPerCm() > 0f && !withoutScale) {
            val ca = if (estimated) "ca. " else ""
            if (mv.len != null) parts += "L $ca${WoundMeasurement.f1(mv.lengthCm())} cm"
            if (mv.wid != null) parts += "B $ca${WoundMeasurement.f1(mv.widthCm())} cm"
            parseDepth()?.let { parts += "T ${WoundMeasurement.f1(it)} cm" }
            if (mv.len != null && mv.wid != null) {
                parts += "Fläche ${WoundMeasurement.f1(mv.lengthCm() * mv.widthCm())} cm²"
                val angle = angleBetween(mv.len!!, mv.wid!!)
                if (mv.step == MeasureView.Step.WID && abs(angle - 90f) > 15f) {
                    parts += "Winkel ${angle.toInt()}° – Breite möglichst rechtwinklig zur Länge"
                }
            }
        } else if (withoutScale) {
            parts += "Ohne Maßstab – Foto wird ohne Messwerte gesendet"
        }
        value.text = "$pseudonym · $location · Wunde $woundNo" + if (parts.isEmpty()) "" else "\n" + parts.joinToString(" · ")
    }

    private fun angleBetween(a: MeasureView.Line, b: MeasureView.Line): Float {
        val ax = a.b.x - a.a.x; val ay = a.b.y - a.a.y
        val bx = b.b.x - b.a.x; val by = b.b.y - b.a.y
        val la = hypot(ax, ay); val lb = hypot(bx, by)
        if (la < 1f || lb < 1f) return 90f
        val cos = ((ax * bx + ay * by) / (la * lb)).coerceIn(-1f, 1f)
        val deg = Math.toDegrees(acos(cos.toDouble())).toFloat()
        return if (deg > 90f) 180f - deg else deg
    }

    // ------------------------------------------------------------------ Senden

    private fun send() {
        val mv = measure ?: return
        val bmp = bitmap ?: return
        if (sending) return
        val depth = parseDepth()
        if (depthField?.text?.isNotBlank() == true && (depth == null || depth < 0f || depth > 20f)) {
            toast("Tiefe bitte als Zahl in cm eingeben, z. B. 0,5.")
            return
        }
        val note = noteField?.text?.toString()?.trim().orEmpty()
        val calibrated = !withoutScale && mv.pxPerCm() > 0f
        val m = WoundMeasurement(
            pxPerCm = if (calibrated) mv.pxPerCm() else 0f,
            lenA = mv.len?.a?.let { PointF(it.x, it.y) }, lenB = mv.len?.b?.let { PointF(it.x, it.y) },
            widA = mv.wid?.a?.let { PointF(it.x, it.y) }, widB = mv.wid?.b?.let { PointF(it.x, it.y) },
            depthCm = depth,
            note = note,
            estimated = calibrated && estimated
        )
        sending = true
        val sendOriginal = originalSwitch?.isChecked ?: true
        val maxEdge = if (Prefs(this).highRes) 4000 else 2560
        toast("Wird vorbereitet …")

        Thread {
            val ok = try {
                prepareAndQueue(bmp, m, sendOriginal, maxEdge)
                true
            } catch (e: Throwable) {
                false
            }
            runOnUiThread {
                if (ok) {
                    photoFile.delete()
                    toast("Wundfoto wird gesendet – auch später, falls gerade kein Netz da ist.")
                    finish()
                } else {
                    sending = false
                    toast("Fehler beim Vorbereiten. Bitte erneut versuchen.")
                }
            }
        }.start()
    }

    private fun prepareAndQueue(bmp: Bitmap, m: WoundMeasurement, sendOriginal: Boolean, maxEdge: Int) {
        val stamp = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.GERMANY).format(takenAt)
        val dateText = SimpleDateFormat("dd.MM.yyyy, HH:mm 'Uhr'", Locale.GERMANY).format(takenAt)
        val shortDate = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(takenAt)
        val base = "${WoundStore.safe(pseudonym)}_${stamp}_${WoundStore.safe(location)}_W$woundNo"
        val title = "$pseudonym · $location · Wunde $woundNo"

        val ctx = applicationContext
        WoundOutbox.cleanupTemp(ctx)
        val item = WoundOutbox.begin(ctx)
        try {
            WoundRenderer.saveAnnotated(bmp, m, File(item, "${base}_1_Messung.jpg"), maxEdge, title, dateText)
            if (sendOriginal) WoundRenderer.saveClean(bmp, File(item, "${base}_2_Original.jpg"), maxEdge)

            val subject = "Wundfoto $pseudonym – $location (W$woundNo) – $shortDate" +
                    if (m.estimated) " – grobe Schätzung" else ""
            val body = buildString {
                appendLine("Wundfoto $pseudonym")
                appendLine()
                appendLine("Lokalisation: $location (Wunde $woundNo)")
                appendLine("Aufnahme:     $dateText")
                val ca = if (m.estimated) "ca. " else ""
                if (m.calibrated) {
                    appendLine("Länge:        $ca${WoundMeasurement.f1(m.lengthCm)} cm")
                    appendLine("Breite:       $ca${WoundMeasurement.f1(m.widthCm)} cm")
                    appendLine("Tiefe:        " + (m.depthCm?.let { WoundMeasurement.f1(it) + " cm (von Hand eingetragen)" } ?: "keine Angabe"))
                    appendLine("Fläche (L×B): $ca${WoundMeasurement.f1(m.areaCm2)} cm²")
                } else {
                    appendLine("Messung:      keine (ohne Maßstab)")
                    m.depthCm?.let { appendLine("Tiefe:        ${WoundMeasurement.f1(it)} cm (von Hand eingetragen)") }
                }
                if (m.note.isNotEmpty()) appendLine("Bemerkung:    ${m.note}")
                appendLine()
                if (m.estimated) {
                    appendLine("Länge und Breite sind eine GROBE SCHÄTZUNG über den Kameraabstand (ohne Messstreifen).")
                    appendLine("Abweichungen von ±20–50 % sind möglich, bei der Fläche entsprechend mehr.")
                    appendLine("Nicht als Messwert in die Dokumentation übernehmen – genaue Messung durch die Wundexpertin.")
                    appendLine()
                } else if (m.calibrated) {
                    appendLine("Länge und Breite wurden am Foto mit einem Messstreifen als Maßstab ermittelt.")
                    appendLine("Es sind Orientierungswerte; maßgeblich bleibt die Messung am Klienten.")
                    appendLine()
                }
                appendLine("Gesendet von FotoMail. Standort- und Gerätedaten wurden entfernt.")
            }
            WoundOutbox.commit(ctx, item, subject, body)
        } catch (e: Throwable) {
            item.deleteRecursively()
            throw e
        }

        if (WoundStore.ghostEnabled(ctx)) {
            try {
                WoundStore.saveGhost(ctx, pseudonym, location, woundNo, bmp)
            } catch (ignored: Throwable) {
            }
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
