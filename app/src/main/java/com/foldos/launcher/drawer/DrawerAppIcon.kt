package com.foldos.launcher.drawer

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.home.HomeIconCache
import com.foldos.launcher.perf.rememberAppIconBitmap
import com.foldos.launcher.theme.LocalFoldOsTokens

/**
 * Loads an app icon off the main thread and returns it as an [ImageBitmap], or null while loading
 * or if the drawable could not be rasterised.
 */
@Composable
fun rememberDrawerIconBitmap(app: AppInfo, sizePx: Int): ImageBitmap? {
    // HomeIconCache.generation is bumped after an icon pack / shape / override change, so the
    // drawer re-rasterises together with the home grid.
    // Shares the home grid's cache (same key format, one memory-bounded LRU in perf.IconBitmapCache),
    // so a cell scrolling back into view reuses the already rasterised, pack-resolved and
    // shape-masked bitmap instead of redoing the work. A hit is returned in the same composition.
    return rememberAppIconBitmap(app, sizePx, HomeIconCache.generation)
}

/**
 * One app icon in the drawer grid, clipped to the theme's icon corner fraction. Shows a tinted
 * placeholder square until the bitmap is ready.
 */
@Composable
fun DrawerAppIcon(
    app: AppInfo,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx() }
    val bitmap = rememberDrawerIconBitmap(app, sizePx)
    val cornerPercent = (tokens.iconCornerFraction * 100f).toInt().coerceIn(0, 50)
    val shape = RoundedCornerShape(percent = cornerPercent)

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (bitmap == null) tokens.highlight else tokens.surface),
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
