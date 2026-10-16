package com.foldos.launcher.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import android.view.accessibility.AccessibilityManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.HomeItemGridInfo
import com.foldos.launcher.a11y.homeItemAccessActions
import com.foldos.launcher.a11y.rememberA11yAnnouncer
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.ui.DragPayload
import com.foldos.launcher.core.ui.dragTarget
import kotlin.math.max

/**
 * Renders one home page by its [HomeStyle]: the per-screen setting (Settings > Home > Cover /
 * Inner screen style), or [HomePage.style] when `HomeScreen(perPageStyle = true)`.
 *
 * @param publishGeometry true for the settled current page, so its item grid publishes the
 *   [GridGeometry] the edit overlay reads through [rememberGridGeometry].
 */
@Composable
internal fun HomePageBody(
    ctx: HomeContext,
    page: HomePage?,
    items: List<HomeItem>,
    modifier: Modifier = Modifier,
    publishGeometry: Boolean = false,
) {
    val style = ctx.styleFor(page)
    // A settled LIST page has no cell grid to publish: drop the geometry of the page before it so the
    // edit overlay falls back to the estimate for this style instead of snapping to a stale grid.
    if (publishGeometry && style == HomeStyle.LIST) {
        LaunchedEffect(ctx.screen) { HomeGeometryStore.measured.remove(ctx.screen) }
    }
    // List and hybrid pages have no cell grid of their own: the whole page body is the drop target
    // and the app is appended below the last item (col / row -1).
    val dragController = ctx.dragController
    val dropHandler = ctx.onDropPayload
    val pageId = page?.id ?: 0L
    val bodyDrop: Modifier = if (
        dragController != null && dropHandler != null && pageId > 0L &&
        (style == HomeStyle.LIST || style == HomeStyle.HYBRID)
    ) {
        Modifier.dragTarget(
            controller = dragController,
            id = "page:$pageId",
            accepts = { true },
            onDrop = { payload, _ -> dropHandler(payload, pageId, -1, -1) },
        )
    } else {
        Modifier
    }
    when (style) {
        HomeStyle.GRID -> HomeGridPage(ctx, page, items, modifier, publishGeometry)
        HomeStyle.DASHBOARD -> HomeDashboardPage(ctx, page, items, modifier, publishGeometry)
        HomeStyle.LIST -> HomeListPage(ctx, page, items, modifier.then(bodyDrop))
        HomeStyle.HYBRID -> HomeHybridPage(ctx, page, items, modifier.then(bodyDrop), publishGeometry)
    }
}

/* ---------------- Slots: one grid cell, holding an item or a stack ---------------- */

/** The items that share one grid slot: a single item, or a stack ordered by `stackIndex`. */
internal class HomeSlot(val items: List<HomeItem>) {
    val anchor: HomeItem get() = items.first()
    val isStack: Boolean get() = items.size > 1

    /** Grid cell for this slot; a free cell when [freeMode] and the anchor carries dp bounds. */
    fun toCell(freeMode: Boolean): HomeCell {
        val a = anchor
        return if (freeMode && a.free && a.hasFreeBounds) {
            HomeCell(a.col, a.row, a.spanX, a.spanY, a.xDp, a.yDp, a.wDp, a.hDp)
        } else {
            HomeCell(a.col, a.row, a.spanX.coerceAtLeast(1), a.spanY.coerceAtLeast(1))
        }
    }
}

/** Groups [items] into slots: standalone items first in order, then one slot per `stackId`. */
internal fun groupHomeSlots(items: List<HomeItem>): List<HomeSlot> {
    val result = ArrayList<HomeSlot>(items.size)
    val stacks = LinkedHashMap<Long, MutableList<HomeItem>>()
    for (item in items) {
        val stackId = item.stackId
        if (stackId == null) {
            result.add(HomeSlot(listOf(item)))
        } else {
            stacks.getOrPut(stackId) { ArrayList() }.add(item)
        }
    }
    for (members in stacks.values) {
        result.add(HomeSlot(members.sortedBy { it.stackIndex }))
    }
    return result
}

/**
 * Lays out [items] as slots on a [cols]-column grid: widgets span their cells, stacks flip in one
 * slot, and in free mode items with dp bounds leave the grid. Long presses on items go to
 * `HomeCallbacks.onItemLongPress`, those on empty cells to `onEmptyLongPress(pageId, col, row)`.
 *
 * @param heroHighlight draw the first glance / clock built-in with the highlight colors.
 */
@Composable
internal fun HomeItemsGrid(
    ctx: HomeContext,
    pageId: Long,
    items: List<HomeItem>,
    cols: Int,
    modifier: Modifier = Modifier,
    fixedRows: Int? = null,
    minCellHeight: Dp = 96.dp,
    gutter: Dp = 0.dp,
    cellPadding: Dp = 6.dp,
    heroHighlight: Boolean = false,
    publishGeometry: Boolean = false,
    a11yMovable: Boolean = false,
    acceptDrops: Boolean = false,
) {
    val slots = remember(items) { groupHomeSlots(items) }
    // Drag and drop: the page's cell grid is a drop target 'page:<pageId>' (apps from the app
    // pop-up, items dragged from another cell); the drop cell comes from the grid's own metrics.
    val dragController = ctx.dragController
    val dropHandler = ctx.onDropPayload
    val dropTarget: HomeGridDrop? = if (acceptDrops && dragController != null && dropHandler != null && pageId > 0L) {
        HomeGridDrop(dragController, "page:$pageId") { payload, col, row -> dropHandler(payload, pageId, col, row) }
    } else {
        null
    }
    val free = ctx.freeMode
    val cells = remember(slots, free) { slots.map { it.toCell(free) } }
    val keys = remember(slots) { slots.map { slot -> slot.anchor.stackId ?: slot.anchor.id } }
    val heroId = remember(slots) {
        slots.firstOrNull { HomeBuiltInIds.isHero(it.anchor.builtinId) }?.anchor?.id
    }
    // Accessibility: Move / Resize actions need the grid size; only grids whose item coordinates
    // are the stored ones (classic grid, dashboard) offer them, see homeItemAccessActions.
    val a11yRows = fixedRows ?: if (publishGeometry) HomeGeometryStore.measured[ctx.screen]?.rows else null
    val a11yGrid = remember(items, cols, a11yRows, a11yMovable) {
        if (a11yMovable) HomeItemGridInfo(items, cols, a11yRows) else null
    }
    HomeCellGrid(
        cols = cols,
        cells = cells,
        modifier = modifier,
        fixedRows = fixedRows,
        minCellHeight = minCellHeight,
        gutter = gutter,
        keys = keys,
        geometryScreen = if (publishGeometry) ctx.screen else null,
        onEmptyLongPress = { col, row -> ctx.emptyLongPress(pageId, col, row) },
        dropTarget = dropTarget,
    ) { index ->
        val slot = slots[index]
        if (slot.isStack && ctx.stacksOn) {
            HomeStackSlot(slot.items, Modifier.fillMaxSize(), smartRules = ctx.smartOn) { item ->
                HomeItemTile(ctx, item, cellPadding, highlight = heroHighlight && item.id == heroId, access = a11yGrid)
            }
        } else {
            HomeItemTile(ctx, slot.anchor, cellPadding, highlight = heroHighlight && slot.anchor.id == heroId, access = a11yGrid)
        }
    }
}

/* ---------------- Classic grid ---------------- */

@Composable
internal fun HomeGridPage(
    ctx: HomeContext,
    page: HomePage?,
    items: List<HomeItem>,
    modifier: Modifier = Modifier,
    publishGeometry: Boolean = false,
) {
    val cols = ctx.cols
    val pageId = page?.id ?: 0L
    val free = ctx.freeMode
    // Only the placeholder shown before any page exists is auto-filled; once a persisted page
    // exists it renders its real items, even when the user has emptied it in edit mode (the
    // synthetic items could not be selected, moved or deleted there).
    val placeholder = page == null
    val gridItems = remember(items, cols, pageId, placeholder, free, ctx.sortedApps) {
        val source = if (items.isEmpty() && placeholder) ctx.autoGridItems(pageId, cols, maxRows = 8) else items
        reflowGridItems(source, cols, skipFree = free)
    }
    // Easy mode shows bigger labels, so each row needs a little more room.
    val labelHeight = if (ctx.labels) (if (ctx.settings.largeIcons) 32.dp else 24.dp) else 0.dp
    HomeItemsGrid(
        ctx = ctx,
        pageId = pageId,
        items = gridItems,
        cols = cols,
        modifier = modifier.fillMaxSize(),
        minCellHeight = ctx.iconSize + labelHeight + 20.dp,
        gutter = ctx.hingeGutter,
        publishGeometry = publishGeometry,
        a11yMovable = true,
        acceptDrops = true,
    )
}

/**
 * Moves every slot that does not fit the current column count (negative coordinates, `col >= cols`,
 * or a span that runs past the last column) into the first free cell in reading order, so lowering
 * "Cover columns" / "Inner columns" in Settings below the seeded count never hides items. Slots that
 * fit keep their stored position; stack mates move together; nothing is written back to the
 * repository. With [skipFree], items carrying free-placement bounds are left where they are.
 */
internal fun reflowGridItems(items: List<HomeItem>, cols: Int, skipFree: Boolean = false): List<HomeItem> {
    val columns = cols.coerceAtLeast(1)
    val slots = groupHomeSlots(items)
    fun isFree(slot: HomeSlot): Boolean = skipFree && slot.anchor.free && slot.anchor.hasFreeBounds
    fun fits(slot: HomeSlot): Boolean {
        val a = slot.anchor
        return isFree(slot) || (a.col >= 0 && a.row >= 0 && a.col + a.spanX.coerceAtLeast(1) <= columns)
    }
    if (slots.all(::fits)) return items

    val occupied = HashSet<Pair<Int, Int>>()
    fun mark(col: Int, row: Int, spanX: Int, spanY: Int) {
        for (r in row until row + spanY) {
            for (c in col until col + spanX) occupied.add(c to r)
        }
    }
    fun free(col: Int, row: Int, spanX: Int, spanY: Int): Boolean {
        for (r in row until row + spanY) {
            for (c in col until col + spanX) if ((c to r) in occupied) return false
        }
        return true
    }

    val kept = slots.filter(::fits)
    kept.filterNot(::isFree).forEach { slot ->
        val a = slot.anchor
        mark(a.col, a.row, a.spanX.coerceAtLeast(1), a.spanY.coerceAtLeast(1))
    }

    val moved = slots
        .filterNot(::fits)
        .sortedWith(compareBy<HomeSlot> { it.anchor.row }.thenBy { it.anchor.col })
        .flatMap { slot ->
            val a = slot.anchor
            val spanX = a.spanX.coerceIn(1, columns)
            val spanY = a.spanY.coerceAtLeast(1)
            var row = 0
            var col = 0
            var placed = false
            while (!placed) {
                col = 0
                while (col + spanX <= columns) {
                    if (free(col, row, spanX, spanY)) {
                        placed = true
                        break
                    }
                    col++
                }
                if (!placed) row++
            }
            mark(col, row, spanX, spanY)
            slot.items.map { it.copy(col = col, row = row, spanX = spanX, spanY = spanY) }
        }
    return kept.flatMap { it.items } + moved
}

/**
 * One item filling its slot: app (with its notification badge), folder ([HomeFolderTile]), pair
 * ([HomePairTile]) or widget. Widgets get [widgetPadding] around them (the grid gap of the mock-up)
 * and a long-press hook. An APP item whose app is in `hiddenApps` leaves its cell empty; one whose
 * app is uninstalled shows [HomeMissingTile].
 */
@Composable
internal fun HomeItemTile(
    ctx: HomeContext,
    item: HomeItem,
    widgetPadding: Dp = 6.dp,
    highlight: Boolean = false,
    access: HomeItemGridInfo? = null,
) {
    when (item.kind) {
        ItemKind.APP -> {
            val app = ctx.appFor(item)
            when {
                app != null -> {
                    // A long press opens the item popup as before; moving the finger on from there
                    // lifts the tile and drags it (to another cell, a page, or the dock).
                    val dragController = ctx.dragController
                    val dragHolder = remember { HomeAnchorHolder() }
                    val dragModifier = if (dragController != null && !ctx.editMode && item.id > 0L) {
                        Modifier.homeItemDrag(
                            controller = dragController,
                            holder = dragHolder,
                            payload = { DragPayload.Item(item) },
                            onStarted = { ctx.pressed.value = null },
                        )
                    } else {
                        Modifier
                    }
                    Box(Modifier.fillMaxSize().then(dragModifier)) {
                        HomeAppTile(
                            app = app,
                            iconSize = ctx.iconSize,
                            showLabel = ctx.labels,
                            onClick = { ctx.onLaunch(app) },
                            badge = ctx.badgeFor(app.packageName),
                            badgeStyle = ctx.badgeStyle,
                            onLongPressAt = { anchor -> ctx.longPress(item, anchor) },
                            a11yActions = rememberHomeItemActions(ctx, item, access),
                        )
                        HomeItemPressOverlay(ctx, item)
                    }
                }
                // Nothing to open here: the cell looks empty, so a long press on it enters edit mode
                // (the grid ignores presses on cells that hold an item, so the tile has to do it).
                ctx.isHiddenItem(item) -> Spacer(
                    Modifier.fillMaxSize().homeLongPress { ctx.emptyLongPress(item.pageId, item.col, item.row) },
                )
                else -> HomeMissingTile(
                    ctx.iconSize,
                    Modifier.homeLongPress { ctx.emptyLongPress(item.pageId, item.col, item.row) },
                )
            }
        }
        ItemKind.FOLDER -> HomeFolderTile(ctx, item, access)
        ItemKind.WIDGET -> {
            val holder = remember { HomeAnchorHolder() }
            val actions = rememberHomeItemActions(ctx, item, access)
            val widgetName = item.title ?: item.builtinId?.let { HomeBuiltInIds.title(it) } ?: "Widget"
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(widgetPadding)
                    // TalkBack: the widget gets a name and the Move / Resize / Remove actions; its
                    // own content stays individually reachable inside it.
                    .semantics {
                        contentDescription = "$widgetName widget"
                        if (actions.isNotEmpty()) customActions = actions
                    }
                    .homeAnchor(holder)
                    // Watched in the Initial pass so it also fires on top of the widget's own
                    // content (hosted app widgets, clickable built-ins); it opens edit mode.
                    .homeLongPressFirst { ctx.widgetLongPress(item, holder.bounds()) },
            ) {
                HomeWidgetItem(item, Modifier.fillMaxSize(), highlight = highlight)
                HomeItemPressOverlay(ctx, item)
            }
        }
        ItemKind.PAIR -> HomePairTile(ctx, item, access)
    }
}

/**
 * The TalkBack actions of [item] (Open menu, Move, Resize, Remove, see [homeItemAccessActions]).
 * Unsaved items (`id <= 0`: auto-filled previews) get the menu action only.
 */
@Composable
internal fun rememberHomeItemActions(
    ctx: HomeContext,
    item: HomeItem,
    access: HomeItemGridInfo?,
): List<CustomAccessibilityAction> {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val announce = rememberA11yAnnouncer()
    // Building the Move / Resize actions walks every sibling, so only do it while an accessibility
    // service (TalkBack, Switch Access, Voice Access) is running.
    val accessibilityOn = rememberAccessibilityServicesEnabled()
    return remember(item, access, accessibilityOn) {
        if (!accessibilityOn) {
            emptyList()
        } else {
            homeItemAccessActions(
                item = item,
                grid = access,
                graph = graph,
                announce = announce,
                onOpenMenu = { ctx.longPress(item, null) },
                onDelete = if (item.id > 0L) ({ removeHomeItem(context, graph, item) }) else null,
            )
        }
    }
}

/** True while any accessibility service is enabled; follows changes through the system listener. */
@Composable
private fun rememberAccessibilityServicesEnabled(): Boolean {
    val context = LocalContext.current
    val manager = remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var enabled by remember(manager) { mutableStateOf(manager?.isEnabled == true) }
    DisposableEffect(manager) {
        val listener = AccessibilityManager.AccessibilityStateChangeListener { on -> enabled = on }
        manager?.addAccessibilityStateChangeListener(listener)
        enabled = manager?.isEnabled == true
        onDispose { manager?.removeAccessibilityStateChangeListener(listener) }
    }
    return enabled
}

/* ---------------- Hybrid panels ---------------- */

/**
 * Hybrid panels (mock-up `HOME.*.hybrid`). Cover: a widget panel (glance over calendar, or the
 * page's widget items) above an apps panel of 4 x 2 favorites. Inner: the apps panel (4 x 4) on
 * the left and a column of widgets (clock, calendar, media, or the page's widget items) on the
 * right, split at the hinge.
 */
@Composable
internal fun HomeHybridPage(
    ctx: HomeContext,
    page: HomePage?,
    items: List<HomeItem>,
    modifier: Modifier = Modifier,
    publishGeometry: Boolean = false,
) {
    val pageId = page?.id ?: 0L
    val free = ctx.freeMode
    val widgetItems = remember(items, ctx.screen, pageId, free) {
        val own = items.filter { it.kind == ItemKind.WIDGET }
        val source = if (own.isEmpty()) defaultHybridWidgetItems(ctx.screen, pageId).asPreviewItems() else own
        reflowGridItems(source, HYBRID_WIDGET_COLUMNS, skipFree = free)
    }
    val widgetRows = remember(widgetItems) { max(1, dashboardRows(widgetItems)) }

    if (!ctx.isInner) {
        Column(modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            HomeSurfacePanel(Modifier.weight(1.15f).fillMaxWidth(), contentPadding = 10.dp) {
                HomeItemsGrid(
                    ctx = ctx,
                    pageId = pageId,
                    items = widgetItems,
                    cols = HYBRID_WIDGET_COLUMNS,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    fixedRows = widgetRows,
                    cellPadding = 4.dp,
                    heroHighlight = true,
                )
            }
            HomeSurfacePanel(Modifier.weight(1f).fillMaxWidth()) {
                HomeFavoriteGrid(
                    ctx = ctx,
                    favorites = ctx.favorites(items, 8),
                    cols = 4,
                    rows = 2,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    items = items,
                    publishGeometry = publishGeometry,
                )
            }
        }
    } else {
        val gap = if (ctx.hingeGutter > 0.dp) 32.dp else 24.dp
        Row(modifier.fillMaxSize()) {
            HomeSurfacePanel(Modifier.weight(1f).fillMaxHeight()) {
                HomeFavoriteGrid(
                    ctx = ctx,
                    favorites = ctx.favorites(items, 16),
                    cols = 4,
                    rows = 4,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    items = items,
                    publishGeometry = publishGeometry,
                )
            }
            Spacer(Modifier.width(gap))
            HomeItemsGrid(
                ctx = ctx,
                pageId = pageId,
                items = widgetItems,
                cols = HYBRID_WIDGET_COLUMNS,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                fixedRows = widgetRows,
                cellPadding = 7.dp,
                heroHighlight = true,
            )
        }
    }
}

/** The hybrid widget panel is two cells wide; full-width cards span both. */
internal const val HYBRID_WIDGET_COLUMNS: Int = 2

/** A plain cols x rows grid of app tiles, filled in order. */
@Composable
internal fun HomeFavoriteGrid(
    ctx: HomeContext,
    favorites: List<AppInfo>,
    cols: Int,
    rows: Int,
    modifier: Modifier = Modifier,
    items: List<HomeItem> = emptyList(),
    publishGeometry: Boolean = false,
) {
    val shown = favorites.take(cols * rows)
    val cells = remember(shown.size, cols) {
        List(shown.size) { index -> HomeCell(index % cols, index / cols) }
    }
    val keys = remember(shown) { shown.map { it.key } }
    HomeCellGrid(
        cols = cols,
        cells = cells,
        modifier = modifier,
        fixedRows = rows,
        keys = keys,
        geometryScreen = if (publishGeometry) ctx.screen else null,
    ) { index ->
        val app = shown[index]
        Box(Modifier.fillMaxSize()) {
            HomeAppTile(
                app = app,
                iconSize = ctx.iconSize,
                showLabel = ctx.labels,
                onClick = { ctx.onLaunch(app) },
                badge = ctx.badgeFor(app.packageName),
                badgeStyle = ctx.badgeStyle,
                onLongPressAt = { anchor -> ctx.longPressApp(app, items, anchor) },
            )
            HomeItemPressOverlay(ctx, ctx.itemFor(app, items))
        }
    }
}
