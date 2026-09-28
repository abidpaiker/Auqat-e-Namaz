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
        val prayerName = intent.getStringExtra(Scheduler.EXTRA_PRAYER)
        val at = intent.getLongExtra(Scheduler.EXTRA_AT, 0L)

        // Cheap opportunistic location refresh (last known only, no active request in the background).
        Locator.lastKnown(ctx)?.let { Locator.store(prefs, it) }

        val late = System.currentTimeMillis() - at
        val prayer = prayerName?.let { runCatching { Prayer.valueOf(it) }.getOrNull() }
        // If the device was off and the alarm is delivered far too late, stay quiet rather than announce a prayer that passed.
        val mode = if (prayer == null) Prefs.SILENT else prefs.alertMode(prayer)
        if (prayer != null && mode != Prefs.SILENT && late < 30 * 60_000L) {
            // Remember what fired, so moving to a place where this prayer is "later" does not fire it a second time.
            prefs.lastFired = "${prayer.name}|${Instant.ofEpochMilli(at).atZone(ZoneId.systemDefault()).toLocalDate()}"
            Notifications.ensureChannels(ctx)
            // Sunrise has no bundled adhan: "sound" there means a chosen file, else the plain notification.
            val playAudio = mode == Prefs.ADHAN && (prayer != Prayer.SUNRISE || prefs.adhanFile(prayer) != null)
            if (playAudio) {
                val svc = Intent(ctx, AdhanService::class.java).setAction(AdhanService.ACTION_PLAY)
                    .putExtra(Scheduler.EXTRA_PRAYER, prayer.label).putExtra(Scheduler.EXTRA_AT, at)
                // Exact alarms grant the background-start exemption; an inexact fallback alarm may not (Android 12+).
                try { ctx.startForegroundService(svc) } catch (e: IllegalStateException) { Notifications.showPrayer(ctx, prayer.label, at) }
            } else {
                Notifications.showPrayer(ctx, prayer.label, at)
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
