package com.foldos.launcher.core.repo

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.foldos.launcher.core.model.WidgetCategory

/**
 * A FoldOS widget drawn in Compose and themed by the launcher.
 *
 * @property id stable id, also used as `HomeItem.builtinId` and `WidgetEntry.id`.
 * @property sizes supported (spanX, spanY) cell sizes; the first is the default.
 * @property content draws the widget for the given span inside [Modifier]; it must fill the
 *   box it is given at every supported size.
 */
data class BuiltInWidget(
    val id: String,
    val name: String,
    val category: WidgetCategory,
    val sizes: List<Pair<Int, Int>>,
    val content: @Composable (spanX: Int, spanY: Int, modifier: Modifier) -> Unit,
) {
    /** Default (first listed) size. */
    val defaultSize: Pair<Int, Int> get() = sizes.firstOrNull() ?: (1 to 1)

    val minSpanX: Int get() = sizes.minOfOrNull { it.first } ?: 1
    val minSpanY: Int get() = sizes.minOfOrNull { it.second } ?: 1
}

/** Every built-in widget. Implemented by the Builtins agent; the Data agent ships an empty one. */
interface BuiltInWidgetRegistry {
    val all: List<BuiltInWidget>

    fun byId(id: String): BuiltInWidget?
}
