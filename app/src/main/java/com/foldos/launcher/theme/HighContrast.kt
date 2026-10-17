package com.foldos.launcher.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * The high-contrast variant of these tokens (Settings > Accessibility > High contrast).
 *
 * - panels become 82% opaque black (dark) or white (light) instead of the theme's translucent
 *   glass, so text never sits on a busy wallpaper;
 * - the hairline border turns into a visible 65% outline, text and icons become pure white or
 *   black, and the sheet fill ([FoldOsTokens.drawerScrim]) is opaque;
 * - the accent is lightened (dark) or darkened (light) until it reaches a 4.5:1 contrast ratio
 *   against the panel, and [FoldOsTokens.onAccent] / [FoldOsTokens.onHighlight] become whichever
 *   of black and white reads best on their fill.
 *
 * Radius, blur, icon corner and the wallpaper brush are unchanged.
 */
fun FoldOsTokens.withHighContrast(): FoldOsTokens {
    val ink = if (isDark) Color.White else Color.Black
    val paper = if (isDark) Color.Black else Color.White
    val panel = paper.copy(alpha = 0.82f)
    // What the panel looks like once drawn over a typical wallpaper tone.
    val panelOnWallpaper = panel.compositeOver(if (isDark) Color(0xFF0C0D12) else Color(0xFFEEF0F5))
    val strongAccent = accent.withContrastAgainst(panelOnWallpaper, minRatio = 4.5f, towardWhite = isDark)
    val highlightOnPanel = highlight.compositeOver(panelOnWallpaper)
    return copy(
        surface = panel,
        surfaceBorder = ink.copy(alpha = 0.65f),
        onSurface = ink,
        accent = strongAccent,
        onAccent = readableOn(strongAccent),
        onHighlight = readableOn(highlightOnPanel),
        drawerScrim = if (isDark) Color(0xFF05060A) else Color(0xFFFCFCFE),
    )
}

/** WCAG relative-luminance contrast ratio of two opaque colors (1..21). */
internal fun contrastRatio(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    val lighter = maxOf(la, lb)
    val darker = minOf(la, lb)
    return (lighter + 0.05f) / (darker + 0.05f)
}

/** Black or white, whichever has the higher contrast ratio on [background]. */
internal fun readableOn(background: Color): Color =
    if (contrastRatio(Color.White, background) >= contrastRatio(Color.Black, background)) Color.White else Color.Black

/** Moves this color toward white or black in 10% steps until it reaches [minRatio] against [background]. */
internal fun Color.withContrastAgainst(background: Color, minRatio: Float, towardWhite: Boolean): Color {
    val target = if (towardWhite) Color.White else Color.Black
    val opaque = copy(alpha = 1f)
    var step = 0
    var candidate = opaque
    while (contrastRatio(candidate, background) < minRatio && step < 10) {
        step++
        candidate = lerp(opaque, target, step / 10f)
    }
    return candidate
}
