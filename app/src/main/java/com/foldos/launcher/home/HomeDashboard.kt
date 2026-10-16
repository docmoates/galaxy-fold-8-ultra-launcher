package com.foldos.launcher.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.model.ScreenKind
import kotlin.math.max

/** Widget columns of the dashboard style: 2 on the cover, 4 on the inner screen (mock-up `--wc`). */
fun dashboardColumns(screen: ScreenKind): Int = if (screen == ScreenKind.COVER) 2 else 4

/** Rows needed to show [items] on a grid: the lowest cell any non-free item reaches. */
fun dashboardRows(items: List<HomeItem>): Int =
    items.filterNot { it.free && it.hasFreeBounds }
        .maxOfOrNull { it.row.coerceAtLeast(0) + it.spanY.coerceAtLeast(1) }
        ?: 1

/**
 * The built-in widgets a new dashboard page starts with (PLAN.md 8.1, mock-up `HOME.*.dashboard`),
 * as unsaved [HomeItem]s (id 0) on [pageId]. The Integrate agent writes them from
 * `ensureDefaultLayout` for dashboard pages; the home screen also shows them as a preview while a
 * dashboard page has no widget items.
 *
 * Inner / Flex (4 columns x 3 rows): glance (2x1), devices, weather / calendar (2x1), media (2x1)
 * / to-do, focus, news (2x1). Cover (2 columns x 6 rows): glance (2x1) / weather, devices /
 * calendar (2x1) / media (2x1) / to-do, focus / news (2x1).
 */
fun defaultDashboardItems(screen: ScreenKind, pageId: Long): List<HomeItem> {
    fun widget(id: String, col: Int, row: Int, spanX: Int = 1, spanY: Int = 1): HomeItem = HomeItem(
        id = 0L,
        pageId = pageId,
        kind = ItemKind.WIDGET,
        col = col,
        row = row,
        spanX = spanX,
        spanY = spanY,
        title = HomeBuiltInIds.title(id),
        builtinId = id,
    )
    return if (screen == ScreenKind.COVER) {
        listOf(
            widget(HomeBuiltInIds.GLANCE, 0, 0, spanX = 2),
            widget(HomeBuiltInIds.WEATHER, 0, 1),
            widget(HomeBuiltInIds.DEVICES, 1, 1),
            widget(HomeBuiltInIds.CALENDAR, 0, 2, spanX = 2),
            widget(HomeBuiltInIds.MEDIA, 0, 3, spanX = 2),
            widget(HomeBuiltInIds.TODO, 0, 4),
            widget(HomeBuiltInIds.FOCUS, 1, 4),
            widget(HomeBuiltInIds.NEWS, 0, 5, spanX = 2),
        )
    } else {
        listOf(
            widget(HomeBuiltInIds.GLANCE, 0, 0, spanX = 2),
            widget(HomeBuiltInIds.DEVICES, 2, 0),
            widget(HomeBuiltInIds.WEATHER, 3, 0),
            widget(HomeBuiltInIds.CALENDAR, 0, 1, spanX = 2),
            widget(HomeBuiltInIds.MEDIA, 2, 1, spanX = 2),
            widget(HomeBuiltInIds.TODO, 0, 2),
            widget(HomeBuiltInIds.FOCUS, 1, 2),
            widget(HomeBuiltInIds.NEWS, 2, 2, spanX = 2),
        )
    }
}

/**
 * The built-in widgets the widget panel of a new hybrid page starts with (mock-up
 * `HOME.*.hybrid`): cover = glance (2x1) over calendar (2x1); inner = clock, calendar, media, each
 * full width of the two-column panel. Same conventions as [defaultDashboardItems].
 */
fun defaultHybridWidgetItems(screen: ScreenKind, pageId: Long): List<HomeItem> {
    fun widget(id: String, row: Int): HomeItem = HomeItem(
        id = 0L,
        pageId = pageId,
        kind = ItemKind.WIDGET,
        col = 0,
        row = row,
        spanX = 2,
        spanY = 1,
        title = HomeBuiltInIds.title(id),
        builtinId = id,
    )
    return if (screen == ScreenKind.COVER) {
        listOf(widget(HomeBuiltInIds.GLANCE, 0), widget(HomeBuiltInIds.CALENDAR, 1))
    } else {
        listOf(widget(HomeBuiltInIds.CLOCK, 0), widget(HomeBuiltInIds.CALENDAR, 1), widget(HomeBuiltInIds.MEDIA, 2))
    }
}

/** True when every free-placement field of this item is set. */
val HomeItem.hasFreeBounds: Boolean
    get() = xDp != null && yDp != null && wDp != null && hDp != null

/**
 * The items a [HomeStyle.DASHBOARD] page draws and edits: its widgets only. APP / FOLDER / PAIR items of
 * a page that was a grid stay in the database (switching back to Grid shows them again) but are
 * never rendered, selected, moved over or counted as occupied cells while the style is Dashboard.
 */
fun dashboardWidgetItems(items: List<HomeItem>): List<HomeItem> = items.filter { it.kind == ItemKind.WIDGET }

/** Gives unsaved preview items distinct negative ids so they can be keyed and told apart. */
internal fun List<HomeItem>.asPreviewItems(): List<HomeItem> =
    mapIndexed { index, item -> if (item.id == 0L) item.copy(id = -(index + 1L)) else item }

/**
 * Widget dashboard (mock-up `HOME.*.dashboard`): the page's items on a 2-column (cover) or
 * 4-column (inner) widget grid whose rows stretch to fill the page; the first glance / clock card
 * is drawn with the highlight colors. A dashboard page without any item shows the default
 * dashboard as a preview until the Integrate agent seeds it.
 */
@Composable
internal fun HomeDashboardPage(
    ctx: HomeContext,
    page: HomePage?,
    items: List<HomeItem>,
    modifier: Modifier = Modifier,
    publishGeometry: Boolean = false,
) {
    val pageId = page?.id ?: 0L
    val cols = dashboardColumns(ctx.screen)
    val free = ctx.freeMode
    val placeholder = page == null
    val shown = remember(items, ctx.screen, pageId, cols, free, placeholder) {
        // A dashboard draws widgets only. The page's APP / FOLDER / PAIR items (kept in the database
        // when a grid page is switched to the dashboard style) stay hidden here and come back when
        // the page goes back to Grid.
        val widgets = dashboardWidgetItems(items)
        // The preview is only the placeholder shown before any page exists; a persisted page
        // (seeded with the default widgets when it becomes a dashboard) renders its real, editable
        // items, even when the user emptied it.
        val source = if (widgets.isNotEmpty() || !placeholder) widgets else defaultDashboardItems(ctx.screen, pageId).asPreviewItems()
        reflowGridItems(source, cols, skipFree = free)
    }
    val rows = remember(shown, ctx.screen) { max(dashboardRows(shown), if (ctx.isInner) 3 else 4) }
    HomeItemsGrid(
        ctx = ctx,
        pageId = pageId,
        items = shown,
        cols = cols,
        modifier = modifier.fillMaxSize(),
        fixedRows = rows,
        gutter = ctx.hingeGutter,
        cellPadding = if (ctx.isInner) 8.dp else 6.dp,
        heroHighlight = true,
        publishGeometry = publishGeometry,
        a11yMovable = true,
        acceptDrops = true,
    )
}
