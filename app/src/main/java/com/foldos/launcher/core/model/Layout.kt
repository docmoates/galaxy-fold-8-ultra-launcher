package com.foldos.launcher.core.model

import android.content.ComponentName
import android.graphics.drawable.Drawable

/** A home page on one screen. [index] is the page order; page 0 is the default page. */
data class HomePage(
    val id: Long,
    val screen: ScreenKind,
    val index: Int,
    val style: HomeStyle,
)

/** What a [HomeItem] holds. */
enum class ItemKind { APP, FOLDER, WIDGET, PAIR }

/**
 * One item placed on a home page. Grid coordinates are in cells; for [GridMode.FREE] layouts
 * the Home agent may interpret them as pixel-snapped units.
 *
 * Widgets ([ItemKind.WIDGET]) are either hosted app widgets, identified by [appWidgetId] (an id
 * allocated from the `AppWidgetHost`), or FoldOS built-ins, identified by [builtinId] (a
 * `BuiltInWidget.id`). Exactly one of the two is set for a widget item.
 *
 * Stacks: items sharing a [stackId] occupy one slot (same page, col, row and spans) and are
 * flipped through in [stackIndex] order. `stackId` is null for an item that stands alone.
 *
 * Free placement: when [free] is true the item is positioned by [xDp]/[yDp] and sized by
 * [wDp]/[hDp] in dp from the page's top-left corner, and the grid fields are only a fallback
 * for the home style that cannot place freely.
 */
data class HomeItem(
    val id: Long,
    val pageId: Long,
    val kind: ItemKind,
    val col: Int,
    val row: Int,
    val spanX: Int = 1,
    val spanY: Int = 1,
    val packageName: String? = null,
    val activityName: String? = null,
    val title: String? = null,
    /** Hosted AppWidget id from `AppWidgetHost.allocateAppWidgetId()`; null for non-app widgets. */
    val appWidgetId: Int? = null,
    /** `BuiltInWidget.id` for a FoldOS built-in widget; null otherwise. */
    val builtinId: String? = null,
    /** Shared id of the stack this item belongs to; null when it stands alone. */
    val stackId: Long? = null,
    /** Position inside its stack; 0 for the front item and for unstacked items. */
    val stackIndex: Int = 0,
    /** True when the item is placed freely (dp fields) instead of snapped to the grid. */
    val free: Boolean = false,
    val xDp: Float? = null,
    val yDp: Float? = null,
    val wDp: Float? = null,
    val hDp: Float? = null,
) {
    /** True for a widget item backed by a hosted app widget. */
    val isAppWidget: Boolean get() = kind == ItemKind.WIDGET && appWidgetId != null

    /** True for a widget item backed by a FoldOS built-in. */
    val isBuiltInWidget: Boolean get() = kind == ItemKind.WIDGET && builtinId != null
}

/**
 * The 17 widget-picker categories (mock-up `CATS` / `CATICON`). [displayName] is the picker
 * label; [iconName] is the Material Symbols name the mock-up uses for the category chip, which
 * UI maps to an `Icons.*` vector.
 */
enum class WidgetCategory(val displayName: String, val iconName: String) {
    CLOCK("Clock & Time", "schedule"),
    WEATHER("Weather", "partly_cloudy_day"),
    CALENDAR("Calendar & Tasks", "event"),
    COMMUNICATION("Communication", "chat"),
    MEDIA("Media & Audio", "music_note"),
    PHOTOS("Photos", "photo_library"),
    PRODUCTIVITY("Productivity & Notes", "edit_note"),
    HEALTH("Health & Fitness", "favorite"),
    DEVICE("Device & System", "smartphone"),
    SMART_HOME("Smart Home", "home"),
    TRAVEL("Travel & Maps", "map"),
    FINANCE("Finance", "payments"),
    NEWS("News & Reading", "newspaper"),
    SEARCH("Search & Shortcuts", "search"),
    SOCIAL("Social", "group"),
    UTILITIES("Utilities", "build"),
    FUN("Fun & Personal", "mood");

    companion object {
        /** Finds a category by its mock-up display name; null when unknown. */
        fun byDisplayName(name: String): WidgetCategory? = entries.firstOrNull { it.displayName == name }
    }
}

/**
 * One row of the widget gallery: a hosted app widget provider or a FoldOS built-in.
 *
 * @property id stable id: `ComponentName.flattenToString()` for app widgets, `BuiltInWidget.id`
 *   for built-ins.
 * @property source the app or "FoldOS built-in" label shown under the name.
 * @property provider the AppWidget provider component; null for built-ins.
 * @property previewLoader lazily loads the provider's preview image; null when none exists.
 */
data class WidgetEntry(
    val id: String,
    val name: String,
    val source: String,
    val category: WidgetCategory,
    val minSpanX: Int,
    val minSpanY: Int,
    val defaultSpanX: Int,
    val defaultSpanY: Int,
    val isBuiltIn: Boolean,
    val provider: ComponentName? = null,
    val previewLoader: (() -> Drawable?)? = null,
)
