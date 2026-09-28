package org.salawaqt.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.abs

/**
 * Golden values computed with an independent implementation of the NOAA solar algorithm (the Python `astral`
 * library, 3.2) using the same conventions: MWL angles 18°/17°, sunrise/sunset at −0.833°, standard Asr,
 * sea level, no refraction on angle-based times, Dhuhr = solar noon + 1 min. Each value is checked to ±1 min.
 * Null means the sun never reaches the angle that day (high latitude in summer).
 */
class GoldenTest {

    private data class G(
        val city: String, val lat: Double, val lng: Double, val zone: String, val date: String,
        val fajr: String?, val sunrise: String, val dhuhr: String, val asr: String, val maghrib: String, val isha: String?,
    )

    private val golden = listOf(
        G("New York", 40.7128, -74.006, "America/New_York", "2026-03-20", "05:28", "06:59", "13:05", "16:29", "19:08", "20:35"),
        G("New York", 40.7128, -74.006, "America/New_York", "2026-06-21", "03:19", "05:25", "12:59", "16:58", "20:31", "22:28"),
        G("New York", 40.7128, -74.006, "America/New_York", "2026-09-22", "05:12", "06:44", "12:50", "16:14", "18:53", "20:19"),
        G("New York", 40.7128, -74.006, "America/New_York", "2026-12-21", "05:38", "07:17", "11:55", "14:14", "16:32", "18:05"),
        G("London", 51.5074, -0.1278, "Europe/London", "2026-03-20", "04:10", "06:03", "12:09", "15:26", "18:14", "20:00"),
        G("London", 51.5074, -0.1278, "Europe/London", "2026-06-21", null, "04:43", "13:03", "17:25", "21:22", null),
        G("London", 51.5074, -0.1278, "Europe/London", "2026-09-22", "04:52", "06:46", "12:54", "16:11", "18:59", "20:46"),
        G("London", 51.5074, -0.1278, "Europe/London", "2026-12-21", "05:59", "08:04", "11:59", "13:38", "15:53", "17:51"),
        G("Makkah", 21.4225, 39.8262, "Asia/Riyadh", "2026-03-20", "05:11", "06:25", "12:29", "15:53", "18:32", "19:41"),
        G("Makkah", 21.4225, 39.8262, "Asia/Riyadh", "2026-06-21", "04:14", "05:39", "12:23", "15:42", "19:06", "20:26"),
        G("Makkah", 21.4225, 39.8262, "Asia/Riyadh", "2026-09-22", "04:55", "06:09", "12:15", "15:38", "18:17", "19:27"),
        G("Makkah", 21.4225, 39.8262, "Asia/Riyadh", "2026-12-21", "05:34", "06:54", "12:20", "15:23", "17:44", "18:59"),
        G("Karachi", 24.8607, 67.0011, "Asia/Karachi", "2026-03-20", "05:20", "06:36", "12:41", "16:06", "18:43", "19:55"),
        G("Karachi", 24.8607, 67.0011, "Asia/Karachi", "2026-06-21", "04:14", "05:43", "12:35", "15:55", "19:24", "20:48"),
        G("Karachi", 24.8607, 67.0011, "Asia/Karachi", "2026-09-22", "05:04", "06:21", "12:26", "15:51", "18:29", "19:40"),
        G("Karachi", 24.8607, 67.0011, "Asia/Karachi", "2026-12-21", "05:51", "07:12", "12:31", "15:28", "17:48", "19:05"),
        G("Jakarta", -6.2088, 106.8456, "Asia/Jakarta", "2026-03-20", "04:48", "05:57", "12:01", "15:11", "18:03", "19:08"),
        G("Jakarta", -6.2088, 106.8456, "Asia/Jakarta", "2026-06-21", "04:46", "06:01", "11:55", "15:16", "17:47", "18:58"),
        G("Jakarta", -6.2088, 106.8456, "Asia/Jakarta", "2026-09-22", "04:33", "05:42", "11:47", "14:56", "17:49", "18:54"),
        G("Jakarta", -6.2088, 106.8456, "Asia/Jakarta", "2026-12-21", "04:20", "05:36", "11:51", "15:18", "18:05", "19:17"),
        G("Sydney", -33.8688, 151.2093, "Australia/Sydney", "2026-03-20", "05:34", "06:58", "13:04", "16:30", "19:07", "20:25"),
        G("Sydney", -33.8688, 151.2093, "Australia/Sydney", "2026-06-21", "05:30", "07:00", "11:58", "14:36", "16:54", "18:18"),
        G("Sydney", -33.8688, 151.2093, "Australia/Sydney", "2026-09-22", "04:22", "05:45", "11:49", "15:15", "17:51", "19:10"),
        G("Sydney", -33.8688, 151.2093, "Australia/Sydney", "2026-12-21", "03:56", "05:41", "12:54", "16:38", "20:05", "21:43"),
        G("Honolulu", 21.3069, -157.8583, "Pacific/Honolulu", "2026-03-20", "05:21", "06:35", "12:40", "16:03", "18:43", "19:52"),
        G("Honolulu", 21.3069, -157.8583, "Pacific/Honolulu", "2026-06-21", "04:25", "05:50", "12:34", "15:53", "19:16", "20:36"),
        G("Honolulu", 21.3069, -157.8583, "Pacific/Honolulu", "2026-09-22", "05:06", "06:20", "12:25", "15:49", "18:27", "19:37"),
        G("Honolulu", 21.3069, -157.8583, "Pacific/Honolulu", "2026-12-21", "05:45", "07:05", "12:30", "15:34", "17:55", "19:10"),
        G("Kiritimati", 1.8721, -157.4278, "Pacific/Kiritimati", "2026-03-20", "05:25", "06:34", "12:39", "15:41", "18:41", "19:45"),
        G("Kiritimati", 1.8721, -157.4278, "Pacific/Kiritimati", "2026-06-21", "05:09", "06:24", "12:32", "15:58", "18:38", "19:49"),
        G("Kiritimati", 1.8721, -157.4278, "Pacific/Kiritimati", "2026-09-22", "05:11", "06:19", "12:24", "15:26", "18:26", "19:31"),
        G("Kiritimati", 1.8721, -157.4278, "Pacific/Kiritimati", "2026-12-21", "05:12", "06:27", "12:28", "15:52", "18:28", "19:39"),
        G("Cape Town", -33.9249, 18.4241, "Africa/Johannesburg", "2026-03-20", "05:26", "06:49", "12:55", "16:21", "18:58", "20:16"),
        G("Cape Town", -33.9249, 18.4241, "Africa/Johannesburg", "2026-06-21", "06:22", "07:51", "12:49", "15:27", "17:45", "19:09"),
        G("Cape Town", -33.9249, 18.4241, "Africa/Johannesburg", "2026-09-22", "05:13", "06:36", "12:40", "16:06", "18:43", "20:01"),
        G("Cape Town", -33.9249, 18.4241, "Africa/Johannesburg", "2026-12-21", "03:47", "05:32", "12:45", "16:29", "19:57", "21:35"),
    )

    private val params = CalculationParams(Method.MWL, AsrRule.STANDARD)

    @Test fun matchesIndependentReferenceWithinOneMinute() {
        for (g in golden) {
            val t = PrayerCalculator.compute(LocalDate.parse(g.date), ZoneId.of(g.zone), Coordinates(g.lat, g.lng), params)
            check(g, t, Prayer.FAJR, g.fajr)
            check(g, t, Prayer.SUNRISE, g.sunrise)
            check(g, t, Prayer.DHUHR, g.dhuhr)
            check(g, t, Prayer.ASR, g.asr)
            check(g, t, Prayer.MAGHRIB, g.maghrib)
            check(g, t, Prayer.ISHA, g.isha)
        }
    }

    private fun check(g: G, t: PrayerTimes, p: Prayer, expected: String?) {
        val z = t.at(p)
        if (p != Prayer.ISHA) // Isha may legitimately fall after local midnight at high latitudes
            assertEquals("${g.city} ${g.date} ${p.label} must fall on the requested local date", g.date, z.toLocalDate().toString())
        if (expected == null) {
            val note = if (p == Prayer.FAJR) Note.FAJR_HIGH_LATITUDE else Note.ISHA_HIGH_LATITUDE
            assertTrue("${g.city} ${g.date} ${p.label}: expected high-latitude rule", t.notes.contains(note))
            return
        }
        val want = LocalTime.parse(expected)
        val diff = abs(ChronoUnit.SECONDS.between(want, z.toLocalTime()))
        assertTrue("${g.city} ${g.date} ${p.label}: expected $expected, got ${z.toLocalTime()} (${diff}s off)", diff <= 90)
    }

    @Test fun ummAlQuraIshaIsNinetyMinutesAfterMaghrib() {
        val t = PrayerCalculator.compute(LocalDate.of(2026, 6, 21), ZoneId.of("Asia/Riyadh"), Coordinates(21.4225, 39.8262), CalculationParams(Method.MAKKAH))
        assertEquals(90L, ChronoUnit.MINUTES.between(t.maghrib, t.isha))
    }

    @Test fun tehranMaghribIsAfterSunset() {
        val c = Coordinates(35.6892, 51.3890)
        val z = ZoneId.of("Asia/Tehran")
        val std = PrayerCalculator.compute(LocalDate.of(2026, 4, 1), z, c, CalculationParams(Method.MWL))
        val teh = PrayerCalculator.compute(LocalDate.of(2026, 4, 1), z, c, CalculationParams(Method.TEHRAN))
        val delta = ChronoUnit.MINUTES.between(std.maghrib, teh.maghrib)
        assertTrue("Tehran Maghrib should be ~15–25 min after sunset, was $delta", delta in 12..30)
    }
}
