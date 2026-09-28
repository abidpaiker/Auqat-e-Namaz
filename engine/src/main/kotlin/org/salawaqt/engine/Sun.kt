package org.salawaqt.engine

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Low-precision solar position, after the U.S. Naval Observatory's "Approximate Solar Coordinates"
 * (the same formulas used by praytimes.org and most prayer-time software).
 * Accuracy is about 1 arc-minute over the years 1950–2050, which puts prayer times within ~10 seconds.
 *
 * All angles in and out are degrees; time is a continuous Julian Day (UTC).
 */
internal object Sun {

    /** Julian Day at a given instant, from Unix epoch seconds. */
    fun julianDay(epochSeconds: Double): Double = epochSeconds / 86400.0 + 2440587.5

    /** Solar declination and equation of time at Julian Day [jd]. */
    fun position(jd: Double): SunPosition {
        val d = jd - 2451545.0                              // days since J2000.0
        val g = fixAngle(357.529 + 0.98560028 * d)          // mean anomaly
        val q = fixAngle(280.459 + 0.98564736 * d)          // mean longitude
        val l = fixAngle(q + 1.915 * sinD(g) + 0.020 * sinD(2 * g)) // ecliptic longitude
        val e = 23.439 - 0.00000036 * d                     // obliquity of the ecliptic
        val ra = fixHour(atan2(cosD(e) * sinD(l), cosD(l)).toDeg() / 15.0) // right ascension, hours
        val decl = asin(sinD(e) * sinD(l)).toDeg()
        val eqt = fixHour(q / 15.0 - ra)                    // hours; wrap so it lies in [-12, 12)
        return SunPosition(declination = decl, equationOfTime = if (eqt > 12) eqt - 24 else eqt)
    }

    // ---- small trig helpers on degrees -------------------------------------------------------

    fun sinD(deg: Double) = sin(Math.toRadians(deg))
    fun cosD(deg: Double) = cos(Math.toRadians(deg))
    fun Double.toDeg() = Math.toDegrees(this)

    /** Wrap to [0, 360). */
    fun fixAngle(a: Double): Double { val r = a - 360.0 * floor(a / 360.0); return if (r < 0) r + 360.0 else r }
    /** Wrap to [0, 24). */
    fun fixHour(h: Double): Double { val r = h - 24.0 * floor(h / 24.0); return if (r < 0) r + 24.0 else r }
}

internal data class SunPosition(val declination: Double, val equationOfTime: Double)
