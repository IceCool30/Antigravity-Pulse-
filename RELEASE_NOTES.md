# Antigravity Pulse v1.2.2 Release Notes

**Antigravity Pulse v1.2.2** adds in-app self-updating and unifies the app icon with the desktop
release. It upgrades cleanly in place over v1.2.1 with no uninstall and no data loss.

> **Build correction:** the first v1.2.2 upload was missing `google-services.json`, which caused a
> startup crash. The APK on this release has been rebuilt with the Firebase configuration restored
> and verified launching on a physical device. If you downloaded the earlier APK, reinstall this one.

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
