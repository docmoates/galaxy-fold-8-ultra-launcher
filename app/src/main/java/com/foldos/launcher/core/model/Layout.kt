package com.foldos.launcher.core.model

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
)
