package com.foldos.launcher.picker

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Event
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Group
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Mood
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Payment
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.ui.graphics.vector.ImageVector
import com.foldos.launcher.core.model.WidgetCategory
import com.foldos.launcher.core.repo.WidgetCatalog

/** Maps the mock-up's Material Symbols names (`CATICON`) to bundled `Icons.Rounded` vectors. */
object WidgetPickerIcons {

    /** Vector for a [WidgetCategory] chip or section header. */
    fun forCategory(category: WidgetCategory): ImageVector = when (category) {
        WidgetCategory.CLOCK -> Icons.Rounded.Schedule
        WidgetCategory.WEATHER -> Icons.Rounded.WbSunny
        WidgetCategory.CALENDAR -> Icons.Rounded.Event
        WidgetCategory.COMMUNICATION -> Icons.AutoMirrored.Rounded.Chat
        WidgetCategory.MEDIA -> Icons.Rounded.MusicNote
        WidgetCategory.PHOTOS -> Icons.Rounded.PhotoLibrary
        WidgetCategory.PRODUCTIVITY -> Icons.Rounded.Description
        WidgetCategory.HEALTH -> Icons.Rounded.Favorite
        WidgetCategory.DEVICE -> Icons.Rounded.Smartphone
        WidgetCategory.SMART_HOME -> Icons.Rounded.Home
        WidgetCategory.TRAVEL -> Icons.Rounded.Map
        WidgetCategory.FINANCE -> Icons.Rounded.Payment
        WidgetCategory.NEWS -> Icons.AutoMirrored.Rounded.Article
        WidgetCategory.SEARCH -> Icons.Rounded.Search
        WidgetCategory.SOCIAL -> Icons.Rounded.Group
        WidgetCategory.UTILITIES -> Icons.Rounded.Build
        WidgetCategory.FUN -> Icons.Rounded.Mood
    }

    /**
     * Vector for a group title as returned by `WidgetCatalog.grouped`: a category display name,
     * the "Recently used" title, the built-in source, or any app name.
     */
    fun forGroup(title: String): ImageVector = when (title) {
        WidgetCatalog.RECENT_GROUP_TITLE -> Icons.Rounded.History
        FoldOsWidgetCatalog.BUILT_IN_SOURCE -> Icons.Rounded.Widgets
        else -> WidgetCategory.byDisplayName(title)?.let(::forCategory) ?: Icons.Rounded.Apps
    }
}
