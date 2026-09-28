package org.salawaqt.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import android.os.Looper
import java.util.function.Consumer

/**
 * Location without Play Services: the framework LocationManager only.
 * We never track continuously. We read the last known fix (free) and ask for one fresh fix when the app opens.
 * Prayer times change by seconds per kilometre, so accuracy is irrelevant; a fix within 10 km is as good as GPS.
 */
object Locator {
    /** Moving less than this does not change prayer times or Qibla noticeably. */
    private const val SIGNIFICANT_MOVE_METRES = 10_000f

    fun hasPermission(ctx: Context): Boolean =
        ctx.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ctx.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** Best cached fix across providers: prefer the most recent, break ties by accuracy. Null if none or no permission. */
    fun lastKnown(ctx: Context): Location? {
        if (!hasPermission(ctx)) return null
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return null
        var best: Location? = null
        for (provider in lm.allProviders) {
            val l = try { lm.getLastKnownLocation(provider) } catch (_: SecurityException) { null } ?: continue
            val b = best
            if (b == null || l.time > b.time + 60_000 || (Math.abs(l.time - b.time) <= 60_000 && l.accuracy < b.accuracy)) best = l
        }
        return best
    }

    /**
     * Ask for one fresh fix from every enabled provider; [onFix] may be called more than once (network first, GPS later).
     * Returns a handle that must be cancelled when the caller goes away.
     */
    fun requestFresh(ctx: Context, onFix: (Location) -> Unit): Cancellable {
        val none = Cancellable {}
        if (!hasPermission(ctx)) return none
        val lm = ctx.getSystemService(LocationManager::class.java) ?: return none
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return none

        if (Build.VERSION.SDK_INT >= 30) {
            val signal = CancellationSignal()
            for (p in providers) {
                try {
                    lm.getCurrentLocation(p, signal, ctx.mainExecutor, Consumer { loc -> if (loc != null) onFix(loc) })
                } catch (_: SecurityException) { }
            }
            return Cancellable { signal.cancel() }
        } else {
            val listeners = ArrayList<LocationListener>()
            for (p in providers) {
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) = onFix(location)
                    @Deprecated("Deprecated in Java") override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {}
                    override fun onProviderEnabled(provider: String) {}
                    override fun onProviderDisabled(provider: String) {}
                }
                try {
                    @Suppress("DEPRECATION")
                    lm.requestSingleUpdate(p, listener, Looper.getMainLooper())
                    listeners += listener
                } catch (_: SecurityException) { }
            }
            return Cancellable { for (l in listeners) lm.removeUpdates(l) }
        }
    }

    /**
     * Store [loc] if it is newer than what we have. Returns true when the position moved enough that prayer
     * times and Qibla should be recalculated (or when this is the first fix).
     */
    fun store(prefs: Prefs, loc: Location): Boolean {
        if (prefs.hasFix && loc.time <= prefs.fixTime) return false
        val moved = if (!prefs.hasFix) true else {
            val d = FloatArray(1)
            Location.distanceBetween(prefs.fixLat, prefs.fixLng, loc.latitude, loc.longitude, d)
            d[0] > SIGNIFICANT_MOVE_METRES
        }
        prefs.saveFix(loc.latitude, loc.longitude, if (loc.hasAltitude()) loc.altitude else 0.0, loc.time)
        return moved
    }

    fun interface Cancellable { fun cancel() }
}
