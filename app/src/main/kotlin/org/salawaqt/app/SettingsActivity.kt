package org.salawaqt.app

import android.app.Activity
import android.app.AlarmManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import org.salawaqt.app.Ui.dp
import org.salawaqt.engine.Method
import org.salawaqt.engine.Prayer
import java.io.File
import java.io.FileOutputStream

/** Every setting on one scrolling page. Values are saved when the screen is left, then the alarm is re-armed. */
class SettingsActivity : Activity() {

    private lateinit var prefs: Prefs
    private lateinit var method: Spinner
    private lateinit var appearance: Spinner
    private lateinit var customBox: LinearLayout
    private lateinit var customFajr: EditText
    private lateinit var customIsha: EditText
    private lateinit var customIshaMin: EditText
    private lateinit var hanafi: Switch
    private lateinit var hijriAdjust: EditText
    private lateinit var adjustments: Map<Prayer, EditText>
    private lateinit var shade: Switch
    private val modes = HashMap<Prayer, Spinner>()
    private val recordingStatus = HashMap<Prayer, TextView>()
    private lateinit var manual: Switch
    private lateinit var manualBox: LinearLayout
    private lateinit var manualLat: EditText
    private lateinit var manualLng: EditText

    private val methodIds = Method.ALL.map { it.id } + "CUSTOM"
    private val methodLabels = Method.ALL.map { it.label } + "Custom angles"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.applyTheme(this)
        prefs = Prefs(this)
        val root = Ui.column(this)
        root.addView(Ui.text(this, "Settings", 28f, bold = true, accent = true))
        root.addView(Ui.space(this, 12))

        // ---- appearance ----
        root.addView(heading("Appearance"))
        appearance = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, listOf("Follow the phone", "Light", "Dark"))
            setSelection(prefs.theme)
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                    if (pos != prefs.theme) { save(); prefs.theme = pos; recreate() }
                }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        root.addView(appearance)
        root.addView(Ui.space(this, 12))

        // ---- calculation ----
        root.addView(heading("Calculation"))
        root.addView(Ui.text(this, "Method", 14f, dim = true))
        method = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, methodLabels)
            setSelection(methodIds.indexOf(prefs.methodId).coerceAtLeast(0))
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) { customBox.visibility = if (methodIds[pos] == "CUSTOM") View.VISIBLE else View.GONE }
                override fun onNothingSelected(p: AdapterView<*>?) {}
            }
        }
        root.addView(method)

        customBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = if (prefs.methodId == "CUSTOM") View.VISIBLE else View.GONE }
        customFajr = number("Fajr angle (°)", prefs.customFajrAngle.toString(), decimal = true)
        customIsha = number("Isha angle (°)", prefs.customIshaAngle.toString(), decimal = true)
        customIshaMin = number("…or Isha = Maghrib + minutes (0 = use angle)", prefs.customIshaInterval.toString())
        customBox.addView(labelled("Fajr angle (°)", customFajr))
        customBox.addView(labelled("Isha angle (°)", customIsha))
        customBox.addView(labelled("Isha as minutes after Maghrib (0 = use the angle)", customIshaMin))
        root.addView(customBox)

        hanafi = switch("Hanafi Asr (shadow ×2, later Asr)", prefs.hanafiAsr)
        root.addView(hanafi)

        hijriAdjust = number("Hijri date ± days", prefs.hijriAdjust.toString(), signed = true)
        root.addView(labelled("Hijri date adjustment (days, to match local moon sighting)", hijriAdjust, inline = true))

        root.addView(Ui.space(this, 8))
        root.addView(Ui.text(this, "Minute adjustments (−60…+60), e.g. to match your local mosque", 14f, dim = true))
        adjustments = Prayer.entries.associateWith { p -> number(p.label, prefs.adjustment(p).toString(), signed = true) }
        for (p in Prayer.entries) root.addView(labelled(p.label, adjustments.getValue(p), inline = true))

        // ---- notifications ----
        root.addView(Ui.space(this, 16))
        root.addView(heading("Notifications"))
        shade = switch("Always show the next prayer in the notification shade (silent)", prefs.shadeEnabled)
        root.addView(shade)
        root.addView(Ui.space(this, 8))
        root.addView(Ui.text(this, "For each time choose Silent, Notification (short system sound) or Adhan. With Adhan you can pick your own recording; it is copied into the app, so it keeps working if the original file is deleted.", 13f, dim = true))
        for (p in Prayer.entries) root.addView(alertRow(p))
        root.addView(Ui.text(this, "While the adhan plays, press volume-down to stop it.", 13f, dim = true))
        root.addView(Ui.space(this, 16))
        if (Build.VERSION.SDK_INT >= 31) {
            root.addView(Ui.button(this, "Allow exact alarms…") {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }.also { val ok = getSystemService(AlarmManager::class.java)?.let { am -> Scheduler.canScheduleExact(am) } ?: true; it.visibility = if (ok) View.GONE else View.VISIBLE })
        }
        root.addView(Ui.button(this, "Exclude from battery optimisation…") {
            val pm = getSystemService(PowerManager::class.java)
            if (pm != null && !pm.isIgnoringBatteryOptimizations(packageName))
                startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$packageName")))
            else
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        })
        root.addView(Ui.text(this, "Some phones (Xiaomi, Huawei, Samsung, OnePlus…) silently kill alarms unless the app is excluded from battery optimisation.", 13f, dim = true))
        root.addView(Ui.button(this, "Notification settings…") {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        })

        // ---- location ----
        root.addView(Ui.space(this, 16))
        root.addView(heading("Location"))
        manual = switch("Use a manual location instead of GPS", prefs.manualLocation)
        manual.setOnCheckedChangeListener { _, on -> manualBox.visibility = if (on) View.VISIBLE else View.GONE }
        root.addView(manual)
        manualBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = if (prefs.manualLocation) View.VISIBLE else View.GONE }
        manualLat = number("Latitude", if (prefs.manualLocation || prefs.manualLat != 0.0) prefs.manualLat.toString() else "", decimal = true, signed = true)
        manualLng = number("Longitude", if (prefs.manualLocation || prefs.manualLng != 0.0) prefs.manualLng.toString() else "", decimal = true, signed = true)
        manualBox.addView(labelled("Latitude (−90…90, south negative)", manualLat))
        manualBox.addView(labelled("Longitude (−180…180, west negative)", manualLng))
        manualBox.addView(Ui.button(this, "Copy current GPS position") {
            if (prefs.hasFix) { manualLat.setText(prefs.fixLat.toString()); manualLng.setText(prefs.fixLng.toString()) }
        })
        root.addView(manualBox)
        root.addView(Ui.text(this, "Times always use the phone's time zone. When travelling, let the phone set its time zone automatically.", 13f, dim = true))

        root.addView(Ui.space(this, 24))
        root.addView(Ui.button(this, "Done") { finish() })
        val ver = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull() ?: "?"
        root.addView(Ui.text(this, "Auqat-e-Namaz $ver · offline · no accounts · no tracking · MIT licence", 12f, dim = true).apply { setPadding(0, dp(12), 0, 0) })
        setContentView(ScrollView(this).apply { addView(root); Ui.fitSystemBars(this) })
    }

    override fun onPause() {
        super.onPause()
        save()
        Scheduler.reschedule(this)
    }

    private fun save() {
        prefs.methodId = methodIds[method.selectedItemPosition]
        customFajr.text.toString().toFloatOrNull()?.let { if (it in 5f..25f) prefs.customFajrAngle = it }
        customIsha.text.toString().toFloatOrNull()?.let { if (it in 5f..25f) prefs.customIshaAngle = it }
        customIshaMin.text.toString().toIntOrNull()?.let { if (it in 0..180) prefs.customIshaInterval = it }
        prefs.hanafiAsr = hanafi.isChecked
        prefs.hijriAdjust = hijriAdjust.text.toString().toIntOrNull() ?: 0
        for ((p, e) in adjustments) prefs.setAdjustment(p, e.text.toString().toIntOrNull() ?: 0)
        prefs.shadeEnabled = shade.isChecked
        for ((p, s) in modes) prefs.setAlertMode(p, s.selectedItemPosition)
        val lat = manualLat.text.toString().toDoubleOrNull()
        val lng = manualLng.text.toString().toDoubleOrNull()
        val valid = lat != null && lng != null && lat in -90.0..90.0 && lng in -180.0..180.0
        if (valid) { prefs.manualLat = lat!!; prefs.manualLng = lng!! }
        prefs.manualLocation = manual.isChecked && valid
    }

    // ---- per-prayer alert rows ----

    /**
     * "Fajr  [Silent | Notification | Adhan]", and — only while Adhan is selected — the recording in use plus
     * Choose… / Default / Test. Sunrise has no bundled adhan, so its third choice is a sound of your own.
     */
    private fun alertRow(p: Prayer): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(6), 0, dp(2)) }
        val head = Ui.row(this)
        val name = Ui.text(this, p.label, 16f)
        Ui.weight(name, 1f)
        val options = listOf("Silent", "Notification", if (p == Prayer.SUNRISE) "Chosen sound" else "Adhan")
        val spinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, options)
            setSelection(prefs.alertMode(p))
        }
        head.addView(name); head.addView(spinner)
        box.addView(head)

        val recording = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, dp(4))
            visibility = if (prefs.alertMode(p) == Prefs.ADHAN) View.VISIBLE else View.GONE
        }
        val status = Ui.text(this, "", 13f, dim = true)
        val buttons = Ui.row(this)
        val choose = Ui.button(this, "Choose…") {
            // The system picker needs no storage permission; we copy the file into app storage right after.
            val i = Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("audio/*")
            startActivityForResult(i, PICK_BASE + p.ordinal)
        }
        val default = Ui.button(this, "Default") { prefs.clearAdhan(p); showRecording(p) }
        val test = Ui.button(this, "Test") { playTest(p) }
        Ui.weight(choose, 1f); Ui.weight(default, 1f); Ui.weight(test, 1f)
        buttons.addView(choose); buttons.addView(default); buttons.addView(test)
        recording.addView(status); recording.addView(buttons)
        box.addView(recording)

        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(a: AdapterView<*>?, v: View?, pos: Int, id: Long) { recording.visibility = if (pos == Prefs.ADHAN) View.VISIBLE else View.GONE }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
        modes[p] = spinner; recordingStatus[p] = status
        showRecording(p)
        return box
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        val p = Prayer.entries.getOrNull(requestCode - PICK_BASE) ?: return
        val name = displayName(uri)
        recordingStatus[p]?.text = "Copying…"
        // Copy into files/adhan/<PRAYER> on a background thread; the picker's URI is only valid briefly, the copy is ours for good.
        Thread {
            val dest = File(filesDir, "adhan/${p.name}")
            val tmp = File(dest.path + ".tmp")
            val ok = runCatching {
                dest.parentFile?.mkdirs()
                contentResolver.openInputStream(uri)!!.use { i -> FileOutputStream(tmp).use { o -> i.copyTo(o) } }
                dest.delete(); check(tmp.renameTo(dest))
            }.isSuccess
            if (!ok) tmp.delete()
            runOnUiThread {
                if (ok) prefs.setAdhanName(p, name)
                else Toast.makeText(this, "Could not read that file", Toast.LENGTH_LONG).show()
                showRecording(p)
            }
        }.start()
    }

    private fun showRecording(p: Prayer) {
        recordingStatus[p]?.text = prefs.adhanName(p)?.let { "Recording: $it" }
            ?: if (p == Prayer.SUNRISE) "No sound chosen yet — the notification sound will be used" else "Bundled recording"
    }

    private fun displayName(uri: Uri): String {
        val name = runCatching {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()
        return name ?: (uri.lastPathSegment ?: "chosen file")
    }

    private fun playTest(p: Prayer) {
        Notifications.ensureChannels(this)
        if (p == Prayer.SUNRISE && prefs.adhanFile(p) == null) { Notifications.showPrayer(this, p.label, System.currentTimeMillis()); return }
        val svc = Intent(this, AdhanService::class.java).setAction(AdhanService.ACTION_PLAY)
            .putExtra(Scheduler.EXTRA_PRAYER, p.label).putExtra(Scheduler.EXTRA_AT, System.currentTimeMillis())
        startForegroundService(svc)
    }

    private companion object { const val PICK_BASE = 10 }

    // ---- widgets ----

    private fun heading(s: String) = Ui.text(this, s, 20f, bold = true, accent = true).apply { setPadding(0, dp(8), 0, dp(8)) }

    private fun switch(label: String, checked: Boolean) = Switch(this).apply {
        text = label; isChecked = checked; setPadding(0, dp(10), 0, dp(10))
        textSize = 16f
    }

    private fun number(hint: String, value: String, decimal: Boolean = false, signed: Boolean = false) = EditText(this).apply {
        this.hint = hint
        inputType = InputType.TYPE_CLASS_NUMBER or (if (decimal) InputType.TYPE_NUMBER_FLAG_DECIMAL else 0) or (if (signed) InputType.TYPE_NUMBER_FLAG_SIGNED else 0)
        setText(value)
        setSelectAllOnFocus(true)
        minWidth = dp(90)
    }

    private fun labelled(label: String, field: EditText, inline: Boolean = false): View {
        if (!inline) return LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(Ui.text(this@SettingsActivity, label, 14f, dim = true)); addView(field) }
        val r = Ui.row(this)
        val t = Ui.text(this, label, 16f)
        Ui.weight(t, 1f)
        field.layoutParams = LinearLayout.LayoutParams(dp(110), ViewGroup.LayoutParams.WRAP_CONTENT)
        field.textAlignment = View.TEXT_ALIGNMENT_VIEW_END
        r.addView(t); r.addView(field)
        return r
    }
}
