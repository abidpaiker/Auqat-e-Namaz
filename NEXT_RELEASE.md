# Release notes and running list

## Shipped in 0.5
First-run setup (location, method with a suggestion from the nearest town's country, Asr with both times shown,
per-event alerts, battery exclusion) · Qibla interference check against the world magnetic model with an
always-visible field/dip line · "Show both Asr times" with independent alerts · twelve more calculation methods
including Moonsighting Committee and a seasonal United Kingdom method calibrated to the London Unified timetable ·
Dubai label fixed.

## Still open
* Reply from londonprayertimes.com about bundling the London table (would make London exact rather than ±3 min).
* F-Droid: adhan licence confirmation, fastlane metadata, tag v0.5, submit.
* Tune Qibla tolerances (15 % / 10°) from real-world trials in a car and near a laptop.

---

# Planned for 0.5 (original notes, kept for reference)

## First-run setup

On the very first launch, before the main screen, walk the user through a short setup, one choice per screen,
each with a sensible default already selected so "Next, Next, Next" gives a working app:

1. **Location** — explain why, then the system prompt; then the "Location while travelling" disclosure and the
   "Allow all the time" prompt (as now, just moved into the setup flow).
2. **Calculation method** — list of methods, with a default suggested from the user's country once a position is
   known (e.g. ISNA for North America, Umm al-Qura for Saudi Arabia, Karachi for South Asia, MWL elsewhere).
   One line under each explaining who uses it.
3. **Asr** — Standard (Shafi'i, Maliki, Hanbali) or Hanafi, with today's Asr time shown for both so the
   difference is concrete.
4. **Alerts** — the six per-prayer rows (Silent / Notification / Adhan), Sunrise defaulting to Silent.
   Then the notifications permission prompt (Android 13+).
5. **Battery** — explain that some phones stop prayer alarms, then the system "allow to run in background"
   (ignore battery optimisation) prompt. On Samsung, also point to Settings → Battery → Background usage limits
   and tell the user to make sure the app is not under "Sleeping apps" or "Deep sleeping apps".

Every choice remains changeable in Settings afterwards. Existing users who update are not shown the setup again.

## Qibla compass: tighter interference check, always-visible reading

The current check only warns when the field strength is outside 25–65 µT (anything the Earth could produce
anywhere), which misses local distortion that bends the needle without changing the strength much.

* Compute the expected field for the current position from `GeomagneticField` (already used for declination):
  total intensity and inclination (dip angle).
* Measure the actual total and dip from the magnetometer and accelerometer (gravity vector).
* Warn when the measured strength differs from expected by more than ~15 %, or the dip by more than ~10°.
  Keep Android's own calibration-accuracy warning as it is.
* Always show a small status line under the compass, e.g.
  `Field 52 µT (expected 52) · dip 66° (expected 66) · calibration good`,
  in the normal colour while within tolerance and amber when outside, with the existing warning sentence below.

## Show both Asr times

Settings option "Show both Asr times" (off by default). When on, the main screen shows the Asr row as two
lines — "Asr (standard)" and "Asr (Hanafi)" — or one row with both times, e.g. `Asr  4:07 PM · Hanafi 4:44 PM`.
With the option on, **both Asr times alert**: Settings → Notifications gains a second row, "Asr (Hanafi)" next
to "Asr (standard)", each with its own Silent / Notification / Adhan choice and its own recording, so the user
can, say, hear the adhan at one and a short notification at the other. No restrictions: adhan at both,
notification at both, or any mix is allowed. The next-prayer header and countdown
treat them as two events (after the standard Asr passes, the header shows "Asr (Hanafi) in …").
The existing Hanafi switch still decides which Asr is the "main" one (bold highlight, used when the option is
off). Same in the silent shade line. The first-run Asr screen already shows both times.

Implementation notes: the scheduler needs an extra event kind for the second Asr (not a seventh prayer in the
engine); the "already fired" guard, the catch-up rule and the pre-prayer GPS refresh must key on it separately
so the two alerts never suppress each other.

## Calculation methods

Add the methods missing from the standard list: Moonsighting Committee Worldwide (seasonal, Khalid Shaukat
formula), Gulf Region (19.5° / Isha 90 min), France UOIF (12/12), Russia (16/15), JAKIM Malaysia (20/18),
KEMENAG Indonesia (20/18), Tunisia (18/18), Algeria (18/17), Morocco (19/17), Portugal (18°, Maghrib +3 min,
Isha 77 min), Jordan (18/18, Maghrib +5 min). Method table needs a "Maghrib + minutes" field. Fix the Dubai
label (currently says "Gulf region", which is a different method).

### UK / London

Calibrated against the London Unified Prayer Timetable (East London Mosque, 2026, 365 days) — data used for
calibration only, not bundled:

* Sunrise = astronomical − 3 min, Dhuhr = solar noon + 5 min, Maghrib = sunset + 3 min, Asr = standard / Hanafi
  shadow rule (all within ±1 min of the timetable).
* Fajr and Isha are seasonal, not fixed angles: Fajr ≈ 15–16° below the horizon in winter falling to ≈ 12° in
  June; Isha ≈ 15° in winter falling to ≈ 9° in June.
* Moonsighting Committee matches London closely: Fajr within ±7 min (avg 3), Isha within ±11 min (avg 5,
  usually a few minutes early).
* MWL (our current default) with the middle-of-night rule is far off in UK summer: Fajr up to ~2 h early and
  Isha up to ~2 h 40 min late in June–July. ISNA is up to ~2 h off.
* Our own fit — seasonal angle = 3-harmonic function of day of year — matches within ±6 min (avg 1–2 min):
  Fajr coefficients [14.402, 1.655, -0.157, -0.847, 0.309, -0.334, -0.072],
  Isha coefficients [12.405, 3.237, -0.296, -0.682, 0.225, -0.238, 0.258]
  (angle = c0 + Σ ck·cos(kω) + sk·sin(kω), ω = 2π(dayOfYear−1)/365.25).

Plan: add a "UK (London Unified, approx.)" method = fitted seasonal Fajr/Isha + the −3 / +5 / +3 margins, and
suggest it (or Moonsighting) in the first-run setup for UK users. Add an engine test asserting the fit stays
within ±6 min of the calibration values (store only our derived numbers in the test, not their table).

## Housekeeping
* Bump versionCode / versionName.
* Re-record the background-location video only if the disclosure wording or position changes noticeably.
