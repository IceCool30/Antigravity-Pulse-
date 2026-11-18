package com.example.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.R

val SpaceGroteskFontFamily = FontFamily(
    Font(R.font.space_grotesk, FontWeight.Normal),
    Font(R.font.space_grotesk, FontWeight.Medium),
    Font(R.font.space_grotesk, FontWeight.Bold)
)

val PlusJakartaSansFontFamily = FontFamily(
    Font(R.font.plus_jakarta_sans, FontWeight.Normal),
    Font(R.font.plus_jakarta_sans, FontWeight.Medium),
    Font(R.font.plus_jakarta_sans, FontWeight.SemiBold),
    Font(R.font.plus_jakarta_sans, FontWeight.Bold)
)

val InterFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal),
    Font(R.font.inter, FontWeight.Medium),
    Font(R.font.inter, FontWeight.Bold)
)

val JetBrainsMonoFontFamily = FontFamily(
    Font(R.font.jetbrains_mono, FontWeight.Normal),
    Font(R.font.jetbrains_mono, FontWeight.Medium),
    Font(R.font.jetbrains_mono, FontWeight.Bold)
)

val RobotoMonoFontFamily = FontFamily(
    Font(R.font.roboto_mono, FontWeight.Normal),
    Font(R.font.roboto_mono, FontWeight.Medium),
    Font(R.font.roboto_mono, FontWeight.Bold)
)

val FiraCodeFontFamily = FontFamily(
    Font(R.font.fira_code, FontWeight.Normal),
    Font(R.font.fira_code, FontWeight.Medium),
    Font(R.font.fira_code, FontWeight.Bold)
)

fun buildDynamicTypography(
    fontName: String,
    baseFontSizePx: Int
): Typography {
    val scale = (baseFontSizePx.coerceIn(11, 22) / 14f)
    val selectedFamily = when (fontName) {
        "Google Sans" -> PlusJakartaSansFontFamily
        "Space Grotesk" -> SpaceGroteskFontFamily
        "Inter" -> InterFontFamily
        "JetBrains Mono" -> JetBrainsMonoFontFamily
        "Roboto Mono" -> RobotoMonoFontFamily
        "Fira Code" -> FiraCodeFontFamily
        else -> PlusJakartaSansFontFamily
    }

    return Typography(
        displayLarge = TextStyle(
            fontFamily = selectedFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (30f * scale).sp,
            lineHeight = (36f * scale).sp,
            letterSpacing = (-0.5).sp
        ),
        headlineMedium = TextStyle(
            fontFamily = selectedFamily,
            fontWeight = FontWeight.Bold,
            fontSize = (21f * scale).sp,
            lineHeight = (27f * scale).sp,
            letterSpacing = (-0.25).sp
        ),
        titleLarge = TextStyle(
            fontFamily = selectedFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (17f * scale).sp,
            lineHeight = (23f * scale).sp
        ),
        titleMedium = TextStyle(
            fontFamily = selectedFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = (15f * scale).sp,
            lineHeight = (20f * scale).sp
        ),
        bodyLarge = TextStyle(
            fontFamily = selectedFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (14.5f * scale).sp,
            lineHeight = (21f * scale).sp
        ),
        bodyMedium = TextStyle(
            fontFamily = selectedFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (13f * scale).sp,
            lineHeight = (18.5f * scale).sp
        ),
        labelLarge = TextStyle(
            fontFamily = selectedFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (12.5f * scale).sp,
            lineHeight = (17f * scale).sp,
            letterSpacing = 0.2.sp
        ),
        labelSmall = TextStyle(
            fontFamily = JetBrainsMonoFontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (11f * scale).sp,
            lineHeight = (15f * scale).sp
        )
    )
}

val Typography = buildDynamicTypography("Google Sans", 14)
