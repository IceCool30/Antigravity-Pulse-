# Changelog

All notable changes to **Antigravity Pulse** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.1.0] - 2026-11-20

### Added
- **Fullscreen Google AI Studio Web (`aistudio.google.com`)**:
  - Full-screen RAM-pinned Google AI Studio workspace (`AiStudioWebScreen`) replacing the deprecated native studio mockup.
  - Seamless dual-WebView lifecycle via `AiStudioPinnedWebViewManager` running alongside the Remote session with zero tab evictions or reloads.
  - Quick action controls (`Prompts`, `Build Apps`, `Library`, `Lag Booster`, `Reload`, `Open in Browser`) accessible via the translucent movable side handle (`MovableFloatingControlPanel`) in fullscreen mode.
  - Collapsible quick-jump toolbar with Lag Booster toggle when system bars are visible.
- **Android 13+ Material You Monochrome Adaptive Icon**:
  - Vector adaptive monochrome icon (`ic_launcher_monochrome`) supporting dynamic Material You theming on modern Android home screens.
- **Automated GitHub Release Workflow (`release-apk.yml`)**:
  - CI/CD workflow triggering on version tags (`v*`) and manual dispatch to build installable APKs (`Antigravity-Pulse-v1.1.0.apk`), compute `SHA256SUMS.txt`, and publish GitHub Releases via `softprops/action-gh-release@v2`.

### Changed & Refactored
- **Lean Database Schema (Room v4)**:
  - Upgraded Room database to version 4 with non-destructive migrations `MIGRATION_2_3` and `MIGRATION_3_4`.
  - Safely purged obsolete legacy session tables (`agent_sessions`, `session_messages`, `file_diffs`, `terminal_logs`, `copilot_messages`, `aiStudioPresets`) while fully preserving user bookmarks in `server_profiles`.
- **Cloud Sync Consolidation**:
  - Streamlined Firestore cloud backup to `users/{uid}/serverProfiles` and `users/{uid}/copilotPrompts`, with strict default-deny security rules rejecting deprecated `aiStudioPresets`.
- **Network Stack Simplification**:
  - Removed unused OkHttp WebSocket client implementation and commented out unused `okhttp` dependency.

### Security & Privacy
- **Strict URL & QR Navigation Scheme Guard**:
  - Multi-layer sanitization rejecting dangerous URL schemes (`javascript:`, `file:`, `content:`, `data:`, `intent:`) in both manual host input and CameraX/ML Kit QR scanner payloads.
- **Disabled WebView Geolocation**:
  - Explicitly disabled geolocation access (`setGeolocationEnabled(false)`) across all WebViews.
- **CameraX + ML Kit Lifecycle Cleanup**:
  - Cleanly shuts down camera execution and ML Kit analyzers upon dialog dismissal to eliminate sensor leaks and background battery drain.
- **Smart 60-Second Background Biometric Re-Lock**:
  - Enforces automatic biometric re-authentication when returning to the app after being backgrounded for more than 60 seconds, accompanied by `FLAG_SECURE` screen privacy.
- **Real Network Auto-Reconnect**:
  - Replaced interval polling with Android's native `ConnectivityManager.NetworkCallback` to detect cellular/Wi-Fi transitions and automatically re-establish remote session connectivity.

### Performance & Battery Optimization
- **Idle-Aware DOM Lag Booster**:
  - Implemented `domDirty` state tracking to throttle DOM mutations and achieve 0% CPU consumption when pages are idle.
- **Memoized Compose Theme & Typography**:
  - Memoized typography, color schemes, and system bar contrast styles to prevent unnecessary recompositions during scrolling and drag gestures.
- **Pure `#000000` OLED Launch Theme**:
  - Flash-free initial launch on AMOLED and OLED displays.

## [1.0.0] - 2026-11-18

### Added
- Fullscreen-first Android companion experience with **Material You dynamic wallpaper accents**, **subtle frosted glass surfaces**, and a true `#000000` OLED dark default theme.
- Compact, translucent frosted-glass movable side control handle that docks cleanly to screen edges or floats freely as a rounded pill with persistent coordinates.
- RAM-pinned remote session WebView with optional DOM Lag Booster, instant session resume after biometric unlock, and automatic responsive viewport scaling.
- Native Android system file picker (`ACTION_OPEN_DOCUMENT`) wired to the WebView `+` file attachment trigger with multi-file selection and zero storage permissions.
- Built-in CameraX + ML Kit QR code scanner and multi-host profile switcher.
- Google Sign-In Cloud Sync (Firebase Authentication + Firestore) for backing up and restoring saved remote hosts and reusable prompt presets with strict per-user isolation.
- Native Android notifications bridging remote PWA agent alerts (`window.Notification` & `ServiceWorker.showNotification`) plus dedicated channels for host connections, security warnings, and Cloud Sync activity, with an in-app **Test Alert** button.
- Optional Biometric App Lock (Fingerprint / Face / Device PIN) with `FLAG_SECURE` screen privacy protection.
- Toggleable **Material You Dynamic Color** and **Frosted Glass Surfaces** in Settings alongside 12 dark color presets, custom hex color pickers, contrast modes, font family selector, and font size controls.
- About Developer profile card in Settings with direct Email and GitHub links.
- Open-source documentation (`README.md`, `CONTRIBUTING.md`, `SECURITY.md`, MIT `LICENSE`) and GitHub Releases distribution guide.

---
Authored by **Solomon Ogar Bukie** ([@IceCool30](https://github.com/IceCool30)).
