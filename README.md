# JedaIn: Stop Doomscrolling

**Package Name:** `com.jedain.app`  
**Developer:** Arry Kusuma Putra  
**Year:** 2026  

---

## Overview

**JedaIn** is an Android digital wellbeing application designed to help users manage screen time and build balanced smartphone habits. Rather than permanently blocking applications, JedaIn promotes mindful usage through real-time floating timer indicators, customizable session limits, structured pause breaks, and reflective check-ins.

---

## Core Features

- **Custom Monitored App Selection**  
  Select specific applications to monitor (such as social media, short-form video platforms, or games). JedaIn runs its timers only when a monitored application is active in the foreground.

- **Real-Time Floating Timer Indicator**  
  A non-intrusive floating indicator displaying the active session countdown and daily usage metrics. The indicator updates visual cues based on session duration, can be moved across the screen, snapped to screen edges, or hidden directly from the system notification panel.

- **Mindful Pause & Break Sessions**  
  When an active session duration limit is reached, JedaIn provides a pause screen encouraging the user to step away from their device. Users can choose to take a break or confirm an extension before resuming.

- **Daily Usage Tracking & Home Screen Widget**  
  Track daily screen time across monitored apps compared against previous usage. An Android Home Screen widget (`DailyAwarenessWidgetProvider`) provides glanceable awareness directly from the launcher.

- **Clean & Distraction-Free Interface**  
  Built with Material 3 design principles featuring light and dark themes, clear configuration options, and daily mindful activity suggestions.

---

## Privacy & Permissions Disclosure

JedaIn processes all data on-device and does not declare network permissions (`android.permission.INTERNET`).

### Privacy Guarantees
- **100% On-Device Processing:** All usage statistics and preferences are computed and stored locally.
- **No Data Collection:** No personal information, screen content, or user identifiers are collected, stored remotely, or transmitted.
- **No Third-Party Analytics:** The application contains no third-party tracking, advertising, or analytics SDKs.

### System Permissions Used

| Permission | System Identifier | Purpose |
| :--- | :--- | :--- |
| **Usage Access** | `PACKAGE_USAGE_STATS` | Reads foreground app duration locally via Android's `UsageStatsManager` to determine session length. |
| **Appear on Top** | `SYSTEM_ALERT_WINDOW` | Displays the floating timer indicator and the session pause screen over monitored apps. |
| **Foreground Service** | `FOREGROUND_SERVICE`<br>`FOREGROUND_SERVICE_SPECIAL_USE` | Keeps the session timer running reliably while monitored apps are open, with a persistent notification for user control. |
| **Post Notifications** | `POST_NOTIFICATIONS` | Delivers non-intrusive status updates and reminder notifications on Android 13+. |

---

## Tech Stack & Architecture

- **Language:** Kotlin
- **UI Toolkit:** Jetpack Compose, Material 3
- **Architecture:** MVVM (Model-View-ViewModel) with Kotlin Coroutines and StateFlow
- **Platform APIs:** Android `UsageStatsManager`, `WindowManager`, `AppWidgetProvider`, Foreground Service
- **Storage:** Android `SharedPreferences`
- **Minimum SDK:** Android 8.0 (API Level 26)
- **Target SDK:** Android 16 (API Level 37)

---

## Project Structure

```text
jedain/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/jedain/app/
│   │   │   │   ├── AppMonitorRepository.kt          # Usage stats retrieval & monitored apps data
│   │   │   │   ├── BreakSessionManager.kt           # Session timer & break management
│   │   │   │   ├── DailyActivityPromptProvider.kt   # Contextual activity recommendations
│   │   │   │   ├── DailyAwarenessWidgetProvider.kt  # Android Home Screen widget
│   │   │   │   ├── FloatingBubbleService.kt         # Foreground Service & floating overlay
│   │   │   │   ├── FloatingSnackbarManager.kt       # In-app reminder notifications
│   │   │   │   ├── HomeScreen.kt                    # Main dashboard & permission setup
│   │   │   │   ├── InterventionActivity.kt          # Session pause screen
│   │   │   │   ├── MainActivity.kt                  # Main entry point
│   │   │   │   ├── ManageMonitoredAppsScreen.kt     # Monitored app selection interface
│   │   │   │   ├── MonitoredAppsViewModel.kt        # State management for monitored apps
│   │   │   │   ├── PermissionHelper.kt              # Permission checks and system intent handlers
│   │   │   │   ├── ScreenTimeCalculator.kt          # Usage event aggregation logic
│   │   │   │   ├── SetTargetScreen.kt               # Target screen time configuration
│   │   │   │   ├── TargetSettingsPreferences.kt     # Local preferences management
│   │   │   │   └── ui/theme/                        # Colors, typography, and styling
│   │   │   ├── res/                                 # Drawables, layouts, and values
│   │   │   └── AndroidManifest.xml                  # Manifest declarations
│   │   └── test/                                    # Unit tests
│   └── build.gradle.kts                             # Module build configuration
├── build.gradle.kts                                 # Root build script
├── settings.gradle.kts                              # Project settings
└── README.md
```

---

## Screenshots

| Dashboard | Floating Indicator | Pause Screen | App Selection |
| :---: | :---: | :---: | :---: |
| *(Screenshot Placeholder)* | *(Screenshot Placeholder)* | *(Screenshot Placeholder)* | *(Screenshot Placeholder)* |

---

## Building from Source

```bash
# Clone the repository
git clone https://github.com/ArryPutra/jedain.git
cd jedain

# Build debug APK
./gradlew assembleDebug

# Run unit tests
./gradlew testDebugUnitTest
```

---

## Author & License

Developed by **Arry Kusuma Putra** (2026).  
Licensed under the [MIT License](LICENSE).
