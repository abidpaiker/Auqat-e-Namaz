package org.salawaqt.engine

import java.time.LocalDate
import kotlin.math.abs

/**
 * Moonsighting Committee Worldwide (moonsighting.com, Khalid Shaukat): Fajr and Isha as a number of minutes
 * before sunrise / after sunset that varies with the season and the latitude, derived from years of twilight
 * observation. The year is divided at the winter solstice into six segments between four anchor values
 * (a, b, c, d) and interpolated linearly. Southern hemisphere: the same curve shifted by half a year.
 * Isha uses the "general" shafaq (the usual choice).
 */
object Moonsighting {
    fun minutes(date: LocalDate, latitude: Double, morning: Boolean): Double {
        val lat = abs(latitude)
        val a: Double; val b: Double; val c: Double; val d: Double
        if (morning) {
            a = 75 + 28.65 / 55.0 * lat; b = 75 + 19.44 / 55.0 * lat; c = 75 + 32.74 / 55.0 * lat; d = 75 + 48.10 / 55.0 * lat
        } else {
            a = 75 + 25.60 / 55.0 * lat; b = 75 + 2.050 / 55.0 * lat; c = 75 - 9.210 / 55.0 * lat; d = 75 + 6.140 / 55.0 * lat
        }
        // Days since the most recent winter solstice (21 Dec north; 21 Jun south).
        var dyy = daysSinceSolstice(date, latitude < 0)
        return when {
            dyy < 91  -> a + (b - a) / 91.0 * dyy
            dyy < 137 -> b + (c - b) / 46.0 * (dyy - 91)
            dyy < 183 -> c + (d - c) / 46.0 * (dyy - 137)
            dyy < 229 -> d + (c - d) / 46.0 * (dyy - 183)
            dyy < 275 -> c + (b - c) / 46.0 * (dyy - 229)
            else      -> b + (a - b) / 91.0 * (dyy - 275)
        }
    }

    internal fun daysSinceSolstice(date: LocalDate, southern: Boolean): Int {
        val doy = date.dayOfYear
        val daysInYear = if (date.isLeapYear) 366 else 365
        return if (!southern) {
            val s = daysInYear - 10          // 21 Dec of the previous year is 10 days before 1 Jan
            if (doy >= s) doy - s else doy + 10
        } else {
            val s = 172                      // 21 Jun
            if (doy >= s) doy - s else doy + (daysInYear - s)
        }
    }
}
