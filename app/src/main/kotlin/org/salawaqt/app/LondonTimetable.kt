package org.salawaqt.app

import android.content.Context
import org.salawaqt.engine.Coordinates
import org.salawaqt.engine.Timetable
import java.time.LocalDate

/**
 * The London Unified Prayer Timetable (East London Mosque / Hizbul Ulama, obtained from londonprayertimes.com),
 * bundled as res/raw/london_unified.txt: one line per calendar day, seven times in minutes after 00:00 UTC.
 * Used wherever the user chooses the "London Unified Prayer Timetable" method; the main screen warns when that
 * is far from London.
 * The table is one year's worth and is reused by calendar date: the sun's position on a given date differs by
 * well under a minute from year to year, and storing UTC minutes makes the clock change irrelevant.
 */
object LondonTimetable : Timetable {
    const val LAT = 51.5074
    const val LNG = -0.1278
    /** Beyond this the main screen suggests the calculated UK method instead. */
    const val FAR_KM = 80.0

    @Volatile private var table: Map<String, Timetable.Entry>? = null

    /** Load once (13 KB; a few milliseconds). Safe to call repeatedly. */
    fun load(ctx: Context) {
        if (table != null) return
        val id = ctx.resources.getIdentifier("london_unified", "raw", ctx.packageName)
        if (id == 0) { table = emptyMap(); return }
        val m = HashMap<String, Timetable.Entry>(370)
        ctx.resources.openRawResource(id).bufferedReader().useLines { lines ->
            for (line in lines) {
                if (line.isBlank() || line.startsWith("#")) continue
                val f = line.split(',')
                if (f.size < 8) continue
                val v = f.drop(1).take(7).map { it.trim().toIntOrNull() }
                if (v.any { it == null }) continue
                m[f[0].trim()] = Timetable.Entry(v[0]!!, v[1]!!, v[2]!!, v[3]!!, v[4]!!, v[5]!!, v[6]!!)
            }
        }
        table = m
    }

    val isLoaded: Boolean get() = !table.isNullOrEmpty()

    fun distanceKm(where: Coordinates): Double = org.salawaqt.engine.Method.distanceKm(where.latitude, where.longitude, LAT, LNG)

    override fun lookup(date: LocalDate, where: Coordinates): Timetable.Entry? {
        val t = table ?: return null
        var key = String.format("%02d-%02d", date.monthValue, date.dayOfMonth)
        if (key == "02-29") key = "02-28"   // the source year had no leap day
        return t[key]
    }
}
