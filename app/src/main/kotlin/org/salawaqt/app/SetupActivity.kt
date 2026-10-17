package org.salawaqt.app

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import org.salawaqt.app.Ui.dp
import org.salawaqt.engine.AsrRule
import org.salawaqt.engine.CalculationParams
import org.salawaqt.engine.Method
import org.salawaqt.engine.Prayer
import org.salawaqt.engine.PrayerCalculator
import java.time.Instant
import java.time.ZoneId

/**
 * First-run setup: five short pages, each with a sensible default already chosen, so "Next, Next, Next" gives a
 * working app. Shown once on a fresh install; everything here can be changed later in Settings.
 */
class SetupActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var pageHolder: FrameLayout
    private lateinit var stepText: TextView
    private lateinit var nextButton: android.widget.Button
    private lateinit var backButton: android.widget.Button
    private var page = 0
    private val pageCount = 5

    // page state
    private var methodGroup: RadioGroup? = null
    private var methodIds = ArrayList<String>()
    private var methodChosenByUser: String? = null   // null until the user taps one; the suggestion may still change as the fix arrives
    private var asrGroup: RadioGroup? = null
    private var bothAsr: CheckBox? = null
    private val modes = HashMap<String, Spinner>()
    private var locationStatus: TextView? = null
    private var locationRequest: Locator.Cancellable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.applyTheme(this)
        prefs = Prefs(this)
        Places.load(this) { runOnUiThread { if (page == 1 && methodChosenByUser == null) show(1) } }

        val root = Ui.column(this)
        root.addView(Ui.text(this, "Auqat-e-Namaz", 28f, bold = true, accent = true))
        stepText = Ui.text(this, "", 14f, dim = true)
        root.addView(stepText)
        root.addView(Ui.space(this, 12))
        pageHolder = FrameLayout(this)
        root.addView(pageHolder, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(Ui.space(this, 12))
        val buttons = Ui.row(this)
        backButton = Ui.button(this, "Back") { if (page > 0) show(page - 1) }
        nextButton = Ui.button(this, "Next") { onNext() }
        Ui.weight(backButton, 1f); Ui.weight(nextButton, 2f)
        buttons.addView(backButton); buttons.addView(nextButton)
        root.addView(buttons)
        Ui.fitSystemBars(root)
        setContentView(root)
        // Android 13+ with targetSdk 33+ routes the back gesture here instead of onBackPressed().
        if (Build.VERSION.SDK_INT >= 33)
            onBackInvokedDispatcher.registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT) { if (page > 0) show(page - 1) }
        show(0)
    }

    override fun onKeyDown(keyCode: Int, event: android.view.KeyEvent?): Boolean {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN && AdhanService.playing) { AdhanService.stop(this); return true }
        return super.onKeyDown(keyCode, event)
    }

    override fun onPause() { super.onPause(); locationRequest?.cancel(); locationRequest = null }
    override fun onBackPressed() { if (page > 0) show(page - 1) /* else: stay — the setup is finished with Next */ }

    private fun show(p: Int) {
        saveCurrent()
        page = p
        stepText.text = "Step ${p + 1} of $pageCount"
        backButton.visibility = if (p == 0) View.INVISIBLE else View.VISIBLE
        nextButton.text = if (p == pageCount - 1) "Finish" else "Next"
        pageHolder.removeAllViews()
        val content = when (p) { 0 -> locationPage(); 1 -> methodPage(); 2 -> asrPage(); 3 -> alertsPage(); else -> batteryPage() }
        pageHolder.addView(ScrollView(this).apply { addView(content) })
    }

    private fun onNext() {
        saveCurrent()
        when (page) {
            3 -> { // alerts chosen: ask for notification permission (Android 13+) before moving on
                if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), REQ_NOTIFY); return
                }
                show(4)
            }
            pageCount - 1 -> finishSetup()
            else -> show(page + 1)
        }
    }

    private fun finishSetup() {
        prefs.setupDone = true
        prefs.permissionsAsked = true
        Scheduler.reschedule(this)
        finish()
    }

    private fun saveCurrent() {
        when (page) {
            1 -> methodGroup?.let { g -> val i = g.checkedRadioButtonId - 1; if (i >= 0 && i < methodIds.size) prefs.methodId = methodIds[i] }
            2 -> { asrGroup?.let { prefs.hanafiAsr = it.checkedRadioButtonId == 2 }; bothAsr?.let { prefs.showBothAsr = it.isChecked } }
            3 -> for ((k, s) in modes) prefs.setAlertMode(k, s.selectedItemPosition)
        }
    }

    // ---- pages ----

    private fun para(s: String, dim: Boolean = false) = Ui.text(this, s, 16f, dim = dim).apply { setPadding(0, 0, 0, dp(12)) }
    private fun title(s: String) = Ui.text(this, s, 22f, bold = true).apply { setPadding(0, 0, 0, dp(10)) }

    private fun locationPage(): View {
        val c = Ui.column(this, 0)
        c.addView(title("Where are you?"))
        c.addView(para("Prayer times and the Qibla depend on your position. The app reads it from the phone's GPS and keeps it on the phone; nothing is ever sent anywhere, and the app has no internet access."))
        c.addView(para("When you travel, the times follow you. If you also allow location “all the time”, the app can take a fresh position a few minutes before each prayer while it is closed.", dim = true))
        val status = Ui.text(this, "", 15f, accent = true).apply { setPadding(0, 0, 0, dp(12)) }
        locationStatus = status
        c.addView(Ui.button(this, "Allow location") { askLocation() })
        c.addView(status)
        c.addView(para("You can also skip this and type a location in by hand later, in Settings.", dim = true))
        updateLocationStatus()
        return c
    }

    private fun askLocation() {
        if (!Locator.hasPermission(this)) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), REQ_LOCATION)
        } else askBackground()
    }

    private fun askBackground() {
        refreshFix()
        if (Build.VERSION.SDK_INT >= 29 && checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) != PackageManager.PERMISSION_GRANTED)
            Ui.backgroundLocationDialog(this) { requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), REQ_BACKGROUND) }
    }

    private fun refreshFix() {
        if (!Locator.hasPermission(this)) return
        Locator.lastKnown(this)?.let { Locator.store(prefs, it) }
        locationRequest?.cancel()
        locationRequest = Locator.requestFresh(this) { loc -> Locator.store(prefs, loc); updateLocationStatus() }
        updateLocationStatus()
    }

    private fun updateLocationStatus() {
        val s = locationStatus ?: return
        val where = prefs.coordinates()
        s.text = when {
            !Locator.hasPermission(this) -> ""
            where == null -> "Location allowed — waiting for a GPS fix…"
            else -> "Location: " + (Places.nearest(where.latitude, where.longitude)?.let { "$it · " } ?: "") + Ui.coords(where.latitude, where.longitude)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQ_LOCATION -> if (Locator.hasPermission(this)) askBackground() else updateLocationStatus()
            REQ_BACKGROUND -> refreshFix()
            REQ_NOTIFY -> show(4)
        }
    }

    private fun methodPage(): View {
        val c = Ui.column(this, 0)
        c.addView(title("Calculation method"))
        c.addView(para("Fajr and Isha depend on how far the sun is below the horizon, and different authorities use different angles. Pick the one your mosque or country follows; the suggestion is based on your location.", dim = true))
        val where = prefs.coordinates()
        val country = where?.let { Places.countryAt(it.latitude, it.longitude) }
        val suggested = Method.suggestedFor(country, where?.latitude ?: 0.0, where?.longitude ?: 0.0)
        val chosen = methodChosenByUser ?: suggested.id
        val g = RadioGroup(this)
        methodIds = ArrayList()
        for ((i, m) in Method.ALL.withIndex()) {
            methodIds += m.id
            g.addView(RadioButton(this).apply {
                id = i + 1            // 0 would collide with "no id"
                text = m.label + (if (m == suggested) "   (suggested)" else "")
                textSize = 16f
                setPadding(dp(4), dp(10), 0, dp(10))
            })
        }
        g.check(methodIds.indexOf(chosen).coerceAtLeast(0) + 1)
        g.setOnCheckedChangeListener { _, id -> if (id - 1 in methodIds.indices) methodChosenByUser = methodIds[id - 1] }
        methodGroup = g
        c.addView(g)
        c.addView(para("Custom angles and minute adjustments are available in Settings.", dim = true))
        return c
    }

    private fun asrPage(): View {
        val c = Ui.column(this, 0)
        c.addView(title("Asr"))
        c.addView(para("Asr begins when an object's shadow has grown by its own height (the Shafi'i, Maliki and Hanbali view) or by twice its height (the Hanafi view). Choose the one you follow.", dim = true))
        val where = prefs.coordinates()
        val zone = ZoneId.systemDefault()
        var stdTime = ""; var hanTime = ""
        if (where != null) {
            val fmt = Ui.timeFormatter(this)
            val m = Method.byId(prefs.methodId) ?: Method.MWL
            val t = PrayerCalculator.compute(Instant.now().atZone(zone).toLocalDate(), zone, where, CalculationParams(m, AsrRule.STANDARD)).roundedToMinute()
            stdTime = "   today " + fmt.format(t.asr); hanTime = "   today " + fmt.format(t.asrOther)
        }
        val g = RadioGroup(this)
        g.addView(RadioButton(this).apply { id = 1; text = "Standard (one shadow length)$stdTime"; textSize = 16f; setPadding(dp(4), dp(10), 0, dp(10)) })
        g.addView(RadioButton(this).apply { id = 2; text = "Hanafi (two shadow lengths)$hanTime"; textSize = 16f; setPadding(dp(4), dp(10), 0, dp(10)) })
        g.check(if (prefs.hanafiAsr) 2 else 1)
        asrGroup = g
        c.addView(g)
        c.addView(Ui.space(this, 12))
        bothAsr = CheckBox(this).apply { text = "Show both Asr times, each with its own alert"; textSize = 16f; isChecked = prefs.showBothAsr }
        c.addView(bothAsr)
        return c
    }

    private fun alertsPage(): View {
        val c = Ui.column(this, 0)
        c.addView(title("Alerts"))
        c.addView(para("For each time: nothing, a short notification sound, or the adhan. Sunrise is silent unless you want it. You can pick your own adhan recordings later in Settings.", dim = true))
        modes.clear()
        for (key in prefs.eventKeys()) {
            val r = Ui.row(this)
            val name = Ui.text(this, when (key) { Prefs.ASR2 -> "Asr (other rule)"; Prayer.ASR.name -> "Asr"; else -> Prayer.valueOf(key).label }, 16f)
            Ui.weight(name, 1f)
            val options = listOf("Silent", "Notification", if (key == Prayer.SUNRISE.name) "Chosen sound" else "Adhan")
            val sp = Spinner(this).apply {
                adapter = ArrayAdapter(this@SetupActivity, android.R.layout.simple_spinner_dropdown_item, options)
                setSelection(prefs.alertMode(key))
            }
            r.addView(name); r.addView(sp)
            r.setPadding(0, dp(6), 0, dp(6))
            c.addView(r)
            modes[key] = sp
        }
        c.addView(para("While the adhan plays, press volume-down to stop it.", dim = true))
        return c
    }

    private fun batteryPage(): View {
        val c = Ui.column(this, 0)
        c.addView(title("Keep the alarms alive"))
        c.addView(para("Some phones quietly stop apps from running in the background to save battery, and then the adhan never plays. Allow this app to run in the background so the prayer alarms always fire."))
        val pm = getSystemService(PowerManager::class.java)
        val status = Ui.text(this, "", 15f, accent = true).apply { setPadding(0, 0, 0, dp(12)) }
        fun refresh() { status.text = if (pm != null && pm.isIgnoringBatteryOptimizations(packageName)) "Background activity is allowed." else "" }
        c.addView(Ui.button(this, "Allow background activity") {
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName))
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            else refresh()
        })
        c.addView(status)
        refresh()
        if (Build.MANUFACTURER.equals("samsung", ignoreCase = true))
            c.addView(para("Samsung: also check Settings → Battery → Background usage limits, and make sure Auqat-e-Namaz is not listed under “Sleeping apps” or “Deep sleeping apps”.", dim = true))
        else
            c.addView(para("Xiaomi, Huawei, OnePlus and some others have their own battery managers; if alarms are missed, look for “autostart” or “battery saver” settings for this app.", dim = true))
        c.addView(para("That is everything. All of these choices can be changed any time in Settings.", dim = true))
        return c
    }

    override fun onResume() {
        super.onResume()
        if (page == 4) show(4)          // refresh the battery status after returning from the system dialog
        if (page == 0) refreshFix()
    }

    private companion object { const val REQ_LOCATION = 11; const val REQ_BACKGROUND = 12; const val REQ_NOTIFY = 13 }
}
