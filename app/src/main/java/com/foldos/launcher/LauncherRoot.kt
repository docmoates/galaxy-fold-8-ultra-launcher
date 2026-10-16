package com.foldos.launcher

import android.widget.Toast
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.model.WidgetEntry
import com.foldos.launcher.dock.Dock
import com.foldos.launcher.dock.DockDefaults
import com.foldos.launcher.drawer.AppDrawer
import com.foldos.launcher.edit.EditModeOverlay
import com.foldos.launcher.edit.ThemeQuickSheet
import com.foldos.launcher.edit.WallpaperSheet
import com.foldos.launcher.fold.effectiveScreen
import com.foldos.launcher.fold.rememberFoldState
import com.foldos.launcher.home.HomeCallbacks
import com.foldos.launcher.home.HomeScreen
import com.foldos.launcher.home.LocalHomeCallbacks
import com.foldos.launcher.home.rememberGridGeometry
import com.foldos.launcher.onboarding.SetupWizard
import com.foldos.launcher.picker.WidgetPicker
import com.foldos.launcher.picker.rememberWidgetPickerBackdropModifier
import com.foldos.launcher.settings.SettingsActivity
import com.foldos.launcher.settings.SettingsRoutes
import com.foldos.launcher.theme.FoldOsSurface
import com.foldos.launcher.theme.LocalFoldOsTokens
import com.foldos.launcher.theme.Wallpaper
import com.foldos.launcher.widgets.AddAppWidgetResult
import com.foldos.launcher.widgets.WidgetSizing
import com.foldos.launcher.widgets.rememberAddAppWidgetFlow
import com.foldos.launcher.widgets.rememberWidgetHostController
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Everything inside the launcher window. Until the first-run wizard has finished it shows the
 * [SetupWizard] full screen; afterwards: wallpaper, [HomeScreen] with the dock, search bar and hub
 * page, the rail dock when configured, the live-edit overlay with its quick sheets, the widget
 * picker and the [AppDrawer] on top. Must be composed inside `FoldOsTheme`.
 */
@Composable
fun LauncherRoot(viewModel: LauncherViewModel, modifier: Modifier = Modifier) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val settingsLoaded by viewModel.settingsLoaded.collectAsStateWithLifecycle()

    when {
        // Until DataStore has answered, only the wallpaper: composing the home here would seed the
        // layout store with the default styles before the wizard has picked the real ones (and flash
        // a default-themed home in front of the wizard).
        !settingsLoaded -> Box(modifier.fillMaxSize()) { Wallpaper() }
        !settings.wizardDone -> {
            // First run: every wizard tap writes straight to the settings; Finish / Skip set
            // wizardDone, which switches this branch over to the home.
            SetupWizard(
                settings = settings,
                onUpdate = { transform -> viewModel.updateSettings(transform) },
                onFinish = { },
                modifier = modifier.fillMaxSize(),
            )
        }
        else -> LauncherHome(viewModel = viewModel, settings = settings, modifier = modifier)
    }
}

/** Remembers the spans picked for an app widget until its bind / configure flow delivers the id. */
private class PendingWidgetSpans(var spanX: Int = 1, var spanY: Int = 1)

@Composable
private fun LauncherHome(viewModel: LauncherViewModel, settings: LauncherSettings, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val drawerOpen by viewModel.drawerOpen.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val pickerOpen by viewModel.widgetPickerOpen.collectAsStateWithLifecycle()
    val homeRequest by viewModel.homeRequests.collectAsStateWithLifecycle()
    val currentPageId by viewModel.currentPageId.collectAsStateWithLifecycle()
    val pageSelectRequest by viewModel.pageSelectRequest.collectAsStateWithLifecycle()

    val fold: FoldState = rememberFoldState()
    val screen: ScreenKind = effectiveScreen(settings, fold)
    val pages by viewModel.pages(screen).collectAsStateWithLifecycle()

    // Seed only once the wizard has chosen the style / columns the seeded pages are laid out for.
    LaunchedEffect(screen, settings.wizardDone) {
        if (settings.wizardDone) viewModel.ensureDefaultLayout(screen)
    }

    // The page edit mode and the picker work on: the settled pager page, else the first page.
    val sortedPages = remember(pages) { pages.sortedBy { it.index } }
    val editPageId: Long? = currentPageId?.takeIf { id -> pages.any { it.id == id } } ?: sortedPages.firstOrNull()?.id
    val editPageStyle: HomeStyle? = pages.firstOrNull { it.id == editPageId }?.style
    val editItemsFlow = remember(editPageId, viewModel) {
        if (editPageId != null) viewModel.graph.layouts.items(editPageId) else flowOf<List<HomeItem>>(emptyList())
    }
    val editItems by editItemsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val geometry = rememberGridGeometry(screen, settings, fold)
    val stacksOn = settings.feature(LauncherSettings.FEATURE_STACKS)

    val dockConfig = settings.dockFor(screen)
    val dockOn = settings.feature(LauncherSettings.FEATURE_DOCK)
    val railDock = dockOn && dockConfig.pos == DockPosition.RAIL
    val pinned = remember(apps, dockConfig, screen) {
        defaultDockApps(apps, DockDefaults.pinnedCount(dockConfig, screen) + DockDefaults.FOLDER_SIZE)
    }
    val recents: List<AppInfo> = remember { emptyList() }

    val onLaunch: (AppInfo) -> Unit = { app -> viewModel.launch(app) }
    val openDrawer: () -> Unit = { viewModel.openDrawer() }
    val openSettings: (String?) -> Unit = { route ->
        context.startActivity(SettingsActivity.intent(context, route))
    }
    val enterEdit: () -> Unit = { viewModel.enterEditMode() }
    val homeCallbacks = remember(viewModel) {
        HomeCallbacks(
            onItemLongPress = { viewModel.enterEditMode() },
            onEmptyLongPress = { _, _, _ -> viewModel.enterEditMode() },
        )
    }

    // Back on the home screen returns to the default page. The drawer, the picker and the edit
    // overlay each have their own BackHandler (composed later, so higher priority).
    BackHandler(enabled = !drawerOpen && !pickerOpen && !editMode) { viewModel.goHome() }

    // Widget picker -> app widget: allocate, bind (system permission dialog when needed), configure
    // through MainActivity.onActivityResult, then place the item. Built-ins are placed directly.
    val widgetHost = rememberWidgetHostController()
    val pendingSpans = remember { PendingWidgetSpans() }
    val packageManager = context.packageManager
    val addAppWidget = rememberAddAppWidgetFlow(widgetHost) { result ->
        when (result) {
            is AddAppWidgetResult.Ready -> {
                val pageId = editPageId
                if (pageId == null) {
                    widgetHost.deleteId(result.appWidgetId)
                    Toast.makeText(context, "No home page to add the widget to", Toast.LENGTH_SHORT).show()
                } else {
                    val label = runCatching { result.provider.loadLabel(packageManager) }.getOrNull()
                        ?: result.provider.provider.packageName
                    viewModel.addAppWidget(
                        appWidgetId = result.appWidgetId,
                        title = label,
                        spanX = pendingSpans.spanX,
                        spanY = pendingSpans.spanY,
                        pageId = pageId,
                        cols = geometry.cols,
                        rows = geometry.rows,
                        pageStyle = editPageStyle,
                    )
                }
            }
            is AddAppWidgetResult.Cancelled -> Toast.makeText(context, result.reason, Toast.LENGTH_SHORT).show()
        }
    }
    val onPickWidget: (WidgetEntry) -> Unit = { entry ->
        val pageId = editPageId
        val spanX = entry.defaultSpanX.coerceIn(1, geometry.cols.coerceAtLeast(1))
        val spanY = entry.defaultSpanY.coerceIn(1, geometry.rows.coerceAtLeast(1))
        when {
            pageId == null -> Toast.makeText(context, "No home page to add the widget to", Toast.LENGTH_SHORT).show()
            entry.isBuiltIn -> viewModel.addBuiltInWidget(entry, pageId, geometry.cols, geometry.rows, editPageStyle)
            else -> {
                val info = entry.provider?.let { widgetHost.providerFor(it) }
                if (info == null) {
                    Toast.makeText(context, "${entry.name} is no longer installed", Toast.LENGTH_SHORT).show()
                } else {
                    pendingSpans.spanX = spanX
                    pendingSpans.spanY = spanY
                    addAppWidget.launch(
                        info,
                        WidgetSizing.spanToDp(spanX, geometry.cellWidthDp),
                        WidgetSizing.spanToDp(spanY, geometry.cellHeightDp),
                    )
                }
            }
        }
    }

    var themesOpen by rememberSaveable { mutableStateOf(false) }
    var wallpaperOpen by rememberSaveable { mutableStateOf(false) }
    // Done / Back / home key leave edit mode without going through the sheets' onDismiss; reset
    // the flags so a sheet does not pop up by itself the next time edit mode opens.
    LaunchedEffect(editMode) {
        if (!editMode) {
            themesOpen = false
            wallpaperOpen = false
        }
    }

    val backdrop = rememberWidgetPickerBackdropModifier(
        visible = drawerOpen || pickerOpen,
        blur = settings.popBlur && (pickerOpen || settings.drawerStyle == DrawerStyle.POPUP),
    )

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().then(backdrop)) {
            Wallpaper()
            CompositionLocalProvider(LocalHomeCallbacks provides homeCallbacks) {
                HomeScreen(
                    screen = screen,
                    fold = fold,
                    settings = settings,
                    pages = pages,
                    itemsForPage = viewModel.graph.layouts::items,
                    apps = apps,
                    onLaunch = onLaunch,
                    onOpenDrawer = openDrawer,
                    onLongPressHome = enterEdit,
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
                    perPageStyle = true,
                    onCurrentPageChanged = { page -> viewModel.setCurrentPage(page) },
                    pageSelectRequest = pageSelectRequest,
                )
            }
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

        // Live edit (PLAN.md 5.2): same Box as the home, both fillMaxSize, so the grid geometry the
        // home publishes in window dp maps 1:1 onto the overlay.
        EditModeOverlay(
            active = editMode && !pickerOpen,
            screen = screen,
            settings = settings,
            pages = pages,
            currentPageId = editPageId ?: 0L,
            geometry = geometry,
            items = editItems,
            onMove = { id, pageId, col, row ->
                editItems.firstOrNull { it.id == id }?.let { viewModel.moveItem(it, pageId, col, row, stacksOn, geometry) }
            },
            onResize = { id, spanX, spanY ->
                editItems.firstOrNull { it.id == id }?.let { viewModel.resizeItem(it, spanX, spanY, geometry) }
            },
            onDelete = { id -> editItems.firstOrNull { it.id == id }?.let { viewModel.deleteItem(it) } },
            onSetPageStyle = { pageId, style -> viewModel.setPageStyle(pageId, style, screen) },
            onAddPage = { viewModel.addPage(screen, settings.styleFor(screen)) },
            onDeletePage = { pageId -> viewModel.deletePage(pageId, screen) },
            onAddWidget = { viewModel.openWidgetPicker() },
            onOpenWallpaper = { wallpaperOpen = true },
            onOpenThemes = { themesOpen = true },
            onOpenDock = { openSettings(SettingsRoutes.DOCK) },
            onOpenSettings = { openSettings(null) },
            onExit = { viewModel.exitEditMode() },
            modifier = Modifier.fillMaxSize(),
            onSelectPage = { pageId -> viewModel.selectPage(pageId) },
        )
        ThemeQuickSheet(visible = editMode && themesOpen, settings = settings, onDismiss = { themesOpen = false })
        WallpaperSheet(visible = editMode && wallpaperOpen, onDismiss = { wallpaperOpen = false })

        WidgetPicker(
            visible = pickerOpen,
            screen = fold.kind,
            settings = settings,
            onPick = onPickWidget,
            onDismiss = { viewModel.closeWidgetPicker() },
            modifier = Modifier.fillMaxSize(),
        )

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
                    HubCard("Next event", "Nothing scheduled", "Calendar connects in phase 5", Modifier.fillMaxWidth())
                }
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    HubCard("Tasks", "0 due today", "Todoist and Google Tasks arrive later", Modifier.fillMaxWidth())
                    HubCard("Mail", "Inbox is quiet", "Gmail connects in phase 5", Modifier.fillMaxWidth())
                    HubCard("People", "No one to reach", "Missed calls and meetings show here", Modifier.fillMaxWidth())
                }
            }
        } else {
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                HubBriefingCard(Modifier.fillMaxWidth())
                HubCard("Next event", "Nothing scheduled", "Calendar connects in phase 5", Modifier.fillMaxWidth())
                HubCard("Tasks", "0 due today", "Todoist and Google Tasks arrive later", Modifier.fillMaxWidth())
                HubCard("Mail", "Inbox is quiet", "Gmail connects in phase 5", Modifier.fillMaxWidth())
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
