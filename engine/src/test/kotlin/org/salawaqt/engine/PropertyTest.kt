package org.salawaqt.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.random.Random

/** Invariants that must hold for every date and every place, checked over a few thousand random cases. */
class PropertyTest {

    private val zones = listOf("UTC", "America/New_York", "Europe/London", "Asia/Karachi", "Asia/Riyadh", "Australia/Sydney",
        "Pacific/Kiritimati", "Pacific/Honolulu", "America/Anchorage", "Asia/Kolkata", "Asia/Kathmandu", "America/St_Johns")

    private fun randomCase(r: Random): Triple<LocalDate, ZoneId, Coordinates> {
        val date = LocalDate.of(2020, 1, 1).plusDays(r.nextLong(0, 365L * 30))
        val zone = ZoneId.of(zones[r.nextInt(zones.size)])
        // Keep the longitude within ±25° of the zone's standard meridian, as real places are.
        val meridian = zone.rules.getStandardOffset(Instant.EPOCH).totalSeconds / 240.0
        var lng = meridian + r.nextDouble(-25.0, 25.0)
        if (lng >= 180) lng -= 360.0; if (lng < -180) lng += 360.0
        val c = Coordinates(r.nextDouble(-64.0, 64.0), lng, if (r.nextInt(4) == 0) r.nextDouble(0.0, 4000.0) else 0.0)
        return Triple(date, zone, c)
    }

    @Test fun prayersAreInOrderAndOnTheRequestedDay() {
        val r = Random(42)
        repeat(3000) {
            val (date, zone, c) = randomCase(r)
            for (method in Method.ALL) for (asr in AsrRule.entries) {
                val t = PrayerCalculator.compute(date, zone, c, CalculationParams(method, asr))
                val ctx = "$date $zone $c ${method.id} $asr -> $t"
                assertTrue("Fajr < Sunrise: $ctx", t.fajr < t.sunrise)
                assertTrue("Sunrise < Dhuhr: $ctx", t.sunrise < t.dhuhr)
                assertTrue("Dhuhr < Asr: $ctx", t.dhuhr < t.asr)
                assertTrue("Asr < Maghrib: $ctx", t.asr < t.maghrib)
                assertTrue("Maghrib < Isha: $ctx", t.maghrib < t.isha)
                assertEquals("Dhuhr on requested date: $ctx", date, t.dhuhr.toLocalDate())
                assertEquals("Sunrise on requested date: $ctx", date, t.sunrise.toLocalDate())
                assertTrue("Fajr within a day before sunrise: $ctx", ChronoUnit.HOURS.between(t.fajr, t.sunrise) < 12)
                assertTrue("Isha within a day after maghrib: $ctx", ChronoUnit.HOURS.between(t.maghrib, t.isha) < 12)
                assertFalse("no polar clamp below 64°: $ctx", t.notes.contains(Note.POLAR_LATITUDE_CLAMPED))
            }
        }
    }

    @Test fun hanafiAsrIsLaterThanStandard() {
        val r = Random(7)
        repeat(1000) {
            val (date, zone, c) = randomCase(r)
            val s = PrayerCalculator.compute(date, zone, c, CalculationParams(asrRule = AsrRule.STANDARD))
            val h = PrayerCalculator.compute(date, zone, c, CalculationParams(asrRule = AsrRule.HANAFI))
            assertTrue("Hanafi Asr later: $date $c", h.asr > s.asr)
            assertTrue("but within 2h: $date $c", ChronoUnit.MINUTES.between(s.asr, h.asr) in 2..120)
        }
    }

    @Test fun adjustmentsShiftExactly() {
        val c = Coordinates(31.5204, 74.3587) // Lahore
        val z = ZoneId.of("Asia/Karachi")
        val d = LocalDate.of(2026, 2, 10)
        val base = PrayerCalculator.compute(d, z, c)
        val adj = PrayerCalculator.compute(d, z, c, CalculationParams(adjustments = Adjustments(fajr = -3, sunrise = 1, dhuhr = 2, asr = 5, maghrib = 3, isha = -7)))
        assertEquals(-3L, ChronoUnit.MINUTES.between(base.fajr, adj.fajr))
        assertEquals(1L, ChronoUnit.MINUTES.between(base.sunrise, adj.sunrise))
        assertEquals(2L, ChronoUnit.MINUTES.between(base.dhuhr, adj.dhuhr))
        assertEquals(5L, ChronoUnit.MINUTES.between(base.asr, adj.asr))
        assertEquals(3L, ChronoUnit.MINUTES.between(base.maghrib, adj.maghrib))
        assertEquals(-7L, ChronoUnit.MINUTES.between(base.isha, adj.isha))
    }

    @Test fun elevationAdvancesSunriseAndDelaysSunset() {
        val z = ZoneId.of("America/Denver"); val d = LocalDate.of(2026, 6, 18)
        val sea = PrayerCalculator.compute(d, z, Coordinates(39.7392, -104.9903, 0.0))
        val mile = PrayerCalculator.compute(d, z, Coordinates(39.7392, -104.9903, 1609.0))
        assertTrue(mile.sunrise < sea.sunrise)
        assertTrue(mile.maghrib > sea.maghrib)
        assertEquals(sea.dhuhr, mile.dhuhr)                       // noon does not depend on elevation
        assertEquals(sea.fajr.toEpochSecond(), mile.fajr.toEpochSecond()) // nor do the angle-based times
        val shift = ChronoUnit.MINUTES.between(mile.sunrise, sea.sunrise)
        assertTrue("about 8 min for a mile of elevation, was $shift", shift in 6..11)
    }

    @Test fun roundingIsToNearestMinute() {
        val t = PrayerCalculator.compute(LocalDate.of(2026, 5, 5), ZoneId.of("UTC"), Coordinates(10.0, 10.0)).roundedToMinute()
        for (p in Prayer.entries) assertEquals(0, t.at(p).second)
    }

    @Test fun calculationIsFast() {
        val c = Coordinates(40.7, -74.0); val z = ZoneId.of("America/New_York")
        val start = System.nanoTime()
        var d = LocalDate.of(2026, 1, 1)
        repeat(3650) { PrayerCalculator.compute(d, z, c); d = d.plusDays(1) }
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("ten years of prayer times took ${ms}ms", ms < 2000)
    }

    @Test fun nextPrayerWrapsAroundMidnightAndReportsCurrent() {
        val c = Coordinates(40.7128, -74.0060); val z = ZoneId.of("America/New_York")
        val today = PrayerCalculator.compute(LocalDate.of(2026, 3, 10), z, c).roundedToMinute()
        // just after Isha → next is tomorrow's Fajr, current is Isha
        val afterIsha = today.isha.plusMinutes(5).toInstant()
        val u = PrayerCalculator.next(afterIsha, z, c)
        assertEquals(Prayer.FAJR, u.prayer)
        assertEquals(LocalDate.of(2026, 3, 11), u.time.toLocalDate())
        assertEquals(Prayer.ISHA, u.current)
        // between sunrise and dhuhr: current is SUNRISE (i.e. no prayer), next is Dhuhr
        val midMorning = today.sunrise.plusHours(1).toInstant()
        val v = PrayerCalculator.next(midMorning, z, c)
        assertEquals(Prayer.DHUHR, v.prayer); assertEquals(Prayer.SUNRISE, v.current)
        // 00:30 local: current is yesterday's Isha, next is today's Fajr
        val smallHours = ZonedDateTime.of(2026, 3, 10, 0, 30, 0, 0, z).toInstant()
        val w = PrayerCalculator.next(smallHours, z, c)
        assertEquals(Prayer.FAJR, w.prayer); assertEquals(LocalDate.of(2026, 3, 10), w.time.toLocalDate()); assertEquals(Prayer.ISHA, w.current)
        // exactly at a prayer time: that prayer is current and the next one is the following prayer
        val atAsr = today.asr.toInstant()
        val x = PrayerCalculator.next(atAsr, z, c)
        assertEquals(Prayer.MAGHRIB, x.prayer); assertEquals(Prayer.ASR, x.current)
    }

    @Test fun skipSunriseGoesStraightToDhuhr() {
        val c = Coordinates(40.7128, -74.0060); val z = ZoneId.of("America/New_York")
        val today = PrayerCalculator.compute(LocalDate.of(2026, 3, 10), z, c).roundedToMinute()
        val afterFajr = today.fajr.plusMinutes(10).toInstant()
        assertEquals(Prayer.SUNRISE, PrayerCalculator.next(afterFajr, z, c).prayer)
        val u = PrayerCalculator.next(afterFajr, z, c, skipSunrise = true)
        assertEquals(Prayer.DHUHR, u.prayer); assertEquals(Prayer.FAJR, u.current)
        // Just before sunrise on a day where Fajr is the last event before midnight? Not possible; but check across midnight:
        val lateNight = today.isha.plusHours(3).toInstant()
        assertEquals(Prayer.FAJR, PrayerCalculator.next(lateNight, z, c, skipSunrise = true).prayer)
    }

    @Test fun nextPrayerTimeIsAlwaysInTheFuture() {
        val r = Random(99)
        repeat(2000) {
            val (date, zone, c) = randomCase(r)
            val now = date.atStartOfDay(zone).plusSeconds(r.nextLong(0, 86400)).toInstant()
            val u = PrayerCalculator.next(now, zone, c)
            assertTrue(u.time.toInstant() > now)
            assertTrue("next within 24h", ChronoUnit.HOURS.between(now, u.time.toInstant()) < 24)
        }
    }
}
