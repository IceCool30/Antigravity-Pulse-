package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.runtime.key
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.AiStudioPinnedWebViewManager
import com.example.data.remote.AiStudioWebSection
import com.example.data.remote.RetainedWebViewManager
import com.example.data.remote.WebViewOfflineCacheHelper
import com.example.ui.components.OfflineContinuityOverlay
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppearanceSettings

/**
 * Full-screen RAM-pinned Google AI Studio Web & Build workspace (`aistudio.google.com`).
 *
 * Mirrors [RetainedWebViewBridgeScreen] for a clean, distraction-free fullscreen experience:
 * - When fullscreen bars are hidden (`isFullscreenBarsHidden == true`), the screen is 100% pure
 *   full-bleed AI Studio with zero distracting bars—while `Prompts`, `Build Apps`, `Library`,
 *   `Lag Booster`, `Reload`, and `Open in Browser` stay one tap away inside the translucent
 *   movable floating handle.
 * - When bars are shown (`isFullscreenBarsHidden == false`), a sleek collapsible toolbar appears
 *   above the WebView with instant quick-jump pills and Lag Booster controls.
 */
@Composable
fun AiStudioWebScreen(
    domLagBoosterEnabled: Boolean,
    isFullscreenBarsHidden: Boolean,
    isWorkspaceActive: Boolean = true,
    onToggleDomBooster: (Boolean) -> Unit,
    onStatusEvent: (String) -> Unit
) {
    val context = LocalContext.current
    val activeSection by AiStudioPinnedWebViewManager.activeSectionState.collectAsStateWithLifecycle()
    val loadProgress by AiStudioPinnedWebViewManager.pageLoadProgress.collectAsStateWithLifecycle()
    val continuity by AiStudioPinnedWebViewManager.continuityState.collectAsStateWithLifecycle()
    val rendererGeneration by AiStudioPinnedWebViewManager.rendererGeneration.collectAsStateWithLifecycle()

    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val elevated = appearance.elevatedSurfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor

    // Keep Lag Booster synced on the pinned AI Studio WebView without ever detaching it
    LaunchedEffect(domLagBoosterEnabled) {
        if (AiStudioPinnedWebViewManager.isPinnedInRam()) {
            AiStudioPinnedWebViewManager.applyLagBooster(domLagBoosterEnabled)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .testTag("ai_studio_web_screen")
    ) {
        // Collapsible Quick-Jump & Lag Booster Toolbar (completely hidden in Fullscreen Mode)
        AnimatedVisibility(visible = !isFullscreenBarsHidden) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .testTag("ai_studio_collapsible_toolbar")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Prompts / Build Apps / Library + Reload & External Browser
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AiStudioWebSection.entries.forEach { section ->
                                val active = activeSection == section
                                Text(
                                    text = section.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (active) accent else fg,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (active) accent.copy(alpha = 0.16f) else elevated)
                                        .border(
                                            1.dp,
                                            if (active) accent.copy(alpha = 0.6f) else border,
                                            RoundedCornerShape(12.dp)
                                        )
                                        .clickable {
                                            AiStudioPinnedWebViewManager.navigateToSection(section)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                        .testTag("ai_studio_web_section_${section.name.lowercase()}")
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = {
                                    AiStudioPinnedWebViewManager.reloadCurrentPage()
                                    onStatusEvent("Reloading AI Studio...")
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("ai_studio_web_reload_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reload AI Studio",
                                    tint = fg,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            IconButton(
                                onClick = {
                                    try {
                                        val url = AiStudioPinnedWebViewManager.currentUrl()
                                        if (RetainedWebViewManager.isSafeHttpUrl(url)) {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                                            )
                                        } else {
                                            onStatusEvent("Blocked unsafe external URL.")
                                        }
                                    } catch (_: Exception) {
                                        onStatusEvent("No external browser available.")
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("ai_studio_open_external_browser_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open AI Studio in External Browser",
                                    tint = accent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }

                    // Row 2: Lag Booster Toggle Chip + Pinned in RAM indicator (matching Antigravity Remote)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FilterChip(
                            selected = domLagBoosterEnabled,
                            onClick = { onToggleDomBooster(!domLagBoosterEnabled) },
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Bolt,
                                        contentDescription = "DOM Lag Booster",
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (domLagBoosterEnabled) "Lag Booster: ON"
                                        else "Lag Booster: OFF"
                                    )
                                }
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = appearance.successColor.copy(alpha = 0.16f),
                                selectedLabelColor = appearance.successColor
                            ),
                            modifier = Modifier.testTag("ai_studio_lag_booster_toggle")
                        )

                        Text(
                            text = "Pinned in RAM",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = secondaryFg,
                            modifier = Modifier.testTag("ai_studio_pinned_ram_badge")
                        )
                    }
                }
                HorizontalDivider(color = border, thickness = 1.dp)
            }
        }

        if (loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                color = accent,
                trackColor = border.copy(alpha = 0.35f),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.5.dp)
                    .testTag("ai_studio_load_progress")
            )
        }

        // Full-bleed RAM-Pinned Google AI Studio WebView with Offline Snapshot Continuity
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            key(rendererGeneration) {
                AndroidView(
                    factory = { ctx ->
                        AiStudioPinnedWebViewManager.obtainPinnedWebView(
                            context = ctx,
                            initialSection = activeSection,
                            domLagBooster = domLagBoosterEnabled,
                            onPageEvent = onStatusEvent
                        ).also { wv ->
                            WebViewOfflineCacheHelper.applyWindowAttachedVisibility(wv, isWorkspaceActive)
                        }
                    },
                    update = { wv ->
                        // Keep WebView permanently attached to window; toggle visibility/focus without reloading
                        WebViewOfflineCacheHelper.applyWindowAttachedVisibility(wv, isWorkspaceActive)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black)
                        .testTag("ai_studio_pinned_webview_container")
                )
            }

            OfflineContinuityOverlay(
                continuity = continuity,
                workspaceLabel = "Google AI Studio",
                onRetryOrReloadLatest = {
                    AiStudioPinnedWebViewManager.refreshWithSnapshotShield()
                    onStatusEvent("Refreshing AI Studio to latest state...")
                },
                onDismissRestoredBanner = {
                    AiStudioPinnedWebViewManager.dismissRestoredBanner()
                }
            )
        }
    }
}
