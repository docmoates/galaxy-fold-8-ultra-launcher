package com.foldos.launcher.fold

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.foldos.launcher.core.fold.FoldState
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.model.ScreenMode
import kotlin.math.min

/**
 * The live [FoldState] of the hosting activity. Creates one [FoldStateObserver] per activity,
 * starts it while this composable is in the composition and stops it on dispose.
 *
 * Outside an activity (previews) it falls back to a static estimate from [LocalConfiguration].
 */
@Composable
fun rememberFoldState(): FoldState {
    val context = LocalContext.current
    val activity = remember(context) { context.findComponentActivity() }
    return if (activity != null) {
        rememberFoldState(activity)
    } else {
        val configuration = LocalConfiguration.current
        remember(configuration) { fallbackFoldState(configuration) }
    }
}

/** Same as [rememberFoldState] for an explicit activity. */
@Composable
fun rememberFoldState(activity: ComponentActivity): FoldState {
    val observer = remember(activity) { FoldStateObserver(activity) }
    DisposableEffect(observer) {
        observer.start()
        onDispose { observer.stop() }
    }
    val configuration = LocalConfiguration.current
    LaunchedEffect(observer, configuration) { observer.refresh() }
    val state: State<FoldState> = observer.state.collectAsState()
    return state.value
}

/**
 * Which layout store a given posture reads from (PLAN.md 2.1).
 *
 * - COVER always uses the cover layout.
 * - INNER and FLEX use the inner layout, except under [ScreenMode.SAME] where the inner screen
 *   scales the cover layout.
 * - FLEX never gets its own store: [com.foldos.launcher.home.HomeScreen] splits the chosen layout
 *   at the hinge itself when [FoldState.kind] is FLEX (half opened with a horizontal hinge).
 */
fun effectiveScreen(settings: LauncherSettings, fold: FoldState): ScreenKind = when (fold.kind) {
    ScreenKind.COVER -> ScreenKind.COVER
    ScreenKind.INNER, ScreenKind.FLEX ->
        if (settings.screenMode == ScreenMode.SAME) ScreenKind.COVER else ScreenKind.INNER
}

private fun fallbackFoldState(configuration: Configuration): FoldState {
    val widthDp = configuration.screenWidthDp
    val heightDp = configuration.screenHeightDp
    // Same rule as FoldStateObserver.compute: the shorter side decides, so rotation does not matter.
    val shortSideDp = min(widthDp, heightDp)
    val kind = if (shortSideDp >= FoldStateObserver.INNER_MIN_WIDTH_DP) ScreenKind.INNER else ScreenKind.COVER
    return FoldState(
        kind = kind,
        isHalfOpened = false,
        hingeBounds = null,
        widthDp = widthDp,
        heightDp = heightDp,
    )
}

private fun Context.findComponentActivity(): ComponentActivity? {
    var current: Context? = this
    while (current != null) {
        if (current is ComponentActivity) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}
