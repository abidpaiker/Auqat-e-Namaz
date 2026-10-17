package org.salawaqt.app

import org.salawaqt.engine.Prayer

/**
 * The adhan recordings shipped inside the app (res/raw, Ogg Vorbis mono). Nasser al-Qatami, Majed al-Hamathani
 * and the Mishary Rashid al-Afasy Fajr adhan are from the Internet Archive item "Adhan Notifications"
 * (archive.org/details/adhan.notifications, Public Domain Mark); Makkah, Madina, Al-Ossi and the default Fajr
 * adhan are from the free collection at aladhan.com/download-adhans. Fajr recordings carry the
 * extra "as-salatu khayrun min an-nawm" line and are offered for Fajr only; the others for every prayer.
 */
object Bundled {
    class Recording(val id: String, val resId: Int, val label: String, val fajr: Boolean)

    val ALL: List<Recording> = listOf(
        // General — the first is the default
        Recording("b:makkah",             R.raw.adhan_makkah,                  "Makkah",                            fajr = false),
        Recording("b:madina",             R.raw.adhan_madina,                  "Madina",                            fajr = false),
        Recording("b:nasser_al_qatami",   R.raw.adhan_nasser_al_qatami,        "Nasser al-Qatami",                  fajr = false),
        Recording("b:majed_al_hamathani", R.raw.adhan_majed_al_hamathani,      "Majed al-Hamathani",                fajr = false),
        Recording("b:al_ossi",            R.raw.adhan_al_ossi,                 "Al-Ossi",                           fajr = false),
        // Fajr — the first is the default
        Recording("b:fajr_adhan",         R.raw.fajr_adhan,                    "Fajr adhan",                        fajr = true),
        Recording("b:fajr_mishary_rashid_al_afasy", R.raw.fajr_mishary_rashid_al_afasy, "Mishary Rashid al-Afasy (Fajr)", fajr = true),
    )

    /** The one used when the user has chosen nothing. */
    fun default(key: String): Recording = if (key == Prayer.FAJR.name) ALL.first { it.fajr } else ALL.first { !it.fajr }

    /** Bundled recordings offered for an event: Fajr sees only the Fajr ones; other prayers only the rest. */
    fun forKey(key: String): List<Recording> = when (key) {
        Prayer.FAJR.name -> ALL.filter { it.fajr }
        Prayer.SUNRISE.name -> emptyList()
        else -> ALL.filter { !it.fajr }
    }

    fun byId(id: String?): Recording? = if (id == null) null else ALL.firstOrNull { it.id == id }
}
