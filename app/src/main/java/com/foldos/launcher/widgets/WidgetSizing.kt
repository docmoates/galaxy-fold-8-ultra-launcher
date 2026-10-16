package com.foldos.launcher.widgets

import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.util.SizeF
import com.foldos.launcher.core.model.HomeItem
import kotlin.math.ceil
import kotlin.math.roundToInt

/**
 * Span <-> dp arithmetic shared by the widget host, the picker and the home grid.
 *
 * FoldOS lays widgets out on [DEFAULT_CELL_DP] cells, so a provider's `minWidth` / `minHeight`
 * (dp) becomes `ceil(dp / cell)` cells, and a span of `n` cells is `n * cell` dp wide. The host
 * reports the real cell size of the page it draws on through [sizeOptions], so a widget always
 * learns the exact dp box it is given.
 */
object WidgetSizing {
    /** Nominal cell edge in dp used to turn provider minimum sizes into spans. */
    const val DEFAULT_CELL_DP: Int = 76

    /** Cells needed to show [dp] on [cellDp]-wide cells; never less than 1. */
    fun spansFor(dp: Int, cellDp: Int = DEFAULT_CELL_DP): Int {
        if (dp <= 0) return 1
        val cell = cellDp.coerceAtLeast(1)
        return ceil(dp / cell.toFloat()).toInt().coerceAtLeast(1)
    }

    /** dp covered by [span] cells of [cellDp] each; both are clamped to >= 1. */
    fun spanToDp(span: Int, cellDp: Int): Int = span.coerceAtLeast(1) * cellDp.coerceAtLeast(1)

    /**
     * App-widget options describing an exact [widthDp] x [heightDp] box: min/max width/height
     * (`OPTION_APPWIDGET_MIN_*` / `MAX_*`) plus the Android 12 `OPTION_APPWIDGET_SIZES` list with
     * that one size. Pass it to `AppWidgetHostView.updateAppWidgetSize`,
     * `AppWidgetManager.updateAppWidgetOptions` or as `EXTRA_APPWIDGET_OPTIONS` when binding.
     */
    fun sizeOptions(widthDp: Int, heightDp: Int, base: Bundle? = null): Bundle {
        val w = widthDp.coerceAtLeast(1)
        val h = heightDp.coerceAtLeast(1)
        val options = if (base != null) Bundle(base) else Bundle()
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w)
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h)
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
        options.putParcelableArrayList(
            AppWidgetManager.OPTION_APPWIDGET_SIZES,
            arrayListOf(SizeF(w.toFloat(), h.toFloat())),
        )
        return options
    }
}

/**
 * Width in dp this item occupies: its free-placement [HomeItem.wDp] when it is placed freely,
 * otherwise [HomeItem.spanX] cells of [cellWidthDp].
 */
fun HomeItem.widgetWidthDp(cellWidthDp: Int): Int {
    val freeWidth: Float? = if (free) wDp else null
    return if (freeWidth != null && freeWidth > 0f) {
        freeWidth.roundToInt().coerceAtLeast(1)
    } else {
        WidgetSizing.spanToDp(spanX, cellWidthDp)
    }
}

/** Height in dp this item occupies; see [widgetWidthDp]. */
fun HomeItem.widgetHeightDp(cellHeightDp: Int): Int {
    val freeHeight: Float? = if (free) hDp else null
    return if (freeHeight != null && freeHeight > 0f) {
        freeHeight.roundToInt().coerceAtLeast(1)
    } else {
        WidgetSizing.spanToDp(spanY, cellHeightDp)
    }
}
