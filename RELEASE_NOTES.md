# Antigravity Pulse v1.2.0 Release Notes

**Antigravity Pulse v1.2.0** is the biggest leap yet: a **1+ hour, zero-reload multi-workspace memory engine**, the new **T3 Code Web** workspace (`https://app.t3.codes/`), an all-in-one **multi-tab Pulse Browser** alongside RAM-pinned GitHub, Vercel, Supabase, and Gemini workspaces, plus safe GPU-only UI animations and a hardened Offline Visual Continuity & security layer.

---

## What's New in v1.2.0

### 1. 1+ Hour Multi-Workspace RAM Keep-Alive (Zero Reloads)
- **Permanent Window-Attached Lazy Workspace Stack**: Every tool stays alive across tab switches — Antigravity Remote, T3 Code, Google AI Studio, Gemini, GitHub, Vercel, Supabase, and the multi-tab Pulse Browser. No evictions, no reloads, no lost scroll positions.
- **Non-Detaching Settings Overlay**: Opening Settings no longer tears down or evicts the active WebView workspace.
- **`RENDERER_PRIORITY_IMPORTANT` Renderers**: All pinned workspaces request elevated renderer priority to resist OS process reclamation during long sessions.
- **Play-Compliant Silent `dataSync` Foreground Keep-Alive Service**: A quiet notification-only keep-alive service with a lightweight 45-second JS timer & WebSocket heartbeat, plus Android 15+ safe FGS timeout handling.
- **`onTrimMemory` State & Scroll Backups**: When the system is under pressure, workspace state and scroll positions are backed up and restored cleanly.
- **New Memory & Keep-Alive Settings Section**: Live RAM badge, per-workspace release chips, and a one-tap **Battery Unrestricted** shortcut so Android doesn't throttle your sessions.

### 2. T3 Code Web Workspace (`https://app.t3.codes/`)
- **Full RAM-Pinned T3 Code**: Run your T3 Code sessions in a native, fullscreen Android workspace with zero reloads.
- **Multi-Environment Switcher**: Jump between **T3 Cloud** and custom **self-hosted / Tailscale HTTPS** servers.
- **QR Pairing**: Scan a pairing QR and the one-time `#token=` handshake fragment is preserved safely through the live connection. Also supports `t3://` deep links.
- **OAuth Popups, Desktop View & Offline Continuity**: Sign in seamlessly, request the desktop layout, and keep your rendered view when the network drops.

### 3. All-in-One Developer Workspaces & Multi-Tab Pulse Browser
- **RAM-Pinned GitHub, Vercel, Supabase & Gemini**: First-class workspaces with quick-jump shortcuts, per-workspace **Desktop View**, and instant zero-reload tab switching.
- **Multi-Tab Pulse Browser**: A new in-app browser with persistent tabs, quick links (including T3 Code), and the same zero-reload tab switching.

### 4. Safe, GPU-Only UI Animations
- **Subtle, Finite Motion (≤ 190ms)**: Refined transitions across workspace switching, the floating control panel, the Settings overlay, chevrons, and status banners.
- **Zero WebView Impact**: Animations are compositor/GPU-only — no layout thrash, no reloads, no dropped frames on your live session.
- **New Smooth UI Animations Toggle**: Turn motion off entirely from Settings if you prefer an instant, static UI.

### 5. Offline Visual Continuity & Security Hardening
- **Cached View Retention**: Your last rendered frame stays on screen during network drops instead of a white flash, then reconnects automatically when the network returns.
- **URL Credential Redaction**: Sensitive auth query parameters and tokens are stripped from backups and lock-screen/notification text.
- **Secure Media Origin Checks**: Camera and microphone access is limited to secure origins (HTTPS and localhost).
- **Download Filename Sanitization**: Strips path traversal (`../../`) and illegal characters from suggested download names.
- **Light Mode Adaptive Contrast**: Improved readability across appearance presets in light mode.

---

## Direct APK Installation

You do **not** need Android Studio or a computer to install Antigravity Pulse.

### Option 1: Direct Download
1. Download **`Antigravity-Pulse-v1.2.0.apk`** from the **Assets** section below.
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
- Upgrading over v1.1.0 keeps your saved hosts, cloud profiles, and appearance settings intact.
- For the best 1+ hour keep-alive experience, set Antigravity Pulse to **Battery Unrestricted** from **Settings → Memory & Keep-Alive**.
- The keep-alive service uses a silent notification by design (Android foreground-service policy); it does not wake your screen or play sounds.

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
