package org.salawaqt.engine

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

object Qibla {
    /** The Kaaba, Makkah. */
    const val KAABA_LATITUDE = 21.422487
    const val KAABA_LONGITUDE = 39.826206

    /**
     * Initial great-circle bearing from [where] to the Kaaba, in degrees clockwise from TRUE north, [0, 360).
     * Compass sensors give MAGNETIC north; the app corrects with the local magnetic declination before comparing.
     */
    fun bearing(where: Coordinates): Double = bearing(where.latitude, where.longitude)

    fun bearing(latitude: Double, longitude: Double): Double {
        val lat1 = Math.toRadians(latitude)
        val lat2 = Math.toRadians(KAABA_LATITUDE)
        val dLon = Math.toRadians(KAABA_LONGITUDE - longitude)
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        val b = Math.toDegrees(atan2(y, x))
        return if (b < 0) b + 360.0 else b
    }

    /** Great-circle distance to the Kaaba in kilometres (haversine, mean Earth radius). Handy for the display. */
    fun distanceKm(where: Coordinates): Double {
        val r = 6371.0088
        val lat1 = Math.toRadians(where.latitude); val lat2 = Math.toRadians(KAABA_LATITUDE)
        val dLat = lat2 - lat1
        val dLon = Math.toRadians(KAABA_LONGITUDE - where.longitude)
        val h = sin(dLat / 2) * sin(dLat / 2) + cos(lat1) * cos(lat2) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * kotlin.math.asin(kotlin.math.sqrt(h))
    }
}
