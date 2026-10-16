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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Every settings page, in the order the root list shows them. */
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
        ProfilesRows.page,
        AiRows.page,
        FeaturesRows.page,
        AboutRows.page,
    )

    fun byRoute(route: String): SettingsPageInfo? = all.firstOrNull { it.route == route }
}

private val advancedRow = SettingsRowInfo("Advanced", "Show every option on every page")

/**
 * Root of Settings: search field, one row per page, the Advanced switch. With a query, the list is
 * replaced by matching rows from all pages; tapping one opens its page (and switches Advanced on
 * when the row needs it).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRootPage(
    query: String,
    onQueryChange: (String) -> Unit,
    advanced: Boolean,
    onAdvancedChange: (Boolean) -> Unit,
    onOpen: (route: String) -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Settings") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
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
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text(text = "Search settings") },
                leadingIcon = {
                    Icon(imageVector = Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
            )

            if (query.isBlank()) {
                SettingsPages.all.forEach { page ->
                    if (page.route == SettingsRoutes.ABOUT) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        AdvancedSwitchRow(advanced = advanced, onAdvancedChange = onAdvancedChange)
                    }
                    PageRow(page = page, onClick = { onOpen(page.route) })
                }
            } else {
                SearchResults(query = query, advanced = advanced, onAdvancedChange = onAdvancedChange, onOpen = onOpen)
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
            .clickable { onClick() },
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

@Composable
private fun SearchResults(
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
            supportingContent = { Text(text = "Try another word, or browse the pages.") },
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
