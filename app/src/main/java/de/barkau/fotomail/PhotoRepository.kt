package de.barkau.fotomail

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat

data class Photo(val id: Long, val uri: Uri, val name: String, val takenMs: Long)

/**
 * Liest Fotos aus der Galerie. Berücksichtigt werden nur Bilder aus dem
 * Kamera-Ordner (DCIM), keine Screenshots und keine heruntergeladenen Bilder.
 */
object PhotoRepository {

    private val collection: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

    fun photoPermission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, photoPermission()) ==
                PackageManager.PERMISSION_GRANTED

    @Suppress("DEPRECATION")
    private fun cameraSelection(): Pair<String, Array<String>> =
        if (Build.VERSION.SDK_INT >= 29) {
            val col = MediaStore.Images.Media.RELATIVE_PATH
            "$col LIKE ? AND $col NOT LIKE ?" to arrayOf("DCIM/%", "%Screenshots%")
        } else {
            val col = MediaStore.Images.Media.DATA
            "$col LIKE ? AND $col NOT LIKE ?" to arrayOf("%/DCIM/%", "%Screenshots%")
        }

    /** Höchste vorhandene Bild-ID – Startpunkt, damit alte Fotos nicht verschickt werden. */
    fun maxId(context: Context): Long {
        context.contentResolver.query(
            collection,
            arrayOf(MediaStore.Images.Media._ID),
            null, null,
            "${MediaStore.Images.Media._ID} DESC"
        )?.use { c ->
            if (c.moveToFirst()) return c.getLong(0)
        }
        return 0L
    }

    fun newPhotos(context: Context, afterId: Long, limit: Int): List<Photo> {
        val (sel, args) = cameraSelection()
        val selection = "$sel AND ${MediaStore.Images.Media._ID} > ?"
        val selectionArgs = args + afterId.toString()
        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.DATE_ADDED
        )
        val result = mutableListOf<Photo>()
        context.contentResolver.query(
            collection, projection, selection, selectionArgs,
            "${MediaStore.Images.Media._ID} ASC"
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val iTaken = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val iAdded = c.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            while (c.moveToNext() && result.size < limit) {
                val id = c.getLong(iId)
                val taken = if (c.isNull(iTaken) || c.getLong(iTaken) <= 0L)
                    c.getLong(iAdded) * 1000L else c.getLong(iTaken)
                result += Photo(
                    id = id,
                    uri = ContentUris.withAppendedId(collection, id),
                    name = c.getString(iName) ?: "Foto_$id",
                    takenMs = taken
                )
            }
        }
        return result
    }

    fun countNew(context: Context, afterId: Long): Int =
        if (afterId < 0) 0 else newPhotos(context, afterId, 500).size
}
