package de.barkau.fotomail.wound

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Pseudonyme, Lokalisationen und Vergleichsbilder für die Wundfotos.
 * Alles liegt nur im geschützten App-Speicher dieses Handys – nicht in der Galerie.
 *
 * Wichtig: Die Zuordnung Pseudonym → Klient steht NICHT in der App,
 * sondern nur in der Liste im Büro.
 */
object WoundStore {

    private const val PREFS = "wound"
    private const val KEY_PSEUDONYMS = "pseudonyms"
    private const val KEY_LAST_PSEUDONYM = "last_pseudonym"
    private const val KEY_GHOST = "ghost_enabled"
    private const val KEY_SEND_ORIGINAL = "send_original"

    /**
     * Frei wählbar (eigene Pseudonym-Liste des Büros): 1–20 Zeichen,
     * Buchstaben, Ziffern, Leerzeichen, Bindestrich, Unterstrich, Punkt.
     */
    private val PSEUDONYM = Regex("^[\\p{L}0-9 _.-]{1,20}$")

    /** Vergleichsbilder werden nach so vielen Tagen automatisch gelöscht. */
    const val GHOST_DAYS = 90

    val LOCATIONS = listOf(
        "Sakrum", "Steiß", "Gesäß links", "Gesäß rechts",
        "Sitzbein links", "Sitzbein rechts", "Trochanter links", "Trochanter rechts",
        "Ferse links", "Ferse rechts", "Knöchel links", "Knöchel rechts",
        "Unterschenkel links", "Unterschenkel rechts", "Fußrücken links", "Fußrücken rechts",
        "Fußsohle links", "Fußsohle rechts", "Zehe(n) links", "Zehe(n) rechts",
        "Knie links", "Knie rechts", "Ellenbogen links", "Ellenbogen rechts",
        "Schulterblatt links", "Schulterblatt rechts", "Wirbelsäule", "Hinterkopf", "Ohr",
        "Bauch", "Brust", "Rücken", "Arm links", "Arm rechts", "Hand links", "Hand rechts",
        "Leiste links", "Leiste rechts", "Sonstige"
    )

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    // ------------------------------------------------------------ Pseudonyme

    fun normalize(p: String): String = p.trim().replace(Regex("\\s+"), " ")

    fun isValidPseudonym(p: String): Boolean {
        val n = normalize(p)
        return PSEUDONYM.matches(n) && n.any { it.isLetterOrDigit() }
    }

    /** Ist schon ein gleiches oder zum Verwechseln ähnliches Pseudonym vorhanden (z. B. „w-017“ / „W 017“)? */
    private fun collides(a: String, b: String): Boolean =
        a.equals(b, ignoreCase = true) || safe(a).equals(safe(b), ignoreCase = true)

    fun pseudonyms(ctx: Context): List<String> =
        (prefs(ctx).getString(KEY_PSEUDONYMS, "") ?: "")
            .split("\n").map { it.trim() }.filter { it.isNotEmpty() }.distinct().sorted()

    fun addPseudonym(ctx: Context, p: String) = addPseudonyms(ctx, listOf(p))

    /** Mehrere auf einmal (z. B. die Liste aus dem Büro einfügen). Gibt die übernommenen zurück. */
    fun addPseudonyms(ctx: Context, items: List<String>): List<String> {
        val existing = pseudonyms(ctx).toMutableList()
        val added = mutableListOf<String>()
        for (raw in items) {
            val n = normalize(raw)
            if (!isValidPseudonym(n)) continue
            if (existing.any { collides(it, n) }) continue
            existing += n
            added += n
        }
        prefs(ctx).edit().putString(KEY_PSEUDONYMS, existing.sorted().joinToString("\n")).apply()
        return added
    }

    fun removePseudonym(ctx: Context, p: String) {
        val list = pseudonyms(ctx).filter { it != p }
        prefs(ctx).edit().putString(KEY_PSEUDONYMS, list.joinToString("\n")).apply()
        // Vergleichsbilder dieses Pseudonyms mit entfernen
        ghostDir(ctx).listFiles()?.filter { it.name.startsWith("v2_" + key(p) + "_") }?.forEach { it.delete() }
    }

    fun lastPseudonym(ctx: Context): String = prefs(ctx).getString(KEY_LAST_PSEUDONYM, "") ?: ""

    fun setLastPseudonym(ctx: Context, v: String) =
        prefs(ctx).edit().putString(KEY_LAST_PSEUDONYM, v).apply()

    fun lastLocation(ctx: Context, pseudonym: String): String =
        prefs(ctx).getString("loc_" + pseudonym, "") ?: ""

    fun setLastLocation(ctx: Context, pseudonym: String, location: String) =
        prefs(ctx).edit().putString("loc_" + pseudonym, location).apply()

    // ------------------------------------------------------------ Optionen

    fun ghostEnabled(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_GHOST, true)
    fun setGhostEnabled(ctx: Context, v: Boolean) = prefs(ctx).edit().putBoolean(KEY_GHOST, v).apply()

    fun sendOriginal(ctx: Context): Boolean = prefs(ctx).getBoolean(KEY_SEND_ORIGINAL, true)
    fun setSendOriginal(ctx: Context, v: Boolean) =
        prefs(ctx).edit().putBoolean(KEY_SEND_ORIGINAL, v).apply()

    // ------------------------------------------------------------ Vergleichsbild

    /** Dateiname-tauglich: nur Buchstaben, Ziffern, Bindestrich. */
    fun safe(s: String): String =
        s.replace("ä", "ae").replace("ö", "oe").replace("ü", "ue").replace("ß", "ss")
            .replace("Ä", "Ae").replace("Ö", "Oe").replace("Ü", "Ue")
            .replace(Regex("[^A-Za-z0-9-]+"), "-").trim('-')

    private fun ghostDir(ctx: Context): File = File(ctx.filesDir, "vergleich").apply { mkdirs() }

    /** Eindeutiger, nicht lesbarer Schlüssel je Pseudonym für Dateinamen (keine Verwechslung ähnlicher Namen). */
    private fun key(p: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(p.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(16)

    private fun ghostFile(ctx: Context, pseudonym: String, location: String, woundNo: Int): File =
        File(ghostDir(ctx), "v2_${key(pseudonym)}_${safe(location)}_$woundNo.jpg")

    fun loadGhost(ctx: Context, pseudonym: String, location: String, woundNo: Int): Bitmap? {
        val f = ghostFile(ctx, pseudonym, location, woundNo)
        if (!f.exists()) return null
        return try {
            BitmapFactory.decodeFile(f.absolutePath)
        } catch (e: OutOfMemoryError) {
            null
        }
    }

    fun hasGhost(ctx: Context, pseudonym: String, location: String, woundNo: Int): Boolean =
        ghostFile(ctx, pseudonym, location, woundNo).exists()

    /** Speichert eine verkleinerte Kopie (max. 1080 px) als Vorlage für die nächste Aufnahme. */
    fun saveGhost(ctx: Context, pseudonym: String, location: String, woundNo: Int, photo: Bitmap) {
        val longEdge = max(photo.width, photo.height)
        val f = if (longEdge > 1080) 1080f / longEdge else 1f
        val small = if (f < 1f) Bitmap.createScaledBitmap(
            photo, (photo.width * f).roundToInt(), (photo.height * f).roundToInt(), true
        ) else photo
        try {
            FileOutputStream(ghostFile(ctx, pseudonym, location, woundNo)).use {
                small.compress(Bitmap.CompressFormat.JPEG, 80, it)
            }
        } finally {
            if (small !== photo) small.recycle()
        }
    }

    /** Löscht Vergleichsbilder, die älter als [GHOST_DAYS] Tage sind. */
    fun purgeOldGhosts(ctx: Context) {
        val limit = System.currentTimeMillis() - GHOST_DAYS * 24L * 60 * 60 * 1000
        ghostDir(ctx).listFiles()?.filter { it.lastModified() < limit || !it.name.startsWith("v2_") }?.forEach { it.delete() }
    }

    /** Reste einer abgebrochenen Aufnahme (z. B. nach Neustart des Handys) entfernen. */
    fun clearCaptures(ctx: Context) {
        File(ctx.filesDir, "aufnahme").listFiles()?.forEach { it.delete() }
    }

    fun deleteAllGhosts(ctx: Context): Int {
        val files = ghostDir(ctx).listFiles() ?: return 0
        files.forEach { it.delete() }
        return files.size
    }

    fun ghostCount(ctx: Context): Int = ghostDir(ctx).listFiles()?.size ?: 0
}
