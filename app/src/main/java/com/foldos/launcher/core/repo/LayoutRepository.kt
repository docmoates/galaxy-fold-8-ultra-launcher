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
     * @param style the starter layout the first page is seeded with (`home.StarterLayouts`), normally
     *   `LauncherSettings.styleFor(screen)` (what the setup wizard chose); null falls back to the
     *   implementation's default.
     * @param rows the visible row count of the page grid (Round 2.0), so the seeded items stay on
     *   screen; null = 6.
     */
    suspend fun ensureDefaultLayout(
        screen: ScreenKind,
        apps: List<AppInfo>,
        columns: Int? = null,
        style: HomeStyle? = null,
        rows: Int? = null,
    )

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

    /**
     * Every page of every screen (ordered by screen kind, then [HomePage.index]) with ALL of its
     * items: grid items, folder children (`parentId` set), stack mates and hosted app widgets.
     * One-shot read, for backup.
     */
    suspend fun allPagesWithItems(): List<Pair<HomePage, List<HomeItem>>>

    /**
     * Replaces the whole layout in one transaction: deletes every page and item, then inserts
     * [pages]. Ids in the input are local keys, not database ids: every page gets a fresh id (and
     * its items' `pageId` is set to it), every item with a non-zero `id` is remembered under that
     * key so `parentId` (folder child -> FOLDER item) and `stackId` (stack mate -> anchor item) are
     * rewritten to the new ids; items with id 0 get a fresh id and cannot be referenced. A
     * `parentId` / `stackId` that names no item in [pages] is cleared. `appWidgetId`s are stored
     * as given (callers should not pass any); no widget ids are released.
     */
    suspend fun replaceAll(pages: List<Pair<HomePage, List<HomeItem>>>)

    /**
     * Puts a new APP item for [app] on [pageId] at [col]/[row] (spans 1 x 1, nothing else moves).
     * When that cell is covered by another item, the nearest free cell is used instead (smallest
     * |dcol| + |drow|, ties to the lower row, then the lower column). [cols] / [rows] bound the search
     * to the grid the user sees (pass `GridGeometry.cols` / `rows`); null searches the rectangle
     * spanned by the requested cell and the page's occupied cells, extended by one row, so a cell
     * is always found. Used when an app is dragged from the app pop-up onto a page.
     *
     * When the visible grid is full the row below the lowest item is used if [growsRows] (dashboard /
     * hybrid panels grow their rows); a page with a fixed row count ([growsRows] false and [rows] given)
     * never draws such a row, so nothing is stored then.
     *
     * @return true when the app was placed, false when a fixed-row page had no room.
     */
    suspend fun placeApp(
        app: AppInfo,
        pageId: Long,
        col: Int,
        row: Int,
        cols: Int? = null,
        rows: Int? = null,
        growsRows: Boolean = true,
    ): Boolean

    /**
     * Stores the dock's apps for [screen]: [appKeys] are `AppInfo.key` values in dock order. An empty
     * list means "use the default dock apps" (the stored list is cleared). Keys of uninstalled apps
     * are kept as written; the reader skips what it cannot resolve.
     */
    suspend fun setDockApps(screen: ScreenKind, appKeys: List<String>)

    /** The dock apps stored by [setDockApps] for [screen] (`AppInfo.key` values); empty = use the defaults. */
    fun dockApps(screen: ScreenKind): Flow<List<String>>

    // ---- Round 2.0: free canvas + cards ----

    /**
     * Inserts a CARD item of [cardStyle] (`HomeItem.CARD_*`) at [col]/[row] on [pageId] spanning
     * [spanX] x [spanY] cells (both clamped to at least `HomeItem.CARD_MIN_SPAN`) and returns its id.
     * Nothing else moves; the caller picks a free spot.
     */
    suspend fun createCard(
        pageId: Long,
        col: Int,
        row: Int,
        spanX: Int,
        spanY: Int,
        cardStyle: String,
        title: String? = null,
    ): Long

    /**
     * Puts [itemId] inside the card [cardId] as child number [order] (its `row`; `col` becomes 0),
     * leaving the grid, any stack and any free placement; the item takes the card's page. A FOLDER
     * moved into a card takes its children along. No-op for a card or for an unknown id.
     */
    suspend fun moveIntoCard(itemId: Long, cardId: Long, order: Int)

    /**
     * Takes [itemId] out of its card and places it at [col]/[row] on [toPageId]. Unlike
     * [removeFromFolder] an emptied card stays. No-op for an item that is not inside a card.
     */
    suspend fun removeFromCard(itemId: Long, toPageId: Long, col: Int, row: Int)

    /** Moves the page [pageId] to position [newIndex] (clamped) on its screen and re-indexes the others. */
    suspend fun movePage(pageId: Long, newIndex: Int)
}
