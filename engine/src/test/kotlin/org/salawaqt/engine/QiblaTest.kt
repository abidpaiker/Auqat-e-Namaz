package org.salawaqt.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QiblaTest {
    /** Reference bearings (true north) as published by several independent Qibla calculators, ±0.5°. */
    private val known = listOf(
        Triple("New York", Coordinates(40.7128, -74.0060), 58.5),
        Triple("London", Coordinates(51.5074, -0.1278), 118.99),
        Triple("Karachi", Coordinates(24.8607, 67.0011), 267.7),
        Triple("Jakarta", Coordinates(-6.2088, 106.8456), 295.1),
        Triple("Cape Town", Coordinates(-33.9249, 18.4241), 23.1),
        Triple("Sydney", Coordinates(-33.8688, 151.2093), 277.5),
        Triple("Tokyo", Coordinates(35.6762, 139.6503), 293.0),
        Triple("San Francisco", Coordinates(37.7749, -122.4194), 18.8),
        Triple("Istanbul", Coordinates(41.0082, 28.9784), 151.6),
        Triple("Cairo", Coordinates(30.0444, 31.2357), 136.1),
    )

    @Test fun matchesPublishedBearings() {
        for ((city, c, bearing) in known) {
            val b = Qibla.bearing(c)
            assertEquals("$city qibla", bearing, b, 0.6)
        }
    }

    @Test fun bearingIsNormalised() {
        for (lat in -80..80 step 10) for (lng in -180 until 180 step 15) {
            val b = Qibla.bearing(lat.toDouble(), lng.toDouble())
            assertTrue("$lat,$lng -> $b", b >= 0.0 && b < 360.0)
        }
    }

    @Test fun dueEastWestAndNorthSouthCases() {
        // Directly south of the Kaaba on its meridian → bearing 0 (north); directly north → 180.
        assertEquals(0.0, Qibla.bearing(0.0, Qibla.KAABA_LONGITUDE), 1e-6)
        assertEquals(180.0, Qibla.bearing(60.0, Qibla.KAABA_LONGITUDE), 1e-6)
        // On the equator far west, the great circle heads north of east.
        assertTrue(Qibla.bearing(0.0, -60.0) in 60.0..90.0)
    }

    @Test fun distanceIsSensible() {
        assertEquals(0.0, Qibla.distanceKm(Coordinates(Qibla.KAABA_LATITUDE, Qibla.KAABA_LONGITUDE)), 1e-6)
        assertEquals(10300.0, Qibla.distanceKm(Coordinates(40.7128, -74.0060)), 100.0)  // New York ≈ 10,300 km
        assertEquals(4820.0, Qibla.distanceKm(Coordinates(51.5074, -0.1278)), 60.0)     // London ≈ 4,800 km
    }
}
