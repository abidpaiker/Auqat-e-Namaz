package org.salawaqt.engine

import java.time.LocalDate

/**
 * How the Fajr and Isha twilight times are found.
 *
 * Most methods are a fixed sun-depression angle. Two are not: the Moonsighting Committee uses a seasonal
 * number of minutes before sunrise / after sunset (derived from observation), and the UK method uses a
 * seasonal angle fitted to the observation-based London Unified Prayer Timetable.
 */
enum class Twilight {
    /** [Method.fajrAngle] / [Method.ishaAngle] all year. */
    FIXED_ANGLE,
    /** Moonsighting Committee Worldwide: 18° but never earlier (Fajr) / later (Isha) than the seasonal minutes rule. */
    MOONSIGHTING,
    /** Angle varies smoothly with the day of the year (coefficients in [Method.seasonalFajr] / [Method.seasonalIsha]). */
    SEASONAL_ANGLE,
}

/**
 * A calculation method: a set of angles, plus a few fixed-minute conventions some authorities use.
 *
 * @property fajrAngle      sun depression (degrees below horizon) at which Fajr begins
 * @property ishaAngle      sun depression at which Isha begins; ignored when [ishaIntervalMinutes] > 0
 * @property ishaIntervalMinutes  if > 0, Isha is a fixed number of minutes after Maghrib (Umm al-Qura style)
 * @property maghribAngle   sun depression for Maghrib; 0 means sunset (the usual choice; Shia methods use a small angle)
 * @property maghribMinutes minutes added after sunset (Portugal, Jordan, the London timetable's safety margin)
 * @property sunriseMinutes minutes added to sunrise (negative = earlier; the London timetable takes 3 off)
 * @property dhuhrMinutes   minutes added after solar noon, on top of the one-minute disc clearance
 * @property seasonalFajr   SEASONAL_ANGLE only: c0, c1, s1, c2, s2 … of angle = c0 + Σ ck·cos(kω) + sk·sin(kω),
 *                          ω = 2π·(dayOfYear − 1)/365.25
 */
data class Method(
    val id: String,
    val label: String,
    val fajrAngle: Double,
    val ishaAngle: Double,
    val ishaIntervalMinutes: Int = 0,
    val maghribAngle: Double = 0.0,
    val maghribMinutes: Int = 0,
    val sunriseMinutes: Int = 0,
    val dhuhrMinutes: Int = 0,
    val twilight: Twilight = Twilight.FIXED_ANGLE,
    val seasonalFajr: DoubleArray = DoubleArray(0),
    val seasonalIsha: DoubleArray = DoubleArray(0),
) {
    companion object {
        val MWL       = Method("MWL",       "Muslim World League",                      18.0, 17.0)
        val ISNA      = Method("ISNA",      "Islamic Society of North America",         15.0, 15.0)
        val MOONSIGHT = Method("MOONSIGHT", "Moonsighting Committee Worldwide",         18.0, 18.0, twilight = Twilight.MOONSIGHTING)
        val EGYPT     = Method("EGYPT",     "Egyptian General Authority of Survey",     19.5, 17.5)
        val MAKKAH    = Method("MAKKAH",    "Umm al-Qura, Makkah",                      18.5, 0.0, ishaIntervalMinutes = 90)
        val KARACHI   = Method("KARACHI",   "Univ. of Islamic Sciences, Karachi",       18.0, 18.0)
        val GULF      = Method("GULF",      "Gulf Region",                              19.5, 0.0, ishaIntervalMinutes = 90)
        val DUBAI     = Method("DUBAI",     "Dubai",                                    18.2, 18.2)
        val KUWAIT    = Method("KUWAIT",    "Kuwait",                                   18.0, 17.5)
        val QATAR     = Method("QATAR",     "Qatar",                                    18.0, 0.0, ishaIntervalMinutes = 90)
        val JORDAN    = Method("JORDAN",    "Jordan (Ministry of Awqaf)",               18.0, 18.0, maghribMinutes = 5)
        val SINGAPORE = Method("SINGAPORE", "Majlis Ugama Islam Singapura",             20.0, 18.0)
        val JAKIM     = Method("JAKIM",     "JAKIM, Malaysia",                          20.0, 18.0)
        val KEMENAG   = Method("KEMENAG",   "Kementerian Agama, Indonesia",             20.0, 18.0)
        val TURKEY    = Method("TURKEY",    "Diyanet, Turkey",                          18.0, 17.0)
        val RUSSIA    = Method("RUSSIA",    "Spiritual Administration of Muslims of Russia", 16.0, 15.0)
        val FRANCE    = Method("FRANCE",    "UOIF, France",                             12.0, 12.0)
        val PORTUGAL  = Method("PORTUGAL",  "Comunidade Islâmica de Lisboa",            18.0, 0.0, ishaIntervalMinutes = 77, maghribMinutes = 3)
        val TUNISIA   = Method("TUNISIA",   "Tunisia",                                  18.0, 18.0)
        val ALGERIA   = Method("ALGERIA",   "Algeria",                                  18.0, 17.0)
        val MOROCCO   = Method("MOROCCO",   "Morocco",                                  19.0, 17.0)
        val TEHRAN    = Method("TEHRAN",    "Inst. of Geophysics, Univ. of Tehran",     17.7, 14.0, maghribAngle = 4.5)
        val JAFARI    = Method("JAFARI",    "Shia Ithna-Ashari (Jafari)",               16.0, 14.0, maghribAngle = 4.0)

        /**
         * United Kingdom. Fajr/Isha angles vary with the season (≈15–16° in winter, ≈12° / ≈9° in June), fitted
         * to the London Unified Prayer Timetable (Hizbul Ulama twilight data, 2026). Sunrise −3, Dhuhr +5 and
         * Maghrib +3 minutes are that timetable's published safety margins. Matches it to within ±3 minutes;
         * usable anywhere in Britain.
         */
        val UK = Method("UK", "United Kingdom (calculated)", 15.0, 15.0,
            sunriseMinutes = -3, dhuhrMinutes = 4, maghribMinutes = 3, twilight = Twilight.SEASONAL_ANGLE,
            seasonalFajr = doubleArrayOf(14.402, 1.654, -0.157, -0.847, 0.309, -0.335, -0.072, -0.261, -0.128, 0.004, 0.017),
            seasonalIsha = doubleArrayOf(12.405, 3.236, -0.296, -0.682, 0.225, -0.239, 0.258, -0.033, -0.075, -0.116, 0.145))

        /**
         * The London Unified Prayer Timetable itself (East London Mosque / Hizbul Ulama), supplied by the app as a
         * [Timetable]; the angles here are the UK calculation, used only where the table has no entry.
         */
        val LONDON = UK.copy(id = "LONDON", label = "London Unified Prayer Timetable")

        /** The built-in table, in the order the UI shows it. */
        val ALL: List<Method> = listOf(
            MWL, ISNA, MOONSIGHT, LONDON, UK, EGYPT, MAKKAH, KARACHI, GULF, DUBAI, KUWAIT, QATAR, JORDAN,
            SINGAPORE, JAKIM, KEMENAG, TURKEY, RUSSIA, FRANCE, PORTUGAL, TUNISIA, ALGERIA, MOROCCO, TEHRAN, JAFARI,
        )

        fun byId(id: String): Method? = ALL.firstOrNull { it.id == id }

        /** Great-circle distance in km (haversine). */
        fun distanceKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
            val r = 6371.0
            val dLat = Math.toRadians(lat2 - lat1); val dLng = Math.toRadians(lng2 - lng1)
            val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2)
            return 2 * r * Math.asin(Math.sqrt(a))
        }

        /** A user-defined method with explicit angles. */
        fun custom(fajrAngle: Double, ishaAngle: Double, ishaIntervalMinutes: Int = 0) =
            Method("CUSTOM", "Custom", fajrAngle, ishaAngle, ishaIntervalMinutes)

        /**
         * A sensible default for a place, used by the first-run setup. Country is an ISO 3166 code (from the
         * offline town list) or null; then latitude decides. Anyone can change it afterwards.
         */
        fun suggestedFor(countryCode: String?, latitude: Double, longitude: Double = 0.0): Method = when (countryCode) {
            "US", "CA" -> ISNA
            "GB" -> if (distanceKm(latitude, longitude, 51.5074, -0.1278) <= 60.0) LONDON else UK
            "IE" -> UK
            "SA", "YE", "OM" -> MAKKAH
            "AE" -> DUBAI
            "KW" -> KUWAIT
            "QA" -> QATAR
            "BH" -> GULF
            "JO" -> JORDAN
            "EG", "SD", "SY", "LB", "IQ", "LY" -> EGYPT
            "PK", "BD", "AF" -> KARACHI
            "IN", "LK", "NP" -> KARACHI
            "SG" -> SINGAPORE
            "MY", "BN" -> JAKIM
            "ID" -> KEMENAG
            "TR", "AZ" -> TURKEY
            "RU", "KZ", "KG", "UZ", "TJ", "TM" -> RUSSIA
            "FR" -> FRANCE
            "PT" -> PORTUGAL
            "TN" -> TUNISIA
            "DZ" -> ALGERIA
            "MA" -> MOROCCO
            "IR" -> TEHRAN
            else -> if (kotlin.math.abs(latitude) >= 48.0) MOONSIGHT else MWL
        }
    }

    // DoubleArray fields would break data-class equality; compare by id (the table is immutable).
    override fun equals(other: Any?): Boolean = other is Method && other.id == id &&
        (id != "CUSTOM" || (other.fajrAngle == fajrAngle && other.ishaAngle == ishaAngle && other.ishaIntervalMinutes == ishaIntervalMinutes))
    override fun hashCode(): Int = id.hashCode()
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

/**
 * A published timetable that replaces the calculation where it applies (e.g. the London Unified Prayer
 * Timetable inside London). Return null for dates or places it does not cover; the calculator then proceeds
 * as usual. Times are minutes after 00:00 UTC of the given date.
 */
interface Timetable {
    class Entry(val fajr: Int, val sunrise: Int, val dhuhr: Int, val asrStandard: Int, val asrHanafi: Int, val maghrib: Int, val isha: Int)
    fun lookup(date: LocalDate, where: Coordinates): Entry?
}

/** Everything that influences the calculation apart from place and date. */
data class CalculationParams(
    val method: Method = Method.MWL,
    val asrRule: AsrRule = AsrRule.STANDARD,
    val highLatitudeRule: HighLatitudeRule = HighLatitudeRule.MIDDLE_OF_NIGHT,
    val adjustments: Adjustments = Adjustments.NONE,
    val timetable: Timetable? = null,
)

/** A place on Earth. Elevation (metres above sea level) slightly advances sunrise and delays sunset. */
data class Coordinates(val latitude: Double, val longitude: Double, val elevation: Double = 0.0) {
    init {
        require(latitude in -90.0..90.0) { "latitude out of range: $latitude" }
        require(longitude in -180.0..180.0) { "longitude out of range: $longitude" }
    }
}
