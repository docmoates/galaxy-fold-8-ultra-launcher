package com.foldos.launcher.home

import android.view.accessibility.AccessibilityManager
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.foldos.launcher.core.model.ItemKind

/**
 * Renders one home page. Since Round 2.0 every page is the same thing: a free-form grid canvas
 * (`settings.colsFor(screen)` columns, rows from the height like Pixel) holding apps, folders,
 * pairs, widgets and cards wherever the user dropped them. `HomePage.style` has no effect here.
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
    HomeCanvasPage(ctx, page, items, modifier, publishGeometry)
}

/* ---------------- Slots: one grid cell, holding an item or a stack ---------------- */

/** The items that share one grid slot: a single item, or a stack ordered by `stackIndex`. */
internal class HomeSlot(val items: List<HomeItem>) {
    val anchor: HomeItem get() = items.first()
    val isStack: Boolean get() = items.size > 1

    /** Grid cell for this slot; a free cell when [freeMode] and the anchor carries dp bounds. */
    fun toCell(freeMode: Boolean): HomeCell {
        val a = anchor
        // App / folder / pair tiles only answer presses on their own icon and label (see HomeCell.tile).
        val tile = !isStack && a.kind != ItemKind.WIDGET && a.kind != ItemKind.CARD
        return if (freeMode && a.free && a.hasFreeBounds) {
            HomeCell(a.col, a.row, a.spanX, a.spanY, a.xDp, a.yDp, a.wDp, a.hDp, tile = tile)
        } else {
            HomeCell(a.col, a.row, a.spanX.coerceAtLeast(1), a.spanY.coerceAtLeast(1), tile = tile)
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

/** True when every free-placement field of this item is set. */
val HomeItem.hasFreeBounds: Boolean
    get() = xDp != null && yDp != null && wDp != null && hDp != null

/** Gives unsaved preview items distinct negative ids so they can be keyed and told apart. */
internal fun List<HomeItem>.asPreviewItems(): List<HomeItem> =
    mapIndexed { index, item -> if (item.id == 0L) item.copy(id = -(index + 1L)) else item }

/** Rows needed to show [items] on a grid: the lowest cell any non-free item reaches. */
fun rowsNeededFor(items: List<HomeItem>): Int =
    items.filterNot { it.free && it.hasFreeBounds }
        .maxOfOrNull { it.row.coerceAtLeast(0) + it.spanY.coerceAtLeast(1) }
        ?: 1

/**
 * Lays out [items] as slots on a [cols]-column grid: widgets and cards span their cells, stacks flip
 * in one slot, and in free mode items with dp bounds leave the grid. Long presses on items go to
 * `HomeCallbacks.onItemLongPress`, those on empty cells to `onEmptyLongPress(pageId, col, row)`.
 *
 * @param heroHighlight draw the first glance / clock built-in with the highlight colors.
 * @param minRows the grid never has fewer rows than this (the row count still grows with the height).
 */
@Composable
internal fun HomeItemsGrid(
    ctx: HomeContext,
    pageId: Long,
    items: List<HomeItem>,
    cols: Int,
    modifier: Modifier = Modifier,
    fixedRows: Int? = null,
    minRows: Int? = null,
    minCellHeight: Dp = 96.dp,
    gutter: Dp = 0.dp,
    cellPadding: Dp = 6.dp,
    heroHighlight: Boolean = false,
    publishGeometry: Boolean = false,
    a11yMovable: Boolean = false,
    acceptDrops: Boolean = false,
) {
    val slots = remember(items) { groupHomeSlots(items) }
    // Drag and drop: the page's cell grid is a drop target 'page:<pageId>' (apps / widgets / cards
    // from the "Add to home" sheet, items dragged from another cell); the drop cell comes from the grid's own metrics.
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
    // are the stored ones offer them, see homeItemAccessActions.
    val a11yRows = fixedRows ?: if (publishGeometry) HomeGeometryStore.measured[ctx.screen]?.rows else null
    val a11yGrid = remember(items, cols, a11yRows, a11yMovable) {
        if (a11yMovable) HomeItemGridInfo(items, cols, a11yRows) else null
    }
    HomeCellGrid(
        cols = cols,
        cells = cells,
        modifier = modifier,
        fixedRows = fixedRows,
        minRows = minRows,
        minCellHeight = minCellHeight,
        gutter = gutter,
        keys = keys,
        geometryScreen = if (publishGeometry) ctx.screen else null,
        onEmptyLongPress = { col, row -> ctx.emptyLongPress(pageId, col, row) },
        dropTarget = dropTarget,
        // A tile owns its icon area (4dp padding + icon) for the empty-space test; its label claims
        // presses itself, and the stretched slack under it is empty space.
        tileHitHeight = ctx.iconSize + 4.dp,
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

/* ---------------- The canvas page ---------------- */

/**
 * The one page layout: `ctx.cols` columns, as many rows as the height allows (never fewer than the
 * items need, so nothing is ever hidden; cells shrink a little when a page holds more rows than fit),
 * every item at its stored cell, hero widgets highlighted, drops accepted.
 */
@Composable
internal fun HomeCanvasPage(
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
    // exists it renders its real items, even when the user has emptied it.
    val placeholder = page == null
    val gridItems = remember(items, cols, pageId, placeholder, free, ctx.sortedApps) {
        val source = if (items.isEmpty() && placeholder) ctx.autoGridItems(pageId, cols, maxRows = 8) else items
        reflowGridItems(source, cols, skipFree = free)
    }
    val needed = remember(gridItems) { rowsNeededFor(gridItems) }
    // Labels reserve two lines (bigger in Easy mode / with a larger label size), so each row needs that room.
    val labelHeight = appLabelBlockHeight(ctx.settings)
    HomeItemsGrid(
        ctx = ctx,
        pageId = pageId,
        items = gridItems,
        cols = cols,
        modifier = modifier.fillMaxSize(),
        minRows = needed,
        minCellHeight = ctx.iconSize + labelHeight + 20.dp,
        gutter = ctx.hingeGutter,
        heroHighlight = true,
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
 * ([HomePairTile]), widget or card ([HomeCardTile]). Widgets get [widgetPadding] around them (the
 * grid gap of the mock-up). An APP item whose app is in `hiddenApps` leaves its cell empty; one whose
 * app is uninstalled shows [HomeMissingTile]. Every tile can be picked up: long press, then move.
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
                    // lifts the tile and drags it (to another cell, a page, a card or the dock).
                    val dragModifier = ctx.itemDragModifier(item)
                    Box(Modifier.fillMaxSize()) {
                        HomeAppTile(
                            app = app,
                            iconSize = ctx.iconSize,
                            showLabel = ctx.labels,
                            onClick = { ctx.onLaunch(app) },
                            badge = ctx.badgeFor(app.packageName),
                            badgeStyle = ctx.badgeStyle,
                            onLongPressAt = { anchor -> ctx.longPress(item, anchor) },
                            a11yActions = rememberHomeItemActions(ctx, item, access),
                            // The drag starts only on the visible icon + label, never on the empty
                            // slack of the stretched cell below it.
                            contentModifier = dragModifier,
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
                    // content (hosted app widgets, clickable built-ins): a long press opens the widget's
                    // menu (Remove, Resize, Move to card); moving on from the press drags the widget.
                    .homeWidgetGestures(ctx, item, holder),
            ) {
                HomeWidgetItem(item, Modifier.fillMaxSize(), highlight = highlight)
                HomeItemPressOverlay(ctx, item)
            }
        }
        ItemKind.PAIR -> HomePairTile(ctx, item, access)
        ItemKind.CARD -> HomeCardTile(ctx, item, widgetPadding, access)
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

/* ---------------- Favorites row (Flex Mode) ---------------- */

/** A plain cols x rows grid of app tiles, filled in order (the six-app row under the hinge in Flex Mode). */
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
