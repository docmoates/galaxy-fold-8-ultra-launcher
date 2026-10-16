package com.foldos.launcher.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.model.ScreenKind

/** Room row for [HomePage]. Enums are stored as their names via [LayoutConverters]. */
@Entity(
    tableName = "pages",
    indices = [Index(value = ["screen", "page_index"])],
)
data class PageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val screen: ScreenKind,
    @ColumnInfo(name = "page_index")
    val index: Int,
    val style: HomeStyle,
) {
    fun toModel(): HomePage = HomePage(id = id, screen = screen, index = index, style = style)

    companion object {
        fun fromModel(page: HomePage): PageEntity =
            PageEntity(id = page.id, screen = page.screen, index = page.index, style = page.style)
    }
}

/** Room row for [HomeItem]. Deleting a page cascades to its items. */
@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = PageEntity::class,
            parentColumns = ["id"],
            childColumns = ["page_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["page_id"])],
)
data class ItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    @ColumnInfo(name = "page_id")
    val pageId: Long,
    val kind: ItemKind,
    @ColumnInfo(name = "grid_col")
    val col: Int,
    @ColumnInfo(name = "grid_row")
    val row: Int,
    @ColumnInfo(name = "span_x")
    val spanX: Int = 1,
    @ColumnInfo(name = "span_y")
    val spanY: Int = 1,
    @ColumnInfo(name = "package_name")
    val packageName: String? = null,
    @ColumnInfo(name = "activity_name")
    val activityName: String? = null,
    val title: String? = null,
) {
    fun toModel(): HomeItem = HomeItem(
        id = id,
        pageId = pageId,
        kind = kind,
        col = col,
        row = row,
        spanX = spanX,
        spanY = spanY,
        packageName = packageName,
        activityName = activityName,
        title = title,
    )

    companion object {
        fun fromModel(item: HomeItem): ItemEntity = ItemEntity(
            id = item.id,
            pageId = item.pageId,
            kind = item.kind,
            col = item.col,
            row = item.row,
            spanX = item.spanX,
            spanY = item.spanY,
            packageName = item.packageName,
            activityName = item.activityName,
            title = item.title,
        )
    }
}
