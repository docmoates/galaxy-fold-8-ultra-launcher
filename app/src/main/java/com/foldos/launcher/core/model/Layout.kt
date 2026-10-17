package com.foldos.launcher.core.model

import android.content.ComponentName
import android.graphics.drawable.Drawable
import kotlinx.serialization.Serializable

/**
 * A home page on one screen. [index] is the page order; page 0 is the default page. Since Round 2.0
 * every page is the same free-form grid canvas: [style] only records the starter layout the page was
 * seeded from and has no effect on rendering.
 */
data class HomePage(
    val id: Long,
    val screen: ScreenKind,
    val index: Int,
    val style: HomeStyle,
)

/**
 * What a [HomeItem] holds. [CARD] (Round 2.0) is a holder drawn on the page that contains other
 * items (`parentId = card.id`): an apps card (mini grid or list of APP / FOLDER / PAIR items) or a
 * widgets card (one or more WIDGET items, swiped through).
 */
enum class ItemKind { APP, FOLDER, WIDGET, PAIR, CARD }

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
    /**
     * Id of the [ItemKind.FOLDER] item this item lives in; null for items placed on the grid. A
     * folder child keeps its folder's [pageId] (so deleting the page removes it) but is never drawn
     * on the page itself: `LayoutRepository.items(pageId)` omits it and `children(folderId)` lists it.
     */
    val parentId: Long? = null,
    /** Second app of an [ItemKind.PAIR] (the first one is [packageName] / [activityName]). */
    val packageName2: String? = null,
    val activityName2: String? = null,
    /** Per-folder style override ("grid", "list" or "big"); null = `LauncherSettings.folderStyle`. */
    val folderStyle: String? = null,
    /**
     * For an [ItemKind.CARD]: [CARD_APPS] (mini icon grid) or [CARD_APPS_LIST] (icon + name rows).
     * Null for every other kind. (The 1.3.x "widgets" style is gone; see [LEGACY_CARD_WIDGETS].)
     */
    val cardStyle: String? = null,
    /** For a card: true draws no surface behind the contents ("transparent" card). */
    val cardClear: Boolean = false,
) {
    /** True for a card holder (its contents come from `LayoutRepository.children(id)`). */
    val isCard: Boolean get() = kind == ItemKind.CARD

    /** True for a card that holds apps, folders and pairs (grid or list style). */
    val isAppsCard: Boolean get() = isCard

    /** True for an apps card drawn as icon + name rows. */
    val isListCard: Boolean get() = isCard && cardStyle == CARD_APPS_LIST

    companion object {
        /** `cardStyle` of an apps card drawn as a mini icon grid. */
        const val CARD_APPS: String = "apps"

        /** `cardStyle` of an apps card drawn as icon + name rows (the old "Minimal list"). */
        const val CARD_APPS_LIST: String = "apps-list"

        /** `cardStyle` of the removed 1.3.x widget card; only migration and old backups still read it (1.4.0 turns them into loose widgets). */
        const val LEGACY_CARD_WIDGETS: String = "widgets"

        /** Every card style, in picker order. */
        val CARD_STYLES: List<String> = listOf(CARD_APPS, CARD_APPS_LIST)

        /** Cards are never smaller than this (cells). */
        const val CARD_MIN_SPAN: Int = 2
    }
    /** True for a widget item backed by a hosted app widget. */
    val isAppWidget: Boolean get() = kind == ItemKind.WIDGET && appWidgetId != null

    /** True for a widget item backed by a FoldOS built-in. */
    val isBuiltInWidget: Boolean get() = kind == ItemKind.WIDGET && builtinId != null

    /** True for a folder container (its contents come from `LayoutRepository.children(id)`). */
    val isFolder: Boolean get() = kind == ItemKind.FOLDER

    /** True for an app pair with both apps set. */
    val isPair: Boolean get() = kind == ItemKind.PAIR && packageName2 != null && activityName2 != null

    /** True when this item is inside a folder or a card rather than on the grid. */
    val inFolder: Boolean get() = parentId != null
}

/**
 * Every gesture the home screen can bind an action to (Settings > Gestures). `_2F` = two fingers;
 * [HOME_DOUBLE] = home button pressed twice; [FOLD] / [UNFOLD] = hinge gestures.
 */
enum class GestureKind {
    SWIPE_UP,
    SWIPE_DOWN,
    SWIPE_LEFT_2F,
    SWIPE_RIGHT_2F,
    SWIPE_UP_2F,
    SWIPE_DOWN_2F,
    DOUBLE_TAP,
    PINCH_IN,
    PINCH_OUT,
    HOME_DOUBLE,
    FOLD,
    UNFOLD,
}

/**
 * What a gesture does. [type] is one of the `GestureAction.TYPE_*` constants; [target] is the
 * `AppInfo.key` ("package/activity") for [TYPE_APP] and null for every other type.
 */
@Serializable
data class GestureAction(
    val type: String = TYPE_NONE,
    val target: String? = null,
) {
    companion object {
        const val TYPE_NONE: String = "none"
        const val TYPE_DRAWER: String = "drawer"
        const val TYPE_SEARCH: String = "search"
        const val TYPE_HUB: String = "hub"
        const val TYPE_SETTINGS: String = "settings"
        const val TYPE_NOTIFICATIONS: String = "notifications"
        const val TYPE_QUICK_SETTINGS: String = "quick_settings"
        const val TYPE_APP: String = "app"
        const val TYPE_EDIT: String = "edit"
        const val TYPE_LOCK_HINT: String = "lock_hint"
        const val TYPE_RECENTS_HINT: String = "recents_hint"

        /** Every assignable type, in Settings > Gestures picker order. */
        val ALL_TYPES: List<String> = listOf(
            TYPE_NONE, TYPE_DRAWER, TYPE_SEARCH, TYPE_HUB, TYPE_SETTINGS, TYPE_NOTIFICATIONS,
            TYPE_QUICK_SETTINGS, TYPE_APP, TYPE_EDIT, TYPE_LOCK_HINT, TYPE_RECENTS_HINT,
        )

        val NONE: GestureAction = GestureAction(TYPE_NONE)

        /** Shorthand for an [TYPE_APP] action launching the app with [appKey] (`AppInfo.key`). */
        fun app(appKey: String): GestureAction = GestureAction(TYPE_APP, appKey)
    }
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
 * @property descriptionLoader lazily reads the provider's own description (call off the main thread); null when none exists.
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
    val descriptionLoader: (() -> String?)? = null,
)
