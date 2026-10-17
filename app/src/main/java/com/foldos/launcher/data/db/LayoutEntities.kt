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

/**
 * Room row for [HomeItem]. Deleting a page cascades to its items.
 *
 * Schema version 2 added the widget, stack and free-placement columns (all nullable except
 * `stack_index` and `free`, which default to 0); see [LayoutDatabase.MIGRATION_1_2]. Version 3
 * added the folder (`parent_id`, `folder_style`) and app-pair (`package_name_2`,
 * `activity_name_2`) columns, all nullable; see [LayoutDatabase.MIGRATION_2_3]. Version 4 added the
 * card columns (`card_style` nullable, `card_clear` default 0); see [LayoutDatabase.MIGRATION_3_4].
 */
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
    indices = [Index(value = ["page_id"]), Index(value = ["parent_id"])],
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
    @ColumnInfo(name = "app_widget_id")
    val appWidgetId: Int? = null,
    @ColumnInfo(name = "builtin_id")
    val builtinId: String? = null,
    @ColumnInfo(name = "stack_id")
    val stackId: Long? = null,
    @ColumnInfo(name = "stack_index", defaultValue = "0")
    val stackIndex: Int = 0,
    @ColumnInfo(name = "free", defaultValue = "0")
    val free: Boolean = false,
    @ColumnInfo(name = "x_dp")
    val xDp: Float? = null,
    @ColumnInfo(name = "y_dp")
    val yDp: Float? = null,
    @ColumnInfo(name = "w_dp")
    val wDp: Float? = null,
    @ColumnInfo(name = "h_dp")
    val hDp: Float? = null,
    @ColumnInfo(name = "parent_id")
    val parentId: Long? = null,
    @ColumnInfo(name = "package_name_2")
    val packageName2: String? = null,
    @ColumnInfo(name = "activity_name_2")
    val activityName2: String? = null,
    @ColumnInfo(name = "folder_style")
    val folderStyle: String? = null,
    @ColumnInfo(name = "card_style")
    val cardStyle: String? = null,
    @ColumnInfo(name = "card_clear", defaultValue = "0")
    val cardClear: Boolean = false,
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
        appWidgetId = appWidgetId,
        builtinId = builtinId,
        stackId = stackId,
        stackIndex = stackIndex,
        free = free,
        xDp = xDp,
        yDp = yDp,
        wDp = wDp,
        hDp = hDp,
        parentId = parentId,
        packageName2 = packageName2,
        activityName2 = activityName2,
        folderStyle = folderStyle,
        cardStyle = cardStyle,
        cardClear = cardClear,
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
            appWidgetId = item.appWidgetId,
            builtinId = item.builtinId,
            stackId = item.stackId,
            stackIndex = item.stackIndex,
            free = item.free,
            xDp = item.xDp,
            yDp = item.yDp,
            wDp = item.wDp,
            hDp = item.hDp,
            parentId = item.parentId,
            packageName2 = item.packageName2,
            activityName2 = item.activityName2,
            folderStyle = item.folderStyle,
            cardStyle = item.cardStyle,
            cardClear = item.cardClear,
        )
    }
}
