# Antigravity Pulse

[![License: MIT](https://img.shields.io/badge/License-MIT-FFC799.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-3DDC84.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%20You-4285F4.svg)](https://developer.android.com/compose)
[![Download APK](https://img.shields.io/badge/Download-APK%20via%20Releases-FFC799.svg?style=flat&logo=android&logoColor=000000)](https://github.com/IceCool30/Antigravity-Pulse-/releases/latest)

**Antigravity Pulse** is a fast, fullscreen-first open-source Android companion designed for connecting to remote desktop sessions and Google AI Studio from your phone. Built with Kotlin, Jetpack Compose, **Material You dynamic colors**, and **subtle frosted glass surfaces** on a true `#000000` OLED dark interface, it keeps your active remote session and Google AI Studio pinned in RAM with zero reloads, provides a compact translucent movable control handle, supports native Android file uploads, and syncs your saved hosts and reusable prompts across devices.

---

## Install Directly on Your Android Phone

You do **not** need Android Studio or a computer to install Antigravity Pulse. You can download and install the APK directly from this repository's GitHub Releases page:

### Option 1: Direct APK Download (Recommended)
1. Open this repository's **[Releases](https://github.com/IceCool30/Antigravity-Pulse-/releases/latest)** page on your Android phone.
2. Under **Assets**, tap the **`Antigravity-Pulse-v1.1.0.apk`** file to download it.
3. Open the downloaded `.apk` file from your notification shade or Files app.
4. If Android prompts you, tap **Settings** and enable **Allow from this source** for your browser or file manager, then tap **Install**.
5. Launch **Antigravity Pulse** from your home screen.

### Option 2: Automatic Updates with Obtainium
If you use [Obtainium](https://github.com/ImranR98/Obtainium) to track open-source Android apps from GitHub:
1. Tap **Add App** in Obtainium.
2. Paste this repository URL:
   ```text
   https://github.com/IceCool30/Antigravity-Pulse-
   ```
3. Tap **Add** → **Install** to receive automatic notifications whenever a new release APK is published.

---

## 3 Main Tabs & Core Architecture

1. **Remote (`RetainedWebViewBridgeScreen`)**
   - RAM-pinned fullscreen WebView for remote Antigravity and workstation desktop sessions.
   - Zero-reload session retention across tab switching and phone backgrounding.
   - Native file attachment picker (`ACTION_OPEN_DOCUMENT`) and PWA notification bridge.
2. **AI Studio (`AiStudioWebScreen`)**
   - Pure fullscreen RAM-pinned `aistudio.google.com` workspace for direct mobile access to **Prompts**, **Build Apps**, and **Drive Library**.
   - In distraction-free fullscreen mode, quick action buttons (`Prompts`, `Build Apps`, `Library`, `Lag Booster`, `Reload`, `Open in Browser`) live unobtrusively inside the translucent movable floating panel (`MovableFloatingControlPanel`).
   - When system bars are visible, an intuitive collapsible toolbar provides quick navigation.
3. **Cloud Sync (`StorageAndCloudSyncScreen`)**
   - Streamlined Room v4 local database (`server_profiles`) with non-destructive migrations (`MIGRATION_2_3` and `MIGRATION_3_4`).
   - Google Sign-In via Jetpack Credential Manager and Firebase Firestore syncing `users/{uid}/serverProfiles` and `users/{uid}/copilotPrompts` with strict default-deny rules.

---

## Key Features

- **Fullscreen-First OLED Experience (`#000000`)**
  Launches directly into distraction-free fullscreen mode on true `#000000` OLED black with zero flash on startup.
- **Dual RAM-Pinned WebViews with Zero Tab Eviction**
  Both the Remote workstation session and Google AI Studio Web remain pinned in application memory simultaneously. Switch between tabs instantly without cold reloads.
- **Compact Translucent Movable Side Handle**
  - Docks cleanly as a sleek edge tab when placed along screen edges.
  - Morphs into a compact rounded floating pill when dragged anywhere on screen.
  - Remembers its exact position across app launches.
  - Provides instant access to host switching, QR pairing, AI Studio shortcuts, DOM Lag Booster, and system bar toggles.
- **Idle-Aware DOM Lag Booster**
  Hardware-accelerated CSS animation layer and dirty-state throttling that drops CPU utilization to 0% when pages are idle, maximizing battery longevity.
- **Instant QR Pairing & Multi-Host Manager**
  Scan pairing QR codes using CameraX + ML Kit with strict scheme sanitization, or paste any remote URL manually to switch workstations in one tap.
- **Native Android File Picker (`+` Attachment Support)**
  Opens Android's native Storage Access Framework document picker (`ACTION_OPEN_DOCUMENT`) with multi-file selection and zero broad storage permissions.
- **Native Agent & App Notifications**
  Bridges remote PWA notifications straight into native Android heads-up alerts so you never miss an agent task completion.
- **Smart 60-Second Biometric Re-Lock & `FLAG_SECURE`**
  Protects sensitive workstation sessions with biometric authentication that automatically re-locks after 60 seconds in the background, with `FLAG_SECURE` preventing Recents screen leaks.
- **Real Network Auto-Reconnect**
  Listens for network connectivity transitions via `ConnectivityManager.NetworkCallback` and automatically resumes active sessions.
- **Android 13+ Material You Monochrome Adaptive Icon**
  Features a native vector monochrome icon (`ic_launcher_monochrome`) that adapts to user wallpaper themes on modern Android devices.

---

## Quick Start

1. **Launch the App**: On first launch, the welcome screen walks you through the essentials.
2. **Connect Your Host**: Tap the translucent side handle → **Scan QR Code** or **Connect Remote URL**, then enter or scan your host link.
3. **Move the Controls Anywhere**: Drag the side handle to whichever edge or corner feels most comfortable for one-handed use.
4. **Attach Phone Files**: Tap the `+` icon inside your remote session to pick files directly from your phone storage.

---

## Tech Stack & Architecture

Antigravity Pulse follows a clean **MVVM** architecture written 100% in Kotlin:

| Layer | Technologies |
| --- | --- |
| **UI & Styling** | Jetpack Compose, Material Design 3, Edge-to-Edge WindowInsets, Adaptive Navigation (Bar / Rail), Android 13+ Material You Monochrome Adaptive Icon, Memoized Styles & Layouts |
| **State & Concurrency** | ViewModel, Kotlin Coroutines, `StateFlow`, `collectAsStateWithLifecycle` |
| **Local Storage** | Room v4 Database (SQLite with non-destructive migrations `MIGRATION_2_3` & `MIGRATION_3_4`) + SharedPreferences for instant appearance & handle state |
| **Cloud Sync & Auth** | Firebase Authentication (Jetpack Credential Manager Google Sign-In), Cloud Firestore (`serverProfiles` & `copilotPrompts`), Firebase App Check |
| **QR Scanning** | AndroidX CameraX + Google ML Kit Barcode Scanning with strict scheme sanitization & clean lifecycle disposal |
| **Networking & Bridge** | Android WebView with custom `WebChromeClient` (`ACTION_OPEN_DOCUMENT`), `WebViewClient` scheme guard, native `ConnectivityManager.NetworkCallback` auto-reconnect |
| **Security** | AndroidX BiometricPrompt with smart 60-second background re-lock, `FLAG_SECURE`, strict URL/QR scheme guard (blocks `javascript:`, `file:`, `content:`, `data:`, `intent:`), geolocation disabled, zero broad storage permissions |
| **Testing** | JUnit 4, Robolectric, Compose UI Test (`createComposeRule`) |

### Project Structure
```text
├── .github/
│   ├── ISSUE_TEMPLATE/        # Bug report & feature request templates
│   └── workflows/             # Automated APK release CI/CD workflow (release-apk.yml)
├── app/src/main/java/com/example/
│   ├── MainActivity.kt        # Root activity, biometric gate, file picker & fullscreen scaffold
│   ├── data/
│   │   ├── cloud/             # Firebase Firestore sync & Google Sign-In repository
│   │   ├── local/             # Room v4 entities, DAO, and database singleton with migrations
│   │   └── remote/            # Dual RAM-pinned WebView managers (Remote & AI Studio) & bridge client
│   ├── notifications/         # Native Android notification channels & PWA alerts bridge
│   ├── ui/
│   │   ├── components/        # Movable translucent frosted-glass side handle & control panel
│   │   ├── screens/           # Remote bridge, AI Studio Web, QR scanner, Cloud Sync & Settings
│   │   └── theme/             # Material You dynamic theme, Frosted Glass modifier, 12 dark presets & persistence
│   └── viewmodel/             # Reactive state management
├── app/src/test/              # Robolectric & Compose UI unit tests
├── firestore.rules            # Zero-trust Firestore security rules
└── gradle/libs.versions.toml  # Centralized Gradle Version Catalog
```

---

## Building from Source

### Prerequisites
- **JDK 17** or higher
- **Android SDK** (Compile SDK 36, Min SDK 24)
- **Gradle 9.3.1** (or `./gradlew`)

### 1. Clone the Repository
```bash
git clone https://github.com/IceCool30/Antigravity-Pulse-.git
cd Antigravity-Pulse-
```

### 2. Optional Environment Configuration
Copy `.env.example` to `.env` if you are configuring optional environment keys:
```bash
cp .env.example .env
```
If you want to connect your own Firebase project for Cloud Sync, replace `app/google-services.json` with your Firebase Android configuration file and deploy `firestore.rules`.

### 3. Build the APK
```bash
# Build an installable debug APK
./gradlew :app:assembleDebug

# Output location:
# app/build/outputs/apk/debug/app-debug.apk
```

### 4. Run Unit & UI Tests
```bash
./gradlew :app:testDebugUnitTest
```

---

## Publishing Releases & Automated CI/CD

Antigravity Pulse includes an automated GitHub Actions release pipeline (`.github/workflows/release-apk.yml`):
- **Automated Workflow**: Whenever a version tag like `v1.1.0` is pushed, GitHub Actions runs the full unit/Robolectric test suite, compiles an installable APK (`Antigravity-Pulse-v1.1.0.apk`), generates `SHA256SUMS.txt`, and publishes an official GitHub Release automatically with `RELEASE_NOTES.md`.
- **Universal Installability**: When signing keystore secrets are configured in repository secrets, release builds are signed with the upload key; otherwise, the workflow signs using the project's existing debug keystore so Android phones and Obtainium can install the APK immediately.
4. Click **Publish release** — Android users (and Obtainium) can immediately download and install the APK directly on their phones.

---

## Security, Permissions & Privacy

Antigravity Pulse is hardened with defense-in-depth protections for everyday use:

- **Strict Per-User Account Isolation**: Cloud Sync uses Google Sign-In via Jetpack Credential Manager and zero-trust Firestore security rules enforcing strict `request.auth.uid == userId` isolation with global default-deny.
- **Workstation Tokens Never Leave Your Phone**: Cloud Sync backs up host names and `http(s)://` URLs only — secret workstation bearer/pairing tokens are stripped and never uploaded to Firestore.
- **Zero-Trust WebView Hardening**: Rejects untrusted/MITM TLS certificates (`onReceivedSslError` strictly cancels), blocks mixed HTTP content inside HTTPS sessions, enables Safe Browsing, blocks `javascript:`/`file://`/`content://`/`intent:` navigation schemes, removes JavaScript-to-native bridges, and disables WebView DevTools debugging in release builds.
- **Screen Privacy & Backup Isolation**: Enables `FLAG_SECURE` when Biometric App Lock is active (blocking screenshots, screen recording, and Recents thumbnail leaks) and explicitly excludes all local databases and preferences from Android cloud backup and device transfer (`allowBackup="false"`).

| Permission | Purpose |
| --- | --- |
| `INTERNET` / `ACCESS_NETWORK_STATE` | Connects to the remote workstation URL you configure and optional Cloud Sync. |
| `CAMERA` *(runtime, on-demand)* | Requested only when you tap **Scan QR Code** to pair with a remote host. |
| `POST_NOTIFICATIONS` *(runtime, Android 13+)* | Delivers native agent completion alerts, host connection updates, and Cloud Sync notifications. |
| **No Storage or Microphone Permissions** | File selection uses Android's built-in system picker (`ACTION_OPEN_DOCUMENT`) with zero storage permissions. |

---

## Contributing

Contributions, bug reports, and pull requests are welcome. Please read [CONTRIBUTING.md](CONTRIBUTING.md) before submitting changes, and see [SECURITY.md](SECURITY.md) for responsible security disclosures.

---

## License

This project is open source and licensed under the **MIT License**. See [LICENSE](LICENSE) for full details.

---

## Author

**Solomon Ogar Bukie**
- **GitHub:** [@IceCool30](https://github.com/IceCool30)
- **Email:** [solomonogarbukie@gmail.com](mailto:solomonogarbukie@gmail.com)
