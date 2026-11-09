# NiftySense 📈🇮🇳

> **Minimal Live Market Widget for Indian Stock Markets (NIFTY 50 & BSE SENSEX)**  
> Inspired by the clean, minimal aesthetic of Groww's widget, crafted into a refined, battery-efficient, offline-resilient native Android experience.

---

## 📱 Features

- **Real-Time Index Tracking:** Live dynamic quotes for **NIFTY 50** (`^NSEI`) and **BSE SENSEX** (`^BSESN`).
- **Complete Metric View:** Current Index points, absolute point change, and percentage change.
- **Intelligent IST Market State Detection:**
  - `OPEN` (9:15 AM – 3:30 PM IST, Mon–Fri)
  - `PRE-OPEN` (9:00 AM – 9:15 AM IST)
  - `POST-CLOSE` (3:30 PM – 4:00 PM IST)
  - `CLOSED` (Evenings, Weekends & Holidays)
- **Zero Battery Waste:** Background synchronization via WorkManager automatically sleeps outside market hours and weekends.
- **3 Native Widget Formats:**
  1. **Small (2×2):** Compact dual ticker with dynamic green/red pill badges.
  2. **Medium (4×1):** Groww-inspired signature split card with live status indicator and 1-tap refresh.
  3. **Large (4×2):** Detailed view with on-device dynamic Canvas sparkline trend graphs, day's high/low ranges, and absolute changes.
- **Light & Dark Mode Adaptive:** Widgets and app screens dynamically adjust to Android system theme with emerald green (`#00C087`) for gains and rose red (`#EB5757`) for dips.
- **Offline Cache & Failover:** Instant startup with DataStore caching, plus automatic failover across Yahoo Finance mirror endpoints.

---

## 📥 Direct APK Downloads

You can download the pre-built, signed APK directly to your Android device:

| Package | Type | Size | Direct Link |
| :--- | :--- | :--- | :--- |
| **Release APK** *(Recommended)* | Signed Release APK | ~43 MB | [Download `niftysense-release-signed.apk`](https://github.com/arrowloki/nifty-sense/releases/download/v1.0.0/niftysense-release-signed.apk) |
| **Debug APK** | Debug APK | ~58 MB | [Download `niftysense-debug.apk`](https://github.com/arrowloki/nifty-sense/releases/download/v1.0.0/niftysense-debug.apk) |

---

## 🛠️ Architecture & Tech Stack

- **Platform:** Native Android (Min SDK 29 / Android 10, Target SDK 34)
- **UI:** 100% Jetpack Compose with Material 3 Design
- **Widgets:** Native Android `RemoteViews` AppWidget with custom Canvas Bitmap sparklines
- **Architecture:** Clean MVVM with Unidirectional Data Flow (UDF)
- **Concurrency & Reactivity:** Kotlin Coroutines & `StateFlow`
- **Networking:** Retrofit 2 + OkHttp 4 with dual mirror failover
- **Persistence:** Jetpack Preferences DataStore
- **Background Sync:** AndroidX WorkManager with IST market-aware scheduling
- **Number & Currency Formatting:** Indian Lakhs/Crores numbering system (`24,941.55`, `80,777.45`)

---

## 📐 Widget Gallery & Setup

### How to Add the Widget:
1. Long-press on any empty space on your Android home screen.
2. Tap **Widgets** and scroll to **NiftySense**.
3. Choose your desired size:
   - **NiftySense Small** (2×2): Quick glance at rates.
   - **NiftySense Medium** (4×1): Side-by-side Nifty & Sensex tracker.
   - **NiftySense Large** (4×2): Complete metrics with intraday sparklines.
4. Tap the widget at any time to open the app or tap the refresh icon for an instant live sync!

---

## 🔨 Building from Source

```bash
git clone https://github.com/arrowloki/nifty-sense.git
cd nifty-sense

# Build Debug APK
./gradlew assembleDebug

# Build Release APK
./gradlew assembleRelease
```

---

## 📄 License
Open source and crafted with ❤️ for Indian market enthusiasts.
