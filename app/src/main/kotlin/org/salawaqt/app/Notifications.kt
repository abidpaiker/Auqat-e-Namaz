package org.salawaqt.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.provider.Settings
import java.time.Instant
import java.time.ZoneId

object Notifications {
    const val CHANNEL_PRAYER = "prayer"   // plain notification with the system notification sound
    const val CHANNEL_ADHAN = "adhan"     // silent channel; the AdhanService plays the audio itself
    const val ID_PRAYER = 1
    const val ID_ADHAN = 2
    const val CHANNEL_STATUS = "status"  // silent, low importance: the persistent "next prayer" line in the shade
    const val ID_STATUS = 3

    fun ensureChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val prayer = NotificationChannel(CHANNEL_PRAYER, "Prayer time", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "A notification at each prayer time"
            setSound(Settings.System.DEFAULT_NOTIFICATION_URI,
                AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
            enableVibration(true)
        }
        val adhan = NotificationChannel(CHANNEL_ADHAN, "Adhan", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Shown while the adhan plays"
            setSound(null, null)
            enableVibration(false)
        }
        val status = NotificationChannel(CHANNEL_STATUS, "Next prayer (silent)", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Always-visible line in the notification shade with the next prayer and today's times"
            setSound(null, null); enableVibration(false); setShowBadge(false)
        }
        nm.createNotificationChannel(prayer)
        nm.createNotificationChannel(adhan)
        nm.createNotificationChannel(status)
    }

    fun build(ctx: Context, channel: String, prayerLabel: String, atMillis: Long, stopAction: Boolean): Notification {
        val time = Ui.timeFormatter(ctx).format(Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()))
        val open = PendingIntent.getActivity(ctx, 2, Intent(ctx, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val b = Notification.Builder(ctx, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(prayerLabel)
            .setContentText("$time · Time for $prayerLabel prayer")
            .setContentIntent(open)
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(!stopAction)
            .setOngoing(stopAction)
            .setShowWhen(true)
            .setWhen(atMillis)
        if (stopAction) {
            val stop = PendingIntent.getService(ctx, 3, Intent(ctx, AdhanService::class.java).setAction(AdhanService.ACTION_STOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            b.addAction(Notification.Action.Builder(null, "Stop", stop).build())
        }
        return b.build()
    }

    /** Post or refresh the silent shade line. Called whenever the alarm is (re)scheduled, i.e. after every prayer. */
    fun showStatus(ctx: Context, up: org.salawaqt.engine.Upcoming, today: org.salawaqt.engine.PrayerTimes) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        val fmt = Ui.timeFormatter(ctx)
        val open = PendingIntent.getActivity(ctx, 4, Intent(ctx, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val line = org.salawaqt.engine.Prayer.entries.joinToString("  ·  ") { "${it.label} ${fmt.format(today.at(it))}" }
        val n = Notification.Builder(ctx, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${up.prayer.label} at ${fmt.format(up.time)}")
            .setContentText(line)
            .setStyle(Notification.BigTextStyle().bigText(line))
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setCategory(Notification.CATEGORY_STATUS)
            .build()
        nm.notify(ID_STATUS, n)
    }

    fun hideStatus(ctx: Context) { ctx.getSystemService(NotificationManager::class.java)?.cancel(ID_STATUS) }

    fun showPrayer(ctx: Context, prayerLabel: String, atMillis: Long) {
        val nm = ctx.getSystemService(NotificationManager::class.java) ?: return
        nm.notify(ID_PRAYER, build(ctx, CHANNEL_PRAYER, prayerLabel, atMillis, stopAction = false))
    }
}
