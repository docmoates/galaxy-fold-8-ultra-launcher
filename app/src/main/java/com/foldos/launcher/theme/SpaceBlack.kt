package com.foldos.launcher.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.unit.dp

/** Pure black used for every Space black surface. */
internal val SpaceBlackSurface: Color = Color(0xFF000000)

/** 1dp outline that keeps shapes visible on pure black (and on a black wallpaper). */
internal val SpaceBlackOutline: Color = Color(0xFF26262B)

/** Secondary text on Space black: white at 72%, which is about 14:1 against black. */
internal val SpaceBlackSecondaryText: Color = Color.White.copy(alpha = 0.72f)

/**
 * The Space black variant of these tokens (Settings > Look > Space black). Applied by [FoldOsTheme] on
 * top of the dark tokens (the theme is resolved dark whatever the Appearance, so the wallpaper brush and the
 * text on it stay light-on-dark), and before [withHighContrast] outlines, so the two settings combine.
 *
 * - [FoldOsTokens.surface] and [FoldOsTokens.drawerScrim] (dock, cards, widgets, folder / add / drawer sheets,
 *   menus, dialogs) become solid #000000 with a 1dp #26262B outline (65% white when [highContrast] is on);
 * - [FoldOsTokens.onSurface] is pure white, [FoldOsTokens.onHighlight] pure white;
 * - the accent stays the user's accent, lightened until it reaches 4.5:1 against black;
 * - [FoldOsTokens.highlight] (selected rows, hero widget tint) is the accent at 22% over black, opaque, so
 *   a selection is still visible on a black card;
 * - blur is off: nothing is translucent, so there is nothing to blur behind.
 *
 * Radius, icon corner and the wallpaper brush are unchanged.
 */
fun FoldOsTokens.withSpaceBlack(highContrast: Boolean = false): FoldOsTokens {
    val black = SpaceBlackSurface
    val strongAccent = accent.withContrastAgainst(black, minRatio = 4.5f, towardWhite = true)
    val solidHighlight = strongAccent.copy(alpha = 0.22f).compositeOver(black)
    return copy(
        surface = black,
        surfaceBorder = if (highContrast) Color.White.copy(alpha = 0.65f) else SpaceBlackOutline,
        onSurface = Color.White,
        accent = strongAccent,
        onAccent = readableOn(strongAccent),
        highlight = solidHighlight,
        onHighlight = Color.White,
        blurRadius = 0.dp,
        drawerScrim = black,
        isDark = true,
        spaceBlack = true,
    )
}
