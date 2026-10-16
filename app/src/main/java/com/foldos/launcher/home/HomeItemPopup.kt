package com.foldos.launcher.home

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.core.AppGraph
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.repo.NotificationSummary
import com.foldos.launcher.items.ContextAction
import com.foldos.launcher.items.ItemContextMenu
import com.foldos.launcher.notifications.NotificationPreviewCard
import com.foldos.launcher.widgets.WidgetHostController
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * The item a long press opened the popup for. [anchor] is the tile's bounds in window px (null when
 * unknown), handed to [HomeItemHooks.onItemLongPress]; [app] is the resolved app for APP items.
 */
@Immutable
internal class HomePressedItem(val item: HomeItem, val anchor: Rect?, val app: AppInfo?)

/**
 * Identity of an item for the long-press popup: saved items by id and page, synthetic preview
 * items (id <= 0: auto-filled grid, favorites fallback) by what they show.
 */
internal fun HomeItem.homePressKey(): String =
    "$pageId|$id|$packageName|$activityName|$packageName2|$activityName2|$builtinId|$appWidgetId"

/**
 * The long-press popup of [item], composed inside the item's tile so both pop-ups anchor to it.
 * Draws nothing unless [item] is the pressed one ([HomeContext.isPressed]). Then:
 *
 * - above the tile: `notifications.NotificationPreviewCard` for an APP whose package has active
 *   notifications, while `settings.notificationPreview` (and the Badges feature) is on. It is a
 *   non-focusable popup so the menu below keeps the back / outside-tap dismissal for both.
 * - next to the tile (below it when there is room): the context menu, `items.ItemContextMenu` with
 *   `ContextAction.defaultsFor(item, settings)`, or [HomeItemHooks.contextMenu] when provided.
 *   Edit hands over to the edit hooks ([HomeContext.editLongPress]); Remove of a hosted app widget
 *   also releases its id ([removeHomeItem]); every other action keeps the menu's default.
 */
@Composable
internal fun HomeItemPressOverlay(ctx: HomeContext, item: HomeItem) {
    val pressed = ctx.pressed.value ?: return
    if (!ctx.isPressed(item)) return
    val context = LocalContext.current
    val graph = LocalAppGraph.current
    val onDismiss: () -> Unit = { ctx.pressed.value = null }

    val previewPackage = item.packageName?.takeIf { ctx.previewOn && item.kind == ItemKind.APP }
    val notifications = rememberHomeNotifications(previewPackage)
    if (previewPackage != null && notifications.isNotEmpty()) {
        val density = LocalDensity.current
        val provider = remember(density) {
            HomePreviewPositionProvider(
                gapPx = with(density) { 10.dp.roundToPx() },
                marginPx = with(density) { 8.dp.roundToPx() },
                menuHeightPx = with(density) { ESTIMATED_MENU_HEIGHT.roundToPx() },
            )
        }
        Popup(popupPositionProvider = provider, onDismissRequest = onDismiss, properties = PopupProperties(focusable = false)) {
            NotificationPreviewCard(packageName = previewPackage, onDismiss = onDismiss)
        }
    }

    val menu = ctx.hooks.contextMenu
    if (menu != null) {
        menu(item, pressed.app, onDismiss)
    } else {
        ItemContextMenu(
            item = item,
            actions = ContextAction.defaultsFor(item, ctx.settings),
            onDismiss = onDismiss,
            modifier = Modifier,
            onAction = { action ->
                when (action) {
                    ContextAction.EDIT -> {
                        ctx.editLongPress(item)
                        true
                    }
                    ContextAction.REMOVE -> if (item.appWidgetId != null && item.id > 0) {
                        removeHomeItem(context, graph, item)
                        true
                    } else {
                        false
                    }
                    else -> false
                }
            },
        )
    }
}

/** Height the context menu is assumed to take when deciding which side the preview goes on. */
private val ESTIMATED_MENU_HEIGHT = 400.dp

/** Active notifications of [packageName] from `graph.badges`; empty for null. */
@Composable
private fun rememberHomeNotifications(packageName: String?): List<NotificationSummary> {
    val graph = LocalAppGraph.current
    val flow: Flow<List<NotificationSummary>> = remember(graph, packageName) {
        if (packageName != null) graph.badges.notificationsFor(packageName) else flowOf(emptyList())
    }
    val notifications by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    return notifications
}

/**
 * Places the notification preview on the opposite side of the tile from the context menu: the
 * menu (`items.NearAnchorPositionProvider`) opens below the tile when [menuHeightPx] fits there,
 * so the preview goes above (and below when the menu has to flip up); when the preferred side has
 * no room the other side is used. Centred on the tile, kept [marginPx] inside the window.
 */
private class HomePreviewPositionProvider(
    private val gapPx: Int,
    private val marginPx: Int,
    private val menuHeightPx: Int,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val w = popupContentSize.width
        val h = popupContentSize.height
        val maxX = (windowSize.width - w - marginPx).coerceAtLeast(marginPx)
        val maxY = (windowSize.height - h - marginPx).coerceAtLeast(marginPx)
        val x = (anchorBounds.left + (anchorBounds.width - w) / 2).coerceIn(minOf(marginPx, maxX), maxX)
        val menuBelow = anchorBounds.bottom + gapPx + menuHeightPx + marginPx <= windowSize.height
        val above = anchorBounds.top - gapPx - h
        val below = anchorBounds.bottom + gapPx
        val aboveFits = above >= marginPx
        val belowFits = below + h + marginPx <= windowSize.height
        val y = when {
            menuBelow && aboveFits -> above
            menuBelow -> below
            belowFits -> below
            else -> above
        }
        return IntOffset(x, y.coerceIn(minOf(marginPx, maxY), maxY))
    }
}

/** Deletes [item] from the layout store (a folder takes its children) and releases a hosted widget id. */
internal fun removeHomeItem(context: Context, graph: AppGraph, item: HomeItem) {
    item.appWidgetId?.let { WidgetHostController.from(context, graph).deleteId(it) }
    graph.appScope.launch { runCatching { graph.layouts.deleteItem(item.id) } }
}
