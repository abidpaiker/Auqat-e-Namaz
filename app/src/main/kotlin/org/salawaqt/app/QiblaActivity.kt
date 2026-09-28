package org.salawaqt.app

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import org.salawaqt.app.Ui.dp
import org.salawaqt.engine.Qibla
import java.util.Locale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Compass pointing to the Kaaba. Heading comes from the rotation-vector sensor (Android's own fusion of
 * accelerometer + magnetometer + gyro); we correct magnetic → true north with the local declination, and
 * warn when the magnetometer looks unreliable. Sensors are registered only while this screen is visible.
 */
class QiblaActivity : Activity(), SensorEventListener {

    private lateinit var prefs: Prefs
    private lateinit var compass: CompassView
    private lateinit var bearingText: TextView
    private lateinit var headingText: TextView
    private lateinit var warning: TextView

    private var sensors: SensorManager? = null
    private var rotation: Sensor? = null
    private var accel: Sensor? = null
    private var magnet: Sensor? = null
    private var magnetic: Sensor? = null

    private var declination = 0f
    private var qiblaBearing = 0.0
    private var heading = Float.NaN            // smoothed true heading, degrees
    private var sensorAccuracy = SensorManager.SENSOR_STATUS_ACCURACY_HIGH
    private var fieldStrength = Float.NaN      // µT
    private val rotationMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var gravity: FloatArray? = null
    private var geomag: FloatArray? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ui.applyTheme(this)
        prefs = Prefs(this)
        val root = Ui.column(this).apply { gravity = Gravity.CENTER_HORIZONTAL }
        root.addView(Ui.text(this, "Qibla", 28f, bold = true, accent = true))
        bearingText = Ui.text(this, "", 16f, dim = true)
        root.addView(bearingText)
        root.addView(Ui.space(this, 16))
        compass = CompassView(this)
        root.addView(compass, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        headingText = Ui.text(this, "", 16f).apply { gravity = Gravity.CENTER }
        warning = Ui.text(this, "", 15f).apply { gravity = Gravity.CENTER; setPadding(0, dp(12), 0, 0) }
        root.addView(headingText); root.addView(warning)
        root.addView(Ui.space(this, 12))
        root.addView(Ui.button(this, "Back") { finish() })
        Ui.fitSystemBars(root)
        setContentView(root)

        sensors = getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        rotation = sensors?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotation == null) { // very old/cheap hardware: fuse accelerometer + magnetometer ourselves
            accel = sensors?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magnet = sensors?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
        magnetic = sensors?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    override fun onResume() {
        super.onResume()
        val where = prefs.coordinates()
        if (where == null) {
            bearingText.text = "No location yet — open the main screen first, or set a manual location."
            compass.hasBearing = false
            return
        }
        qiblaBearing = Qibla.bearing(where)
        declination = GeomagneticField(where.latitude.toFloat(), where.longitude.toFloat(), where.elevation.toFloat(), System.currentTimeMillis()).declination
        bearingText.text = String.format(Locale.getDefault(), "%.1f° from true north · %,.0f km to the Kaaba · declination %+.1f°",
            qiblaBearing, Qibla.distanceKm(where), declination)
        compass.hasBearing = true
        compass.qibla = qiblaBearing.toFloat()

        val sm = sensors ?: return
        if (rotation != null) sm.registerListener(this, rotation, SensorManager.SENSOR_DELAY_UI)
        else { accel?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }; magnet?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) } }
        magnetic?.let { sm.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
        if (magnetic == null) warning.text = "This device has no compass. Use the bearing above with a physical compass or a map."
    }

    override fun onPause() {
        super.onPause()
        sensors?.unregisterListener(this)
    }

    override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {
        if (sensor.type == Sensor.TYPE_ROTATION_VECTOR || sensor.type == Sensor.TYPE_MAGNETIC_FIELD) { sensorAccuracy = accuracy; updateWarning() }
    }

    override fun onSensorChanged(e: SensorEvent) {
        when (e.sensor.type) {
            Sensor.TYPE_ROTATION_VECTOR -> {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, e.values)
                SensorManager.getOrientation(rotationMatrix, orientation)
                onHeading(Math.toDegrees(orientation[0].toDouble()).toFloat())
            }
            Sensor.TYPE_ACCELEROMETER -> { gravity = e.values.clone(); fuse() }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                val v = e.values
                fieldStrength = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
                if (rotation == null) { geomag = v.clone(); fuse() }
                updateWarning()
            }
        }
    }

    private fun fuse() {
        val g = gravity ?: return; val m = geomag ?: return
        if (SensorManager.getRotationMatrix(rotationMatrix, null, g, m)) {
            SensorManager.getOrientation(rotationMatrix, orientation)
            onHeading(Math.toDegrees(orientation[0].toDouble()).toFloat())
        }
    }

    /** Magnetic azimuth in → smoothed true heading out (portrait only; the activity is locked to portrait). */
    private fun onHeading(magneticAzimuth: Float) {
        val trueHeading = norm(magneticAzimuth + declination)
        heading = if (heading.isNaN()) trueHeading else {
            var d = trueHeading - heading           // shortest way round the circle
            if (d > 180) d -= 360 else if (d < -180) d += 360
            norm(heading + d * 0.25f)
        }
        compass.heading = heading
        compass.invalidate()
        val off = norm(qiblaBearing.toFloat() - heading)
        val turn = if (off <= 180) "turn right %.0f°".format(Locale.getDefault(), off) else "turn left %.0f°".format(Locale.getDefault(), 360 - off)
        headingText.text = String.format(Locale.getDefault(), "Heading %.0f° · %s", heading, if (off < 3 || off > 357) "facing the Qibla" else turn)
    }

    private fun updateWarning() {
        val msgs = ArrayList<String>()
        if (sensorAccuracy <= SensorManager.SENSOR_STATUS_ACCURACY_LOW)
            msgs += "Compass needs calibration: move the phone in a figure-8."
        if (!fieldStrength.isNaN() && (fieldStrength < 25f || fieldStrength > 65f))
            msgs += "Magnetic interference (~%d µT). Move away from metal, magnets, cars and electronics.".format(Locale.getDefault(), (fieldStrength / 5).toInt() * 5)
        val t = msgs.joinToString("\n")
        if (warning.text.toString() != t) warning.text = t   // avoid relayout on every sensor event
        compass.unreliable = msgs.isNotEmpty()
    }

    private fun norm(a: Float): Float { var x = a % 360f; if (x < 0) x += 360f; return x }

    /** Dial with N/E/S/W, a north needle, and the Qibla arrow. Everything drawn with three Paints. */
    class CompassView(ctx: Context) : View(ctx) {
        var heading = 0f
        var qibla = 0f
        var hasBearing = false
        var unreliable = false
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = ctx.dp(2).toFloat() }
        private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER; textSize = ctx.dp(16).toFloat() }
        private val path = Path()
        private val accent = Ui.color(ctx, android.R.attr.colorAccent)
        private var textColor = Color.GRAY

        override fun onDraw(c: Canvas) {
            val cx = width / 2f; val cy = height / 2f
            val r = min(width, height) / 2f * 0.86f
            ring.color = if (unreliable) Color.argb(120, 200, 60, 60) else Color.argb(110, 128, 128, 128)
            c.drawCircle(cx, cy, r, ring)

            // Dial rotates against the heading so that north stays north.
            c.save(); c.rotate(-heading, cx, cy)
            fill.color = Color.argb(110, 128, 128, 128)
            for (i in 0 until 72) {
                val len = if (i % 18 == 0) r * 0.10f else if (i % 6 == 0) r * 0.06f else r * 0.03f
                val a = Math.toRadians(i * 5.0)
                c.drawLine(cx + (r - len) * sin(a).toFloat(), cy - (r - len) * cos(a).toFloat(), cx + r * sin(a).toFloat(), cy - r * cos(a).toFloat(), ring)
            }
            for ((i, s) in listOf("N", "E", "S", "W").withIndex()) {
                val a = Math.toRadians(i * 90.0)
                label.color = if (i == 0) Color.rgb(200, 60, 60) else textColor
                c.drawText(s, cx + r * 0.78f * sin(a).toFloat(), cy - r * 0.78f * cos(a).toFloat() + label.textSize / 3, label)
            }
            // Qibla arrow, fixed to the dial at its true bearing
            if (hasBearing) {
                c.save(); c.rotate(qibla, cx, cy)
                fill.color = accent
                path.reset()
                path.moveTo(cx, cy - r * 0.62f)
                path.lineTo(cx + r * 0.12f, cy - r * 0.30f)
                path.lineTo(cx, cy - r * 0.38f)
                path.lineTo(cx - r * 0.12f, cy - r * 0.30f)
                path.close()
                c.drawPath(path, fill)
                ring.color = accent
                c.drawLine(cx, cy - r * 0.38f, cx, cy + r * 0.25f, ring)
                c.restore()
            }
            c.restore()

            // Fixed marker at the top: what the phone is pointing at
            fill.color = textColor
            path.reset(); path.moveTo(cx, cy - r - ctx().dp(2)); path.lineTo(cx - ctx().dp(8), cy - r - ctx().dp(16)); path.lineTo(cx + ctx().dp(8), cy - r - ctx().dp(16)); path.close()
            c.drawPath(path, fill)
            fill.color = textColor
            c.drawCircle(cx, cy, ctx().dp(4).toFloat(), fill)
        }

        private fun ctx() = context

        override fun onAttachedToWindow() {
            super.onAttachedToWindow()
            // Pick up the theme's text colour so the dial is legible in light and dark mode.
            val tv = android.util.TypedValue()
            if (context.theme.resolveAttribute(android.R.attr.textColorPrimary, tv, true)) {
                textColor = if (tv.resourceId != 0) context.getColorStateList(tv.resourceId)?.defaultColor ?: Color.GRAY else tv.data
            }
        }
    }
}
