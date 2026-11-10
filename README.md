# CatchIt 🚌

**Real-Time MRT Feeder & Transit Tracker for Malaysia**

CatchIt is a native, ultra-lightweight (2.8 MB) Android transit app built with Jetpack Compose. It answers one simple question for Malaysian commuters: **When does my bus arrive, and when should I walk to the curb?**

---

## ✨ Features

- **Live Bus GPS Tracking**: Direct 30-second live AVL updates via `data.gov.my` and Prasarana feeds with freshness indicators.
- **Smart Destination Search**: Type landmarks, universities, malls, or complexes (*Dpulze, Todak Academy, Sunway Pyramid, KLCC*) and instantly get the nearest bus stop, walking distance, and exact route code.
- **2-Stage Departure Alarms**: Set an alarm for your stop — CatchIt alerts you when the bus passes your warning checkpoint, then rings when it reaches your pickup curb.
- **Real-Time Service Alerts**: Instant alerts for breakdowns, route diversions, road closures, and delays with background sync.
- **Nationwide Transit Catalog**: Instant offline directory of all 91 MRT feeder routes plus nationwide bus networks across Selangor, KL, Penang, Johor, and Perak.
- **Appearance Settings**: Seamless manual switch between **System**, **Light**, and deep OLED **Dark Mode**.
- **In-App Auto-Updates**: Silent background check with 1-tap seamless installation via GitHub Releases — no Google Play Store account or bloatware needed.

---

## 📲 Download & Installation

Download the latest APK release from [GitHub Releases](https://github.com/shintodd/CatchIt/releases/latest).

1. Download `CatchIt-v1.1.0.apk`.
2. Tap the downloaded file on your Android device.
3. Allow installation from your browser/file manager when prompted.
4. Open **CatchIt** — future updates can be installed with a single tap inside the app.

---

## 🛠️ Tech Stack & Architecture

- **Language & UI**: 100% Kotlin & Jetpack Compose (Material 3).
- **Architecture**: Clean Architecture with reactive Kotlin Flows and Repository pattern.
- **Concurrency**: Kotlin Coroutines (`Dispatchers.IO`, `SupervisorJob`).
- **Background Work**: AndroidX `WorkManager` for persistent 15-minute background disruption polls and alarm tracking.
- **Size Optimization**: ProGuard / R8 full-mode minification with stripped debug symbols $\rightarrow$ **2.81 MB release APK**.
- **Edge-to-Edge**: Fully compliant with Android 15 edge-to-edge drawing and dynamic status bar padding.

---

## 📄 License & Open Data

- Bus positions & AVL telemetry: Prasarana Malaysia Berhad.
- Schedules, routes & stop locations: `data.gov.my` (Open Government Licence - Malaysia).
