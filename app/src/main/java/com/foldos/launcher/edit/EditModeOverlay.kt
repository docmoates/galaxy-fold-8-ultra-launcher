package com.foldos.launcher.edit

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.home.GridGeometry
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** How long a dragged item must hover past the page edge before the target page switches. */
internal const val EDGE_SWITCH_DELAY_MS: Long = 600L

/** The in-progress drag: moving an item or resizing a widget from one corner. Positions in overlay px. */
private sealed interface EditDrag {
    val item: HomeItem

    /** [grab] is the pointer offset inside the item when the drag started. */
    data class Move(override val item: HomeItem, val grab: Offset, val pointer: Offset) : EditDrag

    data class Resize(override val item: HomeItem, val corner: EditCorner, val start: Offset, val pointer: Offset) : EditDrag
}

/**
 * Live edit mode (PLAN.md 5.2, mock-up `editUI()`), drawn on top of the home content that the
 * caller has already scaled to 76%. Nothing is persisted here: every change is reported through a
 * callback and the caller writes it to `LayoutRepository` / `SettingsRepository`.
 *
 * Layout:
 * - top: the style segmented control (Grid / Dashboard / List / Panels) for the current page and a
 *   Done button; under it the page chips (previous / "Page n of m" / next, add page, delete page);
 * - middle: a transparent gesture layer the exact size of the grid. Tap an item to select it, drag
 *   it to move (snaps to cells; hold it past the left or right grid edge for 600 ms to switch the
 *   drop target to the neighbouring page), drag a corner handle of a selected WIDGET to resize, tap
 *   the X chip to delete, tap empty space to deselect. Touches outside the grid and the chrome fall
 *   through to the home underneath, so the pager still swipes;
 * - bottom: the action bar (Wallpaper, Themes, Widgets, Dock, Settings).
 *
 * @param geometry the current page's grid in dp, measured in the coordinate space of this
 *   overlay's own bounds (place the overlay in the same `Box` as the scaled `HomeScreen`, both
 *   `fillMaxSize`, and report the scaled grid's position relative to that box).
 * @param items the items of [currentPageId] (items of other pages are ignored).
 * @param onSelectPage optional: called when the drop target page changes while dragging past an
 *   edge, and from the page arrows, so the caller can scroll its pager along.
 * @param onOpenProfiles optional: adds a "Profiles" entry to the bottom bar (the profile switcher).
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
    onSetPageStyle: (pageId: Long, style: HomeStyle) -> Unit,
    onAddPage: () -> Unit,
    onDeletePage: (pageId: Long) -> Unit,
    onAddWidget: () -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenDock: () -> Unit,
    onOpenSettings: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    onSelectPage: (pageId: Long) -> Unit = {},
    onOpenProfiles: (() -> Unit)? = null,
) {
    if (!active) return
    BackHandler(onBack = onExit)

    val tokens = LocalFoldOsTokens.current
    val density = LocalDensity.current
    val grid = remember(geometry, density) { EditGridPx.from(geometry, density.density) }
    val sortedPages = remember(pages) { pages.sortedBy { it.index } }
    val currentPage = sortedPages.firstOrNull { it.id == currentPageId } ?: sortedPages.firstOrNull()
    val currentIndex = sortedPages.indexOfFirst { it.id == currentPage?.id }.coerceAtLeast(0)
    val style = currentPage?.style ?: settings.styleFor(screen)
    // One entry per slot: the front item of each stack stands for the whole stack.
    val pageItems = remember(items, currentPageId) {
        items.filter { it.pageId == currentPageId }
            .sortedBy { it.stackIndex }
            .distinctBy { it.stackId ?: it.id }
    }

    var selectedId by remember { mutableStateOf<Long?>(null) }
    var drag by remember { mutableStateOf<EditDrag?>(null) }
    var edgeSide by remember { mutableIntStateOf(0) }
    var targetPageIndex by remember { mutableIntStateOf(currentIndex) }
    var confirmDeletePage by remember { mutableStateOf(false) }

    LaunchedEffect(currentPage?.id, sortedPages) {
        targetPageIndex = currentIndex
        confirmDeletePage = false
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

    val selected = pageItems.firstOrNull { it.id == selectedId }
    val targetPage = sortedPages.getOrNull(targetPageIndex) ?: currentPage

    // The gesture coroutine reads the latest values through these so it never restarts mid-drag.
    val itemsState = rememberUpdatedState(pageItems)
    val gridState = rememberUpdatedState(grid)
    val selectedIdState = rememberUpdatedState(selectedId)
    val targetPageState = rememberUpdatedState(targetPage)
    val onMoveState = rememberUpdatedState(onMove)
    val onResizeState = rememberUpdatedState(onResize)

    val handleHitRadiusPx = with(density) { 22.dp.toPx() }
    val edgeSlackPx = with(density) { 10.dp.toPx() }

    fun commit(d: EditDrag?) {
        val g = gridState.value
        when (d) {
            null -> Unit
            is EditDrag.Move -> {
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

    Box(modifier = modifier.fillMaxSize()) {
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
                    val cell = grid.snapMove(d.item, d.pointer - d.grab)
                    val target = grid.cellRect(cell)
                    drawRoundRect(tokens.accent.copy(alpha = 0.18f), target.topLeft, target.size, cellCorner)
                    drawRoundRect(tokens.accent, target.topLeft, target.size, cellCorner, style = Stroke(2.dp.toPx(), pathEffect = dashed))
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

        /* ---- gesture layer: exactly the grid's bounds ---- */
        val bounds = grid.bounds
        val originOffset = Offset(bounds.left, bounds.top)
        Box(
            Modifier
                .offset { IntOffset(bounds.left.roundToInt(), bounds.top.roundToInt()) }
                .size(with(density) { bounds.width.toDp() }, with(density) { bounds.height.toDp() })
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { pos ->
                            val p = pos + originOffset
                            val g = gridState.value
                            val hit = itemsState.value.lastOrNull { g.rectFor(it).contains(p) }
                            selectedId = hit?.id
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { pos ->
                            val p = pos + originOffset
                            val g = gridState.value
                            val current = itemsState.value
                            val sel = current.firstOrNull { it.id == selectedIdState.value }
                            val corner = if (sel != null && sel.kind == ItemKind.WIDGET) {
                                cornerAt(g.rectFor(sel), p, handleHitRadiusPx)
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
                                val p = change.position + originOffset
                                drag = when (d) {
                                    is EditDrag.Move -> d.copy(pointer = p)
                                    is EditDrag.Resize -> d.copy(pointer = p)
                                }
                                edgeSide = if (d is EditDrag.Move) gridState.value.edgeAt(p.x, edgeSlackPx) else 0
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

        /* ---- handles on the selected item (hidden while it is being moved) ---- */
        if (selected != null && drag !is EditDrag.Move) {
            val rect = grid.rectFor(selected)
            EditDeleteChip(
                center = Offset(rect.center.x, rect.top),
                onClick = {
                    onDelete(selected.id)
                    selectedId = null
                },
            )
            if (selected.kind == ItemKind.WIDGET) {
                EditCorner.entries.forEach { corner -> EditResizeHandle(rect.cornerOffset(corner)) }
            }
        }

        /* ---- top chrome: style segments + Done, then page chips (mock-up `.etop` top 46px) ---- */
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(top = 46.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                EditStyleSegments(
                    style = style,
                    onSelect = { next ->
                        val page = currentPage
                        if (page != null && next != page.style) onSetPageStyle(page.id, next)
                    },
                    modifier = Modifier.align(Alignment.Center),
                )
                EditRoundButton(
                    icon = Icons.Rounded.Done,
                    contentDescription = "Done",
                    onClick = onExit,
                    modifier = Modifier.align(Alignment.CenterEnd),
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EditRoundButton(
                    icon = Icons.Rounded.ChevronLeft,
                    contentDescription = "Previous page",
                    enabled = currentIndex > 0,
                    onClick = { sortedPages.getOrNull(currentIndex - 1)?.let { onSelectPage(it.id) } },
                )
                EditChip(label = "Page ${currentIndex + 1} of ${sortedPages.size.coerceAtLeast(1)}", onClick = null)
                EditRoundButton(
                    icon = Icons.Rounded.ChevronRight,
                    contentDescription = "Next page",
                    enabled = currentIndex < sortedPages.lastIndex,
                    onClick = { sortedPages.getOrNull(currentIndex + 1)?.let { onSelectPage(it.id) } },
                )
                Spacer(Modifier.size(4.dp))
                EditChip(label = "Add page", icon = Icons.Rounded.Add, onClick = onAddPage)
                if (sortedPages.size > 1 && currentPage != null) {
                    EditChip(
                        label = if (confirmDeletePage) "Tap again to delete" else "Delete page",
                        icon = Icons.Rounded.Delete,
                        accent = confirmDeletePage,
                        onClick = {
                            if (confirmDeletePage) {
                                confirmDeletePage = false
                                selectedId = null
                                onDeletePage(currentPage.id)
                            } else {
                                confirmDeletePage = true
                            }
                        },
                    )
                }
            }
            val dropTarget = targetPage
            if (drag is EditDrag.Move && dropTarget != null && dropTarget.id != currentPage?.id) {
                EditChip(label = "Drop on page ${targetPageIndex + 1}", onClick = null, accent = true)
            } else if (drag is EditDrag.Move && edgeSide != 0) {
                EditChip(label = "Hold to switch page", onClick = null)
            }
        }

        /* ---- bottom action bar (mock-up `.ebar` bottom 26px) ---- */
        EditActionBar(
            actions = buildList {
                add(EditAction("Wallpaper", Icons.Rounded.Wallpaper, onOpenWallpaper))
                add(EditAction("Themes", Icons.Rounded.Palette, onOpenThemes))
                add(EditAction("Widgets", Icons.Rounded.Widgets, onAddWidget))
                add(EditAction("Dock", Icons.Rounded.Dock, onOpenDock))
                // Profiles (Phase 4+5): the switcher sheet, only while the host offers it.
                if (onOpenProfiles != null) add(EditAction("Profiles", Icons.Rounded.Person, onOpenProfiles))
                add(EditAction("Settings", Icons.Rounded.Settings, onOpenSettings))
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(bottom = 26.dp),
        )
    }
}

/** The X chip centred on the top edge of the selected item. */
@Composable
private fun EditDeleteChip(center: Offset, onClick: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val size = 28.dp
    val half = with(LocalDensity.current) { (size / 2).toPx() }
    Box(
        Modifier
            .offset { IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()) }
            .size(size)
            .clip(CircleShape)
            .background(tokens.drawerScrim, CircleShape)
            .border(1.dp, tokens.surfaceBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Close,
            contentDescription = "Remove",
            tint = tokens.onSurface,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** A corner resize handle; the gesture layer underneath does the actual hit testing and drag. */
@Composable
private fun EditResizeHandle(center: Offset) {
    val tokens = LocalFoldOsTokens.current
    val size = 20.dp
    val half = with(LocalDensity.current) { (size / 2).toPx() }
    Box(
        Modifier
            .offset { IntOffset((center.x - half).roundToInt(), (center.y - half).roundToInt()) }
            .size(size)
            .clip(CircleShape)
            .background(tokens.accent, CircleShape)
            .border(2.dp, tokens.onAccent, CircleShape),
    )
}
