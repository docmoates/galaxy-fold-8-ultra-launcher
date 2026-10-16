package com.foldos.launcher.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.foldos.launcher.core.fold.FoldState
import com.foldos.launcher.core.model.DockPosition
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import kotlin.math.max

/**
 * Where the item grid of the current home page sits, in dp, for the edit overlay: [cols] x [rows]
 * cells of [cellWidthDp] x [cellHeightDp], with the grid's top-left corner at
 * ([originXDp], [originYDp]) measured from the window's top-left corner. On the inner screen the
 * two middle columns are separated by the hinge gutter ([gutterDp]), which [cellAt] accounts for.
 */
@Immutable
data class GridGeometry(
    val cols: Int,
    val rows: Int,
    val cellWidthDp: Int,
    val cellHeightDp: Int,
    val originXDp: Int,
    val originYDp: Int,
    val gutterDp: Int = 0,
) {
    /** Total grid width in dp, gutter included. */
    val widthDp: Int get() = cols * cellWidthDp + gutterDp

    /** Total grid height in dp. */
    val heightDp: Int get() = rows * cellHeightDp

    /**
     * The (col, row) under a point given in window dp, clamped to the grid; null when the grid has
     * no size yet.
     */
    fun cellAt(windowXDp: Float, windowYDp: Float): Pair<Int, Int>? {
        if (cols <= 0 || rows <= 0 || cellWidthDp <= 0 || cellHeightDp <= 0) return null
        val x = windowXDp - originXDp
        val y = windowYDp - originYDp
        val half = cols / 2
        val gutterStart = half * cellWidthDp
        val col = when {
            gutterDp > 0 && cols % 2 == 0 && x >= gutterStart + gutterDp -> ((x - gutterDp) / cellWidthDp).toInt()
            gutterDp > 0 && cols % 2 == 0 && x >= gutterStart -> half
            else -> (x / cellWidthDp).toInt()
        }.coerceIn(0, cols - 1)
        val row = (y / cellHeightDp).toInt().coerceIn(0, rows - 1)
        return col to row
    }

    /** Top-left corner of a cell in window dp. */
    fun cellOrigin(col: Int, row: Int): Pair<Int, Int> {
        val half = cols / 2
        val extra = if (gutterDp > 0 && cols % 2 == 0 && col >= half) gutterDp else 0
        return (originXDp + col * cellWidthDp + extra) to (originYDp + row * cellHeightDp)
    }
}

/**
 * The geometry the home grids actually laid out with, published by `HomeCellGrid` for the settled
 * current page of each screen. [rememberGridGeometry] prefers this over the estimate.
 */
internal object HomeGeometryStore {
    val measured: SnapshotStateMap<ScreenKind, GridGeometry> = mutableStateMapOf()

    fun publish(screen: ScreenKind, geometry: GridGeometry) {
        if (measured[screen] != geometry) measured[screen] = geometry
    }
}

/**
 * The item grid geometry of [screen] for the edit overlay. Returns the geometry the grid last laid
 * out with when the home screen of [screen] has been composed, otherwise an estimate from the
 * window size and the settings ([estimateGridGeometry]; [pageStyle] is the style of the current page when
 * pages carry their own). Recomposes when either changes.
 */
@Composable
fun rememberGridGeometry(
    screen: ScreenKind,
    settings: LauncherSettings,
    fold: FoldState,
    pageStyle: HomeStyle? = null,
): GridGeometry {
    val measured = HomeGeometryStore.measured[screen]
    if (measured != null) return measured
    return remember(screen, settings, fold, pageStyle) { estimateGridGeometry(screen, settings, fold, pageStyle) }
}

/**
 * Estimates the item grid of [screen] from the window size in [fold] and the home settings, using
 * the same paddings `HomeScreen` lays out with (16dp page sides, 10dp vertical, 50+14dp search bar,
 * 27dp page indicator, a dock of icon size + chrome) and typical system bar heights.
 */
fun estimateGridGeometry(
    screen: ScreenKind,
    settings: LauncherSettings,
    fold: FoldState,
    pageStyle: HomeStyle? = null,
): GridGeometry {
    val style = pageStyle ?: settings.styleFor(screen)
    val inner = screen != ScreenKind.COVER
    val cols = when (style) {
        HomeStyle.GRID -> settings.effectiveColsFor(screen).coerceAtLeast(1)
        HomeStyle.DASHBOARD -> dashboardColumns(screen)
        HomeStyle.LIST -> 1
        HomeStyle.HYBRID -> 4
    }
    val dock = settings.dockFor(screen)
    val dockOn = settings.feature(LauncherSettings.FEATURE_DOCK)
    // The bottom dock's search add-on replaces the top search bar (LauncherRoot: topSearchBarOn).
    val searchOn = settings.feature(LauncherSettings.FEATURE_SEARCH) &&
        !(dockOn && dock.pos != DockPosition.RAIL && dock.search)
    val bottomDock = dockOn && dock.pos == DockPosition.BOTTOM
    val topDp = EST_STATUS_BAR_DP + 10 + (if (searchOn) 64 else 0)
    val dockDp = if (bottomDock) settings.effectiveIconSizeDp + (if (dock.labels || dock.two) 56 else 34) else 0
    val bottomDp = 10 + 27 + dockDp + EST_NAV_BAR_DP
    val gutterDp = if (inner && fold.hingeBounds != null && cols % 2 == 0) HingeGutterWidth.value.toInt() else 0
    val widthDp = (fold.widthDp - 2 * HomeSidePadding.value.toInt() - gutterDp).coerceAtLeast(cols)
    val heightDp = (fold.heightDp - topDp - bottomDp).coerceAtLeast(1)
    val labelDp = if (settings.effectiveLabels) (if (settings.largeIcons) 32 else 24) else 0
    val rows = when (style) {
        HomeStyle.GRID -> max(1, heightDp / (settings.effectiveIconSizeDp + labelDp + 20))
        HomeStyle.DASHBOARD -> dashboardRows(defaultDashboardItems(screen, 0L))
        HomeStyle.LIST -> max(1, heightDp / 46)
        HomeStyle.HYBRID -> max(1, heightDp / (settings.effectiveIconSizeDp + labelDp + 20))
    }
    return GridGeometry(
        cols = cols,
        rows = rows,
        cellWidthDp = max(1, widthDp / cols),
        cellHeightDp = max(1, heightDp / rows),
        originXDp = HomeSidePadding.value.toInt(),
        originYDp = topDp,
        gutterDp = gutterDp,
    )
}

private const val EST_STATUS_BAR_DP: Int = 40
private const val EST_NAV_BAR_DP: Int = 24
