package com.foldos.launcher.fold

import android.view.View
import androidx.activity.ComponentActivity
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker
import androidx.window.layout.WindowLayoutInfo
import androidx.window.layout.WindowMetricsCalculator
import com.foldos.launcher.core.fold.FoldState
import com.foldos.launcher.core.model.ScreenKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Produces [FoldState] for one activity from Jetpack WindowManager.
 *
 * - [WindowInfoTracker.windowLayoutInfo] supplies the [FoldingFeature] (HALF_OPENED => isHalfOpened,
 *   bounds => hingeBounds).
 * - [WindowMetricsCalculator] supplies the current window size, converted to dp.
 * - A layout listener on the decor view re-reads the metrics when the window is resized
 *   (fold / unfold without activity recreation, split screen, rotation).
 *
 * Call [start] when the activity is visible and [stop] when it goes away; [rememberFoldState]
 * does this for you inside a DisposableEffect.
 */
class FoldStateObserver(private val activity: ComponentActivity) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private var latestLayoutInfo: WindowLayoutInfo? = null

    private val _state = MutableStateFlow(compute(null))

    /** The current posture; starts with a metrics-only estimate until the first layout info arrives. */
    val state: StateFlow<FoldState> = _state.asStateFlow()

    private val layoutListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
        val sizeChanged = (right - left) != (oldRight - oldLeft) || (bottom - top) != (oldBottom - oldTop)
        if (sizeChanged) publish()
    }

    /** Starts collecting window layout info. Safe to call more than once. */
    fun start() {
        if (job != null) return
        activity.window.decorView.addOnLayoutChangeListener(layoutListener)
        publish()
        job = scope.launch {
            WindowInfoTracker.getOrCreate(activity)
                .windowLayoutInfo(activity)
                .collect { info ->
                    latestLayoutInfo = info
                    publish()
                }
        }
    }

    /** Stops collecting. The last [state] value stays available. */
    fun stop() {
        job?.cancel()
        job = null
        activity.window.decorView.removeOnLayoutChangeListener(layoutListener)
    }

    /** Re-reads the window metrics with the last known folding feature (e.g. after a configuration change). */
    fun refresh() {
        publish()
    }

    private fun publish() {
        _state.value = compute(latestLayoutInfo)
    }

    private fun compute(info: WindowLayoutInfo?): FoldState {
        val metrics = WindowMetricsCalculator.getOrCreate().computeCurrentWindowMetrics(activity)
        val bounds = metrics.bounds
        val density = activity.resources.displayMetrics.density.takeIf { it > 0f } ?: 1f
        val widthDp = (bounds.width() / density).roundToInt()
        val heightDp = (bounds.height() / density).roundToInt()

        val folding = info?.displayFeatures
            ?.filterIsInstance<FoldingFeature>()
            ?.firstOrNull()
        val isHalfOpened = folding != null && folding.state == FoldingFeature.State.HALF_OPENED
        val isTableTop = isHalfOpened && folding != null &&
            folding.orientation == FoldingFeature.Orientation.HORIZONTAL

        // Classify by the shorter window side so a rotated cover screen (~915 x 374 dp) is not
        // mistaken for the inner display just because it is wide.
        val shortSideDp = min(widthDp, heightDp)
        val kind = when {
            isTableTop -> ScreenKind.FLEX
            shortSideDp >= INNER_MIN_WIDTH_DP -> ScreenKind.INNER
            else -> ScreenKind.COVER
        }
        return FoldState(
            kind = kind,
            isHalfOpened = isHalfOpened,
            hingeBounds = folding?.bounds,
            widthDp = widthDp,
            heightDp = heightDp,
        )
    }

    companion object {
        /** Windows whose shorter side is at least this many dp are treated as the inner (unfolded) screen. */
        const val INNER_MIN_WIDTH_DP: Int = 600
    }
}
