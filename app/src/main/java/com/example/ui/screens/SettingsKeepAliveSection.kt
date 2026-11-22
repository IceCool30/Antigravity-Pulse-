package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Animation
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.remote.PinnedWorkspaceRamStatus
import com.example.data.remote.WorkspaceMemoryKeepAliveCoordinator
import com.example.ui.theme.AppearanceSettings
import com.example.ui.theme.LocalAppearanceSettings

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SettingsKeepAliveSection(
    settings: AppearanceSettings,
    permissionsRefreshTick: Int,
    bg: Color,
    surface: Color,
    elevated: Color,
    border: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    isFrostedGlass: Boolean,
    onUpdateSettings: ((AppearanceSettings) -> AppearanceSettings) -> Unit,
    onToggleBackgroundKeepAlive: (Boolean) -> Unit,
    onReleaseWorkspaceFromRam: (String) -> Unit,
    onOpenBatterySettings: () -> Unit
) {
    val context = LocalContext.current
    val successGreen = LocalAppearanceSettings.current.successColor

    val statuses by WorkspaceMemoryKeepAliveCoordinator.workspaceStatuses.collectAsStateWithLifecycle()
    val pinnedCount by WorkspaceMemoryKeepAliveCoordinator.pinnedCount.collectAsStateWithLifecycle()
    val serviceRunning by WorkspaceMemoryKeepAliveCoordinator.isKeepAliveServiceRunning.collectAsStateWithLifecycle()

    val isBatteryUnrestricted = remember(permissionsRefreshTick) {
        WorkspaceMemoryKeepAliveCoordinator.isIgnoringBatteryOptimizations(context)
    }
    val pinnedWorkspaces = remember(statuses) {
        statuses.filter { it.isPinnedInRam }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_keep_alive_section")
    ) {
        SettingsSectionHeader(
            title = "Memory & Keep-Alive",
            secondaryFg = secondaryFg,
            trailing = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(
                            if (serviceRunning) successGreen.copy(alpha = 0.14f)
                            else accent.copy(alpha = 0.12f)
                        )
                        .padding(horizontal = 9.dp, vertical = 4.dp)
                        .testTag("keep_alive_pinned_count_badge")
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (serviceRunning) successGreen else accent)
                    )
                    Text(
                        text = "$pinnedCount Pinned in RAM",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (serviceRunning) successGreen else accent
                    )
                }
            }
        )

        SettingsCard(
            surface = surface,
            accent = accent,
            border = border,
            isFrostedGlass = isFrostedGlass
        ) {
            SettingsToggleRow(
                icon = Icons.Default.Memory,
                title = "1+ Hour RAM Keep-Alive",
                hint = "Keep all open developer workspaces live without reloading",
                checked = settings.isBackgroundKeepAliveEnabled,
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(isBackgroundKeepAliveEnabled = enabled) }
                    onToggleBackgroundKeepAlive(enabled)
                },
                bg = bg,
                fg = fg,
                secondaryFg = secondaryFg,
                accent = accent,
                testTag = "background_keep_alive_switch"
            )

            // Live list of pinned workspaces with on-demand Release RAM chip
            AnimatedVisibility(
                visible = pinnedWorkspaces.isNotEmpty(),
                enter = if (settings.isSmoothAnimationsEnabled) {
                    expandVertically(animationSpec = tween(180)) + fadeIn(animationSpec = tween(160))
                } else {
                    fadeIn(animationSpec = tween(0))
                },
                exit = if (settings.isSmoothAnimationsEnabled) {
                    shrinkVertically(animationSpec = tween(160)) + fadeOut(animationSpec = tween(120))
                } else {
                    fadeOut(animationSpec = tween(0))
                }
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                ) {
                    Text(
                        text = "Active in memory (tap x to release individual RAM)",
                        style = MaterialTheme.typography.labelSmall,
                        color = secondaryFg
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        pinnedWorkspaces.forEach { item ->
                            PinnedWorkspaceRamChip(
                                status = item,
                                elevated = elevated,
                                border = border,
                                fg = fg,
                                secondaryFg = secondaryFg,
                                successGreen = successGreen,
                                onRelease = { onReleaseWorkspaceFromRam(item.id) }
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

            // Play-compliant Battery Unrestricted settings deep-link
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clickable(onClick = onOpenBatterySettings)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
                    .testTag("battery_unrestricted_settings_row"),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.BatteryChargingFull,
                        contentDescription = null,
                        tint = if (isBatteryUnrestricted) successGreen else accent,
                        modifier = Modifier.size(19.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Battery Unrestricted",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = fg
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isBatteryUnrestricted) {
                                "OS background throttling disabled for long sessions"
                            } else {
                                "Set Battery to Unrestricted in Android Settings for 1h+ retention"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = secondaryFg
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                if (isBatteryUnrestricted) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(successGreen.copy(alpha = 0.14f))
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Battery Unrestricted",
                            tint = successGreen,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Unrestricted",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = successGreen
                        )
                    }
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Configure",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = accent
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = "Open Battery Settings",
                            tint = accent,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

            // Smooth GPU-only UI Animations toggle
            SettingsToggleRow(
                icon = Icons.Default.Animation,
                title = "Smooth UI Animations",
                hint = "Subtle GPU-only motion (zero WebView reload overhead)",
                checked = settings.isSmoothAnimationsEnabled,
                onCheckedChange = { enabled ->
                    onUpdateSettings { it.copy(isSmoothAnimationsEnabled = enabled) }
                },
                bg = bg,
                fg = fg,
                secondaryFg = secondaryFg,
                accent = accent,
                testTag = "smooth_animations_switch"
            )
        }
    }
}

@Composable
private fun PinnedWorkspaceRamChip(
    status: PinnedWorkspaceRamStatus,
    elevated: Color,
    border: Color,
    fg: Color,
    secondaryFg: Color,
    successGreen: Color,
    onRelease: () -> Unit
) {
    val durationLabel = remember(status.pinnedSinceElapsedMs) {
        WorkspaceMemoryKeepAliveCoordinator.formatPinnedDuration(status.pinnedSinceElapsedMs)
    }
    val subtitle = if (status.detail.isNotBlank()) status.detail else durationLabel

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(elevated)
            .border(1.dp, border, RoundedCornerShape(14.dp))
            .padding(start = 10.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(successGreen)
        )
        Text(
            text = status.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg
        )
        Text(
            text = "· $subtitle",
            style = MaterialTheme.typography.labelSmall,
            color = secondaryFg
        )
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .clickable(onClick = onRelease)
                .testTag("release_ram_${status.id}"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Release ${status.label} from RAM",
                tint = secondaryFg,
                modifier = Modifier.size(13.dp)
            )
        }
    }
}
