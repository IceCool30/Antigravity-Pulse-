package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AntigravityContrastMode
import com.example.ui.theme.AntigravityThemeMode
import com.example.ui.theme.AntigravityThemePreset
import com.example.ui.theme.AppearanceSettings
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.parseHexColor

@Composable
fun AppearanceSettingsSheet(
    settings: AppearanceSettings,
    permissionsRefreshTick: Int = 0,
    onUpdateSettings: ((AppearanceSettings) -> AppearanceSettings) -> Unit,
    onSelectPreset: (AntigravityThemePreset) -> Unit,
    onResetPreset: () -> Unit,
    onToggleAutoReconnect: (Boolean) -> Unit = {},
    onToggleBackgroundKeepAlive: (Boolean) -> Unit = {},
    onReleaseWorkspaceFromRam: (String) -> Unit = {},
    onOpenBatterySettings: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onSendTestNotification: () -> Unit = {},
    onRequestRuntimePermissions: (Array<String>) -> Unit = {},
    onOpenAppSystemSettings: () -> Unit = {},
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    val activeTheme = LocalAppearanceSettings.current
    val bg = activeTheme.backgroundColor
    val surface = activeTheme.surfaceColor
    val elevated = activeTheme.elevatedSurfaceColor
    val border = activeTheme.borderColor
    val fg = activeTheme.foregroundColor
    val secondaryFg = activeTheme.secondaryTextColor
    val accent = activeTheme.accentColor
    val isFrostedGlass = activeTheme.isFrostedGlassEnabled

    var showPresetPickerDialog by rememberSaveable { mutableStateOf(false) }
    var editingColorField by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .testTag("appearance_settings_sheet")
    ) {
        // Minimalist top bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .width(38.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(border)
                    .align(Alignment.CenterHorizontally)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 8.dp)
                        .testTag("appearance_back_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = secondaryFg,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Back",
                        style = MaterialTheme.typography.titleMedium,
                        color = fg
                    )
                }

                Text(
                    text = "Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    modifier = Modifier.padding(end = 8.dp)
                )
            }
            HorizontalDivider(color = border, thickness = 1.dp)
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 640.dp)
                    .padding(horizontal = 16.dp)
                    .testTag("settings_list"),
                contentPadding = PaddingValues(top = 18.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // SECTION 1: Appearance (Core Controls + Collapsible Advanced Styling)
                item(key = "section_appearance") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsSectionHeader(
                            title = "Appearance",
                            secondaryFg = secondaryFg,
                            trailing = {
                                TextButton(
                                    onClick = onResetPreset,
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("reset_preset_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Replay,
                                        contentDescription = "Reset to Default Dark",
                                        tint = accent,
                                        modifier = Modifier.size(15.dp)
                                    )
                                    Spacer(modifier = Modifier.width(5.dp))
                                    Text(
                                        text = "Reset Default",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = accent
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
                            // Theme Mode [ System | Light | Dark ]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Theme",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = fg
                                )

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(bg)
                                        .border(1.dp, border, RoundedCornerShape(24.dp))
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    ThemeModeIconPill(
                                        icon = Icons.Default.Computer,
                                        contentDescription = "System Theme",
                                        selected = settings.themeMode == AntigravityThemeMode.SYSTEM,
                                        activeBg = elevated,
                                        activeBorder = border,
                                        fg = fg,
                                        secondaryFg = secondaryFg,
                                        onClick = {
                                            onUpdateSettings { it.copy(themeMode = AntigravityThemeMode.SYSTEM) }
                                        }
                                    )
                                    ThemeModeIconPill(
                                        icon = Icons.Default.LightMode,
                                        contentDescription = "Light Theme",
                                        selected = settings.themeMode == AntigravityThemeMode.LIGHT,
                                        activeBg = elevated,
                                        activeBorder = border,
                                        fg = fg,
                                        secondaryFg = secondaryFg,
                                        onClick = {
                                            onUpdateSettings { it.copy(themeMode = AntigravityThemeMode.LIGHT) }
                                        }
                                    )
                                    ThemeModeIconPill(
                                        icon = Icons.Default.DarkMode,
                                        contentDescription = "Dark Theme",
                                        selected = settings.themeMode == AntigravityThemeMode.DARK,
                                        activeBg = elevated,
                                        activeBorder = border,
                                        fg = fg,
                                        secondaryFg = secondaryFg,
                                        onClick = {
                                            onUpdateSettings { it.copy(themeMode = AntigravityThemeMode.DARK) }
                                        }
                                    )
                                }
                            }

                            HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

                            // Preset Selector [ ● Default Dark v ]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Preset",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = fg
                                )

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(20.dp))
                                        .background(elevated)
                                        .border(1.dp, border, RoundedCornerShape(20.dp))
                                        .clickable { showPresetPickerDialog = true }
                                        .padding(horizontal = 14.dp, vertical = 8.dp)
                                        .testTag("open_preset_picker_button"),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(14.dp)
                                            .clip(CircleShape)
                                            .background(parseHexColor(settings.backgroundHex, Color.Black))
                                            .border(2.dp, accent, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = settings.presetName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = fg
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Icon(
                                        imageVector = Icons.Default.KeyboardArrowDown,
                                        contentDescription = "Choose Theme Preset",
                                        tint = secondaryFg,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

                            // Contrast [ Default | Strong ]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 56.dp)
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Contrast",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = fg
                                )

                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(bg)
                                        .border(1.dp, border, RoundedCornerShape(24.dp))
                                        .padding(3.dp),
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    ContrastOptionPill(
                                        label = "Default",
                                        selected = settings.contrastMode == AntigravityContrastMode.DEFAULT,
                                        activeBg = elevated,
                                        activeBorder = border,
                                        fg = fg,
                                        secondaryFg = secondaryFg,
                                        onClick = {
                                            onUpdateSettings { it.copy(contrastMode = AntigravityContrastMode.DEFAULT) }
                                        }
                                    )
                                    ContrastOptionPill(
                                        label = "Strong",
                                        selected = settings.contrastMode == AntigravityContrastMode.STRONG,
                                        activeBg = elevated,
                                        activeBorder = border,
                                        fg = fg,
                                        secondaryFg = secondaryFg,
                                        onClick = {
                                            onUpdateSettings { it.copy(contrastMode = AntigravityContrastMode.STRONG) }
                                        }
                                    )
                                }
                            }

                            HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

                            // Fullscreen Mode (Title-only)
                            SettingsToggleRow(
                                icon = Icons.Default.Fullscreen,
                                title = "Fullscreen Mode",
                                checked = settings.isFullscreenBarsHidden,
                                onCheckedChange = { hidden ->
                                    onUpdateSettings { it.copy(isFullscreenBarsHidden = hidden) }
                                },
                                bg = bg,
                                fg = fg,
                                secondaryFg = secondaryFg,
                                accent = accent,
                                testTag = "fullscreen_bars_switch"
                            )

                            HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

                            // Collapsible Advanced Customization (Hex Colors, Fonts, Material You, Glass, Code Preview)
                            SettingsCustomizeAppearanceBlock(
                                settings = settings,
                                bg = bg,
                                elevated = elevated,
                                border = border,
                                fg = fg,
                                secondaryFg = secondaryFg,
                                accent = accent,
                                onUpdateSettings = onUpdateSettings,
                                onEditColorField = { field -> editingColorField = field }
                            )
                        }
                    }
                }

                // SECTION 2: Memory & Keep-Alive (1+ Hour Multi-Workspace Retention + Smooth UI Motion)
                item(key = "section_memory_keep_alive_$permissionsRefreshTick") {
                    SettingsKeepAliveSection(
                        settings = settings,
                        permissionsRefreshTick = permissionsRefreshTick,
                        bg = bg,
                        surface = surface,
                        elevated = elevated,
                        border = border,
                        fg = fg,
                        secondaryFg = secondaryFg,
                        accent = accent,
                        isFrostedGlass = isFrostedGlass,
                        onUpdateSettings = onUpdateSettings,
                        onToggleBackgroundKeepAlive = onToggleBackgroundKeepAlive,
                        onReleaseWorkspaceFromRam = onReleaseWorkspaceFromRam,
                        onOpenBatterySettings = onOpenBatterySettings
                    )
                }

                // SECTION 3: Security & Automation
                item(key = "section_security") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsSectionHeader(
                            title = "Security & Automation",
                            secondaryFg = secondaryFg
                        )

                        SettingsCard(
                            surface = surface,
                            accent = accent,
                            border = border,
                            isFrostedGlass = isFrostedGlass
                        ) {
                            SettingsToggleRow(
                                icon = Icons.Default.Fingerprint,
                                title = "Biometric Lock",
                                hint = "Fingerprint or PIN on open",
                                checked = settings.isBiometricLockEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateSettings { it.copy(isBiometricLockEnabled = enabled) }
                                },
                                bg = bg,
                                fg = fg,
                                secondaryFg = secondaryFg,
                                accent = accent,
                                testTag = "biometric_lock_switch"
                            )

                            HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

                            SettingsToggleRow(
                                icon = Icons.Default.Autorenew,
                                title = "Auto-Reconnect",
                                checked = settings.isAutoReconnectEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateSettings { it.copy(isAutoReconnectEnabled = enabled) }
                                    onToggleAutoReconnect(enabled)
                                },
                                bg = bg,
                                fg = fg,
                                secondaryFg = secondaryFg,
                                accent = accent,
                                testTag = "auto_reconnect_switch"
                            )
                        }
                    }
                }

                // SECTION 3: Notifications
                item(key = "section_notifications") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        SettingsSectionHeader(
                            title = "Notifications",
                            secondaryFg = secondaryFg
                        )

                        SettingsCard(
                            surface = surface,
                            accent = accent,
                            border = border,
                            isFrostedGlass = isFrostedGlass
                        ) {
                            SettingsToggleRow(
                                icon = Icons.Default.NotificationsActive,
                                title = "Agent Notifications",
                                checked = settings.isApprovalNotificationsEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateSettings { it.copy(isApprovalNotificationsEnabled = enabled) }
                                    if (enabled) {
                                        onRequestNotificationPermission()
                                    }
                                },
                                bg = bg,
                                fg = fg,
                                secondaryFg = secondaryFg,
                                accent = accent,
                                testTag = "agent_notifications_switch"
                            )

                            HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

                            SettingsToggleRow(
                                icon = Icons.Default.NotificationsPaused,
                                title = "Only Alert in Background",
                                hint = "Mute while app is open",
                                checked = settings.isNotifyOnlyInBackground,
                                enabled = settings.isApprovalNotificationsEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateSettings { it.copy(isNotifyOnlyInBackground = enabled) }
                                },
                                bg = bg,
                                fg = fg,
                                secondaryFg = secondaryFg,
                                accent = accent,
                                testTag = "notify_only_background_switch"
                            )

                            HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

                            SettingsToggleRow(
                                icon = Icons.Default.NotificationsNone,
                                title = "App & Sync Alerts",
                                checked = settings.isAppEventNotificationsEnabled,
                                onCheckedChange = { enabled ->
                                    onUpdateSettings { it.copy(isAppEventNotificationsEnabled = enabled) }
                                    if (enabled) {
                                        onRequestNotificationPermission()
                                    }
                                },
                                bg = bg,
                                fg = fg,
                                secondaryFg = secondaryFg,
                                accent = accent,
                                testTag = "app_notifications_switch"
                            )

                            HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 52.dp)
                                    .padding(horizontal = 16.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Test Notification",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = secondaryFg
                                )
                                OutlinedButton(
                                    onClick = onSendTestNotification,
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                    modifier = Modifier.testTag("send_test_notification_button")
                                ) {
                                    Text(
                                        text = "Test",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = accent,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // SECTION 4: Compact Device Permissions Status List
                item(key = "device_permissions_$permissionsRefreshTick") {
                    SettingsPermissionsSection(
                        permissionsRefreshTick = permissionsRefreshTick,
                        surface = surface,
                        border = border,
                        fg = fg,
                        secondaryFg = secondaryFg,
                        accent = accent,
                        isFrostedGlass = isFrostedGlass,
                        onRequestRuntimePermissions = onRequestRuntimePermissions,
                        onOpenAppSystemSettings = onOpenAppSystemSettings
                    )
                }

                // SECTION 5: About Developer
                item(key = "section_developer") {
                    SettingsDeveloperCard(
                        settings = settings,
                        surface = surface,
                        elevated = elevated,
                        border = border,
                        fg = fg,
                        secondaryFg = secondaryFg,
                        accent = accent,
                        isFrostedGlass = isFrostedGlass
                    )
                }

                // SECTION 6: App Updates (GitHub Releases check, download & verified install)
                item(key = "section_updates") {
                    UpdateSettingsSection(
                        surface = surface,
                        border = border,
                        fg = fg,
                        secondaryFg = secondaryFg,
                        accent = accent,
                        isFrostedGlass = isFrostedGlass
                    )
                }
            }
        }
    }

    if (showPresetPickerDialog) {
        SettingsPresetPickerDialog(
            selectedPresetName = settings.presetName,
            surface = surface,
            border = border,
            fg = fg,
            secondaryFg = secondaryFg,
            accent = accent,
            isFrostedGlass = isFrostedGlass,
            onSelectPreset = onSelectPreset,
            onDismiss = { showPresetPickerDialog = false }
        )
    }

    editingColorField?.let { fieldName ->
        SettingsHexColorEditorDialog(
            fieldName = fieldName,
            settings = settings,
            surface = surface,
            border = border,
            fg = fg,
            secondaryFg = secondaryFg,
            accent = accent,
            onApplyHex = { formatted ->
                onUpdateSettings { current ->
                    when (fieldName) {
                        "Background" -> current.copy(backgroundHex = formatted)
                        "Foreground" -> current.copy(foregroundHex = formatted)
                        else -> current.copy(
                            accentHex = formatted,
                            isMaterialYouDynamicEnabled = false
                        )
                    }
                }
            },
            onDismiss = { editingColorField = null }
        )
    }
}
