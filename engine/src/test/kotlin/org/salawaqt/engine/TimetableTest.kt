package org.salawaqt.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TimetableTest {
    private val london = Coordinates(51.5074, -0.1278)
    private val zl = ZoneId.of("Europe/London")

    /** Covers only London-ish positions, and answers the same entry for every date. */
    private val fake = object : Timetable {
        override fun lookup(date: LocalDate, where: Coordinates): Timetable.Entry? =
            if (kotlin.math.abs(where.latitude - 51.5) < 0.5) Timetable.Entry(386, 483, 729, 826, 857, 965, 1062) else null
    }

    @Test fun timetableReplacesCalculationWhereItApplies() {
        val t = PrayerCalculator.compute(LocalDate.of(2026, 1, 1), zl, london, CalculationParams(Method.UK, timetable = fake))
        assertTrue(t.notes.contains(Note.TIMETABLE))
        // 386 min after 00:00 UTC = 06:26 UTC = 06:26 London in January
        assertEquals("06:26", t.fajr.toLocalTime().toString())
        assertEquals("13:46", t.asr.toLocalTime().toString())
        assertEquals("14:17", t.asrOther.toLocalTime().toString())
        val h = PrayerCalculator.compute(LocalDate.of(2026, 1, 1), zl, london, CalculationParams(Method.UK, AsrRule.HANAFI, timetable = fake))
        assertEquals("14:17", h.asr.toLocalTime().toString())
        assertEquals("13:46", h.asrOther.toLocalTime().toString())
    }

    @Test fun adjustmentsStillApplyToTimetableTimes() {
        val p = CalculationParams(Method.UK, adjustments = Adjustments(fajr = 2, isha = -3), timetable = fake)
        val t = PrayerCalculator.compute(LocalDate.of(2026, 1, 1), zl, london, p)
        assertEquals("06:28", t.fajr.toLocalTime().toString())
        assertEquals("17:39", t.isha.toLocalTime().toString())
    }

    @Test fun outsideCoverageFallsBackToCalculation() {
        val manchester = Coordinates(53.4808, -2.2426)
        val t = PrayerCalculator.compute(LocalDate.of(2026, 1, 1), zl, manchester, CalculationParams(Method.UK, timetable = fake))
        assertTrue(!t.notes.contains(Note.TIMETABLE))
        val plain = PrayerCalculator.compute(LocalDate.of(2026, 1, 1), zl, manchester, CalculationParams(Method.UK))
        assertEquals(plain.fajr, t.fajr)
    }
}
