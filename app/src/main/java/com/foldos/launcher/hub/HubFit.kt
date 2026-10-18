package com.foldos.launcher.hub

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.floor
import kotlin.math.max

/*
 * 1.6.0: every Hub tab fits one screen with no vertical scrolling. The tabs measure the height they
 * are given (BoxWithConstraints), ask these pure functions how many rows fit and drop the rest.
 * Nothing here touches Compose state, so the logic can be read (and unit tested) on its own.
 */

/** Gap between rows / cards in the fitted layouts. */
internal val HubFitGap: Dp = 10.dp

/**
 * How many rows of [rowHeight] (with [gap] between neighbours) fit in [availableHeight]; never negative.
 * `fitCount(100.dp, 30.dp, 5.dp)` = 3 (30 + 5 + 30 + 5 + 30 = 100).
 */
internal fun fitCount(availableHeight: Dp, rowHeight: Dp, gap: Dp = 0.dp): Int =
    fitCountValue(availableHeight.value, rowHeight.value, gap.value)

/** Same as [fitCount] on plain numbers (dp or px, as long as all three use the same unit). */
internal fun fitCountValue(available: Float, row: Float, gap: Float = 0f): Int {
    if (row <= 0f || available <= 0f) return 0
    return max(0, floor((available + gap) / (row + gap) + 1e-4f).toInt())
}

/** One day group of the agenda that survived [planAgenda]: show the first [rows] rows of group [index]. */
internal data class AgendaGroupPlan(val index: Int, val rows: Int)

/** What [planAgenda] decided: the groups to draw and whether the footer link still fits. */
internal data class AgendaPlan(val groups: List<AgendaGroupPlan>, val footer: Boolean)

/**
 * Fills [availableDp] with day groups in order. A group needs its header plus at least one row;
 * a group that is cut short ends the list (later days are not shown above an incomplete day).
 * [rowCounts] holds the event count per non-empty day. The footer is only reported when it fits
 * after everything else.
 */
internal fun planAgenda(
    rowCounts: List<Int>,
    availableDp: Float,
    headerDp: Float,
    rowDp: Float,
    gapDp: Float,
    footerDp: Float,
): AgendaPlan {
    val groups = ArrayList<AgendaGroupPlan>()
    var used = 0f
    for ((index, count) in rowCounts.withIndex()) {
        if (count <= 0) continue
        val lead = if (groups.isEmpty()) 0f else gapDp
        val room = availableDp - used - lead - headerDp
        val rows = minOf(count, fitCountValue(room, rowDp))
        if (rows <= 0) break
        groups += AgendaGroupPlan(index, rows)
        used += lead + headerDp + rows * rowDp
        if (rows < count) break
    }
    val footer = groups.isNotEmpty() && availableDp - used >= gapDp + footerDp
    return AgendaPlan(groups, footer)
}

/**
 * Splits [total] items over [columns] columns of up to [perColumn] rows each, filling the first
 * column first. Returns the item count of each column.
 */
internal fun splitIntoColumns(total: Int, columns: Int, perColumn: Int): List<Int> {
    var left = total.coerceAtLeast(0)
    return List(columns.coerceAtLeast(1)) {
        val take = minOf(left, perColumn.coerceAtLeast(0))
        left -= take
        take
    }
}

/** Text-driven heights grow with the font scale; never shrink below 1x. */
internal fun Dp.scaledBy(fontScale: Float): Dp = this * fontScale.coerceAtLeast(1f)
