package com.foldos.launcher.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

/** Queries for home pages and items. Screens are passed as [com.foldos.launcher.core.model.ScreenKind.name]. */
@Dao
interface LayoutDao {

    @Query("SELECT * FROM pages WHERE screen = :screen ORDER BY page_index ASC")
    fun pagesByScreen(screen: String): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE screen = :screen ORDER BY page_index ASC LIMIT 1")
    suspend fun firstPage(screen: String): PageEntity?

    @Query("SELECT COUNT(*) FROM pages WHERE screen = :screen")
    suspend fun countPages(screen: String): Int

    @Query("SELECT * FROM items WHERE page_id = :pageId ORDER BY grid_row ASC, grid_col ASC")
    fun itemsByPage(pageId: Long): Flow<List<ItemEntity>>

    @Query("SELECT COUNT(*) FROM items WHERE page_id IN (SELECT id FROM pages WHERE screen = :screen)")
    suspend fun countItemsForScreen(screen: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: PageEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItem(item: ItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertItems(items: List<ItemEntity>)

    @Query("DELETE FROM items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("DELETE FROM pages WHERE id = :id")
    suspend fun deletePage(id: Long)
}
