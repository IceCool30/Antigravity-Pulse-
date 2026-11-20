package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.BridgeTelemetry
import com.example.data.remote.RetainedWebViewManager
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.frostedGlass

@Composable
fun RetainedWebViewBridgeScreen(
    telemetry: BridgeTelemetry,
    savedFallbackEndpoint: String = "",
    isFullscreenBarsHidden: Boolean,
    onToggleDomBooster: (Boolean) -> Unit,
    onSaveAndConnectUrl: (String) -> Unit,
    onOpenQrScanner: () -> Unit,
    onJsBridgeEvent: (String) -> Unit
) {
    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor
    val loadProgress by RetainedWebViewManager.pageLoadProgress.collectAsStateWithLifecycle()

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

        if (loadProgress in 1..99) {
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
                .weight(1f)
                .fillMaxWidth()
                .testTag("retained_webview_container")
        )
    }
}
