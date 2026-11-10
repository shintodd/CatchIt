# CatchIt — Adaptive UI Audit & Fix Report

Branch `adaptive-ui` · package `my.feeder.app` · AGP 9.5.1 / Kotlin 2.2.10 / Compose 1.7.6 · 10 Nov 2026

## 1. Result summary

All JVM-verifiable surfaces are green; the two screens/surfaces JVM cannot render were verified on the emulator.

| Screen | P1 320×568 | P2 360×800 | P4 360×640 fs2.0 | P6 600×960 | P7 1280×800 | P9 dark fs1.3 |
|---|---|---|---|---|---|---|
| Bus (Home) | ✅ | ✅ | ✅ (fixed) | ✅ | ✅ (fixed) | ✅ |
| Alarms | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Settings | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Notifications | ✅ | ✅ | ✅ (fixed) | ✅ | ✅ | ✅ |
| Station picker | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Route detail (map) | ✅ emu | ✅ emu | — | — | ✅ emu | — |

✅ = screenshot golden + assertions pass. "fixed" = was broken at baseline, fixed and re-verified.

## 2. Findings — fixed

| ID | Sev | File | Fix | Commit |
|---|---|---|---|---|
| S1-1 | S1 | `HomeScreen.kt:123` | search `query` → `rememberSaveable` | `453b177` |
| S1-2 | S1 | `StationHubSheet.kt:47,48` | tab + search → `rememberSaveable` | `453b177` |
| S1-3 | S1 | `MainActivity.kt:220,221` | chosen station → `rememberSaveable` (was reset on every rotate) | `453b177` |
| S1-4 | S1 | `NotificationsScreen.kt:72` | `filter` → `rememberSaveable` | `453b177` |
| S1-5 | S1 | `DepartureAlarmSheet.kt:85` | `pickingFor` → `rememberSaveable` | `453b177` |
| S1-6 | S1 | `MainActivity.kt:218,219` | sheets/alert target → `rememberSaveable` | `453b177` |
| S2-1 | S2 | 4 screens | content width capped at 600dp + centered (`MaxWidthContainer`) | `4466efc` |
| S2-4 | S2 | `NotificationsScreen.kt:266` | `Mark all N as read` → `overflow=Ellipsis` | `4466efc` |
| S2-2 | S2 | `DepartureAlarmSheet.kt:262` | stop list `height(360dp)` → `heightIn(max=360dp)` | `35e2c1a` |
| S2-3 | S2 | `HomeScreen.kt` hero pill | station name `maxLines=1` → `2` (wraps instead of truncating) | `35e2c1a` |

All six S1 findings were the same root cause: the app had **zero `rememberSaveable`** — every user-meaningful state was a plain `remember` and died on rotation, fold, resize or font change.

## 3. Findings — verified OK, no change needed

- **S2-5** `RouteDetailScreen.kt:115` fixed `sheetPeekHeight=140.dp` — renders correctly at 800dp landscape (map + departures + alarm setup all visible). Left unchanged; **360dp landscape not tested** (no foldable/landscape-phone AVD).
- **S2-6** bottom-nav label collision at 320dp / fs2.0 — emulator + touch-target tests show no collision/overlap.
- **S3-1** double bottom inset on nested `Scaffold`s — cosmetic, deferred.
- **S3-2** 38dp icons (`NotificationsScreen.kt:387`, `StationHubSheet.kt:258`) — cosmetic; the clickable ones are inside 48dp `IconButton`s. Deferred.
- **S3-3** `HomeScreen.kt:636` fixed `.width(158.dp)` card — cosmetic, deferred.

## 4. Test infrastructure added

- **Robolectric 4.17** + **compose-ui-test 1.7.6** + `ui-test-manifest` (JVM Compose tests).
- **Roborazzi 1.78.0** screenshot tests — verified compatible with AGP 9.5.1 / Kotlin 2.2.10 / Compose 1.7.6.
- `@Config(qualifiers = "w…dp-h…dp")` is the **only** mechanism that resizes the captured window; `DeviceConfigurationOverride.ForcedSize` changes the composition's configuration but not the window Roborazzi captures (learned empirically — the first 25 goldens were all the wrong size and had to be re-recorded). Font scale and dark mode **do** work via `DeviceConfigurationOverride`.
- 30 goldens in `app/src/test/roborazzi/` (6 profiles × 5 screens), recorded with `recordRoborazziDebug`, verified with `verifyRoborazziDebug`.
- `@AdaptivePreviews` multipreview annotation + P1/P5/P6/P7 device annotations for Android Studio.
- **52 unit tests green**: 18 logic + 2 state-restoration + 30 screenshot + 2 touch-target.

One false positive documented and worked around: M3 `NavigationBarItem` reports `hasVisualOverflow` on its label even when the text renders fine, so that signal is not used as an assertion; nav-label clipping is verified from goldens + emulator instead.

## 5. targetSdk 36/37 readiness

- App targets **35** (min 26, compile 35). The API 36/37 large-screen forced-resize rules do not apply yet.
- **Nothing to lose at 36/37**: the manifest has no orientation lock, no `resizeableActivity=false`, no `configChanges`, no `min/maxAspectRatio`. The app was already resizable — the phone-only layout was already reachable on tablets/foldables today, and that's now fixed (width cap + persisted state).
- The only future work is the SDK bump itself: compileSdk 37 + AGP upgrade when Play requires targetSdk 37 (Aug 2027).

## 6. Not verifiable by the agent — manual checklist

1. Real devices with vendor skins (Samsung One UI, Xiaomi MIUI, Oppo ColorOS) — insets/cutout/font scaling differ.
2. Foldable half-open / tabletop posture (needs Pixel Fold AVD, not configured).
3. Split-screen (P8) and 50% split on tablet — not exercised.
4. Notification appearance on narrow screens and at fs 2.0.
5. TalkBack / accessibility tree.
6. Map (Leaflet WebView) on low-end GPUs — verified on host-GPU emulator only.
7. Density-specific rendering — JVM goldens use mdpi; density is not a layout variable for dp, but drawable scaling is unverified.

## 7. Remaining limitations

- Bottom nav spans full width on tablets (M3 default). Content is capped; a `NavigationRail` on expanded screens is a larger architectural change deliberately not made.
- S3 cosmetic items deferred.
- 360dp-landscape (P5) sheet/peek behaviour not verified.

## Commits

```
3280da3 adaptive: Step 5 - touch-target and bounds assertions for bottom nav
35e2c1a adaptive: S2 - alarm sheet heightIn + station name wraps at large font
4466efc adaptive: S2 - cap content width on wide screens + ellipsize mark-all-read
453b177 adaptive: S1 - persist user state across recreation (rememberSaveable)
c9dd825 adaptive: add Roborazzi baseline + adaptive preview annotations
3e644a8 adaptive: add state-restoration tests
02678dd baseline: adaptive-ui starting point
```
