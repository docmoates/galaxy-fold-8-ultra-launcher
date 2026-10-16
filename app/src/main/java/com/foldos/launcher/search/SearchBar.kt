package com.foldos.launcher.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.theme.FoldOsSurface
import com.foldos.launcher.theme.LocalFoldOsTokens

/**
 * The home-page search pill (mock-up `.srch`: 52dp, radius 26, themed surface). Tapping it calls
 * [onClick], which should open [UniversalSearch]; the Lens glyph on the right starts visual search
 * directly (PLAN.md 4.1), and the sparkle shows while AI features are on. Replaces the Phase 1
 * `HomeSearchBar` stub in `LauncherRoot`.
 */
@Composable
fun SearchBar(
    onClick: () -> Unit,
    settings: LauncherSettings,
    modifier: Modifier = Modifier,
    hidden: Boolean = false,
) {
    if (hidden) return
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    val shape = RoundedCornerShape(26.dp)
    val showLens = settings.webSearchOn()
    val showAi = settings.feature(LauncherSettings.FEATURE_AI)
    FoldOsSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        contentPadding = PaddingValues(start = 18.dp, end = 14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "Search",
                modifier = Modifier.size(22.dp),
                tint = tokens.onSurface,
            )
            Text(
                text = searchPlaceholder(settings),
                modifier = Modifier.weight(1f),
                color = tokens.onSurface.copy(alpha = 0.7f),
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (showAi) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = tokens.accent,
                )
            }
            if (showLens) {
                Icon(
                    imageVector = Icons.Rounded.CenterFocusWeak,
                    contentDescription = "Visual search",
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { SearchActions.visualSearch(context, "") },
                    tint = tokens.onSurface,
                )
            }
        }
    }
}

/** "Search apps, contacts, web…" from the enabled providers; "Search" when none is on. */
internal fun searchPlaceholder(settings: LauncherSettings): String {
    val names = ArrayList<String>()
    if (settings.searchProvider(LauncherSettings.SEARCH_APPS)) names += "apps"
    if (settings.searchProvider(LauncherSettings.SEARCH_CONTACTS)) names += "contacts"
    if (settings.searchProvider(LauncherSettings.SEARCH_SETTINGS)) names += "settings"
    if (settings.searchProvider(LauncherSettings.SEARCH_FILES)) names += "files"
    if (settings.searchProvider(LauncherSettings.SEARCH_CALENDAR)) names += "events"
    if (settings.webSearchOn()) names += "web"
    if (names.isEmpty()) {
        return when {
            settings.searchProvider(LauncherSettings.SEARCH_CALCULATOR) -> "Calculate"
            settings.searchProvider(LauncherSettings.SEARCH_UNITS) -> "Convert units"
            else -> "Search"
        }
    }
    val shown = names.take(3)
    val suffix = if (names.size > 3) "…" else ""
    return "Search " + shown.joinToString(", ") + suffix
}
