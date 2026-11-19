package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.remote.BridgeTelemetry
import com.example.data.remote.RetainedWebViewManager
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalAppearanceSettings

@Composable
fun RetainedWebViewBridgeScreen(
    telemetry: BridgeTelemetry,
    isFullscreenBarsHidden: Boolean,
    onToggleDomBooster: (Boolean) -> Unit,
    onSaveAndConnectUrl: (String) -> Unit,
    onOpenQrScanner: () -> Unit,
    onJsBridgeEvent: (String) -> Unit
) {
    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val accent = appearance.accentColor

    var urlInput by rememberSaveable(telemetry.activeEndpoint) {
        mutableStateOf(telemetry.activeEndpoint)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Collapsible URL, QR, Re-Center & DOM Lag Booster Toolbar (Hidden in Fullscreen Mode)
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
                            .clip(RoundedCornerShape(12.dp))
                            .background(appearance.elevatedSurfaceColor)
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
                            tint = Color(0xFF070708)
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

                    OutlinedButton(
                        onClick = {
                            RetainedWebViewManager.injectMobileLayoutFix()
                            onJsBridgeEvent("Re-centered Antigravity Remote mobile layout")
                        },
                        modifier = Modifier.testTag("webview_recenter_layout_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.CenterFocusStrong,
                            contentDescription = "Re-Center Layout",
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Re-Center", color = accent)
                    }
                }
            }
        }

        // Full-bleed Memory-Pinned WebView Instance (0dp horizontal padding to prevent right-side offset)
        AndroidView(
            factory = { context ->
                RetainedWebViewManager.obtainPinnedWebView(
                    context = context,
                    endpointUrl = telemetry.activeEndpoint,
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
