package de.barkau.fotomail

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Erzeugt aus einem Foto eine neue, saubere JPEG-Datei.
 * Durch das Neu-Kodieren gehen ALLE eingebetteten Metadaten verloren
 * (GPS-Standort, Gerätemodell, Seriennummern, Original-Zeitstempel).
 * Die Ausrichtung wird vorher in die Pixel übernommen.
 */
object ImageSanitizer {

    private val nameFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.GERMANY)

    fun toCleanJpeg(context: Context, photo: Photo, outDir: File, maxEdge: Int): File {
        val cr = context.contentResolver

        // Nur die Ausrichtung wird gelesen. Schlägt das fehl (z. B. bei HEIC),
        // wird das Bild trotzdem verarbeitet statt verworfen.
        val orientation = try {
            cr.openInputStream(photo.uri)?.use {
                ExifInterface(it).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        } catch (ignored: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(photo.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
            throw IOException("Bild nicht lesbar: ${photo.name}")
        }

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxEdge) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        var bmp: Bitmap = cr.openInputStream(photo.uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        } ?: throw IOException("Bild nicht lesbar: ${photo.name}")

        val longEdge = max(bmp.width, bmp.height)
        if (longEdge > maxEdge) {
            val f = maxEdge.toFloat() / longEdge
            val scaled = Bitmap.createScaledBitmap(
                bmp, (bmp.width * f).roundToInt(), (bmp.height * f).roundToInt(), true
            )
            if (scaled !== bmp) bmp.recycle()
            bmp = scaled
        }

        val matrix = orientationMatrix(orientation)
        if (matrix != null) {
            val rotated = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, matrix, true)
            if (rotated !== bmp) bmp.recycle()
            bmp = rotated
        }

        val base = "Foto_" + nameFormat.format(Date(photo.takenMs))
        var out = File(outDir, "$base.jpg")
        var n = 2
        while (out.exists()) out = File(outDir, "${base}_${n++}.jpg")

        try {
            FileOutputStream(out).use { fos ->
                if (!bmp.compress(Bitmap.CompressFormat.JPEG, 85, fos)) {
                    throw IOException("Bild konnte nicht gespeichert werden: ${photo.name}")
                }
            }
        } finally {
            bmp.recycle()
        }
        return out
    }

    private fun orientationMatrix(orientation: Int): Matrix? {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.setScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.setRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> { m.setRotate(180f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.setRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_90 -> m.setRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.setRotate(-90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_ROTATE_270 -> m.setRotate(-90f)
            else -> return null
        }
        return m
    }
}
