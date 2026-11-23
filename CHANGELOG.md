# Changelog

All notable changes to **Antigravity Pulse** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.2] - 2026-11-23

### Added
- In-app update checker and Updates settings section with release notes, download progress, and
  one-tap verified install.
- Daily background update check with an Android notification when a newer release is available.

### Changed
- Launcher, adaptive, monochrome, and notification icons now use the shared obsidian/warm-peach orbit
  mark used by Antigravity Pulse Desktop.

## [1.2.1] - 2026-11-23

### Fixed & Optimized
- **Instant Touch & 60/120Hz Input Responsiveness**:
  - Inactive RAM-pinned workspaces now switch to `View.GONE` with a per-instance `onPause()` — never calling global `pauseTimers()` — so background WebViews stop consuming GPU and UI thread time while full 1+ hour zero-reload retention is preserved.
  - Staggered keep-alive heartbeats at 160 ms intervals avoid synchronized wake-ups across workspaces.
- **Direct Hardware WebView Compositing**:
  - Removed offscreen `graphicsLayer { alpha = ... }` wrappers from live `AndroidView(WebView)` workspace slots and multi-tab Pulse Browser containers, so Chromium composites directly without costly offscreen copies or touch-dispatch lag.
  - Guarded inactive offline continuity overlays so hidden workviews never repaint needlessly.
  - Explicitly declared `android:hardwareAccelerated="true"` in the manifest.
- **Eliminated Recomposition & Main-Thread Stalls**:
  - Removed redundant Lag Booster JavaScript re-injection inside `AndroidView(update)`.
  - Reduced and deferred routine offline snapshot captures: 720 px max dimension, 1200 ms delay, and a 30-second throttle.
  - Remote DOM notification scanning now uses layout-reflow-free `textContent` instead of `innerText`, with a touch/scroll cooldown.
  - Removed duplicate `onPause` state serialization.
- **Crisp Movable Floating Handle**:
  - Unified tap-and-drag gesture handling on the side edge handle so taps open instantly and drags track 1:1 without getting stuck.

## [1.2.0] - 2026-11-22

### Added
- **1+ Hour Multi-Workspace RAM Keep-Alive (Zero Reloads)**:
  - Permanent window-attached lazy workspace stack that keeps every tool alive across tab switches: Antigravity Remote, T3 Code, Google AI Studio, Gemini, GitHub, Vercel, Supabase, and the multi-tab Pulse Browser.
  - Non-detaching Settings overlay that no longer tears down or evicts the active WebView workspace.
  - `RENDERER_PRIORITY_IMPORTANT` renderer priority across all pinned workspaces to resist OS process reclamation.
  - Play-compliant silent `dataSync` foreground keep-alive service (`WorkspaceKeepAliveService`) with a lightweight 45-second JS timer & WebSocket heartbeat and Android 15+ safe timeout handling.
  - `onTrimMemory` state and scroll-position backups so restored sessions resume exactly where they left off.
  - New **Memory & Keep-Alive** Settings section with a live RAM badge, per-workspace release chips, and a direct **Battery Unrestricted** shortcut.
- **T3 Code Web Workspace (`https://app.t3.codes/`)**:
  - Full RAM-pinned T3 Code web experience (`T3CodeWebScreen` + `T3CodePinnedWebViewManager`).
  - Multi-environment switcher covering T3 Cloud plus custom self-hosted and Tailscale HTTPS servers.
  - QR pairing that preserves one-time `#token=` handshake fragments, along with `t3://` deep-link support.
  - OAuth popup handling, Desktop View, and Offline Visual Continuity.
- **All-in-One Developer Workspaces & Multi-Tab Pulse Browser**:
  - RAM-pinned GitHub, Vercel, Supabase, and Gemini workspaces with zero-reload tab switching and per-workspace Desktop View.
  - New multi-tab **Pulse Browser** (`PulseBrowserScreen` + `PulseBrowserManager`) with persistent tabs and instant tab switching.
- **Safe, GPU-Only UI Animations**:
  - Subtle finite transitions (<= 190ms) across workspace switching, the floating control panel, Settings overlay, chevrons, and status banners.
  - Animations are compositor/GPU-only with zero WebView layout or reload impact.
  - New **Smooth UI Animations** toggle in Settings.
- **Offline Visual Continuity & Security Hardening**:
  - Cached view retention and automatic reconnection on network drops across all workspaces.
  - URL credential redaction in backups and notifications.
  - Secure media origin checks for camera and microphone access.
  - Download filename sanitization against path traversal.
  - Light Mode adaptive contrast improvements.

### Changed & Refactored
- **Workspace Navigation & Appearance**:
  - Expanded workspace navigation with first-class pinned developer workspaces.
  - Refactored appearance settings for the new Motion and Memory & Keep-Alive sections.
  - Hardened WebView error reporting and general stability across workspace managers.

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
