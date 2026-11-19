# Antigravity Pulse

[![License: MIT](https://img.shields.io/badge/License-MIT-FFC799.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android%207.0%2B%20(API%2024%2B)-3DDC84.svg)](https://www.android.com/)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4-7F52FF.svg)](https://kotlinlang.org/)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2B%20Material%20You-4285F4.svg)](https://developer.android.com/compose)
[![Download APK](https://img.shields.io/badge/Download-APK%20v1.0.0-FFC799.svg?style=flat&logo=android&logoColor=000000)](releases/AntigravityPulse-v1.0.0.apk?raw=true)

**Antigravity Pulse** is a fast, fullscreen-first open-source Android companion designed for connecting to remote desktop sessions over HTTPS/WSS from your phone. Built with Kotlin, Jetpack Compose, **Material You dynamic colors**, and **subtle frosted glass surfaces** on a true `#000000` OLED dark interface, it keeps your active remote session pinned in RAM with zero reloads, provides a compact translucent movable control handle, supports native Android file uploads, and syncs your saved hosts and reusable prompts across devices.

---

## Install Directly on Your Android Phone (1-Tap Download)

You do **not** need Android Studio, a computer, or any build tools to install Antigravity Pulse. A pre-built, ready-to-install Android APK is included directly in this repository:

### Option 1: Direct 1-Tap APK Download (Recommended)
1. On your Android phone, tap the link below to download the APK directly:
   - **[Download AntigravityPulse-v1.0.0.apk (Direct Download)](releases/AntigravityPulse-v1.0.0.apk?raw=true)**
   - *(Mirror / File View: [releases/AntigravityPulse-v1.0.0.apk](releases/AntigravityPulse-v1.0.0.apk) • [SHA-256 Checksum](releases/AntigravityPulse-v1.0.0.apk.sha256))*
2. Open the downloaded `.apk` file from your notification shade or Files app.
3. If Android prompts you, tap **Settings** and enable **Allow from this source** for your browser or file manager, then tap **Install**.
4. Launch **Antigravity Pulse** from your home screen.

### Option 2: GitHub Releases & Obtainium
If you also publish tagged versions on the **[Releases](https://github.com/IceCool30/antigravity-pulse/releases)** page, [Obtainium](https://github.com/ImranR98/Obtainium) users can add this repository URL to track future updates automatically.

### Verify APK Integrity (Optional)
Verify your downloaded APK against the bundled SHA-256 checksum:
```bash
cd releases && sha256sum -c AntigravityPulse-v1.0.0.apk.sha256
```

---

## Features

- **Fullscreen-First OLED Experience (`#000000`)**
  Launches directly into distraction-free fullscreen mode on true `#000000` OLED black so your remote session uses every pixel of your screen.
- **Compact Translucent Movable Side Handle**
  - Docks cleanly as a sleek slim edge tab when placed along the left or right screen edge.
  - Morphs into a compact rounded floating pill when dragged anywhere else on screen.
  - Remembers your preferred position automatically across restarts.
  - Tap once to open quick controls (Switch Host, Scan QR, Reload, Cloud Sync, Settings, Show/Hide Bars).
- **Zero-Reload RAM-Pinned Session Bridge**
  Keeps your active remote WebView pinned in memory when switching between tabs or opening settings, with an optional **DOM Lag Booster** for smoother scrolling on heavy pages.
- **Instant QR Pairing & Multi-Host Manager**
  Scan pairing QR codes (`https://`, `http://`, `antigravity://pair`, or JSON payloads) using CameraX + ML Kit, or paste any remote URL manually and switch between saved workstations in one tap.
- **Native Android File Picker (`+` Attachment Support)**
  Clicking the `+` / upload button inside your remote session opens Android's native Storage Access Framework document picker (`ACTION_OPEN_DOCUMENT`) with multi-file selection and **zero broad storage permissions**.
- **Cloud Sync (Saved Hosts & Reusable Prompts)**
  Sign in with Google via Jetpack Credential Manager to back up and restore your saved remote host bookmarks and reusable prompt templates using Firebase Firestore.
- **Biometric App Lock**
  Optionally protect saved host URLs and auth tokens with Android Fingerprint, Face Unlock, or Device PIN/Pattern.
- **Material You + Subtle Frosted Glass UI (`#000000` OLED)**
  Adapts accent colors dynamically from your Android 12+ wallpaper (Material You) while preserving a true `#000000` OLED black background, paired with GPU-friendly translucent frosted glass surfaces and specular hairline borders. Both **Material You Dynamic Color** and **Frosted Glass Surfaces** can be toggled anytime in Settings alongside 12 hand-tuned dark presets, custom hex pickers, contrast modes, font families, and font sizing.

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
| **UI & Styling** | Jetpack Compose, Material Design 3, Edge-to-Edge WindowInsets, Adaptive Navigation (Bar / Rail) |
| **State & Concurrency** | ViewModel, Kotlin Coroutines, `StateFlow`, `collectAsStateWithLifecycle` |
| **Local Storage** | Room Database (SQLite) + SharedPreferences for instant appearance & handle state |
| **Cloud Sync & Auth** | Firebase Authentication (Jetpack Credential Manager Google Sign-In), Cloud Firestore, Firebase App Check |
| **QR Scanning** | AndroidX CameraX + Google ML Kit Barcode Scanning |
| **Networking & Bridge** | OkHttp, Android WebView with custom `WebChromeClient` (`ACTION_OPEN_DOCUMENT`) & `WebViewClient` scheme guard |
| **Security** | AndroidX BiometricPrompt, HTTPS/HTTP URL scheme enforcement, zero broad storage permissions |
| **Testing** | JUnit 4, Robolectric, Compose UI Test (`createComposeRule`) |

### Project Structure
```text
├── releases/                  # Ready-to-install direct-download Android APK & SHA-256 checksum
├── .github/workflows/         # Optional CI & GitHub Release APK workflows
├── app/src/main/java/com/example/
│   ├── MainActivity.kt        # Root activity, biometric gate, file picker & fullscreen scaffold
│   ├── data/
│   │   ├── cloud/             # Firebase Firestore sync & Google Sign-In repository
│   │   ├── local/             # Room entities, DAO, and database singleton
│   │   └── remote/            # RAM-pinned WebView manager & bridge client
│   ├── ui/
│   │   ├── components/        # Movable translucent frosted-glass side handle & control panel
│   │   ├── screens/           # Remote bridge, QR scanner, Cloud Sync, Appearance & Developer Settings
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
- **Gradle 9.3.1** (or Android Studio Ladybug / Meerkat+)

### 1. Clone the Repository
```bash
git clone https://github.com/IceCool30/antigravity-pulse.git
cd antigravity-pulse
```

### 2. Optional Environment Configuration
Copy `.env.example` to `.env` if you are configuring optional environment keys:
```bash
cp .env.example .env
```
If you want to connect your own Firebase project for Cloud Sync, replace `app/google-services.json` with your Firebase Android configuration file and deploy `firestore.rules`.

### 3. Build the APK
```bash
# Build a debug APK
gradle :app:assembleDebug

# Output location:
# app/build/outputs/apk/debug/app-debug.apk
```

### 4. Run Unit & UI Tests
```bash
gradle :app:testDebugUnitTest
```

---

## Publishing Releases (100% Free — No GitHub Actions Required)

### Option A: Manual GitHub Release Upload (Works on All Accounts, $0 Cost)
GitHub Releases binary hosting is **100% free** and works even if GitHub Actions is paused or disabled on your account:
1. Build or export your APK (`AntigravityPulse-v1.0.0.apk`).
2. Open your repository on GitHub → click **Releases** (right sidebar) → **Create a new release**.
3. Create tag `v1.0.0`, enter release notes, and drag & drop **`AntigravityPulse-v1.0.0.apk`** into the **Attach binaries** area.
4. Click **Publish release** — Android users (and Obtainium) can immediately download and install the APK directly on their phones.

### Option B: Optional Automated GitHub Actions Workflow
If GitHub Actions is enabled on your account, pushing a tag (`git tag v1.0.0 && git push origin v1.0.0`) or running **Actions → Build & Publish Release APK** automatically runs the test suite, builds a signed APK + SHA-256 checksum, and publishes the GitHub Release.
- *(Optional)* Configure custom release signing secrets under **Settings → Secrets and variables → Actions**: `KEYSTORE_BASE64`, `STORE_PASSWORD`, and `KEY_PASSWORD`.

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
| **No Storage, Mic, or Notification Permissions** | File selection uses Android's built-in system picker (`ACTION_OPEN_DOCUMENT`) with zero storage permissions. |

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
