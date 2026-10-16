package com.foldos.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foldos.launcher.core.AppGraph
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.GestureAction
import com.foldos.launcher.core.model.GestureKind
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.model.WidgetEntry
import com.foldos.launcher.gestures.GestureActions
import com.foldos.launcher.gestures.GestureHost
import com.foldos.launcher.gestures.HomeKeyDoubleTapDetector
import com.foldos.launcher.home.GridGeometry
import com.foldos.launcher.home.HomeIconCache
import com.foldos.launcher.home.PageSelectRequest
import com.foldos.launcher.home.defaultDashboardItems
import com.foldos.launcher.icons.FoldOsIconResolver
import com.foldos.launcher.items.Folders
import com.foldos.launcher.settings.SettingsActivity
import com.foldos.launcher.widgets.WidgetHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * State holder for [MainActivity]. Survives configuration changes (fold / unfold recreates the
 * activity when the manifest does not swallow the change) and keeps the hot flows alive so the
 * home screen does not flash empty on every recreation.
 *
 * Phase 2+3 adds the live-edit state (edit mode, widget picker, current page) and the layout
 * mutations the edit overlay and picker report; every write runs on `graph.appScope` so it
 * finishes even when the UI that started it leaves the composition.
 *
 * Phase 4+5 adds universal search and hub requests, the [GestureHost] the `gestures/` package
 * dispatches bound actions through (including the home key double press), and folder creation
 * when an app is dropped onto another app (or into a folder) in edit mode.
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val graph: AppGraph = FoldOsApplication.from(application).graph

    /** Current launcher settings; defaults until DataStore has been read. */
    val settings: StateFlow<LauncherSettings> = graph.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    /**
     * Null until the first real settings value arrived, so the wizard is never shown by mistake.
     * "Loaded" and the value come from this one flow so they can never be observed out of step.
     */
    val loadedSettings: StateFlow<LauncherSettings?> = graph.settings.settings
        .map<LauncherSettings, LauncherSettings?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** All launchable apps, independent of the fold posture. */
    val apps: StateFlow<List<AppInfo>> = graph.apps.apps
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val pageFlows = HashMap<ScreenKind, StateFlow<List<HomePage>>>()

    /** The process-wide widget host controller (ids, binding, configure results). */
    private val widgetHost: WidgetHostController
        get() = WidgetHostController.from(getApplication<Application>(), graph)

    /** Home pages of one layout store, shared for the life of the view model. */
    fun pages(screen: ScreenKind): StateFlow<List<HomePage>> = pageFlows.getOrPut(screen) {
        graph.layouts.pages(screen).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    /** Settings transforms are applied one at a time, in the order they were requested. */
    private val settingsWrites = Channel<(LauncherSettings) -> LauncherSettings>(Channel.UNLIMITED)

    init {
        graph.appScope.launch {
            for (transform in settingsWrites) {
                runCatching { graph.settings.update(transform) }
                    .onFailure { Log.w(TAG, "updateSettings failed", it) }
            }
        }
    }

    /** Applies a settings transform on the app scope (wizard, quick sheets), in call order. */
    fun updateSettings(transform: (LauncherSettings) -> LauncherSettings) {
        settingsWrites.trySend(transform)
    }

    override fun onCleared() {
        // Buffered transforms are still drained and applied by the consumer on the app scope.
        settingsWrites.close()
        super.onCleared()
    }

    // ------------------------------------------------------------------------------------------
    // Drawer, home key
    // ------------------------------------------------------------------------------------------

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    fun openDrawer() {
        _drawerOpen.value = true
    }

    fun closeDrawer() {
        _drawerOpen.value = false
    }

    private val _homeRequests = MutableStateFlow(0)

    /** Increments every time the user asks to go to the default page (home key, back press). */
    val homeRequests: StateFlow<Int> = _homeRequests.asStateFlow()

    fun goHome() {
        _drawerOpen.value = false
        _widgetPickerOpen.value = false
        _editMode.value = false
        _searchOpen.value = false
        _homeRequests.value = _homeRequests.value + 1
    }

    // ------------------------------------------------------------------------------------------
    // Universal search, hub page, gestures (Phase 4+5)
    // ------------------------------------------------------------------------------------------

    private val _searchOpen = MutableStateFlow(false)

    /** True while `search.UniversalSearch` is shown (search bar tap, SWIPE_UP_2F by default). */
    val searchOpen: StateFlow<Boolean> = _searchOpen.asStateFlow()

    fun openSearch() {
        if (!settings.value.feature(LauncherSettings.FEATURE_SEARCH)) {
            // Search switched off: the drawer's own search field is the fallback.
            openDrawer()
            return
        }
        _drawerOpen.value = false
        _widgetPickerOpen.value = false
        _searchOpen.value = true
    }

    fun closeSearch() {
        _searchOpen.value = false
    }

    private val _hubRequests = MutableStateFlow(0)

    /** Increments every time a gesture asks for the hub page (`GestureAction.TYPE_HUB`). */
    val hubRequests: StateFlow<Int> = _hubRequests.asStateFlow()

    fun openHub() {
        _drawerOpen.value = false
        _widgetPickerOpen.value = false
        _searchOpen.value = false
        _editMode.value = false
        _hubRequests.value = _hubRequests.value + 1
    }

    /** Home key pressed twice within 400 ms while the launcher is showing (`GestureKind.HOME_DOUBLE`). */
    private val homeKeyDoubleTap = HomeKeyDoubleTapDetector()

    /**
     * Called from `MainActivity.onNewIntent` for every home key press while the launcher is already
     * showing: returns to the default page, and on a double press performs the HOME_DOUBLE binding
     * (when the Gestures feature is on and the binding is not "none").
     */
    fun onHomeKeyPressed(context: Context) {
        goHome()
        if (!homeKeyDoubleTap.onHomePressed()) return
        val current = settings.value
        if (!current.feature(LauncherSettings.FEATURE_GESTURES)) return
        val action = current.gesture(GestureKind.HOME_DOUBLE)
        if (action.type != GestureAction.TYPE_NONE) performGesture(context, action)
    }

    /**
     * Performs a bound gesture action through [GestureActions.perform] with this view model as the
     * [GestureHost]: drawer, search, hub and edit mode are state here; Settings opens its activity;
     * TYPE_APP resolves the key against the visible app list (first activity of the package when the
     * key has none) and falls back to the package's launch intent.
     */
    fun performGesture(context: Context, action: GestureAction) {
        GestureActions.perform(context, action, gestureHost(context))
    }

    /** The [GestureHost] for [context] (an activity, so Settings and app launches start from it). */
    fun gestureHost(context: Context): GestureHost = object : GestureHost {
        override fun openDrawer() = this@LauncherViewModel.openDrawer()
        override fun openSearch() = this@LauncherViewModel.openSearch()
        override fun openHub() = this@LauncherViewModel.openHub()
        override fun openSettings() {
            runCatching { context.startActivity(SettingsActivity.intent(context, null)) }
                .onFailure { Log.w(TAG, "Could not open Settings", it) }
        }
        override fun enterEditMode() = this@LauncherViewModel.enterEditMode()
        override fun launch(packageName: String, activityName: String?) {
            val list = apps.value
            val app = (if (activityName != null) list.firstOrNull { it.packageName == packageName && it.activityName == activityName } else null)
                ?: list.firstOrNull { it.packageName == packageName }
            if (app != null) {
                this@LauncherViewModel.launch(app)
                return
            }
            val intent = runCatching { context.packageManager.getLaunchIntentForPackage(packageName) }.getOrNull()
            if (intent == null) {
                Toast.makeText(context, "That app is no longer installed", Toast.LENGTH_SHORT).show()
                return
            }
            runCatching { context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                .onFailure { Log.w(TAG, "Could not launch $packageName", it) }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Edit mode, widget picker, current page
    // ------------------------------------------------------------------------------------------

    private val _editMode = MutableStateFlow(false)

    /** True while the live-edit overlay is shown (long-press on the home background). */
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    fun enterEditMode() {
        _drawerOpen.value = false
        _searchOpen.value = false
        _editMode.value = true
    }

    fun exitEditMode() {
        _editMode.value = false
        _widgetPickerOpen.value = false
    }

    private val _widgetPickerOpen = MutableStateFlow(false)

    /** True while the "Add a widget" picker is open. */
    val widgetPickerOpen: StateFlow<Boolean> = _widgetPickerOpen.asStateFlow()

    fun openWidgetPicker() {
        _drawerOpen.value = false
        _widgetPickerOpen.value = true
    }

    fun closeWidgetPicker() {
        _widgetPickerOpen.value = false
    }

    private val _currentPageId = MutableStateFlow<Long?>(null)

    /** The home page the pager has settled on; null for the hub page or before the first report. */
    val currentPageId: StateFlow<Long?> = _currentPageId.asStateFlow()

    fun setCurrentPage(page: HomePage?) {
        _currentPageId.value = page?.id
    }

    private var selectSerial = 0
    private val _pageSelectRequest = MutableStateFlow<PageSelectRequest?>(null)

    /** The latest request to scroll the home pager to a page (edit overlay arrows, edge drops). */
    val pageSelectRequest: StateFlow<PageSelectRequest?> = _pageSelectRequest.asStateFlow()

    fun selectPage(pageId: Long) {
        selectSerial += 1
        _pageSelectRequest.value = PageSelectRequest(pageId, selectSerial)
    }

    // ------------------------------------------------------------------------------------------
    // Default layout
    // ------------------------------------------------------------------------------------------

    private val ensuredScreens = HashSet<ScreenKind>()

    /**
     * Writes the default pages and items for [screen] once the app list is known, laid out for the
     * column count currently configured for that screen. Runs at most once per screen per process
     * unless the write fails.
     */
    fun ensureDefaultLayout(screen: ScreenKind) {
        if (!ensuredScreens.add(screen)) return
        viewModelScope.launch {
            val list = apps.first { it.isNotEmpty() }
            val current = settings.value
            val result = runCatching {
                graph.layouts.ensureDefaultLayout(screen, list, current.colsFor(screen), current.styleFor(screen))
            }
            if (result.isFailure) {
                Log.w(TAG, "ensureDefaultLayout($screen) failed", result.exceptionOrNull())
                ensuredScreens.remove(screen)
            }
        }
    }

    fun launch(app: AppInfo) {
        graph.apps.launch(app)
    }

    // ------------------------------------------------------------------------------------------
    // Layout mutations (edit overlay)
    // ------------------------------------------------------------------------------------------

    /**
     * Moves [item] to [col]/[row] on [pageId]. When [stacksOn] and the drop lands on another widget
     * (or widget stack), the two are merged into one stack instead (`LayoutRepository.createStack`).
     * An APP or PAIR dropped onto another APP / PAIR makes a **folder** in the target's cell
     * (`items.Folders.createFromDrop`); dropped onto a FOLDER it goes inside (`Folders.addToFolder`).
     * Free-placed items are snapped to the cell and keep their dp size.
     */
    fun moveItem(item: HomeItem, pageId: Long, col: Int, row: Int, stacksOn: Boolean, geometry: GridGeometry) {
        graph.appScope.launch {
            runCatching {
                if (Folders.canLiveInFolder(item)) {
                    val target = graph.layouts.items(pageId).first()
                    val hit = target.firstOrNull { other -> other.id != item.id && other.covers(col, row) }
                    if (hit != null) {
                        if (hit.isFolder) {
                            val count = graph.layouts.children(hit.id).first().size
                            Folders.addToFolder(graph.layouts, hit, item, count)
                            return@runCatching
                        }
                        if (Folders.canLiveInFolder(hit) && Folders.createFromDrop(graph.layouts, hit, item) != null) {
                            return@runCatching
                        }
                    }
                }
                if (stacksOn && item.kind == ItemKind.WIDGET) {
                    val target = graph.layouts.items(pageId).first()
                    val hit = target.firstOrNull { other ->
                        other.kind == ItemKind.WIDGET &&
                            other.id != item.id &&
                            (item.stackId == null || other.stackId != item.stackId) &&
                            other.covers(col, row)
                    }
                    if (hit != null) {
                        val targetStack = if (hit.stackId != null) {
                            target.filter { it.stackId == hit.stackId }.sortedBy { it.stackIndex }
                        } else {
                            listOf(hit)
                        }
                        val draggedStack = if (item.stackId != null) {
                            graph.layouts.items(item.pageId).first()
                                .filter { it.stackId == item.stackId }
                                .sortedBy { it.stackIndex }
                                .ifEmpty { listOf(item) }
                        } else {
                            listOf(item)
                        }
                        graph.layouts.createStack(targetStack.map { it.id } + draggedStack.map { it.id })
                        return@runCatching
                    }
                }
                if (item.free) {
                    graph.layouts.upsertItem(
                        item.copy(
                            pageId = pageId,
                            col = col,
                            row = row,
                            xDp = (col * geometry.cellWidthDp).toFloat(),
                            yDp = (row * geometry.cellHeightDp).toFloat(),
                        ),
                    )
                } else {
                    graph.layouts.moveItem(item.id, pageId, col, row)
                }
            }.onFailure { Log.w(TAG, "moveItem(${item.id}) failed", it) }
        }
    }

    /** Resizes [item] (and its stack mates) to [spanX] x [spanY]; free-placed items get the matching dp size too. */
    fun resizeItem(item: HomeItem, spanX: Int, spanY: Int, geometry: GridGeometry) {
        graph.appScope.launch {
            runCatching {
                graph.layouts.resizeItem(item.id, spanX, spanY)
                if (item.free) {
                    graph.layouts.upsertItem(
                        item.copy(
                            spanX = spanX.coerceAtLeast(1),
                            spanY = spanY.coerceAtLeast(1),
                            wDp = (spanX.coerceAtLeast(1) * geometry.cellWidthDp).toFloat(),
                            hDp = (spanY.coerceAtLeast(1) * geometry.cellHeightDp).toFloat(),
                        ),
                    )
                }
            }.onFailure { Log.w(TAG, "resizeItem(${item.id}) failed", it) }
        }
    }

    /** Removes [item]; a hosted app widget also releases its id from the host. */
    fun deleteItem(item: HomeItem) {
        val appWidgetId = item.appWidgetId
        if (appWidgetId != null) widgetHost.deleteId(appWidgetId)
        graph.appScope.launch {
            runCatching { graph.layouts.deleteItem(item.id) }
                .onFailure { Log.w(TAG, "deleteItem(${item.id}) failed", it) }
        }
    }

    /** Restyles one page (edit mode's segmented control); an empty page turned dashboard is seeded. */
    fun setPageStyle(pageId: Long, style: HomeStyle, screen: ScreenKind) {
        graph.appScope.launch {
            runCatching {
                graph.layouts.setPageStyle(pageId, style)
                if (style == HomeStyle.DASHBOARD) seedDashboardIfEmpty(pageId, screen)
            }.onFailure { Log.w(TAG, "setPageStyle($pageId) failed", it) }
        }
    }

    /**
     * Writes [com.foldos.launcher.home.defaultDashboardItems] to [pageId] when it holds no items,
     * so a page switched to the dashboard style gets real, editable widgets instead of the
     * non-persisted preview the home draws for an empty dashboard page.
     */
    private suspend fun seedDashboardIfEmpty(pageId: Long, screen: ScreenKind) {
        if (graph.layouts.items(pageId).first().isNotEmpty()) return
        defaultDashboardItems(screen, pageId).forEach { graph.layouts.upsertItem(it) }
    }

    /** Appends a page in [style] to [screen] and scrolls to it. */
    fun addPage(screen: ScreenKind, style: HomeStyle) {
        viewModelScope.launch {
            val page = runCatching {
                graph.layouts.addPage(screen, style).also { added ->
                    // A new dashboard page starts with real, editable default widgets.
                    if (style == HomeStyle.DASHBOARD) seedDashboardIfEmpty(added.id, screen)
                }
            }
                .onFailure { Log.w(TAG, "addPage($screen) failed", it) }
                .getOrNull() ?: return@launch
            selectPage(page.id)
        }
    }

    /** Deletes [pageId] with its items (releasing any app widget ids) and shows the first remaining page. */
    fun deletePage(pageId: Long, screen: ScreenKind) {
        viewModelScope.launch {
            val deleted = runCatching {
                val items = graph.layouts.items(pageId).first()
                withContext(Dispatchers.Main) {
                    items.forEach { item -> item.appWidgetId?.let { widgetHost.deleteId(it) } }
                }
                graph.layouts.deletePage(pageId)
            }.onFailure { Log.w(TAG, "deletePage($pageId) failed", it) }.isSuccess
            if (deleted) {
                pages(screen).first { all -> all.none { it.id == pageId } }.firstOrNull()?.let { selectPage(it.id) }
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Adding widgets (picker)
    // ------------------------------------------------------------------------------------------

    /**
     * Adds the built-in [entry] at the first free cell of [pageId] on a [cols] x [rows] grid.
     * [pageStyle] decides whether the page may grow below [rows] (dashboard / hybrid panels size
     * their rows from the items) or is fixed to the visible grid (grid / list), in which case a
     * full page refuses the widget with a toast instead of storing an invisible row.
     */
    fun addBuiltInWidget(entry: WidgetEntry, pageId: Long, cols: Int, rows: Int, pageStyle: HomeStyle?) {
        placeWidget(
            pageId = pageId,
            cols = cols,
            rows = rows,
            growsRows = pageStyle.growsRows(),
            spanX = entry.defaultSpanX,
            spanY = entry.defaultSpanY,
            title = entry.name,
            builtinId = entry.id,
            appWidgetId = null,
        )
    }

    /** Adds the bound and configured app widget [appWidgetId] at the first free cell of [pageId]; see [addBuiltInWidget]. */
    fun addAppWidget(
        appWidgetId: Int,
        title: String,
        spanX: Int,
        spanY: Int,
        pageId: Long,
        cols: Int,
        rows: Int,
        pageStyle: HomeStyle?,
    ) {
        placeWidget(
            pageId = pageId,
            cols = cols,
            rows = rows,
            growsRows = pageStyle.growsRows(),
            spanX = spanX,
            spanY = spanY,
            title = title,
            builtinId = null,
            appWidgetId = appWidgetId,
        )
    }

    /** Dashboard and hybrid widget panels derive their row count from the items, so they can grow. */
    private fun HomeStyle?.growsRows(): Boolean = this == HomeStyle.DASHBOARD || this == HomeStyle.HYBRID

    private fun placeWidget(
        pageId: Long,
        cols: Int,
        rows: Int,
        growsRows: Boolean,
        spanX: Int,
        spanY: Int,
        title: String,
        builtinId: String?,
        appWidgetId: Int?,
    ) {
        graph.appScope.launch {
            runCatching {
                val items = graph.layouts.items(pageId).first()
                val sx = spanX.coerceIn(1, cols.coerceAtLeast(1))
                val sy = spanY.coerceIn(1, rows.coerceAtLeast(1))
                val (col, row) = firstFreeCell(items, cols, rows, sx, sy)
                if (!growsRows && row + sy > rows.coerceAtLeast(1)) {
                    // HomeCellGrid never draws a cell below the derived row count, so the item
                    // would be stored but invisible (and an app widget id bound to nothing).
                    withContext(Dispatchers.Main) {
                        if (appWidgetId != null) widgetHost.deleteId(appWidgetId)
                        Toast.makeText(getApplication<Application>(), "No room on this page", Toast.LENGTH_SHORT).show()
                    }
                    return@runCatching
                }
                graph.layouts.upsertItem(
                    HomeItem(
                        id = 0L,
                        pageId = pageId,
                        kind = ItemKind.WIDGET,
                        col = col,
                        row = row,
                        spanX = sx,
                        spanY = sy,
                        title = title,
                        appWidgetId = appWidgetId,
                        builtinId = builtinId,
                    ),
                )
            }.onFailure {
                Log.w(TAG, "placeWidget on page $pageId failed", it)
                // Never leave a bound app widget id behind when the row could not be written.
                if (appWidgetId != null) withContext(Dispatchers.Main) { widgetHost.deleteId(appWidgetId) }
            }
        }
    }

    private suspend fun applyStyleToScreen(screen: ScreenKind, style: HomeStyle) {
        runCatching {
            graph.layouts.pages(screen).first()
                .filter { it.style != style }
                .forEach { page ->
                    graph.layouts.setPageStyle(page.id, style)
                    if (style == HomeStyle.DASHBOARD) seedDashboardIfEmpty(page.id, screen)
                }
        }.onFailure { Log.w(TAG, "applyStyleToScreen($screen) failed", it) }
    }

    init {
        // Settings > Home > style: a change there restyles every page of THAT screen only, so the
        // per-page styles (set from edit mode) on the other screen survive. The first emission is
        // the stored value, not a change, so it only primes the comparison.
        viewModelScope.launch {
            var previous: Pair<HomeStyle, HomeStyle>? = null
            graph.settings.settings
                .map { it.styleCover to it.styleInner }
                .distinctUntilChanged()
                .collect { next ->
                    val before = previous
                    previous = next
                    if (before == null) return@collect
                    if (next.first != before.first) applyStyleToScreen(ScreenKind.COVER, next.first)
                    if (next.second != before.second) applyStyleToScreen(ScreenKind.INNER, next.second)
                }
        }
        // Icon pack / shape / override changes: drop the rendered icons so the home re-rasterises.
        val resolver = graph.icons as? FoldOsIconResolver
        if (resolver != null) {
            viewModelScope.launch {
                resolver.generation.drop(1).collect { HomeIconCache.clear() }
            }
        }
    }

    private companion object {
        const val TAG = "LauncherViewModel"
    }
}

/** True when this item's grid cells include ([col], [row]); free-placed items use their grid fallback. */
internal fun HomeItem.covers(col: Int, row: Int): Boolean {
    val sx = spanX.coerceAtLeast(1)
    val sy = spanY.coerceAtLeast(1)
    return col >= this.col && col < this.col + sx && row >= this.row && row < this.row + sy
}

/**
 * The first cell, in reading order, where a [spanX] x [spanY] item fits among [items] on a
 * [cols] x [rows] grid. When the grid is full the item goes to the first column of the row below
 * the lowest occupied cell (the dashboard grows its rows to fit).
 */
internal fun firstFreeCell(items: List<HomeItem>, cols: Int, rows: Int, spanX: Int, spanY: Int): Pair<Int, Int> {
    val columns = cols.coerceAtLeast(1)
    val sx = spanX.coerceIn(1, columns)
    val sy = spanY.coerceAtLeast(1)
    val occupied = HashSet<Pair<Int, Int>>()
    var lowest = -1
    for (item in items) {
        val isx = item.spanX.coerceAtLeast(1)
        val isy = item.spanY.coerceAtLeast(1)
        for (r in item.row until item.row + isy) {
            for (c in item.col until item.col + isx) occupied.add(c to r)
        }
        lowest = maxOf(lowest, item.row + isy - 1)
    }
    fun free(col: Int, row: Int): Boolean {
        for (r in row until row + sy) {
            for (c in col until col + sx) if ((c to r) in occupied) return false
        }
        return true
    }
    val searchRows = rows.coerceAtLeast(1)
    for (row in 0..(searchRows - sy)) {
        for (col in 0..(columns - sx)) {
            if (free(col, row)) return col to row
        }
    }
    return 0 to (lowest + 1)
}
