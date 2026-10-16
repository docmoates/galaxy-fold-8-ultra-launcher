package com.foldos.launcher.a11y

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable

/*
 * Reduce-motion helpers. Every one of them reads LocalReduceMotion, which A11yRoot provides from
 * LauncherSettings.reduceMotion; outside A11yRoot they return their input unchanged.
 */

/** [millis] normally, 0 while Reduce motion is on (`tween(0)` is an instant change). */
@Composable
fun a11yDuration(millis: Int): Int = if (LocalReduceMotion.current) 0 else millis

/** [spec] normally, an instant `snap()` while Reduce motion is on. */
@Composable
fun <T> a11ySpec(spec: FiniteAnimationSpec<T>): FiniteAnimationSpec<T> =
    if (LocalReduceMotion.current) snap() else spec

/** [transition] normally, [EnterTransition.None] while Reduce motion is on. */
@Composable
fun a11yEnter(transition: EnterTransition): EnterTransition =
    if (LocalReduceMotion.current) EnterTransition.None else transition

/** [transition] normally, [ExitTransition.None] while Reduce motion is on. */
@Composable
fun a11yExit(transition: ExitTransition): ExitTransition =
    if (LocalReduceMotion.current) ExitTransition.None else transition

/**
 * A function that scrolls a pager to a page: animated normally, instant while Reduce motion is on.
 * Call it from a coroutine: `val scroll = rememberA11yPagerScroll(state)` then `scroll(page)`.
 */
@Composable
fun rememberA11yPagerScroll(state: PagerState): suspend (page: Int) -> Unit {
    val reduce = LocalReduceMotion.current
    return { page ->
        if (reduce) state.scrollToPage(page) else state.animateScrollToPage(page)
    }
}

/** Like [rememberA11yPagerScroll] for a lazy list item index. */
@Composable
fun rememberA11yListScroll(state: LazyListState): suspend (index: Int) -> Unit {
    val reduce = LocalReduceMotion.current
    return { index ->
        if (reduce) state.scrollToItem(index) else state.animateScrollToItem(index)
    }
}
