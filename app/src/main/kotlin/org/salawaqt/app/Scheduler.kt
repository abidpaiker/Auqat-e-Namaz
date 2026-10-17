package org.salawaqt.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import java.time.Instant
import java.time.ZoneId

/**
 * Exactly one alarm is ever pending: the next event. When it fires, [AlarmReceiver] notifies and calls
 * [reschedule] again, so the chain continues. Boot, time and timezone changes, settings changes, and
 * every app open also call [reschedule], which makes the chain self-healing.
 */
object Scheduler {
    const val ACTION_PRAYER = "org.salawaqt.app.PRAYER"
    const val ACTION_PREP = "org.salawaqt.app.PREP"      // fires a few minutes before an event: refresh location
    const val PREP_MINUTES = 4L
    const val CATCH_UP_MS = 30 * 60_000L   // an event that began this recently and never alerted is announced immediately
    const val EXTRA_KEY = "key"            // event key: Prayer.name or Prefs.ASR2
    const val EXTRA_PRAYER = "prayer"      // display label
    const val EXTRA_AT = "at"

    fun reschedule(ctx: Context) {
        val prefs = Prefs(ctx)
        val am = ctx.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(ctx, null, null, 0L)
        if (prefs.coordinates() == null) { am.cancel(pi); am.cancel(prepIntent(ctx)); Notifications.hideStatus(ctx); return }

        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        // Sunrise only gets an alarm when the user asked to hear it; otherwise the chain skips straight to Dhuhr.
        val skipSunrise = !prefs.sunriseAudible
        var (next, current) = Events.around(prefs, now, zone, skipSunrise)
        // If we moved after an event alerted, the same event can look "upcoming" again at the new place. Skip it.
        if (next != null && next.id == prefs.lastFired) next = Events.around(prefs, next.time, zone, skipSunrise).first
        if (next == null) { am.cancel(pi); return }

        // The silent shade line is independent of the alarm: keep it current even when alerts are off.
        if (prefs.shadeEnabled) {
            Notifications.ensureChannels(ctx)
            Notifications.showStatus(ctx, next, Events.forDate(prefs, now.atZone(zone).toLocalDate(), zone))
        } else Notifications.hideStatus(ctx)

        // With alerts off we still arm the alarm so the shade line refreshes at each event; the receiver stays quiet.
        if (!prefs.notificationsEnabled && !prefs.shadeEnabled) { am.cancel(pi); am.cancel(prepIntent(ctx)); return }

        // Catch-up: after moving (e.g. driving east, where prayers come earlier), the current event at the new position
        // may have begun a few minutes ago without ever alerting. Fire it now — late is far better than skipped.
        if (current != null && !current.isSunrise) {
            val ageMs = now.toEpochMilli() - current.time.toEpochMilli()
            if (ageMs in 0..CATCH_UP_MS && prefs.lastFired != current.id && prefs.alertMode(current.key) != Prefs.SILENT) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis(),
                    pendingIntent(ctx, current.key, current.label, current.time.toEpochMilli()))
                return   // the receiver will call reschedule() again after alerting
            }
        }

        val at = next.time.toEpochMilli()
        val fire = pendingIntent(ctx, next.key, next.label, at)

        // A few minutes before the event, wake up and take a fresh GPS fix, so a traveller's adhan is for where
        // they are now, not where they were when the alarm was armed. Needs "Allow all the time" location; otherwise no-op.
        val prepAt = at - PREP_MINUTES * 60_000
        if (!prefs.manualLocation && prepAt > System.currentTimeMillis() + 30_000) {
            if (canScheduleExact(am)) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, prepAt, prepIntent(ctx))
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, prepAt, prepIntent(ctx))
        } else am.cancel(prepIntent(ctx))

        val show = PendingIntent.getActivity(ctx, 1, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        if (canScheduleExact(am)) {
            // setAlarmClock: exact, survives Doze, and the system shows the alarm icon so the user can see it is armed.
            am.setAlarmClock(AlarmManager.AlarmClockInfo(at, show), fire)
        } else {
            // Exact alarms denied by the user (Android 12/13 without the permission): best effort, may be minutes late.
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, fire)
        }
    }

    fun canScheduleExact(am: AlarmManager): Boolean = Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms()

    private fun prepIntent(ctx: Context): PendingIntent =
        PendingIntent.getBroadcast(ctx, 1, Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_PREP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun pendingIntent(ctx: Context, key: String?, label: String?, at: Long): PendingIntent {
        val i = Intent(ctx, AlarmReceiver::class.java).setAction(ACTION_PRAYER)
        if (key != null) i.putExtra(EXTRA_KEY, key).putExtra(EXTRA_PRAYER, label).putExtra(EXTRA_AT, at)
        // Same request code + same action/component → the same PendingIntent, so a new schedule replaces the old one.
        return PendingIntent.getBroadcast(ctx, 0, i, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
