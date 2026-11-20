package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.BridgeTelemetry
import com.example.data.remote.RetainedWebViewManager
import com.example.ui.components.OfflineContinuityOverlay
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.frostedGlass

private val AiStudioSkyAccent = Color(0xFF7DD3FC)

@Composable
fun RetainedWebViewBridgeScreen(
    telemetry: BridgeTelemetry,
    savedFallbackEndpoint: String = "",
    isFullscreenBarsHidden: Boolean,
    onToggleDomBooster: (Boolean) -> Unit,
    onSaveAndConnectUrl: (String) -> Unit,
    onOpenHostSwitcher: () -> Unit = {},
    onOpenQrScanner: () -> Unit,
    onOpenAiStudio: () -> Unit = {},
    onJsBridgeEvent: (String) -> Unit
) {
    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor
    val loadProgress by RetainedWebViewManager.pageLoadProgress.collectAsStateWithLifecycle()
    val hasActiveSession by RetainedWebViewManager.hasActiveSessionState.collectAsStateWithLifecycle()
    val continuity by RetainedWebViewManager.continuityState.collectAsStateWithLifecycle()

    val effectiveEndpoint = telemetry.activeEndpoint.trim()
        .ifBlank { savedFallbackEndpoint.trim() }

    var urlInput by rememberSaveable(effectiveEndpoint) {
        mutableStateOf(effectiveEndpoint)
    }

    // As soon as the saved remote endpoint is restored from Room or prefs, load it if idle
    // without ever reloading an already-live session.
    LaunchedEffect(effectiveEndpoint, telemetry.domLagBoosterEnabled) {
        if (RetainedWebViewManager.isSafeHttpUrl(effectiveEndpoint)) {
            RetainedWebViewManager.ensureUrlLoadedIfIdle(
                url = effectiveEndpoint,
                domLagBooster = telemetry.domLagBoosterEnabled
            )
        }
    }

    val showNativeWelcome = !hasActiveSession && !RetainedWebViewManager.isSafeHttpUrl(effectiveEndpoint)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Collapsible URL, QR & Lag Booster Toolbar (Hidden in Fullscreen Mode)
        AnimatedVisibility(visible = !isFullscreenBarsHidden) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("Antigravity Remote URL") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("webview_url_input")
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onOpenQrScanner,
                        modifier = Modifier
                            .size(48.dp)
                            .frostedGlass(
                                shape = RoundedCornerShape(14.dp),
                                baseColor = appearance.elevatedSurfaceColor,
                                tintColor = accent,
                                borderColor = appearance.borderColor,
                                enabled = appearance.isFrostedGlassEnabled,
                                fillAlpha = 0.78f,
                                tintAlpha = 0.08f,
                                borderAlpha = 0.55f
                            )
                            .testTag("webview_scan_qr_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan Antigravity QR Code",
                            tint = accent
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = {
                            val clean = urlInput.trim()
                            RetainedWebViewManager.reloadOrForceLoadUrl(
                                url = clean,
                                domLagBooster = telemetry.domLagBoosterEnabled
                            )
                            if (RetainedWebViewManager.isSafeHttpUrl(clean)) {
                                onSaveAndConnectUrl(clean)
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent)
                            .testTag("webview_load_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Load or Refresh Remote URL",
                            tint = Color(0xFF000000)
                        )
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = telemetry.domLagBoosterEnabled,
                        onClick = { onToggleDomBooster(!telemetry.domLagBoosterEnabled) },
                        label = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Bolt,
                                    contentDescription = "DOM Lag Booster",
                                    modifier = Modifier.size(15.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (telemetry.domLagBoosterEnabled) "Lag Booster: ON"
                                    else "Lag Booster: OFF"
                                )
                            }
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldOnline.copy(alpha = 0.2f),
                            selectedLabelColor = EmeraldOnline
                        ),
                        modifier = Modifier.testTag("dom_lag_booster_chip")
                    )

                    Text(
                        text = "Pinned in RAM",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryFg
                    )
                }
            }
        }

        if (loadProgress in 1..99 && !showNativeWelcome) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                color = accent,
                trackColor = appearance.borderColor.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .testTag("remote_webview_load_progress")
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            // Full-bleed Memory-Pinned WebView Instance
            AndroidView(
                factory = { context ->
                    RetainedWebViewManager.obtainPinnedWebView(
                        context = context,
                        endpointUrl = effectiveEndpoint,
                        domLagBooster = telemetry.domLagBoosterEnabled,
                        onJsBridgeMessage = onJsBridgeEvent
                    )
                },
                update = { _ ->
                    // No-op on recomposition so the WebView never reloads when switching tabs
                },
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("retained_webview_container")
            )

            // Offline visual snapshot cache & frosted reconnect pill (active only on live sessions)
            if (!showNativeWelcome) {
                OfflineContinuityOverlay(
                    continuity = continuity,
                    workspaceLabel = "Antigravity Remote",
                    onRetryOrReloadLatest = {
                        RetainedWebViewManager.refreshWithSnapshotShield()
                        onJsBridgeEvent("Refreshing Antigravity Remote to latest state...")
                    },
                    onDismissRestoredBanner = {
                        RetainedWebViewManager.dismissRestoredBanner()
                    }
                )
            }

            // Native Welcome Guide with direct Antigravity Remote & Google AI Studio launch buttons
            if (showNativeWelcome) {
                NativeWelcomeLandingScreen(
                    onLaunchRemote = onOpenHostSwitcher,
                    onScanQr = onOpenQrScanner,
                    onLaunchAiStudio = onOpenAiStudio
                )
            }
        }
    }
}

private data class WelcomeGuideItem(
    val icon: ImageVector,
    val title: String,
    val detail: String
)

@Composable
private fun NativeWelcomeLandingScreen(
    onLaunchRemote: () -> Unit,
    onScanQr: () -> Unit,
    onLaunchAiStudio: () -> Unit
) {
    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val elevated = appearance.elevatedSurfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor
    val isFrostedGlass = appearance.isFrostedGlassEnabled

    var selectedGuideTab by rememberSaveable { mutableIntStateOf(0) }
    val activeAccent = if (selectedGuideTab == 0) accent else AiStudioSkyAccent

    val remoteGuideItems = listOf(
        WelcomeGuideItem(
            icon = Icons.Default.QrCodeScanner,
            title = "Connect in seconds",
            detail = "Paste your Remote URL or scan a pairing QR code from your workstation."
        ),
        WelcomeGuideItem(
            icon = Icons.Default.TouchApp,
            title = "Floating side handle",
            detail = "Drag the translucent edge pill anywhere to switch tabs, reload, or adjust settings."
        ),
        WelcomeGuideItem(
            icon = Icons.Default.Mic,
            title = "Voice, files & offline cache",
            detail = "Dictate with your mic, attach files via +, and keep your screen visible if signal drops."
        )
    )

    val aiStudioGuideItems = listOf(
        WelcomeGuideItem(
            icon = Icons.Default.AutoAwesome,
            title = "Fullscreen AI Studio",
            detail = "Open aistudio.google.com edge-to-edge without browser bars or tab reloads."
        ),
        WelcomeGuideItem(
            icon = Icons.Default.Dashboard,
            title = "One-tap workspace shortcuts",
            detail = "Tap the side handle anytime for Prompts, Build Apps, and your Drive Library."
        ),
        WelcomeGuideItem(
            icon = Icons.Default.CloudDone,
            title = "RAM-pinned continuity",
            detail = "Switch between Remote and AI Studio freely while keeping your live session intact."
        )
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .drawBehind {
                // Subtle editorial radial aura at the top — adds depth without visual noise
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            activeAccent.copy(alpha = 0.11f),
                            activeAccent.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        center = Offset(size.width * 0.5f, size.height * 0.14f),
                        radius = size.width * 0.78f
                    ),
                    radius = size.width * 0.78f,
                    center = Offset(size.width * 0.5f, size.height * 0.14f)
                )
            }
            .testTag("native_welcome_screen")
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(22.dp))

            // 1. Minimalist Vector Brand Mark & Editorial Header
            WelcomeBrandEmblem(
                accent = activeAccent,
                surface = surface,
                border = border
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Antigravity Pulse",
                style = MaterialTheme.typography.headlineSmall.copy(
                    letterSpacing = (-0.4).sp
                ),
                fontWeight = FontWeight.SemiBold,
                color = fg,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Your mobile workspace for Antigravity Remote and AI Studio.",
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = secondaryFg,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 310.dp)
            )

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Single Uncluttered Guide Card with Segmented Remote / AI Studio Switcher
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp)
                    .frostedGlass(
                        shape = RoundedCornerShape(22.dp),
                        baseColor = surface,
                        tintColor = activeAccent,
                        borderColor = border,
                        enabled = isFrostedGlass,
                        fillAlpha = 0.72f,
                        tintAlpha = 0.04f,
                        borderAlpha = 0.45f
                    )
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Clean 2-way segmented switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(bg.copy(alpha = 0.65f))
                        .border(1.dp, border.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    WelcomeSegmentTab(
                        label = "Antigravity Remote",
                        selected = selectedGuideTab == 0,
                        activeColor = accent,
                        fg = fg,
                        secondaryFg = secondaryFg,
                        elevated = elevated,
                        onClick = { selectedGuideTab = 0 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("welcome_guide_tab_remote")
                    )
                    WelcomeSegmentTab(
                        label = "AI Studio",
                        selected = selectedGuideTab == 1,
                        activeColor = AiStudioSkyAccent,
                        fg = fg,
                        secondaryFg = secondaryFg,
                        elevated = elevated,
                        onClick = { selectedGuideTab = 1 },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("welcome_guide_tab_ai_studio")
                    )
                }

                // Smoothly cross-fading 3-item scannable guide
                AnimatedContent(
                    targetState = selectedGuideTab,
                    transitionSpec = {
                        fadeIn(tween(180)) togetherWith fadeOut(tween(140))
                    },
                    label = "welcome_guide_content"
                ) { tabIndex ->
                    val items = if (tabIndex == 0) remoteGuideItems else aiStudioGuideItems
                    val itemTint = if (tabIndex == 0) accent else AiStudioSkyAccent
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items.forEach { item ->
                            WelcomeFeatureRow(
                                icon = item.icon,
                                iconTint = itemTint,
                                title = item.title,
                                detail = item.detail,
                                fg = fg,
                                secondaryFg = secondaryFg
                            )
                        }
                    }
                }

                HorizontalDivider(color = border.copy(alpha = 0.35f), thickness = 1.dp)

                // Quiet, single-line secondary QR pairing link (no competing button clutter)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onScanQr)
                        .padding(horizontal = 8.dp, vertical = 8.dp)
                        .testTag("welcome_scan_qr_button"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCodeScanner,
                            contentDescription = "Scan pairing QR code",
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Pair instantly with QR code",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Medium,
                            color = fg
                        )
                    }
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = secondaryFg,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // 3. Single, Focused Bottom Launch Dock — Two Clear Primary Actions
        HorizontalDivider(color = border.copy(alpha = 0.35f), thickness = 1.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(bg.copy(alpha = 0.94f))
                .padding(horizontal = 20.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 460.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onLaunchRemote,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF000000)
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .testTag("welcome_bottom_launch_remote_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Launch Remote",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Button(
                    onClick = onLaunchAiStudio,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = surface,
                        contentColor = AiStudioSkyAccent
                    ),
                    shape = RoundedCornerShape(16.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(50.dp)
                        .border(1.dp, AiStudioSkyAccent.copy(alpha = 0.42f), RoundedCornerShape(16.dp))
                        .testTag("welcome_bottom_launch_ai_studio_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = AiStudioSkyAccent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "AI Studio",
                        style = MaterialTheme.typography.labelLarge,
                        color = AiStudioSkyAccent,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun WelcomeBrandEmblem(
    accent: Color,
    surface: Color,
    border: Color
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(surface)
            .border(1.dp, border.copy(alpha = 0.65f), RoundedCornerShape(18.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(28.dp)) {
            val w = size.width
            val h = size.height
            // Crisp Antigravity pulse waveform & orbital arc drawn at full native DPI
            drawCircle(
                color = accent.copy(alpha = 0.22f),
                radius = w * 0.46f,
                style = Stroke(width = 1.6.dp.toPx())
            )
            val pulsePath = Path().apply {
                moveTo(w * 0.14f, h * 0.56f)
                lineTo(w * 0.34f, h * 0.56f)
                lineTo(w * 0.45f, h * 0.24f)
                lineTo(w * 0.57f, h * 0.76f)
                lineTo(w * 0.68f, h * 0.50f)
                lineTo(w * 0.86f, h * 0.50f)
            }
            drawPath(
                path = pulsePath,
                color = accent,
                style = Stroke(
                    width = 2.4.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )
        }
    }
}

@Composable
private fun WelcomeSegmentTab(
    label: String,
    selected: Boolean,
    activeColor: Color,
    fg: Color,
    secondaryFg: Color,
    elevated: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) elevated else Color.Transparent)
            .then(
                if (selected) {
                    Modifier.border(1.dp, activeColor.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) fg else secondaryFg,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun WelcomeFeatureRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    detail: String,
    fg: Color,
    secondaryFg: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(iconTint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = fg
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 18.sp),
                color = secondaryFg
            )
        }
    }
}
