package com.foldos.launcher.picker

import androidx.compose.runtime.Immutable
import com.foldos.launcher.core.model.WidgetCategory
import com.foldos.launcher.core.model.WidgetEntry

/** Group key of the FoldOS built-in widgets (they have no package of their own). */
internal const val BUILT_IN_GROUP_KEY: String = "foldos.builtin"

/** Row title of the built-in group in the Apps list. */
internal const val BUILT_IN_GROUP_LABEL: String = "FoldOS widgets"

/** Most cards in the Featured grid. */
internal const val FEATURED_MAX: Int = 6

/** The alphabet headers only appear above this many apps. */
internal const val ALPHABET_HEADER_FROM: Int = 12

/** Most rows in the "Recently used" section. */
internal const val RECENT_ROWS_MAX: Int = 4

/** Categories that get their own filter chip (when any widget has them); the rest sit under "More". */
internal val PrimaryChipCategories: List<WidgetCategory> = listOf(
    WidgetCategory.CLOCK,
    WidgetCategory.WEATHER,
    WidgetCategory.CALENDAR,
    WidgetCategory.MEDIA,
    WidgetCategory.PHOTOS,
)

/** Key of the group an [entry] belongs to: [BUILT_IN_GROUP_KEY], else the provider's package name. */
internal fun groupKeyOf(entry: WidgetEntry): String =
    if (entry.isBuiltIn) BUILT_IN_GROUP_KEY else entry.provider?.packageName ?: entry.source

/** All widgets of one app (or of FoldOS itself): one row in the Apps list, one pager in the detail. */
@Immutable
internal class WidgetAppGroup(
    val key: String,
    val label: String,
    val isBuiltIn: Boolean,
    val entries: List<WidgetEntry>,
) {
    val countLabel: String get() = if (entries.size == 1) "1 widget" else "${entries.size} widgets"

    /** Uppercase first letter for the alphabet headers; '#' for anything that is not a letter. */
    val letter: Char
        get() {
            val first = label.trim().firstOrNull() ?: return '#'
            return if (first.isLetter()) first.uppercaseChar() else '#'
        }
}

/** Everything the picker shows, derived once per catalog emission. */
@Immutable
internal class PickerModel(
    val entries: List<WidgetEntry>,
    val recents: List<WidgetEntry>,
    /** True until the catalog has delivered its first list; the UI shows a spinner, not empty states. */
    val loading: Boolean = false,
) {
    /** Built-in group first, then the apps A-Z. */
    val groups: List<WidgetAppGroup>

    val groupByKey: Map<String, WidgetAppGroup>

    /** Up to [FEATURED_MAX] FoldOS built-ins, one per category first. */
    val featured: List<WidgetEntry>

    /** Categories that have at least one widget and no chip of their own. */
    val moreCategories: List<WidgetCategory>

    /** Primary chips that have at least one widget. */
    val chipCategories: List<WidgetCategory>

    init {
        val byKey = LinkedHashMap<String, MutableList<WidgetEntry>>()
        for (entry in entries) {
            byKey.getOrPut(groupKeyOf(entry)) { mutableListOf() }.add(entry)
        }
        val built = ArrayList<WidgetAppGroup>()
        val apps = ArrayList<WidgetAppGroup>()
        for ((key, list) in byKey) {
            if (key == BUILT_IN_GROUP_KEY) {
                built.add(WidgetAppGroup(key, BUILT_IN_GROUP_LABEL, true, list))
            } else {
                apps.add(WidgetAppGroup(key, list.first().source, false, list))
            }
        }
        apps.sortWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
        groups = built + apps
        groupByKey = groups.associateBy { it.key }

        val builtIns = entries.filter { it.isBuiltIn }
        val onePerCategory = builtIns.distinctBy { it.category }
        val firstPick = onePerCategory.take(FEATURED_MAX)
        featured = if (firstPick.size >= FEATURED_MAX) {
            firstPick
        } else {
            val ids = firstPick.map { it.id }.toSet()
            firstPick + builtIns.filter { it.id !in ids }.take(FEATURED_MAX - firstPick.size)
        }

        val present = entries.map { it.category }.toSet()
        chipCategories = PrimaryChipCategories.filter { it in present }
        moreCategories = WidgetCategory.entries.filter { it in present && it !in PrimaryChipCategories }
    }

    /** Groups to list under "Apps": only those with a widget in [category] (null = all). */
    fun groupsFor(category: WidgetCategory?): List<WidgetAppGroup> =
        if (category == null) groups else groups.filter { group -> group.entries.any { it.category == category } }

    /** Flat search over name, app and category; every word of [query] must match one of them. */
    fun search(query: String): List<WidgetEntry> {
        val words = query.trim().split(' ', '\t').filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return entries.filter { entry ->
            words.all { word ->
                entry.name.contains(word, ignoreCase = true) ||
                    entry.source.contains(word, ignoreCase = true) ||
                    entry.category.displayName.contains(word, ignoreCase = true)
            }
        }
    }
}

/** Where the picker currently is: which app's widgets are open and which one the pager starts on. */
internal data class DetailTarget(val groupKey: String, val startEntryId: String?)
