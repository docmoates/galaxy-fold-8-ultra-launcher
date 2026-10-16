package com.foldos.launcher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Queries for home pages and items. Screens are passed as [com.foldos.launcher.core.model.ScreenKind.name]. */
@Dao
interface LayoutDao {

    // Pages

    @Query("SELECT * FROM pages WHERE screen = :screen ORDER BY page_index ASC")
    fun pagesByScreen(screen: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE screen = :screen ORDER BY page_index ASC")
    suspend fun pagesByScreenOnce(screen: String): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id")
    suspend fun pageById(id: Long): PageEntity?

    @Query("SELECT * FROM pages WHERE screen = :screen ORDER BY page_index ASC LIMIT 1")
    suspend fun firstPage(screen: String): PageEntity?

    @Query("SELECT COUNT(*) FROM pages WHERE screen = :screen")
    suspend fun countPages(screen: String): Int

    /** Highest page index on the screen, or -1 when it has no pages. */
    @Query("SELECT COALESCE(MAX(page_index), -1) FROM pages WHERE screen = :screen")
    suspend fun maxPageIndex(screen: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: PageEntity): Long

    /** [style] is a [com.foldos.launcher.core.model.HomeStyle.name]. */
    @Query("UPDATE pages SET style = :style WHERE id = :pageId")
    suspend fun setPageStyle(pageId: Long, style: String)

    @Query("UPDATE pages SET page_index = :index WHERE id = :pageId")
    suspend fun setPageIndex(pageId: Long, index: Int)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePage(id: Long)

    // Items

    @Query("SELECT * FROM items WHERE page_id = :pageId ORDER BY grid_row ASC, grid_col ASC, stack_index ASC")
    fun itemsByPage(pageId: Long): Flow<List<ItemEntity>>

    @Query("SELECT * FROM items WHERE id = :id")
    suspend fun itemById(id: Long): ItemEntity?

    @Query("SELECT * FROM items WHERE id IN (:ids)")
    suspend fun itemsByIds(ids: List<Long>): List<ItemEntity>

    @Query("SELECT COUNT(*) FROM items WHERE page_id IN (SELECT id FROM pages WHERE screen = :screen)")
    suspend fun countItemsForScreen(screen: String): Int

    @Query("SELECT COUNT(*) FROM items WHERE page_id = :pageId")
    suspend fun countItemsForPage(pageId: Long): Int

    @Query("SELECT * FROM items WHERE page_id = :pageId ORDER BY grid_row ASC, grid_col ASC, stack_index ASC")
    suspend fun itemsByPageOnce(pageId: Long): List<ItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: ItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<ItemEntity>)

    @Query("UPDATE items SET page_id = :pageId, grid_col = :col, grid_row = :row WHERE id = :id")
    suspend fun moveItem(id: Long, pageId: Long, col: Int, row: Int)

    @Query("UPDATE items SET page_id = :pageId, grid_col = :col, grid_row = :row WHERE stack_id = :stackId")
    suspend fun moveStack(stackId: Long, pageId: Long, col: Int, row: Int)

    @Query("UPDATE items SET span_x = :spanX, span_y = :spanY WHERE id = :id")
    suspend fun resizeItem(id: Long, spanX: Int, spanY: Int)

    @Query("UPDATE items SET span_x = :spanX, span_y = :spanY WHERE stack_id = :stackId")
    suspend fun resizeStack(stackId: Long, spanX: Int, spanY: Int)

    /** Puts one item into a stack at the anchor's slot. */
    @Query(
        "UPDATE items SET stack_id = :stackId, stack_index = :stackIndex, page_id = :pageId, " +
            "grid_col = :col, grid_row = :row, span_x = :spanX, span_y = :spanY WHERE id = :id",
    )
    suspend fun placeInStack(
        id: Long,
        stackId: Long,
        stackIndex: Int,
        pageId: Long,
        col: Int,
        row: Int,
        spanX: Int,
        spanY: Int,
    )

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItem(id: Long)
}
