package com.foldos.launcher.data

import androidx.room.withTransaction
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.repo.LayoutRepository
import com.foldos.launcher.data.db.ItemEntity
import com.foldos.launcher.data.db.LayoutDao
import com.foldos.launcher.data.db.LayoutDatabase
import com.foldos.launcher.data.db.PageEntity
import com.foldos.launcher.home.StarterLayouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [LayoutRepository] on top of [LayoutDatabase]. On first run each screen gets [DEFAULT_PAGE_COUNT]
 * pages (one populated, one empty); the first page is seeded from the chosen starter layout
 * ([StarterLayouts]: apps grid, widgets, an apps card in list style, or loose widgets over an app card)
 * for the caller's column count (Settings > Home > Cover / Inner columns) and visible rows.
 * Without a column count the LauncherSettings defaults apply (4 columns on the cover, 6 inside).
 *
 * Stacks use the first (anchor) item's row id as the stack id, so stack ids never collide with
 * each other and survive the anchor being flipped to the back.
 */
class LayoutRepositoryImpl(
    private val db: LayoutDatabase,
    private val dockStore: DockAppsStore,
    /**
     * Directory for the per-screen "legacy duplicate pages already tidied" marker files (the app's
     * `noBackupFilesDir`, so a restore never carries a marker over). Null runs the tidy-up every time.
     */
    private val markerDir: File? = null,
) : LayoutRepository {

    private val dao: LayoutDao = db.layoutDao()

    override fun pages(screen: ScreenKind): Flow<List<HomePage>> =
        dao.pagesByScreen(screen.name).map { rows -> rows.map(PageEntity::toModel) }

    override fun items(pageId: Long): Flow<List<HomeItem>> =
        dao.itemsByPage(pageId).map { rows -> rows.map(ItemEntity::toModel) }

    override fun children(folderId: Long): Flow<List<HomeItem>> =
        dao.childrenOf(folderId).map { rows -> rows.map(ItemEntity::toModel) }

    /**
     * First run of a screen (it has no pages yet): one populated page (a dashboard gets the default
     * built-in widgets, every other style a grid of the first apps) plus one empty page. Later calls
     * never put anything back: a page the user emptied or restyled stays as it is. A screen that an
     * earlier version seeded with several identical pages is tidied up once, guarded by a persisted
     * marker so a page the user adds on purpose later is never mistaken for a legacy duplicate (see
     * [removeLegacyDuplicatePages]).
     */
    override suspend fun ensureDefaultLayout(screen: ScreenKind, apps: List<AppInfo>, columns: Int?, style: HomeStyle?, rows: Int?) {
        ensureDefaultLayoutInner(screen, apps, columns, style, rows)
    }


    private suspend fun ensureDefaultLayoutInner(screen: ScreenKind, apps: List<AppInfo>, columns: Int?, style: HomeStyle?, rows: Int?) {
        val marker = markerDir?.let { File(it, "foldos_legacy_pages_checked_${screen.name}") }
        if (marker != null && withContext(Dispatchers.IO) { marker.exists() }) {
            // Already tidied (or created by this version): only seed a screen that has no pages yet.
            db.withTransaction {
                if (dao.countPages(screen.name) == 0) createDefaultPages(screen, apps, columns, style, rows)
            }
            return
        }
        db.withTransaction {
            if (dao.countPages(screen.name) > 0) {
                removeLegacyDuplicatePages(screen)
            } else {
                createDefaultPages(screen, apps, columns, style, rows)
            }
        }
        if (marker != null) {
            withContext(Dispatchers.IO) {
                try {
                    marker.parentFile?.mkdirs()
                    marker.createNewFile()
                } catch (e: java.io.IOException) {
                    // Without a marker the (idempotent) tidy-up simply runs again next launch.
                }
            }
        }
    }

    private suspend fun createDefaultPages(screen: ScreenKind, apps: List<AppInfo>, columns: Int?, style: HomeStyle?, rows: Int?) {
        val pageStyle = style ?: defaultStyle(screen)
        var firstPageId = 0L
        for (index in 0 until DEFAULT_PAGE_COUNT) {
            val id = dao.upsertPage(PageEntity(screen = screen, index = index, style = pageStyle))
            if (index == 0) firstPageId = id
        }
        // Real, editable canvas content from the starter (apps, widgets and cards), see StarterLayouts.
        val cols = columns?.coerceAtLeast(1) ?: defaultColumns(screen)
        StarterLayouts.write(
            layouts = this,
            pageId = firstPageId,
            seed = StarterLayouts.seed(pageStyle, screen, firstPageId, apps, cols, rows ?: StarterLayouts.DEFAULT_ROWS),
        )
    }

    /**
     * Version 1.0.0 seeded three pages per screen and refilled every empty dashboard page with the
     * default widgets, so an inner screen ended up with three identical dashboards. Deletes the pages
     * after the first one whose items are identical (same kind, app / built-in, cell and span) to the
     * first page's and, only when such duplicates exist, the extra trailing empty pages: at most one
     * trailing empty page is kept. A screen without duplicates is never trimmed. A page that differs in any way or
     * holds an item or a hosted app widget is never touched. When that leaves a single page, one empty
     * page of the same style is added back, so the result is the same "one populated page plus one
     * empty page" a fresh install gets. Running it again finds nothing to do.
     */
    private suspend fun removeLegacyDuplicatePages(screen: ScreenKind) {
        val key = screen.name
        val pages = dao.pagesByScreenOnce(key)
        if (pages.size < 2) return
        val first = pages.first()
        val firstItems = dao.allItemsByPageOnce(first.id)
        if (firstItems.isEmpty() || firstItems.any { it.appWidgetId != null }) return
        val firstSignature = pageSignature(firstItems)
        val duplicates = pages.drop(1).filter { page ->
            val items = dao.allItemsByPageOnce(page.id)
            items.isNotEmpty() && items.none { it.appWidgetId != null } && pageSignature(items) == firstSignature
        }
        val duplicateIds = duplicates.map { it.id }.toSet()
        // Trailing empty pages (after the duplicates are gone): keep the first one, drop the rest.
        val kept = pages.filter { it.id !in duplicateIds }
        val trailingEmpty = kept.drop(1).reversed()
            .takeWhile { page -> dao.allItemsByPageOnce(page.id).isEmpty() }
            .reversed()
        // Only a screen that really has 1.0.0's identical duplicate pages is trimmed: without a duplicate,
        // extra empty pages were added by the user (this runs once on layouts of any version, e.g. after
        // updating from 1.1.0 or restoring a backup) and are left alone.
        val extraEmpty = if (duplicates.isEmpty()) emptyList() else trailingEmpty.drop(1)
        if (duplicates.isEmpty() && extraEmpty.isEmpty()) return
        (duplicates + extraEmpty).forEach { dao.deletePage(it.id) }
        val remaining = dao.pagesByScreenOnce(key)
        remaining.forEachIndexed { index, page ->
            if (page.index != index) dao.setPageIndex(page.id, index)
        }
        if (remaining.size == 1) {
            dao.upsertPage(PageEntity(screen = screen, index = 1, style = remaining.first().style))
        }
    }

    /** What a page shows, independent of row ids: sorted "kind|app|built-in|cell|span|in-folder" strings. */
    private fun pageSignature(items: List<ItemEntity>): List<String> =
        items.map { item ->
            listOf(
                item.kind.name,
                item.packageName.orEmpty(),
                item.activityName.orEmpty(),
                item.builtinId.orEmpty(),
                item.col,
                item.row,
                item.spanX,
                item.spanY,
                if (item.parentId != null) "child" else "top",
            ).joinToString("|")
        }.sorted()

    override suspend fun upsertItem(item: HomeItem) {
        dao.upsertItem(ItemEntity.fromModel(item))
    }

    /** Deletes the item; a folder takes its children with it, a card its children and their children. */
    override suspend fun deleteItem(id: Long) {
        db.withTransaction {
            val item = dao.itemById(id)
            if (item?.kind == ItemKind.CARD) dao.deleteGrandchildren(id)
            if (item?.kind == ItemKind.FOLDER || item?.kind == ItemKind.CARD) dao.deleteChildren(id)
            dao.deleteItem(id)
        }
    }

    override suspend fun moveItem(id: Long, pageId: Long, col: Int, row: Int) {
        db.withTransaction {
            val item = dao.itemById(id)
            val stackId = item?.stackId
            if (stackId != null) {
                dao.moveStack(stackId, pageId, col, row)
            } else {
                dao.moveItem(id, pageId, col, row)
            }
            // Folder / card children follow their container so a page delete still cascades to them.
            if (item?.kind == ItemKind.FOLDER || item?.kind == ItemKind.CARD) dao.moveChildren(id, pageId)
            if (item?.kind == ItemKind.CARD) dao.moveGrandchildren(id, pageId)
        }
    }

    override suspend fun resizeItem(id: Long, spanX: Int, spanY: Int) {
        val sx = spanX.coerceAtLeast(1)
        val sy = spanY.coerceAtLeast(1)
        db.withTransaction {
            val stackId = dao.itemById(id)?.stackId
            if (stackId != null) {
                dao.resizeStack(stackId, sx, sy)
            } else {
                dao.resizeItem(id, sx, sy)
            }
        }
    }

    override suspend fun addPage(screen: ScreenKind, style: HomeStyle): HomePage =
        db.withTransaction {
            val index = dao.maxPageIndex(screen.name) + 1
            val id = dao.upsertPage(PageEntity(screen = screen, index = index, style = style))
            HomePage(id = id, screen = screen, index = index, style = style)
        }

    override suspend fun setPageStyle(pageId: Long, style: HomeStyle) {
        dao.setPageStyle(pageId, style.name)
    }

    /**
     * Deletes a page and every item on it (folder children included), then re-indexes the rest so
     * the page indices stay 0..n-1. A screen always keeps at least one page: deleting its only page
     * does nothing. An unknown id does nothing either.
     */
    override suspend fun deletePage(pageId: Long) {
        db.withTransaction {
            val page = dao.pageById(pageId) ?: return@withTransaction
            if (dao.countPages(page.screen.name) <= 1) return@withTransaction
            // Explicit as well as the foreign key cascade, so the items are gone whatever the pragma says.
            dao.deleteItemsOfPage(pageId)
            dao.deletePage(pageId)
            dao.pagesByScreenOnce(page.screen.name).forEachIndexed { index, remaining ->
                if (remaining.index != index) dao.setPageIndex(remaining.id, index)
            }
        }
    }

    override suspend fun createStack(itemIds: List<Long>): Long {
        val ids = itemIds.distinct()
        require(ids.isNotEmpty()) { "createStack needs at least one item" }
        return db.withTransaction {
            val found = dao.itemsByIds(ids).associateBy { it.id }
            val ordered = ids.mapNotNull { found[it] }
            val anchor = ordered.firstOrNull() ?: throw IllegalArgumentException("No such items: $ids")
            val stackId = anchor.stackId ?: anchor.id
            ordered.forEachIndexed { index, item ->
                dao.placeInStack(
                    id = item.id,
                    stackId = stackId,
                    stackIndex = index,
                    pageId = anchor.pageId,
                    col = anchor.col,
                    row = anchor.row,
                    spanX = anchor.spanX,
                    spanY = anchor.spanY,
                )
            }
            stackId
        }
    }

    override suspend fun createFolder(pageId: Long, col: Int, row: Int, title: String, itemIds: List<Long>): Long =
        db.withTransaction {
            val folderId = dao.upsertItem(
                ItemEntity(
                    pageId = pageId,
                    kind = ItemKind.FOLDER,
                    col = col,
                    row = row,
                    title = title,
                ),
            )
            itemIds.distinct().forEachIndexed { index, itemId ->
                // Children keep an order through grid_row (index); grid_col is unused inside a folder.
                dao.placeInFolder(id = itemId, parentId = folderId, pageId = pageId, col = 0, row = index)
            }
            folderId
        }

    override suspend fun createPair(pageId: Long, col: Int, row: Int, a: AppInfo, b: AppInfo): Long =
        dao.upsertItem(
            ItemEntity(
                pageId = pageId,
                kind = ItemKind.PAIR,
                col = col,
                row = row,
                packageName = a.packageName,
                activityName = a.activityName,
                title = "${a.label} & ${b.label}",
                packageName2 = b.packageName,
                activityName2 = b.activityName,
            ),
        )

    override suspend fun removeFromFolder(itemId: Long, toPageId: Long, col: Int, row: Int) {
        db.withTransaction {
            val folderId = dao.itemById(itemId)?.parentId ?: return@withTransaction
            dao.removeFromFolder(id = itemId, pageId = toPageId, col = col, row = row)
            if (dao.countChildren(folderId) == 0) dao.deleteItem(folderId)
        }
    }

    override suspend fun allPagesWithItems(): List<Pair<HomePage, List<HomeItem>>> =
        db.withTransaction {
            dao.allPagesOnce()
                .sortedWith(compareBy({ it.screen.ordinal }, { it.index }))
                .map { page -> page.toModel() to dao.allItemsByPageOnce(page.id).map(ItemEntity::toModel) }
        }

    override suspend fun replaceAll(pages: List<Pair<HomePage, List<HomeItem>>>) {
        db.withTransaction {
            dao.deleteAllItems()
            dao.deleteAllPages()
            // Input ids are local keys: old item id -> freshly inserted row id.
            val newIds = HashMap<Long, Long>()
            val linked = ArrayList<Pair<Long, HomeItem>>()
            val newPageIds = HashMap<Long, Long>()
            // Pass 1: insert pages and items without relations so every item has its new id.
            for ((page, items) in pages) {
                val newPageId = dao.upsertPage(PageEntity(screen = page.screen, index = page.index, style = page.style))
                for (item in items) {
                    val newId = dao.upsertItem(
                        ItemEntity.fromModel(
                            item.copy(id = 0, pageId = newPageId, parentId = null, stackId = null, stackIndex = 0),
                        ),
                    )
                    if (item.id != 0L) newIds[item.id] = newId
                    if (item.parentId != null || item.stackId != null) {
                        linked += newId to item
                        newPageIds[newId] = newPageId
                    }
                }
            }
            // Pass 2: rewrite folder / stack relations to the new ids.
            for ((newId, item) in linked) {
                val parent = item.parentId?.let { newIds[it] }
                val stack = item.stackId?.let { newIds[it] }
                dao.upsertItem(
                    ItemEntity.fromModel(
                        item.copy(
                            id = newId,
                            pageId = newPageIds.getValue(newId),
                            parentId = parent,
                            stackId = stack,
                            stackIndex = if (stack != null) item.stackIndex else 0,
                        ),
                    ),
                )
            }
        }
    }

    override suspend fun placeApp(
        app: AppInfo,
        pageId: Long,
        col: Int,
        row: Int,
        cols: Int?,
        rows: Int?,
        growsRows: Boolean,
    ): Boolean {
        return db.withTransaction {
            val existing = dao.itemsByPageOnce(pageId)
            val occupied = HashSet<Pair<Int, Int>>()
            var maxCol = col.coerceAtLeast(0)
            var maxRow = row.coerceAtLeast(0)
            var lowest = -1
            for (item in existing) {
                val spanX = item.spanX.coerceAtLeast(1)
                val spanY = item.spanY.coerceAtLeast(1)
                for (r in item.row until item.row + spanY) {
                    for (c in item.col until item.col + spanX) occupied.add(c to r)
                }
                maxCol = maxOf(maxCol, item.col + spanX - 1)
                maxRow = maxOf(maxRow, item.row + spanY - 1)
                lowest = maxOf(lowest, item.row + spanY - 1)
            }
            val colLimit = cols?.coerceAtLeast(1) ?: (maxCol + 1)
            val rowLimit = rows?.coerceAtLeast(1) ?: (maxRow + 2)
            val wantedCol = col.coerceIn(0, colLimit - 1)
            val wantedRow = row.coerceIn(0, rowLimit - 1)

            var bestCol = -1
            var bestRow = -1
            var bestDistance = Int.MAX_VALUE
            for (r in 0 until rowLimit) {
                for (c in 0 until colLimit) {
                    if ((c to r) in occupied) continue
                    val distance = kotlin.math.abs(c - wantedCol) + kotlin.math.abs(r - wantedRow)
                    // Rows and columns are visited in increasing order, so a strict < keeps the lower row, then column, on ties.
                    if (distance < bestDistance) {
                        bestDistance = distance
                        bestCol = c
                        bestRow = r
                    }
                }
            }
            if (bestCol < 0) {
                // A page with a fixed row count never draws a row below the grid: refuse instead of storing it.
                if (!growsRows && rows != null) return@withTransaction false
                // The visible grid is full: start the row below the lowest item (dashboard / hybrid pages grow).
                bestCol = 0
                bestRow = lowest + 1
            }
            dao.upsertItem(
                ItemEntity(
                    pageId = pageId,
                    kind = ItemKind.APP,
                    col = bestCol,
                    row = bestRow,
                    packageName = app.packageName,
                    activityName = app.activityName,
                    title = app.label,
                ),
            )
            true
        }
    }

    override suspend fun setDockApps(screen: ScreenKind, appKeys: List<String>) {
        dockStore.set(screen, appKeys)
    }

    override fun dockApps(screen: ScreenKind): Flow<List<String>> = dockStore.apps(screen)

    // ---- Round 2.0: cards and page order ----

    override suspend fun createCard(
        pageId: Long,
        col: Int,
        row: Int,
        spanX: Int,
        spanY: Int,
        cardStyle: String,
        title: String?,
    ): Long = dao.upsertItem(
        ItemEntity(
            pageId = pageId,
            kind = ItemKind.CARD,
            col = col.coerceAtLeast(0),
            row = row.coerceAtLeast(0),
            spanX = spanX.coerceAtLeast(HomeItem.CARD_MIN_SPAN),
            spanY = spanY.coerceAtLeast(HomeItem.CARD_MIN_SPAN),
            title = title?.trim()?.takeIf { it.isNotEmpty() },
            cardStyle = cardStyle.takeIf { it in HomeItem.CARD_STYLES } ?: HomeItem.CARD_APPS,
        ),
    )

    override suspend fun moveIntoCard(itemId: Long, cardId: Long, order: Int) {
        db.withTransaction {
            val item = dao.itemById(itemId) ?: return@withTransaction
            val card = dao.itemById(cardId) ?: return@withTransaction
            if (item.kind == ItemKind.CARD || card.kind != ItemKind.CARD || itemId == cardId) return@withTransaction
            dao.placeInCard(id = itemId, parentId = cardId, pageId = card.pageId, row = order.coerceAtLeast(0))
            // A folder keeps its apps; they follow it onto the card's page.
            if (item.kind == ItemKind.FOLDER) dao.moveChildren(itemId, card.pageId)
        }
    }

    override suspend fun removeFromCard(itemId: Long, toPageId: Long, col: Int, row: Int) {
        db.withTransaction {
            val item = dao.itemById(itemId) ?: return@withTransaction
            val parentId = item.parentId ?: return@withTransaction
            if (dao.itemById(parentId)?.kind != ItemKind.CARD) return@withTransaction
            dao.removeFromFolder(id = itemId, pageId = toPageId, col = col.coerceAtLeast(0), row = row.coerceAtLeast(0))
            if (item.kind == ItemKind.FOLDER) dao.moveChildren(itemId, toPageId)
        }
    }

    override suspend fun movePage(pageId: Long, newIndex: Int) {
        db.withTransaction {
            val page = dao.pageById(pageId) ?: return@withTransaction
            val pages = dao.pagesByScreenOnce(page.screen.name).toMutableList()
            val from = pages.indexOfFirst { it.id == pageId }
            if (from < 0) return@withTransaction
            val to = newIndex.coerceIn(0, pages.lastIndex)
            if (from == to) return@withTransaction
            val moved = pages.removeAt(from)
            pages.add(to, moved)
            pages.forEachIndexed { index, p -> if (p.index != index) dao.setPageIndex(p.id, index) }
        }
    }

    /** Sets or clears (null) one folder's style override ("grid", "list" or "big"). */
    suspend fun setFolderStyle(folderId: Long, style: String?) {
        dao.setFolderStyle(folderId, style)
    }

    /** Inserts or replaces a page; an id of 0 means "assign one". Returns the row id. */
    suspend fun upsertPage(page: HomePage): Long = dao.upsertPage(PageEntity.fromModel(page))

    companion object {
        /** A fresh screen gets one populated page plus one empty page (both in the same style). */
        const val DEFAULT_PAGE_COUNT: Int = 2

        /** Mirrors the LauncherSettings defaults: grid on the cover, dashboard inside. */
        fun defaultStyle(screen: ScreenKind): HomeStyle = when (screen) {
            ScreenKind.COVER -> HomeStyle.GRID
            ScreenKind.INNER, ScreenKind.FLEX -> HomeStyle.DASHBOARD
        }

        /** Fallback column counts, equal to the LauncherSettings defaults (colsCover / colsInner). */
        fun defaultColumns(screen: ScreenKind): Int = when (screen) {
            ScreenKind.COVER -> 4
            ScreenKind.INNER, ScreenKind.FLEX -> 6
        }

        /** Rows of apps seeded on the first page. */
        fun defaultRows(screen: ScreenKind): Int = when (screen) {
            ScreenKind.COVER -> 4
            ScreenKind.INNER, ScreenKind.FLEX -> 4
        }

        /** Seeded app count with the fallback column count: 16 on the cover, 24 on the inner screen. */
        fun defaultAppCount(screen: ScreenKind): Int = defaultColumns(screen) * defaultRows(screen)
    }
}
