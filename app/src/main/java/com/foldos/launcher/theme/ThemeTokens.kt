package com.foldos.launcher.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.ColorSchemeConfig
import com.foldos.launcher.core.model.ColorSchemes
import com.foldos.launcher.core.model.ThemeId
import kotlin.math.max
import kotlin.math.min

/**
 * Resolves the design tokens for [theme] in dark or light mode (PLAN.md 2.3), then applies the
 * color scheme (PLAN.md 2.8):
 *
 * - `colors.scheme == "theme"`: the theme's own palette from the mock-up CSS. For [ThemeId.MATERIAL]
 *   with a non-null [dynamic] scheme, Material You's primary / primaryContainer / surfaces are used.
 * - any preset or "custom": accent, highlight and wallpaper are derived from the picked colors
 *   exactly like `colorVars()` in the mock-up (accent lightness clamped to >= 66% in dark and
 *   <= 42% in light for contrast).
 */
fun tokensFor(theme: ThemeId, dark: Boolean, colors: ColorSchemeConfig, dynamic: ColorScheme?): FoldOsTokens {
    val base = baseTokens(theme, dark)
    val palette = ColorSchemes.resolve(colors)
    return when {
        palette != null -> recolor(base, theme, dark, Color(palette[0]), Color(palette[1]), Color(palette[2]))
        theme == ThemeId.MATERIAL && dynamic != null -> withDynamicColor(base, dark, dynamic)
        else -> base
    }
}

// ---------------------------------------------------------------------------------------------
// Theme palettes (mock-up .th-material / .th-glass / .th-oneui / .th-bold, with .lm variants)
// ---------------------------------------------------------------------------------------------

private fun baseTokens(theme: ThemeId, dark: Boolean): FoldOsTokens = when (theme) {
    ThemeId.MATERIAL -> if (dark) materialDark() else materialLight()
    ThemeId.GLASS -> if (dark) glassDark() else glassLight()
    ThemeId.ONEUI -> if (dark) oneUiDark() else oneUiLight()
    ThemeId.BOLD -> if (dark) boldDark() else boldLight()
}

private fun materialDark() = FoldOsTokens(
    surface = rgba(44, 37, 68, 0.93f),
    surfaceBorder = Color.Transparent,
    onSurface = Color(0xFFEFE9FF),
    accent = Color(0xFFD0BCFF),
    onAccent = Color(0xFF381E72),
    highlight = Color(0xFFD0BCFF),
    onHighlight = Color(0xFF22005D),
    radius = 28.dp,
    blurRadius = 0.dp,
    iconCornerFraction = 0.5f,
    wallpaper = LayeredWallpaperBrush(
        listOf(
            WallLayer.Linear(
                160f,
                listOf(
                    WallStop(Color(0xFF2C2161), 0f),
                    WallStop(Color(0xFF17143D), 0.6f),
                    WallStop(Color(0xFF0D0B24), 1f),
                ),
            ),
            WallLayer.Radial(0.88f, 0.85f, 0.60f, 0.50f, fade(Color(0xFFFF8FB1), 0.65f)),
            WallLayer.Radial(0.18f, 0.12f, 0.55f, 0.45f, fade(Color(0xFF8F72FF), 0.70f)),
        ),
    ),
    drawerScrim = rgba(30, 26, 46, 0.97f),
    isDark = true,
)

private fun materialLight() = FoldOsTokens(
    surface = Color.White.copy(alpha = 0.93f),
    surfaceBorder = Color.Transparent,
    onSurface = Color(0xFF1D1B20),
    accent = Color(0xFF6750A4),
    onAccent = Color.White,
    highlight = Color(0xFFEADDFF),
    onHighlight = Color(0xFF21005D),
    radius = 28.dp,
    blurRadius = 0.dp,
    iconCornerFraction = 0.5f,
    wallpaper = LayeredWallpaperBrush(
        listOf(
            WallLayer.Linear(
                160f,
                listOf(
                    WallStop(Color(0xFFF2ECFF), 0f),
                    WallStop(Color(0xFFEBE4FB), 0.6f),
                    WallStop(Color(0xFFF7EFF7), 1f),
                ),
            ),
            WallLayer.Radial(0.88f, 0.85f, 0.60f, 0.50f, fade(Color(0xFFFFC6D9), 0.65f)),
            WallLayer.Radial(0.18f, 0.12f, 0.55f, 0.45f, fade(Color(0xFFCFBFFF), 0.70f)),
        ),
    ),
    drawerScrim = rgba(251, 247, 255, 0.98f),
    isDark = false,
)

private fun glassDark() = FoldOsTokens(
    surface = Color.White.copy(alpha = 0.075f),
    surfaceBorder = Color.White.copy(alpha = 0.14f),
    onSurface = Color(0xFFF3F6FF),
    accent = Color(0xFF7AD1FF),
    onAccent = Color(0xFF04121F),
    highlight = rgba(122, 209, 255, 0.15f),
    onHighlight = Color(0xFFE4F6FF),
    radius = 22.dp,
    blurRadius = 22.dp,
    iconCornerFraction = 0.3f,
    wallpaper = LayeredWallpaperBrush(
        listOf(
            WallLayer.Solid(Color(0xFF020306)),
            WallLayer.Radial(0.08f, 0.92f, 0.60f, 0.45f, fade(rgba(140, 80, 255, 0.38f), 0.70f)),
            WallLayer.Radial(0.82f, 0.08f, 0.50f, 0.38f, fade(rgba(70, 150, 255, 0.50f), 0.70f)),
        ),
    ),
    drawerScrim = rgba(12, 14, 22, 0.86f),
    isDark = true,
)

private fun glassLight() = FoldOsTokens(
    surface = Color.White.copy(alpha = 0.56f),
    surfaceBorder = Color.White.copy(alpha = 0.95f),
    onSurface = Color(0xFF0E1420),
    accent = Color(0xFF0A84D6),
    onAccent = Color.White,
    highlight = rgba(10, 132, 214, 0.12f),
    onHighlight = Color(0xFF064570),
    radius = 22.dp,
    blurRadius = 22.dp,
    iconCornerFraction = 0.3f,
    wallpaper = LayeredWallpaperBrush(
        listOf(
            WallLayer.Solid(Color(0xFFEEF2F8)),
            WallLayer.Radial(0.08f, 0.92f, 0.60f, 0.45f, fade(rgba(195, 160, 255, 0.50f), 0.70f)),
            WallLayer.Radial(0.82f, 0.08f, 0.50f, 0.38f, fade(rgba(120, 190, 255, 0.60f), 0.70f)),
        ),
    ),
    drawerScrim = rgba(242, 246, 251, 0.86f),
    isDark = false,
)

private fun oneUiDark() = FoldOsTokens(
    surface = rgba(14, 30, 54, 0.52f),
    surfaceBorder = Color.White.copy(alpha = 0.12f),
    onSurface = Color.White,
    accent = Color(0xFF5AA8FF),
    onAccent = Color(0xFF03172E),
    highlight = Color(0xFF2F7CF6),
    onHighlight = Color.White,
    radius = 26.dp,
    blurRadius = 18.dp,
    iconCornerFraction = 0.34f,
    wallpaper = LayeredWallpaperBrush(
        listOf(
            WallLayer.Linear(
                180f,
                listOf(
                    WallStop(Color(0xFF5D97D2), 0f),
                    WallStop(Color(0xFF3F78B8), 0.4f),
                    WallStop(Color(0xFF24578F), 0.7f),
                    WallStop(Color(0xFF163A63), 1f),
                ),
            ),
            WallLayer.Radial(0.72f, 0.22f, 0.30f, 0.14f, fade(rgba(255, 236, 200, 0.70f), 0.70f)),
            WallLayer.Radial(0.90f, 1.04f, 0.90f, 0.34f, hardEdge(Color(0xFF18406A), 0.62f)),
            WallLayer.Radial(0.28f, 1.04f, 1.30f, 0.42f, hardEdge(Color(0xFF0F2C4B), 0.62f)),
        ),
    ),
    drawerScrim = rgba(14, 26, 46, 0.92f),
    isDark = true,
)

private fun oneUiLight() = FoldOsTokens(
    surface = Color.White.copy(alpha = 0.84f),
    surfaceBorder = Color.White.copy(alpha = 0.95f),
    onSurface = Color(0xFF111418),
    accent = Color(0xFF0B6CF0),
    onAccent = Color.White,
    highlight = Color(0xFF0B6CF0),
    onHighlight = Color.White,
    radius = 26.dp,
    blurRadius = 18.dp,
    iconCornerFraction = 0.34f,
    wallpaper = LayeredWallpaperBrush(
        listOf(
            WallLayer.Linear(
                180f,
                listOf(
                    WallStop(Color(0xFFE9F3FC), 0f),
                    WallStop(Color(0xFFD6E8F8), 0.45f),
                    WallStop(Color(0xFFC3DBF2), 1f),
                ),
            ),
            WallLayer.Radial(0.72f, 0.22f, 0.30f, 0.14f, fade(rgba(255, 252, 240, 0.95f), 0.70f)),
            WallLayer.Radial(0.90f, 1.04f, 0.90f, 0.34f, hardEdge(Color(0xFFBCD6F0), 0.62f)),
            WallLayer.Radial(0.28f, 1.04f, 1.30f, 0.42f, hardEdge(Color(0xFFA3C6E8), 0.62f)),
        ),
    ),
    drawerScrim = rgba(247, 249, 252, 0.96f),
    isDark = false,
)

private fun boldDark() = FoldOsTokens(
    surface = rgba(9, 13, 28, 0.90f),
    surfaceBorder = Color(0xFF20305A),
    onSurface = Color(0xFFE2FBFF),
    accent = Color(0xFF19F0FF),
    onAccent = Color(0xFF001316),
    highlight = rgba(25, 240, 255, 0.07f),
    onHighlight = Color(0xFF19F0FF),
    radius = 6.dp,
    blurRadius = 0.dp,
    iconCornerFraction = 0.16f,
    wallpaper = LayeredWallpaperBrush(
        listOf(
            WallLayer.Solid(Color(0xFF04050A)),
            WallLayer.Radial(0.50f, 1.05f, 0.70f, 0.40f, fade(rgba(255, 46, 196, 0.40f), 0.70f)),
            WallLayer.GridLines(rgba(25, 240, 255, 0.06f)),
        ),
    ),
    drawerScrim = rgba(6, 8, 16, 0.97f),
    isDark = true,
)

private fun boldLight() = FoldOsTokens(
    surface = Color.White,
    surfaceBorder = Color(0xFFC6D2EA),
    onSurface = Color(0xFF0A1020),
    accent = Color(0xFF008AA3),
    onAccent = Color.White,
    highlight = rgba(0, 138, 163, 0.07f),
    onHighlight = Color(0xFF006C80),
    radius = 6.dp,
    blurRadius = 0.dp,
    iconCornerFraction = 0.16f,
    wallpaper = LayeredWallpaperBrush(
        listOf(
            WallLayer.Solid(Color(0xFFF3F5FA)),
            WallLayer.Radial(0.50f, 1.05f, 0.70f, 0.40f, fade(rgba(224, 17, 157, 0.18f), 0.70f)),
            WallLayer.GridLines(rgba(0, 110, 140, 0.07f)),
        ),
    ),
    drawerScrim = rgba(247, 249, 252, 0.98f),
    isDark = false,
)

// ---------------------------------------------------------------------------------------------
// Color schemes (port of the mock-up's colorVars())
// ---------------------------------------------------------------------------------------------

private fun recolor(
    base: FoldOsTokens,
    theme: ThemeId,
    dark: Boolean,
    accentIn: Color,
    wall1: Color,
    wall2: Color,
): FoldOsTokens {
    val a = accentIn.toHsl()
    val w1 = wall1.toHsl()
    val w2 = wall2.toHsl()

    val acc = hsl(a.h, a.s, if (dark) max(a.l, 66f) else min(a.l, 42f))
    val onAcc = if (dark) hsl(a.h, min(a.s, 60f), 12f) else Color.White
    val solid = theme == ThemeId.MATERIAL || theme == ThemeId.ONEUI
    val hi = if (solid) {
        if (dark || theme == ThemeId.ONEUI) acc else hsl(a.h, min(a.s, 80f), 90f)
    } else {
        hsl(a.h, a.s, if (dark) 66f else 42f, 0.14f)
    }
    val onHi = if (solid) {
        when {
            dark -> onAcc
            theme == ThemeId.ONEUI -> Color.White
            else -> hsl(a.h, 60f, 18f)
        }
    } else {
        hsl(a.h, a.s, if (dark) 85f else 25f)
    }

    var tokens = base.copy(
        accent = acc,
        onAccent = onAcc,
        highlight = hi,
        onHighlight = onHi,
        wallpaper = schemeWallpaper(theme, dark, a, w1, w2),
    )
    if (theme == ThemeId.MATERIAL) {
        tokens = tokens.copy(
            surface = hsl(a.h, min(a.s, 25f), if (dark) 17f else 99f, 0.93f),
            drawerScrim = hsl(a.h, min(a.s, 25f), if (dark) 12f else 98f, 0.97f),
        )
    }
    return tokens
}

/** Wallpaper gradient from two picked colors (mock-up `colorVars()` `--wall`), grid lines added for Bold. */
private fun schemeWallpaper(theme: ThemeId, dark: Boolean, a: Hsl, w1: Hsl, w2: Hsl): Brush {
    val layers = ArrayList<WallLayer>(4)
    if (dark) {
        layers += WallLayer.Linear(
            160f,
            listOf(
                WallStop(hsl(w1.h, min(w1.s, 50f), 14f), 0f),
                WallStop(hsl(w1.h, min(w1.s, 40f), 6f), 1f),
            ),
        )
        layers += WallLayer.Radial(0.88f, 0.85f, 0.60f, 0.50f, fade(hsl(w2.h, w2.s, w2.l.coerceIn(35f, 50f)), 0.65f))
        layers += WallLayer.Radial(0.18f, 0.12f, 0.55f, 0.45f, fade(hsl(w1.h, w1.s, w1.l.coerceIn(35f, 55f)), 0.70f))
    } else {
        layers += WallLayer.Linear(
            160f,
            listOf(
                WallStop(hsl(w1.h, min(w1.s, 60f), 96f), 0f),
                WallStop(hsl(w2.h, min(w2.s, 50f), 93f), 1f),
            ),
        )
        layers += WallLayer.Radial(0.88f, 0.85f, 0.60f, 0.50f, fade(hsl(w2.h, w2.s, 84f), 0.65f))
        layers += WallLayer.Radial(0.18f, 0.12f, 0.55f, 0.45f, fade(hsl(w1.h, w1.s, 80f), 0.70f))
    }
    if (theme == ThemeId.BOLD) {
        layers += WallLayer.GridLines(hsl(a.h, a.s, 50f, 0.07f))
    }
    return LayeredWallpaperBrush(layers)
}

/** Material You: take primary / primaryContainer / surfaces from the dynamic scheme (PLAN.md 2.3). */
private fun withDynamicColor(base: FoldOsTokens, dark: Boolean, dynamic: ColorScheme): FoldOsTokens {
    val primary = dynamic.primary.toHsl()
    val tertiary = dynamic.tertiary.toHsl()
    return base.copy(
        surface = dynamic.surfaceContainerHigh.copy(alpha = 0.93f),
        onSurface = dynamic.onSurface,
        accent = dynamic.primary,
        onAccent = dynamic.onPrimary,
        highlight = dynamic.primaryContainer,
        onHighlight = dynamic.onPrimaryContainer,
        wallpaper = schemeWallpaper(ThemeId.MATERIAL, dark, primary, primary, tertiary),
        drawerScrim = dynamic.surfaceContainerLow.copy(alpha = 0.97f),
    )
}
