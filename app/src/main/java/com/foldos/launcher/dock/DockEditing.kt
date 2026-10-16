package com.foldos.launcher.dock

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldos.launcher.apppicker.appTileGestures
import com.foldos.launcher.apppicker.openAppInfo
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.ui.DragController
import com.foldos.launcher.core.ui.DragPayload
import com.foldos.launcher.core.ui.DragTargetIds
import com.foldos.launcher.core.ui.dragTarget

/** The `AppInfo.key` a dock can take from [payload] (an app, or an APP item), else null. */
internal fun dockPayloadKey(payload: DragPayload): String? = when (payload) {
    is DragPayload.App -> payload.app.key
    is DragPayload.Item -> {
        val item = payload.item
        val pkg = item.packageName
        val act = item.activityName
        if (item.kind == ItemKind.APP && pkg != null && act != null) "$pkg/$act" else null
    }
}

/**
 * Editing state of one dock: the drop target for apps dragged out of the app pop-up or the home
 * screen, the slot rectangles used to find the slot under the pointer, the reorder / remove-by-drag
 * of the dock's own icons, and the long-press menu. Created by [rememberDockEdit]; null in previews and
 * other windows that have no drag controller or no way to store the result.
 */
internal class DockEditState(val controller: DragController) {

    /** How many apps the dock can hold ([DockDefaults.pinnedCount]). */
    var capacity: Int = 0

    /** Keys of the apps the dock shows, in order. */
    var keys: List<String> = emptyList()

    /** Keys stored after the visible ones (they fill the folder add-on); kept when writing. */
    var tail: List<String> = emptyList()

    var onChange: (List<String>) -> Unit = {}

    var onItemTaken: ((HomeItem) -> Unit)? = null

    /** Slot index -> bounds in root coordinates, for the app icons and the empty-slot placeholders. */
    val slotRects: HashMap<Int, Rect> = HashMap()

    /** Key of the dock icon currently dragged (drawn faint), else null. */
    var draggingKey: String? by mutableStateOf(null)

    /** Key of the dock icon whose menu is open, else null. */
    var menuKey: String? by mutableStateOf(null)

    /** Set by the dock's own drop target; tells a dock-icon drag it was dropped on the dock. */
    var handled: Boolean = false

    /** Removing the last app would make the stored list empty (= defaults), so one app always stays. */
    val canRemove: Boolean get() = keys.size > 1

    /** True while an app (or an APP item) is being dragged, i.e. while the empty slots should show. */
    val dragActive: Boolean get() = controller.payload.value?.let { dockPayloadKey(it) != null } == true

    /** The slot under the pointer while the dock is the hovered target, else -1. */
    val hoverSlot: State<Int> = derivedStateOf {
        if (controller.hoverTargetId.value == DragTargetIds.DOCK) nearestSlot(controller.pointerInRoot.value) else -1
    }

    /** The slot whose centre is closest to [pointer]; 0 while no slot has been laid out. */
    fun nearestSlot(pointer: Offset): Int {
        var best = 0
        var bestDistance = Float.MAX_VALUE
        for ((slot, rect) in slotRects) {
            val dx = rect.center.x - pointer.x
            val dy = rect.center.y - pointer.y
            val distance = dx * dx + dy * dy
            if (distance < bestDistance) {
                bestDistance = distance
                best = slot
            }
        }
        return best
    }

    /**
     * Writes the dock after [key] was dropped with the pointer at [pointer]: an app already in the
     * dock is moved to the slot under the pointer; a new app is inserted before the app under the
     * pointer (or appended on an empty slot); when the dock is full it replaces the app under the pointer.
     */
    fun applyDrop(key: String, pointer: Offset) {
        val lastSlot = (capacity - 1).coerceAtLeast(0)
        val slot = nearestSlot(pointer).coerceIn(0, lastSlot)
        val next = keys.filterNot { it == key }.toMutableList()
        if (capacity > 0 && next.size >= capacity) {
            next[slot.coerceAtMost(next.size - 1)] = key
        } else {
            next.add(slot.coerceAtMost(next.size), key)
        }
        publish(next)
    }

    /** Removes [key] from the dock (no-op when it is the last app). */
    fun remove(key: String) {
        if (!canRemove) return
        publish(keys.filterNot { it == key })
    }

    /**
     * End of a drag that started on a dock icon. [dropped] is true only when a drop target took the payload,
     * so one accepted by something other than the dock (the home grid) takes the app out of the dock, while a
     * release over no target leaves it where it is.
     */
    fun finishIconDrag(key: String, dropped: Boolean) {
        draggingKey = null
        if (dropped && !handled) remove(key)
        handled = false
    }

    private fun publish(visible: List<String>) {
        onChange((visible + tail).distinct())
    }
}

/**
 * The [DockEditState] for a dock, or null when [controller] or [onChange] is missing (then the dock is
 * read-only: no drop target, no drag, no menu).
 */
@Composable
internal fun rememberDockEdit(
    controller: DragController?,
    onChange: ((List<String>) -> Unit)?,
    onItemTaken: ((HomeItem) -> Unit)?,
    capacity: Int,
    keys: List<String>,
    tail: List<String>,
): DockEditState? {
    if (controller == null || onChange == null) return null
    val state = remember(controller) { DockEditState(controller) }
    state.capacity = capacity
    state.keys = keys
    state.tail = tail
    state.onChange = onChange
    state.onItemTaken = onItemTaken
    return state
}

/** Registers the dock as the `dock` drop target of [DockEditState.controller]. */
@Composable
internal fun Modifier.dockDropTarget(edit: DockEditState?): Modifier {
    if (edit == null) return this
    return this.dragTarget(
        controller = edit.controller,
        id = DragTargetIds.DOCK,
        accepts = { payload -> dockPayloadKey(payload) != null },
        onDrop = { payload, pointer ->
            val key = dockPayloadKey(payload)
            if (key != null) {
                edit.handled = true
                edit.applyDrop(key, pointer)
                if (payload is DragPayload.Item) edit.onItemTaken?.invoke(payload.item)
            }
        },
    )
}

/**
 * Drag, long-press menu and slot bounds of one dock app icon. Put it inside the icon's `clickable`
 * (the tap stays with the click). No-op while [edit] is null.
 */
@Composable
internal fun Modifier.dockAppGestures(edit: DockEditState?, app: AppInfo, slot: Int): Modifier {
    if (edit == null) return this
    return this
        .slotBounds(edit, slot)
        .appTileGestures(
            controller = edit.controller,
            payload = { DragPayload.App(app) },
            onTap = null,
            onLongPress = { edit.menuKey = app.key },
            onDragStarted = {
                edit.handled = false
                edit.draggingKey = app.key
            },
            onDragFinished = { dropped -> edit.finishIconDrag(app.key, dropped) },
        )
}

/** Keeps [DockEditState.slotRects] up to date for [slot] while this node is composed. */
@Composable
internal fun Modifier.slotBounds(edit: DockEditState, slot: Int): Modifier {
    DisposableEffect(edit, slot) {
        onDispose { edit.slotRects.remove(slot) }
    }
    return this.onGloballyPositioned { edit.slotRects[slot] = it.boundsInRoot() }
}

/** Accent wash and outline behind the slot under the pointer while an app is dragged over the dock. */
internal fun Modifier.dockSlotHighlight(edit: DockEditState?, slot: Int, accent: Color, corner: Dp): Modifier {
    if (edit == null) return this
    return this.drawBehind {
        if (edit.hoverSlot.value == slot) {
            val radius = CornerRadius(corner.toPx())
            drawRoundRect(color = accent.copy(alpha = 0.22f), cornerRadius = radius)
            drawRoundRect(color = accent, cornerRadius = radius, style = Stroke(width = 2.dp.toPx()))
        }
    }
}

/** A faint dashed square showing a free dock slot while an app is dragged. */
@Composable
internal fun DockSlotPlaceholder(
    edit: DockEditState?,
    slot: Int,
    width: Dp,
    height: Dp,
    palette: DockPalette,
) {
    val dash = palette.onSurface.copy(alpha = 0.35f)
    val corner = (palette.iconCornerFraction.coerceIn(0f, 0.5f) * minOf(width.value, height.value)).dp
    val bounds = if (edit != null) Modifier.slotBounds(edit, slot) else Modifier
    Box(
        modifier = Modifier
            .size(width = width, height = height)
            .then(bounds)
            .dockSlotHighlight(edit, slot, palette.accent, corner)
            .drawBehind {
                drawRoundRect(
                    color = dash,
                    cornerRadius = CornerRadius(corner.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx())),
                    ),
                )
            },
    )
}

/** The menu of a dock icon (Remove from dock, App info); anchored to the enclosing [Box]. */
@Composable
internal fun DockAppMenu(edit: DockEditState?, app: AppInfo) {
    if (edit == null) return
    val context = LocalContext.current
    DropdownMenu(
        expanded = edit.menuKey == app.key,
        onDismissRequest = { edit.menuKey = null },
    ) {
        if (edit.canRemove) {
            DropdownMenuItem(
                text = { Text("Remove from dock") },
                onClick = {
                    edit.menuKey = null
                    edit.remove(app.key)
                },
            )
        }
        DropdownMenuItem(
            text = { Text("App info") },
            onClick = {
                edit.menuKey = null
                openAppInfo(context, app)
            },
        )
    }
}

/** Faint look of the dock icon that is being dragged out (its ghost follows the pointer). */
internal fun Modifier.dockDraggedFade(edit: DockEditState?, key: String): Modifier {
    if (edit == null) return this
    return this.graphicsLayer { alpha = if (edit.draggingKey == key) 0.3f else 1f }
}
