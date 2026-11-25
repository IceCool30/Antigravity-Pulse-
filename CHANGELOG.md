# Changelog

All notable changes to **Antigravity Pulse** are documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [1.2.7] - 2026-11-25

### Fixed
- **Readable In-App Notification Banner (`StatusFeedbackBanner`)**:
  - Switched the banner's frosted glass `baseColor` from `EmeraldOnline` (`#10B981`, whose luminance crossed `FrostedGlass`'s light-surface threshold and rendered an 88% solid green fill behind green text) to the active theme `surface` color with high-contrast `fg` message text, an emerald indicator dot, and a distinct `Dismiss` pill.
- **Google Sign-In Sync & Browser Fallback**:
  - Added failure classification (`classifyGoogleCredentialFailure`) so Play Services credential/reauth errors fall back automatically to Firebase browser OAuth (`OAuthProvider("google.com")`) instead of silently resetting to `"Sign in with Google"`.
  - Added a direct **"Sign in via Browser"** option and `pendingAuthResult` recovery on return, executed token sign-in under `NonCancellable` on the Activity lifecycle scope, emitted initial auth state immediately in `authStateFlow()`, and guarded `clearFirestoreLocalCache` with a `Mutex`.
- **Cleaner Cloud Sync Copy**:
  - Removed `"Firestore"` inscriptions from user-facing Cloud Sync cards and prompt snippet text.

## [1.2.6] - 2026-11-25

### Fixed & Optimized
- **Post-Sign-In Touch, Scroll & UI Responsiveness Across All 8 Workspaces**:
  - Added `PinnedWebViewHost` (`TouchSafeWebViewContainer`) calling `requestDisallowInterceptTouchEvent(true)` on active `ACTION_DOWN` / `ACTION_MOVE` touches and clipping compositor invalidations with `Modifier.graphicsLayer { clip = true }`, preventing Compose `AndroidViewHolder` pointer arbitration from stalling inner `overflow-y: auto` and horizontal SPA containers after authentication.
  - Skipped draw and semantics passes (`drawWithContent {}`) plus O(1) touch short-circuiting for inactive `PulseBrowserScreen` tabs and removed redundant background layers behind WebViews.
  - Eliminated root Compose recomposition storms by isolating per-tab, browser progress (`1..100%`), `canGoBack`, and RAM status collectors (`WorkspaceBackHandler`, `WorkspaceBottomNavigationBar`, `WorkspaceKeepAliveServiceObserver`, `FullscreenMovableControlPanelHost`, and `PulseBrowserManager.activeTabCanGoBack`).
  - Replaced root `WindowInsets.safeDrawing` resizing with `WindowInsets.systemBars.union(WindowInsets.displayCutout)` + `.consumeWindowInsets(innerPadding)` and applied `Modifier.imePadding()` only on the active workspace slot, stopping hidden WebViews from relayouting during keyboard animations.
  - Stopped calling `resumeTimers()` when hiding background WebViews inside `applyWindowAttachedVisibility`, debounced `resumeTimersOnce` (`250 ms`), unified `configureScrollResponsiveWebView` (removing `setInitialScale(0)` relayout quirks), and enabled `databaseEnabled = true` in `PulseBrowserManager`.
  - Restored zero-wildcard GPU optimizations (`backdrop-filter: none !important`, `background-attachment: scroll !important`, `scroll-behavior: auto !important`) in `LAG_BOOSTER_CSS`, added SPA `pushState` / `replaceState` / `popstate` re-injection and `onPageCommitVisible` early styling across all WebView managers, preserved `width=device-width` SPA layouts in `injectMobileLayoutFix`, and throttled/tail-bounded `attachAgentObserver` with `requestIdleCallback`.

## [1.2.5] - 2026-11-24

### Fixed & Optimized
- **Reliable In-App Auto-Updater (`UpdateChecker`, `UpdateDownloader`, `UpdateInstaller`, `UpdateSettingsSection`)**:
  - Upgraded release lookup to a 4-tier HTTPS chain with explicit redirect following and strict host allowlisting: GitHub Releases REST API → direct `/releases/latest` HTTP redirect (zero API rate limit) → cache-busted raw GitHub manifest → jsDelivr CDN mirror.
  - Fixed guaranteed `FileNotFoundException` by creating `cacheDir/update/` before writing and downloading safely to a `.part` temp file with single-pass streaming SHA-256 verification.
  - Fixed download progress stuck at `0%` (now tracks cumulative bytes + MB readout throttled at 100 ms intervals).
  - Fixed Android package installer launch bug by using `Intent.setDataAndType(uri, APK_MIME_TYPE)` instead of setting `type` after `data`.
  - Fixed App Updates settings UI falsely reporting "You're up to date" while checking or on network errors, and stopped the daily check from stamping its 24h cooldown on failed fetches.
- **Smooth 60/120Hz Scrolling Across All 8 Web Workspaces**:
  - Switched all pinned WebViews from non-UI `applicationContext` to a display-aware Activity `MutableContextWrapper` (restoring native 90/120Hz VSync, touch slop, and fling physics) and cleanly detach back to `applicationContext` in `MainActivity.onDestroy()` to prevent leaks.
  - Removed the universal `*` / `*::before` / `*::after` wildcard CSS rule from `LAG_BOOSTER_CSS` that invalidated Blink's style-sharing cache on every virtualized scroll frame.
  - Disabled `offscreenPreRaster` across all pinned WebViews so multiple RAM-pinned tabs never starve Chromium's GPU tile pool during active scrolling.
  - Removed routine `onPageFinished` main-thread `webView.draw()` snapshots, `saveState()`, and `flushCookies()` stalls; offline continuity snapshots remain active on network drops and backgrounding.
  - Paused cross-tab keep-alive JS heartbeats while the app is in the foreground, filtered `preferredDisplayModeId` to match current physical display resolution, and removed the elevated shadow and extra `drawWithContent` wrapper over the active WebView.
- **Release Automation**:
  - Added `scripts/publish-release.ps1` for deterministic local signed builds, certificate verification, `SHA256SUMS.txt` + `app/update-manifest.json` generation, and GitHub Release publishing.

## [1.2.4] - 2026-11-24

### Fixed & Optimized
- **Scroll jank elimination across every web workspace**:
  - Reverted `overScrollMode` to `OVER_SCROLL_NEVER` on all eight WebViews. The Android 12+ "stretch" overscroll effect re-renders the entire WebView through an offscreen stretch layer while dragging past either end of a page, which is a major source of dropped frames on mid-range GPUs.
  - Disabled the native overlay scrollbar (`isVerticalScrollBarEnabled = false`). Pages draw their own scroll indicators, and the native bar invalidated the WebView on every scroll frame.
  - The Lag Booster stylesheet now also strips `backdrop-filter` / `-webkit-backdrop-filter` and `background-attachment: fixed`, and pins `scroll-behavior: auto`. Chromium performs a backdrop texture readback every scroll frame for frosted sticky headers and toolbars, which alone can drop a third of all frames; `background-attachment: fixed` forces a full-viewport repaint per frame.
  - Inactive keep-alive workspace slots are now skipped at the draw phase. They stay composed, measured and attached (so their WebViews never reload), but no longer replay their full-screen background, toolbar and dividers underneath the live workspace on every WebView frame.
  - Removed the redundant full-screen `background` behind the active WebView container; the app Scaffold already paints it.
  - Remote agent observer: replaced `innerText` reads (which force a synchronous Blink layout) with `textContent`, scoped text scans to the `<main>` content region instead of the whole document, and skipped the approval-button walk on timer-only scans when the DOM did not change. Mutation throttle raised 900 ms → 1400 ms and idle timer 1600 ms → 2400 ms.

## [1.2.3] - 2026-11-23

### Fixed & Optimized
- **60/120Hz Hardware-Accelerated Smooth Scrolling Across All Web Workspaces**:
  - Restored Chromium GPU compositor-thread root scrolling across all 8 web apps (Antigravity Remote, T3 Code Web, Google AI Studio, Pulse Browser, GitHub, Vercel, Supabase, and Gemini) by removing blocking `overflow-x: hidden !important;` from root `html, body`.
  - Removed `text-rendering: optimizeLegibility;`, eliminating synchronous HarfBuzz kerning and font-shaping stalls on every scrolled text frame.
  - Eliminated global `max-width: 100vw !important;` overrides on `#root, #app, main` that caused continuous layout reflows in Monaco Editor, virtualized chat feeds, and diff viewers.
  - Enabled Chromium tile pre-rasterization (`settings.offscreenPreRaster = true`) to pre-render offscreen content before fast fling gestures enter the viewport.
  - Switched `overScrollMode` to `View.OVER_SCROLL_IF_CONTENT_SCROLLS` with `scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY` and `isNestedScrollingEnabled = false`, restoring Android's natural fling deceleration curves and StretchEdgeEffect physics.
  - Replaced wildcard `touch-action: manipulation !important;` on buttons/links with compositor-friendly `html, body { touch-action: pan-x pan-y pinch-zoom; overscroll-behavior-y: contain; }`, allowing multi-touch gestures to execute asynchronously on the GPU compositor thread without waiting on JavaScript.
  - Replaced `getBoundingClientRect()` loops in remote agent observer with non-reflowing native `checkVisibility()` and outer dimension metrics, pausing observer scans during active user touch, scroll, and wheel gestures.
  - Explicitly asserted window hardware acceleration (`FLAG_HARDWARE_ACCELERATED`) and configured `preferredDisplayModeId` to request the display's maximum supported refresh rate (90Hz / 120Hz).

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
