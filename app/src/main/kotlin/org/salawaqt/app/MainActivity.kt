package org.salawaqt.app

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.ScrollView
import android.widget.TextView
import org.salawaqt.app.Ui.dp
import org.salawaqt.engine.Note
import org.salawaqt.engine.Prayer
import org.salawaqt.engine.PrayerCalculator
import org.salawaqt.engine.PrayerTimes
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The one screen: next prayer, today's six times, where we think you are, and any warnings. */
class MainActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var nextName: TextView
    private lateinit var nextIn: TextView
    private lateinit var dateLine: TextView
    private lateinit var rows: List<Pair<TextView, TextView>>
    private lateinit var status: TextView
    private lateinit var warnings: TextView

    private val handler = Handler(Looper.getMainLooper())
    private var ticks = 0
    // Once a second: redraw the countdown. Once a minute while on screen: ask for a fresh fix, so a phone left on the
    // car mount keeps the times honest as you drive.
    private val tick = object : Runnable { override fun run() { render(); if (++ticks % 60 == 0) refreshLocation(); handler.postDelayed(this, 1000) } }
    private var locationRequest: Locator.Cancellable? = null
    private var timesCache: PrayerTimes? = null
    private var cacheKey: String = ""
    private var themeId = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        themeId = Ui.applyTheme(this)
        prefs = Prefs(this)
        Notifications.ensureChannels(this)
        Places.load(this) { runOnUiThread { render() } }

        val root = Ui.column(this)
        nextName = Ui.text(this, "—", 36f, bold = true, accent = true)
        nextIn = Ui.text(this, "", 18f, dim = true)
        dateLine = Ui.text(this, "", 14f, dim = true)
        // Tapping the header when there is no location re-asks for permission (the automatic ask happens only once).
        nextName.setOnClickListener { if (prefs.coordinates() == null) { prefs.permissionsAsked = false; requestPermissionsIfNeeded(); refreshLocation() } }
        root.addView(nextName); root.addView(nextIn); root.addView(Ui.space(this, 6)); root.addView(dateLine)
        root.addView(Ui.space(this, 20))

        rows = Prayer.entries.map { p ->
            val r = Ui.row(this)
            val name = Ui.text(this, p.label, 20f)
            val time = Ui.text(this, "", 20f)
            Ui.weight(name, 1f)
            r.addView(name); r.addView(time)
            r.setPadding(0, dp(10), 0, dp(10))
            root.addView(r)
            name to time
        }

        root.addView(Ui.space(this, 20))
        status = Ui.text(this, "", 14f, dim = true)
        warnings = Ui.text(this, "", 14f)
        root.addView(status); root.addView(Ui.space(this, 8)); root.addView(warnings)
        root.addView(Ui.space(this, 20))

        val buttons = Ui.row(this)
        val qibla = Ui.button(this, "Qibla") { startActivity(Intent(this, QiblaActivity::class.java)) }
        val settings = Ui.button(this, "Settings") { startActivity(Intent(this, SettingsActivity::class.java)) }
        Ui.weight(qibla, 1f); Ui.weight(settings, 1f)
        buttons.addView(qibla); buttons.addView(settings)
        root.addView(buttons)

        setContentView(ScrollView(this).apply { addView(root); Ui.fitSystemBars(this) })
    }

    override fun onResume() {
        super.onResume()
        if (Ui.applyTheme(this) != themeId) { recreate(); return }
        requestPermissionsIfNeeded()
        refreshLocation()
        render()
        handler.post(tick)
        Scheduler.reschedule(this)   // settings may have changed; also self-heals the alarm chain
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
        locationRequest?.cancel(); locationRequest = null
    }

    // ---- location ----

    private fun refreshLocation() {
        if (prefs.manualLocation) return
        Locator.lastKnown(this)?.let { if (Locator.store(prefs, it)) Scheduler.reschedule(this) }
        locationRequest?.cancel()
        locationRequest = Locator.requestFresh(this) { loc ->
            if (Locator.store(prefs, loc)) { timesCache = null; Scheduler.reschedule(this); }
            render()
        }
    }

    /** Ask once. If the user says no, the screen explains what is missing instead of nagging on every resume. */
    private fun requestPermissionsIfNeeded() {
        if (prefs.permissionsAsked) return
        val wanted = ArrayList<String>()
        if (!Locator.hasPermission(this)) { wanted += Manifest.permission.ACCESS_FINE_LOCATION; wanted += Manifest.permission.ACCESS_COARSE_LOCATION }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
            wanted += Manifest.permission.POST_NOTIFICATIONS
        if (wanted.isNotEmpty()) requestPermissions(wanted.toTypedArray(), 1) else prefs.permissionsAsked = true
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1) {
            prefs.permissionsAsked = true
            if (Locator.hasPermission(this)) {
                refreshLocation()
                // Background location (Android 10+) lets the alarm receiver pick up a newer fix while you travel.
                // It must be requested on its own, after foreground location. On 11+ this opens a settings page.
                if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 2)
            }
        }
    }

    // ---- rendering ----

    private fun render() {
        val zone = ZoneId.systemDefault()
        val now = Instant.now()
        val where = prefs.coordinates()
        val fmt = Ui.timeFormatter(this)
        val today = now.atZone(zone).toLocalDate()

        dateLine.text = today.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())) +
            " · " + zone.getDisplayName(TextStyle.SHORT, Locale.getDefault())

        if (where == null) {
            nextName.text = "No location"
            nextIn.text = if (Locator.hasPermission(this)) "Waiting for a GPS fix…" else "Tap here to allow location, or set one in Settings"
            for ((_, t) in rows) t.text = "—"
            status.text = ""
            warnings.text = ""
            return
        }

        // Recompute only when the inputs change (date, place, settings); otherwise once a day is plenty.
        val key = "$today|${where.latitude}|${where.longitude}|${where.elevation}|${prefs.params()}|$zone"
        val times = if (key == cacheKey && timesCache != null) timesCache!! else
            PrayerCalculator.compute(today, zone, where, prefs.params()).roundedToMinute().also { timesCache = it; cacheKey = key }

        // Islamic date: advances at Maghrib, not at midnight.
        dateLine.text = dateLine.text.toString() + "\n" + Ui.hijriDate(now, times.instant(Prayer.MAGHRIB), zone, prefs.hijriAdjust)

        val up = PrayerCalculator.next(now, zone, where, prefs.params(), skipSunrise = true)
        val cur = up.current
        nextName.text = up.prayer.label
        nextIn.text = "in " + Ui.countdown(up.time.toEpochSecond() - now.epochSecond) + " · " + fmt.format(up.time)

        for ((i, p) in Prayer.entries.withIndex()) {
            val (name, time) = rows[i]
            val z = times.at(p)
            var s = fmt.format(z)
            if (z.toLocalDate() != today) s += " (${if (z.toLocalDate() > today) "next day" else "prev. day"})"
            time.text = s
            val current = cur == p && p != Prayer.SUNRISE
            val style = if (current) Typeface.BOLD else Typeface.NORMAL
            name.setTypeface(Typeface.DEFAULT, style); time.setTypeface(Typeface.DEFAULT, style)
            val color = Ui.color(this, if (current) android.R.attr.colorAccent else android.R.attr.textColorPrimary)
            name.setTextColor(color); time.setTextColor(color)
            name.alpha = if (current || times.instant(p) > now) 1f else 0.5f   // dim what has passed today
            time.alpha = name.alpha
        }

        // Status line: where the numbers come from
        val src = if (prefs.manualLocation) "Manual location" else {
            val ageMin = (System.currentTimeMillis() - prefs.fixTime) / 60_000
            "GPS " + when {
                ageMin < 2 -> "just now"; ageMin < 120 -> "$ageMin min ago"; ageMin < 48 * 60 -> "${ageMin / 60} h ago"; else -> "${ageMin / 1440} days ago"
            }
        }
        val town = Places.nearest(where.latitude, where.longitude)?.let { "$it · " } ?: ""
        status.text = "$town${Ui.coords(where.latitude, where.longitude)} · $src · ${prefs.method().label}" +
            (if (prefs.hanafiAsr) " · Hanafi Asr" else "")

        // Warnings: only things that affect correctness
        val w = StringBuilder()
        if (!prefs.manualLocation && System.currentTimeMillis() - prefs.fixTime > 12 * 3600_000L)
            w.append("⚠ Location is old. If you have travelled, open the app outdoors or set a manual location.\n")
        if (Note.FAJR_HIGH_LATITUDE in times.notes || Note.ISHA_HIGH_LATITUDE in times.notes)
            w.append("ℹ High latitude: the sun does not reach the Fajr/Isha angle. Times use the middle-of-night rule.\n")
        if (Note.POLAR_LATITUDE_CLAMPED in times.notes)
            w.append("ℹ Polar day/night: times follow the nearest latitude with a sunrise.\n")
        if (prefs.notificationsEnabled) {
            val am = getSystemService(AlarmManager::class.java)
            if (am != null && !Scheduler.canScheduleExact(am))
                w.append("⚠ Exact alarms are not allowed, so notifications may be late. Fix in Settings.\n")
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED)
                w.append("⚠ Notifications are blocked for this app.\n")
        }
        warnings.text = w.toString().trimEnd()
    }
}
