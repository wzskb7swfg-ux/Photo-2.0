package de.barkau.fotomail.wound

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.Typeface
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Messergebnis einer Aufnahme. Strecken in Bild-Pixeln des geladenen Fotos. */
data class WoundMeasurement(
    val pxPerCm: Float,               // 0 = ohne Maßstab
    val lenA: PointF?, val lenB: PointF?,
    val widA: PointF?, val widB: PointF?,
    val depthCm: Float?,              // null = nicht angegeben
    val note: String,
    val estimated: Boolean = false    // true = grobe Schätzung über den Kameraabstand (ohne Messstreifen)
) {
    val calibrated: Boolean get() = pxPerCm > 0f

    val lengthCm: Float
        get() = if (calibrated && lenA != null && lenB != null)
            hypot(lenB.x - lenA.x, lenB.y - lenA.y) / pxPerCm else 0f

    val widthCm: Float
        get() = if (calibrated && widA != null && widB != null)
            hypot(widB.x - widA.x, widB.y - widA.y) / pxPerCm else 0f

    /** Fläche nach der in der Wunddokumentation üblichen Rechteckmethode (Länge × Breite). */
    val areaCm2: Float get() = lengthCm * widthCm

    fun summary(): String {
        if (!calibrated) return "ohne Messung (kein Maßstab gesetzt)" +
                (depthCm?.let { " · Tiefe ${f1(it)} cm" } ?: "")
        val ca = if (estimated) "ca. " else ""
        val parts = mutableListOf("Länge $ca${f1(lengthCm)} cm", "Breite $ca${f1(widthCm)} cm")
        parts += if (depthCm != null) "Tiefe ${f1(depthCm)} cm" else "Tiefe nicht angegeben"
        parts += "Fläche (L×B) $ca${f1(areaCm2)} cm²"
        return parts.joinToString(" · ")
    }

    companion object {
        fun f1(v: Float): String = String.format(Locale.GERMANY, "%.1f", v)
    }
}

object WoundRenderer {

    /** Lädt das Foto, dreht es richtig herum und begrenzt die Größe (Speicher). */
    fun load(file: File, maxEdge: Int = 3000): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0) throw IOException("Foto nicht lesbar")
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        var bmp = BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
            ?: throw IOException("Foto nicht lesbar")
        val orientation = try {
            ExifInterface(file.absolutePath).getAttributeInt(
                ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
            )
        } catch (e: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }
        val m = Matrix()
        val rotate = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
        // inSampleSize halbiert nur – auf die Zielgröße genau begrenzen (Speicher)
        val longEdge = max(bmp.width, bmp.height)
        if (longEdge > maxEdge) {
            val f = maxEdge.toFloat() / longEdge
            val scaled = Bitmap.createScaledBitmap(bmp, (bmp.width * f).roundToInt(), (bmp.height * f).roundToInt(), true)
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }
        if (rotate != 0f) {
            m.setRotate(rotate)
            val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            if (r !== bmp) bmp.recycle()
            bmp = r
        }
        return bmp
    }

    /** Verkleinert auf [maxEdge] und speichert als JPEG ohne Metadaten. */
    fun saveClean(src: Bitmap, out: File, maxEdge: Int) {
        val scaled = scaleTo(src, maxEdge)
        try {
            writeJpeg(scaled, out)
        } finally {
            if (scaled !== src) scaled.recycle()
        }
    }

    /**
     * Zeichnet Länge, Breite, Maßband und eine Infozeile (Pseudonym, Datum, Messwerte) in das Foto.
     */
    fun saveAnnotated(
        src: Bitmap, m: WoundMeasurement, out: File, maxEdge: Int,
        title: String, dateText: String
    ) {
        val f = min(1f, maxEdge.toFloat() / max(src.width, src.height))
        val w = (src.width * f).roundToInt()
        val h = (src.height * f).roundToInt()
        val unit = w / 100f                       // 1 % der Bildbreite
        val band = (unit * 17f).roundToInt()      // Infoleiste unten

        val outBmp = Bitmap.createBitmap(w, h + band, Bitmap.Config.ARGB_8888)
        val c = Canvas(outBmp)
        c.drawColor(Color.WHITE)
        val photoPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
        c.drawBitmap(src, null, RectF(0f, 0f, w.toFloat(), h.toFloat()), photoPaint)

        fun sp(p: PointF?) = p?.let { PointF(it.x * f, it.y * f) }

        if (m.calibrated) {
            val ppc = m.pxPerCm * f
            val ca = if (m.estimated) "ca. " else ""
            drawMeasure(c, sp(m.lenA), sp(m.lenB), MeasureView.COLOR_LEN, "L $ca${WoundMeasurement.f1(m.lengthCm)} cm", unit)
            drawMeasure(c, sp(m.widA), sp(m.widB), MeasureView.COLOR_WID, "B $ca${WoundMeasurement.f1(m.widthCm)} cm", unit)
            drawRuler(c, ppc, w, h, unit, m.estimated)
        }

        // Infoleiste
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(58, 65, 70) }
        val x = unit * 2.5f
        var y = h + unit * 4.6f
        text.textSize = unit * 3.6f
        text.typeface = Typeface.DEFAULT_BOLD
        val maxW = w - 2 * x
        drawFit(c, title, x, y, text, maxW)
        text.typeface = Typeface.DEFAULT
        text.textSize = unit * 2.9f
        y += unit * 3.9f
        drawFit(c, dateText, x, y, text, maxW)
        text.textSize = unit * 2.9f
        y += unit * 3.6f
        drawFit(c, m.summary(), x, y, text, maxW)
        text.textSize = unit * 2.2f
        text.color = Color.rgb(110, 115, 120)
        y += unit * 3.2f
        val hint = when {
            m.estimated -> "Grobe Schätzung über den Kameraabstand (ohne Messstreifen) – Abweichung ±20–50 % möglich, kein Messwert."
            m.calibrated -> "Messung am Foto mit Messstreifen als Maßstab – Orientierungswerte, ersetzen nicht die Messung am Klienten."
            else -> "Foto ohne Maßstab – keine Größenangabe möglich."
        }
        drawFit(c, hint, x, y, text, maxW)

        try {
            writeJpeg(outBmp, out)
        } finally {
            outBmp.recycle()
        }
    }

    /** Zeichnet eine Zeile und verkleinert die Schrift, bis sie in die Breite passt. */
    private fun drawFit(c: Canvas, s: String, x: Float, y: Float, p: Paint, maxWidth: Float) {
        while (p.textSize > 8f && p.measureText(s) > maxWidth) p.textSize *= 0.94f
        c.drawText(s, x, y, p)
    }

    private fun drawMeasure(c: Canvas, a: PointF?, b: PointF?, color: Int, label: String, unit: Float) {
        if (a == null || b == null) return
        val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = Color.argb(190, 0, 0, 0); strokeWidth = unit * 0.9f; strokeCap = Paint.Cap.ROUND
        }
        val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; strokeWidth = unit * 0.45f; strokeCap = Paint.Cap.ROUND
        }
        c.drawLine(a.x, a.y, b.x, b.y, outline)
        c.drawLine(a.x, a.y, b.x, b.y, line)
        // Endmarken senkrecht zur Linie
        val len = hypot(b.x - a.x, b.y - a.y).coerceAtLeast(1f)
        val nx = -(b.y - a.y) / len * unit * 1.6f
        val ny = (b.x - a.x) / len * unit * 1.6f
        for (p in listOf(a, b)) {
            c.drawLine(p.x - nx, p.y - ny, p.x + nx, p.y + ny, outline)
            c.drawLine(p.x - nx, p.y - ny, p.x + nx, p.y + ny, line)
        }
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color; textSize = unit * 3.4f; typeface = Typeface.DEFAULT_BOLD
            setShadowLayer(unit * 0.5f, 0f, 0f, Color.BLACK)
        }
        c.drawText(label, (a.x + b.x) / 2 + unit * 1.5f, (a.y + b.y) / 2 - unit * 1.5f, t)
    }

    /** Maßband mit cm- (und ggf. mm-) Strichen unten links im Foto. */
    private fun drawRuler(c: Canvas, pxPerCm: Float, w: Int, h: Int, unit: Float, estimated: Boolean) {
        if (pxPerCm <= 0f) return
        val cm = listOf(10, 5, 2, 1).firstOrNull { it * pxPerCm <= w * 0.45f } ?: return
        val len = cm * pxPerCm
        val left = unit * 3f
        val base = h - unit * 3f
        val tall = unit * 3.2f
        val bg = Paint().apply { color = Color.argb(215, 255, 255, 255) }
        c.drawRect(left - unit, base - tall - unit * 4.2f, left + len + unit * 3.5f, base + unit * 1.2f, bg)
        val tick = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; strokeWidth = max(1f, unit * 0.18f) }
        c.drawLine(left, base, left + len, base, tick)
        val pxPerMm = pxPerCm / 10f
        val showMm = pxPerMm >= 3f
        val steps = if (showMm) cm * 10 else cm
        for (i in 0..steps) {
            val x = left + i * (if (showMm) pxPerMm else pxPerCm)
            val major = !showMm || i % 10 == 0
            val half = showMm && i % 5 == 0 && !major
            val th = when {
                major -> tall
                half -> tall * 0.65f
                else -> tall * 0.4f
            }
            c.drawLine(x, base, x, base - th, tick)
        }
        val t = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = unit * 2.4f }
        c.drawText("0", left - unit * 0.6f, base - tall - unit * 0.8f, t)
        c.drawText(if (estimated) "ca. $cm cm" else "$cm cm", left + len - unit * (if (estimated) 5f else 2f), base - tall - unit * 0.8f, t)
    }

    private fun scaleTo(src: Bitmap, maxEdge: Int): Bitmap {
        val longEdge = max(src.width, src.height)
        if (longEdge <= maxEdge) return src
        val f = maxEdge.toFloat() / longEdge
        return Bitmap.createScaledBitmap(src, (src.width * f).roundToInt(), (src.height * f).roundToInt(), true)
    }

    private fun writeJpeg(bmp: Bitmap, out: File) {
        FileOutputStream(out).use {
            if (!bmp.compress(Bitmap.CompressFormat.JPEG, 90, it)) throw IOException("Bild konnte nicht gespeichert werden")
        }
    }
}
