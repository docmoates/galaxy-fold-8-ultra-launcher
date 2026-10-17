package com.foldos.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.findRootCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.a11y.HomeItemGridInfo
import com.foldos.launcher.a11y.a11yAppLabel
import com.foldos.launcher.a11y.a11yButton
import com.foldos.launcher.a11y.a11yFocusTile
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.ui.DragPayload
import com.foldos.launcher.core.ui.DragTargetIds
import com.foldos.launcher.core.ui.dragTarget
import com.foldos.launcher.items.FolderIcon
import com.foldos.launcher.items.Folders
import com.foldos.launcher.theme.LocalFoldOsTokens
import com.foldos.launcher.theme.SpaceBlackScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.ceil
import kotlin.math.max

/** The card's children from `LayoutRepository.children`, in order; empty for an unsaved (id <= 0) card. */
@Composable
internal fun rememberCardChildren(card: HomeItem): List<HomeItem> {
    val graph = LocalAppGraph.current
    val flow: Flow<List<HomeItem>> = remember(graph, card.id) {
        if (card.id > 0) graph.layouts.children(card.id) else flowOf(emptyList())
    }
    val children by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    return remember(children) { children.sortedWith(compareBy({ it.row }, { it.col })) }
}

/** Where a card's children were laid out, in the card's own coordinates, for the chrome hit test and drop order. */
private class CardChildBounds {
    var root: LayoutCoordinates? = null
    val children = LinkedHashMap<Long, LayoutCoordinates>()

    fun boundsOf(child: LayoutCoordinates): Rect? {
        val r = root ?: return null
        if (!r.isAttached || !child.isAttached) return null
        return r.localBoundingBoxOf(child, clipBounds = false)
    }

    /** True when [local] (card coordinates) lies on a child, i.e. not on the card chrome. */
    fun onChild(local: Offset): Boolean = children.values.any { c -> boundsOf(c)?.contains(local) == true }

    /** Position of the child under [local], or [count] (the end) when the point is past every child. */
    fun orderAt(local: Offset, ordered: List<HomeItem>, count: Int): Int {
        for ((index, item) in ordered.withIndex()) {
            val c = children[item.id] ?: continue
            if (boundsOf(c)?.contains(local) == true) return index
        }
        return count
    }

    fun toLocal(rootPoint: Offset): Offset? {
        val r = root ?: return null
        if (!r.isAttached) return null
        return r.localPositionOf(r.findRootCoordinates(), rootPoint)
    }
}

/** True when [payload] can go inside [card]. */
internal fun cardAccepts(card: HomeItem, payload: DragPayload): Boolean = when (payload) {
    is DragPayload.App -> card.isAppsCard
    is DragPayload.Widget -> false
    is DragPayload.Card -> false
    is DragPayload.Item -> {
        val item = payload.item
        when {
            item.id <= 0L || item.kind == ItemKind.CARD -> false
            item.kind == ItemKind.WIDGET -> false
            else -> card.isAppsCard
        }
    }
}

/**
 * A CARD item on the page (Round 2.0): a rounded theme surface (or nothing, for a "transparent"
 * card) with an optional title row, holding the card's children. An apps card lays its apps,
 * folders and pairs out as a mini icon grid whose column count follows the card's width (or as
 * icon + name rows in list style); widgets are never inside a card (1.4.0).
 * Children keep their own taps, menus and drags (drag one out onto the page to
 * take it out of the card); a long press on the card chrome (title, padding, empty space) opens
 * the card's menu, and moving on from it drags the whole card. The card is a drop target: apps /
 * widgets / items dropped on it go inside at the position under the pointer.
 */
@Composable
internal fun HomeCardTile(ctx: HomeContext, card: HomeItem, cellPadding: Dp, access: HomeItemGridInfo?) {
    // Space black: an app panel that draws a background is solid black with white labels; a clear panel is not.
    SpaceBlackScope(active = ctx.cardBackground && !card.cardClear) { HomeCardTileContent(ctx, card, cellPadding, access) }
}

@Composable
private fun HomeCardTileContent(ctx: HomeContext, card: HomeItem, cellPadding: Dp, access: HomeItemGridInfo?) {
    val tokens = LocalFoldOsTokens.current
    val children = rememberCardChildren(card)
    val holder = remember { HomeAnchorHolder() }
    val bounds = remember { CardChildBounds() }
    val actions = rememberHomeItemActions(ctx, card, access)
    val shape = RoundedCornerShape(tokens.radius)
    val surface = ctx.cardBackground && !card.cardClear
    val title = card.title?.trim()?.takeIf { it.isNotEmpty() && ctx.cardTitles }
    val kindName = "app card"
    val description = (title ?: kindName) + ", " + children.size + (if (children.size == 1) " item" else " items")

    val dragController = ctx.dragController
    val dropHandler = ctx.onDropOnCard
    val dropModifier: Modifier = if (dragController != null && dropHandler != null && card.id > 0L) {
        val targetId = DragTargetIds.card(card.id)
        Modifier
            .dragTarget(
                controller = dragController,
                id = targetId,
                accepts = { payload -> cardAccepts(card, payload) },
                onDrop = { payload, pointerInRoot ->
                    val local = bounds.toLocal(pointerInRoot)
                    val order = if (local == null) children.size else bounds.orderAt(local, children, children.size)
                    dropHandler(payload, card, order)
                },
            )
            .drawWithContent {
                drawContent()
                if (dragController.hoverTargetId.value == targetId) {
                    val w = 2.dp.toPx()
                    drawRoundRect(
                        color = tokens.accent.copy(alpha = 0.16f),
                        cornerRadius = CornerRadius(tokens.radius.toPx()),
                    )
                    drawRoundRect(
                        color = tokens.accent,
                        topLeft = Offset(w / 2f, w / 2f),
                        size = androidx.compose.ui.geometry.Size(size.width - w, size.height - w),
                        cornerRadius = CornerRadius(tokens.radius.toPx()),
                        style = Stroke(w),
                    )
                }
            }
    } else {
        Modifier
    }

    Box(
        Modifier
            .fillMaxSize()
            .padding(cellPadding)
            .clip(shape)
            .then(
                if (surface) {
                    Modifier.background(tokens.surface, shape).border(1.dp, tokens.surfaceBorder, shape)
                } else {
                    Modifier
                },
            )
            .semantics {
                contentDescription = description
                if (actions.isNotEmpty()) customActions = actions
            }
            .homeAnchor(holder)
            .onGloballyPositioned { bounds.root = it }
            // The card's own long press / drag only on its chrome: a press that lands on a child is the child's.
            .homeWidgetGestures(ctx, card, holder, ignoreAt = { local -> bounds.onChild(local) })
            .then(dropModifier),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 10.dp, vertical = 8.dp)) {
            if (title != null) {
                Text(
                    text = title.uppercase(),
                    color = tokens.onSurface.copy(alpha = 0.72f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 4.dp),
                )
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when {
                    children.isEmpty() -> CardEmptyHint("Drop apps here")
                    card.isListCard -> CardAppList(ctx, children, bounds)
                    else -> CardAppGrid(ctx, children, bounds)
                }
            }
        }
        HomeItemPressOverlay(ctx, card)
    }
}

@Composable
private fun CardEmptyHint(text: String) {
    val tokens = LocalFoldOsTokens.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = tokens.onSurface.copy(alpha = 0.45f),
            fontSize = 13.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(8.dp),
        )
    }
}

/** Records a child's layout coordinates for the card hit test / drop order. */
private fun Modifier.cardChild(bounds: CardChildBounds, id: Long): Modifier =
    onGloballyPositioned { bounds.children[id] = it }

/** One app / folder / pair child drawn in a fixed cell; the child keeps its own tap, menu and drag. */
@Composable
private fun CardAppCell(ctx: HomeContext, child: HomeItem, bounds: CardChildBounds, modifier: Modifier) {
    val tokens = LocalFoldOsTokens.current
    Box(modifier.cardChild(bounds, child.id)) {
        when (child.kind) {
            ItemKind.FOLDER -> Box(Modifier.fillMaxSize().then(ctx.itemDragModifier(child))) { HomeFolderTile(ctx, child) }
            ItemKind.PAIR -> Box(Modifier.fillMaxSize().then(ctx.itemDragModifier(child))) { HomePairTile(ctx, child) }
            else -> {
                val app = ctx.appFor(child)
                if (app == null) {
                    HomeMissingTile(ctx.iconSize)
                } else {
                    HomeAppTile(
                        app = app,
                        iconSize = ctx.iconSize,
                        showLabel = ctx.labels,
                        onClick = { ctx.onLaunch(app) },
                        badge = ctx.badgeFor(app.packageName),
                        badgeStyle = ctx.badgeStyle,
                        onLongPressAt = { anchor -> ctx.longPress(child, anchor) },
                        labelColor = tokens.onSurface,
                        contentModifier = ctx.itemDragModifier(child),
                    )
                    HomeItemPressOverlay(ctx, child)
                }
            }
        }
    }
}

/** Mini icon grid: as many columns as icons (plus 22dp of air) fit the card's width; scrolls when the rows run out. */
@Composable
private fun CardAppGrid(ctx: HomeContext, children: List<HomeItem>, bounds: CardChildBounds) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val cellWidth = ctx.iconSize + 22.dp
        val cols = max(1, (maxWidth / cellWidth).toInt())
        val rowHeight = ctx.iconSize + (if (ctx.labels) appLabelBlockHeight(ctx.settings) else 0.dp) + 12.dp
        val rows = ceil(children.size / cols.toFloat()).toInt().coerceAtLeast(1)
        val fits = rowHeight * rows <= maxHeight
        Column(
            Modifier
                .fillMaxSize()
                .then(if (fits) Modifier else Modifier.verticalScroll(rememberScrollState())),
            verticalArrangement = if (fits) Arrangement.SpaceEvenly else Arrangement.Top,
        ) {
            children.chunked(cols).forEach { rowItems ->
                Row(Modifier.fillMaxWidth().height(rowHeight)) {
                    rowItems.forEach { child ->
                        CardAppCell(ctx, child, bounds, Modifier.weight(1f).fillMaxSize())
                    }
                    repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** List style (the old "Minimal list"): a 32dp icon and the name in one row per app; scrolls. */
@Composable
private fun CardAppList(ctx: HomeContext, children: List<HomeItem>, bounds: CardChildBounds) {
    val tokens = LocalFoldOsTokens.current
    val rowHeight = 44.dp
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        children.forEach { child ->
            val app = ctx.appFor(child)
            val name = when (child.kind) {
                ItemKind.FOLDER -> child.title ?: "Folder"
                ItemKind.PAIR -> child.title ?: homePairTitle(app, ctx.appFor2(child))
                else -> app?.label ?: child.title ?: "App"
            }
            val holder = remember(child.id) { HomeAnchorHolder() }
            val activate: () -> Unit = {
                when (child.kind) {
                    ItemKind.FOLDER -> ctx.openFolder(child)
                    else -> if (app != null) ctx.onLaunch(app)
                }
            }
            val menu: () -> Unit = { ctx.longPress(child, holder.bounds()) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(rowHeight)
                    .cardChild(bounds, child.id)
                    .a11yButton(label = a11yAppLabel(name, ctx.badgeFor(child)), onActivate = activate, onMenu = menu)
                    .a11yFocusTile(onActivate = activate, onMenu = menu)
                    .homeAnchor(holder)
                    .then(ctx.itemDragModifier(child))
                    .homeTapGestures(onTap = activate, onLongPress = menu)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(32.dp)) {
                    when {
                        child.kind == ItemKind.FOLDER -> FolderIcon(
                            folder = child,
                            children = rememberFolderChildren(child),
                            apps = ctx.appsByKey,
                            sizeDp = 32,
                            style = Folders.styleFor(child, ctx.settings),
                            modifier = Modifier.size(32.dp),
                        )
                        app != null -> HomeAppIcon(app = app, size = 32.dp, badge = ctx.badgeFor(child), badgeStyle = ctx.badgeStyle)
                        else -> Box(Modifier.size(32.dp).border(1.dp, tokens.surfaceBorder, CircleShape))
                    }
                }
                Text(
                    text = name,
                    color = tokens.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (ctx.badgeFor(child) > 0) {
                    Box(Modifier.size(8.dp).background(tokens.accent, CircleShape))
                }
            }
            HomeItemPressOverlay(ctx, child)
        }
    }
}

