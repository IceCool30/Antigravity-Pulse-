package com.example.ui.theme

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

enum class AntigravityThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AntigravityContrastMode {
    DEFAULT,
    STRONG
}

data class AntigravityThemePreset(
    val name: String,
    val backgroundHex: String,
    val foregroundHex: String,
    val accentHex: String
)

val OfficialAntigravityPresets = listOf(
    AntigravityThemePreset(
        name = "Default Dark",
        backgroundHex = "#000000",
        foregroundHex = "#FFFFFF",
        accentHex = "#FFC799"
    ),
    AntigravityThemePreset(
        name = "Legacy Dark",
        backgroundHex = "#0B0E14",
        foregroundHex = "#F1F5F9",
        accentHex = "#38BDF8"
    ),
    AntigravityThemePreset(
        name = "Catppuccin",
        backgroundHex = "#1E1E2E",
        foregroundHex = "#CDD6F4",
        accentHex = "#CBA6F7"
    ),
    AntigravityThemePreset(
        name = "Dracula",
        backgroundHex = "#282A36",
        foregroundHex = "#F8F8F2",
        accentHex = "#BD93F9"
    ),
    AntigravityThemePreset(
        name = "Everforest",
        backgroundHex = "#2D353B",
        foregroundHex = "#D3C6AA",
        accentHex = "#A7C080"
    ),
    AntigravityThemePreset(
        name = "Gruvbox Material",
        backgroundHex = "#282828",
        foregroundHex = "#D4BE98",
        accentHex = "#E78A4E"
    ),
    AntigravityThemePreset(
        name = "Monokai",
        backgroundHex = "#272822",
        foregroundHex = "#F8F8F2",
        accentHex = "#A6E22E"
    ),
    AntigravityThemePreset(
        name = "Nord",
        backgroundHex = "#2E3440",
        foregroundHex = "#ECEFF4",
        accentHex = "#88C0D0"
    ),
    AntigravityThemePreset(
        name = "One Dark Pro",
        backgroundHex = "#282C34",
        foregroundHex = "#ABB2BF",
        accentHex = "#61AFEF"
    ),
    AntigravityThemePreset(
        name = "Tokyo Night",
        backgroundHex = "#1A1B26",
        foregroundHex = "#C0CAF5",
        accentHex = "#7AA2F7"
    ),
    AntigravityThemePreset(
        name = "Solarized Dark",
        backgroundHex = "#002B36",
        foregroundHex = "#839496",
        accentHex = "#2AA198"
    ),
    AntigravityThemePreset(
        name = "Vesper",
        backgroundHex = "#101010",
        foregroundHex = "#FFFFFF",
        accentHex = "#FFC799"
    )
)

val AvailableAntigravityFonts = listOf(
    "Google Sans",
    "Space Grotesk",
    "Inter",
    "JetBrains Mono",
    "Fira Code",
    "Roboto Mono"
)

val AvailableFontSizesPx = listOf(12, 13, 14, 15, 16, 18)

object DeveloperAttribution {
    const val NAME: String = "Solomon Ogar Bukie"
    const val EMAIL: String = "solomonogarbukie@gmail.com"
    const val GITHUB_USERNAME: String = "IceCool30"
}

data class AppearanceSettings(
    val themeMode: AntigravityThemeMode = AntigravityThemeMode.DARK,
    val contrastMode: AntigravityContrastMode = AntigravityContrastMode.STRONG,
    val fontFamilyName: String = "Google Sans",
    val fontSizePx: Int = 14,
    val presetName: String = "Default Dark",
    val backgroundHex: String = "#000000",
    val foregroundHex: String = "#FFFFFF",
    val accentHex: String = "#FFC799",
    val isFullscreenBarsHidden: Boolean = true,
    val isBiometricLockEnabled: Boolean = false,
    val isApprovalNotificationsEnabled: Boolean = true,
    val isAutoReconnectEnabled: Boolean = true,
    val floatingHandleXFraction: Float = 0f,
    val floatingHandleYFraction: Float = 0.42f,
    val lastActiveRemoteUrl: String = "",
    val isMaterialYouDynamicEnabled: Boolean = false,
    val isFrostedGlassEnabled: Boolean = true,
    val isAppEventNotificationsEnabled: Boolean = true,
    val isNotifyOnlyInBackground: Boolean = false,
    val lastSignedInUid: String = ""
) {
    val developerName: String get() = DeveloperAttribution.NAME
    val developerEmail: String get() = DeveloperAttribution.EMAIL
    val developerGithub: String get() = DeveloperAttribution.GITHUB_USERNAME
    val isLightMode: Boolean
        get() = themeMode == AntigravityThemeMode.LIGHT

    val backgroundColor: Color
        get() = if (isLightMode) {
            Color(0xFFEFF3F8)
        } else {
            parseHexColor(backgroundHex, Color(0xFF000000))
        }

    val surfaceColor: Color
        get() = if (isLightMode) {
            Color(0xFFFFFFFF)
        } else {
            blendColor(backgroundColor, Color.White, if (contrastMode == AntigravityContrastMode.STRONG) 0.06f else 0.04f)
        }

    val elevatedSurfaceColor: Color
        get() = if (isLightMode) {
            Color(0xFFE2E8F0)
        } else {
            blendColor(backgroundColor, Color.White, if (contrastMode == AntigravityContrastMode.STRONG) 0.11f else 0.08f)
        }

    val borderColor: Color
        get() = if (isLightMode) {
            if (contrastMode == AntigravityContrastMode.STRONG) Color(0xFF94A3B8) else Color(0xFFCBD5E1)
        } else {
            blendColor(backgroundColor, Color.White, if (contrastMode == AntigravityContrastMode.STRONG) 0.22f else 0.13f)
        }

    val foregroundColor: Color
        get() = if (isLightMode) {
            Color(0xFF0F172A)
        } else {
            parseHexColor(foregroundHex, Color.White)
        }

    val secondaryTextColor: Color
        get() = if (isLightMode) {
            if (contrastMode == AntigravityContrastMode.STRONG) Color(0xFF334155) else Color(0xFF475569)
        } else {
            foregroundColor.copy(alpha = if (contrastMode == AntigravityContrastMode.STRONG) 0.78f else 0.64f)
        }

    val rawAccentColor: Color
        get() = parseHexColor(accentHex, Color(0xFFFFC799))

    /**
     * Automatically ensures WCAG AA+ legibility of the accent color on the active Light or Dark
     * surface while preserving the user's chosen hue family (e.g., warm peach #FFC799 adapts into
     * rich high-contrast burnt amber in Light Mode while remaining exact #FFC799 in Dark Mode).
     */
    val accentColor: Color
        get() = ensureReadableAccent(
            rawAccent = rawAccentColor,
            againstSurface = surfaceColor,
            isLightMode = isLightMode,
            minContrast = if (isLightMode) 4.8f else 4.5f
        )

    /**
     * High-contrast text/icon color automatically chosen for solid [accentColor] filled controls.
     */
    val onAccentColor: Color
        get() {
            val darkInk = Color(0xFF070708)
            val lightInk = Color.White
            return if (contrastRatio(accentColor, lightInk) >= contrastRatio(accentColor, darkInk)) {
                lightInk
            } else {
                darkInk
            }
        }

    val aiStudioAccentColor: Color
        get() = if (isLightMode) Color(0xFF0369A1) else Color(0xFF7DD3FC)

    val successColor: Color
        get() = if (isLightMode) Color(0xFF047857) else EmeraldOnline

    val errorColor: Color
        get() = if (isLightMode) Color(0xFFB91C1C) else CrimsonError

    val codeKeywordColor: Color
        get() = if (isLightMode) Color(0xFF0E7490) else Color(0xFF56B6C2)

    val codeStringColor: Color
        get() = if (isLightMode) Color(0xFF15803D) else Color(0xFF98C379)
}

fun parseHexColor(hex: String, fallback: Color): Color {
    val cleaned = hex.trim().removePrefix("#")
    return try {
        when (cleaned.length) {
            6 -> Color(("FF$cleaned").toLong(16))
            8 -> Color(cleaned.toLong(16))
            else -> fallback
        }
    } catch (_: Exception) {
        fallback
    }
}

/**
 * Computes the WCAG 2.1 contrast ratio (1.0 .. 21.0) between two colors.
 */
fun contrastRatio(first: Color, second: Color): Float {
    val l1 = first.luminance()
    val l2 = second.luminance()
    val lighter = maxOf(l1, l2)
    val darker = minOf(l1, l2)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/**
 * Preserves [rawAccent] untouched when it already satisfies [minContrast] against [againstSurface],
 * otherwise smoothly adjusts HSL lightness (keeping the hue family crisp and vibrant) until
 * WCAG contrast is met.
 */
fun ensureReadableAccent(
    rawAccent: Color,
    againstSurface: Color,
    isLightMode: Boolean,
    minContrast: Float = 4.8f
): Color {
    if (contrastRatio(rawAccent, againstSurface) >= minContrast) {
        return rawAccent
    }
    val (h, s, l) = rgbToHsl(rawAccent)
    val targetS = if (s > 0.12f) {
        if (isLightMode) s.coerceIn(0.68f, 0.92f) else s.coerceIn(0.55f, 0.95f)
    } else {
        s
    }

    if (isLightMode) {
        var lo = 0.08f
        var hi = l.coerceIn(0.08f, 0.95f)
        var best = hslToColor(h, targetS, lo)
        repeat(18) {
            val mid = (lo + hi) * 0.5f
            val candidate = hslToColor(h, targetS, mid)
            if (contrastRatio(candidate, againstSurface) >= minContrast) {
                best = candidate
                lo = mid
            } else {
                hi = mid
            }
        }
        return best
    } else {
        var lo = l.coerceIn(0.05f, 0.90f)
        var hi = 0.92f
        var best = hslToColor(h, targetS, hi)
        repeat(18) {
            val mid = (lo + hi) * 0.5f
            val candidate = hslToColor(h, targetS, mid)
            if (contrastRatio(candidate, againstSurface) >= minContrast) {
                best = candidate
                hi = mid
            } else {
                lo = mid
            }
        }
        return best
    }
}

private fun rgbToHsl(color: Color): Triple<Float, Float, Float> {
    val r = color.red.coerceIn(0f, 1f)
    val g = color.green.coerceIn(0f, 1f)
    val b = color.blue.coerceIn(0f, 1f)
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val l = (max + min) * 0.5f
    val s = if (delta < 1e-5f) {
        0f
    } else {
        delta / (1f - abs(2f * l - 1f))
    }
    val h = when {
        delta < 1e-5f -> 0f
        max == r -> 60f * (((g - b) / delta) % 6f)
        max == g -> 60f * (((b - r) / delta) + 2f)
        else -> 60f * (((r - g) / delta) + 4f)
    }.let { if (it < 0f) it + 360f else it }
    return Triple(h, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))
}

private fun hslToColor(h: Float, s: Float, l: Float): Color {
    val c = (1f - abs(2f * l - 1f)) * s
    val hPrime = ((h % 360f) + 360f) % 360f / 60f
    val x = c * (1f - abs(hPrime % 2f - 1f))
    val (r1, g1, b1) = when {
        hPrime < 1f -> Triple(c, x, 0f)
        hPrime < 2f -> Triple(x, c, 0f)
        hPrime < 3f -> Triple(0f, c, x)
        hPrime < 4f -> Triple(0f, x, c)
        hPrime < 5f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    val m = l - c * 0.5f
    return Color(
        red = (r1 + m).coerceIn(0f, 1f),
        green = (g1 + m).coerceIn(0f, 1f),
        blue = (b1 + m).coerceIn(0f, 1f),
        alpha = 1f
    )
}

private fun blendColor(base: Color, target: Color, ratio: Float): Color {
    val r = (base.red * (1f - ratio) + target.red * ratio).coerceIn(0f, 1f)
    val g = (base.green * (1f - ratio) + target.green * ratio).coerceIn(0f, 1f)
    val b = (base.blue * (1f - ratio) + target.blue * ratio).coerceIn(0f, 1f)
    return Color(r, g, b, 1f)
}

val LocalAppearanceSettings = staticCompositionLocalOf { AppearanceSettings() }

class AppearancePreferencesManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(
        "antigravity_appearance_prefs",
        Context.MODE_PRIVATE
    )

    private val _settings = MutableStateFlow(loadFromPrefs())
    val settings: StateFlow<AppearanceSettings> = _settings.asStateFlow()

    private fun loadFromPrefs(): AppearanceSettings {
        val themeStr = prefs.getString("theme_mode", AntigravityThemeMode.DARK.name)
        val contrastStr = prefs.getString("contrast_mode", AntigravityContrastMode.STRONG.name)
        return AppearanceSettings(
            themeMode = AntigravityThemeMode.entries.firstOrNull { it.name == themeStr }
                ?: AntigravityThemeMode.DARK,
            contrastMode = AntigravityContrastMode.entries.firstOrNull { it.name == contrastStr }
                ?: AntigravityContrastMode.STRONG,
            fontFamilyName = prefs.getString("font_family", "Google Sans") ?: "Google Sans",
            fontSizePx = prefs.getInt("font_size_px", 14),
            presetName = prefs.getString("preset_name", "Default Dark") ?: "Default Dark",
            backgroundHex = prefs.getString("bg_hex_v2", "#000000") ?: "#000000",
            foregroundHex = prefs.getString("fg_hex", "#FFFFFF") ?: "#FFFFFF",
            accentHex = prefs.getString("accent_hex", "#FFC799") ?: "#FFC799",
            isFullscreenBarsHidden = prefs.getBoolean("fullscreen_bars_hidden_v2", true),
            isBiometricLockEnabled = prefs.getBoolean("biometric_lock_enabled", false),
            isApprovalNotificationsEnabled = prefs.getBoolean("approval_notifications_enabled", true),
            isAutoReconnectEnabled = prefs.getBoolean("auto_reconnect_enabled", true),
            floatingHandleXFraction = prefs.getFloat("floating_handle_x_fraction", 0f).coerceIn(0f, 1f),
            floatingHandleYFraction = prefs.getFloat("floating_handle_y_fraction", 0.42f).coerceIn(0f, 1f),
            lastActiveRemoteUrl = sanitizePersistedUrl(prefs.getString("last_active_remote_url", "") ?: ""),
            isMaterialYouDynamicEnabled = prefs.getBoolean("material_you_dynamic_enabled_v2", false),
            isFrostedGlassEnabled = prefs.getBoolean("frosted_glass_enabled", true),
            isAppEventNotificationsEnabled = prefs.getBoolean("app_event_notifications_enabled", true),
            isNotifyOnlyInBackground = prefs.getBoolean("notify_only_in_background", false),
            lastSignedInUid = prefs.getString("last_signed_in_uid", "") ?: ""
        )
    }

    private fun sanitizePersistedUrl(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        return com.example.data.remote.RetainedWebViewManager.stripSensitiveUrlCredentials(trimmed)
    }

    fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        val rawNext = transform(_settings.value)
        val next = rawNext.copy(
            lastActiveRemoteUrl = sanitizePersistedUrl(rawNext.lastActiveRemoteUrl)
        )
        _settings.value = next
        prefs.edit()
            .putString("theme_mode", next.themeMode.name)
            .putString("contrast_mode", next.contrastMode.name)
            .putString("font_family", next.fontFamilyName)
            .putInt("font_size_px", next.fontSizePx)
            .putString("preset_name", next.presetName)
            .putString("bg_hex_v2", next.backgroundHex)
            .putString("fg_hex", next.foregroundHex)
            .putString("accent_hex", next.accentHex)
            .putBoolean("fullscreen_bars_hidden_v2", next.isFullscreenBarsHidden)
            .putBoolean("biometric_lock_enabled", next.isBiometricLockEnabled)
            .putBoolean("approval_notifications_enabled", next.isApprovalNotificationsEnabled)
            .putBoolean("auto_reconnect_enabled", next.isAutoReconnectEnabled)
            .putFloat("floating_handle_x_fraction", next.floatingHandleXFraction.coerceIn(0f, 1f))
            .putFloat("floating_handle_y_fraction", next.floatingHandleYFraction.coerceIn(0f, 1f))
            .putString("last_active_remote_url", next.lastActiveRemoteUrl)
            .putBoolean("material_you_dynamic_enabled_v2", next.isMaterialYouDynamicEnabled)
            .putBoolean("frosted_glass_enabled", next.isFrostedGlassEnabled)
            .putBoolean("app_event_notifications_enabled", next.isAppEventNotificationsEnabled)
            .putBoolean("notify_only_in_background", next.isNotifyOnlyInBackground)
            .putString("last_signed_in_uid", next.lastSignedInUid)
            .apply()
    }

    fun applyPreset(preset: AntigravityThemePreset) {
        update {
            it.copy(
                presetName = preset.name,
                backgroundHex = preset.backgroundHex,
                foregroundHex = preset.foregroundHex,
                accentHex = preset.accentHex,
                isMaterialYouDynamicEnabled = false
            )
        }
    }

    fun resetToDefaultDark() {
        val defaultDark = OfficialAntigravityPresets.first()
        update {
            it.copy(
                themeMode = AntigravityThemeMode.DARK,
                presetName = defaultDark.name,
                backgroundHex = defaultDark.backgroundHex,
                foregroundHex = defaultDark.foregroundHex,
                accentHex = defaultDark.accentHex,
                isMaterialYouDynamicEnabled = false
            )
        }
    }

    fun resetCurrentPresetColors() {
        val currentPreset = OfficialAntigravityPresets.firstOrNull {
            it.name == _settings.value.presetName
        } ?: OfficialAntigravityPresets.first()
        applyPreset(currentPreset)
    }
}
