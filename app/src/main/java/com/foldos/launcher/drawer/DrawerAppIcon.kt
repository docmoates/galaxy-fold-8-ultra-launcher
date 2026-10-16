package com.foldos.launcher.drawer

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.core.graphics.drawable.toBitmap
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Loads an app icon off the main thread and returns it as an [ImageBitmap], or null while loading
 * or if the drawable could not be rasterised.
 */
@Composable
fun rememberDrawerIconBitmap(app: AppInfo, sizePx: Int): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, key1 = app.key, key2 = sizePx) {
        value = withContext(Dispatchers.IO) {
            runCatching { loadDrawerIconBitmap(app, sizePx) }.getOrNull()
        }
    }
    return state.value
}

private fun loadDrawerIconBitmap(app: AppInfo, sizePx: Int): ImageBitmap {
    val px = if (sizePx > 0) sizePx else 1
    val drawable = app.iconLoader()
    return drawable.toBitmap(width = px, height = px, config = Bitmap.Config.ARGB_8888).asImageBitmap()
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
