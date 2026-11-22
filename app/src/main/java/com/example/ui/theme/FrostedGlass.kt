package com.example.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lightweight, GPU-friendly subtle Frosted Glass surface modifier designed for 60/120Hz
 * rendering over OLED dark (#000000) and live WebView surfaces without frame-rate drops.
 *
 * Combines a translucent tinted base, a delicate top-lit frost sheen, and a luminous
 * diagonal specular hairline border. Falls back cleanly to a crisp Material You tonal
 * card when [enabled] is false.
 */
fun Modifier.frostedGlass(
    shape: Shape,
    baseColor: Color,
    tintColor: Color,
    borderColor: Color,
    enabled: Boolean = true,
    fillAlpha: Float = 0.78f,
    tintAlpha: Float = 0.06f,
    borderAlpha: Float = 0.55f,
    borderWidth: Dp = 0.85.dp
): Modifier {
    if (!enabled) {
        return this
            .clip(shape)
            .background(baseColor)
            .border(1.dp, borderColor, shape)
    }

    val isDarkBase = (baseColor.red * 0.299f + baseColor.green * 0.587f + baseColor.blue * 0.114f) < 0.5f
    val effectiveFillAlpha = if (isDarkBase) {
        fillAlpha.coerceIn(0.25f, 0.96f)
    } else {
        // Keep daylight glass crisp and legible over bright canvases and WebViews
        fillAlpha.coerceAtLeast(0.88f).coerceAtMost(0.98f)
    }

    val topSheen = if (isDarkBase) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.26f)
    val midSheen = if (isDarkBase) {
        tintColor.copy(alpha = tintAlpha.coerceIn(0f, 0.22f))
    } else {
        tintColor.copy(alpha = (tintAlpha * 0.55f).coerceIn(0f, 0.07f))
    }
    val bottomSheen = if (isDarkBase) {
        Color.Black.copy(alpha = 0.12f)
    } else {
        // Delicate cool-slate bottom shade gives physical elevation instead of white-on-white glare
        Color(0xFF0F172A).copy(alpha = 0.035f)
    }

    val borderBrush = if (isDarkBase) {
        Brush.linearGradient(
            colors = listOf(
                Color.White.copy(alpha = (borderAlpha * 0.42f).coerceIn(0.08f, 0.40f)),
                tintColor.copy(alpha = (borderAlpha * 0.38f).coerceIn(0.08f, 0.42f)),
                borderColor.copy(alpha = (borderAlpha * 0.60f).coerceIn(0.12f, 0.65f))
            )
        )
    } else {
        // Never start light-mode borders with white (which vanishes on white backgrounds)
        Brush.linearGradient(
            colors = listOf(
                borderColor.copy(alpha = borderAlpha.coerceIn(0.52f, 0.84f)),
                tintColor.copy(alpha = (borderAlpha * 0.52f).coerceIn(0.26f, 0.62f)),
                Color(0xFF0F172A).copy(alpha = (borderAlpha * 0.26f).coerceIn(0.14f, 0.28f))
            )
        )
    }

    return this
        .clip(shape)
        .background(baseColor.copy(alpha = effectiveFillAlpha))
        .background(
            Brush.verticalGradient(
                colors = listOf(topSheen, midSheen, bottomSheen)
            )
        )
        .border(borderWidth, borderBrush, shape)
}

/**
 * Converts a Compose [Color] to an uppercase 6-digit `#RRGGBB` hex string.
 */
fun Color.toHexString(): String {
    val rgb = toArgb() and 0x00FFFFFF
    return String.format("#%06X", rgb)
}
