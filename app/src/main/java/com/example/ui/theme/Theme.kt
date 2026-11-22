package com.example.ui.theme

import android.app.Activity
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
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

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
    val view = LocalView.current
    val isDark = when (appearance.themeMode) {
        AntigravityThemeMode.SYSTEM -> isSystemInDarkTheme()
        AntigravityThemeMode.LIGHT -> false
        AntigravityThemeMode.DARK -> true
    }

    // Native Android edge-to-edge system bar icon contrast synced automatically with Light/Dark mode
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            val controller = WindowCompat.getInsetsController(window, view)
            controller.isAppearanceLightStatusBars = !isDark
            controller.isAppearanceLightNavigationBars = !isDark
        }
    }

    val useMaterialYouDynamic = appearance.isMaterialYouDynamicEnabled &&
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

    val dynamicScheme = remember(context, isDark, useMaterialYouDynamic) {
        if (useMaterialYouDynamic) {
            if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else {
            null
        }
    }

    // Blend Material You dynamic wallpaper accent seamlessly while keeping pure #000000 OLED black
    val effectiveAppearance = remember(appearance, isDark, dynamicScheme) {
        appearance.copy(
            themeMode = if (isDark) AntigravityThemeMode.DARK else AntigravityThemeMode.LIGHT,
            accentHex = dynamicScheme?.primary?.toHexString() ?: appearance.accentHex
        )
    }

    val colorScheme = remember(effectiveAppearance, isDark, dynamicScheme) {
        val bg = effectiveAppearance.backgroundColor
        val surface = effectiveAppearance.surfaceColor
        val elevated = effectiveAppearance.elevatedSurfaceColor
        val fg = effectiveAppearance.foregroundColor
        val secondaryFg = effectiveAppearance.secondaryTextColor
        val accent = effectiveAppearance.accentColor
        val onAccent = effectiveAppearance.onAccentColor
        val border = effectiveAppearance.borderColor
        val success = effectiveAppearance.successColor
        val error = effectiveAppearance.errorColor

        if (dynamicScheme != null) {
            dynamicScheme.copy(
                primary = accent,
                onPrimary = onAccent,
                background = bg,
                onBackground = fg,
                surface = if (isDark) surface else dynamicScheme.surface,
                onSurface = fg,
                surfaceVariant = if (isDark) elevated else dynamicScheme.surfaceVariant,
                onSurfaceVariant = secondaryFg,
                outline = border,
                tertiary = success,
                error = error,
                onError = Color.White
            )
        } else if (isDark) {
            darkColorScheme(
                primary = accent,
                onPrimary = onAccent,
                primaryContainer = surface,
                onPrimaryContainer = accent,
                secondary = NeuralViolet,
                onSecondary = Color.White,
                secondaryContainer = elevated,
                onSecondaryContainer = fg,
                tertiary = success,
                onTertiary = Color(0xFF070708),
                background = bg,
                onBackground = fg,
                surface = surface,
                onSurface = fg,
                surfaceVariant = elevated,
                onSurfaceVariant = secondaryFg,
                outline = border,
                error = error,
                onError = Color.White
            )
        } else {
            lightColorScheme(
                primary = accent,
                onPrimary = onAccent,
                primaryContainer = surface,
                onPrimaryContainer = Color(0xFF0F172A),
                secondary = Color(0xFF6D28D9),
                onSecondary = Color.White,
                secondaryContainer = elevated,
                onSecondaryContainer = fg,
                tertiary = success,
                onTertiary = Color.White,
                background = bg,
                onBackground = fg,
                surface = surface,
                onSurface = fg,
                surfaceVariant = elevated,
                onSurfaceVariant = secondaryFg,
                outline = border,
                error = error,
                onError = Color.White
            )
        }
    }

    val dynamicTypography = remember(effectiveAppearance.fontFamilyName, effectiveAppearance.fontSizePx) {
        buildDynamicTypography(
            fontName = effectiveAppearance.fontFamilyName,
            baseFontSizePx = effectiveAppearance.fontSizePx
        )
    }

    CompositionLocalProvider(LocalAppearanceSettings provides effectiveAppearance) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = dynamicTypography,
            shapes = AntigravityMaterialYouShapes,
            content = content
        )
    }
}
