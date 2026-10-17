package com.foldos.launcher.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
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
 * The Space black variant of these tokens (Settings > Look > Space black). Applied only inside [SpaceBlackScope]
 * (widget tiles, app panels, the dock), never by the theme, so the wallpaper, the Appearance, sheets and menus
 * are untouched. Combines with [withHighContrast] (stronger outline).
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

/**
 * What [FoldOsTheme] knows about Space black, provided under the normal tokens. Space black is NOT part of the
 * theme: [LocalFoldOsTokens] and the Material color scheme stay the user's normal ones everywhere. Only the
 * widget tiles, the app panels (app cards / app list cards) and the dock opt in with [SpaceBlackScope].
 *
 * @param baseTokens / [baseColorScheme] the normal (light / dark per Appearance) values, so a pop-up opened
 *   from inside a black surface can go back to them with [NormalThemeScope].
 */
class SpaceBlackConfig(
    val enabled: Boolean,
    val highContrast: Boolean,
    val baseTokens: FoldOsTokens?,
    val baseColorScheme: ColorScheme?,
)

val LocalSpaceBlack = compositionLocalOf { SpaceBlackConfig(false, false, null, null) }

/**
 * Wraps the content of a widget tile, an app panel or the dock. With Settings > Look > Space black on, descendants
 * read [FoldOsTokens.withSpaceBlack] tokens (solid black surface, white text, lightened accent), white
 * [LocalContentColor] and a matching Material color scheme; with it off (or when [active] is false, e.g. an app
 * panel that has no background) this is a no-op and the content keeps the normal tokens.
 */
@Composable
fun SpaceBlackScope(active: Boolean = true, content: @Composable () -> Unit) {
    val config = LocalSpaceBlack.current
    if (!config.enabled || !active) {
        content()
        return
    }
    val base = LocalFoldOsTokens.current
    val tokens = remember(base, config.highContrast) { base.withSpaceBlack(config.highContrast) }
    val scheme = remember(tokens) { tokens.toMaterialColorScheme() }
    CompositionLocalProvider(
        LocalFoldOsTokens provides tokens,
        LocalContentColor provides tokens.onSurface,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}

/**
 * Undoes [SpaceBlackScope] for content that opens from a black surface but is not one (item pop-ups, menus,
 * dialogs): it gets the normal theme again.
 */
@Composable
fun NormalThemeScope(content: @Composable () -> Unit) {
    val config = LocalSpaceBlack.current
    val tokens = config.baseTokens
    val scheme = config.baseColorScheme
    if (tokens == null || scheme == null || LocalFoldOsTokens.current === tokens) {
        content()
        return
    }
    CompositionLocalProvider(
        LocalFoldOsTokens provides tokens,
        LocalContentColor provides tokens.onSurface,
    ) {
        MaterialTheme(
            colorScheme = scheme,
            typography = MaterialTheme.typography,
            shapes = MaterialTheme.shapes,
            content = content,
        )
    }
}
