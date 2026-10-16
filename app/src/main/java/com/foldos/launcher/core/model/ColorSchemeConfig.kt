package com.foldos.launcher.core.model

import kotlinx.serialization.Serializable

/**
 * Settings > Look > Color scheme (mock-up `colors`). Colors are ARGB packed in a Long so the
 * class stays serializable; convert with `Color(value)` in Compose.
 *
 * [scheme] is one of "theme" (dynamic / theme default), a preset id from [ColorSchemes.presets]
 * (`"ocean-dark"`, `"ocean-light"`, ...; a bare family id such as `"ocean"` still resolves, to the
 * dark version), or "custom", in which case [accent], [wall1] and [wall2] are used directly.
 * A primary color always shows its version for the current Appearance, see [ColorSchemes.resolveFor].
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

/** Which of a family's two versions a preset is: the pale (light) tones or the deep (dark) tones. */
enum class ColorTone { LIGHT, DARK }

/**
 * A named palette: accent, wallpaper color 1, wallpaper color 2 (mock-up `SCHEMES`).
 *
 * Every primary color is a [family] with two presets, a [ColorTone.DARK] version (lighter accent over a
 * deep wallpaper) and a [ColorTone.LIGHT] version (deeper accent over pastel wallpaper tints). [id] is
 * `"<family>-dark"` / `"<family>-light"`; the old single ids (`"ocean"`, `"forest"`, ...) still
 * resolve, to the dark version, so stored settings keep working. [name] is the family name
 * ("Blue") for both versions; show [tone] next to it. The three old fields ([accent], [wall1], [wall2])
 * are the values of THIS preset's tone, so a dark preset's three fields are the "dark values".
 */
data class ColorSchemePreset(
    val id: String,
    val name: String,
    val accent: Long,
    val wall1: Long,
    val wall2: Long,
    val family: String = id,
    val tone: ColorTone = ColorTone.DARK,
)

/**
 * One primary color with its two versions (a split swatch in the color picker). The explicit
 * per-tone values are also exposed flat: [lightAccent] / [lightWall1] / [lightWall2] and
 * [darkAccent] / [darkWall1] / [darkWall2].
 */
data class ColorFamily(
    val id: String,
    val name: String,
    val light: ColorSchemePreset,
    val dark: ColorSchemePreset,
) {
    fun forTone(tone: ColorTone): ColorSchemePreset = if (tone == ColorTone.LIGHT) light else dark

    /** The version for the current Appearance: the dark one when [isDark], else the light one. */
    fun forMode(isDark: Boolean): ColorSchemePreset = if (isDark) dark else light

    val lightAccent: Long get() = light.accent
    val lightWall1: Long get() = light.wall1
    val lightWall2: Long get() = light.wall2
    val darkAccent: Long get() = dark.accent
    val darkWall1: Long get() = dark.wall1
    val darkWall2: Long get() = dark.wall2

    /**
     * The value to store in [ColorSchemeConfig.scheme] when this primary color is picked. It is the
     * dark preset's id; [ColorSchemes.resolveFor] still hands out the light version in light mode.
     */
    val schemeId: String get() = dark.id
}

object ColorSchemes {
    private fun family(
        id: String,
        name: String,
        dark: Triple<Long, Long, Long>,
        light: Triple<Long, Long, Long>,
    ): ColorFamily = ColorFamily(
        id = id,
        name = name,
        light = ColorSchemePreset("$id-light", name, light.first, light.second, light.third, id, ColorTone.LIGHT),
        dark = ColorSchemePreset("$id-dark", name, dark.first, dark.second, dark.third, id, ColorTone.DARK),
    )

    /**
     * Twelve primary colors in display order. Dark = a lighter accent (so it reads on deep surfaces) over a
     * deep wallpaper; light = a deeper accent over pastel wallpaper tints. Triples are accent, wallpaper 1,
     * wallpaper 2. The ids of the first nine stored by earlier versions (ocean, forest, sunset, rose, gold,
     * violet, teal, red, mono) are unchanged; their display names are now the color names.
     */
    val families: List<ColorFamily> = listOf(
        family("ocean", "Blue", Triple(0xFF64B5F6, 0xFF1E6FD9, 0xFF6366F1), Triple(0xFF1565C0, 0xFFBBDEFB, 0xFFC7D2FE)),
        family("teal", "Teal", Triple(0xFF2DD4BF, 0xFF0D9488, 0xFF0E7490), Triple(0xFF00796B, 0xFF99F6E4, 0xFFA5F3FC)),
        family("forest", "Green", Triple(0xFF6EE08A, 0xFF16A34A, 0xFF0F766E), Triple(0xFF2E7D32, 0xFFBBF7D0, 0xFF99F6E4)),
        family("lime", "Lime", Triple(0xFFBDE34F, 0xFF65A30D, 0xFF15803D), Triple(0xFF4D7C0F, 0xFFECFCCB, 0xFFD9F99D)),
        family("gold", "Amber", Triple(0xFFFFC53D, 0xFFD97706, 0xFF92400E), Triple(0xFFB45309, 0xFFFDE68A, 0xFFFED7AA)),
        family("sunset", "Orange", Triple(0xFFFF9A55, 0xFFF97316, 0xFFF43F5E), Triple(0xFFC2410C, 0xFFFED7AA, 0xFFFECDD3)),
        family("red", "Red", Triple(0xFFFF6B6B, 0xFFDC2626, 0xFF9F1239), Triple(0xFFB91C1C, 0xFFFECACA, 0xFFFFE4E6)),
        family("rose", "Pink", Triple(0xFFF472B6, 0xFFEC4899, 0xFF8B5CF6), Triple(0xFFBE185D, 0xFFFBCFE8, 0xFFDDD6FE)),
        family("violet", "Purple", Triple(0xFFB794F6, 0xFF7C3AED, 0xFF4F46E5), Triple(0xFF6D28D9, 0xFFDDD6FE, 0xFFC7D2FE)),
        family("indigo", "Indigo", Triple(0xFF8C9EFF, 0xFF4F46E5, 0xFF2563EB), Triple(0xFF3730A3, 0xFFC7D2FE, 0xFFBFDBFE)),
        family("slate", "Slate", Triple(0xFF94A3B8, 0xFF475569, 0xFF334155), Triple(0xFF334155, 0xFFCBD5E1, 0xFFE2E8F0)),
        family("mono", "Mono", Triple(0xFFE5E7EB, 0xFF6B7280, 0xFF374151), Triple(0xFF1F2937, 0xFFE5E7EB, 0xFFD1D5DB)),
    )

    /** Every preset: for each family its dark version, then its light version. */
    val presets: List<ColorSchemePreset> = families.flatMap { listOf(it.dark, it.light) }

    /**
     * Exact id (`"ocean-light"`), a legacy family id (`"ocean"`, meaning the dark version) or a family
     * display name in any case (`"blue"`, also the dark version).
     */
    fun byId(id: String): ColorSchemePreset? =
        presets.firstOrNull { it.id == id }
            ?: families.firstOrNull { it.id == id }?.dark
            ?: families.firstOrNull { it.name.equals(id, ignoreCase = true) }?.dark

    fun familyById(id: String): ColorFamily? = families.firstOrNull { it.id == id }

    /**
     * The family a stored [scheme] belongs to (`"ocean-light"`, `"ocean-dark"` and `"ocean"` all give the
     * ocean family), or null for "theme", "custom" and unknown ids.
     */
    fun familyOfScheme(scheme: String): ColorFamily? {
        val preset = byId(scheme) ?: return null
        return families.firstOrNull { it.id == preset.family }
    }

    /**
     * The effective [accent, wall1, wall2] triple for a config, or null when the scheme is
     * "theme" (use the theme's own colors / Material You). Mirrors the mock-up's `schemeColors`.
     * A preset id gives exactly that preset; see [resolveFor] for the Appearance-aware version.
     */
    fun resolve(config: ColorSchemeConfig): List<Long>? = when (config.scheme) {
        ColorSchemeConfig.SCHEME_THEME -> null
        ColorSchemeConfig.SCHEME_CUSTOM -> listOf(config.accent, config.wall1, config.wall2)
        else -> byId(config.scheme)?.let { listOf(it.accent, it.wall1, it.wall2) }
    }

    /**
     * Like [resolve], but a primary color gives its version for the current Appearance: the dark values
     * when [dark], else the light values, whichever tone the stored id names. "theme" (null) and
     * "custom" behave as in [resolve].
     */
    fun resolveFor(config: ColorSchemeConfig, dark: Boolean): List<Long>? = when (config.scheme) {
        ColorSchemeConfig.SCHEME_THEME -> null
        ColorSchemeConfig.SCHEME_CUSTOM -> listOf(config.accent, config.wall1, config.wall2)
        else -> familyOfScheme(config.scheme)?.forMode(dark)?.let { listOf(it.accent, it.wall1, it.wall2) }
    }
}
