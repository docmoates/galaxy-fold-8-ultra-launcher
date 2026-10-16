package com.foldos.launcher.core.model

import kotlinx.serialization.Serializable

/**
 * Settings > Look > Color scheme (mock-up `colors`). Colors are ARGB packed in a Long so the
 * class stays serializable; convert with `Color(value)` in Compose.
 *
 * [scheme] is one of "theme" (dynamic / theme default), a preset id from [ColorSchemes.presets],
 * or "custom", in which case [accent], [wall1] and [wall2] are used directly.
 */
@Serializable
data class ColorSchemeConfig(
    val scheme: String = SCHEME_THEME,
    val accent: Long = 0xFF7AD1FF,
    val wall1: Long = 0xFF4F46E5,
    val wall2: Long = 0xFFDB2777,
) {
    companion object {
        const val SCHEME_THEME: String = "theme"
        const val SCHEME_CUSTOM: String = "custom"
    }
}

/** A named palette: accent, wallpaper color 1, wallpaper color 2 (mock-up `SCHEMES`). */
data class ColorSchemePreset(
    val id: String,
    val name: String,
    val accent: Long,
    val wall1: Long,
    val wall2: Long,
)

object ColorSchemes {
    val presets: List<ColorSchemePreset> = listOf(
        ColorSchemePreset("ocean", "Ocean", 0xFF1E88E5, 0xFF0EA5E9, 0xFF6366F1),
        ColorSchemePreset("forest", "Forest", 0xFF2E7D32, 0xFF16A34A, 0xFF0F766E),
        ColorSchemePreset("sunset", "Sunset", 0xFFF97316, 0xFFF43F5E, 0xFFA855F7),
        ColorSchemePreset("rose", "Rose", 0xFFE11D74, 0xFFF472B6, 0xFF8B5CF6),
        ColorSchemePreset("gold", "Gold", 0xFFD4A017, 0xFFF59E0B, 0xFF92400E),
        ColorSchemePreset("mono", "Mono", 0xFF9CA3AF, 0xFF6B7280, 0xFF374151),
    )

    fun byId(id: String): ColorSchemePreset? = presets.firstOrNull { it.id == id }

    /**
     * The effective [accent, wall1, wall2] triple for a config, or null when the scheme is
     * "theme" (use the theme's own colors / Material You). Mirrors the mock-up's `schemeColors`.
     */
    fun resolve(config: ColorSchemeConfig): List<Long>? = when (config.scheme) {
        ColorSchemeConfig.SCHEME_THEME -> null
        ColorSchemeConfig.SCHEME_CUSTOM -> listOf(config.accent, config.wall1, config.wall2)
        else -> byId(config.scheme)?.let { listOf(it.accent, it.wall1, it.wall2) }
    }
}
