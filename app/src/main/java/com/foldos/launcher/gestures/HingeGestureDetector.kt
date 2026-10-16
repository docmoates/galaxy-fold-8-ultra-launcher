package com.foldos.launcher.gestures

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.foldos.launcher.core.fold.FoldState
import com.foldos.launcher.core.model.GestureAction
import com.foldos.launcher.core.model.GestureKind
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull

/**
 * Turns [FoldState] transitions into hinge gestures: closed -> open is [GestureKind.UNFOLD], open ->
 * closed is [GestureKind.FOLD]. "Open" means the inner screen in any posture ([ScreenKind.INNER] or
 * [ScreenKind.FLEX]), so half-folding an open device fires nothing. The first state fed in only
 * primes the detector, so nothing fires when the launcher starts.
 *
 * Feed it every state through [onFoldState] (or let [HingeGestureEffect] / [actions] do it) and it
 * reports each transition exactly once.
 */
class HingeGestureDetector {

    private var previousKind: ScreenKind? = null

    /** The posture last fed in, or null before the first call / after [reset]. */
    val lastKind: ScreenKind? get() = previousKind

    /** Records [state]; returns the hinge gesture its posture change completes, or null. */
    fun onFoldState(state: FoldState): GestureKind? {
        val before = previousKind
        val now = state.kind
        previousKind = now
        if (before == null) return null
        return transition(before, now)
    }

    /** The configured action for the transition [state] completes; null when none fires. */
    fun actionFor(state: FoldState, settings: LauncherSettings): GestureAction? {
        val kind = onFoldState(state) ?: return null
        if (!settings.feature(LauncherSettings.FEATURE_GESTURES)) return null
        val action = settings.gesture(kind)
        return if (action.type == GestureAction.TYPE_NONE) null else action
    }

    /** Forgets the last posture, so the next state only primes again. */
    fun reset() {
        previousKind = null
    }

    /**
     * Hinge actions as a flow: every fold / unfold in [foldStates] bound to a non-none action in the
     * current [settings] (read when the transition happens). Hooks nothing while gestures are off.
     */
    fun actions(foldStates: Flow<FoldState>, settings: Flow<LauncherSettings>): Flow<GestureAction> =
        foldStates
            .mapNotNull { state -> onFoldState(state) }
            .map { kind ->
                val current = settings.first()
                if (current.feature(LauncherSettings.FEATURE_GESTURES)) current.gesture(kind) else GestureAction.NONE
            }
            .filter { it.type != GestureAction.TYPE_NONE }

    companion object {
        /** The hinge gesture a posture change completes, or null when the device stayed open / closed. */
        fun transition(from: ScreenKind, to: ScreenKind): GestureKind? {
            val wasOpen = from != ScreenKind.COVER
            val isOpen = to != ScreenKind.COVER
            return when {
                !wasOpen && isOpen -> GestureKind.UNFOLD
                wasOpen && !isOpen -> GestureKind.FOLD
                else -> null
            }
        }
    }
}

/**
 * Composable hook for hinge gestures: remembers one [HingeGestureDetector] and, every time
 * [fold]'s posture changes, performs the bound action through [onAction] (usually
 * [GestureActions.perform]). [enabled] false, or `FEATURE_GESTURES` off, still tracks the posture
 * so a later switch-on does not replay an old transition.
 */
@Composable
fun HingeGestureEffect(
    fold: FoldState,
    enabled: Boolean,
    settings: LauncherSettings,
    onAction: (GestureAction) -> Unit,
) {
    val detector = remember { HingeGestureDetector() }
    val latestEnabled = rememberUpdatedState(enabled)
    val latestSettings = rememberUpdatedState(settings)
    val latestOnAction = rememberUpdatedState(onAction)
    LaunchedEffect(fold.kind) {
        val kind = detector.onFoldState(fold) ?: return@LaunchedEffect
        if (!latestEnabled.value) return@LaunchedEffect
        val current = latestSettings.value
        if (!current.feature(LauncherSettings.FEATURE_GESTURES)) return@LaunchedEffect
        val action = current.gesture(kind)
        if (action.type != GestureAction.TYPE_NONE) latestOnAction.value(action)
    }
}

/**
 * [GestureKind.HOME_DOUBLE]: the home key pressed twice within [timeoutMillis]. The launcher
 * activity receives each press while it is already showing as `onNewIntent`; call [onHomePressed]
 * from there and perform `settings.gesture(GestureKind.HOME_DOUBLE)` when it returns true.
 */
class HomeKeyDoubleTapDetector(private val timeoutMillis: Long = DEFAULT_TIMEOUT_MILLIS) {

    private var lastPressTime: Long = 0L

    /** Records a home key press at [now] (uptime millis); true when it completes a double press. */
    fun onHomePressed(now: Long = SystemClock.uptimeMillis()): Boolean {
        val isDouble = lastPressTime != 0L && now - lastPressTime <= timeoutMillis
        lastPressTime = if (isDouble) 0L else now
        return isDouble
    }

    companion object {
        const val DEFAULT_TIMEOUT_MILLIS: Long = 400L
    }
}
