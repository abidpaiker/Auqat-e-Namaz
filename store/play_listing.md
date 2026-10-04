# Auqat-e-Namaz — Play Store listing material

## Store listing

**App name** (30 chars max)
Auqat-e-Namaz

**Short description** (80 chars max)
Prayer times, Qibla and adhan. Offline, no ads, no accounts, no tracking.

**Full description** (4000 chars max)

Auqat-e-Namaz shows today's prayer times, counts down to the next prayer, points to the Qibla and plays the adhan — and does nothing else.

It was written for people who pray wherever they happen to be: on the road, in the middle of nowhere, in a country they landed in an hour ago. So it works entirely offline. Times are calculated on the phone from your GPS position; no signal is needed, ever. When you travel, the times follow you, and a few minutes before each prayer the app quietly checks where you are so the adhan is for the place you have reached, not the place you left.

WHAT IT DOES
• Today's six times: Fajr, Sunrise, Dhuhr, Asr, Maghrib, Isha, with the next prayer and a live countdown
• Islamic (Hijri) date that changes at Maghrib, adjustable ±2 days to match local moon sighting
• Qibla compass, with a warning when the phone's compass is being disturbed by metal or magnets
• For each prayer, your choice: silent, a short notification, or the adhan. Sunrise is silent unless you want it
• Two bundled adhan recordings (a separate one for Fajr), or pick any audio file on your phone for any prayer
• Exact alarms that survive sleep mode; volume-down stops the adhan
• An optional always-visible line in the notification shade with the next prayer and today's times
• Nearest town shown next to your coordinates, from an offline list
• Light and dark themes

CALCULATION
Muslim World League (default), ISNA, Egyptian, Umm al-Qura, Karachi, Dubai, Kuwait, Qatar, Singapore, Turkey, Tehran, Jafari, or your own angles. Standard or Hanafi Asr. Per-prayer minute adjustments to match your mosque. High-latitude rules for the far north. The formulas are documented and the calculator is tested against an independent solar-position implementation.

WHAT IT DOES NOT DO
No adverts. No accounts. No analytics. No internet permission at all — the app cannot send anything anywhere. No third-party code. Your location never leaves the phone.

PERMISSIONS
Location: to calculate times and the Qibla for where you are. "Allow all the time" is optional; it lets the app refresh your position shortly before a prayer while you travel. You can also type a location in by hand and refuse location permission entirely.
Notifications and alarms: to alert you at prayer times.

This app is not a replacement for the well-known, feature-rich Islamic apps. It is the opposite: as little as possible, done carefully, for people who want prayer times and nothing else.

Free and open source (MIT licence): https://github.com/abidpaiker/Auqat-e-Namaz

**Category:** Lifestyle
**Tags:** Prayer times, Qibla, Adhan, Islam, Salah, Namaz
**Contact email:** abidpaiker@gmail.com
**Website:** https://abidpaiker.github.io/Auqat-e-Namaz/
**Privacy policy:** https://abidpaiker.github.io/Auqat-e-Namaz/privacy

**Graphics:** icon_512.png (app icon), feature_1024x500.png (feature graphic), plus 2–8 phone screenshots
(take on the phone; Google accepts the phone's native resolution).

## Content rating questionnaire
Category: Utility, Productivity, Communication, or Other. Answer No to every question (no violence, no sexual content, no
user interaction, no user-generated content, no sharing of location with others, no purchases). Result: Everyone / PEGI 3.

## Data safety form
* Does your app collect or share any of the required user data types? **No.**
  (Location is used on-device only and never transmitted; the app has no INTERNET permission. Google's definition of
  "collect" is transmitting data off the device, so the honest answer is No.)
* Is all of the user data collected by your app encrypted in transit? — not asked when nothing is collected.
* Do you provide a way for users to request that their data is deleted? — not asked when nothing is collected.
Summary shown to users: "No data collected. No data shared with third parties."

## App content declarations
* **Ads:** No, my app does not contain ads.
* **Target audience:** 18 and over (simplest; avoids the Families policy). The app is suitable for all ages, but
  declaring children as a target audience triggers extra requirements.
* **News app:** No. **COVID-19 app:** No. **Government app:** No. **Financial features:** No. **Health:** No.
* **App access:** All functionality is available without special access (no login).

## Sensitive permissions declarations

**Location permissions (ACCESS_BACKGROUND_LOCATION)**
Core feature: prayer times and Qibla direction depend on the user's location. Background location is used only to
take a single fresh position fix a few minutes before each scheduled prayer alarm, so that a user who has travelled
since last opening the app is alerted at the correct time for where they now are. No location data is stored beyond
the most recent fix, and none is transmitted; the app does not declare the INTERNET permission. Users can decline
background location (the app then refreshes only when opened) or enter a manual location.
Video: a short screen recording showing the main screen with times, Settings → "Use a manual location", and the
permission prompt explaining "Allow all the time" — Google asks for this; record 30–60 s on the phone.

**Exact alarm permission (USE_EXACT_ALARM)**
The app's core function is time-critical alerts: notifying the user, or playing the adhan, at the precise minute a
prayer begins. Inexact alarms can be delayed by many minutes, which defeats the purpose.

**Foreground service (MEDIA_PLAYBACK)**
Used only to play the adhan recording (1–3 minutes) at prayer time so the system does not stop it mid-way. Started
by an exact alarm; shows a notification with a Stop button; stops itself when the recording ends.

**REQUEST_IGNORE_BATTERY_OPTIMIZATIONS**
Offered as an opt-in button in Settings because several manufacturers' battery managers kill scheduled alarms; the
app's alerts are useless if the alarm never fires. Not requested automatically.

## Release notes for version 0.4 (first Play release)
First release on Google Play. Offline prayer times, next-prayer countdown, Hijri date, Qibla compass, per-prayer
alerts (silent / notification / adhan) with your own recordings, and a nearest-town display.
