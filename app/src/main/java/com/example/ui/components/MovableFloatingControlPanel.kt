package com.example.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
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
 * Compact, translucent, movable floating handle & expanded control panel.
 *
 * - Docked to left/right edge: sleek sidebar-tab shape.
 * - Moved anywhere else on screen: compact rounded floating pill.
 * - Position persists as normalized fractions [0f..1f] across restarts.
 * - Context-aware: when on the AI Studio tab, puts Prompts, Build Apps, Library,
 *   Lag Booster, Reload, and Open in Browser one tap away without cluttering fullscreen.
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
    onToggleDomBooster: (Boolean) -> Unit = {},
    onOpenAiStudioExternal: () -> Unit = {},
    onOpenHostSwitcher: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onReloadWeb: () -> Unit,
    onShowBars: () -> Unit,
    onOpenSettings: () -> Unit,
    reloadActionLabel: String = "Reload Remote",
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val haptic = LocalHapticFeedback.current
        val isFrostedGlass = LocalAppearanceSettings.current.isFrostedGlassEnabled
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
                    with(density) { 196.dp.toPx() }.roundToInt(),
                    with(density) { 380.dp.toPx() }.roundToInt()
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
                    .shadow(3.dp, handleShape, ambientColor = Color.Black.copy(alpha = 0.28f))
                    .frostedGlass(
                        shape = handleShape,
                        baseColor = elevated,
                        tintColor = accent,
                        borderColor = accent,
                        enabled = isFrostedGlass,
                        fillAlpha = 0.46f,
                        tintAlpha = 0.10f,
                        borderAlpha = 0.55f
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
                    tint = secondaryFg.copy(alpha = 0.72f),
                    modifier = Modifier.size(13.dp)
                )
                Icon(
                    imageVector = if (isOnRightHalf) {
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = "Open Movable Side Panel Controls",
                    tint = accent.copy(alpha = 0.85f),
                    modifier = Modifier.size(16.dp)
                )
            }
        } else {
            // Subtle tap-outside backdrop scrim to collapse the movable panel
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

            val panelShape = RoundedCornerShape(22.dp)
            Column(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            clampedPanelX.roundToInt(),
                            clampedPanelY.roundToInt()
                        )
                    }
                    .onSizeChanged { panelSize = it }
                    .width(200.dp)
                    .heightIn(max = (maxHeight - 24.dp).coerceAtLeast(220.dp))
                    .shadow(14.dp, panelShape, ambientColor = Color.Black.copy(alpha = 0.45f))
                    .frostedGlass(
                        shape = panelShape,
                        baseColor = surface,
                        tintColor = accent,
                        borderColor = border,
                        enabled = isFrostedGlass,
                        fillAlpha = 0.84f,
                        tintAlpha = 0.08f,
                        borderAlpha = 0.65f
                    )
                    .testTag("floating_quick_menu_overlay")
            ) {
                // Compact Draggable Header Bar
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
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (isAiStudioActive) "AI Studio" else "Controls",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = fg,
                            maxLines = 1
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

                HorizontalDivider(color = border.copy(alpha = 0.6f))

                // Compact Context-Aware Control Actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (isAiStudioActive) {
                        // Distraction-free fullscreen AI Studio quick jumps
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
                            iconTint = if (domLagBoosterEnabled) EmeraldOnline else secondaryFg,
                            textColor = if (domLagBoosterEnabled) EmeraldOnline else fg,
                            containerColor = if (domLagBoosterEnabled) {
                                EmeraldOnline.copy(alpha = 0.16f)
                            } else {
                                elevated.copy(alpha = 0.72f)
                            },
                            testTag = "floating_ai_studio_lag_booster",
                            onClick = {
                                onToggleDomBooster(!domLagBoosterEnabled)
                            }
                        )

                        PanelActionButton(
                            label = reloadActionLabel,
                            icon = Icons.Default.Refresh,
                            iconTint = secondaryFg,
                            textColor = fg,
                            containerColor = elevated.copy(alpha = 0.72f),
                            testTag = "floating_quick_reload_web",
                            onClick = {
                                onExpandedChange(false)
                                onReloadWeb()
                            }
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
                    } else {
                        PanelActionButton(
                            label = activeHostLabel.ifBlank { "Connect Remote" },
                            icon = Icons.Default.Link,
                            iconTint = EmeraldOnline,
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

                        PanelActionButton(
                            label = reloadActionLabel,
                            icon = Icons.Default.Refresh,
                            iconTint = secondaryFg,
                            textColor = fg,
                            containerColor = elevated.copy(alpha = 0.72f),
                            testTag = "floating_quick_reload_web",
                            onClick = {
                                onExpandedChange(false)
                                onReloadWeb()
                            }
                        )
                    }

                    HorizontalDivider(
                        color = border.copy(alpha = 0.55f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    // Main Tabs: Remote, AI Studio & Cloud Sync
                    tabs.forEach { tab ->
                        val isSelected = selectedTabIndex == tab.index
                        PanelActionButton(
                            label = tab.title,
                            icon = tab.icon,
                            iconTint = if (isSelected) accent else secondaryFg,
                            textColor = if (isSelected) accent else fg,
                            containerColor = if (isSelected) accent.copy(alpha = 0.16f) else Color.Transparent,
                            testTag = "floating_tab_${tab.testTag}",
                            onClick = {
                                onSelectTab(tab.index)
                                onExpandedChange(false)
                            }
                        )
                    }

                    HorizontalDivider(
                        color = border.copy(alpha = 0.55f),
                        modifier = Modifier.padding(vertical = 2.dp)
                    )

                    PanelActionButton(
                        label = "Settings",
                        icon = Icons.Default.Settings,
                        iconTint = secondaryFg,
                        textColor = fg,
                        containerColor = Color.Transparent,
                        testTag = "floating_quick_settings",
                        onClick = {
                            onExpandedChange(false)
                            onOpenSettings()
                        }
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(accent.copy(alpha = 0.90f))
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
                            tint = Color(0xFF000000),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Show Bars",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF000000)
                        )
                    }
                }
            }
        }
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
            .clip(RoundedCornerShape(12.dp))
            .background(containerColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
