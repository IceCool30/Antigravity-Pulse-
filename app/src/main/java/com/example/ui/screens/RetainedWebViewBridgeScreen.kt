package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    onJsBridgeEvent: (String) -> Unit
) {
    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val border = appearance.borderColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor

    var urlInput by rememberSaveable(telemetry.activeEndpoint) {
        mutableStateOf(telemetry.activeEndpoint)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
    ) {
        // Collapsible URL & DOM Lag Booster Toolbar (Automatically hidden in Fullscreen Mode!)
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
                        label = { Text("Pinned WebView Bridge URL / Tunnel") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("webview_url_input")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = {
                            RetainedWebViewManager.reloadOrForceLoadUrl(
                                url = urlInput.trim(),
                                domLagBooster = telemetry.domLagBoosterEnabled
                            )
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent)
                            .testTag("webview_load_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Load or Refresh Bridge URL",
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
                                    if (telemetry.domLagBoosterEnabled) "DOM Lag Booster: ON"
                                    else "DOM Lag Booster: OFF"
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
                        text = "Singleton RAM Lock Active",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryFg
                    )
                }
            }
        }

        // Memory-Pinned WebView Instance (expands edge-to-edge when bars are hidden)
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
                .padding(if (isFullscreenBarsHidden) 0.dp else 8.dp)
                .clip(RoundedCornerShape(if (isFullscreenBarsHidden) 0.dp else 12.dp))
                .border(
                    width = if (isFullscreenBarsHidden) 0.dp else 1.dp,
                    color = if (isFullscreenBarsHidden) Color.Transparent else border,
                    shape = RoundedCornerShape(if (isFullscreenBarsHidden) 0.dp else 12.dp)
                )
                .testTag("retained_webview_container")
        )
    }
}
