package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.RetainedWebViewManager
import com.example.data.remote.T3CodePinnedWebViewManager
import com.example.data.remote.T3EnvironmentProfile
import com.example.data.remote.WebViewOfflineCacheHelper
import com.example.ui.components.OfflineContinuityOverlay
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppearanceSettings

/**
 * Full-screen lazy RAM-pinned **T3 Code Web** workspace (`https://app.t3.codes/`).
 *
 * Supports:
 * - Instant hosted `app.t3.codes` control plane with all linked coding-agent environments
 * - Native Environments & Pairing sheet for switching/bookmarking direct self-hosted `t3 serve`,
 *   Tailscale, VM, or `#token=...` pairing URLs
 * - In-app hardened OAuth / `window.open` popup modal
 * - Desktop / Mobile viewport toggling (preserving horizontal scrolling for diffs and terminals)
 * - Lag Booster, Offline Visual Continuity, and RAM-pinned background persistence
 */
@Composable
fun T3CodeWebScreen(
    domLagBoosterEnabled: Boolean,
    isFullscreenBarsHidden: Boolean,
    isWorkspaceActive: Boolean = true,
    showEnvironmentsDialog: Boolean = false,
    onShowEnvironmentsDialogChange: (Boolean) -> Unit = {},
    onOpenT3QrScanner: () -> Unit = {},
    onToggleDomBooster: (Boolean) -> Unit,
    onStatusEvent: (String) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        T3CodePinnedWebViewManager.ensureEnvironmentsLoaded(context)
    }

    val environments by T3CodePinnedWebViewManager.savedEnvironments.collectAsStateWithLifecycle()
    val activeEnvironmentId by T3CodePinnedWebViewManager.activeEnvironmentId.collectAsStateWithLifecycle()
    val isDesktopMode by T3CodePinnedWebViewManager.isDesktopModeState.collectAsStateWithLifecycle()
    val loadProgress by T3CodePinnedWebViewManager.pageLoadProgress.collectAsStateWithLifecycle()
    val continuity by T3CodePinnedWebViewManager.continuityState.collectAsStateWithLifecycle()
    val rendererGeneration by T3CodePinnedWebViewManager.rendererGeneration.collectAsStateWithLifecycle()
    val popupWebView by T3CodePinnedWebViewManager.popupWebViewState.collectAsStateWithLifecycle()
    val popupUrl by T3CodePinnedWebViewManager.popupUrlState.collectAsStateWithLifecycle()

    val activeEnvironment = environments.firstOrNull { it.id == activeEnvironmentId }
        ?: T3CodePinnedWebViewManager.CLOUD_ENVIRONMENT
    val isOnCloud = activeEnvironment.isBuiltInCloud

    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val elevated = appearance.elevatedSurfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor
    val successColor = appearance.successColor

    LaunchedEffect(domLagBoosterEnabled) {
        if (T3CodePinnedWebViewManager.isPinnedInRam()) {
            T3CodePinnedWebViewManager.applyLagBooster(domLagBoosterEnabled)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .testTag("t3_code_web_screen")
    ) {
        AnimatedVisibility(visible = !isFullscreenBarsHidden) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .testTag("t3_code_collapsible_toolbar")
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Row 1: Cloud Home + Environments Switcher + QR Pair + Reload & Open External
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
                            FilterChip(
                                selected = isOnCloud,
                                onClick = {
                                    T3CodePinnedWebViewManager.navigateToCloudHome(context)
                                    onStatusEvent("Opened T3 Code Cloud (app.t3.codes)")
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Cloud,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = "app.t3.codes",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isOnCloud) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.22f),
                                    selectedLabelColor = accent,
                                    selectedLeadingIconColor = accent,
                                    containerColor = elevated,
                                    labelColor = secondaryFg,
                                    iconColor = secondaryFg
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isOnCloud,
                                    borderColor = border,
                                    selectedBorderColor = accent.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("t3_code_cloud_chip")
                            )

                            FilterChip(
                                selected = !isOnCloud,
                                onClick = { onShowEnvironmentsDialogChange(true) },
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Dns,
                                        contentDescription = null,
                                        modifier = Modifier.size(15.dp)
                                    )
                                },
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                },
                                label = {
                                    Text(
                                        text = if (isOnCloud) {
                                            "Environments (${environments.size})"
                                        } else {
                                            activeEnvironment.label.take(18)
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = accent.copy(alpha = 0.22f),
                                    selectedLabelColor = accent,
                                    selectedLeadingIconColor = accent,
                                    selectedTrailingIconColor = accent,
                                    containerColor = elevated,
                                    labelColor = fg,
                                    iconColor = secondaryFg
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = !isOnCloud,
                                    borderColor = border,
                                    selectedBorderColor = accent.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier
                                    .heightIn(min = 48.dp)
                                    .testTag("t3_code_environments_button")
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onOpenT3QrScanner,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("t3_code_qr_pair_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.QrCodeScanner,
                                    contentDescription = "Scan T3 Code Pairing QR",
                                    tint = accent,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    T3CodePinnedWebViewManager.reloadCurrentPage()
                                    onStatusEvent("Reloading T3 Code...")
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("t3_code_reload_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Reload T3 Code",
                                    tint = secondaryFg,
                                    modifier = Modifier.size(19.dp)
                                )
                            }

                            IconButton(
                                onClick = {
                                    try {
                                        val url = T3CodePinnedWebViewManager.currentUrl()
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
                                    .testTag("t3_code_open_external_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                    contentDescription = "Open T3 Code in Browser",
                                    tint = accent,
                                    modifier = Modifier.size(19.dp)
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
                                .heightIn(min = 36.dp)
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
                                    T3CodePinnedWebViewManager.setDesktopMode(next)
                                    onStatusEvent(
                                        if (next) "T3 Code: Desktop View enabled (pinch-to-zoom ON)"
                                        else "T3 Code: Mobile View enabled"
                                    )
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("t3_code_desktop_mode_toggle"),
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
                                .heightIn(min = 36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (domLagBoosterEnabled) successColor.copy(alpha = 0.14f) else elevated
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (domLagBoosterEnabled) successColor.copy(alpha = 0.55f) else border,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onToggleDomBooster(!domLagBoosterEnabled) }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                                .testTag("t3_code_lag_booster_toggle"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Bolt,
                                contentDescription = "Lag Booster",
                                tint = if (domLagBoosterEnabled) successColor else secondaryFg,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (domLagBoosterEnabled) "Lag Booster: ON" else "Lag Booster: OFF",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (domLagBoosterEnabled) successColor else secondaryFg
                            )
                        }

                        Text(
                            text = "Pinned in RAM",
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = JetBrainsMonoFontFamily,
                            color = successColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(successColor.copy(alpha = 0.12f))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                                .testTag("t3_code_pinned_ram_badge")
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
                .testTag("t3_code_pinned_webview_container")
        ) {
            key(rendererGeneration) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        T3CodePinnedWebViewManager.obtainPinnedWebView(
                            context = ctx,
                            domLagBooster = domLagBoosterEnabled,
                            onPageEvent = onStatusEvent
                        ).also { wv ->
                            WebViewOfflineCacheHelper.applyWindowAttachedVisibility(wv, isWorkspaceActive)
                        }
                    },
                    update = { wv ->
                        WebViewOfflineCacheHelper.applyWindowAttachedVisibility(wv, isWorkspaceActive)
                    }
                )
            }

            if (isWorkspaceActive) {
                OfflineContinuityOverlay(
                    continuity = continuity,
                    workspaceLabel = "T3 Code",
                    onRetryOrReloadLatest = { T3CodePinnedWebViewManager.reloadOrRetryLatest() },
                    onDismissRestoredBanner = { T3CodePinnedWebViewManager.dismissRestoredBanner() }
                )
            }
        }
    }

    // In-App OAuth / window.open Popup Dialog
    popupWebView?.let { activePopup ->
        BackHandler(enabled = true) {
            T3CodePinnedWebViewManager.dismissPopupWebView()
        }
        Dialog(
            onDismissRequest = { T3CodePinnedWebViewManager.dismissPopupWebView() },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .border(1.dp, border, RoundedCornerShape(20.dp))
                    .testTag("t3_code_popup_dialog"),
                color = surface
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(elevated)
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "T3 Code Auth / Link",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = fg
                            )
                            Text(
                                text = popupUrl.ifBlank { "Loading..." },
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = secondaryFg,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (RetainedWebViewManager.isSafeHttpUrl(popupUrl)) {
                                IconButton(
                                    onClick = {
                                        try {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(popupUrl))
                                                    .addCategory(Intent.CATEGORY_BROWSABLE)
                                            )
                                        } catch (_: Exception) {
                                        }
                                    },
                                    modifier = Modifier.size(48.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open Popup in External Browser",
                                        tint = accent
                                    )
                                }
                            }
                            IconButton(
                                onClick = { T3CodePinnedWebViewManager.dismissPopupWebView() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("t3_code_popup_close_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Popup",
                                    tint = fg
                                )
                            }
                        }
                    }
                    HorizontalDivider(color = border)
                    AndroidView(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        factory = {
                            (activePopup.parent as? ViewGroup)?.removeView(activePopup)
                            activePopup
                        }
                    )
                }
            }
        }
    }

    // Environments & Pairing Sheet Modal
    if (showEnvironmentsDialog) {
        T3EnvironmentsSheetDialog(
            environments = environments,
            activeEnvironmentId = activeEnvironmentId,
            onSelectEnvironment = { env ->
                T3CodePinnedWebViewManager.selectEnvironment(context, env)
                onStatusEvent("Switched T3 Code to ${env.label}")
                onShowEnvironmentsDialogChange(false)
            },
            onConnectCustomUrl = { label, rawUrl, saveBookmark ->
                val result = T3CodePinnedWebViewManager.openPairingOrCustomUrl(
                    context = context,
                    label = label,
                    rawUrl = rawUrl,
                    saveBookmark = saveBookmark
                )
                result.fold(
                    onSuccess = { profile ->
                        onStatusEvent("Connected T3 Code to ${profile.label}")
                        onShowEnvironmentsDialogChange(false)
                        null
                    },
                    onFailure = { err ->
                        err.message ?: "Invalid T3 Code URL."
                    }
                )
            },
            onDeleteCustomEnvironment = { envId ->
                T3CodePinnedWebViewManager.removeCustomEnvironment(context, envId)
                onStatusEvent("Removed saved T3 Code environment")
            },
            onScanQrPairing = {
                onShowEnvironmentsDialogChange(false)
                onOpenT3QrScanner()
            },
            onDismiss = { onShowEnvironmentsDialogChange(false) }
        )
    }
}

@Composable
private fun T3EnvironmentsSheetDialog(
    environments: List<T3EnvironmentProfile>,
    activeEnvironmentId: String,
    onSelectEnvironment: (T3EnvironmentProfile) -> Unit,
    onConnectCustomUrl: (label: String, rawUrl: String, saveBookmark: Boolean) -> String?,
    onDeleteCustomEnvironment: (String) -> Unit,
    onScanQrPairing: () -> Unit,
    onDismiss: () -> Unit
) {
    val appearance = LocalAppearanceSettings.current
    val surface = appearance.surfaceColor
    val elevated = appearance.elevatedSurfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor
    val onAccent = appearance.onAccentColor
    val successColor = appearance.successColor
    val errorColor = appearance.errorColor

    var envLabel by remember { mutableStateOf("") }
    var envUrl by remember { mutableStateOf("") }
    var saveBookmark by remember { mutableStateOf(true) }
    var validationError by remember { mutableStateOf<String?>(null) }

    val customCount = environments.count { !it.isBuiltInCloud }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = surface,
        modifier = Modifier
            .widthIn(max = 540.dp)
            .testTag("t3_code_environments_dialog"),
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Terminal,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "T3 Environments",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = fg
                    )
                }
                OutlinedButton(
                    onClick = onScanQrPairing,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("t3_env_modal_scan_qr_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.QrCodeScanner,
                        contentDescription = "Scan T3 Pairing QR",
                        tint = accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Pair QR", color = accent, fontWeight = FontWeight.Bold)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Use T3 Code Cloud (app.t3.codes) for all linked T3 Connect environments, or switch directly to a self-hosted server:",
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryFg
                )

                environments.forEach { env ->
                    val isSelected = env.id == activeEnvironmentId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) accent.copy(alpha = 0.14f) else elevated)
                            .border(
                                width = 1.dp,
                                color = if (isSelected) accent.copy(alpha = 0.55f) else border.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            )
                            .clickable { onSelectEnvironment(env) }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("t3_env_item_${env.id}"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .clip(CircleShape)
                                    .background(if (isSelected) successColor else secondaryFg.copy(alpha = 0.4f))
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = env.label,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isSelected) accent else fg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = env.baseUrl,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontFamily = JetBrainsMonoFontFamily,
                                    color = secondaryFg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        if (!env.isBuiltInCloud) {
                            IconButton(
                                onClick = { onDeleteCustomEnvironment(env.id) },
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("t3_env_delete_${env.id}")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Remove ${env.label}",
                                    tint = secondaryFg,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        } else {
                            Text(
                                text = "CLOUD",
                                style = MaterialTheme.typography.labelSmall,
                                fontFamily = JetBrainsMonoFontFamily,
                                color = accent,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(accent.copy(alpha = 0.14f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (customCount == 0) {
                    Text(
                        text = "Tip: Add a direct Tailscale, LAN (e.g. http://192.168.1.20:3773), or pairing URL (#token=...) below to bookmark extra environments.",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryFg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(elevated.copy(alpha = 0.55f))
                            .padding(10.dp)
                    )
                }

                HorizontalDivider(color = border.copy(alpha = 0.5f))

                Text(
                    text = "Add Self-Hosted Host or Paste Pairing Link",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )

                OutlinedTextField(
                    value = envLabel,
                    onValueChange = {
                        envLabel = it
                        validationError = null
                    },
                    label = { Text("Environment Name (Optional)") },
                    placeholder = { Text("e.g., Mac Studio • Tailscale") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("t3_env_label_input")
                )

                OutlinedTextField(
                    value = envUrl,
                    onValueChange = {
                        envUrl = it
                        validationError = null
                    },
                    label = { Text("T3 Code URL or Pairing Link") },
                    placeholder = { Text("https://... or http://host:3773/#token=...") },
                    singleLine = true,
                    isError = validationError != null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("t3_env_url_input")
                )

                if (validationError != null) {
                    Text(
                        text = validationError.orEmpty(),
                        style = MaterialTheme.typography.labelSmall,
                        color = errorColor
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { saveBookmark = !saveBookmark }
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = saveBookmark,
                        onCheckedChange = { saveBookmark = it },
                        modifier = Modifier.testTag("t3_env_save_bookmark_checkbox")
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Bookmark host locally (pairing tokens are never saved)",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryFg
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = envUrl.trim()
                    if (trimmed.isBlank()) {
                        validationError = "Enter a T3 Code URL or pairing link first."
                    } else {
                        validationError = onConnectCustomUrl(envLabel, trimmed, saveBookmark)
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = onAccent
                ),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("t3_env_connect_button")
            ) {
                Text("Connect", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Text("Close", color = secondaryFg)
            }
        }
    )
}
