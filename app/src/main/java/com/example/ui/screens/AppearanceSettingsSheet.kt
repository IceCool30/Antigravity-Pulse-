package com.example.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AntigravityContrastMode
import com.example.ui.theme.AntigravityThemeMode
import com.example.ui.theme.AntigravityThemePreset
import com.example.ui.theme.AppearanceSettings
import com.example.ui.theme.AvailableAntigravityFonts
import com.example.ui.theme.AvailableFontSizesPx
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OfficialAntigravityPresets
import com.example.ui.theme.parseHexColor

/**
 * Full-screen Antigravity Remote Appearance & Theme Customizer matching the user's screenshots:
 * - Theme: System / Light / Dark icon switcher
 * - Contrast: Default / Strong pill switcher
 * - Font: Google Sans, Space Grotesk, Inter, JetBrains Mono, Fira Code, Roboto Mono
 * - Font Size ("Appears larger on mobile"): 12px .. 18px
 * - Fullscreen Mode toggle (Hide Top & Bottom Bars)
 * - Dark Theme Preset selector modal (Default Dark, Legacy Dark, Catppuccin, Dracula, Everforest,
 *   Gruvbox Material, Monokai, Nord, One Dark Pro, Tokyo Night, Solarized Dark, Vesper) + Reset button
 * - Background (#070708), Foreground (#FFFFFF), Accent (#FFC799) custom hex editors
 * - Collapsible live Code Preview (`const greet = (name: string) => ...`)
 */
@Composable
fun AppearanceSettingsSheet(
    settings: AppearanceSettings,
    onUpdateSettings: ((AppearanceSettings) -> AppearanceSettings) -> Unit,
    onSelectPreset: (AntigravityThemePreset) -> Unit,
    onResetPreset: () -> Unit,
    onClose: () -> Unit
) {
    BackHandler(onBack = onClose)

    val bg = settings.backgroundColor
    val surface = settings.surfaceColor
    val elevated = settings.elevatedSurfaceColor
    val border = settings.borderColor
    val fg = settings.foregroundColor
    val secondaryFg = settings.secondaryTextColor
    val accent = settings.accentColor

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
        // Top Drag Handle + "< Back" Header Bar matching Screenshot 3
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
                    .clickable { onClose() }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
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
                        .clip(RoundedCornerShape(18.dp))
                        .background(surface)
                        .border(1.dp, border, RoundedCornerShape(18.dp))
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
                        .clip(RoundedCornerShape(18.dp))
                        .background(surface)
                        .border(1.dp, border, RoundedCornerShape(18.dp))
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
                                    .size(36.dp)
                                    .testTag("reset_preset_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Replay,
                                    contentDescription = "Reset Preset Colors",
                                    tint = secondaryFg,
                                    modifier = Modifier.size(18.dp)
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
        }
    }

    // Modal 1: Full-Height Radio-Button Preset Picker matching Screenshot 4 (`Screenshot_20261118-112636_62bed107.jpg`)
    if (showPresetPickerDialog) {
        Dialog(
            onDismissRequest = { showPresetPickerDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .clickable { showPresetPickerDialog = false }
                    .padding(horizontal = 20.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF3C3C3C))
                        .border(1.dp, Color(0xFF565656), RoundedCornerShape(8.dp))
                        .testTag("preset_radio_list")
                ) {
                    items(OfficialAntigravityPresets) { preset ->
                        val isSelected = preset.name == settings.presetName
                        Column {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        if (isSelected) Color.White.copy(alpha = 0.08f)
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
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Normal,
                                    color = Color.White
                                )
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        onSelectPreset(preset)
                                        showPresetPickerDialog = false
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = Color(0xFF7CE8FF),
                                        unselectedColor = Color(0xFFB0B0B0)
                                    )
                                )
                            }
                            HorizontalDivider(
                                color = Color(0xFF2B2B2B),
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
            "Background" -> listOf("#070708", "#0B0E14", "#101010", "#1E1E2E", "#282A36", "#1A1B26")
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
                        onValueChange = { hexInput = it.uppercase().take(6) },
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        quickSwatches.forEach { swatchHex ->
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
                        val formatted = "#${hexInput.trim().removePrefix("#").padEnd(6, '0').take(6)}"
                        onUpdateSettings { current ->
                            when (fieldName) {
                                "Background" -> current.copy(backgroundHex = formatted)
                                "Foreground" -> current.copy(foregroundHex = formatted)
                                else -> current.copy(accentHex = formatted)
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
