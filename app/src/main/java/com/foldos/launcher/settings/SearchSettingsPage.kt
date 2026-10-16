package com.foldos.launcher.settings

import android.widget.Toast
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.search.SearchIndex
import com.foldos.launcher.search.WebSearch
import kotlinx.coroutines.launch

/** Settings > Search (PLAN.md 5.1): engine, providers, history. Owned by the Search agent. */
object SearchRows {
    /** Navigation route of this page; not in `SettingsRoutes` because that file belongs to Settings. */
    const val ROUTE: String = "search"

    val search = SettingsRowInfo("Universal search", "Swipe down or tap the search bar; providers below")
    val engine = SettingsRowInfo("Web search engine", "Google, Bing, DuckDuckGo, Kagi, Brave or Ecosia")
    val apps = SettingsRowInfo("Apps and shortcuts", "Installed apps and their deep-link shortcuts")
    val contacts = SettingsRowInfo("Contacts", "Call, message or open a contact")
    val settingsProvider = SettingsRowInfo("Launcher settings", "Every FoldOS setting by name")
    val files = SettingsRowInfo("Files", "Recent photos, videos, audio and documents by name")
    val calendar = SettingsRowInfo("Calendar", "Events in the next 30 days")
    val web = SettingsRowInfo("Web", "Search the web, open URLs, visual search; bangs like !g !ddg !yt !w !maps !amazon")
    val calculator = SettingsRowInfo("Calculator", "Type an expression: (12+8)*3, 2^10, 15% of 80")
    val units = SettingsRowInfo("Unit conversion", "5 km to mi, 72 f to c, 100 usd to eur (sample rates)")
    val ranking = SettingsRowInfo("Usage ranking", "Results you pick often move up; recent picks count more", advanced = true)
    val clearHistory = SettingsRowInfo("Clear search history", "Forgets recent searches and result ranking")

    val page = SettingsPageInfo(
        route = ROUTE,
        title = "Search",
        subtitle = "Engine, providers, calculator, units, history",
        icon = Icons.Outlined.Search,
        rows = listOf(search, engine, apps, contacts, settingsProvider, files, calendar, web, calculator, units, ranking, clearHistory),
    )

    /** Provider toggles in result-section order: settings key to row. */
    val providerRows: List<Pair<String, SettingsRowInfo>> = listOf(
        LauncherSettings.SEARCH_APPS to apps,
        LauncherSettings.SEARCH_CONTACTS to contacts,
        LauncherSettings.SEARCH_SETTINGS to settingsProvider,
        LauncherSettings.SEARCH_FILES to files,
        LauncherSettings.SEARCH_CALENDAR to calendar,
        LauncherSettings.SEARCH_WEB to web,
        LauncherSettings.SEARCH_CALCULATOR to calculator,
        LauncherSettings.SEARCH_UNITS to units,
    )
}

@Composable
fun SearchSettingsPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val on = settings.feature(LauncherSettings.FEATURE_SEARCH)
    SettingsPageScaffold(title = SearchRows.page.title, onBack = onBack) {
        ToggleRow(
            info = SearchRows.search,
            checked = on,
            onCheckedChange = { enabled -> update { it.withFeature(LauncherSettings.FEATURE_SEARCH, enabled) } },
        )
        SelectRow(
            info = SearchRows.engine,
            value = settings.searchEngine,
            options = WebSearch.engineOptions,
            onSelect = { engine -> update { it.copy(searchEngine = engine) } },
            visible = on && settings.searchProvider(LauncherSettings.SEARCH_WEB),
        )
        if (on) SectionHeader(text = "Providers")
        SearchRows.providerRows.forEach { (key, info) ->
            ToggleRow(
                info = info,
                checked = settings.searchProvider(key),
                onCheckedChange = { enabled -> update { it.withSearchProvider(key, enabled) } },
                visible = on,
            )
        }
        if (on) SectionHeader(text = "History")
        StaticRow(info = SearchRows.ranking, visible = on, showChevron = false, trailingText = "On")
        StaticRow(
            info = SearchRows.clearHistory,
            visible = on,
            showChevron = false,
            onClick = {
                val scope = FoldOsApplication.from(context).graph.appScope
                scope.launch { runCatching { SearchIndex.clearHistory(context) } }
                Toast.makeText(context, "Search history cleared", Toast.LENGTH_SHORT).show()
            },
        )
    }
}
