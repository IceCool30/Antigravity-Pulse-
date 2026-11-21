package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.AntigravityThemePreset
import com.example.ui.theme.AppearanceSettings
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.OfficialAntigravityPresets
import com.example.ui.theme.frostedGlass
import com.example.ui.theme.parseHexColor

@Composable
internal fun SettingsSectionHeader(
    title: String,
    secondaryFg: Color,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.1.sp,
            color = secondaryFg
        )
        trailing?.invoke()
    }
}

@Composable
internal fun SettingsCard(
    surface: Color,
    accent: Color,
    border: Color,
    isFrostedGlass: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .frostedGlass(
                shape = RoundedCornerShape(18.dp),
                baseColor = surface,
                tintColor = accent,
                borderColor = border,
                enabled = isFrostedGlass,
                fillAlpha = 0.84f,
                tintAlpha = 0.05f,
                borderAlpha = 0.52f
            ),
        content = content
    )
}

@Composable
internal fun SettingsToggleRow(
    icon: ImageVector,
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    bg: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    testTag: String,
    hint: String? = null,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) accent else secondaryFg.copy(alpha = 0.5f),
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = if (enabled) fg else secondaryFg
                )
                if (!hint.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryFg
                    )
                }
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            enabled = enabled,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = bg,
                checkedTrackColor = accent
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
internal fun ThemeModeIconPill(
    icon: ImageVector,
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
            .size(width = 44.dp, height = 36.dp)
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
internal fun ContrastOptionPill(
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
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) fg else secondaryFg
        )
    }
}

@Composable
internal fun HexColorSettingRow(
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
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = fg
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(elevated)
                .border(1.dp, border, RoundedCornerShape(20.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 7.dp)
                .testTag("hex_color_pill_${label.lowercase()}"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(swatchColor)
                    .border(1.dp, border, RoundedCornerShape(4.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "#$cleanHex",
                fontFamily = JetBrainsMonoFontFamily,
                fontSize = 13.sp,
                color = fg
            )
        }
    }
}

@Composable
internal fun SettingsPresetPickerDialog(
    selectedPresetName: String,
    surface: Color,
    border: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    isFrostedGlass: Boolean,
    onSelectPreset: (AntigravityThemePreset) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.72f))
                .clickable(onClick = onDismiss)
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
                        fillAlpha = 0.94f,
                        tintAlpha = 0.08f,
                        borderAlpha = 0.65f
                    )
                    .testTag("preset_radio_list")
            ) {
                items(OfficialAntigravityPresets) { preset ->
                    val isSelected = preset.name == selectedPresetName
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
                                    onDismiss()
                                }
                                .padding(horizontal = 18.dp, vertical = 12.dp)
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
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(presetBgColor)
                                        .border(2.dp, presetAccentColor, CircleShape)
                                )
                                Text(
                                    text = preset.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) accent else fg
                                )
                            }
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    onSelectPreset(preset)
                                    onDismiss()
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = accent,
                                    unselectedColor = secondaryFg
                                )
                            )
                        }
                        HorizontalDivider(
                            color = border.copy(alpha = 0.5f),
                            thickness = 1.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SettingsHexColorEditorDialog(
    fieldName: String,
    settings: AppearanceSettings,
    surface: Color,
    border: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    onApplyHex: (String) -> Unit,
    onDismiss: () -> Unit
) {
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
        onDismissRequest = onDismiss,
        containerColor = surface,
        title = {
            Text(
                text = "Customize $fieldName",
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
                                .clickable { hexInput = swatchHex.removePrefix("#") }
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
                    onApplyHex("#$safeHex")
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = accent,
                    contentColor = Color(0xFF000000)
                )
            ) {
                Text("Apply", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = secondaryFg)
            }
        }
    )
}
