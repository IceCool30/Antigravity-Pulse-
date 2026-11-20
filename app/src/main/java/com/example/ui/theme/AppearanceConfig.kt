package com.example.ui.theme

import android.content.Context
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

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
    val isMaterialYouDynamicEnabled: Boolean = true,
    val isFrostedGlassEnabled: Boolean = true,
    val isAppEventNotificationsEnabled: Boolean = true,
    val isNotifyOnlyInBackground: Boolean = false
) {
    val developerName: String get() = DeveloperAttribution.NAME
    val developerEmail: String get() = DeveloperAttribution.EMAIL
    val developerGithub: String get() = DeveloperAttribution.GITHUB_USERNAME
    val backgroundColor: Color
        get() = if (themeMode == AntigravityThemeMode.LIGHT) {
            Color(0xFFF8FAFC)
        } else {
            parseHexColor(backgroundHex, Color(0xFF000000))
        }

    val surfaceColor: Color
        get() = if (themeMode == AntigravityThemeMode.LIGHT) {
            Color(0xFFFFFFFF)
        } else {
            blendColor(backgroundColor, Color.White, if (contrastMode == AntigravityContrastMode.STRONG) 0.06f else 0.04f)
        }

    val elevatedSurfaceColor: Color
        get() = if (themeMode == AntigravityThemeMode.LIGHT) {
            Color(0xFFF1F5F9)
        } else {
            blendColor(backgroundColor, Color.White, if (contrastMode == AntigravityContrastMode.STRONG) 0.11f else 0.08f)
        }

    val borderColor: Color
        get() = if (themeMode == AntigravityThemeMode.LIGHT) {
            if (contrastMode == AntigravityContrastMode.STRONG) Color(0xFF94A3B8) else Color(0xFFCBD5E1)
        } else {
            blendColor(backgroundColor, Color.White, if (contrastMode == AntigravityContrastMode.STRONG) 0.22f else 0.13f)
        }

    val foregroundColor: Color
        get() = if (themeMode == AntigravityThemeMode.LIGHT) {
            Color(0xFF0F172A)
        } else {
            parseHexColor(foregroundHex, Color.White)
        }

    val secondaryTextColor: Color
        get() = if (themeMode == AntigravityThemeMode.LIGHT) {
            Color(0xFF475569)
        } else {
            foregroundColor.copy(alpha = if (contrastMode == AntigravityContrastMode.STRONG) 0.78f else 0.64f)
        }

    val accentColor: Color
        get() = parseHexColor(accentHex, Color(0xFFFFC799))
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
            lastActiveRemoteUrl = prefs.getString("last_active_remote_url", "") ?: "",
            isMaterialYouDynamicEnabled = prefs.getBoolean("material_you_dynamic_enabled", true),
            isFrostedGlassEnabled = prefs.getBoolean("frosted_glass_enabled", true),
            isAppEventNotificationsEnabled = prefs.getBoolean("app_event_notifications_enabled", true),
            isNotifyOnlyInBackground = prefs.getBoolean("notify_only_in_background", false)
        )
    }

    fun update(transform: (AppearanceSettings) -> AppearanceSettings) {
        val next = transform(_settings.value)
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
            .putBoolean("material_you_dynamic_enabled", next.isMaterialYouDynamicEnabled)
            .putBoolean("frosted_glass_enabled", next.isFrostedGlassEnabled)
            .putBoolean("app_event_notifications_enabled", next.isAppEventNotificationsEnabled)
            .putBoolean("notify_only_in_background", next.isNotifyOnlyInBackground)
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

    fun resetCurrentPresetColors() {
        val currentPreset = OfficialAntigravityPresets.firstOrNull {
            it.name == _settings.value.presetName
        } ?: OfficialAntigravityPresets.first()
        applyPreset(currentPreset)
    }
}
