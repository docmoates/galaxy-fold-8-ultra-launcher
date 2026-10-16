package com.foldos.launcher.home

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import com.foldos.launcher.core.model.HomeItem

/**
 * Hooks the live-edit layer (the Edit agent) provides to the home screen through
 * [LocalHomeCallbacks]. Every hook is optional: when one is null the home screen falls back to the
 * Phase 1 behaviour (`HomeScreen.onLongPressHome`, i.e. opening Settings).
 *
 * @property onItemLongPress a long press on an item tile, widget or stack. Items with an id <= 0
 *   are synthetic previews (auto-filled first page, default dashboard before it is seeded) and
 *   are not in the repository.
 * @property onEmptyLongPress a long press on empty page space. `col` / `row` are the grid cell
 *   under the finger, or -1 / -1 when the press landed outside the item grid (header, list,
 *   panel padding).
 */
@Immutable
data class HomeCallbacks(
    val onItemLongPress: ((item: HomeItem) -> Unit)? = null,
    val onEmptyLongPress: ((pageId: Long, col: Int, row: Int) -> Unit)? = null,
)

/** Provided around `HomeScreen` by whoever owns edit mode; defaults to no hooks. */
val LocalHomeCallbacks: ProvidableCompositionLocal<HomeCallbacks> = compositionLocalOf { HomeCallbacks() }
