package com.foldos.launcher

import android.app.Application
import android.content.Context
import android.content.Intent
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foldos.launcher.backup.StyleRestoreGate
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
import com.foldos.launcher.core.ui.DragPayload
import com.foldos.launcher.edit.UNDO_WINDOW_MS
import com.foldos.launcher.gestures.GestureActions
import com.foldos.launcher.gestures.GestureHost
import com.foldos.launcher.gestures.HomeKeyDoubleTapDetector
import com.foldos.launcher.home.GridGeometry
import com.foldos.launcher.home.HomeIconCache
import com.foldos.launcher.home.PageSelectRequest
import com.foldos.launcher.home.StarterLayouts
import com.foldos.launcher.home.cardAccepts
import com.foldos.launcher.icons.FoldOsIconResolver
import com.foldos.launcher.items.Folders
import com.foldos.launcher.settings.SettingsActivity
import com.foldos.launcher.widgets.WidgetHostController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
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
import kotlinx.coroutines.withTimeoutOrNull

/**
 * State holder for [MainActivity]. Survives configuration changes (fold / unfold recreates the
 * activity when the manifest does not swallow the change) and keeps the hot flows alive so the
 * home screen does not flash empty on every recreation.
 *
 * Round 2.0 (1.3.0): every page is one free-form grid canvas. The layout mutations here place
 * apps, widgets and cards by drag and drop or at the first free cell, move items into and out of
 * cards, keep items from overlapping (nearest free cell, or a "No room" toast), apply starter
 * layouts, and own the edit-mode / "Add to home" sheet state. Every write runs on `graph.appScope`
 * so it finishes even when the UI that started it leaves the composition.
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

    private val pageFlows = java.util.concurrent.ConcurrentHashMap<ScreenKind, StateFlow<List<HomePage>>>()

    /** The process-wide widget host controller (ids, binding, configure results). */
    private val widgetHost: WidgetHostController
        get() = WidgetHostController.from(getApplication<Application>(), graph)

    /** Home pages of one layout store, shared for the life of the view model. */
    fun pages(screen: ScreenKind): StateFlow<List<HomePage>> = pageFlows.computeIfAbsent(screen) {
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
        _addSheetOpen.value = false
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
        _addSheetOpen.value = false
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

    private val _searchSeed = MutableStateFlow<String?>(null)

    /**
     * Text the open search starts with: the character typed on a hardware keyboard while the home
     * screen had focus (`a11y.KeyboardShortcuts.onTypeToSearch`). Null for every other way in.
     */
    val searchSeed: StateFlow<String?> = _searchSeed.asStateFlow()

    /** Opens universal search; [initialText] (type-to-search) becomes the first characters of the query. */
    fun openSearch(initialText: String? = null) {
        if (!settings.value.feature(LauncherSettings.FEATURE_SEARCH)) {
            // Search switched off: the drawer's own search field is the fallback.
            openDrawer()
            return
        }
        _drawerOpen.value = false
        _addSheetOpen.value = false
        _searchSeed.value = initialText?.takeIf { it.isNotEmpty() }
        _searchOpen.value = true
    }

    /**
     * Type-to-search while search is already opening: the character is appended to the seed instead
     * of being dropped before the search field has taken focus. Opens search when it is closed.
     */
    fun typeToSearch(text: String) {
        if (_searchOpen.value) {
            _searchSeed.value = _searchSeed.value.orEmpty() + text
        } else {
            openSearch(text)
        }
    }

    fun closeSearch() {
        _searchOpen.value = false
        _searchSeed.value = null
    }

    private val _hubRequests = MutableStateFlow(0)

    /** Increments every time a gesture asks for the hub page (`GestureAction.TYPE_HUB`). */
    val hubRequests: StateFlow<Int> = _hubRequests.asStateFlow()

    fun openHub() {
        _drawerOpen.value = false
        _addSheetOpen.value = false
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
    // Edit mode, "Add to home" sheet, widget picker, current page
    // ------------------------------------------------------------------------------------------

    private val _editMode = MutableStateFlow(false)

    /** True while the live-edit overlay is shown (pinch in, the sheet's Pages action, an item menu's Edit). */
    val editMode: StateFlow<Boolean> = _editMode.asStateFlow()

    fun enterEditMode() {
        _drawerOpen.value = false
        _searchOpen.value = false
        _editMode.value = true
    }

    fun exitEditMode() {
        _editMode.value = false
        _addSheetOpen.value = false
    }

    private val _addSheetOpen = MutableStateFlow(false)

    /** True while the "Add to home" sheet (apps, widgets, cards, actions) is open: long press on empty space. */
    val addSheetOpen: StateFlow<Boolean> = _addSheetOpen.asStateFlow()

    fun openAddSheet() {
        _drawerOpen.value = false
        _addSheetOpen.value = true
    }

    fun closeAddSheet() {
        _addSheetOpen.value = false
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
     * column count configured for that screen and [rows] visible rows (the estimated grid), then runs
     * the non-overlap repair over every page of the screen. Runs at most once per screen per process
     * unless the write fails.
     */
    fun ensureDefaultLayout(screen: ScreenKind, rows: Int? = null) {
        if (!ensuredScreens.add(screen)) return
        viewModelScope.launch {
            val list = apps.first { it.isNotEmpty() }
            val current = settings.value
            val result = runCatching {
                graph.layouts.ensureDefaultLayout(screen, list, current.effectiveColsFor(screen), current.styleFor(screen), rows)
            }
            if (result.isFailure) {
                Log.w(TAG, "ensureDefaultLayout($screen) failed", result.exceptionOrNull())
                ensuredScreens.remove(screen)
            } else {
                // Items left on top of each other by earlier versions (or by a column-count change) are moved apart once.
                runCatching {
                    graph.layouts.pages(screen).first().forEach { repairOverlaps(it.id, screen) }
                }.onFailure { Log.w(TAG, "repairOverlaps($screen) failed", it) }
            }
        }
    }

    fun launch(app: AppInfo) {
        graph.apps.launch(app)
    }

    // ------------------------------------------------------------------------------------------
    // Layout mutations (edit overlay, drags)
    // ------------------------------------------------------------------------------------------

    /**
     * Moves [item] to [col]/[row] on [pageId]. Like Pixel: a drop on a card puts the item inside it;
     * an APP / PAIR dropped onto another APP / PAIR makes a **folder** in the target's cell
     * (`items.Folders.createFromDrop`), dropped onto a FOLDER it goes inside; a widget dropped on a
     * widget makes a stack when [stacksOn]; otherwise the item takes the cell, or the nearest free
     * spot of its size when the cell is taken (the grid may grow one more row), or the drop is refused
     * with "No room on this page". An item that lives in a card or folder leaves it. Free-placed items
     * are snapped to the cell and keep their dp size.
     */
    fun moveItem(item: HomeItem, pageId: Long, col: Int, row: Int, stacksOn: Boolean, geometry: GridGeometry) {
        graph.appScope.launch {
            runCatching {
                val target = graph.layouts.items(pageId).first()
                val hit = target.firstOrNull { other -> other.id != item.id && other.covers(col, row) }
                if (hit != null && hit.isCard && cardAccepts(hit, DragPayload.Item(item))) {
                    val count = graph.layouts.children(hit.id).first().size
                    putIntoCard(item, hit, count)
                    return@runCatching
                }
                if (item.parentId != null) {
                    // Out of a card (or folder) onto the page: it needs a free cell of its own.
                    val sx = if (item.kind == ItemKind.WIDGET) item.spanX.coerceIn(1, geometry.cols) else 1
                    val sy = if (item.kind == ItemKind.WIDGET) item.spanY.coerceAtLeast(1) else 1
                    val obstacles = target.filter { !it.free }
                    val free = nearestFreeCell(obstacles, geometry.cols, geometry.rows + sy, sx, sy, col, row)
                    if (free == null) {
                        toastNoRoom()
                        return@runCatching
                    }
                    // removeFromCard is a no-op for a folder child; then the folder path takes it (and
                    // deletes a folder left empty).
                    graph.layouts.removeFromCard(item.id, pageId, free.first, free.second)
                    val out = graph.layouts.items(pageId).first().any { it.id == item.id }
                    if (!out) graph.layouts.removeFromFolder(item.id, pageId, free.first, free.second)
                    return@runCatching
                }
                if (Folders.canLiveInFolder(item) && hit != null) {
                    if (hit.isFolder) {
                        val count = graph.layouts.children(hit.id).first().size
                        Folders.addToFolder(graph.layouts, hit, item, count)
                        return@runCatching
                    }
                    if (Folders.canLiveInFolder(hit) && Folders.createFromDrop(graph.layouts, hit, item) != null) {
                        return@runCatching
                    }
                }
                if (stacksOn && item.kind == ItemKind.WIDGET && hit != null && hit.kind == ItemKind.WIDGET &&
                    (item.stackId == null || hit.stackId != item.stackId)
                ) {
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
                // Nothing may land on top of another item: take the nearest free spot of this size instead,
                // or refuse the drop when the page (plus one more row) has none.
                val sx = item.spanX.coerceIn(1, geometry.cols)
                val sy = item.spanY.coerceAtLeast(1)
                var targetCol = col.coerceIn(0, (geometry.cols - sx).coerceAtLeast(0))
                var targetRow = row.coerceAtLeast(0)
                if (!item.free) {
                    val others = target.filter { other ->
                        other.id != item.id && !other.free && (item.stackId == null || other.stackId != item.stackId)
                    }
                    if (others.any { it.overlapsCells(targetCol, targetRow, sx, sy) }) {
                        val free = nearestFreeCell(others, geometry.cols, geometry.rows + sy, sx, sy, targetCol, targetRow)
                        if (free == null) {
                            toastNoRoom()
                            return@runCatching
                        }
                        targetCol = free.first
                        targetRow = free.second
                    }
                }
                if (item.free) {
                    graph.layouts.upsertItem(
                        item.copy(
                            pageId = pageId,
                            col = targetCol,
                            row = targetRow,
                            xDp = (targetCol * geometry.cellWidthDp).toFloat(),
                            yDp = (targetRow * geometry.cellHeightDp).toFloat(),
                        ),
                    )
                } else {
                    graph.layouts.moveItem(item.id, pageId, targetCol, targetRow)
                }
            }.onFailure { Log.w(TAG, "moveItem(${item.id}) failed", it) }
        }
    }

    /** Puts [item] into [card] at [order], renumbering the other children so the order stays dense. */
    private suspend fun putIntoCard(item: HomeItem, card: HomeItem, order: Int) {
        val children = graph.layouts.children(card.id).first().filter { it.id != item.id }.sortedBy { it.row }
        val at = order.coerceIn(0, children.size)
        graph.layouts.moveIntoCard(item.id, card.id, at)
        children.forEachIndexed { index, child ->
            val wanted = if (index < at) index else index + 1
            if (child.row != wanted || child.col != 0) graph.layouts.upsertItem(child.copy(row = wanted, col = 0))
        }
    }

    // ------------------------------------------------------------------------------------------
    // Drag and drop (sheet / app pop-up / tile -> page, card, dock)
    // ------------------------------------------------------------------------------------------

    /** The ordered `AppInfo.key` list of the dock of [screen] (empty = the default dock apps). */
    fun dockApps(screen: ScreenKind): Flow<List<String>> = graph.layouts.dockApps(screen)

    /**
     * Stores the dock apps of [screen] (an empty list goes back to the default dock apps). The app
     * pop-up closes: this is called when an app was dropped on the dock.
     */
    fun setDockApps(screen: ScreenKind, appKeys: List<String>) {
        _drawerOpen.value = false
        graph.appScope.launch {
            runCatching { graph.layouts.setDockApps(screen, appKeys) }
                .onFailure { Log.w(TAG, "setDockApps($screen) failed", it) }
        }
    }

    /**
     * A drag ended on the cell grid of page [pageId] at [col] / [row] (the cell under the pointer).
     * The app pop-up closes.
     *
     * - [DragPayload.Item]: an existing tile moves there ([moveItem]).
     * - [DragPayload.App]: a new 1 x 1 APP item at the cell, the nearest free cell when it is taken; a
     *   drop on an app makes a folder, on a folder adds to it, on an app card goes inside.
     * - [DragPayload.Widget]: a FoldOS built-in is placed at the cell (nearest free spot of its default
     *   size). Hosted app widgets must be bound first; the launcher root handles them before calling here.
     * - [DragPayload.Card]: a new 2 x 2 card of that style at the cell ([createCard]).
     */
    fun dropOnPage(payload: DragPayload, pageId: Long, col: Int, row: Int, stacksOn: Boolean, geometry: GridGeometry) {
        _drawerOpen.value = false
        when (payload) {
            is DragPayload.Item -> {
                if (col < 0 || row < 0 || payload.item.id <= 0L) return
                moveItem(payload.item, pageId, col, row, stacksOn, geometry)
            }
            is DragPayload.App -> placeDroppedApp(payload.app, pageId, col, row, geometry)
            is DragPayload.Widget -> {
                val entry = payload.entry
                if (entry.isBuiltIn) {
                    placeWidget(
                        pageId = pageId,
                        cols = geometry.cols,
                        rows = geometry.rows,
                        spanX = entry.defaultSpanX,
                        spanY = entry.defaultSpanY,
                        title = entry.name,
                        builtinId = entry.id,
                        appWidgetId = null,
                        wantedCol = col,
                        wantedRow = row,
                    )
                } else {
                    Log.w(TAG, "dropOnPage: hosted widget ${entry.id} must be bound by the root first")
                }
            }
            is DragPayload.Card -> createCard(pageId, payload.cardStyle, geometry, col, row)
        }
    }

    /**
     * A drag ended on the card [card] at child position [order]: an existing item goes inside (a child
     * of the same card is reordered), an app becomes a new child, a built-in widget a new widget child.
     * Hosted app widgets are bound by the launcher root first and then added through [addAppWidgetToCard].
     */
    fun dropOnCard(payload: DragPayload, card: HomeItem, order: Int) {
        _drawerOpen.value = false
        if (card.id <= 0L || !cardAccepts(card, payload)) return
        graph.appScope.launch {
            runCatching {
                when (payload) {
                    is DragPayload.Item -> {
                        val item = payload.item
                        if (item.parentId == card.id) {
                            reorderInCard(card, item, order)
                        } else {
                            putIntoCard(item, card, order)
                        }
                    }
                    is DragPayload.App -> insertCardChild(card, appChild(payload.app, card.pageId, card.id, 0), order)
                    is DragPayload.Widget -> {
                        val entry = payload.entry
                        if (entry.isBuiltIn) {
                            insertCardChild(card, widgetChild(card, entry.name, entry.id, null), order)
                        }
                    }
                    is DragPayload.Card -> Unit
                }
            }.onFailure { Log.w(TAG, "dropOnCard(${card.id}) failed", it) }
        }
    }

    /** A bound hosted app widget goes into [card] as its last child. */
    fun addAppWidgetToCard(appWidgetId: Int, title: String, card: HomeItem) {
        graph.appScope.launch {
            runCatching {
                val count = graph.layouts.children(card.id).first().size
                insertCardChild(card, widgetChild(card, title, null, appWidgetId), count)
            }.onFailure {
                Log.w(TAG, "addAppWidgetToCard(${card.id}) failed", it)
                withContext(Dispatchers.Main) { widgetHost.deleteId(appWidgetId) }
            }
        }
    }

    private suspend fun reorderInCard(card: HomeItem, item: HomeItem, order: Int) {
        val children = graph.layouts.children(card.id).first().sortedBy { it.row }.toMutableList()
        val from = children.indexOfFirst { it.id == item.id }
        if (from < 0) return
        val moved = children.removeAt(from)
        children.add(order.coerceIn(0, children.size), moved)
        children.forEachIndexed { index, child ->
            if (child.row != index || child.col != 0) graph.layouts.upsertItem(child.copy(row = index, col = 0))
        }
    }

    /** Inserts the new child [child] (id 0, parentId set) at [order], shifting the children after it. */
    private suspend fun insertCardChild(card: HomeItem, child: HomeItem, order: Int) {
        val children = graph.layouts.children(card.id).first().sortedBy { it.row }
        val at = order.coerceIn(0, children.size)
        children.drop(at).forEachIndexed { offset, existing ->
            graph.layouts.upsertItem(existing.copy(row = at + offset + 1))
        }
        graph.layouts.upsertItem(child.copy(pageId = card.pageId, parentId = card.id, row = at, col = 0))
    }

    private fun widgetChild(card: HomeItem, title: String, builtinId: String?, appWidgetId: Int?): HomeItem = HomeItem(
        id = 0L,
        pageId = card.pageId,
        kind = ItemKind.WIDGET,
        col = 0,
        row = 0,
        spanX = card.spanX.coerceAtLeast(1),
        spanY = card.spanY.coerceAtLeast(1),
        title = title,
        appWidgetId = appWidgetId,
        builtinId = builtinId,
        parentId = card.id,
    )

    private fun placeDroppedApp(app: AppInfo, pageId: Long, col: Int, row: Int, geometry: GridGeometry) {
        graph.appScope.launch {
            runCatching {
                val onPage = graph.layouts.items(pageId).first()
                if (col < 0 || row < 0) {
                    placeAppOrToast(app, pageId, 0, 0, geometry)
                    return@runCatching
                }
                val hit = onPage.firstOrNull { it.covers(col, row) }
                when {
                    hit == null -> placeAppOrToast(app, pageId, col, row, geometry)
                    hit.isCard && hit.isAppsCard -> {
                        val count = graph.layouts.children(hit.id).first().size
                        insertCardChild(hit, appChild(app, hit.pageId, hit.id, 0), count)
                    }
                    hit.isFolder -> {
                        val count = graph.layouts.children(hit.id).first().size
                        graph.layouts.upsertItem(appChild(app, hit.pageId, hit.id, count))
                    }
                    Folders.canLiveInFolder(hit) -> {
                        val folderId = graph.layouts.createFolder(hit.pageId, hit.col, hit.row, Folders.DEFAULT_TITLE, listOf(hit.id))
                        graph.layouts.upsertItem(appChild(app, hit.pageId, folderId, 1))
                    }
                    else -> placeAppOrToast(app, pageId, col, row, geometry)
                }
            }.onFailure { Log.w(TAG, "placeApp(${app.key}) on page $pageId failed", it) }
        }
    }

    /**
     * Puts [app] on the nearest free cell around [col]/[row]; when the visible grid is full the page
     * grows one row (never overlapping anything). Toasts "Added" so a tap in the sheet gets feedback.
     */
    private suspend fun placeAppOrToast(app: AppInfo, pageId: Long, col: Int, row: Int, geometry: GridGeometry): Boolean {
        val placed = graph.layouts.placeApp(app, pageId, col, row, geometry.cols, geometry.rows, growsRows = true)
        if (!placed) toastNoRoom()
        return placed
    }

    private suspend fun toastAddedToHome(label: String) {
        withContext(Dispatchers.Main) {
            Toast.makeText(getApplication<Application>(), "$label added to the home screen", Toast.LENGTH_SHORT).show()
        }
    }

    private suspend fun toastNoRoom() {
        withContext(Dispatchers.Main) {
            Toast.makeText(getApplication<Application>(), "No room on this page", Toast.LENGTH_SHORT).show()
        }
    }

    /** A new APP item inside the folder / card [parentId] (on its page), as child number [order]. */
    private fun appChild(app: AppInfo, pageId: Long, parentId: Long, order: Int): HomeItem = HomeItem(
        id = 0L,
        pageId = pageId,
        kind = ItemKind.APP,
        col = 0,
        row = order,
        packageName = app.packageName,
        activityName = app.activityName,
        title = app.label,
        parentId = parentId,
    )

    /**
     * Resizes [item] (and its stack mates) to [spanX] x [spanY] (cards never below 2 x 2); free-placed
     * items get the matching dp size too. A resize that would cover another item is refused.
     */
    fun resizeItem(item: HomeItem, spanX: Int, spanY: Int, geometry: GridGeometry) {
        val min = if (item.isCard) HomeItem.CARD_MIN_SPAN else 1
        val sx = spanX.coerceIn(min, geometry.cols.coerceAtLeast(min))
        val sy = spanY.coerceAtLeast(min)
        graph.appScope.launch {
            runCatching {
                val others = graph.layouts.items(item.pageId).first().filter { other ->
                    other.id != item.id && !other.free && (item.stackId == null || other.stackId != item.stackId)
                }
                if (others.any { it.overlapsCells(item.col, item.row, sx, sy) }) {
                    toastNoRoom()
                    return@runCatching
                }
                graph.layouts.resizeItem(item.id, sx, sy)
                if (item.free) {
                    graph.layouts.upsertItem(
                        item.copy(
                            spanX = sx,
                            spanY = sy,
                            wDp = (sx * geometry.cellWidthDp).toFloat(),
                            hDp = (sy * geometry.cellHeightDp).toFloat(),
                        ),
                    )
                }
            }.onFailure { Log.w(TAG, "resizeItem(${item.id}) failed", it) }
        }
    }

    /** Pending releases of removed app widgets' host ids, by item id (cancelled when the removal is undone). */
    private val pendingWidgetReleases = java.util.concurrent.ConcurrentHashMap<Long, Job>()

    /** Contents of a removed folder / card (children and their children), by container id, for Undo. */
    private val removedContents = java.util.concurrent.ConcurrentHashMap<Long, List<HomeItem>>()

    /**
     * Removes [item]. A hosted app widget keeps its host id for [UNDO_WINDOW_MS] plus a margin so the
     * edit overlay's Undo ([restoreItem]) can put the very same widget back; the id is released once
     * that window has passed. A folder's or card's contents are remembered for the same window.
     */
    fun deleteItem(item: HomeItem) {
        val appWidgetId = item.appWidgetId
        if (appWidgetId != null) scheduleWidgetRelease(item.id, appWidgetId)
        graph.appScope.launch {
            runCatching {
                if (item.kind == ItemKind.FOLDER || item.kind == ItemKind.CARD) {
                    val children = graph.layouts.children(item.id).first()
                    val grandchildren = children.filter { it.isFolder }.flatMap { graph.layouts.children(it.id).first() }
                    val all = children + grandchildren
                    removedContents[item.id] = all
                    all.forEach { child -> child.appWidgetId?.let { scheduleWidgetRelease(child.id, it) } }
                    graph.appScope.launch {
                        delay(UNDO_WINDOW_MS + WIDGET_RELEASE_MARGIN_MS)
                        removedContents.remove(item.id, all)
                    }
                }
                graph.layouts.deleteItem(item.id)
            }.onFailure { Log.w(TAG, "deleteItem(${item.id}) failed", it) }
        }
    }

    private fun scheduleWidgetRelease(itemId: Long, appWidgetId: Int) {
        pendingWidgetReleases.remove(itemId)?.cancel()
        pendingWidgetReleases[itemId] = graph.appScope.launch {
            delay(UNDO_WINDOW_MS + WIDGET_RELEASE_MARGIN_MS)
            withContext(Dispatchers.Main) { widgetHost.deleteId(appWidgetId) }
            pendingWidgetReleases.remove(itemId)
        }
    }

    /** Undo of [deleteItem]: writes [item] back unchanged (same id), with its contents, keeping widget host ids. */
    fun restoreItem(item: HomeItem) {
        pendingWidgetReleases.remove(item.id)?.cancel()
        val contents = removedContents.remove(item.id).orEmpty()
        contents.forEach { pendingWidgetReleases.remove(it.id)?.cancel() }
        graph.appScope.launch {
            runCatching {
                graph.layouts.upsertItem(item)
                // Folders inside a card first, so their own children find their parent row.
                contents.sortedBy { if (it.parentId == item.id) 0 else 1 }.forEach { graph.layouts.upsertItem(it) }
            }.onFailure { Log.w(TAG, "restoreItem(${item.id}) failed", it) }
        }
    }

    /**
     * "Add to home screen" from the sheet / the app pop-up menu: a new app tile on [pageId] at the first
     * free cell ([GridGeometry.cols] x [GridGeometry.rows]; the page grows a row when it is full). The
     * app pop-up closes.
     */
    fun addAppToHome(app: AppInfo, pageId: Long, geometry: GridGeometry) {
        _drawerOpen.value = false
        graph.appScope.launch {
            runCatching {
                if (placeAppOrToast(app, pageId, 0, 0, geometry)) toastAddedToHome(app.label)
            }.onFailure { Log.w(TAG, "addAppToHome(${app.key}) failed", it) }
        }
    }

    /**
     * Repairs one page so no two slots overlap and nothing sits past the right edge: items are
     * visited in reading order, one that fits where it is stays, every other one takes the first free
     * spot of its size (the grid grows downwards, so a spot always exists). Stacks move together;
     * free-placed items are left alone. Idempotent: a clean page is not written.
     */
    private suspend fun repairOverlaps(pageId: Long, screen: ScreenKind) {
        val cols = settings.value.effectiveColsFor(screen).coerceAtLeast(1)
        val items = graph.layouts.items(pageId).first().filter { it.parentId == null }
        val occupied = HashSet<Pair<Int, Int>>()
        fun fits(col: Int, row: Int, sx: Int, sy: Int): Boolean {
            if (col < 0 || row < 0 || col + sx > cols) return false
            for (r in row until row + sy) for (c in col until col + sx) if ((c to r) in occupied) return false
            return true
        }
        fun mark(col: Int, row: Int, sx: Int, sy: Int) {
            for (r in row until row + sy) for (c in col until col + sx) occupied.add(c to r)
        }
        val slots = items.filter { !(it.free && it.xDp != null) }
            .groupBy { it.stackId ?: -it.id }.values
            .sortedWith(compareBy({ it.first().row }, { it.first().col }))
        for (slot in slots) {
            val a = slot.first()
            val sx = a.spanX.coerceIn(1, cols)
            val sy = a.spanY.coerceAtLeast(1)
            if (fits(a.col, a.row, sx, sy)) {
                mark(a.col, a.row, sx, sy)
                if (sx != a.spanX) slot.forEach { graph.layouts.upsertItem(it.copy(spanX = sx)) }
                continue
            }
            var row = 0
            var col = 0
            search@ while (true) {
                col = 0
                while (col + sx <= cols) {
                    if (fits(col, row, sx, sy)) break@search
                    col++
                }
                row++
            }
            mark(col, row, sx, sy)
            slot.forEach { graph.layouts.upsertItem(it.copy(col = col, row = row, spanX = sx)) }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Pages and starter layouts
    // ------------------------------------------------------------------------------------------

    /** Appends an empty page to [screen] and scrolls to it. */
    fun addPage(screen: ScreenKind) {
        viewModelScope.launch {
            val page = runCatching { graph.layouts.addPage(screen, settings.value.styleFor(screen)) }
                .onFailure { Log.w(TAG, "addPage($screen) failed", it) }
                .getOrNull() ?: return@launch
            selectPage(page.id)
        }
    }

    /** Moves [pageId] one position left ([delta] -1) or right (+1) and keeps it selected. */
    fun movePage(pageId: Long, delta: Int, screen: ScreenKind) {
        viewModelScope.launch {
            val before = graph.layouts.pages(screen).first().sortedBy { it.index }
            val index = before.indexOfFirst { it.id == pageId }
            if (index < 0) return@launch
            runCatching { graph.layouts.movePage(pageId, (index + delta).coerceIn(0, before.lastIndex)) }
                .onFailure { Log.w(TAG, "movePage($pageId) failed", it) }
            selectPage(pageId)
        }
    }

    /**
     * Deletes [pageId] with its items (releasing any app widget ids) and shows the page before it (the
     * new first page when the first one was deleted). A screen always keeps one page: the last page is
     * never deleted.
     */
    fun deletePage(pageId: Long, screen: ScreenKind) {
        viewModelScope.launch {
            val before = graph.layouts.pages(screen).first().sortedBy { it.index }
            val deletedIndex = before.indexOfFirst { it.id == pageId }
            if (deletedIndex < 0 || before.size <= 1) return@launch
            val items = runCatching {
                val all = graph.layouts.allPagesWithItems().firstOrNull { it.first.id == pageId }?.second.orEmpty()
                graph.layouts.deletePage(pageId)
                all
            }.onFailure { Log.w(TAG, "deletePage($pageId) failed", it) }.getOrNull() ?: return@launch
            // The repository ignores an unknown id or a screen's last page without failing, so confirm the
            // page is really gone before the host ids are released (a surviving row would render blank).
            val remaining = withTimeoutOrNull(DELETE_PAGE_WAIT_MS) { pages(screen).first { all -> all.none { it.id == pageId } } }
                ?.sortedBy { it.index } ?: return@launch
            runCatching {
                withContext(Dispatchers.Main) {
                    items.forEach { item -> item.appWidgetId?.let { widgetHost.deleteId(it) } }
                }
            }.onFailure { Log.w(TAG, "deletePage($pageId) host release failed", it) }
            remaining.getOrNull((deletedIndex - 1).coerceAtLeast(0))?.let { selectPage(it.id) }
        }
    }

    /**
     * Applies the starter [starter] (Settings > Starter layouts, a preset, an AI command): with
     * [replacePageId] that page's items are replaced by the starter's content, otherwise a new page is
     * appended and seeded. The seeded page is scrolled to. [geometry] gives the columns and visible rows.
     */
    fun applyStarter(screen: ScreenKind, starter: HomeStyle, replacePageId: Long?, geometry: GridGeometry) {
        viewModelScope.launch {
            val list = apps.first { it.isNotEmpty() }
            val pageId = runCatching {
                StarterLayouts.apply(graph.layouts, screen, starter, list, geometry.cols, geometry.rows, replacePageId)
            }.onFailure { Log.w(TAG, "applyStarter($starter) failed", it) }.getOrNull() ?: return@launch
            // Remember the choice without re-triggering the watcher below.
            StyleRestoreGate.arm(settings.value, settings.value.withStarter(screen, starter))
            updateSettings { it.withStarter(screen, starter) }
            selectPage(pageId)
        }
    }

    /**
     * Settings > Home style / a preset / the AI changed `styleCover` / `styleInner`: the first page of
     * that screen is re-seeded from the starter (a screen without pages yet is left to [ensureDefaultLayout]).
     */
    private suspend fun applyStarterToScreen(screen: ScreenKind, starter: HomeStyle) {
        runCatching {
            val first = graph.layouts.pages(screen).first().minByOrNull { it.index } ?: return@runCatching
            val list = apps.first { it.isNotEmpty() }
            val cols = settings.value.effectiveColsFor(screen)
            StarterLayouts.apply(graph.layouts, screen, starter, list, cols, StarterLayouts.DEFAULT_ROWS, first.id)
        }.onFailure { Log.w(TAG, "applyStarterToScreen($screen) failed", it) }
    }

    // ------------------------------------------------------------------------------------------
    // Adding widgets and cards (sheet taps, drops)
    // ------------------------------------------------------------------------------------------

    /**
     * Adds the built-in [entry] on [pageId]: at [wantedCol] / [wantedRow] when given (the nearest free
     * spot of its default size), else at the first free cell of a [cols] x [rows] grid; the page grows a
     * row when it is full.
     */
    fun addBuiltInWidget(entry: WidgetEntry, pageId: Long, cols: Int, rows: Int, wantedCol: Int? = null, wantedRow: Int? = null) {
        placeWidget(
            pageId = pageId,
            cols = cols,
            rows = rows,
            spanX = entry.defaultSpanX,
            spanY = entry.defaultSpanY,
            title = entry.name,
            builtinId = entry.id,
            appWidgetId = null,
            wantedCol = wantedCol,
            wantedRow = wantedRow,
        )
    }

    /** Adds the bound and configured app widget [appWidgetId]; see [addBuiltInWidget] for the placement. */
    fun addAppWidget(
        appWidgetId: Int,
        title: String,
        spanX: Int,
        spanY: Int,
        pageId: Long,
        cols: Int,
        rows: Int,
        wantedCol: Int? = null,
        wantedRow: Int? = null,
    ) {
        placeWidget(
            pageId = pageId,
            cols = cols,
            rows = rows,
            spanX = spanX,
            spanY = spanY,
            title = title,
            builtinId = null,
            appWidgetId = appWidgetId,
            wantedCol = wantedCol,
            wantedRow = wantedRow,
        )
    }

    private fun placeWidget(
        pageId: Long,
        cols: Int,
        rows: Int,
        spanX: Int,
        spanY: Int,
        title: String,
        builtinId: String?,
        appWidgetId: Int?,
        wantedCol: Int?,
        wantedRow: Int?,
    ) {
        graph.appScope.launch {
            runCatching {
                val items = graph.layouts.items(pageId).first().filter { !it.free }
                val sx = spanX.coerceIn(1, cols.coerceAtLeast(1))
                val sy = spanY.coerceIn(1, rows.coerceAtLeast(1))
                val cell = if (wantedCol != null && wantedRow != null) {
                    nearestFreeCell(items, cols, rows + sy, sx, sy, wantedCol, wantedRow)
                } else {
                    firstFreeCell(items, cols, rows, sx, sy)
                }
                if (cell == null) {
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
                        col = cell.first,
                        row = cell.second,
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

    /**
     * Adds a new card of [cardStyle] (`HomeItem.CARD_*`) to [pageId]: 2 x 2 cells at [wantedCol] /
     * [wantedRow] (nearest free spot) or at the first free spot; the page grows when it is full.
     */
    fun createCard(pageId: Long, cardStyle: String, geometry: GridGeometry, wantedCol: Int? = null, wantedRow: Int? = null) {
        graph.appScope.launch {
            runCatching {
                val items = graph.layouts.items(pageId).first().filter { !it.free }
                val span = HomeItem.CARD_MIN_SPAN
                val sx = span.coerceAtMost(geometry.cols.coerceAtLeast(1))
                val cell = if (wantedCol != null && wantedRow != null) {
                    nearestFreeCell(items, geometry.cols, geometry.rows + span, sx, span, wantedCol, wantedRow)
                } else {
                    firstFreeCell(items, geometry.cols, geometry.rows, sx, span)
                }
                if (cell == null) {
                    toastNoRoom()
                    return@runCatching
                }
                graph.layouts.createCard(pageId, cell.first, cell.second, sx, span, cardStyle, null)
            }.onFailure { Log.w(TAG, "createCard on page $pageId failed", it) }
        }
    }

    init {
        // Settings > Home > Starter layout / presets / AI: a change there re-seeds the first page of THAT
        // screen only. The first emission is the stored value, not a change, so it only primes the comparison.
        viewModelScope.launch {
            var previous: Pair<HomeStyle, HomeStyle>? = null
            graph.settings.settings
                .map { it.styleCover to it.styleInner }
                .distinctUntilChanged()
                .collect { next ->
                    val before = previous
                    previous = next
                    if (before == null) return@collect
                    // A backup restore / settings reset / applyStarter already settled the pages.
                    if (StyleRestoreGate.consume(next)) return@collect
                    if (next.first != before.first) applyStarterToScreen(ScreenKind.COVER, next.first)
                    if (next.second != before.second) applyStarterToScreen(ScreenKind.INNER, next.second)
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

        /** Extra time past the Undo window before a removed app widget's host id is released. */
        const val WIDGET_RELEASE_MARGIN_MS = 1500L

        /** How long deletePage waits for the page list to drop the deleted page before it stops looking. */
        const val DELETE_PAGE_WAIT_MS = 3000L
    }
}

/** The settings with the starter of [screen] recorded as [starter] (`styleCover` / `styleInner`). */
internal fun LauncherSettings.withStarter(screen: ScreenKind, starter: HomeStyle): LauncherSettings = when (screen) {
    ScreenKind.COVER -> copy(styleCover = starter)
    ScreenKind.INNER, ScreenKind.FLEX -> copy(styleInner = starter)
}

/** True when this item's grid cells include ([col], [row]); free-placed items use their grid fallback. */
internal fun HomeItem.covers(col: Int, row: Int): Boolean {
    val sx = spanX.coerceAtLeast(1)
    val sy = spanY.coerceAtLeast(1)
    return col >= this.col && col < this.col + sx && row >= this.row && row < this.row + sy
}

/** True when the cells of this item overlap the [sx] x [sy] rectangle whose top-left cell is [col] / [row]. */
internal fun HomeItem.overlapsCells(col: Int, row: Int, sx: Int, sy: Int): Boolean =
    this.col < col + sx && col < this.col + spanX.coerceAtLeast(1) &&
        this.row < row + sy && row < this.row + spanY.coerceAtLeast(1)

/**
 * The free [sx] x [sy] spot on a [cols] x [rows] grid closest to [wantedCol] / [wantedRow] (smallest
 * |dcol| + |drow|, ties to the lower row, then the lower column); null when [items] leave none.
 */
internal fun nearestFreeCell(
    items: List<HomeItem>,
    cols: Int,
    rows: Int,
    sx: Int,
    sy: Int,
    wantedCol: Int,
    wantedRow: Int,
): Pair<Int, Int>? {
    val columns = cols.coerceAtLeast(1)
    val w = sx.coerceIn(1, columns)
    val h = sy.coerceAtLeast(1)
    var best: Pair<Int, Int>? = null
    var bestDistance = Int.MAX_VALUE
    for (r in 0..(rows - h)) {
        for (c in 0..(columns - w)) {
            if (items.any { it.overlapsCells(c, r, w, h) }) continue
            val distance = kotlin.math.abs(c - wantedCol) + kotlin.math.abs(r - wantedRow)
            if (distance < bestDistance) {
                bestDistance = distance
                best = c to r
            }
        }
    }
    return best
}

/**
 * The first cell, in reading order, where a [spanX] x [spanY] item fits among [items] on a
 * [cols] x [rows] grid. When the grid is full the item goes to the first column of the row below
 * the lowest occupied cell (the page grows its rows to fit).
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
