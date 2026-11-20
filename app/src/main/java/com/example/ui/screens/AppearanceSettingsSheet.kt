package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.NotificationsPaused
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.example.notifications.PulseNotificationManager
import com.example.ui.theme.AntigravityContrastMode
import com.example.ui.theme.AntigravityThemeMode
import com.example.ui.theme.AntigravityThemePreset
import com.example.ui.theme.AppearanceSettings
import com.example.ui.theme.AvailableAntigravityFonts
import com.example.ui.theme.AvailableFontSizesPx
import com.example.ui.theme.EmeraldOnline
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.OfficialAntigravityPresets
import com.example.ui.theme.frostedGlass
import com.example.ui.theme.parseHexColor

@Composable
fun AppearanceSettingsSheet(
    settings: AppearanceSettings,
    permissionsRefreshTick: Int = 0,
    onUpdateSettings: ((AppearanceSettings) -> AppearanceSettings) -> Unit,
    onSelectPreset: (AntigravityThemePreset) -> Unit,
    onResetPreset: () -> Unit,
    onToggleAutoReconnect: (Boolean) -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {},
    onSendTestNotification: () -> Unit = {},
    onRequestRuntimePermissions: (Array<String>) -> Unit = {},
    onOpenAppSystemSettings: () -> Unit = {},
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)
    val context = LocalContext.current

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
    var showFontDropdown by remember { mutableStateOf(false) }
    var showFontSizeDropdown by remember { mutableStateOf(false) }
    var isCodePreviewExpanded by rememberSaveable { mutableStateOf(true) }

    // Hex Color Editor state ("Background", "Foreground", or "Accent")
    var editingColorField by rememberSaveable { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(bg)
            .testTag("appearance_settings_sheet")
    ) {
        // Top Drag Handle + "< Back" Header Bar matching Screenshot 3 + Quick Guide Button
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(surface)
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp)
                    .width(42.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(border)
                    .align(Alignment.CenterHorizontally)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onClose() }
                        .padding(vertical = 4.dp, horizontal = 4.dp)
                        .testTag("appearance_back_button"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Back",
                        tint = secondaryFg,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
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
                    color = fg
                )
            }
            HorizontalDivider(color = border, thickness = 1.dp)
        }

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            contentPadding = PaddingValues(vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // SECTION 1: Appearance
            item {
                Text(
                    text = "Appearance",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlass(
                            shape = RoundedCornerShape(20.dp),
                            baseColor = surface,
                            tintColor = accent,
                            borderColor = border,
                            enabled = isFrostedGlass,
                            fillAlpha = 0.82f,
                            tintAlpha = 0.06f,
                            borderAlpha = 0.58f
                        )
                ) {
                    // Row 1: Theme [ System | Light | Dark ]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Theme",
                            style = MaterialTheme.typography.titleMedium,
                            color = fg
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(bg)
                                .border(1.dp, border, RoundedCornerShape(24.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
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

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // Row 2: Contrast [ Default | Strong ]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Contrast",
                            style = MaterialTheme.typography.titleMedium,
                            color = fg
                        )

                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(bg)
                                .border(1.dp, border, RoundedCornerShape(24.dp))
                                .padding(4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
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

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // Row 3: Font [ Google Sans v ]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Font",
                            style = MaterialTheme.typography.titleMedium,
                            color = fg
                        )

                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(elevated)
                                    .border(1.dp, border, RoundedCornerShape(22.dp))
                                    .clickable { showFontDropdown = true }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .testTag("appearance_font_selector"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = settings.fontFamilyName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = fg
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select Font",
                                    tint = secondaryFg,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showFontDropdown,
                                onDismissRequest = { showFontDropdown = false },
                                modifier = Modifier.background(elevated)
                            ) {
                                AvailableAntigravityFonts.forEach { fontName ->
                                    DropdownMenuItem(
                                        text = { Text(fontName, color = fg) },
                                        onClick = {
                                            onUpdateSettings { it.copy(fontFamilyName = fontName) }
                                            showFontDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // Row 4: Font Size ("Appears larger on mobile") [ 14px v ]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Font Size",
                                style = MaterialTheme.typography.titleMedium,
                                color = fg
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Appears larger on mobile",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                        }

                        Box {
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(elevated)
                                    .border(1.dp, border, RoundedCornerShape(22.dp))
                                    .clickable { showFontSizeDropdown = true }
                                    .padding(horizontal = 18.dp, vertical = 10.dp)
                                    .testTag("appearance_fontsize_selector"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${settings.fontSizePx}px",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = fg
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Select Font Size",
                                    tint = secondaryFg,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showFontSizeDropdown,
                                onDismissRequest = { showFontSizeDropdown = false },
                                modifier = Modifier.background(elevated)
                            ) {
                                AvailableFontSizesPx.forEach { sizePx ->
                                    DropdownMenuItem(
                                        text = { Text("${sizePx}px", color = fg) },
                                        onClick = {
                                            onUpdateSettings { it.copy(fontSizePx = sizePx) }
                                            showFontSizeDropdown = false
                                        }
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // Row 5: Distraction-Free Fullscreen Mode (Hide Top & Bottom Bars)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Distraction-Free Fullscreen",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = fg
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Hide top & bottom bars; summon anytime via floating edge button",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                        }

                        Switch(
                            checked = settings.isFullscreenBarsHidden,
                            onCheckedChange = { hidden ->
                                onUpdateSettings { it.copy(isFullscreenBarsHidden = hidden) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = bg,
                                checkedTrackColor = accent
                            ),
                            modifier = Modifier.testTag("fullscreen_bars_switch")
                        )
                    }

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // Row 6: Material You Dynamic Color (Android 12+ Wallpaper Accent + Pure OLED Black)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Palette,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Material You Dynamic Color",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = fg
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Adapt accent color from your wallpaper while keeping pure #000000 OLED dark",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                        }

                        Switch(
                            checked = settings.isMaterialYouDynamicEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(isMaterialYouDynamicEnabled = enabled) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = bg,
                                checkedTrackColor = accent
                            ),
                            modifier = Modifier.testTag("material_you_toggle")
                        )
                    }

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // Row 7: Subtle Frosted Glass Surfaces
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Frosted Glass Surfaces",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = fg
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Subtle translucent glass sheen on panels, cards & floating controls",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                        }

                        Switch(
                            checked = settings.isFrostedGlassEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(isFrostedGlassEnabled = enabled) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = bg,
                                checkedTrackColor = accent
                            ),
                            modifier = Modifier.testTag("frosted_glass_toggle")
                        )
                    }
                }
            }

            // SECTION 1B: Security, Notifications & Bridge Resilience
            item {
                Text(
                    text = "Security & Bridge Automation",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlass(
                            shape = RoundedCornerShape(20.dp),
                            baseColor = surface,
                            tintColor = accent,
                            borderColor = border,
                            enabled = isFrostedGlass,
                            fillAlpha = 0.82f,
                            tintAlpha = 0.06f,
                            borderAlpha = 0.58f
                        )
                ) {
                    // Biometric App Lock
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Biometric App Lock",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = fg
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Require fingerprint, face unlock, or device PIN when opening Antigravity Pulse",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                        }
                        Switch(
                            checked = settings.isBiometricLockEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(isBiometricLockEnabled = enabled) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = bg,
                                checkedTrackColor = accent
                            ),
                            modifier = Modifier.testTag("biometric_lock_switch")
                        )
                    }

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // WebSocket Auto-Reconnect & Offline Command Queue
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Autorenew,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Auto-Reconnect on Network Recovery",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = fg
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Automatically refresh active Remote & AI Studio sessions when Wi-Fi or 5G reconnects after a drop",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                        }
                        Switch(
                            checked = settings.isAutoReconnectEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(isAutoReconnectEnabled = enabled) }
                                onToggleAutoReconnect(enabled)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = bg,
                                checkedTrackColor = accent
                            ),
                            modifier = Modifier.testTag("auto_reconnect_switch")
                        )
                    }
                }
            }

            // SECTION 1C: Native Phone Notifications (Agent Bridge + App Events)
            item {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlass(
                            shape = RoundedCornerShape(20.dp),
                            baseColor = surface,
                            tintColor = accent,
                            borderColor = border,
                            enabled = isFrostedGlass,
                            fillAlpha = 0.82f,
                            tintAlpha = 0.06f,
                            borderAlpha = 0.58f
                        )
                ) {
                    // Remote Agent & PWA Notifications
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsActive,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Remote Agent Notifications",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = fg
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Live session observer alerts you when your agent finishes a task or requests approval (works while open or minimized; set Android Battery to Unrestricted for longer background runs)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                        }
                        Switch(
                            checked = settings.isApprovalNotificationsEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(isApprovalNotificationsEnabled = enabled) }
                                if (enabled) {
                                    onRequestNotificationPermission()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = bg,
                                checkedTrackColor = accent
                            ),
                            modifier = Modifier.testTag("agent_notifications_switch")
                        )
                    }

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // Only Alert When in Background
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsPaused,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Only Alert in Background",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = fg
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Skip agent banners while you are actively viewing the session; still notify when you switch apps",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                        }
                        Switch(
                            checked = settings.isNotifyOnlyInBackground,
                            enabled = settings.isApprovalNotificationsEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(isNotifyOnlyInBackground = enabled) }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = bg,
                                checkedTrackColor = accent
                            ),
                            modifier = Modifier.testTag("notify_only_background_switch")
                        )
                    }

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // Overall App, Connection & Cloud Sync Notifications
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.NotificationsNone,
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "App & Cloud Sync Alerts",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = fg
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Notify on host connections, Cloud Sync backups, restores, and security warnings",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                        }
                        Switch(
                            checked = settings.isAppEventNotificationsEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateSettings { it.copy(isAppEventNotificationsEnabled = enabled) }
                                if (enabled) {
                                    onRequestNotificationPermission()
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = bg,
                                checkedTrackColor = accent
                            ),
                            modifier = Modifier.testTag("app_notifications_switch")
                        )
                    }

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // Send Test Notification
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Verify phone notification banner & sound",
                            style = MaterialTheme.typography.bodyMedium,
                            color = secondaryFg,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedButton(
                            onClick = onSendTestNotification,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.testTag("send_test_notification_button")
                        ) {
                            Text("Test Alert", color = accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // SECTION 1D: Device Permissions (Microphone, Camera, Notifications & Native File Access)
            item(key = "device_permissions_$permissionsRefreshTick") {
                val micGranted = remember(permissionsRefreshTick) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.RECORD_AUDIO
                    ) == PackageManager.PERMISSION_GRANTED
                }
                val cameraGranted = remember(permissionsRefreshTick) {
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.CAMERA
                    ) == PackageManager.PERMISSION_GRANTED
                }
                val notificationsGranted = remember(permissionsRefreshTick) {
                    PulseNotificationManager.hasNotificationPermission(context)
                }
                val missingPermissions = remember(micGranted, cameraGranted, notificationsGranted) {
                    buildList {
                        if (!micGranted) add(Manifest.permission.RECORD_AUDIO)
                        if (!cameraGranted) add(Manifest.permission.CAMERA)
                        if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Device Permissions",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = fg
                    )
                    if (missingPermissions.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { onRequestRuntimePermissions(missingPermissions.toTypedArray()) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.testTag("enable_all_permissions_button")
                        ) {
                            Text("Enable All", color = accent, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlass(
                            shape = RoundedCornerShape(20.dp),
                            baseColor = surface,
                            tintColor = accent,
                            borderColor = border,
                            enabled = isFrostedGlass,
                            fillAlpha = 0.82f,
                            tintAlpha = 0.06f,
                            borderAlpha = 0.58f
                        )
                ) {
                    // Microphone (Voice Input for Remote & AI Studio)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = null,
                                    tint = if (micGranted) EmeraldOnline else accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Microphone (Voice Input)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = fg
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Enables direct voice dictation & Gemini Live audio inside Remote and AI Studio",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (micGranted) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.testTag("permission_mic_granted_badge")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Microphone Active",
                                    tint = EmeraldOnline,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldOnline,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    onRequestRuntimePermissions(arrayOf(Manifest.permission.RECORD_AUDIO))
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("permission_mic_enable_button")
                            ) {
                                Text("Enable", color = accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // Camera (QR Pairing & Live Video Input)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.CameraAlt,
                                    contentDescription = null,
                                    tint = if (cameraGranted) EmeraldOnline else accent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Camera (QR & Live Video)",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = fg
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Used for QR workstation pairing and camera streaming in AI Studio",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        if (cameraGranted) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.testTag("permission_camera_granted_badge")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Camera Active",
                                    tint = EmeraldOnline,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "Active",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = EmeraldOnline,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    onRequestRuntimePermissions(arrayOf(Manifest.permission.CAMERA))
                                },
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                modifier = Modifier.testTag("permission_camera_enable_button")
                            ) {
                                Text("Enable", color = accent, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // File Access (Zero-Permission Native Picker & Downloads)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = EmeraldOnline,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Files, Photos & Downloads",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = fg
                                )
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Uses Android's built-in system picker & Downloads manager—full access with zero broad storage permissions required",
                                style = MaterialTheme.typography.bodyMedium,
                                color = secondaryFg
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.testTag("permission_files_ready_badge")
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Native File Access Ready",
                                tint = EmeraldOnline,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Ready",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldOnline,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // Open Android App Info (for permanently denied permissions or battery unrestricted)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onOpenAppSystemSettings() }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                            .testTag("open_system_app_permissions_button"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.SettingsApplications,
                                contentDescription = null,
                                tint = secondaryFg,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Open Android App Permissions & Battery Settings",
                                style = MaterialTheme.typography.bodyMedium,
                                color = fg
                            )
                        }
                        Text(
                            text = "Open",
                            style = MaterialTheme.typography.labelLarge,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // SECTION 2: Dark Theme / Preset & Color Customizer (Matching Screenshot 3 & 4)
            item {
                Text(
                    text = "Dark Theme",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = fg,
                    modifier = Modifier.padding(bottom = 10.dp)
                )

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlass(
                            shape = RoundedCornerShape(20.dp),
                            baseColor = surface,
                            tintColor = accent,
                            borderColor = border,
                            enabled = isFrostedGlass,
                            fillAlpha = 0.82f,
                            tintAlpha = 0.06f,
                            borderAlpha = 0.58f
                        )
                ) {
                    // Row 1: Preset [ ↺ ] [ Default Dark v ]
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Preset",
                            style = MaterialTheme.typography.titleMedium,
                            color = fg
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onResetPreset,
                                modifier = Modifier
                                    .size(48.dp)
                                    .testTag("reset_preset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = "Reset Preset Colors",
                                    tint = secondaryFg,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(6.dp))

                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(22.dp))
                                    .background(elevated)
                                    .border(1.dp, border, RoundedCornerShape(22.dp))
                                    .clickable { showPresetPickerDialog = true }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                                    .testTag("open_preset_picker_button"),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = settings.presetName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = fg
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Choose Theme Preset",
                                    tint = secondaryFg,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // Row 2: Background [ ⬛ # 070708 ]
                    HexColorSettingRow(
                        label = "Background",
                        hexValue = settings.backgroundHex,
                        swatchColor = parseHexColor(settings.backgroundHex, Color(0xFF070708)),
                        fg = fg,
                        secondaryFg = secondaryFg,
                        elevated = elevated,
                        border = border,
                        onClick = { editingColorField = "Background" }
                    )

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // Row 3: Foreground [ ⬜ # FFFFFF ]
                    HexColorSettingRow(
                        label = "Foreground",
                        hexValue = settings.foregroundHex,
                        swatchColor = parseHexColor(settings.foregroundHex, Color.White),
                        fg = fg,
                        secondaryFg = secondaryFg,
                        elevated = elevated,
                        border = border,
                        onClick = { editingColorField = "Foreground" }
                    )

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // Row 4: Accent [ 🟧 # FFC799 ]
                    HexColorSettingRow(
                        label = "Accent",
                        hexValue = settings.accentHex,
                        swatchColor = parseHexColor(settings.accentHex, Color(0xFFFFC799)),
                        fg = fg,
                        secondaryFg = secondaryFg,
                        elevated = elevated,
                        border = border,
                        onClick = { editingColorField = "Accent" }
                    )

                    HorizontalDivider(color = border, thickness = 1.dp)

                    // Row 5: Collapsible Code Preview matching Screenshot 3
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .clickable { isCodePreviewExpanded = !isCodePreviewExpanded }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Code Preview",
                                style = MaterialTheme.typography.bodyLarge,
                                color = secondaryFg
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = if (isCodePreviewExpanded) {
                                    Icons.Default.KeyboardArrowUp
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = "Toggle Code Preview",
                                tint = secondaryFg,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        AnimatedVisibility(visible = isCodePreviewExpanded) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(bg)
                                    .border(1.dp, border, RoundedCornerShape(12.dp))
                                    .padding(14.dp)
                            ) {
                                val codeFontSize = (settings.fontSizePx + 1).sp
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(color = secondaryFg)) {
                                            append("// Greet a user by name\n")
                                        }
                                        withStyle(SpanStyle(color = Color(0xFF56B6C2))) {
                                            append("const ")
                                        }
                                        withStyle(SpanStyle(color = accent)) {
                                            append("greet")
                                        }
                                        withStyle(SpanStyle(color = fg)) {
                                            append(" = (")
                                        }
                                        withStyle(SpanStyle(color = fg)) {
                                            append("name")
                                        }
                                        withStyle(SpanStyle(color = secondaryFg)) {
                                            append(": ")
                                        }
                                        withStyle(SpanStyle(color = accent)) {
                                            append("string")
                                        }
                                        withStyle(SpanStyle(color = fg)) {
                                            append(") => {\n  ")
                                        }
                                        withStyle(SpanStyle(color = Color(0xFF56B6C2))) {
                                            append("return ")
                                        }
                                        withStyle(SpanStyle(color = Color(0xFF98C379))) {
                                            append("`Hello, \${")
                                        }
                                        withStyle(SpanStyle(color = fg)) {
                                            append("name")
                                        }
                                        withStyle(SpanStyle(color = Color(0xFF98C379))) {
                                            append("}!`")
                                        }
                                        withStyle(SpanStyle(color = fg)) {
                                            append(";\n};")
                                        }
                                    },
                                    fontFamily = JetBrainsMonoFontFamily,
                                    fontSize = codeFontSize,
                                    lineHeight = (settings.fontSizePx + 8).sp
                                )
                            }
                        }
                    }
                }
            }

            // SECTION 3: About Developer (Clean, Professional Attribution Card)
            item {
                val initials = remember(settings.developerName) {
                    settings.developerName
                        .trim()
                        .split(Regex("\\s+"))
                        .filter { it.isNotEmpty() }
                        .take(2)
                        .joinToString("") { it.first().uppercase() }
                        .ifBlank { "DV" }
                }
                val cleanGithubHandle = settings.developerGithub.trim().removePrefix("@")

                Column {
                    Text(
                        text = "About Developer",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = fg,
                        modifier = Modifier.padding(bottom = 10.dp)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .frostedGlass(
                                shape = RoundedCornerShape(20.dp),
                                baseColor = surface,
                                tintColor = accent,
                                borderColor = border,
                                enabled = isFrostedGlass,
                                fillAlpha = 0.82f,
                                tintAlpha = 0.07f,
                                borderAlpha = 0.60f
                            )
                            .padding(16.dp)
                            .testTag("about_developer_card"),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(46.dp)
                                        .clip(CircleShape)
                                        .background(accent.copy(alpha = 0.16f))
                                        .border(1.2.dp, accent.copy(alpha = 0.55f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = initials,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = accent
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = settings.developerName,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = fg
                                    )
                                    Text(
                                        text = "Developer • Antigravity Pulse v1.0",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = secondaryFg
                                    )
                                }
                            }
                        }

                        HorizontalDivider(color = border, thickness = 1.dp)

                        // Email row (opens mail client)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(elevated)
                                .clickable {
                                    try {
                                        val intent = Intent(
                                            Intent.ACTION_SENDTO,
                                            Uri.parse("mailto:${settings.developerEmail.trim()}")
                                        )
                                        context.startActivity(intent)
                                    } catch (_: Exception) {
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("developer_email_row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = "Developer Email",
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Email",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryFg
                                )
                                Text(
                                    text = settings.developerEmail,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = fg
                                )
                            }
                        }

                        // GitHub row (opens GitHub profile in browser)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(elevated)
                                .clickable {
                                    if (cleanGithubHandle.isNotBlank()) {
                                        try {
                                            val intent = Intent(
                                                Intent.ACTION_VIEW,
                                                Uri.parse("https://github.com/$cleanGithubHandle")
                                            )
                                            context.startActivity(intent)
                                        } catch (_: Exception) {
                                        }
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                                .testTag("developer_github_row"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = "Developer GitHub",
                                tint = accent,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "GitHub",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = secondaryFg
                                )
                                Text(
                                    text = "@$cleanGithubHandle",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = accent,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal 1: High-Contrast Themed Preset Picker with Live Color Swatches
    if (showPresetPickerDialog) {
        Dialog(
            onDismissRequest = { showPresetPickerDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.72f))
                    .clickable { showPresetPickerDialog = false }
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                val pickerShape = RoundedCornerShape(22.dp)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .frostedGlass(
                            shape = pickerShape,
                            baseColor = surface,
                            tintColor = accent,
                            borderColor = border,
                            enabled = isFrostedGlass,
                            fillAlpha = 0.92f,
                            tintAlpha = 0.08f,
                            borderAlpha = 0.65f
                        )
                        .testTag("preset_radio_list")
                ) {
                    items(OfficialAntigravityPresets) { preset ->
                        val isSelected = preset.name == settings.presetName
                        val presetBgColor = parseHexColor(preset.backgroundHex, Color.Black)
                        val presetAccentColor = parseHexColor(preset.accentHex, accent)
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) accent.copy(alpha = 0.14f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        onSelectPreset(preset)
                                        showPresetPickerDialog = false
                                    }
                                    .padding(horizontal = 20.dp, vertical = 14.dp)
                                    .testTag("preset_option_${preset.name}"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(presetBgColor)
                                            .border(2.dp, presetAccentColor, CircleShape)
                                    )
                                    Text(
                                        text = preset.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) accent else fg
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSelectPreset(preset)
                                        showPresetPickerDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = accent,
                                        unselectedColor = secondaryFg
                                    )
                                )
                            }
                            HorizontalDivider(
                                color = border.copy(alpha = 0.55f),
                                thickness = 1.dp
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal 2: Custom Hex Color Input & Quick Swatches
    editingColorField?.let { fieldName ->
        val initialHex = when (fieldName) {
            "Background" -> settings.backgroundHex
            "Foreground" -> settings.foregroundHex
            else -> settings.accentHex
        }
        var hexInput by remember(fieldName) {
            mutableStateOf(initialHex.removePrefix("#"))
        }

        val quickSwatches = when (fieldName) {
            "Background" -> listOf("#000000", "#070708", "#0B0E14", "#101010", "#1E1E2E", "#282A36")
            "Foreground" -> listOf("#FFFFFF", "#F1F5F9", "#CDD6F4", "#D3C6AA", "#ECEFF4", "#C0CAF5")
            else -> listOf("#FFC799", "#38BDF8", "#CBA6F7", "#BD93F9", "#A7C080", "#E78A4E", "#A6E22E", "#88C0D0")
        }

        AlertDialog(
            onDismissRequest = { editingColorField = null },
            containerColor = surface,
            title = {
                Text(
                    text = "Customize $fieldName Color",
                    style = MaterialTheme.typography.titleLarge,
                    color = fg
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = hexInput,
                        onValueChange = { raw ->
                            hexInput = raw.uppercase()
                                .filter { c -> c in '0'..'9' || c in 'A'..'F' }
                                .take(6)
                        },
                        label = { Text("Hex Color (#RRGGBB)") },
                        prefix = { Text("# ") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = "Quick Swatches",
                        style = MaterialTheme.typography.labelLarge,
                        color = secondaryFg
                    )

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(quickSwatches) { swatchHex ->
                            val swatchColor = parseHexColor(swatchHex, accent)
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(swatchColor)
                                    .border(1.dp, border, RoundedCornerShape(8.dp))
                                    .clickable {
                                        hexInput = swatchHex.removePrefix("#")
                                    }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val safeHex = hexInput.uppercase()
                            .filter { c -> c in '0'..'9' || c in 'A'..'F' }
                            .padEnd(6, '0')
                            .take(6)
                        val formatted = "#$safeHex"
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
                        editingColorField = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = Color(0xFF070708)
                    )
                ) {
                    Text("Apply Hex", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { editingColorField = null }) {
                    Text("Cancel", color = secondaryFg)
                }
            }
        )
    }
}

@Composable
private fun ThemeModeIconPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    selected: Boolean,
    activeBg: Color,
    activeBorder: Color,
    fg: Color,
    secondaryFg: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 44.dp, height = 34.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) activeBg else Color.Transparent)
            .then(
                if (selected) Modifier.border(1.dp, activeBorder, RoundedCornerShape(18.dp))
                else Modifier
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = if (selected) fg else secondaryFg,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun ContrastOptionPill(
    label: String,
    selected: Boolean,
    activeBg: Color,
    activeBorder: Color,
    fg: Color,
    secondaryFg: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) activeBg else Color.Transparent)
            .then(
                if (selected) Modifier.border(1.dp, activeBorder, RoundedCornerShape(18.dp))
                else Modifier
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) fg else secondaryFg
        )
    }
}

@Composable
private fun HexColorSettingRow(
    label: String,
    hexValue: String,
    swatchColor: Color,
    fg: Color,
    secondaryFg: Color,
    elevated: Color,
    border: Color,
    onClick: () -> Unit
) {
    val cleanHex = hexValue.uppercase().removePrefix("#")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = fg
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(elevated)
                .border(1.dp, border, RoundedCornerShape(24.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp)
                .testTag("hex_color_pill_${label.lowercase()}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(20.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(swatchColor)
                    .border(1.dp, border, RoundedCornerShape(5.dp))
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = "#",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 14.sp,
                color = secondaryFg
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = cleanHex,
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 14.sp,
                color = fg
            )
        }
    }
}
