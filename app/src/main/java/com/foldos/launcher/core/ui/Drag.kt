package com.foldos.launcher.core.ui

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import kotlin.math.roundToInt

/**
 * What is being dragged: an app from the app pop-up / drawer ([App]) or an item that already lives
 * on a page or in the dock ([Item]).
 */
sealed interface DragPayload {
    data class App(val app: AppInfo) : DragPayload
    data class Item(val item: HomeItem) : DragPayload
}

/** Ids of the drop targets the launcher registers (see `app/CONTRACTS.md`, "Drag and drop"). */
object DragTargetIds {
    /** The dock (bottom or rail). Its `onDrop` inserts / replaces a dock app. */
    const val DOCK: String = "dock"

    /** The item grid of the page the pager has settled on. Its `onDrop` maps the pointer to a cell. */
    const val HOME_GRID: String = "home-grid"

    /** A "Remove" strip shown while an [DragPayload.Item] is dragged in edit mode. */
    const val REMOVE: String = "remove"
}

/**
 * Holds the single drag in progress and the drop targets that may receive it. One instance is created
 * at the launcher root ([rememberDragController]) and provided through [LocalDragController];
 * sources call [start] / [move] / [drop] / [cancel] (usually through [dragSource]), targets register
 * with [registerTarget] (usually through [dragTarget]).
 *
 * All positions are in the root coordinate space of the Compose view (`LayoutCoordinates.localToRoot`
 * / `boundsInRoot()`). The launcher window is edge to edge and the root composable fills it, so root
 * coordinates equal window coordinates.
 */
@Stable
class DragController {

    private val payloadState = mutableStateOf<DragPayload?>(null)
    private val pointerState = mutableStateOf(Offset.Zero)
    private val hoverState = mutableStateOf<String?>(null)

    private class Target(
        val id: String,
        var bounds: () -> Rect?,
        var accepts: (DragPayload) -> Boolean,
        var onDrop: (DragPayload, Offset) -> Unit,
    )

    // Registration order; a re-registered id keeps its place. The last entry is hit-tested first.
    private val targets = ArrayList<Target>()

    /** The payload being dragged, or null when no drag is active. Observable. */
    val payload: State<DragPayload?> get() = payloadState

    /** The pointer position in root coordinates (valid while [payload] is non-null). Observable. */
    val pointerInRoot: State<Offset> get() = pointerState

    /** Id of the target currently under the pointer that accepts the payload, else null. Observable (for highlighting). */
    val hoverTargetId: State<String?> get() = hoverState

    /** True while a drag is active. */
    val isDragging: Boolean get() = payloadState.value != null

    /** Begins a drag of [payload] with the pointer at [pointerInRoot]. Replaces a drag already in progress. */
    fun start(payload: DragPayload, pointerInRoot: Offset) {
        payloadState.value = payload
        pointerState.value = pointerInRoot
        hoverState.value = hit(payload, pointerInRoot)?.id
    }

    /** Moves the pointer. No-op without an active drag. */
    fun move(pointerInRoot: Offset) {
        val current = payloadState.value ?: return
        pointerState.value = pointerInRoot
        hoverState.value = hit(current, pointerInRoot)?.id
    }

    /**
     * Ends the drag at the last pointer position. Hit-tests the registered targets, last registered
     * first; the first one whose `bounds()` contains the pointer and whose `accepts(payload)` is true
     * gets `onDrop(payload, pointerInRoot)`. The drag state is cleared before `onDrop` runs. Nothing
     * happens when no target hits (the payload is simply discarded).
     *
     * @return true when a target took the payload, false when none did (or no drag was active).
     */
    fun drop(): Boolean {
        val current = payloadState.value ?: return false
        val pointer = pointerState.value
        val target = hit(current, pointer)
        clear()
        if (target == null) return false
        target.onDrop(current, pointer)
        return true
    }

    /** Ends the drag without dropping anywhere. */
    fun cancel() {
        clear()
    }

    /**
     * Registers (or replaces, keeping its hit-test priority) the drop target [id].
     *
     * @param bounds the target's bounds in root coordinates, or null while it is not laid out.
     * @param accepts whether [onDrop] can take this payload; targets that refuse are skipped.
     * @param onDrop called with the payload and the pointer position in root coordinates.
     */
    fun registerTarget(
        id: String,
        bounds: () -> Rect?,
        accepts: (DragPayload) -> Boolean,
        onDrop: (DragPayload, Offset) -> Unit,
    ) {
        val existing = targets.firstOrNull { it.id == id }
        if (existing != null) {
            existing.bounds = bounds
            existing.accepts = accepts
            existing.onDrop = onDrop
        } else {
            targets.add(Target(id, bounds, accepts, onDrop))
        }
    }

    /** Removes the drop target [id]; unknown ids are ignored. */
    fun unregisterTarget(id: String) {
        targets.removeAll { it.id == id }
    }

    private fun hit(payload: DragPayload, pointer: Offset): Target? {
        for (index in targets.indices.reversed()) {
            val target = targets[index]
            val rect = target.bounds() ?: continue
            if (rect.contains(pointer) && target.accepts(payload)) return target
        }
        return null
    }

    private fun clear() {
        payloadState.value = null
        hoverState.value = null
    }
}

/** The launcher-wide [DragController]; provided at the launcher root, throws when read without one. */
val LocalDragController = staticCompositionLocalOf<DragController> {
    error("No DragController provided: wrap the launcher in CompositionLocalProvider(LocalDragController provides rememberDragController())")
}

/** A [DragController] that lives as long as the calling composition. */
@Composable
fun rememberDragController(): DragController = remember { DragController() }

/** Latest layout coordinates of a node, kept out of snapshot state so measuring never writes state. */
private class CoordinatesHolder {
    var coordinates: LayoutCoordinates? = null

    fun toRoot(local: Offset): Offset {
        val c = coordinates
        return if (c != null && c.isAttached) c.localToRoot(local) else local
    }

    fun boundsOrNull(): Rect? {
        val c = coordinates
        return if (c != null && c.isAttached) c.boundsInRoot() else null
    }
}

/**
 * Registers this node as the drop target [id] on [controller] while it is in the composition.
 * [accepts] and [onDrop] are read fresh at drop time, so passing new lambdas does not re-register.
 */
@Composable
fun Modifier.dragTarget(
    controller: DragController,
    id: String,
    accepts: (DragPayload) -> Boolean = { true },
    onDrop: (DragPayload, Offset) -> Unit,
): Modifier {
    val holder = remember { CoordinatesHolder() }
    val latestAccepts = rememberUpdatedState(accepts)
    val latestOnDrop = rememberUpdatedState(onDrop)
    DisposableEffect(controller, id) {
        controller.registerTarget(
            id = id,
            bounds = { holder.boundsOrNull() },
            accepts = { payload -> latestAccepts.value(payload) },
            onDrop = { payload, pointer -> latestOnDrop.value(payload, pointer) },
        )
        onDispose { controller.unregisterTarget(id) }
    }
    return this.onGloballyPositioned { holder.coordinates = it }
}

/**
 * Makes this node a drag source: a long press followed by a drag calls `controller.start` (with
 * [payload]), `move` while dragging, and `drop` on release (`cancel` when the gesture is cancelled).
 * Moves are consumed once the drag has started.
 *
 * The node must stay in the composition until the drop: a gesture whose pointer input leaves
 * composition ends silently without `drop` / `cancel`. A host that wants to get out of the way (the
 * app pop-up) hides itself with `graphicsLayer { alpha = 0f }` while `controller.isDragging`, it does
 * not remove its content.
 *
 * @param payload what to drag; return null to ignore the long press.
 * @param onStarted called right after the drag started (e.g. close the pop-up visually, buzz).
 */
@Composable
fun Modifier.dragSource(
    controller: DragController,
    payload: () -> DragPayload?,
    enabled: Boolean = true,
    onStarted: () -> Unit = {},
): Modifier {
    val holder = remember { CoordinatesHolder() }
    val latestPayload = rememberUpdatedState(payload)
    val latestStarted = rememberUpdatedState(onStarted)
    val positioned = this.onGloballyPositioned { holder.coordinates = it }
    if (!enabled) return positioned
    return positioned.pointerInput(controller) {
        detectDragGesturesAfterLongPress(
            onDragStart = { offset ->
                val dragged = latestPayload.value()
                if (dragged != null) {
                    controller.start(dragged, holder.toRoot(offset))
                    latestStarted.value()
                }
            },
            onDragEnd = { controller.drop() },
            onDragCancel = { controller.cancel() },
            onDrag = { change, _ ->
                if (controller.isDragging) {
                    change.consume()
                    controller.move(holder.toRoot(change.position))
                }
            },
        )
    }
}

/**
 * Draws [ghost] (the dragged icon) centred on the pointer while a drag is active. Compose it last
 * inside a root `Box` that fills the window (so root coordinates are its own coordinates); it never
 * intercepts touches.
 */
@Composable
fun DragGhostLayer(
    controller: DragController,
    modifier: Modifier = Modifier,
    ghost: @Composable (DragPayload) -> Unit,
) {
    val payload = controller.payload.value ?: return
    var size by remember { mutableStateOf(IntSize.Zero) }
    Box(modifier.fillMaxSize()) {
        Box(
            Modifier
                .offset {
                    val p = controller.pointerInRoot.value
                    IntOffset((p.x - size.width / 2f).roundToInt(), (p.y - size.height / 2f).roundToInt())
                }
                .onSizeChanged { size = it },
        ) {
            ghost(payload)
        }
    }
}
