package org.salawaqt.engine

/**
 * A calculation method is nothing more than a set of angles.
 *
 * @property fajrAngle      sun depression (degrees below horizon) at which Fajr begins
 * @property ishaAngle      sun depression at which Isha begins; ignored when [ishaIntervalMinutes] > 0
 * @property ishaIntervalMinutes  if > 0, Isha is a fixed number of minutes after Maghrib (Umm al-Qura style)
 * @property maghribAngle   sun depression for Maghrib; 0 means sunset (the usual choice; Shia methods use a small angle)
 */
data class Method(
    val id: String,
    val label: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaIntervalMinutes: Int = 0,
    val maghribAngle: Double = 0.0,
) {
    companion object {
        val MWL      = Method("MWL",      "Muslim World League",                     18.0, 17.0)
        val ISNA     = Method("ISNA",     "Islamic Society of North America",        15.0, 15.0)
        val EGYPT    = Method("EGYPT",    "Egyptian General Authority of Survey",    19.5, 17.5)
        val MAKKAH   = Method("MAKKAH",   "Umm al-Qura, Makkah",                     18.5, 0.0, ishaIntervalMinutes = 90)
        val KARACHI  = Method("KARACHI",  "Univ. of Islamic Sciences, Karachi",      18.0, 18.0)
        val DUBAI    = Method("DUBAI",    "Dubai (Gulf region)",                      18.2, 18.2)
        val KUWAIT   = Method("KUWAIT",   "Kuwait",                                   18.0, 17.5)
        val QATAR    = Method("QATAR",    "Qatar",                                    18.0, 0.0, ishaIntervalMinutes = 90)
        val SINGAPORE= Method("SINGAPORE","Majlis Ugama Islam Singapura",             20.0, 18.0)
        val TURKEY   = Method("TURKEY",   "Diyanet, Turkey",                          18.0, 17.0)
        val TEHRAN   = Method("TEHRAN",   "Inst. of Geophysics, Univ. of Tehran",     17.7, 14.0, maghribAngle = 4.5)
        val JAFARI   = Method("JAFARI",   "Shia Ithna-Ashari (Jafari)",               16.0, 14.0, maghribAngle = 4.0)

        /** The built-in table, in the order the UI shows it. */
        val ALL: List<Method> = listOf(MWL, ISNA, EGYPT, MAKKAH, KARACHI, DUBAI, KUWAIT, QATAR, SINGAPORE, TURKEY, TEHRAN, JAFARI)

        fun byId(id: String): Method? = ALL.firstOrNull { it.id == id }

        /** A user-defined method with explicit angles. */
        fun custom(fajrAngle: Double, ishaAngle: Double, ishaIntervalMinutes: Int = 0) =
            Method("CUSTOM", "Custom", fajrAngle, ishaAngle, ishaIntervalMinutes)
    }
}

/** Asr shadow rule: Asr begins when an object's shadow equals its noon shadow plus [shadowFactor] × its height. */
enum class AsrRule(val shadowFactor: Int) {
    STANDARD(1),   // Shafi'i, Maliki, Hanbali
    HANAFI(2),
}

/**
 * What to do at high latitudes when the sun never reaches the Fajr/Isha angle.
 * The rule bounds the night portion allotted to Fajr (before sunrise) and Isha (after sunset).
 */
enum class HighLatitudeRule {
    /** Fajr no earlier than mid-night; Isha no later than mid-night. */
    MIDDLE_OF_NIGHT,
    /** Fajr/Isha within one seventh of the night. */
    ONE_SEVENTH,
    /** Portion = angle / 60 of the night (e.g. 18° → 18/60 = 0.3 of the night). */
    ANGLE_BASED,
}

/** Per-prayer minute offsets applied after calculation. Positive = later. */
data class Adjustments(
    val fajr: Int = 0,
    val sunrise: Int = 0,
    val dhuhr: Int = 0,
    val asr: Int = 0,
    val maghrib: Int = 0,
    val isha: Int = 0,
) {
    companion object { val NONE = Adjustments() }
}

/** Everything that influences the calculation apart from place and date. */
data class CalculationParams(
    val method: Method = Method.MWL,
    val asrRule: AsrRule = AsrRule.STANDARD,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_NIGHT,
    val adjustments: Adjustments = Adjustments.NONE,
)

/** A place on Earth. Elevation (metres above sea level) slightly advances sunrise and delays sunset. */
data class Coordinates(val latitude: Double, val longitude: Double, val elevation: Double = 0.0) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }
}
