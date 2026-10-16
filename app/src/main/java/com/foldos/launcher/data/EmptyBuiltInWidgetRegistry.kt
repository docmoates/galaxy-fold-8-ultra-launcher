package com.foldos.launcher.data

import com.foldos.launcher.core.repo.BuiltInWidget
import com.foldos.launcher.core.repo.BuiltInWidgetRegistry

/** Placeholder [BuiltInWidgetRegistry] with no widgets, until the Builtins agent's registry lands. */
class EmptyBuiltInWidgetRegistry : BuiltInWidgetRegistry {
    override val all: List<BuiltInWidget> = emptyList()

    override fun byId(id: String): BuiltInWidget? = null
}
