package com.foldos.launcher.home

import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.repo.LayoutRepository
import kotlinx.coroutines.flow.first

/**
 * Starter layouts (Round 2.0): what a page is SEEDED with when the user picks "Classic grid",
 * "Widget dashboard", "Minimal list" or "Panels". The result is ordinary canvas content (loose apps
 * and widgets plus cards); nothing remembers the starter afterwards except `HomePage.style`.
 *
 * Each starter is described as a [Seed]: top-level items (id 0) and, for cards, the children that
 * go inside them (`parent` = index of the card in [Seed.items]). [seed] writes a seed through the
 * [LayoutRepository]; [apply] clears a page first or appends a new page.
 */
object StarterLayouts {

    /** Rows assumed when the caller does not know the page's visible row count. */
    const val DEFAULT_ROWS: Int = 6

    /** The four starters with their tile names, in picker order. */
    val all: List<Pair<HomeStyle, String>> = listOf(
        HomeStyle.GRID to "Classic grid",
        HomeStyle.DASHBOARD to "Widget dashboard",
        HomeStyle.LIST to "Minimal list",
        HomeStyle.HYBRID to "Panels",
    )

    fun name(starter: HomeStyle): String = all.firstOrNull { it.first == starter }?.second ?: starter.name

    /** One-line description for the starter tiles. */
    fun description(starter: HomeStyle): String = when (starter) {
        HomeStyle.GRID -> "Your most used apps as icons"
        HomeStyle.DASHBOARD -> "Glance, weather, calendar and more as loose widgets"
        HomeStyle.LIST -> "One app card in list style: big names, no icons"
        HomeStyle.HYBRID -> "Loose widgets over an app card"
    }

    /** A child of a seeded card: [parent] indexes [Seed.items]. */
    class Child(val parent: Int, val item: HomeItem)

    class Seed(val items: List<HomeItem>, val children: List<Child>)

    /**
     * The seed for [starter] on [screen], laid out for [cols] x [rows] cells of page [pageId].
     * Widgets are the FoldOS built-ins ([HomeBuiltInIds]); apps come from [preferredHomeApps].
     */
    fun seed(starter: HomeStyle, screen: ScreenKind, pageId: Long, apps: List<AppInfo>, cols: Int, rows: Int): Seed {
        val columns = cols.coerceAtLeast(2)
        val visibleRows = rows.coerceAtLeast(3)
        val inner = screen != ScreenKind.COVER
        fun app(a: AppInfo, col: Int, row: Int, parent: Boolean = false): HomeItem = HomeItem(
            id = 0L,
            pageId = pageId,
            kind = ItemKind.APP,
            col = if (parent) 0 else col,
            row = row,
            packageName = a.packageName,
            activityName = a.activityName,
            title = a.label,
        )
        fun widget(id: String, col: Int, row: Int, spanX: Int, spanY: Int): HomeItem = HomeItem(
            id = 0L,
            pageId = pageId,
            kind = ItemKind.WIDGET,
            col = col,
            row = row,
            spanX = spanX.coerceIn(1, columns),
            spanY = spanY.coerceAtLeast(1),
            title = HomeBuiltInIds.title(id),
            builtinId = id,
        )
        fun card(style: String, col: Int, row: Int, spanX: Int, spanY: Int, title: String? = null): HomeItem = HomeItem(
            id = 0L,
            pageId = pageId,
            kind = ItemKind.CARD,
            col = col,
            row = row,
            spanX = spanX.coerceIn(HomeItem.CARD_MIN_SPAN, columns),
            spanY = spanY.coerceAtLeast(HomeItem.CARD_MIN_SPAN),
            title = title,
            cardStyle = style,
        )

        return when (starter) {
            HomeStyle.GRID -> {
                val count = columns * minOf(visibleRows, 4)
                Seed(
                    items = preferredHomeApps(apps, count).mapIndexed { i, a -> app(a, i % columns, i / columns) },
                    children = emptyList(),
                )
            }
            HomeStyle.DASHBOARD -> {
                if (!inner || columns < 6) {
                    val half = columns / 2
                    Seed(
                        items = listOf(
                            widget(HomeBuiltInIds.GLANCE, 0, 0, columns, 1),
                            widget(HomeBuiltInIds.WEATHER, 0, 1, half, 1),
                            widget(HomeBuiltInIds.DEVICES, half, 1, columns - half, 1),
                            widget(HomeBuiltInIds.CALENDAR, 0, 2, columns, 2),
                            widget(HomeBuiltInIds.TODO, 0, 4, half, 1),
                            widget(HomeBuiltInIds.FOCUS, half, 4, columns - half, 1),
                            widget(HomeBuiltInIds.MEDIA, 0, 5, columns, 1),
                        ),
                        children = emptyList(),
                    )
                } else {
                    Seed(
                        items = listOf(
                            widget(HomeBuiltInIds.GLANCE, 0, 0, 3, 1),
                            widget(HomeBuiltInIds.WEATHER, 3, 0, 2, 1),
                            widget(HomeBuiltInIds.DEVICES, 5, 0, columns - 5, 1),
                            widget(HomeBuiltInIds.CALENDAR, 0, 1, 3, 2),
                            widget(HomeBuiltInIds.MEDIA, 3, 1, columns - 3, 1),
                            widget(HomeBuiltInIds.TODO, 3, 2, 2, 1),
                            widget(HomeBuiltInIds.FOCUS, 5, 2, columns - 5, 1),
                            widget(HomeBuiltInIds.NEWS, 0, 3, 3, 2),
                        ),
                        children = emptyList(),
                    )
                }
            }
            HomeStyle.LIST -> {
                val listCard = card(HomeItem.CARD_APPS_LIST, 0, 0, columns, visibleRows, title = "Apps")
                val picked = preferredHomeApps(apps, if (inner) 14 else 12)
                    .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label.trim() })
                Seed(
                    items = listOf(listCard),
                    children = picked.mapIndexed { i, a -> Child(0, app(a, 0, i, parent = true)) },
                )
            }
            HomeStyle.HYBRID -> {
                val topRows = 3
                val appCard = card(HomeItem.CARD_APPS, 0, topRows, columns, (visibleRows - topRows).coerceAtLeast(2))
                val widgets = if (inner && columns >= 6) {
                    val w = columns / 3
                    listOf(
                        widget(HomeBuiltInIds.CLOCK, 0, 0, w, topRows),
                        widget(HomeBuiltInIds.CALENDAR, w, 0, w, topRows),
                        widget(HomeBuiltInIds.MEDIA, 2 * w, 0, columns - 2 * w, topRows),
                    )
                } else {
                    listOf(
                        widget(HomeBuiltInIds.GLANCE, 0, 0, columns, 1),
                        widget(HomeBuiltInIds.CALENDAR, 0, 1, columns, 2),
                    )
                }
                val appCount = if (inner) 16 else 8
                Seed(
                    items = widgets + appCard,
                    children = preferredHomeApps(apps, appCount).mapIndexed { i, a -> Child(widgets.size, app(a, 0, i, parent = true)) },
                )
            }
            }
        }
    }

    /** Writes [seed] to [pageId] through [layouts]: cards first (they need ids), then their children. */
    suspend fun write(layouts: LayoutRepository, pageId: Long, seed: Seed) {
        val cardIds = HashMap<Int, Long>()
        seed.items.forEachIndexed { index, item ->
            if (item.kind == ItemKind.CARD) {
                cardIds[index] = layouts.createCard(
                    pageId = pageId,
                    col = item.col,
                    row = item.row,
                    spanX = item.spanX,
                    spanY = item.spanY,
                    cardStyle = item.cardStyle ?: HomeItem.CARD_APPS,
                    title = item.title,
                )
            } else {
                layouts.upsertItem(item.copy(pageId = pageId))
            }
        }
        for (child in seed.children) {
            val parent = cardIds[child.parent] ?: continue
            layouts.upsertItem(child.item.copy(pageId = pageId, parentId = parent))
        }
    }

    /**
     * Applies [starter] to [screen]: with [replacePageId] the items of that page are deleted first and
     * the seed goes there; without, a new page is appended and seeded. Returns the seeded page id.
     */
    suspend fun apply(
        layouts: LayoutRepository,
        screen: ScreenKind,
        starter: HomeStyle,
        apps: List<AppInfo>,
        cols: Int,
        rows: Int,
        replacePageId: Long?,
    ): Long {
        val pageId = if (replacePageId != null) {
            layouts.items(replacePageId).first().forEach { layouts.deleteItem(it.id) }
            replacePageId
        } else {
            layouts.addPage(screen, starter).id
        }
        write(layouts, pageId, seed(starter, screen, pageId, apps, cols, rows))
        return pageId
    }
}
