# Antigravity Pulse v1.1.0 Release Notes

**Antigravity Pulse v1.1.0** introduces the **Fullscreen RAM-Pinned Google AI Studio Web** experience (`aistudio.google.com`), lean **Room v4** database migrations, comprehensive native Android security hardening, real network auto-reconnect, Android 13+ Material You monochrome adaptive icon, and idle-aware battery optimizations.

---

## What's New in v1.1.0

### 1. Pure Fullscreen Google AI Studio Web (`aistudio.google.com`)
- **Direct Web Workspace**: Full access to Google AI Studio's **Prompts**, **Build Apps**, and **Drive Library** directly on your Android phone.
- **Translucent Movable Control Panel**: In distraction-free fullscreen mode, quick shortcuts (`Prompts`, `Build Apps`, `Library`, `Lag Booster`, `Reload`, `Open in Browser`) are docked neatly inside the floating handle. When system bars are visible, an intuitive collapsible toolbar provides instant navigation.
- **Dual RAM-Pinned WebViews**: Both the Remote Antigravity session and AI Studio Web are pinned simultaneously in RAM—switch between tabs anytime with zero reloads or state loss.

### 2. Lean Local Database & Cloud Sync (Room v4)
- **Room v4 Migration**: Non-destructive migrations (`MIGRATION_2_3` & `MIGRATION_3_4`) streamline local storage down to the lean `server_profiles` table, safely removing deprecated session entities without touching saved host bookmarks.
- **Per-User Cloud Sync**: Google Sign-In via Jetpack Credential Manager and Firebase Firestore with zero-trust security rules strictly isolating `users/{uid}/serverProfiles` and `users/{uid}/copilotPrompts`.

### 3. Native Android Security & Privacy Hardening
- **Strict URL & QR Sanitization**: Blocks dangerous schemes (`javascript:`, `file:`, `content:`, `data:`, `intent:`) from being executed or opened.
- **Disabled Geolocation**: WebView geolocation access is explicitly disabled (`setGeolocationEnabled(false)`).
- **Clean CameraX + ML Kit Lifecycle**: Camera and barcode scanning resources are cleanly closed on dialog dismissal to avoid battery drain.
- **Smart 60-Second Biometric Re-Lock**: Fingerprint/Face Unlock automatically re-arms after 60 seconds in the background, paired with `FLAG_SECURE` screen privacy.
- **Network Resilience**: Uses Android's native `ConnectivityManager.NetworkCallback` to detect network handoffs (Wi-Fi ↔ Cellular) and automatically reconnect active sessions.

### 4. Performance & Visual Polish
- **Idle-Aware DOM Lag Booster**: Automatically sets CPU utilization to 0% during idle states via dirty-check throttling.
- **Material You Monochrome Adaptive Icon**: Native vector monochrome icon (`ic_launcher_monochrome`) for Android 13+ themed home screens.
- **Memoized Compose Theme**: Instantaneous rendering with zero unnecessary recompositions.
- **Pure `#000000` OLED Launch**: Flash-free start on AMOLED/OLED displays.

---

## Direct APK Installation

You do **not** need Android Studio or a computer to install Antigravity Pulse.

### Option 1: Direct Download
1. Download **`Antigravity-Pulse-v1.1.0.apk`** from the **Assets** section below.
2. Open the downloaded file from your browser downloads or file manager.
3. If prompted by Android, tap **Settings** and enable **Allow from this source**, then tap **Install**.
4. Launch **Antigravity Pulse** from your home screen.

### Option 2: Automatic Updates via Obtainium
Track and auto-update directly from GitHub Releases using [Obtainium](https://github.com/ImranR98/Obtainium):
1. In Obtainium, tap **Add App**.
2. Enter repository URL: `https://github.com/IceCool30/Antigravity-Pulse-`
3. Tap **Add** → **Install**.

---

## Integrity & Verification

Verify the SHA-256 checksum of your downloaded APK using `SHA256SUMS.txt`:

```bash
sha256sum -c SHA256SUMS.txt
```

---

**Developed & Maintained by:**  
**Solomon Ogar Bukie** ([@IceCool30](https://github.com/IceCool30))  
Email: [solomonogarbukie@gmail.com](mailto:solomonogarbukie@gmail.com)
