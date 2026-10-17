package org.salawaqt.app

import org.salawaqt.engine.Prayer
import org.salawaqt.engine.PrayerCalculator
import org.salawaqt.engine.PrayerTimes
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One thing that can alert: a prayer, or the second Asr when the user shows both. The main screen, the
 * scheduler and the receiver all work from this list, so they agree on what is "next".
 */
data class Event(val key: String, val label: String, val time: Instant, val date: LocalDate) {
    /** "MAGHRIB|2026-09-21": identifies one occurrence, for the "already fired" guard. */
    val id: String get() = "$key|$date"
    val isSunrise: Boolean get() = key == Prayer.SUNRISE.name
}

object Events {
    /** Label for an event key: the main Asr is plain "Asr"; the other rule's Asr says which rule it is. */
    fun label(prefs: Prefs, key: String): String = when (key) {
        Prefs.ASR2 -> if (prefs.hanafiAsr) "Asr (Standard)" else "Asr (Hanafi)"
        Prayer.ASR.name -> if (prefs.showBothAsr) (if (prefs.hanafiAsr) "Asr (Hanafi)" else "Asr (Standard)") else "Asr"
        else -> Prayer.valueOf(key).label
    }

    /** The day's events in time order, from already-computed (minute-rounded) times. */
    fun forDay(prefs: Prefs, times: PrayerTimes): List<Event> {
        val out = ArrayList<Event>(7)
        for (p in Prayer.entries) {
            out += Event(p.name, label(prefs, p.name), times.instant(p), times.date)
            if (p == Prayer.ASR && prefs.showBothAsr) out += Event(Prefs.ASR2, label(prefs, Prefs.ASR2), times.asrOtherInstant, times.date)
        }
        out.sortBy { it.time }
        return out
    }

    fun forDate(prefs: Prefs, date: LocalDate, zone: ZoneId): List<Event> {
        val where = prefs.coordinates() ?: return emptyList()
        return forDay(prefs, PrayerCalculator.compute(date, zone, where, prefs.params()).roundedToMinute())
    }

    /**
     * Next event strictly after [now] (today or tomorrow), optionally skipping sunrise, and the event that most
     * recently began (yesterday's if none today yet). Null next only when there is no location.
     */
    fun around(prefs: Prefs, now: Instant, zone: ZoneId, skipSunrise: Boolean): Pair<Event?, Event?> {
        val today = now.atZone(zone).toLocalDate()
        val t0 = forDate(prefs, today, zone)
        if (t0.isEmpty()) return null to null
        val n = now.epochSecond
        val okNext = { e: Event -> e.time.epochSecond > n && !(skipSunrise && e.isSunrise) }
        val next = t0.firstOrNull(okNext) ?: forDate(prefs, today.plusDays(1), zone).firstOrNull(okNext)
        val current = t0.lastOrNull { it.time.epochSecond <= n } ?: forDate(prefs, today.minusDays(1), zone).lastOrNull()
        return next to current
    }
}
