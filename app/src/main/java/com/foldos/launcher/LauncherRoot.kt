package com.foldos.launcher

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.a11y.KeyboardShortcuts
import com.foldos.launcher.a11y.LocalKeyboardShortcuts
import com.foldos.launcher.core.fold.FoldState
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.DockBackground
import com.foldos.launcher.core.model.DockPosition
import com.foldos.launcher.core.model.DrawerStyle
import com.foldos.launcher.core.model.GestureAction
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
import com.foldos.launcher.gestures.HingeGestureEffect
import com.foldos.launcher.gestures.homeGestures
import com.foldos.launcher.home.HomeCallbacks
import com.foldos.launcher.home.HomeScreen
import com.foldos.launcher.home.LocalHomeCallbacks
import com.foldos.launcher.home.rememberGridGeometry
import com.foldos.launcher.hub.HubPage
import com.foldos.launcher.notifications.QuietDrawerSheet
import com.foldos.launcher.onboarding.SetupWizard
import com.foldos.launcher.picker.WidgetPicker
import com.foldos.launcher.picker.rememberWidgetPickerBackdropModifier
import com.foldos.launcher.profiles.ProfileAutoSwitchEffect
import com.foldos.launcher.profiles.ProfileSwitcherSheet
import com.foldos.launcher.search.SearchBar
import com.foldos.launcher.search.UniversalSearch
import com.foldos.launcher.settings.SettingsActivity
import com.foldos.launcher.settings.SettingsRoutes
import com.foldos.launcher.theme.Wallpaper
import com.foldos.launcher.widgets.AddAppWidgetResult
import com.foldos.launcher.widgets.WidgetSizing
import com.foldos.launcher.widgets.rememberAddAppWidgetFlow
import com.foldos.launcher.widgets.rememberWidgetHostController
import kotlinx.coroutines.flow.flowOf
import java.util.Locale

/**
 * Everything inside the launcher window. Until the first-run wizard has finished it shows the
 * [SetupWizard] full screen; afterwards: wallpaper, [HomeScreen] with the dock, the search bar and
 * the Hub page, the rail dock when configured, the live-edit overlay with its quick sheets, the
 * widget picker, the [AppDrawer] and, on top of everything, universal search. Must be composed
 * inside `FoldOsTheme` with `LocalAppGraph` and `builtins.LocalPermissionRequester` provided.
 */
@Composable
fun LauncherRoot(viewModel: LauncherViewModel, modifier: Modifier = Modifier) {
    val loadedSettings by viewModel.loadedSettings.collectAsStateWithLifecycle()
    val settings = loadedSettings

    when {
        // Until DataStore has answered, only the wallpaper: composing the home here would seed the
        // layout store with the default styles before the wizard has picked the real ones (and flash
        // a default-themed home in front of the wizard).
        settings == null -> Box(modifier.fillMaxSize()) { Wallpaper() }
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
    val searchOpen by viewModel.searchOpen.collectAsStateWithLifecycle()
    val searchSeed by viewModel.searchSeed.collectAsStateWithLifecycle()
    val editMode by viewModel.editMode.collectAsStateWithLifecycle()
    val pickerOpen by viewModel.widgetPickerOpen.collectAsStateWithLifecycle()
    val homeRequest by viewModel.homeRequests.collectAsStateWithLifecycle()
    val hubRequest by viewModel.hubRequests.collectAsStateWithLifecycle()
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


    // Hardware keyboard (a11y/KeyboardNavigation.kt): a printable key on the home screen opens search
    // with that character as the start of the query. Off while search is switched off or an overlay is up;
    // while search itself is opening, further characters are appended to the seed.
    val typeToSearchOn = settings.feature(LauncherSettings.FEATURE_SEARCH) &&
        !editMode && !drawerOpen && !pickerOpen
    val keyboardShortcuts = remember(viewModel, typeToSearchOn) {
        KeyboardShortcuts(
            onTypeToSearch = if (typeToSearchOn) {
                { typed: String -> viewModel.typeToSearch(typed) }
            } else {
                null
            },
        )
    }
    val onLaunch: (AppInfo) -> Unit = { app -> viewModel.launch(app) }
    val openDrawer: () -> Unit = { viewModel.openDrawer() }
    val openSearch: () -> Unit = { viewModel.openSearch() }
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

    // Gestures (PLAN.md 4): bound actions run through the view model's GestureHost. The touch
    // detector wraps every home page (HomeScreen.pageModifier); the hinge detector watches the
    // fold posture; the home key double press is handled in MainActivity.onNewIntent.
    val gesturesOn = settings.feature(LauncherSettings.FEATURE_GESTURES)
    val onGestureAction: (GestureAction) -> Unit = { action -> viewModel.performGesture(context, action) }
    val pageGestures = Modifier.homeGestures(
        enabled = !editMode && !drawerOpen && !pickerOpen && !searchOpen,
        settings = settings,
        onAction = onGestureAction,
    )
    HingeGestureEffect(fold = fold, enabled = settings.wizardDone, settings = settings, onAction = onGestureAction)

    // Profiles: schedule / Do Not Disturb rules re-evaluated on resume, DND receiver while composed.
    ProfileAutoSwitchEffect()

    // Back on the home screen returns to the default page. The drawer, the picker, the edit
    // overlay and universal search each have their own BackHandler (composed later, so higher priority).
    BackHandler(enabled = !drawerOpen && !pickerOpen && !editMode && !searchOpen) { viewModel.goHome() }

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
    var profilesOpen by rememberSaveable { mutableStateOf(false) }
    var quietDrawerOpen by rememberSaveable { mutableStateOf(false) }
    // Done / Back / home key leave edit mode without going through the sheets' onDismiss; reset
    // the flags so a sheet does not pop up by itself the next time edit mode opens.
    LaunchedEffect(editMode) {
        if (!editMode) {
            themesOpen = false
            wallpaperOpen = false
        }
    }

    val backdrop = rememberWidgetPickerBackdropModifier(
        visible = drawerOpen || pickerOpen || searchOpen,
        blur = settings.popBlur && (pickerOpen || searchOpen || settings.drawerStyle == DrawerStyle.POPUP),
    )
    val profilesOn = settings.feature(LauncherSettings.FEATURE_PROFILES)
    val onNotifications: (() -> Unit)? = if (settings.feature(LauncherSettings.FEATURE_BADGES)) {
        { quietDrawerOpen = true }
    } else {
        null
    }

    Box(modifier = modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize().then(backdrop)) {
            Wallpaper(showSystemWallpaper = settings.dailyWallpaper)
            CompositionLocalProvider(
                LocalHomeCallbacks provides homeCallbacks,
                LocalKeyboardShortcuts provides keyboardShortcuts,
            ) {
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
                                onSearch = openSearch,
                                modifier = Modifier.padding(start = sideMargin, end = sideMargin, bottom = 6.dp),
                                onNotifications = onNotifications,
                            )
                        }
                    },
                    searchBar = if (settings.feature(LauncherSettings.FEATURE_SEARCH)) {
                        { SearchBar(onClick = openSearch, settings = settings) }
                    } else {
                        null
                    },
                    hub = if (settings.hubOn) {
                        { HubPage(screen = fold.kind, settings = settings, modifier = Modifier.fillMaxSize()) }
                    } else {
                        null
                    },
                    homeRequest = homeRequest,
                    perPageStyle = true,
                    onCurrentPageChanged = { page -> viewModel.setCurrentPage(page) },
                    pageSelectRequest = pageSelectRequest,
                    editMode = editMode,
                    pageModifier = pageGestures,
                    // The gestures detector binds SWIPE_UP itself; the page's own detector only
                    // fills in while the Gestures feature is off.
                    swipeUpOpensDrawer = !gesturesOn,
                    hubSelectRequest = hubRequest,
                    // The home takes keyboard focus back whenever an overlay closes (type-to-search, arrow keys).
                    keyboardRefocusKey = Triple(drawerOpen, pickerOpen, searchOpen),
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
                    onSearch = openSearch,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .windowInsetsPadding(WindowInsets.systemBars)
                        .padding(end = 10.dp),
                    onNotifications = onNotifications,
                )
            }
        }

        // Live edit (PLAN.md 5.2): same Box as the home, both fillMaxSize, so the grid geometry the
        // home publishes in window dp maps 1:1 onto the overlay. Dropping an app onto another app
        // makes a folder, onto a folder adds to it (LauncherViewModel.moveItem).
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
            onOpenProfiles = if (profilesOn) {
                { profilesOpen = true }
            } else {
                null
            },
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

        // Universal search (PLAN.md 4): search bar tap, dock search bar, or a bound gesture.
        UniversalSearch(
            visible = searchOpen,
            screen = fold.kind,
            settings = settings,
            onDismiss = { viewModel.closeSearch() },
            onLaunch = { app ->
                viewModel.closeSearch()
                viewModel.launch(app)
            },
            onOpenSettingsRoute = { route ->
                viewModel.closeSearch()
                openSettings(route)
            },
            modifier = Modifier.fillMaxSize(),
            initialQuery = searchSeed,
        )

        // Profile switcher (edit bar > Profiles) and the quiet notification drawer (dock add-on).
        if (profilesOpen && profilesOn) {
            ProfileSwitcherSheet(onDismiss = { profilesOpen = false })
        }
        if (quietDrawerOpen) {
            QuietDrawerSheet(onDismiss = { quietDrawerOpen = false })
        }
    }
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
