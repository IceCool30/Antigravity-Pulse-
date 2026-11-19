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
    val topSheen = if (isDarkBase) Color.White.copy(alpha = 0.075f) else Color.White.copy(alpha = 0.45f)
    val midSheen = tintColor.copy(alpha = tintAlpha.coerceIn(0f, 0.22f))
    val bottomSheen = if (isDarkBase) Color.Black.copy(alpha = 0.12f) else Color.White.copy(alpha = 0.12f)

    val borderBrush = Brush.linearGradient(
        colors = listOf(
            if (isDarkBase) Color.White.copy(alpha = (borderAlpha * 0.42f).coerceIn(0.08f, 0.40f))
            else Color.White.copy(alpha = 0.85f),
            tintColor.copy(alpha = (borderAlpha * 0.38f).coerceIn(0.08f, 0.42f)),
            borderColor.copy(alpha = (borderAlpha * 0.60f).coerceIn(0.12f, 0.65f))
        )
    )

    return this
        .clip(shape)
        .background(baseColor.copy(alpha = fillAlpha.coerceIn(0.25f, 0.96f)))
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
