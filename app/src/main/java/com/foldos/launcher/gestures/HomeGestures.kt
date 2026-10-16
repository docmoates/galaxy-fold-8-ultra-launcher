package com.foldos.launcher.gestures

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.GestureAction
import com.foldos.launcher.core.model.GestureKind
import com.foldos.launcher.core.model.LauncherSettings
import kotlin.math.abs

/**
 * Home-screen gesture detection (Settings > Gestures). Put it on the page container, **after** the
 * page's long-press modifier in the chain (so it sits inside it and sees pointer events first):
 *
 * - one-finger vertical swipes: [GestureKind.SWIPE_UP] / [GestureKind.SWIPE_DOWN] once the finger
 *   has travelled [SWIPE_DISTANCE_DP] (80 dp), or [FLICK_DISTANCE_DP] at [FLICK_VELOCITY_DP_PER_S]
 *   or faster. The moves are consumed once the drag is clearly vertical, so the pager does not
 *   start a page scroll and the long-press detector gives up.
 * - one-finger horizontal drags are left alone: the `HorizontalPager` keeps them.
 * - two-finger swipes in the four directions ([GestureKind.SWIPE_UP_2F] ...), decided by the
 *   centroid's travel; every move with two fingers down is consumed.
 * - pinch: [GestureKind.PINCH_IN] below [PINCH_IN_SCALE] (0.85), [GestureKind.PINCH_OUT] above
 *   [PINCH_OUT_SCALE] (1.15), measured as the distance between the two fingers at the end over the
 *   distance when the second finger landed. A pinch wins over a two-finger swipe.
 * - [GestureKind.DOUBLE_TAP]: two quick taps (within `ViewConfiguration.doubleTapTimeoutMillis`,
 *   under [DOUBLE_TAP_SLOP_DP] apart). Taps are counted whether or not a sibling long-press detector
 *   consumed them, so a double tap on an empty grid cell works; a double tap on an app icon fires
 *   the first tap's launch and may fire this too.
 *
 * A gesture that another node takes over (a child scroll or a widget consuming the moves, or the
 * pager claiming a horizontal drag) is abandoned without an action. The detector never consumes
 * down or up events, so taps and long presses on the same node keep working.
 *
 * Returns `this` unchanged when [enabled] is false or `FEATURE_GESTURES` is off. [settings] and
 * [onAction] are read at the moment a gesture completes, so changing a binding in Settings takes
 * effect without recreating the pointer input.
 *
 * @param onAction called with the bound [GestureAction] (never [GestureAction.TYPE_NONE]); the
 *   caller usually forwards it to [GestureActions.perform].
 */
@Composable
fun Modifier.homeGestures(
    enabled: Boolean,
    settings: LauncherSettings,
    onAction: (GestureAction) -> Unit,
): Modifier {
    val latestSettings = rememberUpdatedState(settings)
    val latestOnAction = rememberUpdatedState(onAction)
    val taps = remember { TapHistory() }
    val active = enabled && settings.feature(LauncherSettings.FEATURE_GESTURES)
    if (!active) return this
    return pointerInput(Unit) {
        val config = HomeGestureConfig.from(this)
        awaitEachGesture {
            val kind = detectHomeGesture(config, taps) ?: return@awaitEachGesture
            val action = latestSettings.value.gesture(kind)
            if (action.type != GestureAction.TYPE_NONE) latestOnAction.value(action)
        }
    }
}

/**
 * The same detector reporting the raw [GestureKind] instead of looking up a binding (previews,
 * tests, custom hosts). Not a composable; [onGesture] is captured as the pointer input key.
 */
fun Modifier.homeGestureDetector(onGesture: (GestureKind) -> Unit): Modifier = pointerInput(onGesture) {
    val config = HomeGestureConfig.from(this)
    val taps = TapHistory()
    awaitEachGesture {
        detectHomeGesture(config, taps)?.let(onGesture)
    }
}

/** Finger travel that counts as a swipe (one or two fingers). */
const val SWIPE_DISTANCE_DP: Int = 80

/** Minimum travel for a fast flick to count as a swipe before reaching [SWIPE_DISTANCE_DP]. */
const val FLICK_DISTANCE_DP: Int = 24

/** Velocity (dp per second) a flick needs to count as a swipe at [FLICK_DISTANCE_DP]. */
const val FLICK_VELOCITY_DP_PER_S: Int = 1000

/** How far apart the two taps of a double tap may land. */
const val DOUBLE_TAP_SLOP_DP: Int = 48

/** Two-finger scale below which the gesture is a pinch in. */
const val PINCH_IN_SCALE: Float = 0.85f

/** Two-finger scale above which the gesture is a pinch out. */
const val PINCH_OUT_SCALE: Float = 1.15f

/** Thresholds resolved to pixels once per pointer input block. */
internal class HomeGestureConfig(
    val swipeDistancePx: Float,
    val flickDistancePx: Float,
    val flickVelocityPx: Float,
    val doubleTapSlopPx: Float,
    val touchSlopPx: Float,
    val longPressTimeoutMillis: Long,
    val doubleTapTimeoutMillis: Long,
    val doubleTapMinTimeMillis: Long,
) {
    companion object {
        fun from(scope: PointerInputScope): HomeGestureConfig = with(scope) {
            HomeGestureConfig(
                swipeDistancePx = SWIPE_DISTANCE_DP.dp.toPx(),
                flickDistancePx = FLICK_DISTANCE_DP.dp.toPx(),
                flickVelocityPx = FLICK_VELOCITY_DP_PER_S.dp.toPx(),
                doubleTapSlopPx = DOUBLE_TAP_SLOP_DP.dp.toPx(),
                touchSlopPx = viewConfiguration.touchSlop,
                longPressTimeoutMillis = viewConfiguration.longPressTimeoutMillis,
                doubleTapTimeoutMillis = viewConfiguration.doubleTapTimeoutMillis,
                doubleTapMinTimeMillis = viewConfiguration.doubleTapMinTimeMillis,
            )
        }
    }
}

/** The previous single tap, kept across gestures so the next one can complete a double tap. */
internal class TapHistory {
    var valid: Boolean = false
        private set
    var downTime: Long = 0L
        private set
    var upTime: Long = 0L
        private set
    var position: Offset = Offset.Zero
        private set

    fun record(downTime: Long, upTime: Long, position: Offset) {
        this.downTime = downTime
        this.upTime = upTime
        this.position = position
        valid = true
    }

    fun clear() {
        valid = false
    }
}

/**
 * Tracks one gesture from its first down to the last up and classifies it, or returns null when
 * it was a plain tap, a horizontal page swipe, something a child took over, or too small.
 */
internal suspend fun AwaitPointerEventScope.detectHomeGesture(
    config: HomeGestureConfig,
    taps: TapHistory,
): GestureKind? {
    val down = awaitFirstDown(requireUnconsumed = false)
    val downTime = down.uptimeMillis
    val downPosition = down.position
    val velocity = VelocityTracker()
    velocity.addPosition(downTime, downPosition)

    var maxPointers = 1
    var cancelled = false
    var verticalOwned = false
    var singleTotal = Offset.Zero
    var twoStartCentroid: Offset? = null
    var twoStartDistance = 0f
    var twoLastCentroid = Offset.Zero
    var twoLastDistance = 0f
    var lastTime = downTime

    while (true) {
        val event = awaitPointerEvent()
        val pressed = event.changes.filter { it.pressed }
        if (pressed.size > maxPointers) maxPointers = pressed.size
        for (change in event.changes) if (change.uptimeMillis > lastTime) lastTime = change.uptimeMillis

        if (!cancelled) {
            val owned = verticalOwned || maxPointers >= 2
            val takenByChild = event.changes.any { it.isConsumed && it.positionChanged() }
            if (takenByChild && !owned) {
                cancelled = true
            } else if (pressed.size >= 2) {
                val a = pressed[0].position
                val b = pressed[1].position
                val centroid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                val distance = (a - b).getDistance()
                if (twoStartCentroid == null) {
                    twoStartCentroid = centroid
                    twoStartDistance = distance
                }
                twoLastCentroid = centroid
                twoLastDistance = distance
                for (change in event.changes) if (change.positionChanged()) change.consume()
            } else if (pressed.size == 1 && maxPointers == 1) {
                val change = pressed[0]
                singleTotal += change.position - change.previousPosition
                velocity.addPosition(change.uptimeMillis, change.position)
                if (!verticalOwned) {
                    val dx = abs(singleTotal.x)
                    val dy = abs(singleTotal.y)
                    if (dy > config.touchSlopPx && dy > dx) {
                        verticalOwned = true
                    } else if (dx > config.touchSlopPx && dx >= dy) {
                        // A horizontal drag belongs to the pager.
                        cancelled = true
                    }
                }
                if (verticalOwned && change.positionChanged()) change.consume()
            }
        }

        if (pressed.isEmpty()) break

        if (!cancelled && !verticalOwned && maxPointers == 1) {
            // Not ours yet. Parents (the pager) run after this node in the Main pass; wait for the
            // Final pass of the same event to learn whether one of them claimed the drag.
            awaitPointerEvent(PointerEventPass.Final)
            if (event.changes.any { it.isConsumed && it.positionChanged() }) cancelled = true
        }
    }

    if (cancelled) {
        taps.clear()
        return null
    }

    if (maxPointers >= 2) {
        taps.clear()
        val start = twoStartCentroid ?: return null
        val zoom = if (twoStartDistance > 0f) twoLastDistance / twoStartDistance else 1f
        val pan = twoLastCentroid - start
        val panX = abs(pan.x)
        val panY = abs(pan.y)
        return when {
            zoom < PINCH_IN_SCALE -> GestureKind.PINCH_IN
            zoom > PINCH_OUT_SCALE -> GestureKind.PINCH_OUT
            panY >= config.swipeDistancePx && panY >= panX ->
                if (pan.y < 0f) GestureKind.SWIPE_UP_2F else GestureKind.SWIPE_DOWN_2F
            panX >= config.swipeDistancePx && panX > panY ->
                if (pan.x < 0f) GestureKind.SWIPE_LEFT_2F else GestureKind.SWIPE_RIGHT_2F
            else -> null
        }
    }

    if (verticalOwned) {
        taps.clear()
        val dy = singleTotal.y
        val vy = velocity.calculateVelocity().y
        val farEnough = abs(dy) >= config.swipeDistancePx
        val flicked = abs(dy) >= config.flickDistancePx &&
            abs(vy) >= config.flickVelocityPx &&
            (vy < 0f) == (dy < 0f)
        if (!farEnough && !flicked) return null
        return if (dy < 0f) GestureKind.SWIPE_UP else GestureKind.SWIPE_DOWN
    }

    // A single pointer that never travelled past the slop: a tap, unless it was held too long.
    val isTap = singleTotal.getDistance() <= config.touchSlopPx &&
        lastTime - downTime < config.longPressTimeoutMillis
    if (!isTap) {
        taps.clear()
        return null
    }
    val isDoubleTap = taps.valid &&
        downTime - taps.upTime <= config.doubleTapTimeoutMillis &&
        downTime - taps.downTime >= config.doubleTapMinTimeMillis &&
        (downPosition - taps.position).getDistance() <= config.doubleTapSlopPx
    if (isDoubleTap) {
        taps.clear()
        return GestureKind.DOUBLE_TAP
    }
    taps.record(downTime, lastTime, downPosition)
    return null
}
