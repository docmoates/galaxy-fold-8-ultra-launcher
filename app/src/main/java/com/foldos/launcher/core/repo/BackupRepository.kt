package com.foldos.launcher.core.repo

import com.foldos.launcher.core.model.LauncherSettings
import kotlinx.serialization.Serializable

/**
 * Export / import of the launcher's configuration as one JSON document ([BackupFile]).
 *
 * Included: [LauncherSettings], home layouts (pages and items, including folders, stacks, app pairs
 * and built-in widgets by `builtinId`), per-app icon overrides. Excluded:
 * hosted app widgets (their `appWidgetId`s are bound to this install), search ranking / recents,
 * mail tokens and notification state. Implemented by the Backup agent.
 */
interface BackupRepository {
    /** The current configuration as a [BackupFile] encoded with `Json { ignoreUnknownKeys = true; encodeDefaults = true }`. */
    suspend fun exportJson(): String

    /** Validates [json] and, when it is a supported [BackupFile], replaces the current configuration with it. */
    suspend fun importJson(json: String): ImportResult
}

/** Outcome of [BackupRepository.importJson]; [itemsImported] counts home items written (0 on failure). */
data class ImportResult(
    val ok: Boolean,
    val message: String,
    val itemsImported: Int,
) {
    companion object {
        fun failure(message: String): ImportResult = ImportResult(ok = false, message = message, itemsImported = 0)
    }
}

/**
 * The on-disk backup document. [version] is bumped when the shape changes incompatibly; readers
 * reject a version higher than they know. [createdAt] is epoch milliseconds. [iconOverrides] maps
 * `AppInfo.key` ("package/activity") to the stored override value of `IconResolver.override`.
 * [dockApps] maps `ScreenKind.name` to that screen's dock apps (`AppInfo.key` list) and [appGroups] maps an app
 * pop-up group name to its `AppInfo.key` list, in creation order; both are optional so older files import.
 *
 * Encode with `encodeDefaults = true`, otherwise [version] (a default) is omitted from the output.
 */
@Serializable
data class BackupFile(
    val version: Int = 1,
    val createdAt: Long,
    val settings: LauncherSettings,
    val pages: List<BackupPage>,
    val iconOverrides: Map<String, String> = emptyMap(),
    val dockApps: Map<String, List<String>> = emptyMap(),
    val appGroups: Map<String, List<String>> = emptyMap(),
)

/** One home page. [screen] is `ScreenKind.name`, [style] is `HomeStyle.name`; [index] is the page order on its screen. */
@Serializable
data class BackupPage(
    val screen: String,
    val index: Int,
    val style: String,
    val items: List<BackupItem> = emptyList(),
)

/**
 * A `HomeItem` without database ids and without `appWidgetId` (hosted app widgets are not backed up).
 * [kind] is `ItemKind.name`. Relations are positions in the enclosing [BackupPage.items] list:
 * [parentIndex] = index of the FOLDER or CARD item holding this child (a folder may itself sit in a
 * card), [stackAnchorIndex] = index of the stack's anchor item (the item whose row id was the stack
 * id); both null when not applicable. A child keeps `col = 0` and its `row` as its order, like `HomeItem`.
 */
@Serializable
data class BackupItem(
    val kind: String,
    val col: Int,
    val row: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val packageName: String? = null,
    val activityName: String? = null,
    val title: String? = null,
    val builtinId: String? = null,
    val stackAnchorIndex: Int? = null,
    val stackIndex: Int = 0,
    val free: Boolean = false,
    val xDp: Float? = null,
    val yDp: Float? = null,
    val wDp: Float? = null,
    val hDp: Float? = null,
    val parentIndex: Int? = null,
    val packageName2: String? = null,
    val activityName2: String? = null,
    val folderStyle: String? = null,
    /** Round 2.0 (1.3.0): `HomeItem.cardStyle` of a CARD item; absent in older files. */
    val cardStyle: String? = null,
    /** Round 2.0: `HomeItem.cardClear` of a CARD item. */
    val cardClear: Boolean = false,
)
