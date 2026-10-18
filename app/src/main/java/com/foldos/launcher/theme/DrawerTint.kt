package com.foldos.launcher.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.foldos.launcher.core.model.DrawerTint

/** The app pop-up / drawer panel fill and the text color that is readable on it. */
internal data class DrawerPanelColors(val panel: Color, val onPanel: Color)

/** Fill opacity of the tinted panel: the blur behind still shows through a little, text stays readable. */
private const val TintedPanelAlpha = 0.94f
private val NearBlack = Color(0xFF0B0C10)

/**
 * Colors for the app pop-up panel (Settings > Home > "Pop-up color").
 *
 * - [DrawerTint.NEUTRAL] (and High contrast, which wants opaque neutral panels) keeps [FoldOsTokens.drawerScrim]
 *   and [FoldOsTokens.onSurface] exactly as before.
 * - [DrawerTint.THEME] takes the hue of the chosen color scheme ([FoldOsTokens.accent], which is the scheme accent,
 *   a custom accent or the Material You color) at low saturation, very dark in a dark appearance and very light in a
 *   light one, 94% opaque. Space black does not change it (that setting only recolors widget tiles, app cards
 *   and the dock). The text color is [FoldOsTokens.onSurface] when that reads at 4.5:1 on the panel (also over the
 *   worst backdrop, black or white showing through the remaining 6%), else white or near-black, whichever reads better.
 */
internal fun FoldOsTokens.drawerPanelColors(tint: DrawerTint, highContrast: Boolean): DrawerPanelColors {
    if (tint == DrawerTint.NEUTRAL || highContrast) return DrawerPanelColors(drawerScrim, onSurface)
    val a = accent.toHsl()
    val saturation = minOf(a.s, 42f)
    val lightness = if (isDark) 15f else 93f
    val base = hsl(a.h, saturation, lightness)
    val panel = base.copy(alpha = TintedPanelAlpha)
    val onPanel = listOf(onSurface.copy(alpha = 1f), Color.White, NearBlack)
        .let { options ->
            options.firstOrNull { worstContrast(it, panel) >= 4.5f }
                ?: options.maxBy { worstContrast(it, panel) }
        }
    return DrawerPanelColors(panel, onPanel)
}

/** Lowest contrast of [text] on [panel] over a black or a white backdrop. */
private fun worstContrast(text: Color, panel: Color): Float =
    minOf(
        contrastRatio(text, panel.compositeOver(Color.Black)),
        contrastRatio(text, panel.compositeOver(Color.White)),
    )
