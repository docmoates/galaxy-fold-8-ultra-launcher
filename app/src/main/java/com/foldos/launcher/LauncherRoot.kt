package com.foldos.launcher

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.core.fold.FoldState
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.DockBackground
import com.foldos.launcher.core.model.DockPosition
import com.foldos.launcher.core.model.DrawerStyle
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.dock.Dock
import com.foldos.launcher.dock.DockDefaults
import com.foldos.launcher.drawer.AppDrawer
import com.foldos.launcher.drawer.rememberDrawerBackdropModifier
import com.foldos.launcher.fold.effectiveScreen
import com.foldos.launcher.fold.rememberFoldState
import com.foldos.launcher.home.HomeScreen
import com.foldos.launcher.settings.SettingsActivity
import com.foldos.launcher.theme.FoldOsSurface
import com.foldos.launcher.theme.LocalFoldOsTokens
import com.foldos.launcher.theme.Wallpaper
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything inside the launcher window: wallpaper, [HomeScreen] with the dock, search bar and hub
 * page, the rail dock when configured, and the [AppDrawer] on top. Must be composed inside
 * `FoldOsTheme`.
 */
@Composable
fun LauncherRoot(viewModel: LauncherViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val drawerOpen by viewModel.drawerOpen.collectAsStateWithLifecycle()
    val homeRequest by viewModel.homeRequests.collectAsStateWithLifecycle()

    val fold: FoldState = rememberFoldState()
    val screen: ScreenKind = effectiveScreen(settings, fold)
    val pages by viewModel.pages(screen).collectAsStateWithLifecycle()

    LaunchedEffect(screen) { viewModel.ensureDefaultLayout(screen) }

    val dockConfig = settings.dockFor(screen)
    val dockOn = settings.feature(LauncherSettings.FEATURE_DOCK)
    val railDock = dockOn && dockConfig.pos == DockPosition.RAIL
    val pinned = remember(apps, dockConfig, screen) {
        defaultDockApps(apps, DockDefaults.pinnedCount(dockConfig, screen) + DockDefaults.FOLDER_SIZE)
    }
    val recents: List<AppInfo> = remember { emptyList() }

    val onLaunch: (AppInfo) -> Unit = { app -> viewModel.launch(app) }
    val openDrawer: () -> Unit = { viewModel.openDrawer() }
    val openSettings: () -> Unit = {
        context.startActivity(Intent(context, SettingsActivity::class.java))
    }

    // Back on the home screen returns to the default page. While the drawer is open its own
    // BackHandler (composed later, so higher priority) closes it instead.
    BackHandler(enabled = !drawerOpen) { viewModel.goHome() }

    val backdrop = rememberDrawerBackdropModifier(
        visible = drawerOpen,
        blur = settings.popBlur && settings.drawerStyle == DrawerStyle.POPUP,
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().then(backdrop)) {
            Wallpaper()
            HomeScreen(
                screen = screen,
                fold = fold,
                settings = settings,
                pages = pages,
                itemsForPage = viewModel.graph.layouts::items,
                apps = apps,
                onLaunch = onLaunch,
                onOpenDrawer = openDrawer,
                onLongPressHome = openSettings,
                dock = {
                    if (dockOn && !railDock) {
                        val sideMargin = when (dockConfig.bg) {
                            DockBackground.SHELF, DockBackground.LINE -> 0.dp
                            else -> 16.dp
                        }
                        Dock(
                            config = dockConfig,
                            screen = screen,
                            pinned = pinned,
                            recents = recents,
                            settings = settings,
                            onLaunch = onLaunch,
                            onOpenDrawer = openDrawer,
                            onSearch = openDrawer,
                            modifier = Modifier.padding(start = sideMargin, end = sideMargin, bottom = 6.dp),
                        )
                    }
                },
                searchBar = if (settings.feature(LauncherSettings.FEATURE_SEARCH)) {
                    { HomeSearchBar(showAi = settings.feature(LauncherSettings.FEATURE_AI), onClick = openDrawer) }
                } else {
                    null
                },
                hub = if (settings.hubOn) {
                    { HubTodayPage(settings = settings, fold = fold) }
                } else {
                    null
                },
                homeRequest = homeRequest,
            )
            if (railDock) {
                Dock(
                    config = dockConfig,
                    screen = screen,
                    pinned = pinned,
                    recents = recents,
                    settings = settings,
                    onLaunch = onLaunch,
                    onOpenDrawer = openDrawer,
                    onSearch = openDrawer,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .padding(end = 10.dp),
                )
            }
        }
        AppDrawer(
            visible = drawerOpen,
            screen = fold.kind,
            settings = settings,
            apps = apps,
            onLaunch = onLaunch,
            onDismiss = { viewModel.closeDrawer() },
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Search bar stub                                                                             */
/* ------------------------------------------------------------------------------------------ */

/** Pill search bar at the top of each home page (mock-up `.srch`). Tapping opens the drawer. */
@Composable
fun HomeSearchBar(showAi: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(25.dp)
    FoldOsSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(shape)
            .clickable(onClick = onClick),
        shape = shape,
        contentPadding = PaddingValues(horizontal = 18.dp),
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
                text = "Search apps",
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
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Hub placeholder                                                                             */
/* ------------------------------------------------------------------------------------------ */

/**
 * Phase 1 hub: the Today tab with a greeting, the date and placeholder briefing cards, plus the
 * tab bar on the configured side. Inner screen splits the cards into two panes.
 */
@Composable
fun HubTodayPage(settings: LauncherSettings, fold: FoldState, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val now = rememberHubClock()
    val inner = fold.kind != ScreenKind.COVER
    val tabBarOnTop = settings.hubBar == LauncherSettings.HUB_BAR_TOP

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (tabBarOnTop) HubTabBar(settings)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = greetingFor(now.hour),
                color = tokens.onSurface,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = now.format(DateTimeFormatter.ofPattern("EEEE, MMM d", Locale.getDefault())),
                color = tokens.onSurface.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (inner) {
            Row(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HubBriefingCard(Modifier.fillMaxWidth())
                    HubCard("Next event", "Nothing scheduled", "Calendar connects in phase 2", Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HubCard("Tasks", "0 due today", "Todoist and Google Tasks arrive later", Modifier.fillMaxWidth())
                    HubCard("Mail", "Inbox is quiet", "Gmail connects in phase 2", Modifier.fillMaxWidth())
                    HubCard("People", "No one to reach", "Missed calls and meetings show here", Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HubBriefingCard(Modifier.fillMaxWidth())
                HubCard("Next event", "Nothing scheduled", "Calendar connects in phase 2", Modifier.fillMaxWidth())
                HubCard("Tasks", "0 due today", "Todoist and Google Tasks arrive later", Modifier.fillMaxWidth())
                HubCard("Mail", "Inbox is quiet", "Gmail connects in phase 2", Modifier.fillMaxWidth())
            }
        }
        if (!tabBarOnTop) HubTabBar(settings)
    }
}

@Composable
private fun HubBriefingCard(modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    FoldOsSurface(
        modifier = modifier,
        color = tokens.highlight,
        borderColor = Color.Transparent,
        contentPadding = PaddingValues(18.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(
                    imageVector = Icons.Rounded.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = tokens.onHighlight,
                )
                Text(
                    text = "TODAY",
                    color = tokens.onHighlight.copy(alpha = 0.8f),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
            }
            Text(
                text = "Your morning briefing will appear here once calendar, mail and tasks are connected.",
                color = tokens.onHighlight,
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

@Composable
private fun HubCard(title: String, body: String, caption: String, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    FoldOsSurface(modifier = modifier, contentPadding = PaddingValues(horizontal = 18.dp, vertical = 14.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title.uppercase(Locale.getDefault()),
                color = tokens.onSurface.copy(alpha = 0.72f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Text(
                text = body,
                color = tokens.onSurface,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = caption,
                color = tokens.onSurface.copy(alpha = 0.7f),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Today plus the enabled tabs (mock-up `HUBTABS`); Today is selected, the others are stubs. */
@Composable
private fun HubTabBar(settings: LauncherSettings) {
    val tokens = LocalFoldOsTokens.current
    val tabs = ArrayList<Triple<String, String, ImageVector>>(5)
    tabs.add(Triple("today", "Today", Icons.Rounded.WbSunny))
    if (LauncherSettings.HUB_TAB_AI in settings.hubTabs) tabs.add(Triple(LauncherSettings.HUB_TAB_AI, "AI", Icons.Rounded.AutoAwesome))
    if (LauncherSettings.HUB_TAB_MAIL in settings.hubTabs) tabs.add(Triple(LauncherSettings.HUB_TAB_MAIL, "Mail", Icons.Rounded.Mail))
    if (LauncherSettings.HUB_TAB_CAL in settings.hubTabs) tabs.add(Triple(LauncherSettings.HUB_TAB_CAL, "Calendar", Icons.Rounded.CalendarMonth))
    if (LauncherSettings.HUB_TAB_PEOPLE in settings.hubTabs) tabs.add(Triple(LauncherSettings.HUB_TAB_PEOPLE, "Contacts", Icons.Rounded.Person))

    FoldOsSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(tokens.radius + 6.dp),
        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            tabs.forEach { (id, name, icon) ->
                val selected = id == "today"
                val tint = if (selected) tokens.accent else tokens.onSurface.copy(alpha = 0.7f)
                Column(
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(imageVector = icon, contentDescription = name, modifier = Modifier.size(22.dp), tint = tint)
                    Text(
                        text = name,
                        color = tint,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun greetingFor(hour: Int): String = when {
    hour < 5 -> "Good night"
    hour < 12 -> "Good morning"
    hour < 17 -> "Good afternoon"
    else -> "Good evening"
}

/** Wall clock that recomposes once a minute. */
@Composable
private fun rememberHubClock(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            val millisToNextMinute = (60 - now.second) * 1000L - (now.nano / 1_000_000L)
            delay(millisToNextMinute.coerceIn(250L, 60_000L))
        }
    }
    return now
}

/* ------------------------------------------------------------------------------------------ */
/* Default dock contents                                                                       */
/* ------------------------------------------------------------------------------------------ */

/** Package name fragments tried in order to pick sensible dock apps on a fresh install. */
private val DOCK_PRIORITY: List<List<String>> = listOf(
    listOf("dialer", "contacts.phone", ".phone"),
    listOf("messaging", "messages", ".mms"),
    listOf("chrome", "sbrowser", "browser"),
    listOf("camera"),
    listOf("photos", "gallery"),
    listOf("gmail", "email"),
    listOf("maps"),
    listOf("youtube"),
    listOf("spotify", "music"),
    listOf("calendar"),
    listOf("vending", "galaxyapps", "samsungapps"),
    listOf("android.settings"),
)

/**
 * Picks [count] apps for the dock until the user pins their own: well-known apps first (phone,
 * messages, browser, camera, ...), then the alphabetical list.
 */
fun defaultDockApps(apps: List<AppInfo>, count: Int): List<AppInfo> {
    if (count <= 0 || apps.isEmpty()) return emptyList()
    val picked = ArrayList<AppInfo>(count)
    val usedKeys = HashSet<String>()
    for (keywords in DOCK_PRIORITY) {
        if (picked.size >= count) break
        val match = apps.firstOrNull { app ->
            app.key !in usedKeys && keywords.any { app.packageName.lowercase(Locale.ROOT).contains(it) }
        }
        if (match != null) {
            picked.add(match)
            usedKeys.add(match.key)
        }
    }
    for (app in apps) {
        if (picked.size >= count) break
        if (usedKeys.add(app.key)) picked.add(app)
    }
    return picked
}
