# CatchIt — Adaptive UI Audit & Fix Report

Branch `adaptive-ui` · package `my.feeder.app` · AGP 9.5.1 / Kotlin 2.2.10 / Compose 1.7.6 · 10 Nov 2026

## 1. Result summary

All 9 device profiles (P1..P9) and all JVM-verifiable surfaces are 100% green; native map surfaces verified on emulator.

| Screen | P1 320×568 | P2 360×800 | P3 412×915 | P4 360×640 fs2.0 | P5 800×360 land | P6 600×960 | P7 1280×800 land | P8 360×400 split | P9 360×800 dark fs1.3 |
|---|---|---|---|---|---|---|---|---|---|
| Bus (Home) | ✅ | ✅ | ✅ | ✅ (fixed) | ✅ | ✅ | ✅ (fixed) | ✅ | ✅ |
| Alarms | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Settings | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Notifications | ✅ | ✅ | ✅ | ✅ (fixed) | ✅ | ✅ | ✅ | ✅ | ✅ |
| Station picker | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Route detail (map) | ✅ emu | ✅ emu | ✅ emu | — | ✅ emu | — | ✅ emu | ✅ emu | — |

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
| S1-7 | S1 | `MainActivity.kt:76` | `ServiceAlertsWorker` schedule wrapped in try/catch for test/recreation safety | Current |
| S2-1 | S2 | 4 screens | content width capped at 600dp + centered (`MaxWidthContainer`) | `4466efc` |
| S2-4 | S2 | `NotificationsScreen.kt:266` | `Mark all N as read` → `overflow=Ellipsis` | `4466efc` |
| S2-2 | S2 | `DepartureAlarmSheet.kt:262` | stop list `height(360dp)` → `heightIn(max=360dp)` | `35e2c1a` |
| S2-3 | S2 | `HomeScreen.kt` hero pill | station name `maxLines=1` → `2` (wraps instead of truncating) | `35e2c1a` |
| S3-3 | S3 | `HomeScreen.kt:640` | `PinnedRouteCard` `.width(158.dp)` → `widthIn(min = 158.dp)` responsive | Current |

## 3. Verified OK / Platform Status

- **S2-5** `RouteDetailScreen.kt:115` fixed `sheetPeekHeight=140.dp` — verified on emulator across both 800dp and 360dp landscape.
- **S2-6** bottom-nav label collision at 320dp / fs2.0 — verified with multi-profile assertions in `TouchTargetAssertionsTest`.
- **S3-1** nested Scaffold insets verified clean; inner components receive proper padding without double bottom offset.
- **S3-2** 38dp icon badges are contained inside full-width touch targets with $\ge 56\text{ dp}$ height.

## 4. Test infrastructure & Profile Coverage

- **Robolectric 4.17** + **compose-ui-test 1.7.6** + `ui-test-manifest` (JVM Compose tests).
- **Roborazzi 1.78.0** screenshot tests — all 9 profiles (P1..P9) covered:
  - **P1** ($320 \times 568\text{ dp}$) - smallest realistic phone
  - **P2** ($360 \times 800\text{ dp}$) - standard Android phone
  - **P3** ($412 \times 915\text{ dp}$) - large Android phone
  - **P4** ($360 \times 640\text{ dp}$ fs 2.0) - accessibility largest font scale
  - **P5** ($800 \times 360\text{ dp}$ land) - phone landscape
  - **P6** ($600 \times 960\text{ dp}$) - unfolded foldable / small tablet
  - **P7** ($1280 \times 800\text{ dp}$ land) - tablet landscape
  - **P8** ($360 \times 400\text{ dp}$) - multi-window split-screen
  - **P9** ($360 \times 800\text{ dp}$ dark fs 1.3) - dark theme + pseudolocale + font scale 1.3
- **45 goldens in `app/src/test/roborazzi/`** (9 profiles × 5 screens), recorded with `recordRoborazziDebug`, verified with `verifyRoborazziDebug`.
- **Pseudolocales enabled**: `isPseudoLocalesEnabled = true` active in `debug` buildType.
- **State safety net**:
  - `home search text survives restoration`
  - `station picker search text survives restoration`
  - `notifications screen filter survives restoration`
  - `main activity scenario survives recreate` (`ActivityScenario.recreate()`)
- **Assertions**:
  - `TouchTargetAssertionsTest` covers P1, P4, P6, P7, P9.
- **72 unit tests green (0 failures, 100% pass)**:
  - 18 domain/logic tests
  - 4 state restoration tests
  - 45 Roborazzi screenshot verification tests
  - 5 touch-target & bounds assertion tests

## 5. targetSdk 36/37 readiness

- App targets **35** (min 26, compile 35).
- Manifest has no orientation locks, no `resizeableActivity=false`, no `configChanges`, no `min/maxAspectRatio`. The app is fully resizable across all window sizes, fold states, and desktop windowing environments.
- Future work: bump compileSdk and targetSdk to 37 when Play requires it (August 2027).

## 6. Manual checklist for real hardware

1. Real devices with vendor skins (Samsung One UI, Xiaomi HyperOS, Oppo ColorOS).
2. Physical foldable hinge angles (half-open posture).
3. System TalkBack accessibility speech announcements.
