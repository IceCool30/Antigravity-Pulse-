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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Smartphone
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.ConnectedAppBrowserLinks
import com.example.data.remote.PulseBrowserManager
import com.example.data.remote.RetainedWebViewManager
import com.example.ui.components.OfflineContinuityOverlay
import com.example.ui.components.PinnedWebViewHost
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.frostedGlass

/**
 * Integrated multi-tab Pulse Browser sharing the app's unified cookie store across
 * Google AI Studio, Gemini, GitHub, Vercel, Supabase, and any custom URL / preview host.
 */
@Composable
fun PulseBrowserScreen(
    domLagBoosterEnabled: Boolean,
    isWorkspaceActive: Boolean = true,
    onStatusEvent: (String) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val tabs by PulseBrowserManager.tabs.collectAsStateWithLifecycle()
    val activeTabId by PulseBrowserManager.activeTabId.collectAsStateWithLifecycle()
    val isAddressBarVisible by PulseBrowserManager.isAddressBarVisible.collectAsStateWithLifecycle()

    val activeTab = tabs.firstOrNull { it.id == activeTabId } ?: tabs.first()
    var addressInput by remember(activeTab.id, activeTab.url) {
        mutableStateOf(activeTab.url)
    }

    val appearance = LocalAppearanceSettings.current
    val bg = appearance.backgroundColor
    val surface = appearance.surfaceColor
    val elevated = appearance.elevatedSurfaceColor
    val border = appearance.borderColor
    val fg = appearance.foregroundColor
    val secondaryFg = appearance.secondaryTextColor
    val accent = appearance.accentColor
    val isFrostedGlass = appearance.isFrostedGlassEnabled

    LaunchedEffect(activeTab.id, domLagBoosterEnabled) {
        PulseBrowserManager.applyLagBooster(activeTab.id, domLagBoosterEnabled)
    }

    fun submitAddress() {
        val resolved = PulseBrowserManager.navigateActiveTab(addressInput)
        if (resolved == null) {
            onStatusEvent("Blocked unsafe URL scheme in Pulse Browser.")
        } else {
            focusManager.clearFocus()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .testTag("pulse_browser_screen")
    ) {
        AnimatedVisibility(visible = isAddressBarVisible) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(surface)
                    .testTag("browser_top_chrome")
            ) {
                // Row 1: Compact Tab Strip + New Tab (+)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        tabs.forEach { tab ->
                            val selected = tab.id == activeTab.id
                            Row(
                                modifier = Modifier
                                    .widthIn(min = 84.dp, max = 160.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) accent.copy(alpha = 0.16f) else elevated
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (selected) accent.copy(alpha = 0.55f) else border,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .clickable { PulseBrowserManager.selectTab(tab.id) }
                                    .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)
                                    .testTag("browser_tab_${tab.id}"),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = tab.title.ifBlank { "New Tab" },
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (selected) accent else fg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .clickable { PulseBrowserManager.closeTab(tab.id) }
                                        .testTag("browser_close_tab_${tab.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Close ${tab.title}",
                                        tint = secondaryFg,
                                        modifier = Modifier.size(13.dp)
                                    )
                                }
                            }
                        }
                    }

                    IconButton(
                        onClick = { PulseBrowserManager.openNewTab() },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("browser_new_tab_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "New Browser Tab",
                            tint = accent,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                }

                // Row 2: Back / Forward + Omnibox + Reload / Desktop / Hide Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { PulseBrowserManager.goBackActiveTab() },
                        enabled = activeTab.canGoBack,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("browser_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = if (activeTab.canGoBack) fg else secondaryFg.copy(alpha = 0.35f),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = { PulseBrowserManager.goForwardActiveTab() },
                        enabled = activeTab.canGoForward,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("browser_forward_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            tint = if (activeTab.canGoForward) fg else secondaryFg.copy(alpha = 0.35f),
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    // Omnibox URL / Search input
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 38.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(elevated)
                            .border(1.dp, border, RoundedCornerShape(12.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (activeTab.url.startsWith("https://", ignoreCase = true)) {
                                Icons.Default.Lock
                            } else {
                                Icons.Default.Search
                            },
                            contentDescription = null,
                            tint = if (activeTab.url.startsWith("https://", ignoreCase = true)) {
                                appearance.successColor
                            } else {
                                secondaryFg
                            },
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            if (addressInput.isEmpty()) {
                                Text(
                                    text = "Search or enter URL",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = secondaryFg,
                                    maxLines = 1
                                )
                            }
                            BasicTextField(
                                value = addressInput,
                                onValueChange = { addressInput = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = fg,
                                    fontSize = 13.sp
                                ),
                                cursorBrush = SolidColor(accent),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                                keyboardActions = KeyboardActions(onGo = { submitAddress() }),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("browser_address_input")
                            )
                        }
                        if (addressInput.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Clear Address",
                                tint = secondaryFg,
                                modifier = Modifier
                                    .size(16.dp)
                                    .clickable { addressInput = "" }
                            )
                        }
                    }

                    IconButton(
                        onClick = { PulseBrowserManager.reloadActiveTab() },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("browser_reload_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload Page",
                            tint = secondaryFg,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = {
                            val next = !activeTab.isDesktopMode
                            PulseBrowserManager.setActiveTabDesktopMode(next)
                            onStatusEvent(
                                if (next) "Browser: Desktop View enabled"
                                else "Browser: Mobile View enabled"
                            )
                        },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("browser_desktop_mode_button")
                    ) {
                        Icon(
                            imageVector = if (activeTab.isDesktopMode) Icons.Default.DesktopWindows else Icons.Default.Smartphone,
                            contentDescription = "Toggle Desktop View",
                            tint = if (activeTab.isDesktopMode) accent else secondaryFg,
                            modifier = Modifier.size(17.dp)
                        )
                    }

                    IconButton(
                        onClick = { PulseBrowserManager.setAddressBarVisible(false) },
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("browser_hide_bar_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fullscreen,
                            contentDescription = "Hide Browser Bar",
                            tint = secondaryFg,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                HorizontalDivider(color = border, thickness = 1.dp)
            }
        }

        if (activeTab.url.isNotBlank() && activeTab.loadProgress in 1..99) {
            LinearProgressIndicator(
                progress = { activeTab.loadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(2.dp),
                color = accent,
                trackColor = Color.Transparent
            )
        }

        // Main Content: Persistent Window-Attached Multi-Tab WebView Stack + Speed Dial overlay
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(bg)
                .testTag("browser_viewport_container")
        ) {
            // Keep every navigated browser tab permanently attached to the window so switching
            // between tabs never detaches, throttles, or reloads any background tab.
            tabs.filter { it.url.isNotBlank() }.forEach { tab ->
                val isTabActive = tab.id == activeTab.id
                val shouldBeVisible = isTabActive && isWorkspaceActive
                val tabLayerModifier = if (shouldBeVisible) {
                    Modifier.graphicsLayer { clip = true }
                } else {
                    Modifier
                        .drawWithContent {}
                        .clearAndSetSemantics {}
                }
                key(tab.id, tab.rendererGeneration) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .zIndex(if (isTabActive) 1f else 0f)
                            .then(tabLayerModifier)
                    ) {
                        PinnedWebViewHost(
                            isWorkspaceActive = shouldBeVisible,
                            modifier = Modifier.fillMaxSize(),
                            webViewProvider = { ctx ->
                                PulseBrowserManager.obtainWebViewForTab(
                                    context = ctx,
                                    tabId = tab.id,
                                    domLagBooster = domLagBoosterEnabled,
                                    onPageEvent = onStatusEvent
                                )
                            }
                        )
                    }
                }
            }

            if (activeTab.url.isBlank()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                        .background(bg)
                ) {
                    BrowserSpeedDialHome(
                        surface = surface,
                        elevated = elevated,
                        border = border,
                        fg = fg,
                        secondaryFg = secondaryFg,
                        accent = accent,
                        isFrostedGlass = isFrostedGlass,
                        onOpenUrl = { url ->
                            addressInput = url
                            PulseBrowserManager.navigateActiveTab(url)
                        }
                    )
                }
            } else if (isWorkspaceActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(2f)
                ) {
                    OfflineContinuityOverlay(
                        continuity = activeTab.continuityState,
                        workspaceLabel = activeTab.title.ifBlank { "Browser" },
                        onRetryOrReloadLatest = { PulseBrowserManager.reloadOrRetryActiveTab() },
                        onDismissRestoredBanner = { PulseBrowserManager.dismissActiveRestoredBanner() }
                    )
                }
            }
        }
    }
}

@Composable
private fun BrowserSpeedDialHome(
    surface: Color,
    elevated: Color,
    border: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    isFrostedGlass: Boolean,
    onOpenUrl: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp)
            .testTag("browser_speed_dial"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = 0.14f))
                        .border(1.dp, accent.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Public,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Column {
                    Text(
                        text = "Pulse Browser",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = fg
                    )
                    Text(
                        text = "Shared sessions across all connected workspaces",
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryFg
                    )
                }
            }

            // 2-Column Connected App Speed Dial Grid
            val rows = ConnectedAppBrowserLinks.chunked(2)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        pair.forEach { link ->
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 74.dp)
                                    .frostedGlass(
                                        shape = RoundedCornerShape(16.dp),
                                        baseColor = surface,
                                        tintColor = accent,
                                        borderColor = border,
                                        enabled = isFrostedGlass,
                                        fillAlpha = 0.84f,
                                        tintAlpha = 0.05f,
                                        borderAlpha = 0.52f
                                    )
                                    .clickable { onOpenUrl(link.url) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                                    .testTag("browser_quick_link_${link.id}"),
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = link.label,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = fg
                                    )
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                                        contentDescription = "Open ${link.label}",
                                        tint = accent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = link.subtitle,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryFg,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}
