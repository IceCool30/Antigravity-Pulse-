package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

val AntigravityMaterialYouShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun AntigravityPulseTheme(
    appearance: AppearanceSettings = AppearanceSettings(),
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val isDark = when (appearance.themeMode) {
        AntigravityThemeMode.SYSTEM -> isSystemInDarkTheme()
        AntigravityThemeMode.LIGHT -> false
        AntigravityThemeMode.DARK -> true
    }

    val useMaterialYouDynamic = appearance.isMaterialYouDynamicEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val dynamicScheme = if (useMaterialYouDynamic) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        null
    }

    // Blend Material You dynamic wallpaper accent seamlessly while keeping pure #000000 OLED black
    val effectiveAppearance = if (isDark) {
        appearance.copy(
            themeMode = AntigravityThemeMode.DARK,
            accentHex = dynamicScheme?.primary?.toHexString() ?: appearance.accentHex
        )
    } else {
        appearance.copy(
            themeMode = AntigravityThemeMode.LIGHT,
            accentHex = dynamicScheme?.primary?.toHexString() ?: appearance.accentHex
        )
    }

    val bg = effectiveAppearance.backgroundColor
    val surface = effectiveAppearance.surfaceColor
    val elevated = effectiveAppearance.elevatedSurfaceColor
    val fg = effectiveAppearance.foregroundColor
    val secondaryFg = effectiveAppearance.secondaryTextColor
    val accent = effectiveAppearance.accentColor
    val border = effectiveAppearance.borderColor

    val colorScheme = if (dynamicScheme != null) {
        dynamicScheme.copy(
            primary = accent,
            background = bg,
            onBackground = fg,
            surface = if (isDark) surface else dynamicScheme.surface,
            onSurface = fg,
            surfaceVariant = if (isDark) elevated else dynamicScheme.surfaceVariant,
            onSurfaceVariant = secondaryFg,
            outline = border,
            error = CrimsonError,
            onError = Color.White
        )
    } else if (isDark) {
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
            shapes = AntigravityMaterialYouShapes,
            content = content
        )
    }
}

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    AntigravityPulseTheme(
        appearance = AppearanceSettings(
            themeMode = if (darkTheme) AntigravityThemeMode.DARK else AntigravityThemeMode.LIGHT,
            isMaterialYouDynamicEnabled = dynamicColor
        ),
        content = content
    )
}
