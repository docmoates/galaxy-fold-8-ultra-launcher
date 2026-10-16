package com.foldos.launcher.edit

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.home.GridGeometry
import kotlin.math.max
import kotlin.math.roundToInt

/** Which corner of a selected widget a resize drag started on. */
internal enum class EditCorner { TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

/** Grid cell rectangle: the result of snapping a move or a resize. */
internal data class EditCellRect(val col: Int, val row: Int, val spanX: Int, val spanY: Int)

/**
 * The page grid of [GridGeometry] converted to pixels in the overlay's own coordinate space, so
 * pointer positions (px) and item rectangles can be compared directly.
 *
 * @property density px per dp, used for free-placed items whose position is stored in dp.
 */
internal class EditGridPx(
    val cols: Int,
    val rows: Int,
    val cellW: Float,
    val cellH: Float,
    val originX: Float,
    val originY: Float,
    val density: Float,
) {
    val width: Float get() = cols * cellW
    val height: Float get() = rows * cellH
    val bounds: Rect get() = Rect(originX, originY, originX + width, originY + height)

    /** Pixel rectangle of a cell span. */
    fun cellRect(col: Int, row: Int, spanX: Int, spanY: Int): Rect {
        val left = originX + col * cellW
        val top = originY + row * cellH
        return Rect(left, top, left + max(1, spanX) * cellW, top + max(1, spanY) * cellH)
    }

    fun cellRect(rect: EditCellRect): Rect = cellRect(rect.col, rect.row, rect.spanX, rect.spanY)

    /** Pixel rectangle of an item: its dp box when free-placed, otherwise its grid cells. */
    fun rectFor(item: HomeItem): Rect {
        val x = item.xDp
        val y = item.yDp
        val w = item.wDp
        val h = item.hDp
        if (item.free && x != null && y != null && w != null && h != null) {
            val left = originX + x * density
            val top = originY + y * density
            return Rect(left, top, left + w * density, top + h * density)
        }
        return cellRect(item.col, item.row, item.spanX, item.spanY)
    }

    /** Nearest column for an item whose left edge is at [leftPx], kept inside the grid. */
    fun snapCol(leftPx: Float, spanX: Int): Int =
        ((leftPx - originX) / cellW).roundToInt().coerceIn(0, max(0, cols - max(1, spanX)))

    /** Nearest row for an item whose top edge is at [topPx], kept inside the grid. */
    fun snapRow(topPx: Float, spanY: Int): Int =
        ((topPx - originY) / cellH).roundToInt().coerceIn(0, max(0, rows - max(1, spanY)))

    /** Snapped cell for a moved item whose top-left corner is at [topLeftPx]. */
    fun snapMove(item: HomeItem, topLeftPx: Offset): EditCellRect {
        val spanX = max(1, item.spanX).coerceAtMost(cols)
        val spanY = max(1, item.spanY).coerceAtMost(rows)
        return EditCellRect(snapCol(topLeftPx.x, spanX), snapRow(topLeftPx.y, spanY), spanX, spanY)
    }

    /**
     * The cell rectangle after dragging [corner] of [item] by [delta] px. The opposite corner stays
     * put; spans never drop below 1 x 1 or leave the grid.
     */
    fun resized(item: HomeItem, corner: EditCorner, delta: Offset): EditCellRect {
        val dCols = (delta.x / cellW).roundToInt()
        val dRows = (delta.y / cellH).roundToInt()
        var col = item.col.coerceIn(0, max(0, cols - 1))
        var row = item.row.coerceIn(0, max(0, rows - 1))
        var spanX = max(1, item.spanX).coerceAtMost(max(1, cols - col))
        var spanY = max(1, item.spanY).coerceAtMost(max(1, rows - row))
        val right = col + spanX
        val bottom = row + spanY
        when (corner) {
            EditCorner.TOP_LEFT -> {
                col = (col + dCols).coerceIn(0, right - 1)
                row = (row + dRows).coerceIn(0, bottom - 1)
                spanX = right - col
                spanY = bottom - row
            }
            EditCorner.TOP_RIGHT -> {
                row = (row + dRows).coerceIn(0, bottom - 1)
                spanY = bottom - row
                spanX = (spanX + dCols).coerceIn(1, max(1, cols - col))
            }
            EditCorner.BOTTOM_LEFT -> {
                col = (col + dCols).coerceIn(0, right - 1)
                spanX = right - col
                spanY = (spanY + dRows).coerceIn(1, max(1, rows - row))
            }
            EditCorner.BOTTOM_RIGHT -> {
                spanX = (spanX + dCols).coerceIn(1, max(1, cols - col))
                spanY = (spanY + dRows).coerceIn(1, max(1, rows - row))
            }
        }
        return EditCellRect(col, row, spanX, spanY)
    }

    /** -1 when [x] is left of the grid by more than [slack] px, 1 when right of it, else 0. */
    fun edgeAt(x: Float, slack: Float): Int = when {
        x < originX - slack -> -1
        x > originX + width + slack -> 1
        else -> 0
    }

    companion object {
        /** Converts the Home agent's dp geometry to pixels with [density] px per dp. */
        fun from(geometry: GridGeometry, density: Float): EditGridPx = EditGridPx(
            cols = max(1, geometry.cols),
            rows = max(1, geometry.rows),
            cellW = max(1f, geometry.cellWidthDp.toFloat() * density),
            cellH = max(1f, geometry.cellHeightDp.toFloat() * density),
            originX = geometry.originXDp.toFloat() * density,
            originY = geometry.originYDp.toFloat() * density,
            density = density,
        )
    }
}

/** The corner of [rect] within [radiusPx] of [point], or null when none is. */
internal fun cornerAt(rect: Rect, point: Offset, radiusPx: Float): EditCorner? {
    fun near(corner: Offset): Boolean = (corner - point).getDistance() <= radiusPx
    return when {
        near(rect.topLeft) -> EditCorner.TOP_LEFT
        near(rect.topRight) -> EditCorner.TOP_RIGHT
        near(rect.bottomLeft) -> EditCorner.BOTTOM_LEFT
        near(rect.bottomRight) -> EditCorner.BOTTOM_RIGHT
        else -> null
    }
}

/** Pixel position of [corner] on [rect]. */
internal fun Rect.cornerOffset(corner: EditCorner): Offset = when (corner) {
    EditCorner.TOP_LEFT -> topLeft
    EditCorner.TOP_RIGHT -> topRight
    EditCorner.BOTTOM_LEFT -> bottomLeft
    EditCorner.BOTTOM_RIGHT -> bottomRight
}
