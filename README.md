# Auqat-e-Namaz

**Prayer Times • Next Prayer • Qibla • Notifications — and nothing unnecessary.**

A deliberately small Android app. It calculates prayer times and the Qibla direction on the phone from your
coordinates, works fully offline, tracks you as you travel, and sends a notification (or plays the adhan) at each
prayer. No accounts, no analytics, no ads, no network access at all (the app does not even declare the INTERNET
permission), no third-party libraries.

## Layout

```
engine/   pure Kotlin/JVM — solar position, prayer-time calculator, method table, Qibla bearing. Unit-tested.
app/      the Android shell — one main screen, a Qibla compass, a settings page, alarms and notifications.
```

The engine has no Android dependency, so it is tested on the JVM against an independent solar-position
implementation and thousands of random dates/places (`./gradlew :engine:test`). The app is plain framework Views
built in code: no AndroidX, no Compose, no coroutines, no XML layouts. The release APK is about 12 MB, nearly all
of it the two adhan recordings and the offline town list; the code itself is a few hundred KB.

## Building

Requirements: Android Studio (or just the Android SDK) and JDK 17+. Then:

```
./gradlew :engine:test          # run the calculation tests
./gradlew :app:assembleDebug    # build a debug APK → app/build/outputs/apk/debug/
./gradlew :app:assembleRelease  # minified release APK; signed with the key in keystore.properties if present
```

Or open the folder in Android Studio and press Run.

Bundled data lives in `app/src/main/res/raw/`: `adhan.mp3` (the five daily prayers), `adhan_fajr.mp3` (Fajr, with
the extra "as-salatu khayrun min an-nawm" line) and `cities5000.txt` (the offline town list). See Credits below.

## How the times are calculated

Every prayer time is derived from the instant of local solar noon on the requested local date, as
noon ± the hour angle at which the sun reaches a given altitude:

| Time    | Sun altitude                                                   |
|---------|----------------------------------------------------------------|
| Fajr    | −(Fajr angle of the chosen method)                              |
| Sunrise | −0.833° − 0.0347·√elevation(m)  (refraction + solar radius + dip) |
| Dhuhr   | solar noon + 1 minute                                           |
| Asr     | atan(1 / (shadow factor + tan\|latitude − declination\|)), factor 1 (standard) or 2 (Hanafi) |
| Maghrib | sunset (same as sunrise, evening) — or a small angle for the Tehran/Jafari methods |
| Isha    | −(Isha angle) — or Maghrib + 90 minutes for Umm al-Qura / Qatar   |

Solar declination and the equation of time use the USNO low-precision formulas (accurate to ~1 arc-minute,
i.e. a few seconds of time), re-evaluated at each prayer's own time. All arithmetic is in UTC; the phone's time
zone is applied only when a time is displayed, so daylight-saving transitions are handled by `java.time`, not by
the calculator. Results are rounded to the nearest minute for display and alarms.

**Methods built in:** Muslim World League (18°/17°, default), ISNA (15°/15°), Egyptian (19.5°/17.5°),
Umm al-Qura (18.5°, Isha = Maghrib + 90 min), Karachi (18°/18°), Dubai (18.2°/18.2°), Kuwait (18°/17.5°),
Qatar (18°, +90 min), Singapore (20°/18°), Diyanet Turkey (18°/17°), Tehran (17.7°/14°, Maghrib 4.5°),
Jafari (16°/14°, Maghrib 4°), and Custom (any angles).

**High latitudes:** when the sun never reaches the Fajr/Isha angle (roughly above 48°–52° in summer), Fajr and
Isha are bounded by the middle-of-night rule and the screen says so. Above the polar circle, times follow the
nearest latitude that still has a sunrise. Both cases are flagged in the result (`PrayerTimes.notes`).

**Qibla:** initial great-circle bearing from your position to the Kaaba (21.422487° N, 39.826206° E), relative
to true north. The compass screen converts the phone's magnetic heading to true north using the local magnetic
declination (`GeomagneticField`), and warns when the sensor reports low accuracy or when the measured field
strength is outside the 25–65 µT range of the Earth's field (metal, magnets, cars, laptops).

## Location and travel

The app never tracks you. It reads the phone's last known position (free) and asks for one fresh fix each time
you open it. Moving more than 10 km recalculates times, Qibla and the pending alarm. With GPS there is no
dependence on a network, so it works in the middle of nowhere; the first fix outdoors after a long flight can
take a minute — the screen shows the previous location's times meanwhile and warns that they may be stale.

Manual location (Settings) is the fallback: enter coordinates, or copy the last GPS fix and edit it. Times always
use the phone's time zone; keep "automatic time zone" on when travelling.

## Alarms and notifications

Exactly one alarm is ever armed: the next prayer (`AlarmManager.setAlarmClock`, which survives Doze and shows the
system alarm icon). When it fires, the receiver notifies — or starts a short foreground service that plays the
adhan through the alarm audio stream — and arms the next one. Boot, time or time-zone changes, app updates,
settings changes and every app launch re-arm it, so the chain heals itself. Alarms delivered more than 30 minutes
late (phone was off) are dropped silently rather than announcing a prayer that has passed.

On Android 12/13 the app needs the *exact alarm* permission (Settings → "Allow exact alarms…"); on 13+ it is
granted automatically through `USE_EXACT_ALARM`. Some manufacturers (Xiaomi, Huawei, Samsung, OnePlus…) kill
background alarms unless the app is excluded from battery optimisation; there is a button for that too.

Permissions declared and why: fine/coarse location (calculation), background location (lets the alarm receiver
pick up a newer position while you travel; optional), notifications, exact alarms, boot completed, foreground
service for media playback (adhan), ignore battery optimisations (the request dialog).

## Settings

Appearance (follow phone / light / dark) · calculation method · custom Fajr/Isha angles · Hanafi/standard Asr ·
Hijri date ±2 days · per-prayer minute adjustments (−60…+60) · silent shade line on/off · for each of the six
times, Silent / Notification / Adhan, each with its own recording (bundled, or any audio file on the phone — the
file is copied into the app's private storage so it keeps working if the original is deleted) · manual location.
That is the whole list.

## Credits

* `adhan.mp3` — adhan by Mansour Al-Zahrani, from the collection at https://aladhan.com/download-adhans.
* `adhan_fajr.mp3` — Fajr adhan recorded in Doha, Qatar, from https://archive.org/details/adhan.recordings.from.doha.qatar.
  Both are distributed there as free recordings; rights holders who object are welcome to open an issue.
* Nearest-town names: [GeoNames](https://www.geonames.org) `cities5000` extract, licensed
  [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/). Bundled as `app/src/main/res/raw/cities5000.txt`.

## Tests

`engine/src/test`:

* `GoldenTest` — 36 city/date combinations across both hemispheres and the date line, checked to ±1 minute
  against an independent NOAA-algorithm implementation.
* `PropertyTest` — thousands of random dates and places: ordering of the six times, each on the requested local
  date, Hanafi Asr later than standard, adjustments shift exactly, elevation only affects sunrise/sunset,
  next-prayer logic across midnight, speed (ten years of times in well under a second).
* `EdgeCaseTest` — DST spring-forward and fall-back days, UTC+14 and UTC−12, half-hour and 45-minute zones,
  leap day, year boundary, high-latitude rules, polar day/night, every built-in method.
* `QiblaTest` — bearings for ten cities against published values, normalisation, distance.

## License

MIT — see `LICENSE`. If you redistribute, please keep the calculation transparent: the method table and the
formulas above are what make the times verifiable against your local mosque.
