package com.foldos.launcher.core.repo

import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.ScreenKind
import kotlinx.coroutines.flow.Flow

/** Home pages and the items on them, per screen. Implemented by the Data agent (Room). */
interface LayoutRepository {
    /** Pages for one screen, ordered by [HomePage.index]. */
    fun pages(screen: ScreenKind): Flow<List<HomePage>>

    /** Items on one page. */
    fun items(pageId: Long): Flow<List<HomeItem>>

    /**
     * Creates the default pages and items for [screen] if it has none yet, using [apps] to
     * pick the initial favorites. No-op when the screen already has pages.
     *
     * @param columns the grid column count the seeded items should be laid out for, normally
     *   `LauncherSettings.colsFor(screen)`; null falls back to the implementation's default.
     */
    suspend fun ensureDefaultLayout(screen: ScreenKind, apps: List<AppInfo>, columns: Int? = null)

    /** Inserts the item, or replaces the item with the same id. An id of 0 means "assign one". */
    suspend fun upsertItem(item: HomeItem)

    suspend fun deleteItem(id: Long)
}
