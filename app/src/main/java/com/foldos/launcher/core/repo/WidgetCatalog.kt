package com.foldos.launcher.core.repo

import com.foldos.launcher.core.model.WidgetEntry
import kotlinx.coroutines.flow.Flow

/**
 * The searchable widget gallery: every hosted AppWidget provider plus every FoldOS built-in.
 * Implemented by the Picker agent; the Data agent ships a placeholder listing installed
 * providers only.
 */
interface WidgetCatalog {
    /** All entries, re-emitted when providers are installed or removed. */
    val entries: Flow<List<WidgetEntry>>

    /**
     * Entries grouped for the picker. [by] is one of [GROUP_CATEGORY] ("category": one group per
     * [com.foldos.launcher.core.model.WidgetCategory], in enum order), [GROUP_APP] ("app": one group
     * per [WidgetEntry.source], A-Z) or [GROUP_RECENT] ("recent": a "Recently used" group first,
     * then the category groups). Matches `LauncherSettings.wpGroup`. Empty groups are omitted.
     */
    fun grouped(by: String): Flow<List<Pair<String, List<WidgetEntry>>>>

    /** Records that the widget with [id] was just added, for the "recent" grouping. */
    suspend fun markUsed(id: String)

    companion object {
        const val GROUP_CATEGORY: String = "category"
        const val GROUP_APP: String = "app"
        const val GROUP_RECENT: String = "recent"

        /** Group title used for the recently-used block in [GROUP_RECENT] mode. */
        const val RECENT_GROUP_TITLE: String = "Recently used"
    }
}
