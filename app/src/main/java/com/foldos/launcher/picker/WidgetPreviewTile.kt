package com.foldos.launcher.picker

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.foldos.launcher.core.model.WidgetEntry
import com.foldos.launcher.core.repo.BuiltInWidget
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Size of one home-grid cell at which built-in widgets are drawn before being scaled down. */
internal val WidgetPreviewFullCell: Dp = 72.dp

/** Largest span shown in a preview (mock-up previews are capped at 4x4). */
internal const val WIDGET_PREVIEW_MAX_SPAN: Int = 4

/** Width of a preview tile for [cellSize]: wide enough for a two-line label under a 1x1 widget. */
internal fun widgetTileWidth(cellSize: Dp): Dp = cellSize * WIDGET_PREVIEW_MAX_SPAN

/**
 * One widget in the picker (mock-up `.wp-it`): a fixed preview box sized by the entry's default
 * span, the name in bold, and "source · WxH" below.
 *
 * Built-ins ([builtIn] non-null) render their real composable at [WidgetPreviewFullCell] per cell
 * and scale it down into the box with [Modifier.scale]; app widgets draw the provider's preview
 * image (or icon) through [WidgetEntry.previewLoader].
 */
@Composable
internal fun WidgetPreviewTile(
    entry: WidgetEntry,
    builtIn: BuiltInWidget?,
    cellSize: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val spanX = entry.defaultSpanX.coerceIn(1, WIDGET_PREVIEW_MAX_SPAN)
    val spanY = entry.defaultSpanY.coerceIn(1, WIDGET_PREVIEW_MAX_SPAN)
    val previewShape = RoundedCornerShape(12.dp)
    val sourceLabel = if (entry.isBuiltIn) "Built in" else entry.source

    Column(
        modifier = modifier
            .width(widgetTileWidth(cellSize))
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = cellSize * spanX, height = cellSize * spanY)
                .shadow(elevation = 4.dp, shape = previewShape, clip = false)
                .clip(previewShape)
                .background(tokens.surface)
                .border(1.dp, tokens.surfaceBorder, previewShape),
        ) {
            if (builtIn != null) {
                BuiltInPreview(builtIn = builtIn, spanX = spanX, spanY = spanY, cellSize = cellSize)
            } else {
                AppWidgetPreview(entry = entry, boxWidth = cellSize * spanX, boxHeight = cellSize * spanY)
            }
            // Transparent cover so the preview content never steals the tap from the tile.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable(onClick = onClick),
            )
        }
        Text(
            text = entry.name,
            color = tokens.onSurface,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = "$sourceLabel · ${entry.defaultSpanX}x${entry.defaultSpanY}",
            color = tokens.onSurface.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Draws [builtIn] at full size inside a box of its scaled size, shrunk with [Modifier.scale]. */
@Composable
private fun BuiltInPreview(builtIn: BuiltInWidget, spanX: Int, spanY: Int, cellSize: Dp) {
    val scale = cellSize / WidgetPreviewFullCell
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(width = WidgetPreviewFullCell * spanX, height = WidgetPreviewFullCell * spanY)
                .scale(scale),
        ) {
            builtIn.content(spanX, spanY, Modifier.fillMaxSize())
        }
    }
}

/** Provider preview image (or icon) loaded off the main thread; a widgets glyph until it lands. */
@Composable
private fun AppWidgetPreview(entry: WidgetEntry, boxWidth: Dp, boxHeight: Dp) {
    val tokens = LocalFoldOsTokens.current
    val density = LocalDensity.current
    val maxPx = with(density) { maxOf(boxWidth, boxHeight).roundToPx() * 2 }.coerceAtLeast(1)
    val bitmap = rememberWidgetPreviewBitmap(entry, maxPx)
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = entry.name,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                contentScale = ContentScale.Fit,
            )
        } else {
            Icon(
                imageVector = Icons.Rounded.Widgets,
                contentDescription = null,
                tint = tokens.onSurface.copy(alpha = 0.35f),
                modifier = Modifier.size(minOf(boxWidth, boxHeight) / 2),
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
