package org.salawaqt.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.salawaqt.engine.Prayer
import java.time.Instant
import java.time.ZoneId

/** Fires at a prayer time. Notifies (or starts the adhan), then arms the next alarm. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Scheduler.ACTION_PREP -> prep(ctx)
            Scheduler.ACTION_PRAYER -> fire(ctx, intent)
        }
    }

    /**
     * A few minutes before the prayer: ask for one fresh fix (up to ~8 s, the receiver is kept alive with goAsync),
     * store it, and re-arm the alarm so it matches where the phone is now.
     */
    private fun prep(ctx: Context) {
        val prefs = Prefs(ctx)
        val pending = goAsync()
        var done = false
        val finish = {
            if (!done) { done = true; Scheduler.reschedule(ctx); pending.finish() }
        }
        val request = Locator.requestFresh(ctx) { loc -> Locator.store(prefs, loc); finish() }
        Handler(Looper.getMainLooper()).postDelayed({ request.cancel(); finish() }, 8_000)
    }

    private fun fire(ctx: Context, intent: Intent) {
        val prefs = Prefs(ctx)
        val key = intent.getStringExtra(Scheduler.EXTRA_KEY)
        val label = intent.getStringExtra(Scheduler.EXTRA_PRAYER) ?: "Prayer"
        val at = intent.getLongExtra(Scheduler.EXTRA_AT, 0L)

        // Cheap opportunistic location refresh (last known only, no active request in the background).
        Locator.lastKnown(ctx)?.let { Locator.store(prefs, it) }

        val late = System.currentTimeMillis() - at
        val mode = if (key == null) Prefs.SILENT else prefs.alertMode(key)
        // If the device was off and the alarm is delivered far too late, stay quiet rather than announce a prayer that passed.
        if (key != null && mode != Prefs.SILENT && late < 30 * 60_000L) {
            // Remember what fired, so moving to a place where this event is "later" does not fire it a second time.
            prefs.lastFired = "$key|${Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()}"
            Notifications.ensureChannels(ctx)
            // Sunrise has no bundled adhan: "sound" there means a chosen file, else the plain notification.
            val playAudio = mode == Prefs.ADHAN && (key != Prayer.SUNRISE.name || prefs.adhanFile(key) != null)
            if (playAudio) {
                val svc = Intent(ctx, AdhanService::class.java).setAction(AdhanService.ACTION_PLAY)
                    .putExtra(Scheduler.EXTRA_KEY, key).putExtra(Scheduler.EXTRA_PRAYER, label).putExtra(Scheduler.EXTRA_AT, at)
                // Exact alarms grant the background-start exemption; an inexact fallback alarm may not (Android 12+).
                try { ctx.startForegroundService(svc) } catch (e: IllegalStateException) { Notifications.showPrayer(ctx, label, at) }
            } else {
                Notifications.showPrayer(ctx, label, at)
            }
        }
        Scheduler.reschedule(ctx)
    }
}

/** Boot, time or timezone change, app update: re-arm the alarm from scratch. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> Scheduler.reschedule(ctx)
        }
    }
}
