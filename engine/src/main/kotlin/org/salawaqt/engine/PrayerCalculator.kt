package org.salawaqt.engine

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.tan

enum class Prayer(val label: String) {
    FAJR("Fajr"), SUNRISE("Sunrise"), DHUHR("Dhuhr"), ASR("Asr"), MAGHRIB("Maghrib"), ISHA("Isha");
}

/** Things the calculator had to do that the user may want to know about. */
enum class Note {
    /** Sun never reached the Fajr angle (or the night portion rule bound it); Fajr is from the high-latitude rule. */
    FAJR_HIGH_LATITUDE,
    /** Same for Isha. */
    ISHA_HIGH_LATITUDE,
    /** Polar day/night: no sunrise or sunset today. Times were computed for the nearest latitude that has one. */
    POLAR_LATITUDE_CLAMPED,
    /** Times came from a published timetable (see [Timetable]) rather than the calculation. */
    TIMETABLE,
}

/**
 * The six times for one local calendar day. Internally each is an exact UTC instant; the zone is only used
 * when you ask for a [ZonedDateTime], so daylight-saving transitions are handled by java.time, not by us.
 */
class PrayerTimes internal constructor(
    val date: LocalDate,
    val zone: ZoneId,
    private val epochSeconds: LongArray,     // indexed by Prayer.ordinal; index 6 = Asr under the other shadow rule
    val notes: Set<Note>,
) {
    fun instant(p: Prayer): Instant = Instant.ofEpochSecond(epochSeconds[p.ordinal])
    fun at(p: Prayer): ZonedDateTime = instant(p).atZone(zone)

    /** Asr under the shadow rule that was *not* requested (Hanafi if standard was asked for, and vice versa). */
    val asrOtherInstant: Instant get() = Instant.ofEpochSecond(epochSeconds[OTHER_ASR])
    val asrOther: ZonedDateTime get() = asrOtherInstant.atZone(zone)

    val fajr: ZonedDateTime get() = at(Prayer.FAJR)
    val sunrise: ZonedDateTime get() = at(Prayer.SUNRISE)
    val dhuhr: ZonedDateTime get() = at(Prayer.DHUHR)
    val asr: ZonedDateTime get() = at(Prayer.ASR)
    val maghrib: ZonedDateTime get() = at(Prayer.MAGHRIB)
    val isha: ZonedDateTime get() = at(Prayer.ISHA)

    /** Same times rounded to the nearest whole minute — what the app displays and schedules. */
    fun roundedToMinute(): PrayerTimes =
        PrayerTimes(date, zone, LongArray(epochSeconds.size) { ((epochSeconds[it] + 30) / 60) * 60 }, notes)

    /** First prayer whose time is strictly after [now], or null if all of today's have passed. */
    fun nextAfter(now: Instant): Pair<Prayer, Instant>? {
        val n = now.epochSecond
        for (p in Prayer.entries) if (epochSeconds[p.ordinal] > n) return p to instant(p)
        return null
    }

    /** Last prayer whose time is at or before [now], or null if none yet today. */
    fun lastAtOrBefore(now: Instant): Prayer? {
        val n = now.epochSecond
        var last: Prayer? = null
        for (p in Prayer.entries) if (epochSeconds[p.ordinal] <= n) last = p
        return last
    }

    internal companion object { const val OTHER_ASR = 6 }

    override fun toString(): String =
        Prayer.entries.joinToString(", ", "PrayerTimes($date $zone: ", if (notes.isEmpty()) ")" else " notes=$notes)") {
            "${it.label}=${at(it).toLocalTime()}"
        }
}

object PrayerCalculator {

    private const val DAY = 86400.0
    private const val DHUHR_OFFSET_SECONDS = 60L   // time for the sun's disc to clear the meridian

    /**
     * Compute prayer times for the local calendar [date] at [where], expressed in [zone].
     *
     * Method: every time is derived from the instant of local solar noon on that day (found by iteration), as
     * noon ± the hour angle at which the sun reaches the required altitude. Solar declination is re-evaluated
     * at each prayer's own time (two passes), which keeps the result within a few seconds of a full ephemeris.
     */
    fun compute(date: LocalDate, zone: ZoneId, where: Coordinates, params: CalculationParams = CalculationParams()): PrayerTimes {
        params.timetable?.lookup(date, where)?.let { return fromTimetable(date, zone, it, params) }
        val notes = HashSet<Note>(2)
        val m = params.method

        // Anchor: local noon on the requested local date. atStartOfDay handles a DST gap at midnight.
        val anchor = date.atStartOfDay(zone).toEpochSecond().toDouble() + 12 * 3600
        val noon = solarNoon(anchor, where.longitude)

        // Polar day/night: clamp latitude so the horizon-crossing exists. Only ever matters above ~66°.
        val horizon = -(0.833 + 0.0347 * kotlin.math.sqrt(where.elevation.coerceAtLeast(0.0)))
        var lat = where.latitude
        val declNoon = Sun.position(Sun.julianDay(noon)).declination
        val maxLat = 90.0 - abs(declNoon) + horizon - 0.5   // horizon is negative
        if (abs(lat) > maxLat) { lat = if (lat > 0) maxLat else -maxLat; notes += Note.POLAR_LATITUDE_CLAMPED }

        val sunrise = timeAt(horizon, lat, noon, morning = true)!!
        val sunset  = timeAt(horizon, lat, noon, morning = false)!!

        // --- Fajr / Isha with high-latitude bounding ---
        val nextSunrise = timeAt(horizon, lat, solarNoon(anchor + DAY, where.longitude), morning = true) ?: (sunrise + DAY)
        val night = nextSunrise - sunset

        val fajrAngle = if (m.twilight == Twilight.SEASONAL_ANGLE) seasonalAngle(m.seasonalFajr, date) else m.fajrAngle
        val ishaAngle = if (m.twilight == Twilight.SEASONAL_ANGLE) seasonalAngle(m.seasonalIsha, date) else m.ishaAngle

        var fajr = timeAt(-fajrAngle, lat, noon, morning = true)
        if (m.twilight == Twilight.MOONSIGHTING) {
            // Observation-based bound: Fajr is never earlier than this many minutes before sunrise.
            val safe = sunrise - Moonsighting.minutes(date, where.latitude, morning = true) * 60
            if (fajr == null || fajr < safe) fajr = safe
        }
        val fajrPortion = nightPortion(params.highLatitudeRule, fajrAngle, night)
        if (fajr == null || sunrise - fajr > fajrPortion) { fajr = sunrise - fajrPortion; notes += Note.FAJR_HIGH_LATITUDE }

        var isha: Double
        if (m.ishaIntervalMinutes > 0) {
            isha = sunset + (m.ishaIntervalMinutes + m.maghribMinutes) * 60.0
        } else {
            var t = timeAt(-ishaAngle, lat, noon, morning = false)
            if (m.twilight == Twilight.MOONSIGHTING) {
                val safe = sunset + Moonsighting.minutes(date, where.latitude, morning = false) * 60
                if (t == null || t > safe) t = safe
            }
            val portion = nightPortion(params.highLatitudeRule, ishaAngle, night)
            if (t == null || t - sunset > portion) { isha = sunset + portion; notes += Note.ISHA_HIGH_LATITUDE } else isha = t
        }

        // --- Maghrib: sunset, or a small depression angle for methods that use one, plus any fixed margin ---
        val maghrib = (if (m.maghribAngle > 0) timeAt(-m.maghribAngle, lat, noon, morning = false) ?: sunset else sunset) +
            m.maghribMinutes * 60.0

        // --- Asr: altitude at which shadow = noon shadow + factor × height ---
        val declAsr = Sun.position(Sun.julianDay(noon + 3 * 3600)).declination
        fun asrFor(rule: AsrRule): Double {
            val alt = Math.toDegrees(atan(1.0 / (rule.shadowFactor + tan(Math.toRadians(abs(lat - declAsr))))))
            // Only in the polar-clamped case can the sun fail to reach the Asr altitude; then take mid-afternoon.
            return timeAt(alt, lat, noon, morning = false) ?: (noon + (sunset - noon) / 2)
        }
        val asr = asrFor(params.asrRule)
        val asrOther = asrFor(if (params.asrRule == AsrRule.STANDARD) AsrRule.HANAFI else AsrRule.STANDARD)

        val dhuhr = noon + DHUHR_OFFSET_SECONDS + m.dhuhrMinutes * 60.0

        val a = params.adjustments
        val out = LongArray(7)
        out[Prayer.FAJR.ordinal]    = (fajr    + a.fajr    * 60).roundToLong()
        out[Prayer.SUNRISE.ordinal] = (sunrise + (a.sunrise + m.sunriseMinutes) * 60).roundToLong()
        out[Prayer.DHUHR.ordinal]   = (dhuhr   + a.dhuhr   * 60).roundToLong()
        out[Prayer.ASR.ordinal]     = (asr     + a.asr     * 60).roundToLong()
        out[Prayer.MAGHRIB.ordinal] = (maghrib + a.maghrib * 60).roundToLong()
        out[Prayer.ISHA.ordinal]    = (isha    + a.isha    * 60).roundToLong()
        out[PrayerTimes.OTHER_ASR]  = (asrOther + a.asr    * 60).roundToLong()
        return PrayerTimes(date, zone, out, notes)
    }

    /** A timetable entry (minutes after 00:00 UTC of [date]) as PrayerTimes, with the user's minute adjustments applied. */
    private fun fromTimetable(date: LocalDate, zone: ZoneId, e: Timetable.Entry, params: CalculationParams): PrayerTimes {
        val midnight = date.atStartOfDay(java.time.ZoneOffset.UTC).toEpochSecond()
        val a = params.adjustments
        val hanafi = params.asrRule == AsrRule.HANAFI
        val out = LongArray(7)
        out[Prayer.FAJR.ordinal]    = midnight + (e.fajr + a.fajr) * 60L
        out[Prayer.SUNRISE.ordinal] = midnight + (e.sunrise + a.sunrise) * 60L
        out[Prayer.DHUHR.ordinal]   = midnight + (e.dhuhr + a.dhuhr) * 60L
        out[Prayer.ASR.ordinal]     = midnight + ((if (hanafi) e.asrHanafi else e.asrStandard) + a.asr) * 60L
        out[Prayer.MAGHRIB.ordinal] = midnight + (e.maghrib + a.maghrib) * 60L
        out[Prayer.ISHA.ordinal]    = midnight + (e.isha + a.isha) * 60L
        out[PrayerTimes.OTHER_ASR]  = midnight + ((if (hanafi) e.asrStandard else e.asrHanafi) + a.asr) * 60L
        return PrayerTimes(date, zone, out, setOf(Note.TIMETABLE))
    }

    /** angle = c0 + Σ ck·cos(kω) + sk·sin(kω), ω = 2π·(dayOfYear − 1)/365.25. */
    internal fun seasonalAngle(c: DoubleArray, date: LocalDate): Double {
        if (c.isEmpty()) return 18.0
        val w = 2 * Math.PI * (date.dayOfYear - 1) / 365.25
        var a = c[0]
        var k = 1
        var i = 1
        while (i + 1 < c.size) { a += c[i] * kotlin.math.cos(k * w) + c[i + 1] * kotlin.math.sin(k * w); i += 2; k++ }
        return a
    }

    /**
     * The next prayer after [now] for someone at [where]: looks at today and tomorrow (local dates in [zone]),
     * so it is correct after Isha and across midnight. Times are minute-rounded, matching what is displayed.
     */
    fun next(now: Instant, zone: ZoneId, where: Coordinates, params: CalculationParams = CalculationParams(), skipSunrise: Boolean = false): Upcoming {
        val today = now.atZone(zone).toLocalDate()
        val t0 = compute(today, zone, where, params).roundedToMinute()
        val t1 by lazy { compute(today.plusDays(1), zone, where, params).roundedToMinute() }
        var nxt = t0.nextAfter(now) ?: t1.nextAfter(now)!!
        if (skipSunrise && nxt.first == Prayer.SUNRISE) nxt = t0.nextAfter(nxt.second) ?: t1.nextAfter(nxt.second)!!
        val current = t0.lastAtOrBefore(now)
            ?: compute(today.minusDays(1), zone, where, params).roundedToMinute().lastAtOrBefore(now)
        return Upcoming(nxt.first, nxt.second.atZone(zone), current)
    }

    // ------------------------------------------------------------------------------------------------

    private fun nightPortion(rule: HighLatitudeRule, angle: Double, night: Double): Double = when (rule) {
        HighLatitudeRule.MIDDLE_OF_NIGHT -> night / 2
        HighLatitudeRule.ONE_SEVENTH     -> night / 7
        HighLatitudeRule.ANGLE_BASED     -> night * angle / 60.0
    }

    /** Instant (epoch seconds) of local solar noon nearest to [anchor]. */
    internal fun solarNoon(anchor: Double, longitude: Double): Double {
        var t = anchor
        repeat(2) {
            val eqt = Sun.position(Sun.julianDay(t)).equationOfTime
            val utcDayStart = floor(t / DAY) * DAY
            var noon = utcDayStart + (12.0 - longitude / 15.0 - eqt) * 3600
            while (noon - anchor > DAY / 2) noon -= DAY
            while (anchor - noon > DAY / 2) noon += DAY
            t = noon
        }
        return t
    }

    /**
     * Instant when the sun's centre is at [altitude] degrees (negative = below horizon), before or after [noon].
     * Null if the sun never reaches that altitude on this day.
     */
    internal fun timeAt(altitude: Double, latitude: Double, noon: Double, morning: Boolean): Double? {
        var t = noon
        repeat(2) {
            val decl = Sun.position(Sun.julianDay(t)).declination
            val arg = (Sun.sinD(altitude) - Sun.sinD(latitude) * Sun.sinD(decl)) / (Sun.cosD(latitude) * Sun.cosD(decl))
            if (arg < -1.0 || arg > 1.0) return null
            val ha = Math.toDegrees(acos(arg)) / 15.0 * 3600   // seconds from noon
            t = if (morning) noon - ha else noon + ha
        }
        return t
    }
}

/** Result of [PrayerCalculator.next]. [current] is the prayer whose time has most recently begun (SUNRISE means "between sunrise and Dhuhr"). */
data class Upcoming(val prayer: Prayer, val time: ZonedDateTime, val current: Prayer?)
