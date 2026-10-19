package de.barkau.fotomail.wound

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.camera2.interop.Camera2CameraInfo
import androidx.camera.camera2.interop.Camera2Interop
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import java.io.File
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Kamera für Wundfotos: Hilfsrahmen, Neigungsanzeige und (optional) das letzte Foto
 * derselben Wunde halbtransparent als Vorlage. Das Foto landet nur im App-Speicher.
 */
class WoundCameraActivity : ComponentActivity(), SensorEventListener {

    private lateinit var previewView: PreviewView
    private lateinit var overlay: GuideOverlay
    private lateinit var shutter: TextView
    private lateinit var torchBtn: TextView
    private lateinit var ghostBtn: TextView

    private var imageCapture: ImageCapture? = null
    private var camera: Camera? = null
    private var torchOn = false
    private var busy = false

    private var sensorManager: SensorManager? = null

    // Für die grobe Schätzung ohne Messstreifen (Kameraabstand über den Autofokus)
    @Volatile
    private var focusDiopters = -1f
    @Volatile
    private var focalMm = 0f
    private var sensorLongMm = 0f
    @Volatile
    private var focusCalibration = -1

    private val askCamera = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startCamera()
        } else {
            Toast.makeText(this, "Ohne Kamera-Zugriff können keine Wundfotos gemacht werden.", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private lateinit var pseudonym: String
    private lateinit var location: String
    private var woundNo = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pseudonym = intent.getStringExtra(WoundActivity.EXTRA_PSEUDONYM) ?: run { finish(); return }
        location = intent.getStringExtra(WoundActivity.EXTRA_LOCATION) ?: ""
        woundNo = intent.getIntExtra(WoundActivity.EXTRA_WOUND_NO, 1)
        val useGhost = intent.getBooleanExtra(WoundActivity.EXTRA_GHOST, true)

        // Wundbild nicht in Screenshots oder der App-Übersicht zeigen
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        val root = FrameLayout(this)
        root.setBackgroundColor(Color.BLACK)

        previewView = PreviewView(this).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
        root.addView(previewView, FrameLayout.LayoutParams(MATCH, MATCH))

        overlay = GuideOverlay(this)
        if (useGhost) overlay.ghost = WoundStore.loadGhost(this, pseudonym, location, woundNo)
        root.addView(overlay, FrameLayout.LayoutParams(MATCH, MATCH))

        // Kopfzeile
        val top = Ui.vbox(this).apply {
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            val p = Ui.dp(this@WoundCameraActivity, 12f)
            setPadding(p, p, p, p)
        }
        top.addView(Ui.text(this, "$pseudonym · $location · Wunde $woundNo", 16f, Color.WHITE, true))
        top.addView(
            Ui.text(
                this, "Messstreifen neben die Wunde legen · Kamera parallel zur Wundfläche halten",
                13f, Color.argb(220, 255, 255, 255)
            )
        )
        root.addView(top, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.TOP))

        // Bedienleiste
        val bar = Ui.hbox(this).apply {
            setBackgroundColor(Color.argb(150, 0, 0, 0))
            gravity = Gravity.CENTER
            val p = Ui.dp(this@WoundCameraActivity, 14f)
            setPadding(p, p, p, p + Ui.dp(this@WoundCameraActivity, 10f))
        }
        torchBtn = roundLabel("Licht\naus") { toggleTorch() }
        ghostBtn = roundLabel(if (overlay.ghost != null) "Vorlage\nan" else "Vorlage\n–") { toggleGhost() }
        shutter = TextView(this).apply {
            background = Ui.rounded(this@WoundCameraActivity, Color.WHITE, Ui.ACCENT, 40f)
            setOnClickListener { takePhoto() }
            contentDescription = "Foto aufnehmen"
        }
        val side = Ui.dp(this, 72f)
        bar.addView(torchBtn, LinearLayout.LayoutParams(0, WRAP, 1f))
        bar.addView(shutter, LinearLayout.LayoutParams(side, side))
        bar.addView(ghostBtn, LinearLayout.LayoutParams(0, WRAP, 1f))
        root.addView(bar, FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM))

        setContentView(root)
        applySystemBarPadding(root, top, bar)

        sensorManager = getSystemService(Context.SENSOR_SERVICE) as? SensorManager

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            askCamera.launch(Manifest.permission.CAMERA)
        }
    }

    override fun onResume() {
        super.onResume()
        busy = false
        sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager?.unregisterListener(this)
    }

    private fun roundLabel(s: String, onClick: () -> Unit): TextView =
        Ui.text(this, s, 13f, Color.WHITE, true).apply {
            gravity = Gravity.CENTER
            setOnClickListener { onClick() }
        }

    @androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            try {
                val provider = future.get()
                val previewBuilder = Preview.Builder()
                Camera2Interop.Extender(previewBuilder).setSessionCaptureCallback(
                    object : CameraCaptureSession.CaptureCallback() {
                        override fun onCaptureCompleted(
                            session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult
                        ) {
                            result.get(CaptureResult.LENS_FOCAL_LENGTH)?.let { if (it > 0f) focalMm = it }
                            val d = result.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: return
                            focusDiopters = d
                            if (focusCalibration > 0 && d > 0.3f) {
                                val cm = (100f / d).toInt()
                                if (cm != overlay.distanceCm) {
                                    overlay.distanceCm = cm
                                    overlay.postInvalidate()
                                }
                            }
                        }
                    })
                val preview = previewBuilder.build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val capture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                    .setFlashMode(ImageCapture.FLASH_MODE_OFF)
                    .build()
                provider.unbindAll()
                val cam = provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, capture)
                camera = cam
                imageCapture = capture
                readOptics(cam)
            } catch (e: Exception) {
                Toast.makeText(this, "Kamera konnte nicht gestartet werden: ${e.message}", Toast.LENGTH_LONG).show()
                finish()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    /** Brennweite, Sensorgröße und ob der Autofokus echte Abstände liefert. */
    @androidx.annotation.OptIn(markerClass = [ExperimentalCamera2Interop::class])
    private fun readOptics(cam: Camera) {
        try {
            val info = Camera2CameraInfo.from(cam.cameraInfo)
            focusCalibration = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_FOCUS_DISTANCE_CALIBRATION) ?: -1
            if (focalMm <= 0f) {
                focalMm = info.getCameraCharacteristic(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)?.firstOrNull() ?: 0f
            }
            val size = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PHYSICAL_SIZE)
            val pixel = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE)
            val active = info.getCameraCharacteristic(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
            var long = if (size != null) max(size.width, size.height) else 0f
            // nur die aktive Sensorfläche wird für das Bild genutzt
            if (pixel != null && active != null) {
                val pl = max(pixel.width, pixel.height).toFloat()
                val al = max(active.width(), active.height()).toFloat()
                if (pl > 0f && al > 0f && al <= pl) long *= al / pl
            }
            sensorLongMm = long
        } catch (e: Exception) {
            focusCalibration = -1
        }
    }

    private fun toggleTorch() {
        val cam = camera ?: return
        if (!cam.cameraInfo.hasFlashUnit()) {
            Toast.makeText(this, "Dieses Handy hat kein Licht an der Kamera.", Toast.LENGTH_SHORT).show()
            return
        }
        torchOn = !torchOn
        cam.cameraControl.enableTorch(torchOn)
        torchBtn.text = if (torchOn) "Licht\nan" else "Licht\naus"
    }

    private fun toggleGhost() {
        if (overlay.ghost == null) {
            Toast.makeText(this, "Für diese Wunde gibt es noch kein vorheriges Foto.", Toast.LENGTH_SHORT).show()
            return
        }
        overlay.showGhost = !overlay.showGhost
        ghostBtn.text = if (overlay.showGhost) "Vorlage\nan" else "Vorlage\naus"
        overlay.invalidate()
    }

    private fun takePhoto() {
        val capture = imageCapture ?: return
        if (busy) return
        busy = true
        shutter.alpha = 0.4f
        val dir = File(filesDir, "aufnahme").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "wunde_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        val diopters = focusDiopters
        capture.takePicture(options, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    shutter.alpha = 1f
                    if (torchOn) toggleTorch()
                    startActivity(
                        Intent(this@WoundCameraActivity, MeasureActivity::class.java)
                            .putExtras(intent)
                            .putExtra(WoundActivity.EXTRA_PHOTO, file.absolutePath)
                            .putExtra(WoundActivity.EXTRA_TAKEN_AT, System.currentTimeMillis())
                            .putExtra(WoundActivity.EXTRA_FOCUS_DIOPTERS, diopters)
                            .putExtra(WoundActivity.EXTRA_FOCUS_CAL, focusCalibration)
                            .putExtra(WoundActivity.EXTRA_FOCAL_MM, focalMm)
                            .putExtra(WoundActivity.EXTRA_SENSOR_MM, sensorLongMm)
                    )
                    finish()
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    shutter.alpha = 1f
                    file.delete()
                    Toast.makeText(
                        this@WoundCameraActivity,
                        "Foto fehlgeschlagen: ${exception.message}", Toast.LENGTH_LONG
                    ).show()
                }
            })
    }

    // Neigung gegenüber der Waagerechten – hilft, bei Folgefotos den gleichen Winkel zu treffen
    override fun onSensorChanged(event: SensorEvent) {
        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val g = sqrt(x * x + y * y + z * z)
        if (g < 1f) return
        val tilt = Math.toDegrees(atan2(sqrt(x * x + y * y).toDouble(), abs(z).toDouble())).toFloat()
        overlay.tiltDegrees = tilt
        overlay.invalidate()
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
        private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    }
}

/** Zeichnet Hilfsrahmen, Fadenkreuz, Neigungsanzeige und die halbtransparente Vorlage. */
class GuideOverlay(context: Context) : View(context) {

    var ghost: Bitmap? = null
    var showGhost = true
    var tiltDegrees = -1f
    /** Ungefährer Abstand zur Wunde in cm (nur wenn die Kamera ihn liefert), -1 = unbekannt. */
    @Volatile
    var distanceCm = -1

    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 1.5f).toFloat()
        pathEffect = DashPathEffect(floatArrayOf(Ui.dp(context, 8f).toFloat(), Ui.dp(context, 6f).toFloat()), 0f)
    }
    private val solid = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = Ui.dp(context, 3f).toFloat()
    }
    private val ghostPaint = Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 95 }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = Ui.dp(context, 14f).toFloat()
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 0f, 0f, Color.BLACK)
    }
    private val src = Rect()
    private val dst = RectF()

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()

        // Vorlage: wie die Vorschau mittig beschnitten (FILL_CENTER)
        val g = ghost
        if (g != null && showGhost) {
            val scale = max(w / g.width, h / g.height)
            val sw = (w / scale).toInt()
            val sh = (h / scale).toInt()
            val sx = (g.width - sw) / 2
            val sy = (g.height - sh) / 2
            src.set(sx, sy, sx + sw, sy + sh)
            dst.set(0f, 0f, w, h)
            canvas.drawBitmap(g, src, dst, ghostPaint)
        }

        // Hilfsrahmen (mittleres Drittel) + Fadenkreuz
        val fw = w * 0.62f
        val fh = fw
        val l = (w - fw) / 2
        val t = (h - fh) / 2
        canvas.drawRect(l, t, l + fw, t + fh, line)
        val c = Ui.dp(context, 14f).toFloat()
        canvas.drawLine(w / 2 - c, h / 2, w / 2 + c, h / 2, line)
        canvas.drawLine(w / 2, h / 2 - c, w / 2, h / 2 + c, line)

        // Neigungsanzeige
        if (tiltDegrees >= 0f) {
            // Nur Information: Bei liegenden Klienten soll das Handy flach sein,
            // bei Wunden an senkrechten Flächen (z. B. Seitenlage) dagegen nicht.
            val ok = tiltDegrees <= 8f
            solid.color = if (ok) Color.rgb(115, 200, 107) else Color.argb(220, 255, 255, 255)
            // bewusst ohne Haken: „waagerecht“ ist nicht immer richtig (z. B. Seitenlage)
            val r = Ui.dp(context, 26f).toFloat()
            val cy = t + fh + Ui.dp(context, 44f)
            canvas.drawCircle(w / 2, cy, r, solid)
            // Punkt wandert mit der Neigung
            val off = (tiltDegrees.coerceAtMost(30f) / 30f) * r
            solid.style = Paint.Style.FILL
            canvas.drawCircle(w / 2, cy - off, Ui.dp(context, 6f).toFloat(), solid)
            solid.style = Paint.Style.STROKE
            val txt = if (ok) "waagerecht" else "Neigung ${tiltDegrees.toInt()}°"
            canvas.drawText(txt, w / 2, cy + r + Ui.dp(context, 20f), label)
        }

        // Abstand (für gleichbleibende Folgefotos, ideal 20–30 cm)
        val d = distanceCm
        if (d in 5..150) {
            canvas.drawText("Abstand ca. $d cm", w / 2, t - Ui.dp(context, 12f), label)
        }
    }
}
