package com.foldos.launcher.items

import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.repo.LayoutRepository

/**
 * Folder rules shared by the home grid, edit mode and this package's UI. Pure functions plus thin
 * wrappers over [LayoutRepository]; nothing here touches Compose.
 */
object Folders {
    /** How many children a folder icon previews. */
    const val PREVIEW_COUNT: Int = 4

    /** Title of a folder created without one. */
    const val DEFAULT_TITLE: String = "Folder"

    /** The style a folder renders with: its own override when valid, else the global setting. */
    fun styleFor(folder: HomeItem, settings: LauncherSettings): String =
        folder.folderStyle?.takeIf { it in LauncherSettings.FOLDER_STYLES } ?: settings.folderStyle

    /** Human-readable name of a folder style for pickers. */
    fun styleName(style: String): String = when (style) {
        LauncherSettings.FOLDER_LIST -> "List"
        LauncherSettings.FOLDER_BIG -> "Big"
        else -> "Grid"
    }

    /**
     * True for a persisted APP or PAIR item: the only kinds a folder holds. Widgets, folders and
     * synthetic preview items (id <= 0) never go into a folder.
     */
    fun canLiveInFolder(item: HomeItem): Boolean =
        item.id > 0 && !item.inFolder && (item.kind == ItemKind.APP || item.kind == ItemKind.PAIR)

    /**
     * Edit mode dropped [dragged] onto [target], both loose APP / PAIR items on the grid: creates a
     * folder in [target]'s cell holding both (target first) and returns its id, or null when either
     * item cannot live in a folder (then the caller treats the drop as a plain move / stack).
     */
    suspend fun createFromDrop(
        layouts: LayoutRepository,
        target: HomeItem,
        dragged: HomeItem,
        title: String = DEFAULT_TITLE,
    ): Long? {
        if (!canLiveInFolder(target) || !canLiveInFolder(dragged) || target.id == dragged.id) return null
        return layouts.createFolder(target.pageId, target.col, target.row, title, listOf(target.id, dragged.id))
    }

    /**
     * Edit mode dropped [item] onto the FOLDER [folder]: moves it inside as the last child. [childCount]
     * is the folder's current child count (its `children(folder.id)` list size), which becomes the
     * new child's order (`row`). No-op when [item] cannot live in a folder.
     */
    suspend fun addToFolder(layouts: LayoutRepository, folder: HomeItem, item: HomeItem, childCount: Int) {
        if (!folder.isFolder || folder.id <= 0 || !canLiveInFolder(item)) return
        layouts.upsertItem(
            item.copy(
                pageId = folder.pageId,
                parentId = folder.id,
                col = 0,
                row = childCount.coerceAtLeast(0),
                spanX = 1,
                spanY = 1,
                stackId = null,
                stackIndex = 0,
                free = false,
                xDp = null,
                yDp = null,
                wDp = null,
                hDp = null,
            ),
        )
    }

    /** Renames a folder (blank titles fall back to [DEFAULT_TITLE]). */
    suspend fun rename(layouts: LayoutRepository, folder: HomeItem, title: String) {
        if (!folder.isFolder || folder.id <= 0) return
        layouts.upsertItem(folder.copy(title = title.trim().ifEmpty { DEFAULT_TITLE }))
    }

    /** Sets (or with null clears) one folder's style override. */
    suspend fun setStyle(layouts: LayoutRepository, folder: HomeItem, style: String?) {
        if (!folder.isFolder || folder.id <= 0) return
        layouts.upsertItem(folder.copy(folderStyle = style?.takeIf { it in LauncherSettings.FOLDER_STYLES }))
    }
}
