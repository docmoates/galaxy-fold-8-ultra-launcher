package com.foldos.launcher.apppicker

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import com.foldos.launcher.core.ui.DragController
import com.foldos.launcher.core.ui.DragPayload

/** Press time after which a touch on an app icon becomes a long press (menu or drag). */
internal const val TILE_LONG_PRESS_MS: Long = 300L

private const val RESULT_TAP = 1
private const val RESULT_ABANDONED = 2

/** Latest layout coordinates of a node, kept out of snapshot state so measuring never writes state. */
private class TileCoordinates {
    var coordinates: LayoutCoordinates? = null

    fun toRoot(local: Offset): Offset {
        val c = coordinates
        return if (c != null && c.isAttached) c.localToRoot(local) else local
    }
}

/**
 * Touch handling of an app icon that can be tapped, long-pressed for a menu, and dragged to the home
 * screen or the dock. Shared by the app pop-up tiles and the dock icons.
 *
 * - Release within [TILE_LONG_PRESS_MS] without moving: [onTap] (when non-null; when null the tap is
 *   left to a `clickable` on the same node).
 * - Press held for [TILE_LONG_PRESS_MS], then moved past the touch slop: `controller.start(payload)`,
 *   `move` while dragging, `drop` on release (`cancel` when the gesture is cancelled). Moves are
 *   consumed from the long press on, so a parent list does not scroll.
 * - Press held, then released without moving: [onLongPress] (the "Add to group" / "Remove from dock" menu).
 *
 * Put it after (inside) a `clickable` in the modifier chain so the release is consumed before the click
 * sees it. The node must stay in the composition until the drop (see `Drag.kt`).
 *
 * @param controller null disables dragging (previews, windows without a drag controller).
 * @param payload what to drag; null ignores the drag.
 * @param onDragStarted called right after `controller.start`.
 * @param onDragFinished called after `controller.drop()` (true when a target took the payload, false when none did) or `controller.cancel()` (false); any
 *   `onDrop` of a target has already run when it is called with true.
 */
@Composable
internal fun Modifier.appTileGestures(
    controller: DragController?,
    payload: () -> DragPayload?,
    onTap: (() -> Unit)? = null,
    onLongPress: (() -> Unit)? = null,
    onDragStarted: () -> Unit = {},
    onDragFinished: (dropped: Boolean) -> Unit = {},
): Modifier {
    val holder = remember { TileCoordinates() }
    val latestPayload = rememberUpdatedState(payload)
    val latestTap = rememberUpdatedState(onTap)
    val latestLongPress = rememberUpdatedState(onLongPress)
    val latestStarted = rememberUpdatedState(onDragStarted)
    val latestFinished = rememberUpdatedState(onDragFinished)
    val haptic = LocalHapticFeedback.current
    val positioned = this.onGloballyPositioned { holder.coordinates = it }
    return positioned.pointerInput(controller) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val slop = viewConfiguration.touchSlop

            // Phase 1: wait for the long press; a release, a move past the slop or a consumed event ends it.
            var result = RESULT_ABANDONED
            val endedEarly = withTimeoutOrNull<Boolean>(TILE_LONG_PRESS_MS) {
                while (true) {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null || change.isConsumed) {
                        result = RESULT_ABANDONED
                        break
                    }
                    if (change.changedToUpIgnoreConsumed()) {
                        result = RESULT_TAP
                        if (latestTap.value != null) change.consume()
                        break
                    }
                    if ((change.position - down.position).getDistance() > slop) {
                        result = RESULT_ABANDONED
                        break
                    }
                }
                true
            }
            if (endedEarly != null) {
                if (result == RESULT_TAP) latestTap.value?.invoke()
                return@awaitEachGesture
            }

            // Phase 2: long press. Drag once the finger moves, otherwise treat the release as "open menu".
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            var dragging = false
            var finished = false
            try {
                val graceful = drag(down.id) { change ->
                    change.consume()
                    val active = controller
                    if (!dragging) {
                        if (active != null && (change.position - down.position).getDistance() > slop) {
                            val dragged = latestPayload.value()
                            if (dragged != null) {
                                dragging = true
                                active.start(dragged, holder.toRoot(change.position))
                                latestStarted.value()
                            }
                        }
                    } else if (active != null) {
                        active.move(holder.toRoot(change.position))
                    }
                }
                if (graceful) {
                    for (c in currentEvent.changes) {
                        if (c.changedToUp()) c.consume()
                    }
                    finished = true
                    if (dragging) {
                        val accepted = controller?.drop() == true
                        latestFinished.value(accepted)
                    } else {
                        latestLongPress.value?.invoke()
                    }
                } else {
                    finished = true
                    if (dragging) {
                        controller?.cancel()
                        latestFinished.value(false)
                    }
                }
            } finally {
                if (dragging && !finished) controller?.cancel()
            }
        }
    }
}
