# Antigravity Pulse v1.2.7 Release Notes

**Antigravity Pulse v1.2.7** fixes the unreadable in-app notification banner, fixes Google Sign-In not sticking or syncing after account selection, and removes the Firestore label from Cloud Sync.

It upgrades cleanly in place over v1.2.6 (and earlier v1.x releases) with no uninstall and no data loss.

---

## What's Fixed in v1.2.7

### 1. Readable In-App Notification Banner
- **High-Contrast Status Pill**: Fixed the green-on-green status banner caused by `EmeraldOnline` crossing the frosted-glass light threshold. Status banners now use the theme card surface with crisp foreground text, an emerald dot, and a clear `Dismiss` chip.

### 2. Reliable Google Sign-In Sync & Browser Fallback
- **Automatic Fallback & Clear Diagnostics**: When Play Services Credential Manager fails to mint a token, the app surfaces the diagnostic message and falls back to Firebase browser OAuth instead of silently dropping back to the sign-in button.
- **Browser Sign-In Option & Session Sync**: Added a **Sign in via Browser** button, pending OAuth recovery, `NonCancellable` token sign-in, and immediate auth state syncing in Cloud Sync.

### 3. Cleaner Cloud Sync UI
- Removed the `(Firestore)` and `Firestore` text from Cloud Sync screens.

---

# Antigravity Pulse v1.2.6 Release Notes

**Antigravity Pulse v1.2.6** fixes post-sign-in scroll freezes, sluggish touch handling, keyboard resize lag, and GPU compositor bottlenecks across all 8 web workspaces (Antigravity Remote, T3 Code, Google AI Studio, Gemini, GitHub, Vercel, Supabase, and Pulse Browser).

It upgrades cleanly in place over v1.2.5 (and earlier v1.x releases) with no uninstall and no data loss.

---

## What's Fixed in v1.2.6

### 1. Responsive Inner-Container Touch & Smooth 60/120Hz Scrolling
- **Touch-Safe WebView Hosting (`PinnedWebViewHost`)**: Calls `requestDisallowInterceptTouchEvent(true)` during active drags so Compose never stalls inner `overflow-y: auto` or horizontal code/diff scroll containers once signed into an app, while clipping WebView compositor bounds cleanly.
- **Zero-`*` GPU Blur & Parallax Cleanup**: Restores targeted zero-wildcard `backdrop-filter: none !important`, `background-attachment: scroll !important`, and `scroll-behavior: auto !important` rules with early `onPageCommitVisible` injection and SPA route hooks (`pushState` / `replaceState` / `popstate`).
- **Preserved Responsive Viewports**: Stops overwriting `html, body` dimensions on apps that already declare `width=device-width`, and removes `setInitialScale(0)` relayout quirks across all WebViews.

### 2. Zero Root Recomposition & Background Contention
- **Isolated State Collectors**: High-frequency browser tab load ticks (`1..100%`), back-navigation state, and workspace RAM indicators no longer recompose the root `Scaffold` or hidden WebViews.
- **Active-Only Keyboard Resizing**: Soft keyboard (`IME`) insets now resize only the currently visible active workspace instead of forcing background WebViews to relayout on every keyboard frame.
- **Cleaner Background Pausing & Agent Observer**: Hiding background WebViews no longer wakes shared Blink timers, inactive `PulseBrowserScreen` tabs skip draw/semantics passes, and the remote agent observer uses `requestIdleCallback` with tail-bounded DOM reads.

---

# Antigravity Pulse v1.2.5 Release Notes

**Antigravity Pulse v1.2.5** fixes in-app update fetching/downloading/installing and eliminates scrolling slowness across all 8 web workspaces (Antigravity Remote, T3 Code, Google AI Studio, Gemini, GitHub, Vercel, Supabase, and Pulse Browser).

It upgrades cleanly in place over v1.2.4 (and all earlier v1.x releases) with no uninstall and no data loss.

---

## What's Fixed in v1.2.5

### 1. In-App Auto-Updater Fixed End-to-End
- **4-Tier Resilient Release Lookup**: Automatically tries GitHub Releases API → direct `/releases/latest` redirect (immune to REST API rate limits) → cache-busted raw manifest → jsDelivr CDN mirror, strictly over HTTPS on trusted GitHub/CDN hosts.
- **Fixed Download & Install Crashes**: Creates `cacheDir/update/` before download, tracks cumulative download progress (MB + %) with single-pass SHA-256 verification, and fixes `Intent.setDataAndType(...)` so Android's package installer launches cleanly.
- **Accurate Status & Error Reporting**: Settings → App Updates now shows real checking/error/retry states instead of falsely claiming you are up to date on failures, and daily checks retry automatically if a network check fails.

### 2. Smooth 60/120Hz Scrolling Across Every Workspace
- **Display-Aware WebView Context**: All 8 pinned WebViews now attach to the active Activity `UiContext` via `MutableContextWrapper` (restoring native 90/120Hz VSync, touch slop, and fling physics) and cleanly detach on `onDestroy()`.
- **Eliminated Blink Style & GPU Tile Stalls**: Removed the universal `*` CSS wildcard rule from the Lag Booster, disabled `offscreenPreRaster` across pinned tabs so GPU tile memory isn't starved, and removed routine `onPageFinished` main-thread snapshot/cookie stalls.
- **Zero Foreground Interference**: Cross-tab keep-alive JS heartbeats now pause while you are actively scrolling in the foreground, and the active WebView renders directly without elevated idle-handle shadows or extra draw wrappers.

---

# Antigravity Pulse v1.2.3 Release Notes

**Antigravity Pulse v1.2.3** is a major scrolling performance and smoothness release. It eliminates WebView scrolling lag and frame drops across all 8 web workspaces (Antigravity Remote, T3 Code, Google AI Studio, Pulse Browser, GitHub, Vercel, Supabase, and Gemini), unlocking butter-smooth 60Hz and 120Hz scrolling.

It upgrades cleanly in place over v1.2.2 with no uninstall and no data loss.

---

## What's Fixed & Optimized in v1.2.3

### 1. GPU Compositor-Threaded Scrolling
- **Fast-Path Root Scrolling**: Removed `overflow-x: hidden !important;` from root `html, body`, restoring Blink's asynchronous compositor-driven viewport scrolling. Scrolling no longer stalls on the JavaScript main thread.
- **Fast-Path Font Rasterization**: Removed `text-rendering: optimizeLegibility;` on `html, body`. Chromium now caches glyphs without recalculating kerning pairs and ligatures on every frame.
- **Reflow Prevention**: Removed heavy global overrides on `#root, #app, main, [role="main"]` that forced repeated layout reflows during scrolling in Monaco Editor, markdown renderers, and virtualized lists.

### 2. High-Performance Touch & Gesture Handling
- **Non-Blocking Touch Gestures**: Replaced wildcard `touch-action: manipulation` across thousands of buttons and links with `html, body { touch-action: pan-x pan-y pinch-zoom; overscroll-behavior-y: contain; }`, allowing instant fling and pan gestures directly on the GPU thread.
- **Layout-Free Agent Observer**: Replaced `getBoundingClientRect()` loops with non-reflowing `checkVisibility()` and outer dimension metrics in the remote agent observer, and paused all background DOM inspections during active touch, scroll, and wheel gestures.

### 3. Native Android Scroll Physics & 120Hz Display Modes
- **Offscreen Pre-Rasterization**: Enabled `settings.offscreenPreRaster = true` across all WebViews, eliminating blank tile flashing during high-velocity flings.
- **Smooth Fling Deceleration**: Restored Android's natural fling deceleration physics and stretch overscroll via `overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS` with overlay scrollbars and `isNestedScrollingEnabled = false`.
- **High-Refresh-Rate Display**: Configured the Activity window to request the hardware display's maximum refresh rate (90Hz / 120Hz) via `preferredDisplayModeId`.

---

# Antigravity Pulse v1.2.2 Release Notes

**Antigravity Pulse v1.2.2** adds in-app self-updating and unifies the app icon with the desktop
release. It upgrades cleanly in place over v1.2.1 with no uninstall and no data loss.

> **Build correction:** the first v1.2.2 upload was missing `google-services.json`, which caused a
> startup crash. The APK on this release has been rebuilt with the Firebase configuration restored
> and verified launching on a physical device. If you downloaded the earlier APK, reinstall this one.

> **Performance + updater correction:** the daily update check was performing network I/O on the UI
> thread, causing scrolling lag and freezes. It now runs on `Dispatchers.IO`. The updater also falls
> back to a rate-limit-free manifest
> (`app/update-manifest.json`) when GitHub's REST API is unreachable from your network.

## What's New in v1.2.2

- **In-app updates.** Settings now has an Updates section with a manual check, release notes,
  download progress, and one-tap install. The app also checks GitHub Releases once a day in the
  background and posts a notification when a newer version is available.
- **Verified downloads.** The APK is downloaded to the app cache and its SHA-256 is checked against
  the release's `SHA256SUMS.txt` before Android is asked to install it. An unverified file is never
  handed to the installer.
- **Unknown-sources handling.** If install permission is missing, the app links to Android's
  *Install unknown apps* settings so you can grant it and retry.
- **Unified branding.** The launcher icon, adaptive icon, monochrome theme icon, and notification
  glyph now use the same obsidian/warm-peach orbit mark as Antigravity Pulse Desktop.

---

# Antigravity Pulse v1.2.1 Release Notes

**Antigravity Pulse v1.2.1** is a focused touch-responsiveness and performance patch. It removes GPU-heavy offscreen compositing from live WebViews, genuinely suspends inactive RAM-pinned workspaces without sacrificing zero-reload retention, defers expensive snapshot work off the touch path, and makes the movable floating handle respond instantly to taps and 1:1 drags.

It builds directly on v1.2.0 and **upgrades cleanly in place over v1.2.0** (and v1.1.0/v1.0.0) with no uninstall and no data loss.

---

## What's Fixed in v1.2.1

### 1. Instant Touch & 60/120Hz Input Responsiveness
- **True Per-Instance Suspension**: Inactive RAM-pinned workspaces now switch to `View.GONE` and call `onPause()` on that WebView instance only — never the global `pauseTimers()` — so background tabs stop competing for GPU and UI thread time while every session stays loaded in memory.
- **Staggered Heartbeats**: Keep-alive heartbeats are offset by 160 ms per workspace, preventing synchronized wake-ups across all pinned tools.
- **Full Retention Preserved**: The 1+ hour zero-reload multi-workspace experience from v1.2.0 is unchanged — only the wasted background work is gone.

### 2. Direct Hardware WebView Compositing
- **No More Offscreen Copies**: Removed `graphicsLayer { alpha = ... }` wrappers around live `AndroidView(WebView)` workspace slots and multi-tab Pulse Browser containers, so Chromium renders straight to the display without offscreen hardware FBO copies or touch-dispatch lag.
- **Guarded Offline Overlays**: Inactive offline continuity overlays no longer repaint behind hidden workspaces.
- **Explicit Hardware Acceleration**: `android:hardwareAccelerated="true"` is now declared in the manifest.

### 3. No More Recomposition & Main-Thread Stalls
- **No Redundant JS Injection**: Lag Booster scripts are no longer re-injected on every `AndroidView(update)` pass.
- **Deferred Snapshots**: Routine offline snapshots are now capped at 720 px, delayed 1200 ms, and throttled to once per 30 seconds, keeping rapid SPA navigations off the touch path.
- **Reflow-Free DOM Scanning**: Remote DOM notification detection uses `textContent` instead of layout-reflowing `innerText`, with a touch/scroll cooldown.
- **Less Serialization**: Duplicate `onPause` state serialization was removed.

### 4. Crisp Movable Floating Handle
- **Unified Tap + Drag Gesture**: Taps on the side edge handle now open the panel immediately, while drags track your finger 1:1 without the handle getting stuck.

---

## Direct APK Installation

You do **not** need Android Studio or a computer to install Antigravity Pulse.

### Option 1: Direct Download
1. Download **`Antigravity-Pulse-v1.2.1.apk`** from the **Assets** section below.
2. Open the downloaded file from your browser downloads or file manager.
3. If prompted by Android, tap **Settings** and enable **Allow from this source**, then tap **Install**.
4. Launch **Antigravity Pulse** from your home screen.

### Option 2: Automatic Updates via Obtainium
Track and auto-update directly from GitHub Releases using [Obtainium](https://github.com/ImranR98/Obtainium):
1. In Obtainium, tap **Add App**.
2. Enter repository URL: `https://github.com/IceCool30/Antigravity-Pulse-`
3. Tap **Add** → **Install**.

---

## Upgrade Notes
- Upgrading over v1.2.0 keeps all saved hosts, cloud profiles, appearance settings, and pinned workspace state intact.
- No uninstall is required: v1.2.1 is signed with the same certificate as v1.0.0, v1.1.0, and v1.2.0.
- For the best long-session responsiveness, keep **Battery Unrestricted** enabled under **Settings → Memory & Keep-Alive**.

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
