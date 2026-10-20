package com.foldos.launcher.hub

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.a11y.a11yButton
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.drawer.DrawerAppIcon
import com.foldos.launcher.settings.SettingsActivity
import com.foldos.launcher.theme.FoldOsSurface
import com.foldos.launcher.theme.LocalFoldOsTokens

/**
 * The Hub's Launch tab (1.7.0), for people who keep the Pixel or One UI launcher as their home screen and
 * open FoldOS only for the Hub. Like the other tabs it never scrolls as a page: a search field, a Pinned row
 * and a Recent row (each only while the height leaves room for a useful list below) sit above a rounded box
 * holding every launchable app, and only that box scrolls. Tap launches an app; long-press offers pin / unpin
 * and App info. The search field filters the box live, the keyboard's search key launches the first match,
 * and a query with no match offers a web search. Inner screen: apps on the left 55%, Pinned and Recent as lists
 * on the right.
 */
@Composable
internal fun HubLaunchTab(
    screen: ScreenKind,
    settings: LauncherSettings,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val fontScale = LocalDensity.current.fontScale
    val store = remember(context) { HubLaunchStore(context) }
    val apps by remember(graph) { graph.apps.apps }.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val inner = screen != ScreenKind.COVER

    val byKey = remember(apps) { apps.associateBy { it.key } }
    val pinned = remember(apps, store.pinned) { store.pinned.mapNotNull { byKey[it] } }
    val recent = remember(apps, store.recent) { store.recent.mapNotNull { byKey[it] } }
    val shown = remember(apps, query) { filterLaunchApps(apps, query) }

    val open: (AppInfo) -> Unit = { app ->
        store.recordLaunch(app.key)
        graph.apps.launch(app)
    }
    val actions = LaunchActions(
        open = open,
        isPinned = store::isPinned,
        togglePin = { store.togglePin(it.key) },
        info = { app -> launchAppInfo(context, app) },
    )

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = HubPagePadding),
    ) {
        val gap = HubFitGap
        val barH = HubSlimBarHeight.scaledBy(fontScale)
        val searchH = 50.dp
        val kickerH = 22.dp.scaledBy(fontScale)
        val cellH = LaunchCellHeight.scaledBy(fontScale)
        val slots = (maxWidth / LaunchCellWidth).toInt().coerceIn(4, 6)
        val searching = query.isNotBlank()

        // Cover: a Pinned / Recent row is shown only while at least MinGridHeight stays for the app box.
        val left = maxHeight - barH - searchH - gap * 2
        val rowSectionH = kickerH + cellH + gap
        val showPinned = !inner && !searching && left >= rowSectionH + MinGridHeight
        val showRecent = !inner && !searching && recent.isNotEmpty() &&
            left >= (if (showPinned) rowSectionH * 2 else rowSectionH) + MinGridHeight

        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(gap)) {
            HubSlimBar(title = "Launch") {
                HubIconButton(
                    icon = Icons.Rounded.Settings,
                    contentDescription = "FoldOS settings",
                    onClick = { context.startActivity(SettingsActivity.intent(context)) },
                    size = 40.dp,
                    tinted = false,
                )
            }
            HubSearchPill(
                value = query,
                onValueChange = { query = it },
                placeholder = if (apps.isEmpty()) "Search apps" else "Search ${apps.size} apps",
                onSearch = {
                    val first = shown.firstOrNull()
                    if (first != null) open(first) else if (searching) searchWeb(context, query)
                },
            )
            if (showPinned) {
                Column {
                    HubKicker("Pinned", Modifier.height(kickerH))
                    if (pinned.isEmpty()) {
                        Box(Modifier.height(cellH).fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                            HubEmptyRow("Long-press any app to pin it here")
                        }
                    } else {
                        AppRow(pinned.take(slots), slots, actions)
                    }
                }
            }
            if (showRecent) {
                Column {
                    HubKicker("Recent", Modifier.height(kickerH))
                    AppRow(recent.take(slots), slots, actions)
                }
            }
            if (inner) {
                Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    AllAppsBox(shown, query, actions, Modifier.weight(0.55f).fillMaxHeight())
                    if (!searching) {
                        SideLists(pinned, recent, actions, Modifier.weight(0.45f).fillMaxHeight(), kickerH)
                    }
                }
            } else {
                AllAppsBox(shown, query, actions, Modifier.weight(1f).fillMaxWidth())
            }
        }
    }
}

/** What a tap or long-press on an app does; shared by every cell and row of the tab. */
private class LaunchActions(
    val open: (AppInfo) -> Unit,
    val isPinned: (String) -> Boolean,
    val togglePin: (AppInfo) -> Unit,
    val info: (AppInfo) -> Unit,
)

/** Apps whose label starts with [query] first, then those that contain it (case-insensitive); all apps for a blank query. */
internal fun filterLaunchApps(apps: List<AppInfo>, query: String): List<AppInfo> {
    val q = query.trim()
    if (q.isEmpty()) return apps
    val (starts, rest) = apps.filter { it.label.contains(q, ignoreCase = true) }
        .partition { it.label.startsWith(q, ignoreCase = true) }
    return starts + rest
}

/** One row of [slots] equally wide app cells, left aligned. */
@Composable
private fun AppRow(apps: List<AppInfo>, slots: Int, actions: LaunchActions) {
    Row(Modifier.fillMaxWidth()) {
        repeat(slots) { index ->
            Box(Modifier.weight(1f)) {
                apps.getOrNull(index)?.let { LaunchAppCell(it, actions) }
            }
        }
    }
}

/** The rounded box with every app (or the matches of [query]); the only part of the tab that scrolls. */
@Composable
private fun AllAppsBox(apps: List<AppInfo>, query: String, actions: LaunchActions, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    FoldOsSurface(modifier = modifier) {
        when {
            apps.isNotEmpty() -> LazyVerticalGrid(
                columns = GridCells.Adaptive(LaunchCellWidth),
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
            ) {
                items(apps, key = { it.key + it.user.hashCode() }) { app -> LaunchAppCell(app, actions) }
            }
            query.isNotBlank() -> Column(Modifier.fillMaxWidth()) {
                HubEmptyRow("No apps match")
                LaunchRow(
                    label = "Search the web for “${query.trim()}”",
                    icon = { WebIcon() },
                    onClick = { searchWeb(context, query) },
                )
            }
            else -> HubEmptyRow("No apps found")
        }
    }
}

/** Inner screen, right pane: Pinned and Recent as icon + name rows, split by the height that is left. */
@Composable
private fun SideLists(
    pinned: List<AppInfo>,
    recent: List<AppInfo>,
    actions: LaunchActions,
    modifier: Modifier,
    kickerH: Dp,
) {
    BoxWithConstraints(modifier) {
        val rowsLeft = fitCount(maxHeight - kickerH * 2 - HubFitGap * 2, LaunchRowHeight)
        val recentRows = minOf(recent.size, rowsLeft / 2)
        val pinnedRows = minOf(maxOf(pinned.size, 1), rowsLeft - recentRows)
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(HubFitGap)) {
            Column {
                HubKicker("Pinned", Modifier.height(kickerH))
                HubListCard(Modifier.fillMaxWidth()) {
                    if (pinned.isEmpty()) HubEmptyRow("Long-press an app to pin it")
                    pinned.take(pinnedRows).forEachIndexed { index, app ->
                        if (index > 0) HubRowDivider()
                        LaunchAppRow(app, actions)
                    }
                }
            }
            if (recentRows > 0) {
                Column {
                    HubKicker("Recent", Modifier.height(kickerH))
                    HubListCard(Modifier.fillMaxWidth()) {
                        recent.take(recentRows).forEachIndexed { index, app ->
                            if (index > 0) HubRowDivider()
                            LaunchAppRow(app, actions)
                        }
                    }
                }
            }
        }
    }
}

/** An icon over a one-line label; tap launches, long-press opens the pin / App info menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LaunchAppCell(app: AppInfo, actions: LaunchActions) {
    val tokens = LocalFoldOsTokens.current
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .a11yButton(
                    label = app.label,
                    onActivate = { actions.open(app) },
                    onMenu = { menu = true },
                    menuLabel = "App options",
                )
                .combinedClickable(onClick = { actions.open(app) }, onLongClick = { menu = true })
                .padding(horizontal = 2.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            DrawerAppIcon(app = app, size = 48.dp)
            Text(
                text = app.label,
                color = tokens.onSurface,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        AppMenu(app, actions, menu) { menu = false }
    }
}

/** An icon + name row for the inner screen's side lists; same gestures as [LaunchAppCell]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LaunchAppRow(app: AppInfo, actions: LaunchActions) {
    var menu by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        LaunchRow(
            label = app.label,
            icon = { DrawerAppIcon(app = app, size = 36.dp) },
            onClick = { actions.open(app) },
            onLongClick = { menu = true },
        )
        AppMenu(app, actions, menu) { menu = false }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LaunchRow(
    label: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(LaunchRowHeight)
            .a11yButton(label = label, onActivate = onClick, onMenu = onLongClick)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        icon()
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = tokens.onSurface,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun WebIcon() {
    val tokens = LocalFoldOsTokens.current
    Icon(Icons.Rounded.Public, contentDescription = null, modifier = Modifier.size(36.dp), tint = tokens.accent)
}

@Composable
private fun AppMenu(app: AppInfo, actions: LaunchActions, expanded: Boolean, onDismiss: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = { Text(if (actions.isPinned(app.key)) "Unpin" else "Pin") },
            onClick = { actions.togglePin(app); onDismiss() },
        )
        DropdownMenuItem(
            text = { Text("App info") },
            onClick = { actions.info(app); onDismiss() },
        )
    }
}

/** The system App info page of [app]. */
private fun launchAppInfo(context: Context, app: AppInfo) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", app.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** A web search for [query] in the default browser. */
private fun searchWeb(context: Context, query: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/search?q=" + Uri.encode(query.trim())))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private val LaunchCellWidth: Dp = 72.dp
private val LaunchCellHeight: Dp = 76.dp
private val LaunchRowHeight: Dp = 52.dp

/** The app box keeps at least this much height before a Pinned / Recent row may take space from it. */
private val MinGridHeight: Dp = 220.dp
