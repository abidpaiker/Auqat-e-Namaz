package org.salawaqt.app

import android.content.Context
import java.time.Instant
import java.time.ZoneId
import android.os.Build
import android.view.WindowInsets
import android.graphics.Typeface
import android.text.format.DateFormat
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The few helpers needed to build the screens in code. No layout XML, no inflation. */
object Ui {
    fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density + 0.5f).toInt()

    fun column(ctx: Context, padding: Int = 20): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        val p = ctx.dp(padding); setPadding(p, p, p, p)
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun row(ctx: Context): LinearLayout = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    fun text(ctx: Context, s: CharSequence = "", sizeSp: Float = 16f, bold: Boolean = false, dim: Boolean = false, accent: Boolean = false): TextView = TextView(ctx).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setTextColor(color(ctx, if (accent) android.R.attr.colorAccent else if (dim) android.R.attr.textColorSecondary else android.R.attr.textColorPrimary))
    }

    /** Resolve a colour from the current theme, so light and dark modes both work. */
    fun color(ctx: Context, attr: Int): Int {
        val tv = TypedValue()
        ctx.theme.resolveAttribute(attr, tv, true)
        return if (tv.resourceId != 0) ctx.getColorStateList(tv.resourceId)?.defaultColor ?: tv.data else tv.data
    }

    /** Apply the user's appearance choice. Must be called before setContentView. Returns the theme id applied. */
    fun applyTheme(activity: android.app.Activity): Int {
        val choice = Prefs(activity).theme
        val systemDark = (activity.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        val dark = choice == 2 || (choice == 0 && systemDark)
        val id = if (dark) R.style.Theme_SalaWaqt_Dark else R.style.Theme_SalaWaqt
        activity.setTheme(id)
        return id
    }

    fun button(ctx: Context, label: String, onClick: () -> Unit): Button = Button(ctx).apply {
        text = label
        isAllCaps = false
        setOnClickListener { onClick() }
    }

    fun weight(v: android.view.View, w: Float) {
        v.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w)
    }

    fun space(ctx: Context, h: Int): android.view.View = android.view.View(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(h))
    }

    /** Android 15+ draws edge-to-edge by default; keep content out from under the status and navigation bars. */
    fun fitSystemBars(root: android.view.View) {
        if (Build.VERSION.SDK_INT < 30) return
        val l = root.paddingLeft; val t = root.paddingTop; val r = root.paddingRight; val b = root.paddingBottom
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(WindowInsets.Type.systemBars())
            v.setPadding(l + bars.left, t + bars.top, r + bars.right, b + bars.bottom)
            insets
        }
    }

    private val hijriMonths = arrayOf("Muharram", "Safar", "Rabi' al-Awwal", "Rabi' al-Thani", "Jumada al-Ula", "Jumada al-Akhirah",
        "Rajab", "Sha'ban", "Ramadan", "Shawwal", "Dhu al-Qi'dah", "Dhu al-Hijjah")

    /**
     * Hijri date for a moment. The Islamic day begins at sunset, so after today's Maghrib we show tomorrow's date.
     * Uses the Umm al-Qura calendar built into Android; [adjustDays] lets the user follow a local moon sighting.
     */
    fun hijriDate(now: Instant, maghribToday: Instant, zone: ZoneId, adjustDays: Int): String {
        var day = now.atZone(zone).toLocalDate()
        if (!now.isBefore(maghribToday)) day = day.plusDays(1)
        val h = java.time.chrono.HijrahDate.from(day).plus(adjustDays.toLong(), java.time.temporal.ChronoUnit.DAYS)
        return "${h.get(java.time.temporal.ChronoField.DAY_OF_MONTH)} ${hijriMonths[h.get(java.time.temporal.ChronoField.MONTH_OF_YEAR) - 1]} ${h.get(java.time.temporal.ChronoField.YEAR)} AH"
    }

    /** Local time in the user's 12/24-hour preference. */
    fun timeFormatter(ctx: Context): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(ctx)) "HH:mm" else "h:mm a", Locale.getDefault())

    /** "1h 05m" / "12m" / "45s" */
    fun countdown(seconds: Long): String {
        val s = seconds.coerceAtLeast(0)
        val h = s / 3600; val m = (s % 3600) / 60
        return when {
            h > 0 -> String.format(Locale.getDefault(), "%dh %02dm", h, m)
            m > 0 -> String.format(Locale.getDefault(), "%dm %02ds", m, s % 60)
            else -> String.format(Locale.getDefault(), "%ds", s)
        }
    }

    fun coords(lat: Double, lng: Double): String =
        String.format(Locale.getDefault(), "%.3f°%s, %.3f°%s", Math.abs(lat), if (lat >= 0) "N" else "S", Math.abs(lng), if (lng >= 0) "E" else "W")
}
