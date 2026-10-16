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
import com.foldos.launcher.home.defaultDashboardItems
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [LayoutRepository] on top of [LayoutDatabase]. On first run each screen gets [DEFAULT_PAGE_COUNT]
 * pages, and the first page is filled with the first apps in a grid: the caller's column count
 * (Settings > Home > Cover / Inner columns) times 4 rows on the cover or 3 rows on the inner screen.
 * Without a column count the LauncherSettings defaults apply (4 columns / 16 apps on the cover,
 * 6 columns / 18 apps on the inner screen).
 *
 * Stacks use the first (anchor) item's row id as the stack id, so stack ids never collide with
 * each other and survive the anchor being flipped to the back.
 */
class LayoutRepositoryImpl(private val db: LayoutDatabase) : LayoutRepository {

    private val dao: LayoutDao = db.layoutDao()

    override fun pages(screen: ScreenKind): Flow<List<HomePage>> =
        dao.pagesByScreen(screen.name).map { rows -> rows.map(PageEntity::toModel) }

    override fun items(pageId: Long): Flow<List<HomeItem>> =
        dao.itemsByPage(pageId).map { rows -> rows.map(ItemEntity::toModel) }

    override fun children(folderId: Long): Flow<List<HomeItem>> =
        dao.childrenOf(folderId).map { rows -> rows.map(ItemEntity::toModel) }

    override suspend fun ensureDefaultLayout(screen: ScreenKind, apps: List<AppInfo>, columns: Int?, style: HomeStyle?) {
        db.withTransaction {
            val key = screen.name
            if (dao.countPages(key) == 0) {
                val pageStyle = style ?: defaultStyle(screen)
                for (index in 0 until DEFAULT_PAGE_COUNT) {
                    dao.upsertPage(PageEntity(screen = screen, index = index, style = pageStyle))
                }
            }
            val firstPage = dao.firstPage(key) ?: return@withTransaction
            // A dashboard first page starts with the built-in widgets (seeded below), every other
            // style with a grid of the first apps.
            if (firstPage.style != HomeStyle.DASHBOARD && dao.countItemsForScreen(key) == 0) {
                val cols = columns?.coerceAtLeast(1) ?: defaultColumns(screen)
                val count = cols * defaultRows(screen)
                val rows = apps.take(count).mapIndexed { position, app ->
                    ItemEntity(
                        pageId = firstPage.id,
                        kind = ItemKind.APP,
                        col = position % cols,
                        row = position / cols,
                        packageName = app.packageName,
                        activityName = app.activityName,
                        title = app.label,
                    )
                }
                if (rows.isNotEmpty()) dao.upsertItems(rows)
            }
            // Every empty dashboard page gets the default built-in widgets (PLAN.md 8.1), so they
            // are real, editable items rather than the preview the home screen shows for an empty page.
            dao.pagesByScreenOnce(key)
                .filter { it.style == HomeStyle.DASHBOARD }
                .forEach { page ->
                    if (dao.countItemsForPage(page.id) == 0) {
                        val widgets = defaultDashboardItems(screen, page.id).map { ItemEntity.fromModel(it) }
                        if (widgets.isNotEmpty()) dao.upsertItems(widgets)
                    }
                }
        }
    }

    override suspend fun upsertItem(item: HomeItem) {
        dao.upsertItem(ItemEntity.fromModel(item))
    }

    /** Deletes the item; a folder takes its children with it. */
    override suspend fun deleteItem(id: Long) {
        db.withTransaction {
            val item = dao.itemById(id)
            if (item?.kind == ItemKind.FOLDER) dao.deleteChildren(id)
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
            // Folder children follow their folder so a page delete still cascades to them.
            if (item?.kind == ItemKind.FOLDER) dao.moveChildren(id, pageId)
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

    /** Deletes a page and, through the foreign key, every item on it; then re-indexes the rest. */
    override suspend fun deletePage(pageId: Long) {
        db.withTransaction {
            val page = dao.pageById(pageId)
            dao.deletePage(pageId)
            if (page != null) {
                dao.pagesByScreenOnce(page.screen.name).forEachIndexed { index, remaining ->
                    if (remaining.index != index) dao.setPageIndex(remaining.id, index)
                }
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

    /** Sets or clears (null) one folder's style override ("grid", "list" or "big"). */
    suspend fun setFolderStyle(folderId: Long, style: String?) {
        dao.setFolderStyle(folderId, style)
    }

    /** Inserts or replaces a page; an id of 0 means "assign one". Returns the row id. */
    suspend fun upsertPage(page: HomePage): Long = dao.upsertPage(PageEntity.fromModel(page))

    companion object {
        const val DEFAULT_PAGE_COUNT: Int = 3

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
            ScreenKind.INNER, ScreenKind.FLEX -> 3
        }

        /** Seeded app count with the fallback column count: 16 on the cover, 18 on the inner screen. */
        fun defaultAppCount(screen: ScreenKind): Int = defaultColumns(screen) * defaultRows(screen)
    }
}
