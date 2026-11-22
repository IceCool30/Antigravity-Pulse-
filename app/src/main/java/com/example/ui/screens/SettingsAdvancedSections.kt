package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.notifications.PulseNotificationManager
import com.example.ui.theme.AppearanceSettings
import com.example.ui.theme.AvailableAntigravityFonts
import com.example.ui.theme.AvailableFontSizesPx
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.LocalAppearanceSettings
import com.example.ui.theme.parseHexColor

@Composable
internal fun SettingsCustomizeAppearanceBlock(
    settings: AppearanceSettings,
    bg: Color,
    elevated: Color,
    border: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    onUpdateSettings: ((AppearanceSettings) -> AppearanceSettings) -> Unit,
    onEditColorField: (String) -> Unit
) {
    var isCustomizeExpanded by rememberSaveable { mutableStateOf(false) }
    var isCodePreviewExpanded by rememberSaveable { mutableStateOf(false) }
    var showFontDropdown by remember { mutableStateOf(false) }
    var showFontSizeDropdown by remember { mutableStateOf(false) }

    val swatchBg = parseHexColor(settings.backgroundHex, Color(0xFF000000))
    val swatchFg = parseHexColor(settings.foregroundHex, Color.White)
    val swatchAccent = parseHexColor(settings.accentHex, Color(0xFFFFC799))

    Column(modifier = Modifier.fillMaxWidth()) {
        // Collapsible trigger row (collapsed by default)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 54.dp)
                .clickable { isCustomizeExpanded = !isCustomizeExpanded }
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .testTag("customize_appearance_toggle"),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(19.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Colors, Font & Preview",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    color = fg
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                ColorPreviewDot(color = swatchBg, border = border)
                ColorPreviewDot(color = swatchFg, border = border)
                ColorPreviewDot(color = swatchAccent, border = border)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    imageVector = if (isCustomizeExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (isCustomizeExpanded) {
                        "Collapse Colors, Font & Preview"
                    } else {
                        "Expand Colors, Font & Preview"
                    },
                    tint = secondaryFg,
                    modifier = Modifier.size(20.dp)
                )
            }
        }

        AnimatedVisibility(visible = isCustomizeExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("customize_appearance_content")
            ) {
                HorizontalDivider(color = border.copy(alpha = 0.7f), thickness = 1.dp)

                // Custom Hex Rows
                HexColorSettingRow(
                    label = "Background",
                    hexValue = settings.backgroundHex,
                    swatchColor = swatchBg,
                    fg = fg,
                    secondaryFg = secondaryFg,
                    elevated = elevated,
                    border = border,
                    onClick = { onEditColorField("Background") }
                )

                HorizontalDivider(color = border.copy(alpha = 0.55f), thickness = 1.dp)

                HexColorSettingRow(
                    label = "Foreground",
                    hexValue = settings.foregroundHex,
                    swatchColor = swatchFg,
                    fg = fg,
                    secondaryFg = secondaryFg,
                    elevated = elevated,
                    border = border,
                    onClick = { onEditColorField("Foreground") }
                )

                HorizontalDivider(color = border.copy(alpha = 0.55f), thickness = 1.dp)

                HexColorSettingRow(
                    label = "Accent",
                    hexValue = settings.accentHex,
                    swatchColor = swatchAccent,
                    fg = fg,
                    secondaryFg = secondaryFg,
                    elevated = elevated,
                    border = border,
                    onClick = { onEditColorField("Accent") }
                )

                HorizontalDivider(color = border.copy(alpha = 0.55f), thickness = 1.dp)

                // Font Family Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Font",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = fg
                    )

                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(elevated)
                                .border(1.dp, border, RoundedCornerShape(20.dp))
                                .clickable { showFontDropdown = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("appearance_font_selector"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = settings.fontFamilyName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = fg
                            )
                            Spacer(modifier = Modifier.width(8.dp))
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

                HorizontalDivider(color = border.copy(alpha = 0.55f), thickness = 1.dp)

                // Font Size Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Font Size",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        color = fg
                    )

                    Box {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(elevated)
                                .border(1.dp, border, RoundedCornerShape(20.dp))
                                .clickable { showFontSizeDropdown = true }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                                .testTag("appearance_fontsize_selector"),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${settings.fontSizePx}px",
                                style = MaterialTheme.typography.bodyMedium,
                                color = fg
                            )
                            Spacer(modifier = Modifier.width(10.dp))
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

                HorizontalDivider(color = border.copy(alpha = 0.55f), thickness = 1.dp)

                // Material You Dynamic Color (Title-only; OFF by default so #FFC799 applies)
                SettingsToggleRow(
                    icon = Icons.Default.Palette,
                    title = "Material You Dynamic Color",
                    checked = settings.isMaterialYouDynamicEnabled,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(isMaterialYouDynamicEnabled = enabled) }
                    },
                    bg = bg,
                    fg = fg,
                    secondaryFg = secondaryFg,
                    accent = accent,
                    testTag = "material_you_toggle"
                )

                HorizontalDivider(color = border.copy(alpha = 0.55f), thickness = 1.dp)

                // Frosted Glass Surfaces (Title-only)
                SettingsToggleRow(
                    icon = Icons.Default.AutoAwesome,
                    title = "Frosted Glass Surfaces",
                    checked = settings.isFrostedGlassEnabled,
                    onCheckedChange = { enabled ->
                        onUpdateSettings { it.copy(isFrostedGlassEnabled = enabled) }
                    },
                    bg = bg,
                    fg = fg,
                    secondaryFg = secondaryFg,
                    accent = accent,
                    testTag = "frosted_glass_toggle"
                )

                HorizontalDivider(color = border.copy(alpha = 0.55f), thickness = 1.dp)

                // Collapsible Live Code Preview (collapsed by default)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 36.dp)
                            .clickable { isCodePreviewExpanded = !isCodePreviewExpanded }
                            .testTag("code_preview_toggle"),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Live Code Preview",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = secondaryFg
                        )
                        Icon(
                            imageVector = if (isCodePreviewExpanded) {
                                Icons.Default.KeyboardArrowUp
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = "Toggle Live Code Preview",
                            tint = secondaryFg,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    AnimatedVisibility(visible = isCodePreviewExpanded) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 10.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(bg)
                                .border(1.dp, border, RoundedCornerShape(12.dp))
                                .padding(14.dp)
                                .testTag("code_preview_box")
                        ) {
                            val activeTheme = LocalAppearanceSettings.current
                            val codeFontSize = (settings.fontSizePx + 1).sp
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = secondaryFg)) {
                                        append("// Greet a user by name\n")
                                    }
                                    withStyle(SpanStyle(color = activeTheme.codeKeywordColor)) {
                                        append("const ")
                                    }
                                    withStyle(SpanStyle(color = accent)) {
                                        append("greet")
                                    }
                                    withStyle(SpanStyle(color = fg)) {
                                        append(" = (name")
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
                                    withStyle(SpanStyle(color = activeTheme.codeKeywordColor)) {
                                        append("return ")
                                    }
                                    withStyle(SpanStyle(color = activeTheme.codeStringColor)) {
                                        append("`Hello, \${")
                                    }
                                    withStyle(SpanStyle(color = fg)) {
                                        append("name")
                                    }
                                    withStyle(SpanStyle(color = activeTheme.codeStringColor)) {
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

@Composable
private fun ColorPreviewDot(
    color: Color,
    border: Color
) {
    Box(
        modifier = Modifier
            .size(13.dp)
            .clip(CircleShape)
            .background(color)
            .border(1.dp, border, CircleShape)
    )
}

@Composable
internal fun SettingsPermissionsSection(
    permissionsRefreshTick: Int,
    surface: Color,
    border: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    isFrostedGlass: Boolean,
    onRequestRuntimePermissions: (Array<String>) -> Unit,
    onOpenAppSystemSettings: () -> Unit
) {
    val context = LocalContext.current

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

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSectionHeader(
            title = "Device Permissions",
            secondaryFg = secondaryFg,
            trailing = if (missingPermissions.isNotEmpty()) {
                {
                    OutlinedButton(
                        onClick = { onRequestRuntimePermissions(missingPermissions.toTypedArray()) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.testTag("enable_all_permissions_button")
                    ) {
                        Text(
                            text = "Enable All",
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                null
            }
        )

        SettingsCard(
            surface = surface,
            accent = accent,
            border = border,
            isFrostedGlass = isFrostedGlass
        ) {
            CompactPermissionStatusRow(
                icon = Icons.Default.Mic,
                label = "Microphone",
                isGranted = micGranted,
                grantedLabel = "Active",
                grantedTag = "permission_mic_granted_badge",
                enableTag = "permission_mic_enable_button",
                fg = fg,
                accent = accent,
                onEnable = {
                    onRequestRuntimePermissions(arrayOf(Manifest.permission.RECORD_AUDIO))
                }
            )

            HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

            CompactPermissionStatusRow(
                icon = Icons.Default.CameraAlt,
                label = "Camera",
                isGranted = cameraGranted,
                grantedLabel = "Active",
                grantedTag = "permission_camera_granted_badge",
                enableTag = "permission_camera_enable_button",
                fg = fg,
                accent = accent,
                onEnable = {
                    onRequestRuntimePermissions(arrayOf(Manifest.permission.CAMERA))
                }
            )

            HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

            CompactPermissionStatusRow(
                icon = Icons.Default.FolderOpen,
                label = "Files & Downloads",
                isGranted = true,
                grantedLabel = "Ready",
                grantedTag = "permission_files_ready_badge",
                enableTag = null,
                fg = fg,
                accent = accent,
                onEnable = null
            )

            HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 50.dp)
                    .clickable(onClick = onOpenAppSystemSettings)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
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
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "System App Settings",
                        style = MaterialTheme.typography.bodyMedium,
                        color = fg
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Open",
                        style = MaterialTheme.typography.labelMedium,
                        color = accent,
                        fontWeight = FontWeight.SemiBold
                    )
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Open System App Settings",
                        tint = accent,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun CompactPermissionStatusRow(
    icon: ImageVector,
    label: String,
    isGranted: Boolean,
    grantedLabel: String,
    grantedTag: String,
    enableTag: String?,
    fg: Color,
    accent: Color,
    onEnable: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val grantedGreen = LocalAppearanceSettings.current.successColor
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isGranted) grantedGreen else accent,
                modifier = Modifier.size(19.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = fg
            )
        }

        if (isGranted) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(grantedGreen.copy(alpha = 0.12f))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
                    .testTag(grantedTag)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "$label $grantedLabel",
                    tint = grantedGreen,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = grantedLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = grantedGreen,
                    fontWeight = FontWeight.Bold
                )
            }
        } else if (onEnable != null && enableTag != null) {
            OutlinedButton(
                onClick = onEnable,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                modifier = Modifier.testTag(enableTag)
            ) {
                Text(
                    text = "Enable",
                    style = MaterialTheme.typography.labelMedium,
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
internal fun SettingsDeveloperCard(
    settings: AppearanceSettings,
    surface: Color,
    elevated: Color,
    border: Color,
    fg: Color,
    secondaryFg: Color,
    accent: Color,
    isFrostedGlass: Boolean
) {
    val context = LocalContext.current
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

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSectionHeader(
            title = "About Developer",
            secondaryFg = secondaryFg
        )

        SettingsCard(
            surface = surface,
            accent = accent,
            border = border,
            isFrostedGlass = isFrostedGlass,
            modifier = Modifier.testTag("about_developer_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(accent.copy(alpha = 0.14f))
                            .border(1.dp, accent.copy(alpha = 0.5f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = initials,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = accent
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = settings.developerName,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = fg
                        )
                        Text(
                            text = "Developer · Antigravity Pulse",
                            style = MaterialTheme.typography.labelSmall,
                            color = secondaryFg
                        )
                    }
                }

                HorizontalDivider(color = border.copy(alpha = 0.6f), thickness = 1.dp)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
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
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = settings.developerEmail,
                        style = MaterialTheme.typography.bodyMedium,
                        color = fg
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(elevated)
                        .clickable {
                            if (cleanGithubHandle.isNotBlank()) {
                                try {
                                    val intent = Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://github.com/$cleanGithubHandle")
                                    ).addCategory(Intent.CATEGORY_BROWSABLE)
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
                        modifier = Modifier.size(17.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
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
