package org.salawaqt.app

import android.content.Context
import android.content.SharedPreferences
import org.salawaqt.engine.Adjustments
import org.salawaqt.engine.AsrRule
import org.salawaqt.engine.CalculationParams
import org.salawaqt.engine.Coordinates
import org.salawaqt.engine.Method
import org.salawaqt.engine.Prayer
import java.io.File

/** All persistent state: a handful of settings and the last location. Plain SharedPreferences; nothing else. */
class Prefs(context: Context) {
    private val p: SharedPreferences = context.applicationContext.getSharedPreferences("salawaqt", Context.MODE_PRIVATE)
    private val filesDir: File = context.applicationContext.filesDir
    private val app: Context = context.applicationContext

    // ---- calculation settings ----
    var methodId: String
        get() = p.getString("method", Method.MWL.id) ?: Method.MWL.id
        set(v) = p.edit().putString("method", v).apply()
    var customFajrAngle: Float
        get() = p.getFloat("custom_fajr", 18f)
        set(v) = p.edit().putFloat("custom_fajr", v).apply()
    var customIshaAngle: Float
        get() = p.getFloat("custom_isha", 17f)
        set(v) = p.edit().putFloat("custom_isha", v).apply()
    var customIshaInterval: Int
        get() = p.getInt("custom_isha_min", 0)
        set(v) = p.edit().putInt("custom_isha_min", v).apply()
    var hanafiAsr: Boolean
        get() = p.getBoolean("hanafi", false)
        set(v) = p.edit().putBoolean("hanafi", v).apply()

    fun adjustment(prayer: Prayer): Int = p.getInt("adj_${prayer.name}", 0)
    fun setAdjustment(prayer: Prayer, minutes: Int) = p.edit().putInt("adj_${prayer.name}", minutes.coerceIn(-60, 60)).apply()

    /** Show the Asr of the other shadow rule too, as its own row with its own alert. */
    var showBothAsr: Boolean
        get() = p.getBoolean("both_asr", false)
        set(v) = p.edit().putBoolean("both_asr", v).apply()

    // ---- alerts, one choice per event ----
    // Events are keyed by Prayer.name, plus ASR2 for the second Asr when showBothAsr is on.
    companion object {
        const val SILENT = 0; const val NOTIFY = 1; const val ADHAN = 2
        const val ASR2 = "ASR2"
    }

    /** SILENT, NOTIFY (short system sound) or ADHAN (bundled or chosen recording). Sunrise defaults to silent. */
    fun alertMode(key: String): Int {
        val k = "mode_$key"
        if (p.contains(k)) return p.getInt(k, SILENT).coerceIn(0, 2)
        if (key == Prayer.SUNRISE.name) return SILENT
        if (key == ASR2) return alertMode(Prayer.ASR.name)   // the second Asr starts out like the first
        // Versions 0.1/0.2 had two global switches; honour them until the user sets the row.
        return when { p.getBoolean("adhan", false) -> ADHAN; p.getBoolean("notify", true) -> NOTIFY; else -> SILENT }
    }
    fun alertMode(prayer: Prayer): Int = alertMode(prayer.name)
    fun setAlertMode(key: String, mode: Int) = p.edit().putInt("mode_$key", mode.coerceIn(0, 2)).apply()
    fun setAlertMode(prayer: Prayer, mode: Int) = setAlertMode(prayer.name, mode)

    /** All event keys that can alert, in time order. */
    fun eventKeys(): List<String> = buildList {
        for (pr in Prayer.entries) { add(pr.name); if (pr == Prayer.ASR && showBothAsr) add(ASR2) }
    }

    /** True if at least one time alerts audibly or visibly. */
    val notificationsEnabled: Boolean get() = eventKeys().any { alertMode(it) != SILENT }
    val sunriseAudible: Boolean get() = alertMode(Prayer.SUNRISE) != SILENT

    /** A silent, persistent notification in the shade showing the next prayer and today's times. */
    var shadeEnabled: Boolean
        get() = p.getBoolean("shade", true)
        set(v) = p.edit().putBoolean("shade", v).apply()

    // ---- adhan recordings: a small library in files/adhan/lib, and one selection per event ----

    /** One user-added recording: a file in private storage plus the name the user gave it. */
    data class Recording(val id: String, val name: String)

    /**
     * All recordings the user has added, in the order added. Files are copied into the app's private storage, so
     * they keep playing if the original is deleted or lives on a removed SD card.
     */
    fun recordings(): List<Recording> {
        migrateOldRecordings()
        val raw = p.getString("adhan_lib", "") ?: ""
        return raw.split('\n').filter { it.isNotBlank() }.mapNotNull { line ->
            val i = line.indexOf('|'); if (i <= 0) null else Recording(line.substring(0, i), line.substring(i + 1))
        }
    }
    private fun saveRecordings(list: List<Recording>) =
        p.edit().putString("adhan_lib", list.joinToString("\n") { "${it.id}|${it.name.replace('\n', ' ').replace('|', '/')}" }).apply()

    /** Register a file already copied to files/adhan/lib/[id]. */
    fun addRecording(id: String, name: String) = saveRecordings(recordings() + Recording(id, name))
    fun renameRecording(id: String, name: String) = saveRecordings(recordings().map { if (it.id == id) Recording(id, name) else it })
    fun removeRecording(id: String) {
        File(filesDir, "adhan/lib/$id").delete()
        saveRecordings(recordings().filter { it.id != id })
        for (k in Prayer.entries.map { it.name } + ASR2) if (p.getString("adhan_sel_$k", null) == id) setSelection(k, null)
    }

    /** Which recording an event uses: a user Recording id, a Bundled id ("b:…"), or null for the default bundled one. */
    fun selection(key: String): String? = p.getString("adhan_sel_$key", null)
    fun setSelection(key: String, id: String?) = p.edit().putString("adhan_sel_$key", id).apply()

    /** The file to play for [key], or null to use the bundled recording. */
    fun adhanFile(key: String): File? {
        val id = selection(key) ?: return null
        if (id.startsWith("b:")) return null
        return File(filesDir, "adhan/lib/$id").takeIf { it.isFile && it.length() > 0 }
    }
    /** The bundled resource to play for [key] (its chosen one, or the default). */
    fun bundledFor(key: String): Bundled.Recording = Bundled.byId(selection(key)) ?: Bundled.default(key)
    fun adhanFile(prayer: Prayer): File? = adhanFile(prayer.name)
    /** Display name of the recording in use for [key]. */
    fun adhanName(key: String): String {
        val id = selection(key)
        if (id != null && !id.startsWith("b:")) recordings().firstOrNull { it.id == id }?.let { return it.name }
        if (key == Prayer.SUNRISE.name) return "Notification sound"
        return bundledFor(key).label
    }

    /** 0.4 kept one file per prayer in files/adhan/<KEY>; fold those into the library once. */
    private fun migrateOldRecordings() {
        if (p.getBoolean("adhan_lib_migrated", false)) return
        p.edit().putBoolean("adhan_lib_migrated", true).apply()
        val lib = File(filesDir, "adhan/lib").apply { mkdirs() }
        val acc = ArrayList<Recording>()
        for (k in Prayer.entries.map { it.name } + ASR2) {
            val old = File(filesDir, "adhan/$k")
            if (!old.isFile) continue
            val id = "r${System.currentTimeMillis()}_$k"
            if (old.renameTo(File(lib, id))) {
                acc += Recording(id, p.getString("adhan_name_$k", null) ?: k.lowercase().replaceFirstChar { it.uppercase() })
                setSelection(k, id)
            }
            p.edit().remove("adhan_name_$k").apply()
        }
        if (acc.isNotEmpty()) saveRecordings(acc)
    }

    // ---- location ----
    var manualLocation: Boolean
        get() = p.getBoolean("manual", false)
        set(v) = p.edit().putBoolean("manual", v).apply()
    var manualLat: Double
        get() = p.getString("manual_lat", "0")?.toDoubleOrNull() ?: 0.0
        set(v) = p.edit().putString("manual_lat", v.toString()).apply()
    var manualLng: Double
        get() = p.getString("manual_lng", "0")?.toDoubleOrNull() ?: 0.0
        set(v) = p.edit().putString("manual_lng", v.toString()).apply()

    /** Last device fix. Stored as strings to keep full double precision without bit tricks. */
    val hasFix: Boolean get() = p.contains("fix_lat")
    val fixLat: Double get() = p.getString("fix_lat", "0")?.toDoubleOrNull() ?: 0.0
    val fixLng: Double get() = p.getString("fix_lng", "0")?.toDoubleOrNull() ?: 0.0
    val fixAlt: Double get() = p.getString("fix_alt", "0")?.toDoubleOrNull() ?: 0.0
    val fixTime: Long get() = p.getLong("fix_time", 0L)
    fun saveFix(lat: Double, lng: Double, alt: Double, time: Long) =
        p.edit().putString("fix_lat", lat.toString()).putString("fix_lng", lng.toString())
            .putString("fix_alt", alt.toString()).putLong("fix_time", time).apply()

    /** "MAGHRIB|2026-09-21": the last prayer that actually alerted, so a location change cannot fire it twice. */
    var lastFired: String?
        get() = p.getString("last_fired", null)
        set(v) = p.edit().putString("last_fired", v).apply()

    /** Appearance: 0 = follow the phone's dark-mode setting, 1 = light, 2 = dark. */
    var theme: Int
        get() = p.getInt("theme", 0)
        set(v) = p.edit().putInt("theme", v.coerceIn(0, 2)).apply()

    /** Days to add to the calculated Hijri date, to follow a local moon-sighting announcement. */
    var hijriAdjust: Int
        get() = p.getInt("hijri_adj", 0)
        set(v) = p.edit().putInt("hijri_adj", v.coerceIn(-2, 2)).apply()

    /** Runtime permissions are requested once; afterwards the main screen just says what is missing. */
    var permissionsAsked: Boolean
        get() = p.getBoolean("perms_asked", false)
        set(v) = p.edit().putBoolean("perms_asked", v).apply()

    /**
     * The first-run setup has been completed (or skipped). Installs upgraded from 0.4 or earlier already have
     * settings, so they count as done and never see the setup.
     */
    var setupDone: Boolean
        get() = p.getBoolean("setup_done", false) || p.contains("perms_asked")
        set(v) = p.edit().putBoolean("setup_done", v).apply()

    // ---- derived ----

    fun method(): Method = if (methodId == "CUSTOM")
        Method.custom(customFajrAngle.toDouble(), customIshaAngle.toDouble(), customIshaInterval)
    else Method.byId(methodId) ?: Method.MWL

    fun params(): CalculationParams = CalculationParams(
        method = method(),
        asrRule = if (hanafiAsr) AsrRule.HANAFI else AsrRule.STANDARD,
        // Inside London the UK method uses the mosques' published timetable instead of the calculation.
        timetable = if (methodId == Method.LONDON.id) { LondonTimetable.load(app); LondonTimetable } else null,
        adjustments = Adjustments(
            fajr = adjustment(Prayer.FAJR), sunrise = adjustment(Prayer.SUNRISE), dhuhr = adjustment(Prayer.DHUHR),
            asr = adjustment(Prayer.ASR), maghrib = adjustment(Prayer.MAGHRIB), isha = adjustment(Prayer.ISHA),
        ),
    )

    /** The coordinates to calculate for, or null if we have nothing yet. */
    fun coordinates(): Coordinates? = when {
        manualLocation -> runCatching { Coordinates(manualLat, manualLng) }.getOrNull()
        hasFix -> runCatching { Coordinates(fixLat, fixLng) }.getOrNull()   // ignore GPS altitude: inland horizons are not lower
        else -> null
    }
}
