package com.foldos.launcher.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.Dp
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem

/**
 * The slots through which the `items/` package (folders, app pairs, context menu) plugs into the
 * home screen. Provide it around `HomeScreen` with [LocalHomeItemHooks]; every slot is optional:
 *
 * - [folderIcon]: overrides the folder icon of a FOLDER item in a [Dp]-sized box; `children` are
 *   the folder's items from `LayoutRepository.children(folderId)`. Default: `items.FolderIcon`
 *   in `Folders.styleFor(item, settings)`.
 * - [onOpenFolder]: takes over a tap on a folder. Default: `items.FolderSheet` pops up at the
 *   folder tile (rename through `Folders.rename`, "Remove from folder" through
 *   `LayoutRepository.removeFromFolder` at the first free cell of the folder's page).
 * - [pairIcon]: overrides the icon of a PAIR item; `a` / `b` are the resolved apps (null when
 *   uninstalled). Default: `items.PairIcon`.
 * - [onLaunchPair]: takes over a tap on a pair. Default: `items.AppPairs.launch(context, item,
 *   appsByKey)` while the Pairs feature is on, else the one app that is left.
 * - [contextMenu]: replaces the long-press context menu for `item` (`app` resolved for APP items).
 *   It is composed inside the pressed tile, so it should be a `Popup` anchored to its parent (as
 *   `items.ItemContextMenu` is); `onDismiss` closes it and the notification preview above the
 *   tile. Default: `items.ItemContextMenu` with `ContextAction.defaultsFor(item, settings)`, Edit
 *   routed to `HomeCallbacks.onItemLongPress` and Remove of a hosted app widget releasing its id.
 * - [onItemLongPress]: takes over the whole long press (outside edit mode) when it returns true;
 *   `anchor` is the pressed tile's bounds in window px (null when unknown).
 */
@Immutable
data class HomeItemHooks(
    val folderIcon: (@Composable (item: HomeItem, children: List<HomeItem>, size: Dp, modifier: Modifier) -> Unit)? = null,
    val onOpenFolder: ((item: HomeItem) -> Unit)? = null,
    val pairIcon: (@Composable (item: HomeItem, a: AppInfo?, b: AppInfo?, size: Dp, modifier: Modifier) -> Unit)? = null,
    val onLaunchPair: ((item: HomeItem) -> Unit)? = null,
    val contextMenu: (@Composable (item: HomeItem, app: AppInfo?, onDismiss: () -> Unit) -> Unit)? = null,
    val onItemLongPress: ((item: HomeItem, anchor: Rect?) -> Boolean)? = null,
)

/** Provided around `HomeScreen` by the Integrate agent; defaults to the home's own fallbacks. */
val LocalHomeItemHooks: ProvidableCompositionLocal<HomeItemHooks> = compositionLocalOf { HomeItemHooks() }
