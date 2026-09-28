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

    // ---- alerts, one choice per prayer time ----
    companion object { const val SILENT = 0; const val NOTIFY = 1; const val ADHAN = 2 }

    /** SILENT, NOTIFY (short system sound) or ADHAN (bundled or chosen recording). Sunrise defaults to silent. */
    fun alertMode(prayer: Prayer): Int {
        val key = "mode_${prayer.name}"
        if (p.contains(key)) return p.getInt(key, SILENT).coerceIn(0, 2)
        if (prayer == Prayer.SUNRISE) return SILENT
        // Versions 0.1/0.2 had two global switches; honour them until the user sets the row.
        return when { p.getBoolean("adhan", false) -> ADHAN; p.getBoolean("notify", true) -> NOTIFY; else -> SILENT }
    }
    fun setAlertMode(prayer: Prayer, mode: Int) = p.edit().putInt("mode_${prayer.name}", mode.coerceIn(0, 2)).apply()

    /** True if at least one time alerts audibly or visibly. */
    val notificationsEnabled: Boolean get() = Prayer.entries.any { alertMode(it) != SILENT }
    val sunriseAudible: Boolean get() = alertMode(Prayer.SUNRISE) != SILENT

    /** A silent, persistent notification in the shade showing the next prayer and today's times. */
    var shadeEnabled: Boolean
        get() = p.getBoolean("shade", true)
        set(v) = p.edit().putBoolean("shade", v).apply()

    /**
     * A chosen recording is copied into the app's private storage (files/adhan/<PRAYER>), so it keeps playing even
     * if the original is deleted or lives on a removed SD card. Null = no file chosen, use the bundled recording.
     */
    fun adhanFile(prayer: Prayer): File? = File(filesDir, "adhan/${prayer.name}").takeIf { it.isFile && it.length() > 0 }
    fun adhanName(prayer: Prayer): String? = p.getString("adhan_name_${prayer.name}", null)
    fun setAdhanName(prayer: Prayer, name: String?) = p.edit().putString("adhan_name_${prayer.name}", name).apply()
    fun clearAdhan(prayer: Prayer) { File(filesDir, "adhan/${prayer.name}").delete(); setAdhanName(prayer, null) }

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

    // ---- derived ----

    fun method(): Method = if (methodId == "CUSTOM")
        Method.custom(customFajrAngle.toDouble(), customIshaAngle.toDouble(), customIshaInterval)
    else Method.byId(methodId) ?: Method.MWL

    fun params(): CalculationParams = CalculationParams(
        method = method(),
        asrRule = if (hanafiAsr) AsrRule.HANAFI else AsrRule.STANDARD,
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
