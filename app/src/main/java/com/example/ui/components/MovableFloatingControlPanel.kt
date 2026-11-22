package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DesktopWindows
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.SubdirectoryArrowRight
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.WebAsset
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.data.remote.AiStudioWebSection
import com.example.data.remote.DevWebWorkspace
import com.example.data.remote.WorkspaceShortcut
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.frostedGlass
import kotlin.math.roundToInt

data class FloatingPanelTabItem(
    val index: Int,
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

/**
 * Compact, translucent, movable floating handle & dropdown-driven control sidebar.
 *
 * - Keeps the panel short and uncluttered using two collapsible dropdowns:
 *   1. `Workspaces ▾` (compact 2-column grid of all connected workspaces)
 *   2. `Page Tools ▾` (context-aware shortcuts, Desktop View, Lag Booster, Browser bar/tabs)
 * - Keeps `Reload`, `Settings`, and `Show Bars` permanently 1-tap in the footer.
 */
@Composable
fun MovableFloatingControlPanel(
    isExpanded: Boolean,
    xFraction: Float,
    yFraction: Float,
    tabs: List<FloatingPanelTabItem>,
    selectedTabIndex: Int,
    activeHostLabel: String,
    isAiStudioActive: Boolean = false,
    activeAiStudioSection: AiStudioWebSection = AiStudioWebSection.NEW_CHAT,
    activeDevWorkspace: DevWebWorkspace? = null,
    activeDevShortcutId: String = "",
    isT3CodeActive: Boolean = false,
    activeT3EnvironmentLabel: String = "T3 Code Cloud",
    isBrowserActive: Boolean = false,
    isBrowserBarVisible: Boolean = true,
    browserTabCount: Int = 1,
    isDesktopModeEnabled: Boolean = false,
    domLagBoosterEnabled: Boolean = true,
    surface: Color,
    elevated: Color,
    border: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    onExpandedChange: (Boolean) -> Unit,
    onPersistPositionFraction: (Float, Float) -> Unit,
    onSelectTab: (Int) -> Unit,
    onSelectAiStudioSection: (AiStudioWebSection) -> Unit = {},
    onSelectDevShortcut: (WorkspaceShortcut) -> Unit = {},
    onOpenT3CloudHome: () -> Unit = {},
    onOpenT3Environments: () -> Unit = {},
    onOpenT3QrScanner: () -> Unit = {},
    onOpenT3External: () -> Unit = {},
    onToggleDesktopMode: (Boolean) -> Unit = {},
    onToggleDomBooster: (Boolean) -> Unit = {},
    onOpenAiStudioExternal: () -> Unit = {},
    onOpenDevWorkspaceExternal: () -> Unit = {},
    onNewBrowserTab: () -> Unit = {},
    onToggleBrowserBar: () -> Unit = {},
    onOpenBrowserExternal: () -> Unit = {},
    onOpenHostSwitcher: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onReloadWeb: () -> Unit,
    onShowBars: () -> Unit,
    onOpenSettings: () -> Unit,
    reloadActionLabel: String = "Reload Remote",
    modifier: Modifier = Modifier
) {
    var workspacesDropdownOpen by remember { mutableStateOf(false) }
    var pageToolsDropdownOpen by remember { mutableStateOf(false) }

    LaunchedEffect(isExpanded) {
        if (!isExpanded) {
            workspacesDropdownOpen = false
            pageToolsDropdownOpen = false
        }
    }

    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val haptic = LocalHapticFeedback.current
        val currentAppearance = LocalAppearanceSettings.current
        val isFrostedGlass = currentAppearance.isFrostedGlassEnabled
        val isLightMode = currentAppearance.isLightMode
        val onAccent = currentAppearance.onAccentColor
        val successColor = currentAppearance.successColor
        val containerWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val containerHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val edgeMarginPx = with(density) { 8.dp.toPx() }

        val defaultHandleWidthPx = with(density) { 38.dp.toPx() }
        val defaultHandleHeightPx = with(density) { 40.dp.toPx() }

        var handleSize by remember {
            mutableStateOf(IntSize(defaultHandleWidthPx.roundToInt(), defaultHandleHeightPx.roundToInt()))
        }
        var panelSize by remember {
            mutableStateOf(
                IntSize(
                    with(density) { 218.dp.toPx() }.roundToInt(),
                    with(density) { 250.dp.toPx() }.roundToInt()
                )
            )
        }

        val maxHandleX = (containerWidthPx - handleSize.width.coerceAtLeast(1)).coerceAtLeast(0f)
        val maxHandleY = (containerHeightPx - handleSize.height.coerceAtLeast(1)).coerceAtLeast(0f)

        var handleX by remember { mutableFloatStateOf(xFraction.coerceIn(0f, 1f) * maxHandleX) }
        var handleY by remember { mutableFloatStateOf(yFraction.coerceIn(0f, 1f) * maxHandleY) }

        LaunchedEffect(xFraction, yFraction, maxHandleX, maxHandleY) {
            handleX = (xFraction.coerceIn(0f, 1f) * maxHandleX).coerceIn(0f, maxHandleX)
            handleY = (yFraction.coerceIn(0f, 1f) * maxHandleY).coerceIn(0f, maxHandleY)
        }

        fun persistCurrentFractions() {
            val nextXFrac = if (maxHandleX > 0f) (handleX / maxHandleX).coerceIn(0f, 1f) else 0f
            val nextYFrac = if (maxHandleY > 0f) (handleY / maxHandleY).coerceIn(0f, 1f) else 0.42f
            onPersistPositionFraction(nextXFrac, nextYFrac)
        }

        if (!isExpanded) {
            val isOnRightHalf = handleX > (maxHandleX * 0.5f)
            val handleShape = when {
                handleX <= edgeMarginPx -> RoundedCornerShape(topEnd = 14.dp, bottomEnd = 14.dp)
                handleX >= (maxHandleX - edgeMarginPx) -> RoundedCornerShape(topStart = 14.dp, bottomStart = 14.dp)
                else -> RoundedCornerShape(16.dp)
            }

            Row(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            handleX.coerceIn(0f, maxHandleX).roundToInt(),
                            handleY.coerceIn(0f, maxHandleY).roundToInt()
                        )
                    }
                    .onSizeChanged { handleSize = it }
                    .shadow(if (isLightMode) 5.dp else 3.dp, handleShape, ambientColor = Color.Black.copy(alpha = 0.28f))
                    .frostedGlass(
                        shape = handleShape,
                        baseColor = if (isLightMode) surface else elevated,
                        tintColor = accent,
                        borderColor = if (isLightMode) border else accent,
                        enabled = isFrostedGlass,
                        fillAlpha = if (isLightMode) 0.94f else 0.56f,
                        tintAlpha = 0.10f,
                        borderAlpha = if (isLightMode) 0.78f else 0.55f
                    )
                    .pointerInput(maxHandleX, maxHandleY) {
                        detectDragGestures(
                            onDragEnd = {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                persistCurrentFractions()
                            },
                            onDragCancel = { persistCurrentFractions() },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                handleX = (handleX + dragAmount.x).coerceIn(0f, maxHandleX)
                                handleY = (handleY + dragAmount.y).coerceIn(0f, maxHandleY)
                            }
                        )
                    }
                    .clickable {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onExpandedChange(true)
                    }
                    .padding(horizontal = 5.dp, vertical = 9.dp)
                    .testTag("floating_fullscreen_edge_handle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Drag to move control handle",
                    tint = secondaryFg.copy(alpha = if (isLightMode) 0.88f else 0.72f),
                    modifier = Modifier.size(13.dp)
                )
                Icon(
                    imageVector = if (isOnRightHalf) {
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = "Open Movable Side Panel Controls",
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            val activeTabLabel = tabs.firstOrNull { it.index == selectedTabIndex }?.title ?: "Remote"
            val headerTitle = when {
                isT3CodeActive -> "T3 Code"
                isAiStudioActive -> "AI Studio"
                activeDevWorkspace != null -> activeDevWorkspace.displayName
                isBrowserActive -> "Browser ($browserTabCount)"
                else -> activeTabLabel
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.14f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        onExpandedChange(false)
                    }
                    .testTag("floating_panel_backdrop")
            )

            val maxPanelX = (containerWidthPx - panelSize.width - edgeMarginPx).coerceAtLeast(edgeMarginPx)
            val maxPanelY = (containerHeightPx - panelSize.height - edgeMarginPx).coerceAtLeast(edgeMarginPx)
            val clampedPanelX = handleX.coerceIn(edgeMarginPx, maxPanelX)
            val clampedPanelY = handleY.coerceIn(edgeMarginPx, maxPanelY)

            val panelShape = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            clampedPanelX.roundToInt(),
                            clampedPanelY.roundToInt()
                        )
                    }
                    .onSizeChanged { panelSize = it }
                    .width(224.dp)
                    .heightIn(max = (maxHeight - 24.dp).coerceAtLeast(200.dp))
                    .shadow(14.dp, panelShape, ambientColor = Color.Black.copy(alpha = 0.45f))
                    .frostedGlass(
                        shape = panelShape,
                        baseColor = surface,
                        tintColor = accent,
                        borderColor = border,
                        enabled = isFrostedGlass,
                        fillAlpha = 0.88f,
                        tintAlpha = 0.07f,
                        borderAlpha = 0.62f
                    )
                    .testTag("floating_quick_menu_overlay")
            ) {
                // Compact Draggable Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(elevated.copy(alpha = if (isFrostedGlass) 0.55f else 0.88f))
                        .pointerInput(maxHandleX, maxHandleY, maxPanelX, maxPanelY) {
                            detectDragGestures(
                                onDragEnd = { persistCurrentFractions() },
                                onDragCancel = { persistCurrentFractions() },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    handleX = (handleX + dragAmount.x).coerceIn(0f, maxHandleX)
                                    handleY = (handleY + dragAmount.y).coerceIn(0f, maxHandleY)
                                }
                            )
                        }
                        .padding(start = 10.dp, end = 4.dp, top = 5.dp, bottom = 5.dp)
                        .testTag("floating_panel_drag_header"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragIndicator,
                            contentDescription = "Drag to reposition panel",
                            tint = accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = headerTitle,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = fg,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    IconButton(
                        onClick = { onExpandedChange(false) },
                        modifier = Modifier
                            .size(32.dp)
                            .testTag("floating_panel_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Collapse Panel",
                            tint = secondaryFg,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                HorizontalDivider(color = border.copy(alpha = 0.55f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // DROPDOWN 1: Workspaces (collapsed by default -> compact 2-col grid when opened)
                    PanelDropdownHeader(
                        label = "Workspaces",
                        badge = activeTabLabel,
                        icon = Icons.Default.Apps,
                        expanded = workspacesDropdownOpen,
                        accent = accent,
                        fg = fg,
                        secondaryFg = secondaryFg,
                        elevated = elevated,
                        border = border,
                        testTag = "floating_workspaces_dropdown_toggle",
                        onClick = { workspacesDropdownOpen = !workspacesDropdownOpen }
                    )

                    AnimatedVisibility(visible = workspacesDropdownOpen) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                                .testTag("floating_workspaces_dropdown_content"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            tabs.chunked(2).forEach { pair ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    pair.forEach { tab ->
                                        val isSelected = selectedTabIndex == tab.index
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(
                                                    if (isSelected) accent.copy(alpha = 0.16f)
                                                    else elevated.copy(alpha = 0.65f)
                                                )
                                                .border(
                                                    width = 1.dp,
                                                    color = if (isSelected) accent.copy(alpha = 0.5f) else Color.Transparent,
                                                    shape = RoundedCornerShape(10.dp)
                                                )
                                                .clickable {
                                                    onSelectTab(tab.index)
                                                    onExpandedChange(false)
                                                }
                                                .padding(horizontal = 7.dp, vertical = 7.dp)
                                                .testTag("floating_tab_${tab.testTag}"),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = tab.icon,
                                                contentDescription = tab.title,
                                                tint = if (isSelected) accent else secondaryFg,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Spacer(modifier = Modifier.width(5.dp))
                                            Text(
                                                text = tab.title,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                                color = if (isSelected) accent else fg,
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

                    // DROPDOWN 2: Page Tools (collapsed by default -> context-aware actions for active tab)
                    PanelDropdownHeader(
                        label = "Page Tools",
                        badge = null,
                        icon = Icons.Default.Tune,
                        expanded = pageToolsDropdownOpen,
                        accent = accent,
                        fg = fg,
                        secondaryFg = secondaryFg,
                        elevated = elevated,
                        border = border,
                        testTag = "floating_page_tools_dropdown_toggle",
                        onClick = { pageToolsDropdownOpen = !pageToolsDropdownOpen }
                    )

                    AnimatedVisibility(visible = pageToolsDropdownOpen) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 2.dp)
                                .testTag("floating_page_tools_dropdown_content"),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            when {
                                isT3CodeActive -> {
                                    PanelActionButton(
                                        label = "app.t3.codes (Cloud)",
                                        icon = Icons.Default.Cloud,
                                        iconTint = accent,
                                        textColor = fg,
                                        containerColor = elevated.copy(alpha = 0.72f),
                                        testTag = "floating_t3_cloud_home",
                                        onClick = {
                                            onOpenT3CloudHome()
                                            onExpandedChange(false)
                                        }
                                    )
                                    PanelActionButton(
                                        label = "Envs: ${activeT3EnvironmentLabel.take(14)}",
                                        icon = Icons.Default.Dns,
                                        iconTint = accent,
                                        textColor = fg,
                                        containerColor = accent.copy(alpha = 0.14f),
                                        testTag = "floating_t3_environments",
                                        onClick = {
                                            onExpandedChange(false)
                                            onOpenT3Environments()
                                        }
                                    )
                                    PanelActionButton(
                                        label = "Scan T3 Pairing QR",
                                        icon = Icons.Default.QrCodeScanner,
                                        iconTint = accent,
                                        textColor = fg,
                                        containerColor = elevated.copy(alpha = 0.72f),
                                        testTag = "floating_t3_scan_qr",
                                        onClick = {
                                            onExpandedChange(false)
                                            onOpenT3QrScanner()
                                        }
                                    )
                                    PanelActionButton(
                                        label = if (isDesktopModeEnabled) "Desktop View: ON" else "Desktop View: OFF",
                                        icon = if (isDesktopModeEnabled) Icons.Default.DesktopWindows else Icons.Default.Smartphone,
                                        iconTint = if (isDesktopModeEnabled) accent else secondaryFg,
                                        textColor = if (isDesktopModeEnabled) accent else fg,
                                        containerColor = if (isDesktopModeEnabled) accent.copy(alpha = 0.14f) else elevated.copy(alpha = 0.72f),
                                        testTag = "floating_t3_desktop_mode",
                                        onClick = { onToggleDesktopMode(!isDesktopModeEnabled) }
                                    )
                                    PanelActionButton(
                                        label = if (domLagBoosterEnabled) "Lag Booster: ON" else "Lag Booster: OFF",
                                        icon = Icons.Default.Bolt,
                                        iconTint = if (domLagBoosterEnabled) successColor else secondaryFg,
                                        textColor = if (domLagBoosterEnabled) successColor else fg,
                                        containerColor = if (domLagBoosterEnabled) successColor.copy(alpha = 0.14f) else elevated.copy(alpha = 0.72f),
                                        testTag = "floating_t3_lag_booster",
                                        onClick = { onToggleDomBooster(!domLagBoosterEnabled) }
                                    )
                                    PanelActionButton(
                                        label = "Open in Browser",
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        iconTint = accent,
                                        textColor = fg,
                                        containerColor = elevated.copy(alpha = 0.72f),
                                        testTag = "floating_t3_open_browser",
                                        onClick = {
                                            onExpandedChange(false)
                                            onOpenT3External()
                                        }
                                    )
                                }

                                isAiStudioActive -> {
                                    PanelActionButton(
                                        label = "Prompts",
                                        icon = Icons.AutoMirrored.Filled.Chat,
                                        iconTint = if (activeAiStudioSection == AiStudioWebSection.NEW_CHAT) accent else secondaryFg,
                                        textColor = if (activeAiStudioSection == AiStudioWebSection.NEW_CHAT) accent else fg,
                                        containerColor = if (activeAiStudioSection == AiStudioWebSection.NEW_CHAT) {
                                            accent.copy(alpha = 0.16f)
                                        } else {
                                            elevated.copy(alpha = 0.72f)
                                        },
                                        testTag = "floating_ai_studio_prompts",
                                        onClick = {
                                            onSelectAiStudioSection(AiStudioWebSection.NEW_CHAT)
                                            onExpandedChange(false)
                                        }
                                    )
                                    PanelActionButton(
                                        label = "Build Apps",
                                        icon = Icons.Default.Code,
                                        iconTint = if (activeAiStudioSection == AiStudioWebSection.BUILD_APPS) accent else secondaryFg,
                                        textColor = if (activeAiStudioSection == AiStudioWebSection.BUILD_APPS) accent else fg,
                                        containerColor = if (activeAiStudioSection == AiStudioWebSection.BUILD_APPS) {
                                            accent.copy(alpha = 0.16f)
                                        } else {
                                            elevated.copy(alpha = 0.72f)
                                        },
                                        testTag = "floating_ai_studio_build_apps",
                                        onClick = {
                                            onSelectAiStudioSection(AiStudioWebSection.BUILD_APPS)
                                            onExpandedChange(false)
                                        }
                                    )
                                    PanelActionButton(
                                        label = "Library",
                                        icon = Icons.Default.FolderOpen,
                                        iconTint = if (activeAiStudioSection == AiStudioWebSection.DRIVE_LIBRARY) accent else secondaryFg,
                                        textColor = if (activeAiStudioSection == AiStudioWebSection.DRIVE_LIBRARY) accent else fg,
                                        containerColor = if (activeAiStudioSection == AiStudioWebSection.DRIVE_LIBRARY) {
                                            accent.copy(alpha = 0.16f)
                                        } else {
                                            elevated.copy(alpha = 0.72f)
                                        },
                                        testTag = "floating_ai_studio_library",
                                        onClick = {
                                            onSelectAiStudioSection(AiStudioWebSection.DRIVE_LIBRARY)
                                            onExpandedChange(false)
                                        }
                                    )
                                    PanelActionButton(
                                        label = if (domLagBoosterEnabled) "Lag Booster: ON" else "Lag Booster: OFF",
                                        icon = Icons.Default.Bolt,
                                        iconTint = if (domLagBoosterEnabled) successColor else secondaryFg,
                                        textColor = if (domLagBoosterEnabled) successColor else fg,
                                        containerColor = if (domLagBoosterEnabled) successColor.copy(alpha = 0.14f) else elevated.copy(alpha = 0.72f),
                                        testTag = "floating_ai_studio_lag_booster",
                                        onClick = { onToggleDomBooster(!domLagBoosterEnabled) }
                                    )
                                    PanelActionButton(
                                        label = "Open in Browser",
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        iconTint = accent,
                                        textColor = fg,
                                        containerColor = elevated.copy(alpha = 0.72f),
                                        testTag = "floating_ai_studio_open_browser",
                                        onClick = {
                                            onExpandedChange(false)
                                            onOpenAiStudioExternal()
                                        }
                                    )
                                }

                                activeDevWorkspace != null -> {
                                    activeDevWorkspace.shortcuts.forEach { shortcut ->
                                        val selected = activeDevShortcutId == shortcut.id
                                        PanelActionButton(
                                            label = shortcut.label,
                                            icon = Icons.Default.SubdirectoryArrowRight,
                                            iconTint = if (selected) accent else secondaryFg,
                                            textColor = if (selected) accent else fg,
                                            containerColor = if (selected) accent.copy(alpha = 0.14f) else elevated.copy(alpha = 0.72f),
                                            testTag = "floating_${activeDevWorkspace.id}_shortcut_${shortcut.id}",
                                            onClick = {
                                                onSelectDevShortcut(shortcut)
                                                onExpandedChange(false)
                                            }
                                        )
                                    }
                                    PanelActionButton(
                                        label = if (isDesktopModeEnabled) "Desktop View: ON" else "Desktop View: OFF",
                                        icon = if (isDesktopModeEnabled) Icons.Default.DesktopWindows else Icons.Default.Smartphone,
                                        iconTint = if (isDesktopModeEnabled) accent else secondaryFg,
                                        textColor = if (isDesktopModeEnabled) accent else fg,
                                        containerColor = if (isDesktopModeEnabled) accent.copy(alpha = 0.14f) else elevated.copy(alpha = 0.72f),
                                        testTag = "floating_dev_desktop_mode",
                                        onClick = { onToggleDesktopMode(!isDesktopModeEnabled) }
                                    )
                                    PanelActionButton(
                                        label = if (domLagBoosterEnabled) "Lag Booster: ON" else "Lag Booster: OFF",
                                        icon = Icons.Default.Bolt,
                                        iconTint = if (domLagBoosterEnabled) successColor else secondaryFg,
                                        textColor = if (domLagBoosterEnabled) successColor else fg,
                                        containerColor = if (domLagBoosterEnabled) successColor.copy(alpha = 0.14f) else elevated.copy(alpha = 0.72f),
                                        testTag = "floating_dev_lag_booster",
                                        onClick = { onToggleDomBooster(!domLagBoosterEnabled) }
                                    )
                                    PanelActionButton(
                                        label = "Open in Browser",
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        iconTint = accent,
                                        textColor = fg,
                                        containerColor = elevated.copy(alpha = 0.72f),
                                        testTag = "floating_dev_open_browser",
                                        onClick = {
                                            onExpandedChange(false)
                                            onOpenDevWorkspaceExternal()
                                        }
                                    )
                                }

                                isBrowserActive -> {
                                    PanelActionButton(
                                        label = "New Browser Tab",
                                        icon = Icons.Default.Add,
                                        iconTint = accent,
                                        textColor = fg,
                                        containerColor = elevated.copy(alpha = 0.72f),
                                        testTag = "floating_browser_new_tab",
                                        onClick = {
                                            onNewBrowserTab()
                                            onExpandedChange(false)
                                        }
                                    )
                                    PanelActionButton(
                                        label = if (isBrowserBarVisible) "Hide Address Bar" else "Show Address Bar",
                                        icon = Icons.Default.WebAsset,
                                        iconTint = secondaryFg,
                                        textColor = fg,
                                        containerColor = elevated.copy(alpha = 0.72f),
                                        testTag = "floating_browser_toggle_bar",
                                        onClick = {
                                            onToggleBrowserBar()
                                            onExpandedChange(false)
                                        }
                                    )
                                    PanelActionButton(
                                        label = if (isDesktopModeEnabled) "Desktop View: ON" else "Desktop View: OFF",
                                        icon = if (isDesktopModeEnabled) Icons.Default.DesktopWindows else Icons.Default.Smartphone,
                                        iconTint = if (isDesktopModeEnabled) accent else secondaryFg,
                                        textColor = if (isDesktopModeEnabled) accent else fg,
                                        containerColor = if (isDesktopModeEnabled) accent.copy(alpha = 0.14f) else elevated.copy(alpha = 0.72f),
                                        testTag = "floating_browser_desktop_mode",
                                        onClick = { onToggleDesktopMode(!isDesktopModeEnabled) }
                                    )
                                    PanelActionButton(
                                        label = if (domLagBoosterEnabled) "Lag Booster: ON" else "Lag Booster: OFF",
                                        icon = Icons.Default.Bolt,
                                        iconTint = if (domLagBoosterEnabled) successColor else secondaryFg,
                                        textColor = if (domLagBoosterEnabled) successColor else fg,
                                        containerColor = if (domLagBoosterEnabled) successColor.copy(alpha = 0.14f) else elevated.copy(alpha = 0.72f),
                                        testTag = "floating_browser_lag_booster",
                                        onClick = { onToggleDomBooster(!domLagBoosterEnabled) }
                                    )
                                    PanelActionButton(
                                        label = "Open in External",
                                        icon = Icons.AutoMirrored.Filled.OpenInNew,
                                        iconTint = accent,
                                        textColor = fg,
                                        containerColor = elevated.copy(alpha = 0.72f),
                                        testTag = "floating_browser_open_external",
                                        onClick = {
                                            onExpandedChange(false)
                                            onOpenBrowserExternal()
                                        }
                                    )
                                }

                                else -> {
                                    PanelActionButton(
                                        label = activeHostLabel.ifBlank { "Connect Remote" },
                                        icon = Icons.Default.Link,
                                        iconTint = successColor,
                                        textColor = fg,
                                        containerColor = elevated.copy(alpha = 0.72f),
                                        testTag = "floating_quick_connect_host",
                                        onClick = {
                                            onExpandedChange(false)
                                            onOpenHostSwitcher()
                                        }
                                    )
                                    PanelActionButton(
                                        label = "Scan QR Code",
                                        icon = Icons.Default.QrCodeScanner,
                                        iconTint = accent,
                                        textColor = fg,
                                        containerColor = elevated.copy(alpha = 0.72f),
                                        testTag = "floating_quick_scan_qr",
                                        onClick = {
                                            onExpandedChange(false)
                                            onOpenQrScanner()
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = border.copy(alpha = 0.5f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    // Pinned Compact Footer Row: Reload + Settings (always 1-tap)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(elevated.copy(alpha = 0.75f))
                                .clickable {
                                    onExpandedChange(false)
                                    onReloadWeb()
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .testTag("floating_quick_reload_web"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = reloadActionLabel,
                                tint = secondaryFg,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Reload",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = fg,
                                maxLines = 1
                            )
                        }

                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(elevated.copy(alpha = 0.75f))
                                .clickable {
                                    onExpandedChange(false)
                                    onOpenSettings()
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp)
                                .testTag("floating_quick_settings"),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Settings",
                                tint = secondaryFg,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = "Settings",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = fg,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.96f))
                            .clickable {
                                onExpandedChange(false)
                                onShowBars()
                            }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                            .testTag("restore_bars_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Show Top & Bottom Bars",
                            tint = onAccent,
                            modifier = Modifier.size(15.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Show Bars",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = onAccent
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PanelDropdownHeader(
    label: String,
    badge: String?,
    icon: ImageVector,
    expanded: Boolean,
    accent: Color,
    fg: Color,
    secondaryFg: Color,
    elevated: Color,
    border: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (expanded) accent.copy(alpha = 0.12f) else elevated.copy(alpha = 0.72f))
            .border(
                width = 1.dp,
                color = if (expanded) accent.copy(alpha = 0.45f) else border.copy(alpha = 0.45f),
                shape = RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (expanded) accent else secondaryFg,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (expanded) accent else fg,
                maxLines = 1
            )
            if (!badge.isNullOrBlank() && !expanded) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Icon(
            imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
            contentDescription = if (expanded) "Collapse $label" else "Expand $label",
            tint = if (expanded) accent else secondaryFg,
            modifier = Modifier.size(17.dp)
        )
    }
}

@Composable
private fun PanelActionButton(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    textColor: Color,
    containerColor: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 9.dp, vertical = 7.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(7.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
