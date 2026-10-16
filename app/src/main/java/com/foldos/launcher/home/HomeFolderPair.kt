package com.foldos.launcher.home

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.a11y.HomeItemGridInfo
import com.foldos.launcher.a11y.a11yAppLabel
import com.foldos.launcher.a11y.a11yButton
import com.foldos.launcher.a11y.a11yFocusTile
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.items.AppPairs
import com.foldos.launcher.items.FolderIcon
import com.foldos.launcher.items.FolderSheet
import com.foldos.launcher.items.Folders
import com.foldos.launcher.items.PairIcon
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/* ---------------- Folders ---------------- */

/** The folder's children from `LayoutRepository.children`; empty for an unsaved (id <= 0) folder. */
@Composable
internal fun rememberFolderChildren(folder: HomeItem): List<HomeItem> {
    val graph = LocalAppGraph.current
    val flow: Flow<List<HomeItem>> = remember(graph, folder.id) {
        if (folder.id > 0) graph.layouts.children(folder.id) else flowOf(emptyList())
    }
    val children by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    return children
}

/**
 * A FOLDER item on the grid: `items.FolderIcon` (or [HomeItemHooks.folderIcon] when provided) in
 * the folder's style (`Folders.styleFor`), the children's summed notification badge and the label.
 * Tap opens the folder ([HomeContext.openFolder]): `items.FolderSheet` pops up next to the icon
 * (or [HomeItemHooks.onOpenFolder] takes over); long press opens the item popup
 * ([HomeItemPressOverlay]).
 *
 * FolderSheet callbacks: rename writes through `Folders.rename`; "Remove from folder" puts the
 * child back on the folder's page at the first free cell (`LayoutRepository.removeFromFolder`).
 */
@Composable
internal fun HomeFolderTile(ctx: HomeContext, item: HomeItem, access: HomeItemGridInfo? = null) {
    val tokens = LocalFoldOsTokens.current
    val graph = LocalAppGraph.current
    val children = rememberFolderChildren(item)
    val holder = remember { HomeAnchorHolder() }
    val folderName = item.title ?: Folders.DEFAULT_TITLE
    val itemWord = if (children.size == 1) "item" else "items"
    val openFolder: () -> Unit = { ctx.openFolder(item) }
    val openMenu: () -> Unit = { ctx.longPress(item, holder.bounds()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .a11yButton(
                label = a11yAppLabel("$folderName, folder with ${children.size} $itemWord", ctx.badgeFor(children)),
                onActivate = openFolder,
                onMenu = openMenu,
                activateLabel = "Open folder",
                customActions = rememberHomeItemActions(ctx, item, access),
            )
            .a11yFocusTile(onActivate = openFolder, onMenu = openMenu)
            .homeAnchor(holder)
            .homeTapGestures(onTap = openFolder, onLongPress = openMenu)
            .padding(horizontal = 1.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HomeBadgedBox(count = ctx.badgeFor(children), style = ctx.badgeStyle, modifier = Modifier.size(ctx.iconSize)) {
            val hook = ctx.hooks.folderIcon
            if (hook != null) {
                hook(item, children, ctx.iconSize, Modifier.size(ctx.iconSize))
            } else {
                FolderIcon(
                    folder = item,
                    children = children,
                    apps = ctx.appsByKey,
                    sizeDp = ctx.settings.effectiveIconSizeDp,
                    style = Folders.styleFor(item, ctx.settings),
                    modifier = Modifier.size(ctx.iconSize),
                )
            }
        }
        if (ctx.labels) {
            Spacer(Modifier.height(AppLabelStyle.ICON_GAP))
            AppLabel(text = folderName, color = tokens.onSurface)
        }
        // Pop-ups anchored to this tile: the open folder and the long-press menu.
        if (ctx.hooks.onOpenFolder == null && ctx.folderSheet.value?.id == item.id && item.id > 0) {
            val cols = ctx.cols
            FolderSheet(
                folder = item,
                children = children,
                apps = ctx.appsByKey,
                onLaunch = ctx.onLaunch,
                onRename = { title -> graph.appScope.launch { Folders.rename(graph.layouts, item, title) } },
                onRemove = { child ->
                    graph.appScope.launch {
                        runCatching {
                            val onPage = graph.layouts.items(item.pageId).first()
                            val (col, row) = homeFirstFreeCell(onPage, cols)
                            graph.layouts.removeFromFolder(child.id, item.pageId, col, row)
                        }
                    }
                },
                onDismiss = { ctx.folderSheet.value = null },
            )
        }
        HomeItemPressOverlay(ctx, item)
    }
}

/**
 * The first 1x1 cell, in reading order on a [cols]-column grid, that none of [items] covers; the
 * first column of the row below the lowest item when the visible rows are full (the grid reflows
 * items that fall off the page, see `reflowGridItems`).
 */
internal fun homeFirstFreeCell(items: List<HomeItem>, cols: Int): Pair<Int, Int> {
    val columns = cols.coerceAtLeast(1)
    val occupied = HashSet<Pair<Int, Int>>()
    var lowest = -1
    for (item in items) {
        val spanX = item.spanX.coerceAtLeast(1)
        val spanY = item.spanY.coerceAtLeast(1)
        for (r in item.row until item.row + spanY) {
            for (c in item.col until item.col + spanX) occupied.add(c to r)
        }
        lowest = maxOf(lowest, item.row + spanY - 1)
    }
    for (row in 0..(lowest + 1).coerceAtLeast(0)) {
        for (col in 0 until columns) {
            if ((col to row) !in occupied) return col to row
        }
    }
    return 0 to (lowest + 1)
}

/* ---------------- App pairs ---------------- */

/**
 * A PAIR item: `items.PairIcon` (or [HomeItemHooks.pairIcon] when provided) with both apps' badges
 * summed and the label ("A & B" when the item has no title). Tap launches the pair
 * ([HomeContext.launchPair]); long press opens the item popup ([HomeItemPressOverlay]).
 */
@Composable
internal fun HomePairTile(ctx: HomeContext, item: HomeItem, access: HomeItemGridInfo? = null) {
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    val a = ctx.appFor(item)
    val b = ctx.appFor2(item)
    val holder = remember { HomeAnchorHolder() }
    val pairName = item.title ?: homePairTitle(a, b)
    val launchPair: () -> Unit = { ctx.launchPair(item, context) }
    val openMenu: () -> Unit = { ctx.longPress(item, holder.bounds()) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .a11yButton(
                label = a11yAppLabel("$pairName, app pair", ctx.badgeFor(item)),
                onActivate = launchPair,
                onMenu = openMenu,
                activateLabel = "Open both apps",
                customActions = rememberHomeItemActions(ctx, item, access),
            )
            .a11yFocusTile(onActivate = launchPair, onMenu = openMenu)
            .homeAnchor(holder)
            .homeTapGestures(onTap = launchPair, onLongPress = openMenu)
            .padding(horizontal = 1.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HomeBadgedBox(count = ctx.badgeFor(item), style = ctx.badgeStyle, modifier = Modifier.size(ctx.iconSize)) {
            val hook = ctx.hooks.pairIcon
            if (hook != null) {
                hook(item, a, b, ctx.iconSize, Modifier.size(ctx.iconSize))
            } else {
                PairIcon(pair = item, a = a, b = b, sizeDp = ctx.settings.effectiveIconSizeDp, modifier = Modifier.size(ctx.iconSize))
            }
        }
        if (ctx.labels) {
            Spacer(Modifier.height(AppLabelStyle.ICON_GAP))
            AppLabel(text = pairName, color = tokens.onSurface)
        }
        HomeItemPressOverlay(ctx, item)
    }
}

/** "A & B" from the resolved apps; "Pair" when neither is installed. */
fun homePairTitle(a: AppInfo?, b: AppInfo?): String = when {
    a != null && b != null -> "${a.label} & ${b.label}"
    a != null -> a.label
    b != null -> b.label
    else -> "Pair"
}

/**
 * Launches a pair without [HomeItemHooks.onLaunchPair]: both apps through `items.AppPairs.launch`
 * while the Pairs feature is on; otherwise, or when one app is gone, just the app that is left.
 */
internal fun launchHomePair(context: Context, ctx: HomeContext, item: HomeItem) {
    if (ctx.pairsOn && AppPairs.launch(context, item, ctx.appsByKey)) return
    val single = ctx.appFor(item) ?: ctx.appFor2(item) ?: return
    ctx.onLaunch(single)
}
