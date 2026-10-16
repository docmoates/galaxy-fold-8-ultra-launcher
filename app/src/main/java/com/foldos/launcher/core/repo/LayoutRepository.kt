package com.foldos.launcher.core.repo

import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.ScreenKind
import kotlinx.coroutines.flow.Flow

/** Home pages and the items on them, per screen. Implemented by the Data agent (Room). */
interface LayoutRepository {
    /** Pages for one screen, ordered by [HomePage.index]. */
    fun pages(screen: ScreenKind): Flow<List<HomePage>>

    /** Items placed on one page (folder children excluded), ordered by row, column, then stack index. */
    fun items(pageId: Long): Flow<List<HomeItem>>

    /** Items inside the folder [folderId] (`HomeItem.parentId == folderId`), ordered by row then column. */
    fun children(folderId: Long): Flow<List<HomeItem>>

    /**
     * Creates the default pages and items for [screen] if it has none yet, using [apps] to
     * pick the initial favorites. No-op when the screen already has pages.
     *
     * @param columns the grid column count the seeded items should be laid out for, normally
     *   `LauncherSettings.colsFor(screen)`; null falls back to the implementation's default.
     * @param style the style the seeded pages get, normally `LauncherSettings.styleFor(screen)`
     *   (what the setup wizard chose); null falls back to the implementation's default. A
     *   dashboard first page is seeded with the default built-in widgets instead of apps.
     */
    suspend fun ensureDefaultLayout(screen: ScreenKind, apps: List<AppInfo>, columns: Int? = null, style: HomeStyle? = null)

    /** Inserts the item, or replaces the item with the same id. An id of 0 means "assign one". */
    suspend fun upsertItem(item: HomeItem)

    suspend fun deleteItem(id: Long)

    /**
     * Moves one item to [col]/[row] on [pageId], which may be a page on the same screen. Items in
     * the same stack move with it.
     */
    suspend fun moveItem(id: Long, pageId: Long, col: Int, row: Int)

    /** Resizes one item (and its stack mates) to [spanX] x [spanY] cells. */
    suspend fun resizeItem(id: Long, spanX: Int, spanY: Int)

    /** Appends an empty page with [style] after the screen's last page and returns it. */
    suspend fun addPage(screen: ScreenKind, style: HomeStyle): HomePage

    suspend fun setPageStyle(pageId: Long, style: HomeStyle)

    /** Deletes a page and every item on it, then closes the gap in the remaining page indices. */
    suspend fun deletePage(pageId: Long)

    /**
     * Puts [itemIds] into one stack occupying the first item's slot, in list order, and returns the
     * stack id. Items already in a stack are moved into the new one.
     */
    suspend fun createStack(itemIds: List<Long>): Long

    /**
     * Creates a FOLDER item titled [title] at [col]/[row] on [pageId] and moves [itemIds] into it
     * (they leave the grid and any stack; their order inside the folder is list order). Returns the
     * folder's id.
     */
    suspend fun createFolder(pageId: Long, col: Int, row: Int, title: String, itemIds: List<Long>): Long

    /**
     * Creates a PAIR item (split-screen [a] + [b]) at [col]/[row] on [pageId] and returns its id.
     * `packageName`/`activityName` hold [a], `packageName2`/`activityName2` hold [b].
     */
    suspend fun createPair(pageId: Long, col: Int, row: Int, a: AppInfo, b: AppInfo): Long

    /**
     * Takes [itemId] out of its folder and places it at [col]/[row] on [toPageId]. A folder left
     * empty is deleted.
     */
    suspend fun removeFromFolder(itemId: Long, toPageId: Long, col: Int, row: Int)
}
