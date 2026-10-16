package com.foldos.launcher.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.a11yHeading

/** Every detailed settings page, in the order "More settings" shows them (also the settings search index). */
object SettingsPages {
    val all: List<SettingsPageInfo> = listOf(
        QuickSetupRows.page,
        ScreensRows.page,
        HomeRows.page,
        DockRows.page,
        HubRows.page,
        LookRows.page,
        WidgetsRows.page,
        GesturesRows.page,
        SearchRows.page,
        HiddenAppsRows.page,
        MailSettingsRows.page,
        AiRows.page,
        BackupRows.page,
        AccessibilityRows.page,
        FeaturesRows.page,
        AboutRows.page,
    )

    fun byRoute(route: String): SettingsPageInfo? = all.firstOrNull { it.route == route }
}

private val advancedRow = SettingsRowInfo("Advanced", "Show every option on every page")

/**
 * "More settings": the detailed pages (the former Settings root list) with the Advanced switch. The
 * sample-based home ([SettingsHomePage]) links here; everything that is a toggle, slider, dropdown or
 * hex field lives on these pages.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreSettingsPage(
    advanced: Boolean,
    onAdvancedChange: (Boolean) -> Unit,
    onOpen: (route: String) -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "More settings", modifier = Modifier.a11yHeading()) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsPages.all.forEach { page ->
                if (page.route == SettingsRoutes.ABOUT) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    AdvancedSwitchRow(advanced = advanced, onAdvancedChange = onAdvancedChange)
                }
                PageRow(page = page, onClick = { onOpen(page.route) })
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AdvancedSwitchRow(advanced: Boolean, onAdvancedChange: (Boolean) -> Unit) {
    // Always visible: the row filter must not hide the switch that controls it.
    CompositionLocalProvider(LocalSettingsRowFilter provides SettingsRowFilter()) {
        ToggleRow(
            info = advancedRow,
            checked = advanced,
            onCheckedChange = onAdvancedChange,
        )
    }
}

@Composable
private fun PageRow(page: SettingsPageInfo, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onClick() },
        leadingContent = {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        headlineContent = { Text(text = page.title) },
        supportingContent = { Text(text = page.subtitle) },
    )
}

/**
 * Rows of every detailed page matching [query]; tapping one opens its page (and switches Advanced on
 * when the row needs it). Used by the search in [SettingsHomePage].
 */
@Composable
internal fun SearchResults(
    query: String,
    advanced: Boolean,
    onAdvancedChange: (Boolean) -> Unit,
    onOpen: (route: String) -> Unit,
) {
    val matching = SettingsPages.all.map { page -> page to page.rows.filter { it.matches(query) } }
        .filter { it.second.isNotEmpty() }
    if (matching.isEmpty()) {
        ListItem(
            leadingContent = { Icon(imageVector = Icons.Outlined.Tune, contentDescription = null) },
            headlineContent = { Text(text = "No settings match \"$query\"") },
            supportingContent = { Text(text = "Try another word, or browse More settings.") },
        )
        return
    }
    // Search shows advanced rows too; opening one turns Advanced on so the row is visible.
    CompositionLocalProvider(LocalSettingsRowFilter provides SettingsRowFilter(query = query, advanced = true)) {
        matching.forEach { (page, rows) ->
            SectionHeader(text = page.title)
            rows.forEach { row ->
                StaticRow(
                    info = row,
                    onClick = {
                        if (row.advanced && !advanced) onAdvancedChange(true)
                        onOpen(page.route)
                    },
                    trailingText = if (row.advanced) "Advanced" else null,
                )
            }
        }
    }
}
