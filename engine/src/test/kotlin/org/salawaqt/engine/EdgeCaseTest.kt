package org.salawaqt.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.temporal.ChronoUnit

/** Timezone/DST transitions, date-line zones, high latitudes, and the poles. */
class EdgeCaseTest {

    private val nyc = Coordinates(40.7128, -74.0060)
    private val ny = ZoneId.of("America/New_York")

    @Test fun springForwardDayInNewYork() {
        // 2026-03-08: clocks jump 02:00 → 03:00. Local clock times must reflect EDT after the jump.
        val before = PrayerCalculator.compute(LocalDate.of(2026, 3, 7), ny, nyc)
        val day = PrayerCalculator.compute(LocalDate.of(2026, 3, 8), ny, nyc)
        assertEquals(ZoneOffset.ofHours(-5), before.dhuhr.offset)
        assertEquals(ZoneOffset.ofHours(-4), day.dhuhr.offset)
        // Fajr (~05:xx) is already after the 02:00 jump, so it is EDT too; the previous evening's Isha was EST.
        assertEquals(ZoneOffset.ofHours(-4), day.fajr.offset)
        assertEquals(ZoneOffset.ofHours(-5), before.isha.offset)
        // On the wall clock, Dhuhr appears ~1 hour later than the day before (solar noon barely moves).
        val wallShift = ChronoUnit.MINUTES.between(before.dhuhr.toLocalTime(), day.dhuhr.toLocalTime())
        assertTrue("wall-clock shift was $wallShift min", wallShift in 58..62)
        // But the real interval between the two Dhuhrs is ~24h, not 25.
        val real = ChronoUnit.MINUTES.between(before.dhuhr, day.dhuhr)
        assertTrue("real interval was $real min", real in 1438..1442)
    }

    @Test fun fallBackDayInNewYork() {
        // 2026-11-01: clocks go 02:00 → 01:00. The day is 25 hours long; times are still unambiguous instants.
        val before = PrayerCalculator.compute(LocalDate.of(2026, 10, 31), ny, nyc)
        val day = PrayerCalculator.compute(LocalDate.of(2026, 11, 1), ny, nyc)
        assertEquals(ZoneOffset.ofHours(-4), before.dhuhr.offset)
        assertEquals(ZoneOffset.ofHours(-4), before.isha.offset)
        assertEquals(ZoneOffset.ofHours(-5), day.fajr.offset)   // Fajr (~05:20) is after the 02:00→01:00 change, so EST
        assertEquals(ZoneOffset.ofHours(-5), day.dhuhr.offset)
        val real = ChronoUnit.MINUTES.between(before.dhuhr, day.dhuhr)
        assertTrue("real interval was $real min", real in 1438..1442)
        assertEquals(LocalDate.of(2026, 11, 1), day.fajr.toLocalDate())
        assertEquals(LocalDate.of(2026, 11, 1), day.isha.toLocalDate())
    }

    @Test fun fajrInsideTheRepeatedHourIsWellDefined() {
        // A place where Fajr falls in the 01:00–02:00 hour that repeats on fall-back day: pick a longitude/zone
        // combination where Fajr is ~01:30 local. Europe/London 2026-10-25 with a far-east longitude inside the zone
        // doesn't exist, so instead verify the generic property: converting the instant back and forth is stable.
        val z = ZoneId.of("America/New_York")
        val t = PrayerCalculator.compute(LocalDate.of(2026, 11, 1), z, nyc)
        for (p in Prayer.entries) {
            val zdt = t.at(p)
            assertEquals(t.instant(p), zdt.toInstant())
            assertEquals(zdt, zdt.toInstant().atZone(z))
        }
    }

    @Test fun kiritimatiUtcPlus14StaysOnItsOwnDate() {
        // UTC+14 with longitude 157°W: solar noon is ~22:30 UTC the previous day. Every time must land on the local date.
        val z = ZoneId.of("Pacific/Kiritimati")
        val c = Coordinates(1.8721, -157.4278)
        for (day in 1..28) {
            val d = LocalDate.of(2026, 2, day)
            val t = PrayerCalculator.compute(d, z, c)
            for (p in Prayer.entries) assertEquals("$d ${p.label}", d, t.at(p).toLocalDate())
            assertTrue(t.dhuhr.hour in 12..13)
        }
    }

    @Test fun samoaAndBakerIslandStyleOffsets() {
        // Pacific/Apia is UTC+13; also try a UTC-12 fixed offset at longitude 176°W (Baker Island has no zone).
        val apia = PrayerCalculator.compute(LocalDate.of(2026, 7, 4), ZoneId.of("Pacific/Apia"), Coordinates(-13.8506, -171.7513))
        assertEquals(LocalDate.of(2026, 7, 4), apia.dhuhr.toLocalDate()); assertTrue(apia.dhuhr.hour in 12..13)
        val baker = PrayerCalculator.compute(LocalDate.of(2026, 7, 4), ZoneOffset.ofHours(-12), Coordinates(0.1936, -176.4769))
        assertEquals(LocalDate.of(2026, 7, 4), baker.dhuhr.toLocalDate()); assertTrue(baker.dhuhr.hour in 11..13)
    }

    @Test fun nonWholeHourZones() {
        val kathmandu = PrayerCalculator.compute(LocalDate.of(2026, 4, 1), ZoneId.of("Asia/Kathmandu"), Coordinates(27.7172, 85.3240))
        assertTrue("Kathmandu Dhuhr ~12:0x, was ${kathmandu.dhuhr.toLocalTime()}", kathmandu.dhuhr.hour == 12 && kathmandu.dhuhr.minute < 15)
        val stJohns = PrayerCalculator.compute(LocalDate.of(2026, 1, 15), ZoneId.of("America/St_Johns"), Coordinates(47.5615, -52.7126))
        assertTrue("St John's Dhuhr ~12:2x, was ${stJohns.dhuhr.toLocalTime()}", stJohns.dhuhr.hour == 12 && stJohns.dhuhr.minute in 5..20)
    }

    @Test fun leapDayAndYearBoundary() {
        val c = Coordinates(33.6844, 73.0479) // Islamabad
        val z = ZoneId.of("Asia/Karachi")
        val leap = PrayerCalculator.compute(LocalDate.of(2028, 2, 29), z, c)
        assertEquals(LocalDate.of(2028, 2, 29), leap.dhuhr.toLocalDate())
        val nye = PrayerCalculator.compute(LocalDate.of(2026, 12, 31), z, c)
        val nyd = PrayerCalculator.compute(LocalDate.of(2027, 1, 1), z, c)
        assertTrue(ChronoUnit.MINUTES.between(nye.fajr, nyd.fajr) in 1438..1442)
        // After NYE Isha, "next" is New Year's Day Fajr
        val u = PrayerCalculator.next(nye.isha.plusMinutes(1).toInstant(), z, c)
        assertEquals(Prayer.FAJR, u.prayer); assertEquals(LocalDate.of(2027, 1, 1), u.time.toLocalDate())
    }

    @Test fun highLatitudeSummerUsesTheNightRule() {
        val oslo = Coordinates(59.9139, 10.7522); val z = ZoneId.of("Europe/Oslo"); val d = LocalDate.of(2026, 6, 21)
        for (rule in HighLatitudeRule.entries) {
            val t = PrayerCalculator.compute(d, z, oslo, CalculationParams(Method.MWL, highLatitudeRule = rule))
            assertTrue("$rule flags fajr", t.notes.contains(Note.FAJR_HIGH_LATITUDE))
            assertTrue("$rule flags isha", t.notes.contains(Note.ISHA_HIGH_LATITUDE))
            assertTrue(t.fajr < t.sunrise && t.maghrib < t.isha)
            val night = ChronoUnit.MINUTES.between(t.maghrib, PrayerCalculator.compute(d.plusDays(1), z, oslo).sunrise)
            val fajrPortion = ChronoUnit.MINUTES.between(t.fajr, t.sunrise)
            val ishaPortion = ChronoUnit.MINUTES.between(t.maghrib, t.isha)
            val expected = when (rule) {
                HighLatitudeRule.MIDDLE_OF_NIGHT -> night / 2
                HighLatitudeRule.ONE_SEVENTH -> night / 7
                HighLatitudeRule.ANGLE_BASED -> night * 18 / 60
            }
            assertTrue("$rule fajr portion $fajrPortion vs $expected", Math.abs(fajrPortion - expected) <= 2)
            if (rule != HighLatitudeRule.ANGLE_BASED) assertTrue("$rule isha portion $ishaPortion vs $expected", Math.abs(ishaPortion - expected) <= 2)
        }
        // In winter the rule is not needed at Oslo's latitude.
        val w = PrayerCalculator.compute(LocalDate.of(2026, 12, 21), z, oslo)
        assertTrue(w.notes.isEmpty())
    }

    @Test fun polarDayAndNightDoNotCrash() {
        for (lat in listOf(70.0, 78.2, 85.0, 89.9, -75.0, -89.9)) for (month in 1..12) {
            val c = Coordinates(lat, 15.0)
            val t = PrayerCalculator.compute(LocalDate.of(2026, month, 15), ZoneId.of("Europe/Oslo"), c)
            assertTrue("$lat/$month ordering: $t", t.fajr < t.sunrise && t.sunrise < t.dhuhr && t.dhuhr < t.asr && t.asr < t.maghrib && t.maghrib < t.isha)
            val next = PrayerCalculator.next(t.roundedToMinute().dhuhr.toInstant().plusSeconds(1), ZoneId.of("Europe/Oslo"), c)
            assertEquals(Prayer.ASR, next.prayer)
        }
        val midsummerSvalbard = PrayerCalculator.compute(LocalDate.of(2026, 6, 21), ZoneId.of("Arctic/Longyearbyen"), Coordinates(78.2232, 15.6267))
        assertTrue(midsummerSvalbard.notes.contains(Note.POLAR_LATITUDE_CLAMPED))
        val equator = PrayerCalculator.compute(LocalDate.of(2026, 6, 21), ZoneId.of("Africa/Nairobi"), Coordinates(0.0, 36.8))
        assertTrue(equator.notes.isEmpty())
    }

    @Test fun eachMethodProducesSensibleFajrAndIsha() {
        val c = Coordinates(30.0444, 31.2357) // Cairo
        val z = ZoneId.of("Africa/Cairo"); val d = LocalDate.of(2026, 10, 10)
        val sunset = PrayerCalculator.compute(d, z, c).maghrib
        for (m in Method.ALL) {
            val t = PrayerCalculator.compute(d, z, c, CalculationParams(m))
            val fajrToSunrise = ChronoUnit.MINUTES.between(t.fajr, t.sunrise)
            val maghribToIsha = ChronoUnit.MINUTES.between(sunset, t.isha)
            assertTrue("${m.id} fajr→sunrise $fajrToSunrise", fajrToSunrise in 60..110)
            assertTrue("${m.id} maghrib→isha $maghribToIsha", maghribToIsha in 55..100)
            assertTrue(t.notes.isEmpty())
        }
        // Larger Fajr angle → earlier Fajr, always.
        val a15 = PrayerCalculator.compute(d, z, c, CalculationParams(Method.custom(15.0, 15.0)))
        val a18 = PrayerCalculator.compute(d, z, c, CalculationParams(Method.custom(18.0, 18.0)))
        val a20 = PrayerCalculator.compute(d, z, c, CalculationParams(Method.custom(20.0, 20.0)))
        assertTrue(a20.fajr < a18.fajr && a18.fajr < a15.fajr)
        assertTrue(a20.isha > a18.isha && a18.isha > a15.isha)
    }
}
