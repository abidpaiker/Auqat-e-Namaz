package org.salawaqt.app

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.VolumeProvider
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import org.salawaqt.engine.Prayer

/**
 * Plays the adhan once as a foreground service so the OS does not kill it mid-way. Uses USAGE_ALARM so it
 * sounds even when the ringer is silent (respecting the user's Do Not Disturb alarm rules). Stops itself
 * when the audio ends or when "Stop" is tapped.
 */
class AdhanService : Service() {
    companion object {
        const val ACTION_PLAY = "org.salawaqt.app.PLAY"
        const val ACTION_STOP = "org.salawaqt.app.STOP"
        /** True while an adhan is playing, so the app's screens can turn volume-down into "stop". */
        @Volatile var playing = false
        fun stop(ctx: android.content.Context) { if (playing) ctx.startService(Intent(ctx, AdhanService::class.java).setAction(ACTION_STOP)) }
    }

    /**
     * Second line of defence for the volume keys: when the system handles volume-down itself (some phones route
     * the keys past our media session after a few seconds), the volume goes down — and we stop on that.
     */
    private val volumeWatcher = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context, i: Intent) {
            val now = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1)
            val before = i.getIntExtra("android.media.EXTRA_PREV_VOLUME_STREAM_VALUE", -1)
            if (now in 0 until before) stop()
        }
    }
    private var watching = false

    private var player: MediaPlayer? = null
    private var session: MediaSession? = null
    private var playingLabel: String? = null

    /**
     * While the adhan plays, the hardware volume keys are routed to us through a MediaSession:
     * volume-down stops the adhan; volume-up raises the alarm volume as usual.
     */
    private fun grabVolumeKeys() {
        val am = getSystemService(AudioManager::class.java) ?: return
        val max = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
        val s = MediaSession(this, "adhan")
        s.setPlaybackState(PlaybackState.Builder().setState(PlaybackState.STATE_PLAYING, 0, 1f).setActions(PlaybackState.ACTION_STOP).build())
        s.setCallback(object : MediaSession.Callback() { override fun onStop() { stop() } })
        s.setPlaybackToRemote(object : VolumeProvider(VOLUME_CONTROL_RELATIVE, max, am.getStreamVolume(AudioManager.STREAM_ALARM)) {
            override fun onAdjustVolume(direction: Int) {
                if (direction < 0) { stop(); return }
                if (direction > 0) {
                    am.adjustStreamVolume(AudioManager.STREAM_ALARM, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    currentVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
                }
            }
        })
        s.isActive = true
        session = s
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP || intent == null) { stop(); return START_NOT_STICKY }

        val label = intent.getStringExtra(Scheduler.EXTRA_PRAYER) ?: "Prayer"
        val key = intent.getStringExtra(Scheduler.EXTRA_KEY) ?: Prayer.entries.firstOrNull { it.label == label }?.name
        val at = intent.getLongExtra(Scheduler.EXTRA_AT, System.currentTimeMillis())
        // Already playing this adhan (e.g. the alarm re-fired after a location change): keep going, do not restart.
        if (player?.isPlaying == true && playingLabel == label) return START_NOT_STICKY
        playingLabel = label
        playing = true
        if (!watching) {
            val filter = android.content.IntentFilter("android.media.VOLUME_CHANGED_ACTION")
            runCatching {
                // Android 13+ wants the export flag; a system broadcast still reaches a non-exported receiver.
                if (Build.VERSION.SDK_INT >= 33) registerReceiver(volumeWatcher, filter, android.content.Context.RECEIVER_NOT_EXPORTED)
                else registerReceiver(volumeWatcher, filter)
            }
            watching = true
        }
        val notification = Notifications.build(this, Notifications.CHANNEL_ADHAN, label, at, stopAction = true)
        if (Build.VERSION.SDK_INT >= 29)
            startForeground(Notifications.ID_ADHAN, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        else
            startForeground(Notifications.ID_ADHAN, notification)

        val prefs = Prefs(this)
        val custom = key?.let { prefs.adhanFile(it) }
        val bundled = (if (key != null) prefs.bundledFor(key) else Bundled.default(Prayer.DHUHR.name)).resId

        player?.release()
        session?.release(); grabVolumeKeys()
        player = MediaPlayer().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            setOnCompletionListener { this@AdhanService.stop() }
            setOnErrorListener { _, _, _ -> this@AdhanService.stop(); true }
            // The chosen file is our own private copy; if it is somehow unreadable, fall back to the bundled recording.
            val ok = custom != null && runCatching { setDataSource(custom.path); prepare() }.isSuccess
            if (!ok) {
                reset()
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                val bundledOk = runCatching {
                    resources.openRawResourceFd(bundled).use { afd -> setDataSource(afd.fileDescriptor, afd.startOffset, afd.length) }
                    prepare()
                }.isSuccess
                if (!bundledOk) {
                    // Nothing playable (should not happen with the shipped files): never crash at prayer time — notify instead.
                    Notifications.showPrayer(this@AdhanService, label, at)
                    this@AdhanService.stop()
                    return START_NOT_STICKY
                }
            }
            start()
        }
        return START_NOT_STICKY
    }

    private fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
        playingLabel = null
        playing = false
        if (watching) { runCatching { unregisterReceiver(volumeWatcher) }; watching = false }
        session?.let { it.isActive = false; it.release() }; session = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        playing = false
        if (watching) { runCatching { unregisterReceiver(volumeWatcher) }; watching = false }
        player?.release(); player = null
        session?.release(); session = null
        super.onDestroy()
    }
}
