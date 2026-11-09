# HERMES AGENT HANDOFF: CatchIt (formerly FeederMY)

> **Last updated:** 9 November 2026
> **Android app:** `C:\Users\ainol\Dev\FeederMY\feedermy-android`
> **E2E suite:** `C:\Users\ainol\Dev\FeederMY\feedermy-android\e2e`
>
> ⚠️ The project used to live at `.gemini\antigravity\scratch\feedermy-android` and before that on
> OneDrive. Both are stale — the paths above are the only valid ones. Do not edit or build from the
> old locations. (The old file also described a 4-tab app with a Journey Planner and Rail Map;
> those screens have been deleted. See §10.)

---

## 1. What CatchIt is

A single-purpose Android app for RapidKL MRT feeder buses in Klang Valley, Malaysia.

**One question it answers:** *"When does my feeder bus actually come, and when do I start walking?"*

Three screens, and only three:

| Screen | What it does |
| --- | --- |
| **Bus** | Pinned routes with live bus counts, a daily-rotating popular shelf, a route picker over all 91 routes, and route detail with a live map, real timetables, stop ETAs and per-stop alarms. |
| **Alarms** | The armed departure alarms, plus a shortcut into service updates. |
| **Settings** | The two alarm lead times and battery guidance. |

Deliberately **not** a journey planner, rail map, or general transit app. Those screens were deleted.

### The wedge against Moovit

| Rival | Their problem | CatchIt's answer |
| --- | --- | --- |
| **Moovit** | No RapidKL feeder real-time data — schedules plus crowdsourced guesses, with ads | Real operator AVL, no ads, no account |
| **MyRapid PULSE** | Has the data but poor UX (2.7 stars); wrong timetables | Real data, live speed, plain language |

### Two-stage departure alarm — the core feature

The alarm is set on **one route**, and **each stage is anchored to its own stop** on that route:

- **Stage 1 "Leave now"** — fires when the bus **reaches** an early stop. The moment to start moving.
- **Stage 2 "Go to your waiting spot"** — fires when the bus **reaches** a later stop. The moment
  to be at your waiting spot.

**There is no lead-time setting.** The rider picks the stops; the app decides when the bus has
arrived. A bus counts as arriving at a stop when that stop is its inferred next stop on the route,
or when the estimated ETA to it is 3 minutes or less (`ARRIVING_ETA_MINUTES` in `GtfsRepository`).
The threshold is internal — the user never sees or sets it.

Both stages are measured against **one and the same approaching bus**, so the two warnings always
describe the same vehicle. When both stages share one stop, stage 2 is worded "Be at the curb"
instead.

Real example (the user's own setup): T507, stage 1 at **BOMBA CYBERJAYA**, stage 2 at
**CENTURY SQUARE** — the bus reaches Bomba and you leave, it reaches Century Square and you
are already there waiting.

Real example (the user's own setup): T507, stage 1 at **BOMBA CYBERJAYA**, stage 2 at
**PERDANA LAKEVIEW WEST**, with the waiting spot at Century Square — stage 2 tells them to head
over there.

Each stage fires **at most once** per armed alarm, and the fired state is persisted so a restart
cannot re-announce a stage. The alarm is set from **one obvious button** on the route detail
screen ("Set up my alarm"), not from a bell on each stop — the per-stop bells were confusing.
The Settings screen has no knobs for this any more; it explains the model instead.

---

## 2. Toolchain & environment

| Dependency | Exact path / version | Gotcha |
| --- | --- | --- |
| **JDK** | `C:\Program Files\Android\Android Studio\jbr` | Set `JAVA_HOME` to this; do not use system `java`. |
| **Android SDK** | `C:\Users\ainol\AppData\Local\Android\Sdk` | |
| **ADB** | `C:\Users\ainol\AppData\Local\Android\Sdk\platform-tools\adb.exe` | Not on PATH. |
| **Emulator** | `C:\Users\ainol\AppData\Local\Android\Sdk\emulator\emulator.exe`, AVD `Medium_Phone_API_37.0` | Screen is 1080×2400 (9:20). |
| **Gradle** | **9.1.0** — do not upgrade to 9.6.0 | AGP 8.13.2 needs the `InternalProblems` API that 9.6.0 removed. |
| **AGP / Kotlin** | 8.13.2 / 2.1.0 | Compose BOM 2025.01.00. |
| **Node.js** | v26.12.1 | For the E2E suite. |

Build:

```bash
cd "C:/Users/ainol/Dev/FeederMY/feedermy-android"
export JAVA_HOME="C:/Program Files/Android/Android Studio/jbr"
export PATH="$JAVA_HOME/bin:$PATH"
./gradlew.bat assembleDebug        # debug APK
./gradlew.bat assembleRelease      # signed release APK
./gradlew.bat bundleRelease        # AAB for Play
```

`build-apk.bat` and `install-and-run.bat` use `%~dp0`, so they work from any directory.

---

## 3. Live data sources

| Source | URL | Role |
| --- | --- | --- |
| Prasarana kiosk AVL (Socket.IO) | `https://rapidbus-socketio-avl.prasarana.com.my` | Primary live feed. Covers routes `data.gov.my` omits. |
| Government open data GTFS-RT | `https://api.data.gov.my/gtfs-realtime/vehicle-position/prasarana/?category=rapid-bus-mrtfeeder` | Fallback live feed. |
| Static GTFS | `https://api.data.gov.my/gtfs-static/prasarana/?category=rapid-bus-mrtfeeder` | Baked into `app/src/main/assets/feeder_schedule.json` at build time — never fetched at runtime. |
| Service alerts | `https://r.jina.ai/https://myrapid.com.my/pulse/service-alerts-on-pulse/` | Disruption notices. Cache-busted with a Unix-second `nc` parameter. |

**Coverage:** 84 of 91 routes have live vehicles.

### Non-obvious details that will bite you

- **The kiosk feed intermittently returns nothing** and `data.gov.my` intermittently returns an
  empty feed. That is exactly why there are two live sources. Do not "fix" the kiosk client by
  assuming the data is there.
- **Kiosk timestamps are MYT wall-clock**, not epoch. The emulator runs on GMT, so they must be
  parsed with an explicit `Asia/Kuala_Lumpur` zone. Without this every vehicle looks stale.
- **`GPS_STALE_SECONDS = 120`** in `GtfsRepository`. Vehicles whose GPS fix is older than that are
  dropped, which is what stopped ghost buses appearing at 0 km/h. A parked bus will therefore
  correctly show as "no bus online".
- **Protobuf types:** latitude, longitude and speed are `fixed32` (wire type 5), not varint.
  Speed is field **5** of `Position` and is in m/s — multiply by 3.6 for km/h.
- **The GTFS-RT endpoint 301-redirects.** Redirect handling must be on.
- **`data.gov.my` omits T507** and 32 other routes. The kiosk feed supplies them.
- **ETAs are inferred**, not reported: bearing + distance + speed + the route's stop order. A bus
  that has already passed the stop must never trigger an arrival alarm.

---

## 4. Architecture of the live data layer

| File | Responsibility |
| --- | --- |
| `data/GtfsRepository.kt` | Both live feeds merged, stale filtering, bearing-aware next-stop inference, `soonestBusToStop(...)`, route pins. |
| `data/GtfsRealtimeDecoder.kt` | Zero-dependency protobuf decoder. |
| `data/PrasaranaKioskClient.kt` | Socket.IO AVL client. |
| `data/ServiceAlertsRepository.kt` | Cache-busted fetch, disk cache, dedup, new-alert detection, read state. |
| `data/ServiceAlertsNotifier.kt` | High-priority grouped service-update notifications. |
| `data/ServiceAlertsWorker.kt` | WorkManager safety net (15 min) so updates arrive with the app closed. |
| `data/DepartureAlarmStore.kt` | Durable armed alarms (with a stop per stage) and which stages have fired. |
| `data/DepartureAlarmEngine.kt` | Decides when to announce, from real bus position. Anchors on one bus and measures both stages against it. |
| `data/DepartureAlarmNotifier.kt` | Posts "Leave now" / "Go to your waiting spot". |
| `ui/screens/DepartureAlarmSheet.kt` | The two-stage alarm setup sheet: a stop picker and a lead time per stage. |
| `data/DepartureAlarmWorker.kt` | 60-second self-rescheduling worker for the closed-app case. |

### The notification logo must be set explicitly

`android.largeIcon=null` makes Android's notification header fall back to the **application
icon**, which SystemUI serves from its own cache. That cache is keyed by package rather than by
icon version, so after the icon changed it kept rendering the **previous** mark — the old C
with the amber check, while the launcher correctly showed the new bus.

`data/BrandIcon.kt` renders the adaptive icon from the app's own resources into a circular bitmap
and both notifiers set it with `setLargeIcon(...)`. The notification logo and the launcher logo
can now never drift apart, and the stale-cache problem cannot reach the user.

**Never remove the `setLargeIcon` call.** The small icon alone is not enough.

## The splash

`app/src/main/res/raw/splash_loading.mp4` is the branded intro animation, played by
`ui/screens/SplashScreen.kt` through a `TextureView` and `MediaPlayer`. The splash is shown
in full and the app's data loads behind it, so it always runs its full length.

**The video is fitted, not cropped.** It is 1080x1920 (9:16) and a modern phone is
1080x2400 (20:9), so cropping to fill cuts 12.5% off each side and clips the ends off the
CatchIt wordmark. The animation's background is pure black, so fitting it leaves letterbox
bars that are invisible against the black splash.

**It has audio.** The previous splash was silent. The new animation has a swell that rises
from silence and peaks as the mark lands (mean -23.5 dB, max -4.9 dB), so every cold start
now makes a sound. Strip the audio track if that is not wanted:

```bash
ffmpeg -i splash.mp4 -c:v copy -an splash_silent.mp4
```

The resource is referenced directly as `R.raw.splash_loading`, not looked up by name, so a
missing file is a compile error rather than a splash that silently never plays.

## The map opens on the route

The route map used to open on a hard-coded Klang Valley centre and jump to the route
about a second later, which reads as "it put it in somewhere else". It now opens
already on the route.

- The route's centre is passed in the query string (`feeder_map.html?lat=..&lon=..&zoom=15`),
  so the first painted frame is the route.
- `map.invalidateSize()` runs before every `fitBounds` - the map sits in a bottom
  sheet whose height changes as the sheet settles, and Leaflet caches the container size.
- `fitRouteBounds()` always runs, even when the route has no shape; it falls back to
  the stops, then to the buses.
- Live buses only extend the view when they are within a padded route bound, so a bus
  parked at a depot on the far side of the loop cannot squash the route into a corner.
- `loadWithOverviewMode = false`, because overview mode scales the page and shifts the
  map sideways.
- The user can pan or zoom freely; the map no longer yanks itself back on a resize.

**How to check it:** open a route and count the green route pixels in a screenshot. The
route should fill roughly 75-85% of the map width from about 0.35 s onwards. If the
route is missing for the first second, the fit is landing late.

## Ghost arrivals

A **ghost arrival** is the app saying the bus is at your stop, you run to the
stop, and nothing comes for half an hour. It is the most damaging thing a
transit app can do, because it destroys trust in every other number it shows.

Five causes, all guarded against in `data/GtfsRepository.kt`:

| Cause | Guard |
|---|---|
| Driver on a 15-30 min break at the terminal, transponder still pinging | `isAtTerminal(...)` - a stopped bus within 150 m of the route's first or last stop is on a break, and is excluded from the alarm's candidate pool entirely |
| Bus on the opposite carriageway, stop 18 m away but heading the other way | `stopServiceHeading(...)` derives the direction the route travels through a stop from its neighbours; a bus heading more than 90 deg off is going the wrong way |
| Bus already past the stop, still "close" in a loop | `nextStopSeq > anchor.seq` skips it; the arrival test also requires the heading to point back at the stop |
| Upstream replaying the last GPS fix of a parked bus | `GPS_STALE_SECONDS = 120` drops anything older |
| Kiosk wall-clock timestamps parsed in the device timezone | `gpsAgeSeconds(...)` parses explicitly in `Asia/Kuala_Lumpur` |

The one that bit hardest was the **speed floor**. A stopped bus is given a
20 km/h floor so the ETA maths does not divide by zero, and that turns a bus
700 m away at a red light into "2 min away". The old arrival test trusted that
number. Now `isArrivingAtStop(...)` only counts a stopped bus as arriving when
it is within 50 m of the bay, or stopped at the light just short of it.

**Run the tests before changing any of this:**

```
./gradlew.bat testDebugUnitTest
```

`app/src/test/java/my/feeder/app/data/GhostArrivalTest.kt` pins down every case
in the checklist, using synthetic positions so it is deterministic.

### Two subtleties in the bus lookup

1. **A stopped bus has no heading.** The heading filter used to reject any bus more than 400 m
   from the anchor whose heading pointed elsewhere. A bus sitting at a terminal has a stale or
   zero heading, so it was being thrown away — exactly the bus that was about to depart. The
   heading check is now skipped when `speedKmh < 5`; the stop-sequence check
   (`bus.nextStopSeq > anchor.seq`) still rules out buses travelling the other way.
2. **The anchor is the later of the two stops.** `laterStopOnRoute(...)` compares the route's
   stop sequence. For T507, BOMBA CYBERJAYA is seq 17 and PERDANA LAKEVIEW WEST is seq 19, so the
   bus is chosen by its approach to Perdana Lakeview West, and stage 1's ETA to Bomba is measured
   on that same bus.

### Permission timing

`POST_NOTIFICATIONS` is deliberately **not** requested on launch. A permission prompt before the
user has any reason to want notifications blocks the whole first screen. It is asked for:

1. when the user turns on a departure alarm (`MainActivity.requestNotificationPermission()`),
   which is exactly when notifications become useful to them; and
2. from a "Turn on alerts for new disruptions" banner on the service-updates screen, shown only
   when notifications are actually switched off.

This also stopped the E2E suite breaking whenever `pm clear` reset the permission.

### Alarm flow, end to end

1. User taps "Set up my alarm" on the route detail screen, picks a stop and a lead time for
   each stage, then "Turn alarm on" → `GtfsRepository.armAlert(...)`.
2. The alert is written to `DepartureAlarmStore` (SharedPreferences `departure_alarms`) **and**
   mirrored into the in-memory list the UI reads.
3. `DepartureAlarmWorker.schedule(context)` starts a one-time worker that re-arms itself every
   60 s. WorkManager's periodic floor is 15 min, which is far too coarse for a 3-minute warning.
4. While the app is open, `MainActivity`'s 30-second live loop checks the same engine, so timing
   is exact in the foreground.
5. `DepartureAlarmEngine.evaluate()` calls `GtfsRepository.approachingBusWithEtas(...)`, which
   picks **one** bus — the one heading for the later of the two stops — and returns its ETA to
   every stop the alarm cares about, plus the set of stops it is *arriving* at. Stage 1 fires when
   the bus is arriving at stop 1, stage 2 when it is arriving at stop 2. Because both ETAs come
   from the same bus, the two warnings always describe one vehicle.
6. `DepartureAlarmStore.markFired(...)` records the stage so it is never announced twice.
7. `pruneCompleted()` removes the alarm once both stages have fired — so an alarm that has
   completed disappears from the Alarms screen. That is intentional.

### Sheet behaviour

`TwoStageAlertSheet` shows the alarm's **armed** timings when reopened, offers **Save changes**
when they differ, and **Turn alarm off** underneath. Saving updates the timings without resetting
the fired stages (`DepartureAlarmStore.updateTimings`).

---

## 5. Release build

| Item | Value |
| --- | --- |
| Application ID | `my.feeder.app` |
| Version | 1.0.4 (build 5) |
| Min / target SDK | 26 / 35 |
| Release APK | `app/build/outputs/apk/release/app-release.apk` (~2.95 MB, signed) |
| Play bundle | `app/build/outputs/bundle/release/app-release.aab` (~5.06 MB) |
| Debug APK | `app/build/outputs/apk/debug/app-debug.apk` (~12.3 MB) |
| Keystore | `keystore/upload.jks`, credentials in `keystore.properties` (both git-ignored) |
| R8 rules | `app/proguard-rules.pro` — **must exist**, or `minifyReleaseWithR8` fails. |

Signing is conditional: if `keystore.properties` is absent the build still succeeds and produces
an unsigned APK, so a clone without the keystore is not broken.

`proguard-rules.pro` keeps the hand-written wire decoders, the WorkManager workers and the
notification receivers — all of which R8 would otherwise strip.

---

## 6. Permissions

| Permission | Why |
| --- | --- |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Fetch bus positions, timetables, service notices. |
| `POST_NOTIFICATIONS` | Departure alarms and service updates. **Not requested on launch** — it is asked for at the moment the user turns on an alarm, and from a banner on the service-updates screen. Asking on first launch blocked the whole first screen and read as a dark pattern. |
| `VIBRATE` | Make an alarm noticeable. |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Optional, so alarms still fire when the app is closed. |

The app **does not** request location. `REQUEST_INSTALL_PACKAGES` and the in-app APK updater were
removed for Play policy reasons. Firebase was removed too — there is no backend, and service
updates work by polling plus WorkManager.

---

## 7. E2E suite

```bash
cd "C:/Users/ainol/Dev/FeederMY/feedermy-android/e2e"
export ANDROID_HOME="C:/Users/ainol/AppData/Local/Android/Sdk"
export ANDROID_SDK_ROOT="C:/Users/ainol/AppData/Local/Android/Sdk"
export E2E_TELEMETRY_DISABLED=1
npx e2e run
```

**Current status: 11/11 passing** — 9 in `tests/home.e2e.ts`, 2 in `tests/service-alerts.e2e.ts`.

Gotchas:
- The package `e2e@0.22.1` does not exist; versions jump to `0.23.0`.
- The pin/unpin control is an `IconButton` whose accessible label lives on a **nested** icon node.
  `getByRole('button', 'Pin')` finds nothing — use `getByLabel('Pin')` / `getByLabel('Unpin')`.
- Assertions must not depend on leftover state from a previous test.

---

## 8. Store assets

| Asset | Path |
| --- | --- |
| Listing copy, data-safety answers, release checklist | `PLAY_STORE_LISTING.md` |
| Privacy policy | `PRIVACY_POLICY.md` |
| Play icon 512×512 | `store/icon_512.png` |
| Feature graphic 1024×500 | `store/feature_graphic.png` |
| Phone screenshots (9:16, 1080×1920) | `store/screenshots/01..07*.png` |
| Adaptive icon | `app/src/main/res/drawable/ic_launcher_{foreground.png,background.xml}` + legacy `mipmap-*` PNGs |
| Notification small icon | `app/src/main/res/drawable-*/ic_catchit_notification.png` |
| Notification large icon | Rendered at runtime by `data/BrandIcon.kt` from the adaptive icon. Never leave this to the system — see below. |

**Brand:** the approved assets live in `brand/` (copied from `Desktop\CatchIt_Branding`, which is
on OneDrive and therefore not a reliable source):

| File | What it is |
| --- | --- |
| `brand/CatchIt_Bus_Glyph.png` | The approved white bus glyph — front-facing, roof with a destination-slot notch, windscreen, two wheels. This is the app icon mark. |
| `brand/CatchIt_Master_Lockup.png` | The approved wordmark. The in-app header crops the wordmark out of this (x 320 → end) so the typography stays exactly as designed. |
| `brand/CatchIt_App_Icon.png` | The previous letter mark (C + check) on the green field. Kept for reference. |
| `brand/CatchIt_App_Icon_BadgeOnly.png` | The letter mark alone on black. The **yellow check** is extracted from here — the approved accent is `#FEE909`, not an amber. |
| `brand/CatchIt_App_Icon_Round.png` | Round version of the previous icon. |

The shipped launcher icon is now the **bus glyph**, white, on the approved green gradient
(`#235432` → `#0F2A1D`, which is exactly the gradient in `ic_launcher_background.xml`).

`ic_launcher_foreground.png` is 432×432 (the 108 dp adaptive layer).

**Sizing rule — this is the one that bites.** The launcher masks the adaptive icon to the central
**72 dp of the 108 dp layer** — only **66.7%** of the canvas is ever visible. The approved
`brand/CatchIt_App_Icon.png` puts its mark at **55.7%** of the canvas. The bus must be the same:
scale it so the ink is **56% of the canvas**, centred, and never larger. An earlier build used
90%, which the mask cropped on all four sides — the wheels vanished and the icon stopped reading
as a bus at all.

Current bus ink bbox in the 432 canvas: x 95–335, y 98–333 (55.8% wide).

**Notification icon:** `drawable-*/ic_catchit_notification.png` is the bus glyph as a white
silhouette at 24/36/48/72/96 px. Both the departure alarm and the service-alert notifications use
it (`setSmallIcon`). Android renders a notification's small icon as an alpha-masked silhouette,
so it must be white on transparent — the approved glyph already is.

**Regenerating the icon:** crop the bus out of `brand/CatchIt_Bus_Glyph.png`, scale it, and
centre it on the canvas. Do not hand-redraw the bus — the approved glyph is the source of truth.

Screenshots are the emulator screen (1080×2400) scaled to 864×1920 and padded to 1080×1920 with
`#0F2A1D`, so the bottom navigation bar is never cropped.

---

## 9. Known gaps

- **Version name/code has not been bumped** since 1.0.4 (5). Bump before the first Play upload.
- **The privacy policy still has a placeholder contact address** (`catchit.support@example.com`).
- **No unit or instrumentation test source sets.** Coverage is the 11 E2E tests plus manual
  on-device verification.
- **The bundled timetable has no service-day filtering** — weekends and public holidays are not
  distinguished.
- **Stage-2 ("Be at the curb") has been verified on-device**, but only when a bus is genuinely
  closing on the stop; a parked bus will never trigger it.
- **WorkManager timing is best effort in the background.** Foreground timing is exact.
- **The launcher icon's legacy PNGs and adaptive foreground were regenerated** — if you replace
  them, re-check the safe-zone distance.

---

## 10. Removed on purpose — do not reintroduce

The user approved removing all of these:

- Journey Planner, Rail Map, the old Live Bus screen, the route-strip sheet, the old alert sheet.
- The in-app APK updater, its FileProvider and `REQUEST_INSTALL_PACKAGES`.
- Firebase Cloud Messaging and the placeholder `google-services.json` (no backend; service updates
  work by polling plus WorkManager).
- The emoji that used to stand in for icons — all icons are genuine Lucide vectors.

---

---

## 11. The promo video (rebuilt 2026-11-09, twice)

`store/video/CatchIt_promo_9x16.mp4` - **27.6 s, 1080x1920, 60 fps, H.264 + AAC, 5.5 MB**.
Editable source: `ad/` (one HyperFrames HTML composition). md5 `45cd1473eb0b`.

**The ad is the real app.** Every app screen is a device capture of the release build on
the emulator (`ad/assets/screens/*.png`, 864x1920, placed at x=108 on a 1080 canvas). The
HTML adds motion, captions and the end card; it never redraws the UI.

**There was a failed middle version.** The first ad was a 30 fps composite of 10 fps
scrcpy recordings (juddery). The second rebuilt the screens as hand-written CSS from the
design tokens - and the user rejected it: *"that video literally looks nothing like my
app at all"*. It had invented its own colours (#f4f7f5 canvas, a green hero) instead of
the app's real tokens (`LightCanvasBg #F6F8F3`, `PulseGreen900 #0F2A1D`,
`TelemetryLiveLight #15803D`, accent `#FEE909`, radius 12/16/22/28, type 12/14/16/20).
**Do not hand-rebuild the app's UI for an ad. Re-capture the screens.**

Eight scenes: the app's own splash animation (full bleed, with its own sound) -> Bus
home -> search -> route detail with the live map -> the stop rail with live ETAs -> the
two-stage alarm setup sheet -> the armed alarm -> end card. Scene 7 shows a genuinely
armed alarm: `T108 / MRT SRI DAMANSARA SENTRAL PINTU A -> TAMAN BUKIT MALURI (SELATAN)`
with live bus `VAK1034`.

**The audio is real music**: "Sports Highlights" by Ahjay Stelino from MixKit
(`assets.mixkit.co/music/51/51.mp3`, MixKit Free License - commercial use, no
attribution). Cut from 56.0 s for 26.6 s, the flattest window in the track. The app's own
splash sound plays under the opening via `<video data-has-audio="true">`; the music
enters at `data-start="1.0"`.

Two earlier beds were wrong and both sounded broken:
- the splash's 3-second swell stretched 7.6x with `atempo` - silent 1.4 s, then a
  21-second crescendo peaking only at the end;
- a hand-synthesised five-note pad - a static drone that swelled 4x toward the end and
  carried hard clicks on every scene change.

Shipped file verified: mean -19.8 dB, peak -3.1 dB, **3.5 dB total level range** across
the whole ad, zero silence gaps, zero decode errors. The `delivery` render is a 61 MB
master; the shipped file is re-encoded to CRF 23 (5.5 MB) with no visible loss.

**Verify the ad matches the app numerically, not by eye.** A rendered frame against the
screen capture it came from: **1.83/255 (0.72%)** mean absolute pixel difference above
the caption band, against **55.28/255 (21.7%)** for a different screen as a control. If
the first number climbs into the tens, the app's UI changed and the screens need
re-capturing.

HyperFrames gotchas that each cost a rebuild are in `store/video/README.md` and the
`catchit-release` skill.

**Not done:** no 16:9 cut, no voiceover.

---

## 12. ARTEMIS (Google's Android automation agent) - installed 2026-11-10

The user pointed at `github.com/google/artemis`. It is now installed and working on this
machine. See the `android-artemis` skill for the full detail.

| Piece | Path |
|---|---|
| repo | `C:\Users\ainol\Dev\artemis` (Apache-2.0) |
| venv | `C:\Users\ainol\Dev\artemis\.venv` (Python 3.12, via `uv`) |
| uv | `C:\Users\ainol\Dev\.tools\uv\uv.exe` |
| scrcpy | `C:\Users\ainol\Dev\.tools\scrcpy\scrcpy-win64-v2.7\scrcpy.exe` |
| wrapper | `C:\Users\ainol\Dev\.tools\artemis-run.py` |

**Always run it through the wrapper** - it resolves the API credential from the Windows
user environment at run time and passes it in through the environment only, so no second
copy of the key is written to disk. `config/artemis.jsonc` points `default.provider` at
`openai` with this machine's OpenAI-compatible Claude model, and `object_detector` /
`hopper` were repointed off the Gemini-only models.

Verified: `doctor` reports **Status: Ready** (device connected, multimodal credential
active, FFmpeg + scrcpy resolved). A real task on CatchIt - "open the app, wait for the
Bus screen, report the live bus count" - **completed in 3 steps / ~36 s** via
`--profile flash`, reading the accessibility tree through ARTEMIS's on-device helper
(v1.2.0, installed on emulator-5554) and returning a structured report.

**Its numeric readings can be wrong**: it reported "1 bus live" when the app actually
showed "No buses live". Use it for navigation and structure; verify any number against
`uiautomator dump`.

### The capture pipeline this replaced

`scripts/artemis-capture-screens.py` drives the app to each store/ad screen by natural
language, then screenshots it. ARTEMIS leaves the app in the state it finished in, so
the capture is taken after the task completes - no sleep-and-hope race. All 7 screens
were captured this way and verified against expected text.

**Always stage to `CATCHIT_CAPTURE_DIR` first.** The shipped screenshots were taken when
buses were live; a night capture shows "No buses live" and is not fit to publish.

### Two things ARTEMIS cannot do here

- The route-detail map needs **network access**; captured offline the map area is grey
  placeholder blocks.
- Its MCP server is not in the Hermes catalog, so it is driven through its CLI. Adding
  an `mcp_servers` entry to `hermes/config.yaml` would expose `mobile_run_task`,
  `mobile_get_device_state`, `mobile_inspect_trace` and `mobile_diagnose` to the
  assistant - that needs the user's go-ahead, since Hermes MCP config is edited by
  `manage_connections`, not by hand.

---

## 13. Destination search - "I just want to go somewhere" (2026-11-10)

The rider does not know bus routes, only where they want to go. The Bus screen's
search now answers that.

**What ships (built and verified on the release build):** the search matches route code,
route headsign, **and stop name**. A stop match is grouped by stop *name*, so one
physical stop served by several routes is a single row showing every route number, the
direction, and the next departure:

```
Stops
SKYPARK CYBERJAYA
T504  ->  MRT CYBERJAYA UTARA        06:00
```

Feeder routes are loops, so the direction matters as much as the number - a result that
only gave the route code would send half the riders the wrong way.

**Data used:** the bundled catalogue already has everything. 91 routes, 2,514 stop
entries, 1,988 unique stop names, and **every stop carries a lat/lon**. The headsign is
the direction label. The schedule gives the next departure. Live AVL gives the live
count. **No new service, no new dependency, no new permission, no cost.**

Verified against the real catalogue: "Cyberjaya" -> 4 routes + 28 stops; "Putrajaya" -> 2
routes; "Cheras" -> 6 routes + 83 stops; "KL Sentral" -> 0 (that is rail, not a feeder
route - the app correctly has no answer).

**Files:** `ui/screens/HomeScreen.kt` (`matchStops`, `StopSearchResultRow`,
`StopMatch`), `app/src/test/java/my/feeder/app/ui/screens/StopSearchTest.kt`,
`e2e/tests/home.e2e.ts` (new test). Tests: **unit 18/18**, **E2E 12/12**.

**This is not the Journey Planner.** It answers one question - which bus, from where,
when - and never plans an itinerary. The Journey Planner was removed on purpose; keep
this from growing back into one.

### The two increments still open

1. **"Near me"** - the data is ready (lat/lon on every stop, `haversineMeters` already
   implemented) but the app holds **no location permission at all**, so it cannot sort
   stops by distance from the rider. Costs one permission, no service.
2. **Arbitrary place names** (`Taman Bahagia`) - needs a real geocoder. Google Places is
   paid (~$5/1000 geocoding, $2.83/1000 autocomplete); Photon / Nominatim / Esri World
   Geocoding are free or free-tier but need a proxy or self-hosting for in-app use.

### An E2E fragility found and fixed

`pretest:e2e` clears the app data, which wipes the cached service alerts, so the
notifications screen must fetch them before it can show anything. The default assertion
window lost that race once the feed grew to 10 alerts, and the service-alerts test
failed with "no node (match count 0)". Fixed with an explicit
`toBeVisible({ timeout: 30000 })`, and the matcher now accepts both the plural
`Mark all 9 as read` and the singular `Mark 1 as read`.
