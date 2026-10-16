package com.foldos.launcher.backup

import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.repo.BackupFile
import com.foldos.launcher.core.repo.BackupItem
import com.foldos.launcher.core.repo.BackupPage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/**
 * Pure conversion between the database model ([HomePage] / [HomeItem]) and the backup document
 * ([BackupPage] / [BackupItem]). No Android classes, no I/O; the app check on import is passed in.
 */
internal object BackupCodec {

    /** Highest [BackupFile.version] this build reads and writes. */
    const val SUPPORTED_VERSION: Int = 1

    /** `encodeDefaults = true` keeps `version` in the output. */
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }

    /** What [buildLayout] produced: the pages to hand to `LayoutRepository.replaceAll` and the counts. */
    class Built(
        val pages: List<Pair<HomePage, List<HomeItem>>>,
        val imported: Int,
        val skipped: Int,
    )

    /** The document's `version`, or null when the text is not a JSON object. A missing field reads as 1. */
    fun peekVersion(text: String): Int? {
        val root = json.parseToJsonElement(text)
        if (root !is JsonObject) return null
        val version = root["version"] ?: return SUPPORTED_VERSION
        return (version as? JsonPrimitive)?.intOrNull
    }

    fun decode(text: String): BackupFile = json.decodeFromString(BackupFile.serializer(), text)

    fun encode(file: BackupFile): String = json.encodeToString(BackupFile.serializer(), file)

    // ------------------------------------------------------------------------------------------
    // Export
    // ------------------------------------------------------------------------------------------

    /**
     * Pages with their items as [BackupPage]s. Hosted app widgets (their ids belong to this install)
     * and orphaned folder children are left out; relations are re-expressed as positions in the
     * filtered list of each page.
     */
    fun exportPages(pages: List<Pair<HomePage, List<HomeItem>>>): List<BackupPage> =
        pages.map { (page, all) ->
            val widgetsFiltered = all.filter { item ->
                !item.isAppWidget && (item.kind != ItemKind.WIDGET || item.isBuiltInWidget)
            }
            val folderIds = widgetsFiltered.filter { it.isFolder && it.parentId == null }.map { it.id }.toSet()
            val items = widgetsFiltered.filter { item ->
                val parent = item.parentId
                parent == null || (parent in folderIds && !item.isFolder)
            }
            val positionById = HashMap<Long, Int>()
            items.forEachIndexed { position, item -> positionById[item.id] = position }
            val stacks = resolveStacks(
                keys = items.map { if (it.parentId == null) it.stackId else null },
                order = items.map { it.stackIndex },
                preferredAnchor = { stackId -> positionById[stackId] },
            )
            BackupPage(
                screen = page.screen.name,
                index = page.index,
                style = page.style.name,
                items = items.mapIndexed { position, item ->
                    val (anchor, rank) = stacks[position]
                    BackupItem(
                        kind = item.kind.name,
                        col = item.col,
                        row = item.row,
                        spanX = item.spanX,
                        spanY = item.spanY,
                        packageName = item.packageName,
                        activityName = item.activityName,
                        title = item.title,
                        builtinId = item.builtinId,
                        stackAnchorIndex = anchor,
                        stackIndex = rank,
                        free = item.free,
                        xDp = item.xDp,
                        yDp = item.yDp,
                        wDp = item.wDp,
                        hDp = item.hDp,
                        parentIndex = item.parentId?.let { positionById[it] },
                        packageName2 = item.packageName2,
                        activityName2 = item.activityName2,
                        folderStyle = item.folderStyle,
                    )
                },
            )
        }

    // ------------------------------------------------------------------------------------------
    // Import
    // ------------------------------------------------------------------------------------------

    /**
     * Turns [backup] into pages ready for `LayoutRepository.replaceAll`. Every kept item carries a
     * unique non-zero local id (a running counter) so `parentId` / `stackId` can refer to it.
     *
     * [resolveApp] maps a package and activity to the activity name to use on this device (the
     * same name, or the package's launcher activity when it was renamed) or null when the app is
     * not installed. Skipped: apps that are not installed, pairs missing either app, widgets
     * without a built-in id, folders left without children, children of a bad parent, and pages
     * with an unknown screen. Unknown styles fall back to GRID; page indexes are made contiguous.
     */
    fun buildLayout(backup: BackupFile, resolveApp: (String?, String?) -> String?): Built {
        var nextId = 1L
        var imported = 0
        var skipped = 0
        val byScreen = LinkedHashMap<ScreenKind, MutableList<BackupPage>>()
        for (page in backup.pages) {
            val screen = ScreenKind.entries.firstOrNull { it.name == page.screen }
            if (screen == null) {
                skipped += page.items.size
                continue
            }
            byScreen.getOrPut(screen) { mutableListOf() }.add(page)
        }

        val result = ArrayList<Pair<HomePage, List<HomeItem>>>()
        for (screen in ScreenKind.entries) {
            val pages = byScreen[screen]?.sortedBy { it.index } ?: continue
            pages.forEachIndexed { pageIndex, page ->
                val style = HomeStyle.entries.firstOrNull { it.name == page.style } ?: HomeStyle.GRID
                val built = buildPageItems(page.items, resolveApp, nextId)
                nextId = built.nextId
                imported += built.items.size
                skipped += page.items.size - built.items.size
                result += HomePage(id = 0, screen = screen, index = pageIndex, style = style) to built.items
            }
        }
        return Built(pages = result, imported = imported, skipped = skipped)
    }

    private class PageItems(val items: List<HomeItem>, val nextId: Long)

    private fun buildPageItems(
        raw: List<BackupItem>,
        resolveApp: (String?, String?) -> String?,
        firstId: Long,
    ): PageItems {
        val templates = arrayOfNulls<HomeItem>(raw.size)

        // Pass 1: per-item validity and the app check.
        for (i in raw.indices) {
            val source = raw[i]
            val kind = ItemKind.entries.firstOrNull { it.name == source.kind } ?: continue
            val base = HomeItem(
                id = 0,
                pageId = 0,
                kind = kind,
                col = source.col.coerceAtLeast(0),
                row = source.row.coerceAtLeast(0),
                spanX = source.spanX.coerceAtLeast(1),
                spanY = source.spanY.coerceAtLeast(1),
                title = source.title,
                free = source.free,
                xDp = source.xDp,
                yDp = source.yDp,
                wDp = source.wDp,
                hDp = source.hDp,
                folderStyle = source.folderStyle,
            )
            templates[i] = when (kind) {
                ItemKind.APP -> {
                    val activity = resolveApp(source.packageName, source.activityName)
                    if (activity == null) null else base.copy(packageName = source.packageName, activityName = activity)
                }
                ItemKind.PAIR -> {
                    val first = resolveApp(source.packageName, source.activityName)
                    val second = resolveApp(source.packageName2, source.activityName2)
                    if (first == null || second == null) {
                        null
                    } else {
                        base.copy(
                            packageName = source.packageName,
                            activityName = first,
                            packageName2 = source.packageName2,
                            activityName2 = second,
                        )
                    }
                }
                ItemKind.WIDGET ->
                    if (source.builtinId.isNullOrBlank()) null else base.copy(builtinId = source.builtinId)
                ItemKind.FOLDER -> base
            }
        }

        // Pass 2: folder children need a real top-level folder; a folder needs a surviving child.
        val parentOf = IntArray(raw.size) { -1 }
        val hasChild = BooleanArray(raw.size)
        for (i in raw.indices) {
            val template = templates[i] ?: continue
            val parent = raw[i].parentIndex ?: continue
            val parentOk = parent in raw.indices &&
                parent != i &&
                templates[parent]?.kind == ItemKind.FOLDER &&
                raw[parent].parentIndex == null &&
                (template.kind == ItemKind.APP || template.kind == ItemKind.PAIR)
            if (parentOk) {
                parentOf[i] = parent
                hasChild[parent] = true
            } else {
                templates[i] = null
            }
        }
        for (i in raw.indices) {
            val template = templates[i] ?: continue
            if (template.kind == ItemKind.FOLDER && (raw[i].parentIndex != null || !hasChild[i])) templates[i] = null
        }

        // Pass 3: ids, then folder and stack relations.
        val kept = raw.indices.filter { templates[it] != null }
        val ids = LongArray(kept.size) { firstId + it }
        val positionOfRaw = HashMap<Int, Int>()
        kept.forEachIndexed { position, rawIndex -> positionOfRaw[rawIndex] = position }
        val stacks = resolveStacks(
            keys = kept.map { if (parentOf[it] < 0) raw[it].stackAnchorIndex else null },
            order = kept.map { raw[it].stackIndex },
            preferredAnchor = { rawAnchor -> positionOfRaw[rawAnchor] },
        )
        val items = kept.mapIndexed { position, rawIndex ->
            val template = templates[rawIndex]!!
            val parentRaw = parentOf[rawIndex]
            val (anchorPosition, rank) = stacks[position]
            template.copy(
                id = ids[position],
                col = if (parentRaw >= 0) 0 else template.col,
                parentId = if (parentRaw >= 0) positionOfRaw[parentRaw]?.let { ids[it] } else null,
                stackId = anchorPosition?.let { ids[it] },
                stackIndex = rank,
            )
        }
        return PageItems(items = items, nextId = firstId + kept.size)
    }

    /**
     * Normalises stacks. [keys] holds each position's stack key (null = not stacked), [order] its
     * stack order. A key with fewer than two members stops being a stack. The anchor is the member at
     * [preferredAnchor] (the original anchor) when it is part of the group, else the front member.
     * Returns, per position, the anchor's position (null = unstacked) and a contiguous stack index.
     */
    private fun <K : Any> resolveStacks(
        keys: List<K?>,
        order: List<Int>,
        preferredAnchor: (K) -> Int?,
    ): List<Pair<Int?, Int>> {
        val result = MutableList<Pair<Int?, Int>>(keys.size) { null to 0 }
        val groups = LinkedHashMap<K, MutableList<Int>>()
        keys.forEachIndexed { position, key ->
            if (key != null) groups.getOrPut(key) { mutableListOf() }.add(position)
        }
        for ((key, members) in groups) {
            if (members.size < 2) continue
            val sorted = members.sortedWith(compareBy({ order[it] }, { it }))
            val anchor = preferredAnchor(key)?.takeIf { it in members } ?: sorted.first()
            sorted.forEachIndexed { rank, position -> result[position] = anchor to rank }
        }
        return result
    }
}
