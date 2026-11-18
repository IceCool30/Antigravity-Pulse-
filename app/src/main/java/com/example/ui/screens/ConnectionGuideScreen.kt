package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.remote.RetainedWebViewManager
import com.example.ui.theme.AppearanceSettings
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily

data class GuideStepInfo(
    val stepNumber: Int,
    val title: String,
    val subtitle: String,
    val bullets: List<String>,
    val codeHint: String? = null
)

val CompleteAntigravityGuideSteps = listOf(
    GuideStepInfo(
        stepNumber = 1,
        title = "Copy Your Antigravity Remote Link",
        subtitle = "Get the URL from your desktop Antigravity IDE or remote browser session",
        bullets = listOf(
            "Option A (Browser Remote Link): In Google Antigravity on your PC, open Remote Control and copy the full https:// or http:// session URL (or scan the QR code on your phone and copy the link).",
            "Option B (Same Wi-Fi / LAN): If your phone and computer are on the same Wi-Fi, use your PC's local IP address and Antigravity port (e.g., http://192.168.1.50:8443).",
            "Option C (Tailscale / Cloudflare Tunnel): Use your private Tailscale MagicDNS URL (e.g., https://workstation.tailnet.ts.net:8443) to connect from anywhere on mobile data."
        ),
        codeHint = "Example URL: https://antigravity.local.tailnet.ts.net:8443"
    ),
    GuideStepInfo(
        stepNumber = 2,
        title = "Paste & Connect Your Host in Seconds",
        subtitle = "Connect via the interactive box below, the top Session Pill, or the Web Bridge tab",
        bullets = listOf(
            "Direct Web Bridge: Open the 'Web Bridge' tab, paste your Antigravity Remote URL at the top, and tap Refresh (↻). Your exact remote browser interface will load inside a hardware-accelerated, memory-pinned container.",
            "Native Host Profile: Tap the session dropdown pill [ ● ... ▾ ] at the top-left of the screen and choose 'Configure Remote Host' to save your URL and optional pairing token.",
            "Or use the Quick-Connect form directly below to connect in one tap right now!"
        )
    ),
    GuideStepInfo(
        stepNumber = 3,
        title = "Zero-Reload Pinned RAM & DOM Lag Booster",
        subtitle = "Switch apps or minimize your phone without ever reloading your workspace",
        bullets = listOf(
            "Singleton RAM Lock: Unlike mobile browsers that evict tabs and force slow reloads when you minimize them, Antigravity Pulse pins your active WebView and WebSocket in Android application memory.",
            "DOM Lag Booster: Keep 'DOM Lag Booster: ON' enabled in the Web Bridge tab to strip heavy CSS transitions and offscreen layout reflows so scrolling long agent conversations is instant.",
            "0ms Local Room Cache: Agent sessions, file diffs, and terminal logs are cached locally in on-device SQLite so they open in 0ms even offline."
        )
    ),
    GuideStepInfo(
        stepNumber = 4,
        title = "Distraction-Free Fullscreen Mode (⛶)",
        subtitle = "Hide both the top and bottom bars for a 100% fullscreen remote interface",
        bullets = listOf(
            "Enter Fullscreen: Tap the Fullscreen icon (⛶) in the top-right bar (or toggle 'Distraction-Free Fullscreen' inside ⚙️ Settings). Both the top header and bottom navigation tabs will slide away completely.",
            "Summon Controls Anytime: While in Fullscreen Mode, tap the subtle floating handle on the left edge of your screen to switch tabs, open Appearance Settings, or tap 'Show Bars' to pin the bars back."
        )
    ),
    GuideStepInfo(
        stepNumber = 5,
        title = "Add Context (+), Live Voice & Themes",
        subtitle = "Attach Files/Folders/Mentions/Browser/Drive, talk via gemini-3.8-live, and customize themes",
        bullets = listOf(
            "Add Context (+ Button): Tap '+' next to the prompt box to attach Files (or phone screenshots), Folder trees, @Mentions, slash Actions, live Chrome MCP Browser state, or Google Drive specs.",
            "Gemini Live Voice (🎙️ Icon): Tap the waveform icon next to the prompt box to start a real-time voice conversation with gemini-3.8-live and dispatch commands to Antigravity hands-free.",
            "12 Official Theme Presets: Tap ⚙️ in the top bar to switch between Default Dark, Legacy Dark, Catppuccin, Dracula, Everforest, Gruvbox Material, Monokai, Nord, One Dark Pro, Tokyo Night, Solarized Dark, and Vesper."
        )
    )
)

/**
 * Full-screen interactive Step-by-Step Connection & Usage Guide shown automatically
 * when the user opens the app (and launchable anytime from Settings or the top bar).
 */
@Composable
fun ConnectionGuideScreen(
    settings: AppearanceSettings,
    activeEndpointUrl: String,
    onUpdateSettings: ((AppearanceSettings) -> AppearanceSettings) -> Unit,
    onQuickConnectWebBridge: (hostName: String, url: String, token: String) -> Unit,
    onQuickConnectNativeSessions: (hostName: String, url: String, token: String) -> Unit,
    onOpenAppearanceSettings: () -> Unit,
    onCloseGuide: () -> Unit
) {
    BackHandler(onBack = onCloseGuide)

    val bg = settings.backgroundColor
    val surface = settings.surfaceColor
    val elevated = settings.elevatedSurfaceColor
    val border = settings.borderColor
    val fg = settings.foregroundColor
    val secondaryFg = settings.secondaryTextColor
    val accent = settings.accentColor

    var hostLabel by rememberSaveable { mutableStateOf("My Antigravity Remote") }
    var hostUrl by rememberSaveable(activeEndpointUrl) { mutableStateOf(activeEndpointUrl) }
    var hostToken by rememberSaveable { mutableStateOf("") }
    var quickConnectFeedback by rememberSaveable { mutableStateOf<String?>(null) }
    var showQrScanner by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .testTag("startup_connection_guide_screen")
    ) {
        // Top Header Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accent.copy(alpha = 0.16f))
                        .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(10.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.RocketLaunch,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "How to Connect & Use",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = fg
                    )
                    Text(
                        text = "Complete Step-by-Step Antigravity Phone Guide",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryFg
                    )
                }
            }

            IconButton(
                onClick = onCloseGuide,
                modifier = Modifier.testTag("close_connection_guide_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Close Guide",
                    tint = secondaryFg
                )
            }
        }

        HorizontalDivider(color = border, thickness = 1.dp)

        // Scrollable Step-by-Step Content + Interactive Quick-Connect Card
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Welcome Summary Banner
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(elevated)
                        .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = EmeraldOnline,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Welcome to Antigravity Pulse",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = fg
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Follow the 5 quick steps below to link your phone to your Google Antigravity session, eliminate browser lag and tab reloads, and run in 100% Fullscreen Mode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryFg
                    )
                }
            }

            // Step 1 Card
            item {
                GuideStepCard(
                    step = CompleteAntigravityGuideSteps[0],
                    icon = Icons.Default.QrCodeScanner,
                    surface = surface,
                    elevated = elevated,
                    border = border,
                    fg = fg,
                    secondaryFg = secondaryFg,
                    accent = accent
                )
            }

            // Step 2 Card + Embedded Interactive Quick-Connect Box!
            item {
                GuideStepCard(
                    step = CompleteAntigravityGuideSteps[1],
                    icon = Icons.Default.Link,
                    surface = surface,
                    elevated = elevated,
                    border = border,
                    fg = fg,
                    secondaryFg = secondaryFg,
                    accent = accent
                ) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(bg)
                            .border(1.dp, accent.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Quick Connect",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                            OutlinedButton(
                                onClick = { showQrScanner = true },
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.testTag("guide_scan_qr_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan Desktop QR Code",
                                    tint = accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan QR Code", color = accent, fontWeight = FontWeight.Bold)
                            }
                        }
                        OutlinedTextField(
                            value = hostLabel,
                            onValueChange = { hostLabel = it },
                            label = { Text("Workspace / Host Label") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("guide_quick_host_label")
                        )
                        OutlinedTextField(
                            value = hostUrl,
                            onValueChange = { hostUrl = it },
                            label = { Text("Antigravity Remote URL (https://, http://, or wss://)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("guide_quick_host_url")
                        )
                        OutlinedTextField(
                            value = hostToken,
                            onValueChange = { hostToken = it },
                            label = { Text("Pairing / Auth Token (Optional)") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("guide_quick_host_token")
                        )

                        if (quickConnectFeedback != null) {
                            Text(
                                text = quickConnectFeedback.orEmpty(),
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldOnline
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    val cleanUrl = hostUrl.trim()
                                    if (cleanUrl.isBlank()) {
                                        quickConnectFeedback = "Please paste your Antigravity remote URL above first."
                                        return@Button
                                    }
                                    val cleanName = hostLabel.trim().ifBlank { "Antigravity Remote" }
                                    RetainedWebViewManager.reloadOrForceLoadUrl(cleanUrl, true)
                                    onQuickConnectWebBridge(cleanName, cleanUrl, hostToken.trim())
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = accent,
                                    contentColor = Color(0xFF070708)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("guide_connect_web_bridge_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open in Web Bridge", fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = {
                                    val cleanUrl = hostUrl.trim()
                                    if (cleanUrl.isBlank()) {
                                        quickConnectFeedback = "Please paste your Antigravity remote URL above first."
                                        return@OutlinedButton
                                    }
                                    val cleanName = hostLabel.trim().ifBlank { "Antigravity Remote" }
                                    quickConnectFeedback = "Saved '$cleanName'! Opening Native Sessions..."
                                    onQuickConnectNativeSessions(cleanName, cleanUrl, hostToken.trim())
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("guide_connect_native_sessions_button")
                            ) {
                                Text("Save & Open Sessions", color = fg)
                            }
                        }
                    }
                }
            }

            // Step 3 Card
            item {
                GuideStepCard(
                    step = CompleteAntigravityGuideSteps[2],
                    icon = Icons.Default.Bolt,
                    surface = surface,
                    elevated = elevated,
                    border = border,
                    fg = fg,
                    secondaryFg = secondaryFg,
                    accent = accent
                )
            }

            // Step 4 Card + Quick Fullscreen Toggle
            item {
                GuideStepCard(
                    step = CompleteAntigravityGuideSteps[3],
                    icon = Icons.Default.Fullscreen,
                    surface = surface,
                    elevated = elevated,
                    border = border,
                    fg = fg,
                    secondaryFg = secondaryFg,
                    accent = accent
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            onUpdateSettings { it.copy(isFullscreenBarsHidden = true) }
                            onCloseGuide()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("guide_try_fullscreen_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Enter Fullscreen Mode Now (Hide Top & Bottom Bars)", color = accent)
                    }
                }
            }

            // Step 5 Card + Quick Appearance Button
            item {
                GuideStepCard(
                    step = CompleteAntigravityGuideSteps[4],
                    icon = Icons.Default.Palette,
                    surface = surface,
                    elevated = elevated,
                    border = border,
                    fg = fg,
                    secondaryFg = secondaryFg,
                    accent = accent
                ) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = onOpenAppearanceSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("guide_open_appearance_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Customize Theme Presets & Colors (⚙️)", color = fg)
                    }
                }
            }
        }

        HorizontalDivider(color = border, thickness = 1.dp)

        // Bottom Bar: Startup Toggle + "Start Using Antigravity Pulse" Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show guide automatically on app launch",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = fg
                    )
                    Text(
                        text = "You can also view this guide anytime inside ⚙️ Settings",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryFg
                    )
                }
                Switch(
                    checked = settings.showStartupGuideOnLaunch,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(showStartupGuideOnLaunch = enabled) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = bg,
                        checkedTrackColor = accent
                    ),
                    modifier = Modifier.testTag("startup_guide_toggle_switch")
                )
            }

            Button(
                onClick = onCloseGuide,
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF070708)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("finish_connection_guide_button")
            ) {
                Text(
                    text = "Got It — Start Using Antigravity Pulse",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (showQrScanner) {
        QrPairingScannerDialog(
            onPairingScanned = { payload ->
                hostLabel = payload.hostName
                hostUrl = payload.endpointUrl
                hostToken = payload.authToken
                quickConnectFeedback = "Scanned QR: ${payload.hostName} (${payload.endpointUrl})"
                showQrScanner = false
            },
            onDismiss = { showQrScanner = false }
        )
    }
}

@Composable
private fun GuideStepCard(
    step: GuideStepInfo,
    icon: ImageVector,
    surface: Color,
    elevated: Color,
    border: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    extraContent: (@Composable () -> Unit)? = null
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(surface)
            .border(1.dp, border, RoundedCornerShape(18.dp))
            .padding(16.dp)
            .testTag("guide_step_card_${step.stepNumber}")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(accent),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${step.stepNumber}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF070708)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = step.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = fg
                )
                Text(
                    text = step.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = secondaryFg
                )
            }
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        step.bullets.forEach { bullet ->
            Row(
                modifier = Modifier.padding(vertical = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    text = "•",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = bullet,
                    style = MaterialTheme.typography.bodyMedium,
                    color = fg,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        if (step.codeHint != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(elevated)
                    .border(1.dp, border, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = step.codeHint,
                    fontFamily = JetBrainsMonoFontFamily,
                    fontSize = 12.sp,
                    color = accent
                )
            }
        }

        extraContent?.invoke()
    }
}

/**
 * Embedded "How to Connect & Use Guide" section directly inside `AppearanceSettingsSheet` (Settings),
 * allowing the user to read every step inside Settings, toggle startup launch behavior, or open
 * the full interactive Connection Wizard.
 */
@Composable
fun SettingsEmbeddedGuideSection(
    settings: AppearanceSettings,
    onUpdateSettings: ((AppearanceSettings) -> AppearanceSettings) -> Unit,
    onOpenFullInteractiveGuide: () -> Unit
) {
    val bg = settings.backgroundColor
    val surface = settings.surfaceColor
    val elevated = settings.elevatedSurfaceColor
    val border = settings.borderColor
    val fg = settings.foregroundColor
    val secondaryFg = settings.secondaryTextColor
    val accent = settings.accentColor

    var isStepsExpanded by rememberSaveable { mutableStateOf(true) }

    Column {
        Text(
            text = "Connection & Usage Guide",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(surface)
                .border(1.dp, border, RoundedCornerShape(18.dp))
                .testTag("settings_embedded_guide_section")
        ) {
            // Row 1: Launch Interactive Setup & Connection Guide Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Interactive Connection Wizard",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = fg
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Step-by-step walkthrough with quick URL paste & connect",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryFg
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Button(
                    onClick = onOpenFullInteractiveGuide,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF070708)
                    ),
                    modifier = Modifier.testTag("settings_open_guide_wizard_button")
                ) {
                    Text("Open Guide", fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = border, thickness = 1.dp)

            // Row 2: Toggle "Show Guide Automatically When App Opens"
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Show Guide on App Startup",
                        style = MaterialTheme.typography.titleMedium,
                        color = fg
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Automatically display the step-by-step connection guide when opening the app",
                        style = MaterialTheme.typography.bodyMedium,
                        color = secondaryFg
                    )
                }

                Switch(
                    checked = settings.showStartupGuideOnLaunch,
                    onCheckedChange = { show ->
                        onUpdateSettings { it.copy(showStartupGuideOnLaunch = show) }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = bg,
                        checkedTrackColor = accent
                    ),
                    modifier = Modifier.testTag("settings_startup_guide_switch")
                )
            }

            HorizontalDivider(color = border, thickness = 1.dp)

            // Row 3: Expandable Complete Step-by-Step Guide Directly Inside Settings
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isStepsExpanded = !isStepsExpanded }
                        .padding(vertical = 4.dp)
                        .testTag("settings_toggle_inline_guide_steps"),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Step-by-Step Manual (Steps 1–5)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = accent
                    )
                    Icon(
                        imageVector = if (isStepsExpanded) {
                            Icons.Default.KeyboardArrowUp
                        } else {
                            Icons.Default.KeyboardArrowDown
                        },
                        contentDescription = "Expand or Collapse Guide Steps",
                        tint = secondaryFg
                    )
                }

                AnimatedVisibility(visible = isStepsExpanded) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CompleteAntigravityGuideSteps.forEach { step ->
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(elevated)
                                    .border(1.dp, border, RoundedCornerShape(12.dp))
                                    .padding(12.dp)
                            ) {
                                Text(
                                    text = "Step ${step.stepNumber}: ${step.title}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = accent
                                )
                                Text(
                                    text = step.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryFg,
                                    modifier = Modifier.padding(bottom = 6.dp)
                                )
                                step.bullets.forEach { bullet ->
                                    Text(
                                        text = "• $bullet",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = fg,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
