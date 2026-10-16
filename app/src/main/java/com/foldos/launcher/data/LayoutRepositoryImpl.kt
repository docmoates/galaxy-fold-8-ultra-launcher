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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * [LayoutRepository] on top of [LayoutDatabase]. On first run each screen gets [DEFAULT_PAGE_COUNT]
 * pages, and the first page is filled with the first apps in a grid: the caller's column count
 * (Settings > Home > Cover / Inner columns) times 4 rows on the cover or 3 rows on the inner screen.
 * Without a column count the LauncherSettings defaults apply (4 columns / 16 apps on the cover,
 * 6 columns / 18 apps on the inner screen).
 */
class LayoutRepositoryImpl(private val db: LayoutDatabase) : LayoutRepository {

    private val dao: LayoutDao = db.layoutDao()

    override fun pages(screen: ScreenKind): Flow<List<HomePage>> =
        dao.pagesByScreen(screen.name).map { rows -> rows.map(PageEntity::toModel) }

    override fun items(pageId: Long): Flow<List<HomeItem>> =
        dao.itemsByPage(pageId).map { rows -> rows.map(ItemEntity::toModel) }

    override suspend fun ensureDefaultLayout(screen: ScreenKind, apps: List<AppInfo>, columns: Int?) {
        db.withTransaction {
            val key = screen.name
            if (dao.countPages(key) == 0) {
                val style = defaultStyle(screen)
                for (index in 0 until DEFAULT_PAGE_COUNT) {
                    dao.upsertPage(PageEntity(screen = screen, index = index, style = style))
                }
            }
            if (dao.countItemsForScreen(key) == 0) {
                val firstPage = dao.firstPage(key) ?: return@withTransaction
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
        }
    }

    override suspend fun upsertItem(item: HomeItem) {
        dao.upsertItem(ItemEntity.fromModel(item))
    }

    override suspend fun deleteItem(id: Long) {
        dao.deleteItem(id)
    }

    /** Inserts or replaces a page; an id of 0 means "assign one". Returns the row id. */
    suspend fun upsertPage(page: HomePage): Long = dao.upsertPage(PageEntity.fromModel(page))

    /** Deletes a page and, through the foreign key, every item on it. */
    suspend fun deletePage(id: Long) {
        dao.deletePage(id)
    }

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
