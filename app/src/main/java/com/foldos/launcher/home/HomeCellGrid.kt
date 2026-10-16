package com.foldos.launcher.home

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** Grid coordinates of one child of [HomeCellGrid]. */
internal data class HomeCell(
    val col: Int,
    val row: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
)

private class HomeCellPlacement(val placeable: Placeable, val x: Int, val y: Int)

/**
 * Fixed-cell grid that places each child at its [HomeCell]. Cells stretch to fill the available
 * size; children outside the grid are not placed.
 *
 * @param fixedRows row count, or null to derive it from the height and [minCellHeight].
 * @param gutter extra horizontal space inserted between the two middle columns (only when
 *   [cols] is even) so the hinge of the inner screen falls into a gap, not a cell.
 */
@Composable
internal fun HomeCellGrid(
    cols: Int,
    cells: List<HomeCell>,
    modifier: Modifier = Modifier,
    fixedRows: Int? = null,
    minCellHeight: Dp = 96.dp,
    gutter: Dp = 0.dp,
    content: @Composable (index: Int) -> Unit,
) {
    Layout(
        content = {
            cells.indices.forEach { index ->
                Box { content(index) }
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val columns = max(1, cols)
        val width = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
        val minCellHeightPx = max(1, minCellHeight.roundToPx())
        val rows = when {
            fixedRows != null -> max(1, fixedRows)
            constraints.hasBoundedHeight -> max(1, constraints.maxHeight / minCellHeightPx)
            else -> 1
        }
        val height = if (constraints.hasBoundedHeight) constraints.maxHeight else rows * minCellHeightPx
        val gutterPx = if (gutter > 0.dp && columns % 2 == 0) gutter.roundToPx() else 0
        val half = columns / 2
        val cellW = max(1, (width - gutterPx) / columns)
        val cellH = max(1, height / rows)

        fun startX(col: Int): Int = col * cellW + (if (gutterPx > 0 && col >= half) gutterPx else 0)
        fun endX(colEnd: Int): Int = colEnd * cellW + (if (gutterPx > 0 && colEnd > half) gutterPx else 0)

        val placements = ArrayList<HomeCellPlacement>(measurables.size)
        measurables.forEachIndexed { index, measurable ->
            val cell = cells.getOrNull(index) ?: return@forEachIndexed
            if (cell.col < 0 || cell.row < 0 || cell.col >= columns || cell.row >= rows) return@forEachIndexed
            val endCol = min(columns, cell.col + max(1, cell.spanX))
            val endRow = min(rows, cell.row + max(1, cell.spanY))
            val x = startX(cell.col)
            val w = max(1, endX(endCol) - x)
            val y = cell.row * cellH
            val h = max(1, (endRow - cell.row) * cellH)
            placements.add(HomeCellPlacement(measurable.measure(Constraints.fixed(w, h)), x, y))
        }

        layout(width, height) {
            placements.forEach { it.placeable.placeRelative(it.x, it.y) }
        }
    }
}
