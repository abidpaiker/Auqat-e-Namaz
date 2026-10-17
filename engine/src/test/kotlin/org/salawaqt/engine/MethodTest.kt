package org.salawaqt.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** The methods added in 0.5: Moonsighting Committee, the seasonal UK method, fixed-minute margins, both Asr times. */
class MethodTest {
    private val london = Coordinates(51.5074, -0.1278)
    private val zl = ZoneId.of("Europe/London")

    /**
     * Values derived from the 2026 London Unified Prayer Timetable (East London Mosque / Hizbul Ulama) — the
     * calibration target of [Method.UK]. These are a handful of our own readings, not their table.
     */
    private val londonUnified = listOf(
        // date,          fajr,   sunrise, dhuhr,  asr(std), asr(hanafi), maghrib, isha   (local clock time)
        Triple(LocalDate.of(2026, 1, 1),  listOf("06:26", "08:03", "12:09", "13:46", "14:17", "16:05", "17:42"), 0),
        Triple(LocalDate.of(2026, 3, 15), listOf("04:35", "06:12", "12:15", "15:21", "16:09", "18:08", "19:29"), 0),
        Triple(LocalDate.of(2026, 6, 21), listOf("02:40", "04:40", "13:07", "17:25", "18:40", "21:25", "22:43"), 0),
        Triple(LocalDate.of(2026, 9, 10), listOf("04:52", "06:24", "13:03", "16:31", "17:25", "19:30", "20:43"), 0),
        Triple(LocalDate.of(2026, 11, 20), listOf("05:42", "07:24", "11:51", "13:44", "14:18", "16:08", "17:42"), 0),
    )

    private fun minutesOff(actual: java.time.ZonedDateTime, hhmm: String): Long {
        val (h, m) = hhmm.split(":").map { it.toInt() }
        val expected = actual.toLocalDate().atTime(h, m).atZone(zl)
        return ChronoUnit.MINUTES.between(expected, actual)
    }

    @Test fun ukMethodMatchesLondonUnifiedTimetable() {
        for ((date, times, _) in londonUnified) {
            val t = PrayerCalculator.compute(date, zl, london, CalculationParams(Method.UK)).roundedToMinute()
            val h = PrayerCalculator.compute(date, zl, london, CalculationParams(Method.UK, AsrRule.HANAFI)).roundedToMinute()
            val offs = listOf(
                "fajr" to minutesOff(t.fajr, times[0]), "sunrise" to minutesOff(t.sunrise, times[1]),
                "dhuhr" to minutesOff(t.dhuhr, times[2]), "asr" to minutesOff(t.asr, times[3]),
                "asr-hanafi" to minutesOff(h.asr, times[4]), "maghrib" to minutesOff(t.maghrib, times[5]),
                "isha" to minutesOff(t.isha, times[6]),
            )
            for ((name, off) in offs) assertTrue("$date $name off by $off min", kotlin.math.abs(off) <= 3)
            assertTrue(t.notes.isEmpty())   // the seasonal angle is always reachable, even in June
        }
    }

    @Test fun moonsightingIsCloseToLondonUnified() {
        for ((date, times, _) in londonUnified) {
            val t = PrayerCalculator.compute(date, zl, london, CalculationParams(Method.MOONSIGHT)).roundedToMinute()
            assertTrue("$date fajr", kotlin.math.abs(minutesOff(t.fajr, times[0])) <= 10)
            assertTrue("$date isha", kotlin.math.abs(minutesOff(t.isha, times[6])) <= 15)
        }
    }

    @Test fun moonsightingSeasonalMinutesAreContinuousAndSymmetric() {
        var prev = Moonsighting.minutes(LocalDate.of(2025, 12, 31), 45.0, morning = true)
        for (d in 1..365) {
            val m = Moonsighting.minutes(LocalDate.of(2026, 1, 1).plusDays((d - 1).toLong()), 45.0, morning = true)
            assertTrue("jump on day $d: $prev → $m", kotlin.math.abs(m - prev) < 1.0)
            prev = m
        }
        // Southern hemisphere: the curve is shifted by half a year.
        val n = Moonsighting.minutes(LocalDate.of(2026, 1, 15), 45.0, morning = false)
        val s = Moonsighting.minutes(LocalDate.of(2026, 7, 16), -45.0, morning = false)
        assertEquals(n, s, 2.0)
        // At the equator every segment collapses to 75 minutes.
        assertEquals(75.0, Moonsighting.minutes(LocalDate.of(2026, 4, 1), 0.0, morning = true), 1e-9)
    }

    @Test fun moonsightingNeverExceedsEighteenDegrees() {
        // In Cairo 18° is always reachable and is earlier than the seasonal bound, so Moonsighting == 18/18 there... except
        // that the seasonal bound caps it. Either way Fajr must not be earlier than 18° and Isha not later than 18°.
        val c = Coordinates(30.0444, 31.2357); val z = ZoneId.of("Africa/Cairo")
        for (month in 1..12) {
            val d = LocalDate.of(2026, month, 15)
            val ms = PrayerCalculator.compute(d, z, c, CalculationParams(Method.MOONSIGHT))
            val a18 = PrayerCalculator.compute(d, z, c, CalculationParams(Method.custom(18.0, 18.0)))
            assertTrue("$d fajr", !ms.fajr.isBefore(a18.fajr))
            assertTrue("$d isha", !ms.isha.isAfter(a18.isha))
        }
    }

    @Test fun fixedMinuteMarginsApply() {
        val c = Coordinates(31.9539, 35.9106); val z = ZoneId.of("Asia/Amman"); val d = LocalDate.of(2026, 5, 5)
        val plain = PrayerCalculator.compute(d, z, c, CalculationParams(Method.custom(18.0, 18.0))).roundedToMinute()
        val jordan = PrayerCalculator.compute(d, z, c, CalculationParams(Method.JORDAN)).roundedToMinute()
        assertEquals(5L, ChronoUnit.MINUTES.between(plain.maghrib, jordan.maghrib))
        assertEquals(plain.isha, jordan.isha)   // angle-based Isha is unaffected by the Maghrib margin

        val lisbon = Coordinates(38.7223, -9.1393); val zp = ZoneId.of("Europe/Lisbon")
        val pt = PrayerCalculator.compute(d, zp, lisbon, CalculationParams(Method.PORTUGAL)).roundedToMinute()
        val sunset = PrayerCalculator.compute(d, zp, lisbon, CalculationParams(Method.custom(18.0, 18.0))).roundedToMinute().maghrib
        assertEquals(3L, ChronoUnit.MINUTES.between(sunset, pt.maghrib))
        assertEquals(77L, ChronoUnit.MINUTES.between(pt.maghrib, pt.isha))
    }

    @Test fun bothAsrTimesAreAvailable() {
        val c = Coordinates(40.657, -75.349); val z = ZoneId.of("America/New_York"); val d = LocalDate.of(2026, 9, 27)
        val std = PrayerCalculator.compute(d, z, c, CalculationParams(Method.ISNA, AsrRule.STANDARD))
        val han = PrayerCalculator.compute(d, z, c, CalculationParams(Method.ISNA, AsrRule.HANAFI))
        assertEquals(std.asrOther, han.asr)
        assertEquals(han.asrOther, std.asr)
        assertTrue(std.asrOther.isAfter(std.asr))
        val r = std.roundedToMinute()
        assertEquals(0, r.asrOther.second)
    }

    @Test fun everyMethodHasUniqueIdAndIsFound() {
        assertEquals(Method.ALL.size, Method.ALL.map { it.id }.toSet().size)
        for (m in Method.ALL) assertEquals(m, Method.byId(m.id))
        assertEquals(Method.ISNA, Method.suggestedFor("US", 40.0))
        assertEquals(Method.LONDON, Method.suggestedFor("GB", 51.5074, -0.1278))
        assertEquals(Method.LONDON, Method.suggestedFor("GB", 51.8787, -0.4200))   // Luton, ~48 km
        assertEquals(Method.UK, Method.suggestedFor("GB", 53.4808, -2.2426))       // Manchester
        assertEquals(Method.MOONSIGHT, Method.suggestedFor("DE", 52.5))
        assertEquals(Method.MWL, Method.suggestedFor(null, 10.0))
    }
}
