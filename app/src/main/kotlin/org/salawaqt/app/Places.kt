package org.salawaqt.app

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.sqrt

/**
 * Nearest town, entirely offline, from the GeoNames "cities5000" table bundled in res/raw (towns of 5,000+ people,
 * CC BY 4.0, https://www.geonames.org). Loaded once on a background thread; a linear scan of ~55,000 rows takes a
 * few milliseconds, so no index is needed.
 */
object Places {
    /** [pull] is how many km of extra distance a town's size is worth: 3 km per factor of ten in population. */
    private class Town(val name: String, val region: String, val lat: Float, val lng: Float, val pull: Float)
    @Volatile private var towns: Array<Town>? = null
    @Volatile private var loading = false

    fun load(ctx: Context, onLoaded: () -> Unit) {
        if (towns != null || loading) return
        loading = true
        Thread {
            val id = ctx.resources.getIdentifier("cities5000", "raw", ctx.packageName)
            if (id == 0) { loading = false; return@Thread }   // file not bundled: feature silently off
            val list = ArrayList<Town>(60_000)
            BufferedReader(InputStreamReader(ctx.resources.openRawResource(id), Charsets.UTF_8), 1 shl 16).useLines { lines ->
                for (line in lines) {
                    // GeoNames columns: 0 id, 1 name, 2 ascii, 3 alternates, 4 lat, 5 lng, 6 class, 7 code, 8 country, 9 cc2, 10 admin1 … 14 population
                    val f = line.split('\t')
                    if (f.size < 15) continue
                    val lat = f[4].toFloatOrNull() ?: continue
                    val lng = f[5].toFloatOrNull() ?: continue
                    val country = f[8]
                    val region = if (country == "US" || country == "CA" || country == "AU") f[10] else country
                    val pop = max(f[14].toLongOrNull() ?: 0L, 1000L)
                    list += Town(f[1], region, lat, lng, 3f * log10(pop.toFloat()))
                }
            }
            towns = list.toTypedArray()
            loading = false
            onLoaded()
        }.apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
    }

    private var cacheKey = 0L
    private var cacheValue: String? = null

    /**
     * e.g. "Bethlehem, PA (4 km)" or null while loading / if the table is absent. Cached per position (called every second).
     * Not simply the closest entry: the table lists every census tract of 5,000 people, so the closest is often a name
     * nobody uses ("Middletown" 2 km away instead of Bethlehem 4 km away). Each town's distance is discounted by
     * 3 km per factor of ten in population, so a real city wins over a nearby suburb unless it is much farther.
     */
    fun nearest(lat: Double, lng: Double): String? {
        val t = towns ?: return null
        val key = (Math.round(lat * 1000) shl 32) xor (Math.round(lng * 1000) and 0xffffffffL)
        if (key == cacheKey && cacheValue != null) return cacheValue
        cacheValue = find(t, lat, lng); cacheKey = key
        return cacheValue
    }

    private fun find(t: Array<Town>, lat: Double, lng: Double): String? {
        val cosLat = cos(Math.toRadians(lat)).toFloat()
        var best: Town? = null; var bestScore = Float.MAX_VALUE; var bestKm = 0f
        for (town in t) {
            val dy = town.lat - lat.toFloat()
            if (dy > 1f || dy < -1f) continue          // > 111 km: cannot win against anything reasonable
            var dx = town.lng - lng.toFloat(); if (dx > 180) dx -= 360 else if (dx < -180) dx += 360
            dx *= cosLat
            val km = sqrt(dx * dx + dy * dy) * 111.2f
            val score = km - town.pull
            if (score < bestScore) { bestScore = score; best = town; bestKm = km }
        }
        if (best == null) {   // nothing within a degree (open ocean, desert): fall back to the plain nearest
            var bestD = Float.MAX_VALUE
            for (town in t) {
                val dy = town.lat - lat.toFloat()
                var dx = town.lng - lng.toFloat(); if (dx > 180) dx -= 360 else if (dx < -180) dx += 360
                dx *= cosLat
                val d = dx * dx + dy * dy
                if (d < bestD) { bestD = d; best = town }
            }
            bestKm = sqrt(bestD) * 111.2f
        }
        val b = best ?: return null
        val km = bestKm
        val dist = if (km < 1.5f) "" else " (${km.toInt()} km)"
        return "${b.name}, ${b.region}$dist"
    }
}
