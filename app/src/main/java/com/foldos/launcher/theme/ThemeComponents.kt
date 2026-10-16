package com.foldos.launcher.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Draws the current theme's wallpaper ([FoldOsTokens.wallpaper]) full-bleed. Place it as the first
 * child of the root `Box` of a screen so everything else layers on top of it.
 *
 * [showSystemWallpaper] draws nothing, so the phone's own wallpaper shows through the transparent
 * launcher window (`windowShowWallpaper`); the launcher uses it while Daily wallpaper is on.
 */
@Composable
fun Wallpaper(modifier: Modifier = Modifier, showSystemWallpaper: Boolean = false) {
    if (showSystemWallpaper) return
    val tokens = LocalFoldOsTokens.current
    Box(modifier = modifier.fillMaxSize().background(tokens.wallpaper))
}

/**
 * A themed panel (mock-up `.surf` / `.w` / `.pn`): [FoldOsTokens.surface] fill, a 1dp
 * [FoldOsTokens.surfaceBorder] hairline and the theme radius. Used for widgets, docks, folders,
 * the search pill and settings cards.
 *
 * [blurBehind] marks the surface as wanting a frosted backdrop on themes with a non-zero
 * [FoldOsTokens.blurRadius] (Glass, One UI). It is currently a no-op placeholder: Compose has no
 * blur-behind primitive, so the translucent fill is what shows. See [blurBehind].
 */
@Composable
fun FoldOsSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(LocalFoldOsTokens.current.radius),
    color: Color = LocalFoldOsTokens.current.surface,
    borderColor: Color = LocalFoldOsTokens.current.surfaceBorder,
    blurBehind: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: @Composable BoxScope.() -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    Box(
        modifier = modifier
            .blurBehind(enabled = blurBehind && tokens.blurRadius > 0.dp, radius = tokens.blurRadius)
            .clip(shape)
            .background(color)
            .border(width = 1.dp, color = borderColor, shape = shape)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Placeholder for a backdrop blur ("frosted glass") behind this node. Intentionally a no-op for
 * now: a real implementation needs either a RenderNode-based backdrop capture or Android 12+
 * window blur-behind, which lives outside this package. Kept as a modifier so call sites do not
 * change when it is implemented.
 */
@Suppress("UNUSED_PARAMETER")
fun Modifier.blurBehind(enabled: Boolean, radius: Dp): Modifier = this
