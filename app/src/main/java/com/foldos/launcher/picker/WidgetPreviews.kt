package com.foldos.launcher.picker

import android.graphics.Bitmap
import android.os.Process
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.WidgetEntry
import com.foldos.launcher.core.repo.BuiltInWidget
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Size of one home-grid cell at which built-in widgets are drawn before being scaled to fit a preview box. */
internal val WidgetPreviewFullCell: Dp = 72.dp

/** Largest span used when sizing a preview (mock-up previews are capped at 4x4). */
internal const val WIDGET_PREVIEW_MAX_SPAN: Int = 4

/** Default span of [entry] clamped to what a preview can show. */
internal fun previewSpanX(entry: WidgetEntry): Int = entry.defaultSpanX.coerceIn(1, WIDGET_PREVIEW_MAX_SPAN)

internal fun previewSpanY(entry: WidgetEntry): Int = entry.defaultSpanY.coerceIn(1, WIDGET_PREVIEW_MAX_SPAN)

/** Width / height of the widget at its default size, clamped so extreme providers stay presentable. */
internal fun previewAspect(entry: WidgetEntry): Float =
    (previewSpanX(entry).toFloat() / previewSpanY(entry).toFloat()).coerceIn(0.4f, 3f)

/** "2x2" for the default size of [entry]. */
internal fun sizeLabel(entry: WidgetEntry): String = "${entry.defaultSpanX}x${entry.defaultSpanY}"

private fun Dp.isBounded(): Boolean = value.isFinite()

/**
 * Preview of [entry] that fills whatever box [modifier] gives it, aspect-correct and never cropped.
 *
 * Built-ins draw their real composable at the widget's default span size (72dp per cell) and scale
 * it with `graphicsLayer` to fit; app widgets draw the provider's preview image (or the app icon
 * the catalog falls back to) with [contentScale]. Touches never reach the live content: with
 * [onClick] the preview is a tap target for it, without it taps are swallowed.
 */
@Composable
internal fun WidgetPreview(
    entry: WidgetEntry,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    onClick: (() -> Unit)? = null,
) {
    val registry = LocalAppGraph.current.builtIns
    val builtIn: BuiltInWidget? = remember(entry.id, registry) {
        if (entry.isBuiltIn) registry.byId(entry.id) else null
    }
    Box(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        if (builtIn != null) {
            BuiltInPreview(
                builtIn = builtIn,
                spanX = previewSpanX(entry),
                spanY = previewSpanY(entry),
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            AppWidgetPreview(entry = entry, contentScale = contentScale, modifier = Modifier.fillMaxSize())
        }
        // Transparent cover so the live content never steals the tap.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (onClick != null) {
                        Modifier.clickable(onClick = onClick)
                    } else {
                        Modifier.pointerInput(Unit) { detectTapGestures(onTap = { }) }
                    },
                ),
        )
    }
}

/** Draws [builtIn] at spanX x spanY cells of [WidgetPreviewFullCell] and scales it to fit the box. */
@Composable
private fun BuiltInPreview(builtIn: BuiltInWidget, spanX: Int, spanY: Int, modifier: Modifier = Modifier) {
    BoxWithConstraints(modifier = modifier.clipToBounds(), contentAlignment = Alignment.Center) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        val fullWidth = WidgetPreviewFullCell * spanX
        val fullHeight = WidgetPreviewFullCell * spanY
        val scale: Float = if (boxWidth.isBounded() && boxHeight.isBounded()) {
            min(boxWidth / fullWidth, boxHeight / fullHeight).coerceIn(0.05f, 3f)
        } else {
            1f
        }
        Box(
            modifier = Modifier
                .requiredSize(width = fullWidth, height = fullHeight)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
        ) {
            builtIn.content(spanX, spanY, Modifier.fillMaxSize())
        }
    }
}

/** Provider preview image (or icon) loaded off the main thread; a widgets glyph until it lands. */
@Composable
private fun AppWidgetPreview(entry: WidgetEntry, contentScale: ContentScale, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val boxWidth = maxWidth
        val boxHeight = maxHeight
        val bounded = boxWidth.isBounded() && boxHeight.isBounded()
        val longest: Dp = if (bounded) maxOf(boxWidth, boxHeight) else 200.dp
        val glyph: Dp = if (bounded) minOf(boxWidth, boxHeight) / 2 else 32.dp
        val maxPx = with(density) { longest.roundToPx() * 2 }.coerceIn(96, 1600)
        val bitmap = rememberWidgetPreviewBitmap(entry, maxPx)
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Widgets,
                contentDescription = null,
                tint = tokens.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.size(glyph),
            )
        }
    }
}

/**
 * Rasterises [WidgetEntry.previewLoader] on IO at most [maxPx] on its longer side. Null while
 * loading, when the entry has no loader, or when the drawable cannot be drawn.
 */
@Composable
private fun rememberWidgetPreviewBitmap(entry: WidgetEntry, maxPx: Int): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, key1 = entry.id, key2 = maxPx) {
        val loader = entry.previewLoader ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching {
                val drawable = loader() ?: return@runCatching null
                val iw = drawable.intrinsicWidth
                val ih = drawable.intrinsicHeight
                val (w, h) = if (iw > 0 && ih > 0) {
                    val factor = min(1f, maxPx / max(iw, ih).toFloat())
                    (iw * factor).roundToInt().coerceAtLeast(1) to (ih * factor).roundToInt().coerceAtLeast(1)
                } else {
                    maxPx to maxPx
                }
                drawable.toBitmap(width = w, height = h, config = Bitmap.Config.ARGB_8888).asImageBitmap()
            }.getOrNull()
        }
    }
    return state.value
}

// ---- app icons --------------------------------------------------------------------------------

/** Small in-memory cache of the app icons shown in the app list; cleared whenever the picker closes. */
internal object WidgetPickerIconCache {
    private val cache = LruCache<String, ImageBitmap>(96)

    fun get(key: String): ImageBitmap? = cache.get(key)

    fun put(key: String, value: ImageBitmap) {
        cache.put(key, value)
    }

    fun clear() {
        cache.evictAll()
    }
}

/**
 * The launcher icon of [packageName] (through the active icon pack/shape), [sizePx] square, loaded
 * on IO. Null while loading or when the package has no icon.
 */
@Composable
internal fun rememberAppIconBitmap(packageName: String, sizePx: Int): ImageBitmap? {
    val context = LocalContext.current
    val graph = LocalAppGraph.current
    val cacheKey = "$packageName@$sizePx"
    val state = produceState<ImageBitmap?>(
        initialValue = WidgetPickerIconCache.get(cacheKey),
        key1 = cacheKey,
    ) {
        if (value != null) return@produceState
        val loaded: ImageBitmap? = withContext(Dispatchers.IO) {
            runCatching {
                val pm = context.applicationContext.packageManager
                val info = AppInfo(
                    packageName = packageName,
                    activityName = "",
                    label = packageName,
                    user = Process.myUserHandle(),
                    iconLoader = { pm.getApplicationIcon(packageName) },
                )
                val drawable = runCatching { graph.icons.icon(info) }.getOrNull() ?: pm.getApplicationIcon(packageName)
                drawable.toBitmap(width = sizePx, height = sizePx, config = Bitmap.Config.ARGB_8888).asImageBitmap()
            }.getOrNull()
        }
        if (loaded != null) {
            WidgetPickerIconCache.put(cacheKey, loaded)
            value = loaded
        }
    }
    return state.value
}
