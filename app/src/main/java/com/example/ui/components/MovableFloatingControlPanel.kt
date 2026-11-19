package com.example.ui.components

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
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.GraphicEq
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.example.ui.theme.EmeraldOnline
import kotlin.math.roundToInt

data class FloatingPanelTabItem(
    val index: Int,
    val title: String,
    val icon: ImageVector,
    val testTag: String
)

/**
 * Movable / Draggable Floating Control Handle & Expanded Side Panel.
 *
 * - Users can drag the collapsed handle OR drag the open panel's header bar to ANY (x, y)
 *   position on screen (left edge, right edge, top, bottom, or floating anywhere).
 * - Position is normalized to fractions [0f..1f] and persisted via [onPersistPositionFraction]
 *   so it stays exactly where the user placed it across rotations and app restarts.
 * - Expanded panel is automatically clamped within container bounds so it is never clipped.
 */
@Composable
fun MovableFloatingControlPanel(
    isExpanded: Boolean,
    xFraction: Float,
    yFraction: Float,
    tabs: List<FloatingPanelTabItem>,
    selectedTabIndex: Int,
    activeHostLabel: String,
    surface: Color,
    elevated: Color,
    border: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    onExpandedChange: (Boolean) -> Unit,
    onPersistPositionFraction: (Float, Float) -> Unit,
    onSelectTab: (Int) -> Unit,
    onOpenHostSwitcher: () -> Unit,
    onOpenQrScanner: () -> Unit,
    onRecenterWebLayout: () -> Unit,
    onReloadWeb: () -> Unit,
    onOpenLiveVoice: () -> Unit,
    onShowBars: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGuide: () -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(
        modifier = modifier.fillMaxSize()
    ) {
        val density = LocalDensity.current
        val containerWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
        val containerHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val edgeMarginPx = with(density) { 8.dp.toPx() }

        val defaultHandleWidthPx = with(density) { 48.dp.toPx() }
        val defaultHandleHeightPx = with(density) { 54.dp.toPx() }

        var handleSize by remember {
            mutableStateOf(IntSize(defaultHandleWidthPx.roundToInt(), defaultHandleHeightPx.roundToInt()))
        }
        var panelSize by remember {
            mutableStateOf(
                IntSize(
                    with(density) { 210.dp.toPx() }.roundToInt(),
                    with(density) { 420.dp.toPx() }.roundToInt()
                )
            )
        }

        val maxHandleX = (containerWidthPx - handleSize.width.coerceAtLeast(1)).coerceAtLeast(0f)
        val maxHandleY = (containerHeightPx - handleSize.height.coerceAtLeast(1)).coerceAtLeast(0f)

        var handleX by remember { mutableFloatStateOf(xFraction.coerceIn(0f, 1f) * maxHandleX) }
        var handleY by remember { mutableFloatStateOf(yFraction.coerceIn(0f, 1f) * maxHandleY) }

        // Keep pixel offsets synchronized when screen dimensions or persisted fractions change
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
                handleX <= edgeMarginPx -> RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
                handleX >= (maxHandleX - edgeMarginPx) -> RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                else -> RoundedCornerShape(18.dp)
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
                    .shadow(8.dp, handleShape)
                    .clip(handleShape)
                    .background(elevated.copy(alpha = 0.92f))
                    .border(1.dp, accent.copy(alpha = 0.55f), handleShape)
                    .pointerInput(maxHandleX, maxHandleY) {
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
                    .clickable { onExpandedChange(true) }
                    .padding(horizontal = 8.dp, vertical = 14.dp)
                    .testTag("floating_fullscreen_edge_handle"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.DragIndicator,
                    contentDescription = "Drag to move control handle",
                    tint = secondaryFg,
                    modifier = Modifier.size(16.dp)
                )
                Icon(
                    imageVector = if (isOnRightHalf) {
                        Icons.AutoMirrored.Filled.KeyboardArrowLeft
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    contentDescription = "Open Movable Side Panel Controls",
                    tint = accent,
                    modifier = Modifier.size(20.dp)
                )
            }
        } else {
            // Subtle tap-outside backdrop scrim to collapse the movable panel
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.22f))
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

            Column(
                modifier = Modifier
                    .offset {
                        IntOffset(
                            clampedPanelX.roundToInt(),
                            clampedPanelY.roundToInt()
                        )
                    }
                    .onSizeChanged { panelSize = it }
                    .width(214.dp)
                    .heightIn(max = (maxHeight - 24.dp).coerceAtLeast(240.dp))
                    .shadow(14.dp, RoundedCornerShape(20.dp))
                    .clip(RoundedCornerShape(20.dp))
                    .background(surface.copy(alpha = 0.98f))
                    .border(1.dp, border, RoundedCornerShape(20.dp))
                    .testTag("floating_quick_menu_overlay")
            ) {
                // Dedicated Draggable Header Bar so the user can move the open panel anywhere
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(elevated)
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
                        .padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
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
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Column {
                            Text(
                                text = "Controls",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = fg,
                                maxLines = 1
                            )
                            Text(
                                text = "Drag to move",
                                style = MaterialTheme.typography.labelSmall,
                                color = secondaryFg,
                                maxLines = 1
                            )
                        }
                    }

                    IconButton(
                        onClick = { onExpandedChange(false) },
                        modifier = Modifier
                            .size(36.dp)
                            .testTag("floating_panel_close_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Collapse Panel",
                            tint = secondaryFg,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                HorizontalDivider(color = border)

                // Scrollable Control Actions
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Connect / Switch Host
                    PanelActionButton(
                        label = activeHostLabel.ifBlank { "Connect Remote URL" },
                        icon = Icons.Default.Link,
                        iconTint = EmeraldOnline,
                        textColor = fg,
                        containerColor = elevated,
                        testTag = "floating_quick_connect_host",
                        onClick = {
                            onExpandedChange(false)
                            onOpenHostSwitcher()
                        }
                    )

                    // Scan QR Pair
                    PanelActionButton(
                        label = "Scan QR Code",
                        icon = Icons.Default.QrCodeScanner,
                        iconTint = accent,
                        textColor = fg,
                        containerColor = elevated,
                        testTag = "floating_quick_scan_qr",
                        onClick = {
                            onExpandedChange(false)
                            onOpenQrScanner()
                        }
                    )

                    // Re-Center Web Layout (fixes right-side alignment drift in 1 tap)
                    PanelActionButton(
                        label = "Re-Center Layout",
                        icon = Icons.Default.CenterFocusStrong,
                        iconTint = accent,
                        textColor = fg,
                        containerColor = elevated,
                        testTag = "floating_quick_recenter_layout",
                        onClick = {
                            onExpandedChange(false)
                            onRecenterWebLayout()
                        }
                    )

                    // Reload Active Page
                    PanelActionButton(
                        label = "Reload Remote",
                        icon = Icons.Default.Refresh,
                        iconTint = secondaryFg,
                        textColor = fg,
                        containerColor = elevated,
                        testTag = "floating_quick_reload_web",
                        onClick = {
                            onExpandedChange(false)
                            onReloadWeb()
                        }
                    )

                    // Live Voice Assistant
                    PanelActionButton(
                        label = "Live Voice",
                        icon = Icons.Default.GraphicEq,
                        iconTint = EmeraldOnline,
                        textColor = fg,
                        containerColor = elevated,
                        testTag = "floating_quick_live_voice",
                        onClick = {
                            onExpandedChange(false)
                            onOpenLiveVoice()
                        }
                    )

                    HorizontalDivider(color = border, modifier = Modifier.padding(vertical = 2.dp))

                    // 3 Main Working Tabs (Remote, AI Copilot, Cloud Sync)
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

                    HorizontalDivider(color = border, modifier = Modifier.padding(vertical = 2.dp))

                    // Settings & Guide
                    PanelActionButton(
                        label = "Settings & Themes",
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

                    PanelActionButton(
                        label = "How to Use Guide",
                        icon = Icons.AutoMirrored.Filled.HelpOutline,
                        iconTint = accent,
                        textColor = accent,
                        containerColor = Color.Transparent,
                        testTag = "floating_quick_guide",
                        onClick = {
                            onExpandedChange(false)
                            onOpenGuide()
                        }
                    )

                    // Optional Show Bars Toggle
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent)
                            .clickable {
                                onExpandedChange(false)
                                onShowBars()
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                            .testTag("restore_bars_button"),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FullscreenExit,
                            contentDescription = "Show Top & Bottom Bars",
                            tint = Color(0xFF070708),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Show Bars",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF070708)
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
            .padding(horizontal = 10.dp, vertical = 9.dp)
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = iconTint,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
