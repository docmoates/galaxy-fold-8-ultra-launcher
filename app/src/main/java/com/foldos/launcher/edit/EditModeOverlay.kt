package com.foldos.launcher.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowLeft
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowRight
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.a11y.a11yDuration
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.ui.DragController
import com.foldos.launcher.core.ui.DragPayload
import com.foldos.launcher.core.ui.DragTargetIds
import com.foldos.launcher.core.ui.dragTarget
import com.foldos.launcher.home.GridGeometry
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.roundToInt

/** How long a dragged item must hover past the page edge before the target page switches. */
internal const val EDGE_SWITCH_DELAY_MS: Long = 600L

/** How long the red "Tap again to delete" page confirmation waits for the second tap. */
internal const val DELETE_PAGE_CONFIRM_MS: Long = 3000L

/** How long the "removed" snackbar (and its Undo) stays up. */
internal const val UNDO_WINDOW_MS: Long = 5000L

/** The in-progress drag: moving an item or resizing a widget / card from one corner. Positions in overlay px. */
private sealed interface EditDrag {
    val item: HomeItem

    /** [grab] is the pointer offset inside the item when the drag started. */
    data class Move(override val item: HomeItem, val grab: Offset, val pointer: Offset) : EditDrag

    data class Resize(override val item: HomeItem, val corner: EditCorner, val start: Offset, val pointer: Offset) : EditDrag
}

/** The item removed last, kept in memory for [UNDO_WINDOW_MS] so Undo can put it back. */
private data class RemovedItem(val item: HomeItem, val message: String, val undoable: Boolean)

/**
 * Live edit mode (Round 2.0: the page is a free canvas, so this is just the chrome around it).
 *
 * The home underneath is drawn smaller by [EditScaledHome] (76%, centred between the two bars):
 * - ONE top bar ([EditTopBarHeight] below the status bar): the page strip (previous / "Page n/m" /
 *   next, Done) and the page tools (move page left / right, add page, delete page);
 * - ONE bottom bar ([EditBottomBarHeight] above the navigation bar): Add (the "Add to home" sheet),
 *   Wallpaper, Themes, Dock, Settings. While an item is being dragged the bar turns into the
 *   **Remove** strip: drop the item on it to remove it (Undo in the snackbar);
 * - on the page grid: a transparent gesture layer. Tap an item to select it (at most one is
 *   selected; tapping empty space or Back deselects), drag it to move (snaps to cells; hold past the
 *   left or right grid edge for 600 ms to switch the drop page; drop it on a card to put it inside),
 *   drag a corner handle of a selected WIDGET or CARD to resize. The selected item carries its
 *   remove X at its top centre. Remove is one tap and shows a snackbar with Undo for
 *   [UNDO_WINDOW_MS] (a folder or card asks for a second tap first, because its contents go with it).
 *
 * Nothing is persisted here: every change is reported through a callback and the caller writes it
 * to `LayoutRepository` / `SettingsRepository`.
 *
 * @param geometry the current page's grid in dp, measured in the UNSCALED home (window dp). The
 *   overlay maps it through the same [EditFrame] [EditScaledHome] draws the home with.
 * @param items the items of [currentPageId] (items of other pages are ignored).
 * @param onDelete called with the item id the moment Remove is tapped (an app widget's id is
 *   released by the caller, see `LauncherViewModel.deleteItem`).
 * @param onMovePage moves the page one position left (-1) or right (+1) on its screen.
 * @param onAdd opens the "Add to home" sheet.
 * @param dragController the launcher-wide controller; the Remove strip registers
 *   `DragTargetIds.REMOVE` on it so items dragged from the (scaled) home or from a card can be dropped there.
 * @param onSelectPage optional: called when the drop target page changes while dragging past an
 *   edge, and from the page arrows, so the caller can scroll its pager along.
 * @param onRestoreItem optional: called with the removed item when Undo is tapped; the caller must
 *   write it back unchanged (`LayoutRepository.upsertItem(item)`, same id) and keep an app widget's
 *   host id alive until the snackbar has expired. Null = no Undo button.
 * @param selectRequestId optional: select this item as soon as the overlay shows (the item a long
 *   press or the "Resize" / "Edit home" menu entry came from).
 * @param onChromeMeasured reports the measured bar heights ([EditChrome]), also available without
 *   the overlay through [rememberEditChrome].
 */
@Composable
fun EditModeOverlay(
    active: Boolean,
    screen: ScreenKind,
    settings: LauncherSettings,
    pages: List<HomePage>,
    currentPageId: Long,
    geometry: GridGeometry,
    items: List<HomeItem>,
    onMove: (id: Long, pageId: Long, col: Int, row: Int) -> Unit,
    onResize: (id: Long, spanX: Int, spanY: Int) -> Unit,
    onDelete: (id: Long) -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: (pageId: Long) -> Unit,
    onMovePage: (pageId: Long, delta: Int) -> Unit,
    onAdd: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenDock: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    dragController: DragController? = null,
    onSelectPage: (pageId: Long) -> Unit = {},
    onRestoreItem: ((HomeItem) -> Unit)? = null,
    selectRequestId: Long? = null,
    onChromeMeasured: (EditChrome) -> Unit = {},
) {
    if (!active) return
    BackHandler(onBack = onExit)

    val tokens = LocalFoldOsTokens.current
    val density = LocalDensity.current

    // The overlay fills the same window as the scaled home, so the same frame maps the grid.
    var overlaySize by remember { mutableStateOf(IntSize.Zero) }
    val chrome = rememberEditChrome()
    val frame = remember(overlaySize, chrome) {
        editFrameFor(overlaySize.width.toFloat(), overlaySize.height.toFloat(), chrome)
    }
    val grid = remember(geometry, density, frame) { EditGridPx.from(geometry, density.density, frame) }
    val insetPx = with(density) { 14.dp.toPx() }
    // The area between the bars, shrunk a little: selected-item controls are clamped into it.
    val clampBounds: Rect? = remember(overlaySize, chrome, insetPx) {
        val r = Rect(
            left = insetPx,
            top = chrome.topPx + insetPx,
            right = overlaySize.width - insetPx,
            bottom = overlaySize.height - chrome.bottomPx - insetPx,
        )
        if (r.right > r.left && r.bottom > r.top) r else null
    }

    var entered by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { entered = true }
    val enterAlpha by animateFloatAsState(
        targetValue = if (entered) 1f else 0f,
        animationSpec = tween(durationMillis = a11yDuration(220)),
        label = "editOverlayIn",
    )

    val sortedPages = remember(pages) { pages.sortedBy { it.index } }
    val currentPage = sortedPages.firstOrNull { it.id == currentPageId } ?: sortedPages.firstOrNull()
    val currentIndex = sortedPages.indexOfFirst { it.id == currentPage?.id }.coerceAtLeast(0)
    // One entry per slot: the front item of each stack stands for the whole stack.
    val pageItems = remember(items, currentPageId) {
        items.filter { it.pageId == currentPageId && it.parentId == null }
            .sortedBy { it.stackIndex }
            .distinctBy { it.stackId ?: it.id }
    }

    var selectedId by remember { mutableStateOf<Long?>(null) }
    var drag by remember { mutableStateOf<EditDrag?>(null) }
    var edgeSide by remember { mutableIntStateOf(0) }
    var targetPageIndex by remember { mutableIntStateOf(currentIndex) }
    var confirmDeletePage by remember { mutableStateOf(false) }
    var confirmContainerId by remember { mutableStateOf<Long?>(null) }
    var removed by remember { mutableStateOf<RemovedItem?>(null) }
    var topBar by remember { mutableIntStateOf(0) }
    var bottomBar by remember { mutableIntStateOf(0) }

    LaunchedEffect(selectRequestId) {
        if (selectRequestId != null) selectedId = selectRequestId
    }
    LaunchedEffect(currentPage?.id, sortedPages) {
        targetPageIndex = currentIndex
    }
    // The delete confirmation survives page-list emissions and only resets when the shown page changes.
    LaunchedEffect(currentPage?.id) { confirmDeletePage = false }
    LaunchedEffect(selectedId) { confirmContainerId = null }
    LaunchedEffect(confirmDeletePage) {
        if (confirmDeletePage) {
            delay(DELETE_PAGE_CONFIRM_MS)
            confirmDeletePage = false
        }
    }
    LaunchedEffect(removed) {
        if (removed != null) {
            delay(UNDO_WINDOW_MS)
            removed = null
        }
    }
    LaunchedEffect(edgeSide) {
        if (edgeSide == 0) return@LaunchedEffect
        delay(EDGE_SWITCH_DELAY_MS)
        val next = targetPageIndex + edgeSide
        if (next in sortedPages.indices) {
            targetPageIndex = next
            onSelectPage(sortedPages[next].id)
        }
        // Reset so staying at the edge re-arms the timer and pages on every 600 ms.
        edgeSide = 0
    }
    // Back deselects first (composed after the exit handler, so it has priority while selected).
    BackHandler(enabled = selectedId != null) { selectedId = null }

    val selected = pageItems.firstOrNull { it.id == selectedId }
    val targetPage = sortedPages.getOrNull(targetPageIndex) ?: currentPage

    // The gesture coroutine reads the latest values through these so it never restarts mid-drag.
    val itemsState = rememberUpdatedState(pageItems)
    val gridState = rememberUpdatedState(grid)
    val clampState = rememberUpdatedState(clampBounds)
    val selectedIdState = rememberUpdatedState(selectedId)
    val targetPageState = rememberUpdatedState(targetPage)
    val onMoveState = rememberUpdatedState(onMove)
    val onResizeState = rememberUpdatedState(onResize)

    val handleHitRadiusPx = with(density) { 22.dp.toPx() }
    val edgeSlackPx = with(density) { 10.dp.toPx() }

    /** Remove: one tap deletes (a folder / card first asks to be tapped again) and offers Undo. */
    fun removeItem(item: HomeItem, confirmed: Boolean) {
        val container = item.kind == ItemKind.FOLDER || item.kind == ItemKind.CARD
        if (container && !confirmed && confirmContainerId != item.id) {
            confirmContainerId = item.id
            return
        }
        onDelete(item.id)
        removed = RemovedItem(
            item = item,
            message = removedMessage(item),
            // A folder's / card's contents are deleted with it and are put back by the caller's restore.
            undoable = onRestoreItem != null,
        )
        selectedId = null
        confirmContainerId = null
    }

    /** True when the overlay point [p] lies on the bottom bar, which is the Remove strip while dragging. */
    fun onRemoveStrip(p: Offset): Boolean =
        overlaySize.height > 0 && p.y >= overlaySize.height - chrome.bottomPx

    fun commit(d: EditDrag?) {
        val g = gridState.value
        when (d) {
            null -> Unit
            is EditDrag.Move -> {
                if (onRemoveStrip(d.pointer)) {
                    removeItem(d.item, confirmed = true)
                    return
                }
                val target = targetPageState.value ?: return
                val cell = g.snapMove(d.item, d.pointer - d.grab)
                val changed = target.id != d.item.pageId || cell.col != d.item.col || cell.row != d.item.row || d.item.free
                if (changed) onMoveState.value(d.item.id, target.id, cell.col, cell.row)
                if (target.id != d.item.pageId) selectedId = null
            }
            is EditDrag.Resize -> {
                val cell = g.resized(d.item, d.corner, d.pointer - d.start)
                if (cell.col != d.item.col || cell.row != d.item.row) {
                    onMoveState.value(d.item.id, d.item.pageId, cell.col, cell.row)
                }
                if (cell.spanX != d.item.spanX || cell.spanY != d.item.spanY) {
                    onResizeState.value(d.item.id, cell.spanX, cell.spanY)
                }
            }
        }
    }

    // The bottom bar is the Remove strip while an item is on the move: our own drag or a controller drag.
    val controllerPayload = dragController?.payload?.value
    val controllerDraggingItem = controllerPayload is DragPayload.Item
    val removeStripShown = drag is EditDrag.Move || controllerDraggingItem
    val removeHot = (drag as? EditDrag.Move)?.let { onRemoveStrip(it.pointer) } == true ||
        dragController?.hoverTargetId?.value == DragTargetIds.REMOVE

    Box(
        modifier = modifier
            .fillMaxSize()
            .onSizeChanged { overlaySize = it }
            .graphicsLayer { alpha = enterAlpha },
    ) {
        /* ---- grid guide, selection outline, drag ghost (no pointer input, so it never blocks) ---- */
        val dashed = remember { PathEffect.dashPathEffect(floatArrayOf(14f, 10f), 0f) }
        Canvas(Modifier.fillMaxSize()) {
            val cellCorner = CornerRadius(8.dp.toPx())
            val guide = tokens.onSurface.copy(alpha = 0.10f)
            for (r in 0 until grid.rows) {
                for (c in 0 until grid.cols) {
                    val cell = grid.cellRect(c, r, 1, 1).deflate(3.dp.toPx())
                    drawRoundRect(guide, cell.topLeft, cell.size, cellCorner, style = Stroke(1.dp.toPx()))
                }
            }
            val d = drag
            when (d) {
                is EditDrag.Move -> {
                    if (!onRemoveStrip(d.pointer)) {
                        val cell = grid.snapMove(d.item, d.pointer - d.grab)
                        val target = grid.cellRect(cell)
                        drawRoundRect(tokens.accent.copy(alpha = 0.18f), target.topLeft, target.size, cellCorner)
                        drawRoundRect(tokens.accent, target.topLeft, target.size, cellCorner, style = Stroke(2.dp.toPx(), pathEffect = dashed))
                    }
                    val ghost = grid.rectFor(d.item)
                    val topLeft = d.pointer - d.grab
                    drawRoundRect(tokens.accent.copy(alpha = 0.30f), topLeft, Size(ghost.width, ghost.height), cellCorner)
                    drawRoundRect(tokens.onSurface.copy(alpha = 0.7f), topLeft, Size(ghost.width, ghost.height), cellCorner, style = Stroke(1.5.dp.toPx()))
                }
                is EditDrag.Resize -> {
                    val target = grid.cellRect(grid.resized(d.item, d.corner, d.pointer - d.start))
                    drawRoundRect(tokens.accent.copy(alpha = 0.18f), target.topLeft, target.size, cellCorner)
                    drawRoundRect(tokens.accent, target.topLeft, target.size, cellCorner, style = Stroke(2.dp.toPx()))
                }
                null -> if (selected != null) {
                    val rect = grid.rectFor(selected)
                    drawRoundRect(tokens.accent, rect.topLeft, rect.size, cellCorner, style = Stroke(2.dp.toPx()))
                }
            }
        }

        /* ---- gesture layer: the grid's bounds plus the handle reach, so corner handles can be grabbed ---- */
        val bounds = grid.bounds
        val layerLeft = bounds.left - handleHitRadiusPx
        val layerTop = bounds.top - handleHitRadiusPx
        val layerOriginState = rememberUpdatedState(Offset(layerLeft, layerTop))
        Box(
            Modifier
                .offset { IntOffset(layerLeft.roundToInt(), layerTop.roundToInt()) }
                .size(
                    with(density) { (bounds.width + 2f * handleHitRadiusPx).toDp() },
                    with(density) { (bounds.height + 2f * handleHitRadiusPx).toDp() },
                )
                .testTag(EditTags.GRID_LAYER)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { pos ->
                            val p = pos + layerOriginState.value
                            val g = gridState.value
                            val hit = itemsState.value.lastOrNull { g.rectFor(it).contains(p) }
                            selectedId = hit?.id
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val p = pos + layerOriginState.value
                            val g = gridState.value
                            val current = itemsState.value
                            val sel = current.firstOrNull { it.id == selectedIdState.value }
                            val corner = if (sel != null && sel.resizable) {
                                cornerAt(g.rectFor(sel), p, handleHitRadiusPx, clampState.value)
                            } else {
                                null
                            }
                            drag = if (sel != null && corner != null) {
                                EditDrag.Resize(sel, corner, p, p)
                            } else {
                                val hit = current.lastOrNull { g.rectFor(it).contains(p) }
                                if (hit != null) {
                                    selectedId = hit.id
                                    EditDrag.Move(hit, p - g.rectFor(hit).topLeft, p)
                                } else {
                                    null
                                }
                            }
                        },
                        onDrag = { change, _ ->
                            val d = drag
                            if (d != null) {
                                change.consume()
                                val p = change.position + layerOriginState.value
                                drag = when (d) {
                                    is EditDrag.Move -> d.copy(pointer = p)
                                    is EditDrag.Resize -> d.copy(pointer = p)
                                }
                                edgeSide = if (d is EditDrag.Move && !onRemoveStrip(p)) gridState.value.edgeAt(p.x, edgeSlackPx) else 0
                            }
                        },
                        onDragEnd = {
                            commit(drag)
                            drag = null
                            edgeSide = 0
                        },
                        onDragCancel = {
                            drag = null
                            edgeSide = 0
                        },
                    )
                },
        )

        /* ---- controls ON the selected item (hidden while anything is being dragged) ---- */
        if (selected != null && drag == null) {
            val rect = grid.rectFor(selected)
            if (selected.resizable) {
                EditCorner.entries.forEach { corner ->
                    EditResizeHandle(
                        center = rect.cornerOffset(corner, clampBounds),
                        tag = EditTags.resizeHandle(corner.name),
                    )
                }
            }
            val confirming = confirmContainerId == selected.id
            val halfWidthPx = with(density) { (if (confirming) ConfirmPillWidth else RemoveHitSize).toPx() / 2f }
            val halfHeightPx = with(density) { (if (confirming) ConfirmPillHeight else RemoveHitSize).toPx() / 2f }
            val chipBounds = clampBounds?.let { b ->
                val r = Rect(
                    left = b.left + max(0f, halfWidthPx - insetPx),
                    top = b.top + max(0f, halfHeightPx - insetPx),
                    right = b.right - max(0f, halfWidthPx - insetPx),
                    bottom = b.bottom - max(0f, halfHeightPx - insetPx),
                )
                if (r.right > r.left && r.bottom > r.top) r else null
            }
            // Inside the item's top edge, centred; never under a bar.
            val chipCenter = clampInto(Offset(rect.center.x, rect.top + halfHeightPx + insetPx * 0.4f), chipBounds)
            val chipModifier = Modifier.offset {
                IntOffset((chipCenter.x - halfWidthPx).roundToInt(), (chipCenter.y - halfHeightPx).roundToInt())
            }
            if (confirming) {
                EditConfirmPill(
                    label = if (selected.kind == ItemKind.CARD) "Remove card?" else "Remove folder?",
                    onClick = { removeItem(selected, confirmed = true) },
                    modifier = chipModifier,
                )
            } else {
                EditRemoveButton(
                    contentDescription = when (selected.kind) {
                        ItemKind.WIDGET -> "Remove widget"
                        ItemKind.CARD -> "Remove card"
                        else -> "Remove"
                    },
                    onClick = { removeItem(selected, confirmed = false) },
                    modifier = chipModifier,
                )
            }
        }

        /* ---- the ONE top bar: page strip + page tools (mock-up `.etop`) ---- */
        Column(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .onSizeChanged { topBar = it.height }
                .windowInsetsPadding(WindowInsets.systemBars)
                .height(EditTopBarHeight)
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .testTag(EditTags.TOP_BAR),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val dropTarget = targetPage
            val pageLabel = when {
                confirmDeletePage -> "Tap again to delete"
                drag is EditDrag.Move && dropTarget != null && dropTarget.id != currentPage?.id ->
                    "Drop on page ${targetPageIndex + 1}"
                drag is EditDrag.Move && edgeSide != 0 -> "Hold to switch"
                else -> "Page ${currentIndex + 1}/${sortedPages.size.coerceAtLeast(1)}"
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp)
                    .testTag(EditTags.PAGE_STRIP),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditRoundButton(
                    icon = Icons.Rounded.ChevronLeft,
                    contentDescription = "Previous page",
                    enabled = currentIndex > 0,
                    onClick = { sortedPages.getOrNull(currentIndex - 1)?.let { onSelectPage(it.id) } },
                    modifier = Modifier.testTag(EditTags.PAGE_PREV),
                )
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    // While confirming, the chip itself is the red confirm target (the trash button below does
                    // the same), so a tap on what reads "Tap again to delete" really deletes the page.
                    val deleteTarget = currentPage?.id
                    val canConfirmDelete = confirmDeletePage && deleteTarget != null && sortedPages.size > 1
                    val confirmDelete: (() -> Unit)? = if (canConfirmDelete) {
                        {
                            confirmDeletePage = false
                            selectedId = null
                            if (deleteTarget != null) onDeletePage(deleteTarget)
                        }
                    } else {
                        null
                    }
                    EditChip(
                        label = pageLabel,
                        onClick = confirmDelete,
                        accent = !confirmDeletePage && drag is EditDrag.Move,
                        danger = confirmDeletePage,
                        modifier = Modifier.testTag(EditTags.PAGE_LABEL),
                    )
                }
                EditRoundButton(
                    icon = Icons.Rounded.ChevronRight,
                    contentDescription = "Next page",
                    enabled = currentIndex < sortedPages.lastIndex,
                    onClick = { sortedPages.getOrNull(currentIndex + 1)?.let { onSelectPage(it.id) } },
                    modifier = Modifier.testTag(EditTags.PAGE_NEXT),
                )
                EditChip(
                    label = "Done",
                    icon = Icons.Rounded.Done,
                    accent = true,
                    onClick = onExit,
                    modifier = Modifier.testTag(EditTags.DONE),
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(40.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditRoundButton(
                    icon = Icons.Rounded.KeyboardDoubleArrowLeft,
                    contentDescription = "Move page left",
                    enabled = currentIndex > 0 && currentPage != null,
                    onClick = { currentPage?.let { onMovePage(it.id, -1) } },
                    modifier = Modifier.testTag(EditTags.PAGE_LEFT),
                )
                EditRoundButton(
                    icon = Icons.Rounded.KeyboardDoubleArrowRight,
                    contentDescription = "Move page right",
                    enabled = currentIndex < sortedPages.lastIndex && currentPage != null,
                    onClick = { currentPage?.let { onMovePage(it.id, 1) } },
                    modifier = Modifier.testTag(EditTags.PAGE_RIGHT),
                )
                EditChip(
                    label = "Add page",
                    icon = Icons.Rounded.Add,
                    onClick = onAddPage,
                    modifier = Modifier.testTag(EditTags.ADD_PAGE),
                )
                // A screen always keeps at least one page, so the last page has no delete button.
                if (sortedPages.size > 1 && currentPage != null) {
                    EditRoundButton(
                        icon = Icons.Rounded.Delete,
                        contentDescription = if (confirmDeletePage) "Tap again to delete this page" else "Delete page",
                        danger = confirmDeletePage,
                        onClick = {
                            if (confirmDeletePage) {
                                confirmDeletePage = false
                                selectedId = null
                                onDeletePage(currentPage.id)
                            } else {
                                confirmDeletePage = true
                            }
                        },
                        modifier = Modifier.testTag(EditTags.DELETE_PAGE),
                    )
                }
            }
        }

        /* ---- the ONE bottom bar: actions, or the Remove strip while something is dragged (mock-up `.ebar`) ---- */
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .onSizeChanged { bottomBar = it.height }
                .windowInsetsPadding(WindowInsets.systemBars)
                .height(EditBottomBarHeight)
                .testTag(EditTags.BOTTOM_BAR),
            contentAlignment = Alignment.Center,
        ) {
            if (removeStripShown) {
                EditRemoveStrip(
                    hot = removeHot,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 14.dp)
                        .then(
                            if (dragController != null) {
                                Modifier.dragTarget(
                                    controller = dragController,
                                    id = DragTargetIds.REMOVE,
                                    accepts = { payload -> payload is DragPayload.Item && payload.item.id > 0L },
                                    onDrop = { payload, _ ->
                                        if (payload is DragPayload.Item) removeItem(payload.item, confirmed = true)
                                    },
                                )
                            } else {
                                Modifier
                            },
                        ),
                )
            } else {
                EditActionBar(
                    actions = listOf(
                        EditAction("Add", Icons.Rounded.AddCircle, onAdd, EditTags.ACTION_ADD),
                        EditAction("Wallpaper", Icons.Rounded.Wallpaper, onOpenWallpaper, EditTags.ACTION_WALLPAPER),
                        EditAction("Themes", Icons.Rounded.Palette, onOpenThemes, EditTags.ACTION_THEMES),
                        EditAction("Dock", Icons.Rounded.Dock, onOpenDock, EditTags.ACTION_DOCK),
                        EditAction("Settings", Icons.Rounded.Settings, onOpenSettings, EditTags.ACTION_SETTINGS),
                    ),
                )
            }
        }

        /* ---- remove confirmation with Undo, just above the bottom bar ---- */
        val pending = removed
        if (pending != null) {
            EditSnackbar(
                message = pending.message,
                actionLabel = if (pending.undoable) "Undo" else null,
                onAction = {
                    onRestoreItem?.invoke(pending.item)
                    selectedId = pending.item.id
                    removed = null
                },
                // Anchored to the TOP of the bottom action bar (the computed chrome: bar height plus the
                // navigation inset) and lifted by EditSnackbarGap.
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp)
                    .offset {
                        val barPx = chrome.bottomPx
                        IntOffset(0, -(barPx + EditSnackbarGap.roundToPx()))
                    },
            )
        }
    }

    // Report the measured bars (fixed heights plus insets, so this normally equals rememberEditChrome()).
    LaunchedEffect(topBar, bottomBar) {
        if (topBar > 0 && bottomBar > 0) onChromeMeasured(EditChrome(topBar, bottomBar))
    }
}

/** Widgets and cards get corner resize handles; everything else is one cell. */
private val HomeItem.resizable: Boolean get() = kind == ItemKind.WIDGET || kind == ItemKind.CARD

private fun removedMessage(item: HomeItem): String = when (item.kind) {
    ItemKind.WIDGET -> "Widget removed"
    ItemKind.APP -> "App removed from home"
    ItemKind.FOLDER -> "Folder removed"
    ItemKind.PAIR -> "App pair removed"
    ItemKind.CARD -> "Card removed"
}

/** Space between the top of the bottom action bar and the bottom of the "removed / Undo" snackbar. */
private val EditSnackbarGap = 12.dp
private val RemoveHitSize = 44.dp
private val ConfirmPillWidth = 150.dp
private val ConfirmPillHeight = 40.dp

/** The Remove strip that replaces the bottom actions while an item is dragged (red when the item hovers over it). */
@Composable
private fun EditRemoveStrip(hot: Boolean, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val pill = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .clip(pill)
            .background(if (hot) EditDangerColor else tokens.drawerScrim, pill)
            .border(1.dp, if (hot) EditDangerColor else tokens.surfaceBorder, pill)
            .testTag(EditTags.REMOVE_STRIP),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Delete,
            contentDescription = null,
            tint = if (hot) androidx.compose.ui.graphics.Color.White else tokens.onSurface,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = if (hot) "Release to remove" else "Drag here to remove",
            color = if (hot) androidx.compose.ui.graphics.Color.White else tokens.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

/** The remove X: a 28dp frosted circle inside a 44dp touch target, drawn on the selected item. */
@Composable
private fun EditRemoveButton(contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Box(
        modifier = modifier
            .size(RemoveHitSize)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .testTag(EditTags.REMOVE_ITEM),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(tokens.drawerScrim, CircleShape)
                .border(1.dp, tokens.surfaceBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = contentDescription,
                tint = tokens.onSurface,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

/** Second tap for removing a folder or card (its contents go with it). Fixed size so the caller can centre it. */
@Composable
private fun EditConfirmPill(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val pill = RoundedCornerShape(999.dp)
    Box(
        modifier = modifier
            .width(ConfirmPillWidth)
            .height(ConfirmPillHeight)
            .clip(pill)
            .background(tokens.accent, pill)
            .clickable(onClick = onClick)
            .testTag(EditTags.CONFIRM_REMOVE_FOLDER),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = tokens.onAccent,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

/** A corner resize handle; the gesture layer underneath does the actual hit testing and drag. */
@Composable
private fun EditResizeHandle(center: Offset, tag: String) {
    val tokens = LocalFoldOsTokens.current
    val size = 20.dp
    val half = with(LocalDensity.current) { (size / 2).toPx() }
    Box(
        Modifier
            .offset { IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()) }
            .size(size)
            .clip(CircleShape)
            .background(tokens.accent, CircleShape)
            .border(2.dp, tokens.onAccent, CircleShape)
            .testTag(tag),
    )
}
