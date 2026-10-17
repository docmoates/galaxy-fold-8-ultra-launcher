package com.foldos.launcher.data.db

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.ItemKind

/**
 * Room 3 -> 4 (Round 2.0): turns the four per-page styles into free-canvas content, with plain SQL
 * on the half-migrated database (no entities, so this never depends on the current model).
 *
 * Per page, by its old `style`:
 * - GRID: the items stay where they are.
 * - DASHBOARD: the page's widgets were laid out on a 2-column (cover) / 4-column (inner) widget grid; they
 *   are re-spanned onto the page grid (4 / 6 columns: a cover widget doubles its column and width, an
 *   inner one gets 2 cells for a 1-wide and 3 for a 2-wide widget) and then packed from the top in
 *   reading order, widgets first, then the apps / folders / pairs the dashboard used to hide.
 * - LIST: one apps card in list style (`card_style = apps-list`, title "Apps") takes the page's apps,
 *   pairs and folders as children; it covers the page (6 rows), or 4 rows when the page also has
 *   widgets, which are packed below it.
 * - HYBRID: a widget card (rows 0-2) holds the page's widgets and an apps card (rows 3-5) its apps.
 *
 * Every page is finally packed so no two slots overlap (a stack counts as one slot). The page `style`
 * column is left as it was: it now only records the starter the page came from.
 */
internal object CanvasMigration {

    private const val ROWS = 6

    private class Row(
        val id: Long,
        val kind: String,
        var col: Int,
        var row: Int,
        var spanX: Int,
        var spanY: Int,
        val stackId: Long?,
        val free: Boolean,
    )

    private class Page(val id: Long, val screen: String, val style: String)

    fun convertPages(db: SupportSQLiteDatabase) {
        val pages = ArrayList<Page>()
        db.query("SELECT id, screen, style FROM pages").use { c ->
            while (c.moveToNext()) pages.add(Page(c.getLong(0), c.getString(1) ?: "COVER", c.getString(2) ?: "GRID"))
        }
        for (page in pages) {
            val cols = if (page.screen == "COVER") 4 else 6
            runCatching { convertPage(db, page, cols) }
        }
    }

    private fun convertPage(db: SupportSQLiteDatabase, page: Page, cols: Int) {
        val items = topLevelItems(db, page.id)
        when (page.style) {
            "DASHBOARD" -> {
                for (item in items) {
                    if (item.kind != ItemKind.WIDGET.name || item.free) continue
                    if (cols == 4) {
                        item.col *= 2
                        item.spanX = (item.spanX * 2).coerceIn(1, cols)
                    } else {
                        item.spanX = (if (item.spanX >= 2) 3 else 2).coerceAtMost(cols)
                        item.col = (item.col * 3 / 2).coerceIn(0, cols - item.spanX)
                    }
                }
                // Widgets first (they were the visible content), then the apps the dashboard hid.
                val ordered = items.filter { it.kind == ItemKind.WIDGET.name } + items.filter { it.kind != ItemKind.WIDGET.name }
                pack(db, ordered, cols)
            }
            "LIST" -> {
                val holdable = items.filter { it.kind != ItemKind.WIDGET.name && it.kind != ItemKind.CARD.name }
                val widgets = items.filter { it.kind == ItemKind.WIDGET.name }
                val cardRows = if (widgets.isEmpty()) ROWS else 4
                val cardId = insertCard(db, page.id, 0, 0, cols, cardRows, HomeItem.CARD_APPS_LIST, "Apps")
                holdable.sortedWith(compareBy({ it.row }, { it.col })).forEachIndexed { order, item ->
                    placeInCard(db, item.id, cardId, page.id, order)
                }
                widgets.forEach { it.row += cardRows }
                val card = Row(cardId, ItemKind.CARD.name, 0, 0, cols, cardRows, null, false)
                pack(db, listOf(card) + widgets, cols)
            }
            "HYBRID" -> {
                val holdable = items.filter { it.kind != ItemKind.WIDGET.name && it.kind != ItemKind.CARD.name }
                val widgets = items.filter { it.kind == ItemKind.WIDGET.name }
                val widgetCard = insertCard(db, page.id, 0, 0, cols, 3, HomeItem.CARD_WIDGETS, null)
                widgets.sortedWith(compareBy({ it.row }, { it.col })).forEachIndexed { order, item ->
                    placeInCard(db, item.id, widgetCard, page.id, order)
                }
                val appCard = insertCard(db, page.id, 0, 3, cols, ROWS - 3, HomeItem.CARD_APPS, null)
                holdable.sortedWith(compareBy({ it.row }, { it.col })).forEachIndexed { order, item ->
                    placeInCard(db, item.id, appCard, page.id, order)
                }
            }
            else -> pack(db, items, cols)
        }
    }

    private fun topLevelItems(db: SupportSQLiteDatabase, pageId: Long): List<Row> {
        val result = ArrayList<Row>()
        db.query(
            "SELECT id, kind, grid_col, grid_row, span_x, span_y, stack_id, free FROM items " +
                "WHERE page_id = ? AND parent_id IS NULL ORDER BY grid_row ASC, grid_col ASC, stack_index ASC",
            arrayOf<Any>(pageId),
        ).use { c ->
            while (c.moveToNext()) {
                result.add(
                    Row(
                        id = c.getLong(0),
                        kind = c.getString(1) ?: "APP",
                        col = c.getInt(2),
                        row = c.getInt(3),
                        spanX = c.getInt(4).coerceAtLeast(1),
                        spanY = c.getInt(5).coerceAtLeast(1),
                        stackId = if (c.isNull(6)) null else c.getLong(6),
                        free = c.getInt(7) != 0,
                    ),
                )
            }
        }
        return result
    }

    private fun insertCard(
        db: SupportSQLiteDatabase,
        pageId: Long,
        col: Int,
        row: Int,
        spanX: Int,
        spanY: Int,
        style: String,
        title: String?,
    ): Long {
        val values = ContentValues().apply {
            put("page_id", pageId)
            put("kind", ItemKind.CARD.name)
            put("grid_col", col)
            put("grid_row", row)
            put("span_x", spanX)
            put("span_y", spanY)
            put("stack_index", 0)
            put("free", 0)
            put("card_style", style)
            put("card_clear", 0)
            if (title != null) put("title", title)
        }
        return db.insert("items", SQLiteDatabase.CONFLICT_REPLACE, values)
    }

    private fun placeInCard(db: SupportSQLiteDatabase, id: Long, cardId: Long, pageId: Long, order: Int) {
        db.execSQL(
            "UPDATE items SET parent_id = ?, page_id = ?, grid_col = 0, grid_row = ?, stack_id = NULL, stack_index = 0, " +
                "free = 0, x_dp = NULL, y_dp = NULL, w_dp = NULL, h_dp = NULL WHERE id = ?",
            arrayOf<Any>(cardId, pageId, order, id),
        )
    }

    /**
     * Packs [items] (page order) onto a [cols]-wide grid of unbounded height: a slot that fits where it
     * is keeps its cell, every other slot takes the first free spot in reading order. Stacks move
     * together; free-placed items are left alone.
     */
    private fun pack(db: SupportSQLiteDatabase, items: List<Row>, cols: Int) {
        val occupied = HashSet<Pair<Int, Int>>()
        fun fits(col: Int, row: Int, sx: Int, sy: Int): Boolean {
            if (col < 0 || row < 0 || col + sx > cols) return false
            for (r in row until row + sy) for (c in col until col + sx) if ((c to r) in occupied) return false
            return true
        }
        fun mark(col: Int, row: Int, sx: Int, sy: Int) {
            for (r in row until row + sy) for (c in col until col + sx) occupied.add(c to r)
        }
        val slots = LinkedHashMap<Long, MutableList<Row>>()
        for (item in items) {
            if (item.free) continue
            slots.getOrPut(item.stackId ?: -item.id) { ArrayList() }.add(item)
        }
        for (slot in slots.values) {
            val a = slot.first()
            val sx = a.spanX.coerceIn(1, cols)
            val sy = a.spanY.coerceAtLeast(1)
            var col = a.col
            var row = a.row
            if (!fits(col, row, sx, sy)) {
                row = 0
                search@ while (true) {
                    col = 0
                    while (col + sx <= cols) {
                        if (fits(col, row, sx, sy)) break@search
                        col++
                    }
                    row++
                }
            }
            mark(col, row, sx, sy)
            for (member in slot) {
                if (member.col != col || member.row != row || member.spanX != sx) {
                    db.execSQL(
                        "UPDATE items SET grid_col = ?, grid_row = ?, span_x = ? WHERE id = ?",
                        arrayOf<Any>(col, row, sx, member.id),
                    )
                }
            }
        }
    }
}
