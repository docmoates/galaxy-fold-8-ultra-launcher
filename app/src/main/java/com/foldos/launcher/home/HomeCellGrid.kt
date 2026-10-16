package com.foldos.launcher.home

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.ScreenKind
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Where one child of [HomeCellGrid] goes. Normally a grid cell ([col], [row], [spanX] x [spanY]);
 * when all four dp fields are set the child is placed freely at ([xDp], [yDp]) with size
 * [wDp] x [hDp] from the grid's top-left corner instead (free placement, `GridMode.FREE`).
 */
internal data class HomeCell(
    val col: Int,
    val row: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val xDp: Float? = null,
    val yDp: Float? = null,
    val wDp: Float? = null,
    val hDp: Float? = null,
) {
    val isFree: Boolean get() = xDp != null && yDp != null && wDp != null && hDp != null
}

private class HomeCellPlacement(val placeable: Placeable, val x: Int, val y: Int)

/**
 * The cell metrics a grid last measured with, in px. Plain fields (not snapshot state) so the
 * measure pass never writes state; read by the long-press hit test and the geometry publisher.
 */
internal class HomeGridMetrics {
    var cols: Int = 1
    var rows: Int = 1
    var cellW: Int = 1
    var cellH: Int = 1
    var gutterPx: Int = 0

    fun colAt(x: Int): Int {
        val half = cols / 2
        val gutterStart = half * cellW
        val col = when {
            gutterPx > 0 && x >= gutterStart + gutterPx -> (x - gutterPx) / cellW
            gutterPx > 0 && x >= gutterStart -> half
            else -> x / cellW
        }
        return col.coerceIn(0, max(0, cols - 1))
    }

    fun rowAt(y: Int): Int = (y / cellH).coerceIn(0, max(0, rows - 1))
}

/**
 * Fixed-cell grid that places each child at its [HomeCell]. Cells stretch to fill the available
 * size; children outside the grid are not placed. Free cells ([HomeCell.isFree]) are placed at
 * their dp rectangle, clamped to the grid bounds.
 *
 * @param fixedRows row count, or null to derive it from the height and [minCellHeight].
 * @param gutter extra horizontal space inserted between the two middle columns (only when
 *   [cols] is even) so the hinge of the inner screen falls into a gap, not a cell.
 * @param keys optional stable keys per child (same size as [cells]) so children keep their state
 *   when the list is reordered.
 * @param geometryScreen when non-null, the grid publishes its measured [GridGeometry] for that
 *   screen into the store read by [rememberGridGeometry].
 * @param onEmptyLongPress a long press that no child consumed, with the cell under the finger.
 */
@Composable
internal fun HomeCellGrid(
    cols: Int,
    cells: List<HomeCell>,
    modifier: Modifier = Modifier,
    fixedRows: Int? = null,
    minCellHeight: Dp = 96.dp,
    gutter: Dp = 0.dp,
    keys: List<Any>? = null,
    geometryScreen: ScreenKind? = null,
    onEmptyLongPress: ((col: Int, row: Int) -> Unit)? = null,
    content: @Composable (index: Int) -> Unit,
) {
    val metrics = remember { HomeGridMetrics() }
    val density = LocalDensity.current

    val positioned = if (geometryScreen != null) {
        Modifier.onGloballyPositioned { coordinates ->
            val origin = coordinates.positionInWindow()
            val geometry = with(density) {
                GridGeometry(
                    cols = metrics.cols,
                    rows = metrics.rows,
                    cellWidthDp = metrics.cellW.toDp().value.roundToInt(),
                    cellHeightDp = metrics.cellH.toDp().value.roundToInt(),
                    originXDp = origin.x.toDp().value.roundToInt(),
                    originYDp = origin.y.toDp().value.roundToInt(),
                    gutterDp = metrics.gutterPx.toDp().value.roundToInt(),
                )
            }
            HomeGeometryStore.publish(geometryScreen, geometry)
        }
    } else {
        Modifier
    }
    // The callback is usually a fresh lambda per recomposition; read the latest one inside a
    // detector that is keyed only on whether a callback exists.
    val latestEmptyLongPress by rememberUpdatedState(onEmptyLongPress)
    val press = if (onEmptyLongPress != null) {
        Modifier.pointerInput(metrics) {
            detectTapGestures(
                onLongPress = { offset ->
                    latestEmptyLongPress?.invoke(metrics.colAt(offset.x.roundToInt()), metrics.rowAt(offset.y.roundToInt()))
                },
            )
        }
    } else {
        Modifier
    }

    Layout(
        content = {
            cells.indices.forEach { index ->
                val k = keys?.getOrNull(index)
                if (k != null) {
                    key(k) { Box { content(index) } }
                } else {
                    Box { content(index) }
                }
            }
        },
        modifier = modifier.then(positioned).then(press),
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

        metrics.cols = columns
        metrics.rows = rows
        metrics.cellW = cellW
        metrics.cellH = cellH
        metrics.gutterPx = gutterPx

        fun startX(col: Int): Int = col * cellW + (if (gutterPx > 0 && col >= half) gutterPx else 0)
        fun endX(colEnd: Int): Int = colEnd * cellW + (if (gutterPx > 0 && colEnd > half) gutterPx else 0)

        val placements = ArrayList<HomeCellPlacement>(measurables.size)
        measurables.forEachIndexed { index, measurable ->
            val cell = cells.getOrNull(index) ?: return@forEachIndexed
            if (cell.isFree) {
                val x = cell.xDp!!.dp.roundToPx().coerceIn(0, max(0, width - 1))
                val y = cell.yDp!!.dp.roundToPx().coerceIn(0, max(0, height - 1))
                val w = cell.wDp!!.dp.roundToPx().coerceIn(1, max(1, width - x))
                val h = cell.hDp!!.dp.roundToPx().coerceIn(1, max(1, height - y))
                placements.add(HomeCellPlacement(measurable.measure(Constraints.fixed(w, h)), x, y))
                return@forEachIndexed
            }
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
