package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

@Composable
fun AntigravityPulseTheme(
    appearance: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit
) {
    val isDark = when (appearance.themeMode) {
        AntigravityThemeMode.SYSTEM -> isSystemInDarkTheme()
        AntigravityThemeMode.LIGHT -> false
        AntigravityThemeMode.DARK -> true
    }

    val effectiveAppearance = if (isDark) {
        appearance.copy(themeMode = AntigravityThemeMode.DARK)
    } else {
        appearance.copy(themeMode = AntigravityThemeMode.LIGHT)
    }

    val bg = effectiveAppearance.backgroundColor
    val surface = effectiveAppearance.surfaceColor
    val elevated = effectiveAppearance.elevatedSurfaceColor
    val fg = effectiveAppearance.foregroundColor
    val secondaryFg = effectiveAppearance.secondaryTextColor
    val accent = effectiveAppearance.accentColor
    val border = effectiveAppearance.borderColor

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = accent,
            onPrimary = Color(0xFF070708),
            primaryContainer = surface,
            onPrimaryContainer = accent,
            secondary = NeuralViolet,
            onSecondary = Color.White,
            secondaryContainer = elevated,
            onSecondaryContainer = fg,
            tertiary = EmeraldOnline,
            onTertiary = Color(0xFF070708),
            background = bg,
            onBackground = fg,
            surface = surface,
            onSurface = fg,
            surfaceVariant = elevated,
            onSurfaceVariant = secondaryFg,
            outline = border,
            error = CrimsonError,
            onError = Color.White
        )
    } else {
        lightColorScheme(
            primary = accent,
            onPrimary = Color(0xFF070708),
            primaryContainer = surface,
            onPrimaryContainer = Color(0xFF0F172A),
            secondary = NeuralViolet,
            onSecondary = Color.White,
            secondaryContainer = elevated,
            onSecondaryContainer = fg,
            tertiary = EmeraldOnline,
            onTertiary = Color.White,
            background = bg,
            onBackground = fg,
            surface = surface,
            onSurface = fg,
            surfaceVariant = elevated,
            onSurfaceVariant = secondaryFg,
            outline = border,
            error = CrimsonError,
            onError = Color.White
        )
    }

    val dynamicTypography = buildDynamicTypography(
        fontName = effectiveAppearance.fontFamilyName,
        baseFontSizePx = effectiveAppearance.fontSizePx
    )

    CompositionLocalProvider(LocalAppearanceSettings provides effectiveAppearance) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = dynamicTypography,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    AntigravityPulseTheme(content = content)
}
