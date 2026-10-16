package com.foldos.launcher.home

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Process-wide cache of rendered app icons keyed by "package/activity@sizePx". */
internal object HomeIconCache {
    private val cache = LruCache<String, ImageBitmap>(256)

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, bitmap: ImageBitmap) {
        cache.put(key, bitmap)
    }
}

/**
 * Loads an app icon off the main thread through [AppInfo.iconLoader], rasterises it with
 * core-ktx `Drawable.toBitmap` and returns it as a [Painter]. Null while loading or on failure.
 */
@Composable
fun rememberHomeAppIconPainter(app: AppInfo, sizePx: Int): Painter? {
    val key = "${app.key}@$sizePx"
    // The producer restarts whenever [key] changes, so it always resolves the painter for the
    // current app instead of trusting the value left over from the previous one.
    val painter by produceState<Painter?>(null, key) {
        val hit = HomeIconCache.get(key)
        if (hit != null) {
            value = BitmapPainter(hit)
            return@produceState
        }
        value = null
        val bitmap = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = app.iconLoader()
                val size = sizePx.coerceAtLeast(1)
                drawable.toBitmap(size, size, Bitmap.Config.ARGB_8888).asImageBitmap()
            }.getOrNull()
        }
        if (bitmap != null) {
            HomeIconCache.put(key, bitmap)
            value = BitmapPainter(bitmap)
        }
    }
    return painter
}

/** One app icon clipped to the theme's icon shape. Shows a surface-colored placeholder while loading. */
@Composable
fun HomeAppIcon(app: AppInfo, size: Dp, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }
    val percent = (tokens.iconCornerFraction * 100f).toInt().coerceIn(0, 50)
    val shape = RoundedCornerShape(percent = percent)
    val painter = rememberHomeAppIconPainter(app, sizePx)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(if (painter == null) tokens.surface else Color.Transparent),
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = app.label,
                modifier = Modifier.size(size),
                contentScale = ContentScale.Fit,
            )
        }
    }
}

/** Icon plus optional label, centered in its cell. Tap launches; long-press is left to the caller. */
@Composable
fun HomeAppTile(
    app: AppInfo,
    iconSize: Dp,
    showLabel: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    labelColor: Color = LocalFoldOsTokens.current.onSurface,
    onLongClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .homeTapGestures(onTap = onClick, onLongPress = onLongClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HomeAppIcon(app = app, size = iconSize)
        if (showLabel) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = app.label,
                color = labelColor,
                style = MaterialTheme.typography.labelMedium.copy(shadow = homeTextShadow()),
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}
