package com.foldos.launcher.home

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.ui.DragController
import com.foldos.launcher.core.ui.DragPayload
import com.foldos.launcher.core.ui.dragTarget
import com.foldos.launcher.edit.LocalEditHomeRoot
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlin.math.floor
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
    /**
     * True for an app / folder / pair tile. Such a tile only answers presses on its own visible
     * content (icon and label, which the tile checks itself), not on the whole stretched cell, so
     * [HomeGridMetrics.itemIndexAt] covers just the top [HomeGridMetrics.tileHitHeightPx] of the cell.
     */
    val tile: Boolean = false,
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

    /** Measured size of the grid node in px (cells plus the unused remainder at the right / bottom edge). */
    var widthPx: Int = 1
    var heightPx: Int = 1

    /** Pixels per dp at the last measure; free cells are stored in dp. */
    var density: Float = 1f

    /**
     * Height in px from the top of a cell that a [HomeCell.tile] always owns (its icon area). The
     * rest of the tile (its label) claims presses itself by consuming them, and the slack under the
     * label of a stretched cell belongs to nobody, so a long press there is an empty-space press.
     */
    var tileHitHeightPx: Int = Int.MAX_VALUE

    /**
     * Exact column under [x] (px from the grid's own left edge), or null when the point is left of
     * the grid, in the hinge gutter, or in the unused remainder right of the last column. Floor
     * division by the cell pitch; the gutter is skipped, never rounded into a neighbouring column.
     */
    fun colAtExact(x: Float): Int? {
        if (x < 0f) return null
        val col: Int = if (gutterPx > 0) {
            val gutterStart = (cols / 2 * cellW).toFloat()
            when {
                x < gutterStart -> floor(x / cellW).toInt()
                x < gutterStart + gutterPx -> return null
                else -> floor((x - gutterPx) / cellW).toInt()
            }
        } else {
            floor(x / cellW).toInt()
        }
        return if (col in 0 until cols) col else null
    }

    /** Exact row under [y] (px from the grid's own top edge), or null above / below the cells. */
    fun rowAtExact(y: Float): Int? {
        if (y < 0f) return null
        val row = floor(y / cellH).toInt()
        return if (row in 0 until rows) row else null
    }

    /** The (col, row) cell under a grid-local point, or null in the gutter / outside the cells. */
    fun cellAt(x: Float, y: Float): Pair<Int, Int>? {
        val col = colAtExact(x) ?: return null
        val row = rowAtExact(y) ?: return null
        return col to row
    }

    /** Right edge of the column range ending before [colEnd] (exclusive), hinge gutter included. */
    fun endX(colEnd: Int): Int {
        val half = cols / 2
        return colEnd * cellW + (if (gutterPx > 0 && colEnd > half) gutterPx else 0)
    }

    /**
     * Index in [cells] of the topmost child that covers the grid-local point ([x], [y]) in px, or
     * null when the point is over no child (an empty cell, the gutter, the unused remainder at the
     * edge). A child covers exactly the rectangle the layout places it in: its span clamped to the
     * grid, so a cell outside its span never matches and a child that is not placed (its cell is
     * outside the grid) never matches. Later children are drawn on top, so they are tested first.
     */
    fun itemIndexAt(cells: List<HomeCell>, x: Float, y: Float): Int? {
        for (index in cells.indices.reversed()) {
            val cell = cells[index]
            if (cell.isFree) {
                if (freeContains(cell, x, y)) return index
                continue
            }
            if (cell.col < 0 || cell.row < 0 || cell.col >= cols || cell.row >= rows) continue
            val endCol = min(cols, cell.col + max(1, cell.spanX))
            val endRow = min(rows, cell.row + max(1, cell.spanY))
            val left = startX(cell.col)
            val right = max(left + 1, endX(endCol))
            val top = cell.row * cellH
            val cellBottom = max(top + 1, endRow * cellH)
            // tileHitHeightPx is Int.MAX_VALUE when unset: skip the clamp rather than overflow the sum.
            val bottom = if (cell.tile && tileHitHeightPx != Int.MAX_VALUE) {
                min(cellBottom, top + max(1, tileHitHeightPx))
            } else {
                cellBottom
            }
            if (x >= left && x < right && y >= top && y < bottom) return index
        }
        return null
    }

    /** The same dp rectangle the layout gives a free cell, clamped to the grid bounds. */
    private fun freeContains(cell: HomeCell, x: Float, y: Float): Boolean {
        val xDp = cell.xDp ?: return false
        val yDp = cell.yDp ?: return false
        val wDp = cell.wDp ?: return false
        val hDp = cell.hDp ?: return false
        val left = (xDp * density).roundToInt().coerceIn(0, max(0, widthPx - 1))
        val top = (yDp * density).roundToInt().coerceIn(0, max(0, heightPx - 1))
        val w = (wDp * density).roundToInt().coerceIn(1, max(1, widthPx - left))
        val fullH = (hDp * density).roundToInt().coerceIn(1, max(1, heightPx - top))
        // A free tile's content is top-aligned, so the slack under it is empty space like in a grid cell.
        val h = if (cell.tile && tileHitHeightPx != Int.MAX_VALUE) min(fullH, max(1, tileHitHeightPx)) else fullH
        return x >= left && x < left + w && y >= top && y < top + h
    }

    /**
     * Clamped column for drops and the drop highlight: the nearest column, so a point in the gutter
     * or past the edge still lands on a cell. Use [colAtExact] to find out what is really under a point.
     */
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

    /** Left edge of column [col] in px, hinge gutter included. */
    fun startX(col: Int): Int {
        val half = cols / 2
        return col * cellW + (if (gutterPx > 0 && col >= half) gutterPx else 0)
    }
}

/**
 * Makes a [HomeCellGrid] a drop target of [controller] under [id]: a drop reports the cell under the
 * pointer through [onDrop], and the cell under a hovering drag is highlighted.
 */
internal class HomeGridDrop(
    val controller: DragController,
    val id: String,
    val onDrop: (payload: DragPayload, col: Int, row: Int) -> Unit,
)

/** Layout coordinates of the grid node, kept out of snapshot state (written from `onGloballyPositioned`). */
private class HomeGridCoordinates {
    var coordinates: LayoutCoordinates? = null

    /** [rootPoint] (root coordinates) as a point inside the grid, or null while the grid is detached. */
    fun toLocal(rootPoint: Offset): Offset? {
        val c = coordinates ?: return null
        if (!c.isAttached) return null
        return c.localPositionOf(c.findRootCoordinates(), rootPoint)
    }
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
 * @param onEmptyLongPress a long press on a point that no child covers (an empty cell, the hinge
 *   gutter, the unused edge), with the exact cell under the finger, or -1 / -1 when the finger is
 *   not over a cell. A press that lands on a child is never reported here, whatever that child does
 *   with it; a child that wants to leave the long press to the page must handle it itself.
 * @param dropTarget when non-null the grid accepts drag-and-drop (see [HomeGridDrop]).
 * @param tileHitHeight height from the top of a cell that a [HomeCell.tile] counts as covered in the
 *   empty-space hit test (its icon area); the tile's label claims presses by consuming them. Null =
 *   the whole cell.
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
    dropTarget: HomeGridDrop? = null,
    tileHitHeight: Dp? = null,
    content: @Composable (index: Int) -> Unit,
) {
    val metrics = remember { HomeGridMetrics() }
    val density = LocalDensity.current
    val gridCoordinates = remember { HomeGridCoordinates() }
    val editHomeRoot = LocalEditHomeRoot.current
    val highlight = LocalFoldOsTokens.current.accent
    val dropModifier: Modifier = if (dropTarget != null) {
        Modifier
            .onGloballyPositioned { gridCoordinates.coordinates = it }
            .dragTarget(
                controller = dropTarget.controller,
                id = dropTarget.id,
                accepts = { true },
                onDrop = { payload, pointerInRoot ->
                    val local = gridCoordinates.toLocal(pointerInRoot)
                    if (local != null) {
                        dropTarget.onDrop(
                            payload,
                            metrics.colAt(local.x.roundToInt()),
                            metrics.rowAt(local.y.roundToInt()),
                        )
                    }
                },
            )
            .drawWithContent {
                drawContent()
                if (dropTarget.controller.hoverTargetId.value == dropTarget.id) {
                    val local = gridCoordinates.toLocal(dropTarget.controller.pointerInRoot.value)
                    if (local != null) {
                        val col = metrics.colAt(local.x.roundToInt())
                        val row = metrics.rowAt(local.y.roundToInt())
                        drawRoundRect(
                            color = highlight.copy(alpha = 0.28f),
                            topLeft = Offset(metrics.startX(col).toFloat(), (row * metrics.cellH).toFloat()),
                            size = Size(metrics.cellW.toFloat(), metrics.cellH.toFloat()),
                            cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                        )
                    }
                }
            }
    } else {
        Modifier
    }

    val positioned = if (geometryScreen != null) {
        Modifier.onGloballyPositioned { coordinates ->
            // The window position of the UNSCALED grid. In edit mode the home is drawn scaled down;
            // measured against the home root (inside the scaling layer) the origin is the same at
            // every moment of the scale animation, so the edit overlay can map it itself.
            val homeRoot = editHomeRoot?.coordinates
            val origin = if (homeRoot != null && homeRoot.isAttached && coordinates.isAttached) {
                homeRoot.localPositionOf(coordinates, Offset.Zero)
            } else {
                coordinates.positionInWindow()
            }
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
    val latestCells by rememberUpdatedState(cells)
    // Empty-space long press. The hit test runs in the grid's own coordinate space (the pointer is
    // relative to this node), exactly: a press that lands on a child (whatever its span) is that
    // child's business (its tile opens its own menu or edit mode), so this detector ignores it and
    // never reports another cell. It only fires when nothing covers the point; the reported cell is
    // the exact one, or -1 / -1 for the hinge gutter / the unused edge of the grid.
    val press = if (onEmptyLongPress != null) {
        Modifier.pointerInput(metrics) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = true)
                if (metrics.itemIndexAt(latestCells, down.position.x, down.position.y) != null) {
                    return@awaitEachGesture
                }
                down.consume()
                val slop = viewConfiguration.touchSlop
                val early = withTimeoutOrNull<Boolean>(viewConfiguration.longPressTimeoutMillis) {
                    var over = false
                    while (!over) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null ||
                            !change.pressed ||
                            event.changes.count { it.pressed } > 1 ||
                            change.isConsumed ||
                            (change.position - down.position).getDistance() > slop
                        ) {
                            over = true
                        }
                    }
                    true
                }
                if (early != null) return@awaitEachGesture
                val cell = metrics.cellAt(down.position.x, down.position.y)
                latestEmptyLongPress?.invoke(cell?.first ?: -1, cell?.second ?: -1)
                // Swallow the rest of the gesture so nothing else reacts to the release.
                var stillDown = true
                while (stillDown) {
                    val event = awaitPointerEvent()
                    event.changes.forEach { it.consume() }
                    stillDown = event.changes.any { it.pressed }
                }
            }
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
        modifier = modifier.then(positioned).then(press).then(dropModifier),
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
        metrics.tileHitHeightPx = tileHitHeight?.roundToPx() ?: Int.MAX_VALUE
        metrics.widthPx = width
        metrics.heightPx = height
        // `this.density`: the composable's own `density` local would otherwise shadow the scope's Float.
        metrics.density = this.density

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
