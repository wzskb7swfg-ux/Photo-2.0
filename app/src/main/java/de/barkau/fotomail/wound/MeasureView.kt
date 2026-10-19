package de.barkau.fotomail.wound

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.hypot
import kotlin.math.min

/**
 * Zeigt das Wundfoto und lässt drei Strecken setzen:
 * Kalibrierung (bekannte Länge auf dem Messstreifen), Länge und Breite der Wunde.
 * Alle Punkte werden in Bild-Pixeln gespeichert. Zwei Finger: zoomen/verschieben.
 * Beim Ziehen eines Punktes erscheint eine Lupe.
 */
class MeasureView(context: Context, val bitmap: Bitmap) : View(context) {

    enum class Step { CAL, LEN, WID, DONE }

    class Line(val a: PointF, val b: PointF) {
        fun length(): Float = hypot(b.x - a.x, b.y - a.y)
    }

    var step: Step = Step.CAL
        set(v) {
            field = v
            ensureLine(v)
            invalidate()
        }

    var cal: Line? = null
    var len: Line? = null
    var wid: Line? = null

    /** Bekannte Länge der Kalibrierstrecke in cm (0 = keine Messung). */
    var calCm: Float = 0f
        set(v) { field = v; invalidate(); onChange?.invoke() }

    var onChange: (() -> Unit)? = null

    /** Schritte, in denen mindestens ein Punkt bewegt wurde (Schutz vor ungeprüften Vorgabewerten). */
    val touched = mutableSetOf<Step>()

    // --- Ansicht
    private val base = Matrix()
    private val view = Matrix()
    private val inverse = Matrix()
    private var zoom = 1f
    private var panX = 0f
    private var panY = 0f
    private var baseScale = 1f
    private var baseDx = 0f
    private var baseDy = 0f

    // --- Ziehen
    private var dragPoint: PointF? = null
    private var lastX = 0f
    private var lastY = 0f
    private var panning = false

    private val dp = context.resources.displayMetrics.density
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG)
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 0, 0, 0); style = Paint.Style.STROKE; strokeWidth = 5f * dp
        strokeCap = Paint.Cap.ROUND
    }
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2.5f * dp; strokeCap = Paint.Cap.ROUND
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textSize = 15f * dp; isFakeBoldText = true
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }
    private val loupeBorder = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; style = Paint.Style.STROKE; strokeWidth = 3f * dp
    }
    private val cross = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED; style = Paint.Style.STROKE; strokeWidth = 1.5f * dp
    }
    private val loupePath = Path()

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(d: ScaleGestureDetector): Boolean {
            val newZoom = (zoom * d.scaleFactor).coerceIn(1f, 8f)
            val f = newZoom / zoom
            // um den Fingermittelpunkt zoomen
            panX = d.focusX - f * (d.focusX - panX)
            panY = d.focusY - f * (d.focusY - panY)
            zoom = newZoom
            updateMatrix()
            return true
        }
    })

    init {
        setBackgroundColor(Color.BLACK)
    }

    companion object {
        val COLOR_CAL = Color.rgb(255, 255, 255)
        val COLOR_LEN = Color.rgb(0, 200, 255)
        val COLOR_WID = Color.rgb(255, 212, 0)
    }

    // ------------------------------------------------------------------ Messwerte

    /** Maßstab aus der Schätzung über den Kameraabstand (0 = nicht verwendet). */
    var fixedPxPerCm: Float = 0f

    fun pxPerCm(): Float {
        if (fixedPxPerCm > 0f) return fixedPxPerCm
        val c = cal ?: return 0f
        if (calCm <= 0f) return 0f
        val l = c.length()
        return if (l > 1f) l / calCm else 0f
    }

    fun lengthCm(): Float {
        val p = pxPerCm(); val l = len ?: return 0f
        return if (p > 0f) l.length() / p else 0f
    }

    fun widthCm(): Float {
        val p = pxPerCm(); val w = wid ?: return 0f
        return if (p > 0f) w.length() / p else 0f
    }

    private fun ensureLine(s: Step) {
        val w = bitmap.width.toFloat()
        val h = bitmap.height.toFloat()
        val d = min(w, h) * 0.18f
        when (s) {
            Step.CAL -> if (cal == null) cal = Line(PointF(w / 2 - d, h * 0.78f), PointF(w / 2 + d, h * 0.78f))
            Step.LEN -> if (len == null) len = Line(PointF(w / 2, h / 2 - d), PointF(w / 2, h / 2 + d))
            Step.WID -> if (wid == null) wid = Line(PointF(w / 2 - d * 0.7f, h / 2), PointF(w / 2 + d * 0.7f, h / 2))
            Step.DONE -> {}
        }
        onChange?.invoke()
    }

    private fun activeLine(): Line? = when (step) {
        Step.CAL -> cal
        Step.LEN -> len
        Step.WID -> wid
        Step.DONE -> null
    }

    // ------------------------------------------------------------------ Geometrie

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        baseScale = min(w.toFloat() / bitmap.width, h.toFloat() / bitmap.height)
        baseDx = (w - bitmap.width * baseScale) / 2f
        baseDy = (h - bitmap.height * baseScale) / 2f
        zoom = 1f; panX = 0f; panY = 0f
        updateMatrix()
    }

    private fun updateMatrix() {
        base.setScale(baseScale, baseScale)
        base.postTranslate(baseDx, baseDy)
        view.set(base)
        view.postScale(zoom, zoom)
        view.postTranslate(panX, panY)
        clampPan()
        view.invert(inverse)
        invalidate()
    }

    private fun clampPan() {
        // Bild nicht ganz aus dem Sichtbereich schieben
        val pts = floatArrayOf(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat())
        view.mapPoints(pts)
        var dx = 0f; var dy = 0f
        val margin = 40f * dp
        if (pts[2] < margin) dx = margin - pts[2]
        if (pts[0] > width - margin) dx = width - margin - pts[0]
        if (pts[3] < margin) dy = margin - pts[3]
        if (pts[1] > height - margin) dy = height - margin - pts[1]
        if (dx != 0f || dy != 0f) {
            panX += dx; panY += dy
            view.postTranslate(dx, dy)
        }
    }

    private fun toView(p: PointF): PointF {
        val a = floatArrayOf(p.x, p.y); view.mapPoints(a); return PointF(a[0], a[1])
    }

    private fun currentScale(): Float = baseScale * zoom

    // ------------------------------------------------------------------ Zeichnen

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawBitmap(bitmap, view, bmpPaint)

        if (fixedPxPerCm <= 0f) cal?.let { drawLine(canvas, it, COLOR_CAL, step == Step.CAL, calLabel()) }
        len?.let { drawLine(canvas, it, COLOR_LEN, step == Step.LEN, cmLabel("L", lengthCm())) }
        wid?.let { drawLine(canvas, it, COLOR_WID, step == Step.WID, cmLabel("B", widthCm())) }

        dragPoint?.let { drawLoupe(canvas, it) }
    }

    private fun calLabel(): String =
        if (calCm > 0f) "Maßstab ${fmt(calCm)} cm" else "Maßstab"

    private fun cmLabel(prefix: String, v: Float): String =
        if (v > 0f) "$prefix ${fmt(v)} cm" else prefix

    private fun fmt(v: Float): String = String.format(java.util.Locale.GERMANY, "%.1f", v)

    private fun drawLine(canvas: Canvas, line: Line, color: Int, active: Boolean, text: String) {
        val a = toView(line.a); val b = toView(line.b)
        canvas.drawLine(a.x, a.y, b.x, b.y, outline)
        stroke.color = color
        stroke.alpha = if (active || step == Step.DONE) 255 else 170
        canvas.drawLine(a.x, a.y, b.x, b.y, stroke)
        val r = (if (active) 13f else 7f) * dp
        for (p in listOf(a, b)) {
            canvas.drawCircle(p.x, p.y, r, outline)
            canvas.drawCircle(p.x, p.y, r, stroke)
            fill.color = color
            canvas.drawCircle(p.x, p.y, 2.5f * dp, fill)
        }
        val mx = (a.x + b.x) / 2 + 10f * dp
        val my = (a.y + b.y) / 2 - 10f * dp
        textPaint.color = color
        canvas.drawText(text, mx, my, textPaint)
    }

    private fun drawLoupe(canvas: Canvas, p: PointF) {
        val r = 62f * dp
        val vp = toView(p)
        // Lupe in die Ecke, die vom Finger weg liegt
        val cx = if (vp.x < width / 2f) width - r - 12f * dp else r + 12f * dp
        val cy = r + 12f * dp
        val mag = currentScale() * 3f
        canvas.save()
        loupePath.reset()
        loupePath.addCircle(cx, cy, r, Path.Direction.CW)
        canvas.clipPath(loupePath)
        canvas.drawColor(Color.BLACK)
        val m = Matrix()
        m.setScale(mag, mag)
        m.postTranslate(cx - p.x * mag, cy - p.y * mag)
        canvas.drawBitmap(bitmap, m, bmpPaint)
        canvas.restore()
        canvas.drawCircle(cx, cy, r, loupeBorder)
        val c = 10f * dp
        canvas.drawLine(cx - c, cy, cx + c, cy, cross)
        canvas.drawLine(cx, cy - c, cx, cy + c, cross)
    }

    // ------------------------------------------------------------------ Bedienung

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y
                dragPoint = hitHandle(e.x, e.y)
                panning = dragPoint == null
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                // zweiter Finger: Ziehen abbrechen, nur zoomen/verschieben
                dragPoint = null
                panning = true
                lastX = midX(e); lastY = midY(e)
                invalidate()
            }
            MotionEvent.ACTION_MOVE -> {
                val x = if (e.pointerCount > 1) midX(e) else e.x
                val y = if (e.pointerCount > 1) midY(e) else e.y
                val dx = x - lastX
                val dy = y - lastY
                lastX = x; lastY = y
                val dp0 = dragPoint
                if (dp0 != null && e.pointerCount == 1) {
                    // relativ verschieben – Punkt bleibt sichtbar, Finger verdeckt ihn nicht
                    val s = currentScale()
                    dp0.x = (dp0.x + dx / s).coerceIn(0f, bitmap.width - 1f)
                    dp0.y = (dp0.y + dy / s).coerceIn(0f, bitmap.height - 1f)
                    touched += step
                    onChange?.invoke()
                    invalidate()
                } else if (panning && !scaleDetector.isInProgress || e.pointerCount > 1) {
                    if (zoom > 1f || e.pointerCount > 1) {
                        panX += dx; panY += dy
                        updateMatrix()
                    }
                }
            }
            MotionEvent.ACTION_POINTER_UP -> {
                // verbleibenden Finger als neuen Ausgangspunkt nehmen
                val idx = if (e.actionIndex == 0) 1 else 0
                lastX = e.getX(idx); lastY = e.getY(idx)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragPoint = null
                panning = false
                invalidate()
            }
        }
        return true
    }

    private fun midX(e: MotionEvent) = (e.getX(0) + e.getX(1)) / 2f
    private fun midY(e: MotionEvent) = (e.getY(0) + e.getY(1)) / 2f

    private fun hitHandle(x: Float, y: Float): PointF? {
        val line = activeLine() ?: return null
        val slop = 44f * dp
        var best: PointF? = null
        var bestD = Float.MAX_VALUE
        for (p in listOf(line.a, line.b)) {
            val v = toView(p)
            val d = hypot(v.x - x, v.y - y)
            if (d < slop && d < bestD) { best = p; bestD = d }
        }
        return best
    }

    /** Zoom zurücksetzen. */
    fun resetZoom() {
        zoom = 1f; panX = 0f; panY = 0f
        updateMatrix()
    }
}
