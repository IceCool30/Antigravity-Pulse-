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
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
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
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.DevWebWorkspace
import com.example.data.remote.DevWebWorkspaceRegistry
import com.example.data.remote.RetainedWebViewManager
import com.example.data.remote.WebViewOfflineCacheHelper
import com.example.ui.components.OfflineContinuityOverlay
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppearanceSettings

/**
 * Full-screen lazy RAM-pinned developer web workspace (GitHub, Vercel, or Supabase).
 *
 * Mirrors [AiStudioWebScreen]: pure full-bleed WebView in Fullscreen Mode, and a clean collapsible
 * toolbar with quick-jump shortcuts, Desktop View toggle, Lag Booster, Reload, Open in Browser,
 * and Pinned-in-RAM badge when bars are shown.
 */
@Composable
fun PinnedWebWorkspaceScreen(
    workspace: DevWebWorkspace,
    domLagBoosterEnabled: Boolean,
    isFullscreenBarsHidden: Boolean,
    isWorkspaceActive: Boolean = true,
    onToggleDomBooster: (Boolean) -> Unit,
    onStatusEvent: (String) -> Unit
) {
    val context = LocalContext.current
    val session = remember(workspace) { DevWebWorkspaceRegistry.session(workspace) }

    val activeShortcutId by session.activeShortcutId.collectAsStateWithLifecycle()
    val isDesktopMode by session.isDesktopModeState.collectAsStateWithLifecycle()
    val loadProgress by session.pageLoadProgress.collectAsStateWithLifecycle()
    val continuity by session.continuityState.collectAsStateWithLifecycle()
    val rendererGeneration by session.rendererGeneration.collectAsStateWithLifecycle()

    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val elevated = appearance.elevatedSurfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor

    LaunchedEffect(workspace, domLagBoosterEnabled) {
        if (session.isPinnedInRam()) {
            session.applyLagBooster(domLagBoosterEnabled)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .testTag("${workspace.id}_web_screen")
    ) {
        AnimatedVisibility(visible = !isFullscreenBarsHidden) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .testTag("${workspace.id}_collapsible_toolbar")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Workspace Quick-Jump Pills + Reload & Open External
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            workspace.shortcuts.forEach { shortcut ->
                                val selected = activeShortcutId == shortcut.id
                                FilterChip(
                                    selected = selected,
                                    onClick = { session.navigateToShortcut(shortcut) },
                                    label = {
                                        Text(
                                            text = shortcut.label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = accent.copy(alpha = 0.22f),
                                        selectedLabelColor = accent,
                                        containerColor = elevated,
                                        labelColor = secondaryFg
                                    ),
                                    border = FilterChipDefaults.filterChipBorder(
                                        enabled = true,
                                        selected = selected,
                                        borderColor = border,
                                        selectedBorderColor = accent.copy(alpha = 0.7f)
                                    ),
                                    modifier = Modifier.testTag("${workspace.id}_shortcut_${shortcut.id}")
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    session.reloadCurrentPage()
                                    onStatusEvent("Reloading ${workspace.displayName}...")
                                },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("${workspace.id}_reload_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reload ${workspace.displayName}",
                                    tint = secondaryFg,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    try {
                                        val url = session.currentUrl()
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
                                    .size(40.dp)
                                    .testTag("${workspace.id}_open_external_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open ${workspace.displayName} in Browser",
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    // Row 2: Desktop View Toggle + Lag Booster + Pinned in RAM Badge
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isDesktopMode) accent.copy(alpha = 0.16f) else elevated
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (isDesktopMode) accent.copy(alpha = 0.55f) else border,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable {
                                    val next = !isDesktopMode
                                    session.setDesktopMode(next)
                                    onStatusEvent(
                                        if (next) "${workspace.displayName}: Desktop View enabled"
                                        else "${workspace.displayName}: Mobile View enabled"
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("${workspace.id}_desktop_mode_toggle"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (isDesktopMode) Icons.Default.DesktopWindows else Icons.Default.Smartphone,
                                contentDescription = "Toggle Desktop View",
                                tint = if (isDesktopMode) accent else secondaryFg,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (isDesktopMode) "Desktop View: ON" else "Desktop View",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDesktopMode) accent else secondaryFg
                            )
                        }

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (domLagBoosterEnabled) appearance.successColor.copy(alpha = 0.14f) else elevated
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (domLagBoosterEnabled) appearance.successColor.copy(alpha = 0.55f) else border,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onToggleDomBooster(!domLagBoosterEnabled) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("${workspace.id}_lag_booster_toggle"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Lag Booster",
                                tint = if (domLagBoosterEnabled) appearance.successColor else secondaryFg,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (domLagBoosterEnabled) "Lag Booster: ON" else "Lag Booster: OFF",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (domLagBoosterEnabled) appearance.successColor else secondaryFg
                            )
                        }

                        Text(
                            text = "Pinned in RAM",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = appearance.successColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(appearance.successColor.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("${workspace.id}_pinned_ram_badge")
                        )
                    }
                }
                HorizontalDivider(color = border, thickness = 1.dp)
            }
        }

        if (loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = accent,
                trackColor = Color.Transparent
            )
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(bg)
                .testTag("${workspace.id}_pinned_webview_container")
        ) {
            key(workspace, rendererGeneration) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        session.obtainPinnedWebView(
                            context = ctx,
                            domLagBooster = domLagBoosterEnabled,
                            onPageEvent = onStatusEvent
                        ).also { wv ->
                            WebViewOfflineCacheHelper.applyWindowAttachedVisibility(wv, isWorkspaceActive)
                        }
                    },
                    update = { wv ->
                        WebViewOfflineCacheHelper.applyWindowAttachedVisibility(wv, isWorkspaceActive)
                        session.applyLagBooster(domLagBoosterEnabled)
                    }
                )
            }

            OfflineContinuityOverlay(
                continuity = continuity,
                workspaceLabel = workspace.displayName,
                onRetryOrReloadLatest = { session.reloadOrRetryLatest() },
                onDismissRestoredBanner = { session.dismissRestoredBanner() }
            )
        }
    }
}
