package com.foldos.launcher.a11y

import androidx.compose.runtime.Immutable
import androidx.compose.ui.semantics.CustomAccessibilityAction
import com.foldos.launcher.core.AppGraph
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.ItemKind
import kotlinx.coroutines.launch

/**
 * What [homeItemAccessActions] needs to know about the grid an item sits in.
 *
 * @property siblings every item on the same page (stack mates included).
 * @property cols grid columns.
 * @property rows grid rows when known (null = unknown: the item can then not grow downwards).
 */
@Immutable
class HomeItemGridInfo(
    val siblings: List<HomeItem>,
    val cols: Int,
    val rows: Int?,
)

/**
 * The TalkBack "actions" menu of a home item: Open menu (the long-press menu), Move left / right /
 * up / down, Resize (widgets: wider / narrower / taller / shorter) and Remove from home screen.
 * They are the keyboard-and-screen-reader equivalents of the drag and resize handles in edit mode,
 * so a move or resize only offers itself when the target cells are inside the grid and free.
 *
 * Moves and resizes go straight to `LayoutRepository.moveItem` / `resizeItem` on the app scope
 * (stack mates follow, as everywhere else). Items that are not saved yet (`id <= 0`), free-placed
 * items and items without [grid] get only the menu and remove actions.
 *
 * @param onOpenMenu opens the item's long-press popup; null leaves the action out.
 * @param onDelete removes the item (including releasing a hosted widget id); null leaves it out.
 * @param announce speaks the outcome ("Moved left") without moving focus.
 */
fun homeItemAccessActions(
    item: HomeItem,
    grid: HomeItemGridInfo?,
    graph: AppGraph,
    announce: (String) -> Unit,
    onOpenMenu: (() -> Unit)?,
    onDelete: (() -> Unit)?,
): List<CustomAccessibilityAction> {
    val actions = ArrayList<CustomAccessibilityAction>(10)
    if (onOpenMenu != null) {
        actions.add(CustomAccessibilityAction("Open menu") { onOpenMenu(); true })
    }
    if (grid != null && item.id > 0L && !item.free) {
        val slotKey = item.stackId ?: item.id
        val occupied = HashSet<Pair<Int, Int>>()
        for (other in grid.siblings) {
            if ((other.stackId ?: other.id) == slotKey) continue
            if (other.free || other.inFolder) continue
            val sx = other.spanX.coerceAtLeast(1)
            val sy = other.spanY.coerceAtLeast(1)
            for (r in other.row until other.row + sy) {
                for (c in other.col until other.col + sx) occupied.add(c to r)
            }
        }
        val maxRows = grid.rows ?: (item.row + item.spanY.coerceAtLeast(1))

        fun fits(col: Int, row: Int, spanX: Int, spanY: Int): Boolean {
            if (col < 0 || row < 0 || spanX < 1 || spanY < 1) return false
            if (col + spanX > grid.cols || row + spanY > maxRows) return false
            for (r in row until row + spanY) {
                for (c in col until col + spanX) if ((c to r) in occupied) return false
            }
            return true
        }

        val spanX = item.spanX.coerceAtLeast(1)
        val spanY = item.spanY.coerceAtLeast(1)

        fun move(label: String, spoken: String, dCol: Int, dRow: Int) {
            val col = item.col + dCol
            val row = item.row + dRow
            if (!fits(col, row, spanX, spanY)) return
            actions.add(
                CustomAccessibilityAction(label) {
                    graph.appScope.launch { runCatching { graph.layouts.moveItem(item.id, item.pageId, col, row) } }
                    announce(spoken)
                    true
                },
            )
        }
        move("Move left", "Moved left", -1, 0)
        move("Move right", "Moved right", 1, 0)
        move("Move up", "Moved up", 0, -1)
        move("Move down", "Moved down", 0, 1)

        if (item.kind == ItemKind.WIDGET || item.kind == ItemKind.CARD) {
            fun resize(label: String, spoken: String, newSpanX: Int, newSpanY: Int) {
                if (!fits(item.col, item.row, newSpanX, newSpanY)) return
                actions.add(
                    CustomAccessibilityAction(label) {
                        graph.appScope.launch { runCatching { graph.layouts.resizeItem(item.id, newSpanX, newSpanY) } }
                        announce(spoken)
                        true
                    },
                )
            }
            resize("Resize wider", "Widget is wider", spanX + 1, spanY)
            resize("Resize narrower", "Widget is narrower", spanX - 1, spanY)
            resize("Resize taller", "Widget is taller", spanX, spanY + 1)
            resize("Resize shorter", "Widget is shorter", spanX, spanY - 1)
        }
    }
    if (onDelete != null) {
        actions.add(
            CustomAccessibilityAction("Remove from home screen") {
                onDelete()
                announce("Removed")
                true
            },
        )
    }
    return actions
}
