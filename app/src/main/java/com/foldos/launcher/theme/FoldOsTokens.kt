package com.foldos.launcher.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Resolved design tokens for one theme x appearance x color scheme (PLAN.md 2.3 / 2.8).
 *
 * Mirrors the CSS custom properties of the mock-up's `.th-*` blocks:
 * `--surf` -> [surface], `--surf-b` -> [surfaceBorder], `--on` -> [onSurface], `--acc` -> [accent],
 * `--on-acc` -> [onAccent], `--hi` -> [highlight], `--on-hi` -> [onHighlight], `--blur` -> [blurRadius],
 * `--icr` -> [iconCornerFraction], `--wall` -> [wallpaper], `--drawer` -> [drawerScrim].
 */
@Immutable
data class FoldOsTokens(
    /** Card / panel / dock fill (may be translucent). */
    val surface: Color,
    /** 1dp hairline around surfaces (transparent for Material). */
    val surfaceBorder: Color,
    /** Primary text and icon color on the wallpaper and on surfaces. */
    val onSurface: Color,
    /** Accent used for primary buttons, selected states, themed icons. */
    val accent: Color,
    /** Text / icon color on top of [accent]. */
    val onAccent: Color,
    /** Highlight card fill (the "hero" widget tint). */
    val highlight: Color,
    /** Text / icon color on top of [highlight]. */
    val onHighlight: Color,
    /** Corner radius for widgets, panels and the dock. */
    val radius: Dp,
    /** Backdrop blur radius; 0.dp means the theme uses solid surfaces. */
    val blurRadius: Dp,
    /** Icon shape as a fraction of the icon size (0.5 = circle, 0.16 = squircle-ish square). */
    val iconCornerFraction: Float,
    /** Full-bleed wallpaper brush (see [Wallpaper]). */
    val wallpaper: Brush,
    /** Background of the app drawer / hub sheets (mock-up `--drawer`). */
    val drawerScrim: Color,
    /** True when these tokens are the dark variant. */
    val isDark: Boolean,
)

/** The current [FoldOsTokens]; provided by [FoldOsTheme]. */
val LocalFoldOsTokens = staticCompositionLocalOf<FoldOsTokens> { error("No FoldOsTokens") }
