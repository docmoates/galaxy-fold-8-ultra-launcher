package com.foldos.launcher.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.a11y.a11yDirectionalFocus
import com.foldos.launcher.a11y.a11yTypeToSearch
import com.foldos.launcher.a11y.rememberA11yPagerScroll
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.fold.FoldState
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.ui.DragController
import com.foldos.launcher.core.ui.DragPayload
import com.foldos.launcher.edit.LocalEditHomeRoot
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlin.math.max

/**
 * The launcher home: a horizontal pager over the hub page (when enabled, on the configured side)
 * and the home pages of [screen] (every page one free-form grid canvas since Round 2.0), with the
 * page indicator and [dock] below. In Flex Mode
 * ([FoldState.kind] is [ScreenKind.FLEX], i.e. half opened with a horizontal hinge, and the hinge
 * bounds are known) the first page is shown above the hinge and the dock plus a six-app row below
 * it. A half-opened book posture (vertical hinge) keeps the normal paged layout with a gutter over
 * the hinge.
 *
 * The composable pads for the system bars itself; pass a [modifier] without inset padding.
 *
 * @param screen which layout store to show, normally `effectiveScreen(settings, fold)`.
 * @param pages the home pages of [screen], any order (sorted by [HomePage.index] here).
 * @param itemsForPage items of one page by id, usually `layouts::items`.
 * @param apps all launchable apps; items are resolved against them by package/activity.
 * @param dock the dock for this screen, rendered by the dock composable of the Home/Dock agent.
 * @param searchBar optional search bar shown at the top of every home page.
 * @param hub optional hub page content; a placeholder is shown when null and the hub is on.
 * @param homeRequest a counter; every increment scrolls the pager back to the first home page.
 * @param onCurrentPageChanged reports the home page the pager has settled on (null while the hub
 *   page is showing), so edit mode and the widget picker know which page they work on.
 * @param pageSelectRequest scrolls the pager to the requested page whenever a new request arrives
 *   (the edit overlay's page arrows and edge drops).
 * @param editMode true while the live-edit overlay is up: an item long press then goes straight to
 *   `HomeCallbacks.onItemLongPress` (Phase 1 behaviour) instead of opening the item popup, and any
 *   open popup / folder sheet closes.
 * @param pageModifier applied once to the whole home root (pager, page indicator and dock; not while
 *   the hub page is showing), where the `gestures/` package hooks its detector, so a swipe that
 *   starts on the dock or the indicator is seen too.
 * @param swipeUpOpensDrawer true (default) keeps the root's own swipe-up-to-drawer detector; pass
 *   false while [pageModifier] carries the `gestures/` detector, which binds SWIPE_UP itself, so a
 *   swipe up does not open the drawer twice.
 * @param onHubPageChanged called with true when the pager settles on the hub page and false when it
 *   leaves it. The page indicator and the home dock are hidden on the hub page (the hub draws its
 *   own tab bar), so the host can hide anything else that belongs to the home dock (the rail dock).
 * @param dragController the launcher-wide drag controller. Home pages with a cell grid register
 *   the drop target `page:<pageId>` (list / hybrid pages register the whole body); a drop calls
 *   [onDropPayload] with the cell under the pointer (-1 / -1 on pages without a cell grid). Dragging
 *   near the left / right edge for 600 ms flips the pager. Tiles can be picked up (long press, then
 *   move) as [DragPayload.Item] while edit mode is off.
 * @param onDropPayload see [dragController].
 * @param onDropOnCard Round 2.0: a payload dropped on a card (`DragTargetIds.card(id)`), with the
 *   child position under the pointer; null = cards take no drops.
 * @param hubSelectRequest a counter; every increment scrolls the pager to the hub page (gesture
 *   action `TYPE_HUB`). Ignored while the hub is off or in Flex Mode.
 * @param keyboardRefocusKey any value that changes when an overlay (drawer, search, picker) closes; the home
 *   then takes keyboard focus back so type-to-search and arrow keys keep working.
 *
 * Edit-mode hooks are read from [LocalHomeCallbacks], folder / pair / context-menu slots from
 * [LocalHomeItemHooks]; provide both around this composable. Badges come from
 * `LocalAppGraph.current.badges.counts` (in `settings.badgeStyle`, only while the Badges feature
 * is on) and `hiddenApps` are honoured through the already filtered [apps] list: an item whose app
 * is hidden leaves its cell empty.
 */
@Composable
fun HomeScreen(
    screen: ScreenKind,
    fold: FoldState,
    settings: LauncherSettings,
    pages: List<HomePage>,
    itemsForPage: (Long) -> Flow<List<HomeItem>>,
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onOpenDrawer: () -> Unit,
    onLongPressHome: () -> Unit,
    dock: @Composable () -> Unit,
    searchBar: @Composable (() -> Unit)?,
    hub: @Composable (() -> Unit)?,
    homeRequest: Int = 0,
    modifier: Modifier = Modifier,
    onCurrentPageChanged: ((HomePage?) -> Unit)? = null,
    pageSelectRequest: PageSelectRequest? = null,
    editMode: Boolean = false,
    pageModifier: Modifier = Modifier,
    swipeUpOpensDrawer: Boolean = true,
    hubSelectRequest: Int = 0,
    keyboardRefocusKey: Any? = null,
    onHubPageChanged: ((isHub: Boolean) -> Unit)? = null,
    dragController: DragController? = null,
    onDropPayload: ((payload: DragPayload, pageId: Long, col: Int, row: Int) -> Unit)? = null,
    onDropOnCard: ((payload: DragPayload, card: HomeItem, order: Int) -> Unit)? = null,
) {
    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    // True while the pager shows the hub page: the page indicator and the home dock slide away
    // (the hub has its own tab bar) and the home swipe detectors step aside for its scrolling content.
    var onHub by remember { mutableStateOf(false) }
    val reportHub = rememberUpdatedState(onHubPageChanged)
    val sortedApps = remember(apps) { apps.sortedBy { it.label.lowercase() } }
    val sortedPages = remember(pages) { pages.sortedBy { it.index } }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val callbacks = LocalHomeCallbacks.current
    val hooks = LocalHomeItemHooks.current
    val graph = LocalAppGraph.current
    val editHomeRoot = LocalEditHomeRoot.current

    // Notification badges (Settings > Notifications): only subscribed while they can show.
    val badgesOn = settings.feature(LauncherSettings.FEATURE_BADGES) && settings.badgeStyle != LauncherSettings.BADGE_OFF
    val countsFlow: Flow<Map<String, Int>> = remember(graph, badgesOn) {
        if (badgesOn) graph.badges.counts else flowOf(emptyMap())
    }
    val badges by countsFlow.collectAsStateWithLifecycle(initialValue = emptyMap())

    // Long-press popup and open folder (both pop up at their tile); they close when edit mode takes over.
    val pressed = remember { mutableStateOf<HomePressedItem?>(null) }
    val folderSheet = remember { mutableStateOf<HomeItem?>(null) }
    LaunchedEffect(editMode) {
        if (editMode) {
            pressed.value = null
            folderSheet.value = null
        }
    }

    // FoldStateObserver reports FLEX only for HALF_OPENED with a horizontal hinge (table-top).
    val flex = fold.kind == ScreenKind.FLEX && fold.hingeBounds != null
    val gutter = if (fold.kind == ScreenKind.INNER && fold.hingeBounds != null) HingeGutterWidth else 0.dp

    val ctx = HomeContext(
        screen = screen,
        fold = fold,
        settings = settings,
        apps = apps,
        appsByKey = appsByKey,
        sortedApps = sortedApps,
        onLaunch = onLaunch,
        onOpenDrawer = onOpenDrawer,
        onLongPressHome = onLongPressHome,
        hingeGutter = gutter,
        callbacks = callbacks,
        hooks = hooks,
        badges = badges,
        editMode = editMode,
        pressed = pressed,
        folderSheet = folderSheet,
        pageModifier = pageModifier,
        swipeUpOpensDrawer = swipeUpOpensDrawer,
        dragController = dragController,
        onDropPayload = onDropPayload,
        onDropOnCard = onDropOnCard,
    )

    // Swipes (gestures/ detector: swipe up / down, two-finger, pinch, double tap; or the swipe-up
    // fallback) are detected on the whole home root, so a swipe that starts on the dock, the page
    // indicator or the dock handle opens the drawer just like one on a page. Children that scroll
    // or flip (stacks, lists) still win. The hub page keeps its own vertical scrolling.
    LaunchedEffect(flex) {
        // Flex mode has no hub page: forget a hub state left over from the paged layout.
        if (flex && onHub) {
            onHub = false
            reportHub.value?.invoke(false)
        }
    }
    val swipesActive = flex || !onHub
    val rootSwipes: Modifier = if (swipesActive) {
        Modifier.then(ctx.pageModifier).homeSwipeUp(ctx.swipeUpOpensDrawer, ctx.onOpenDrawer)
    } else {
        Modifier
    }

    // One label size (Settings > Home > Label size) for every tile under this home.
    ProvideLabelSize(settings) {
        Box(
            modifier
                .fillMaxSize()
                // Hardware keyboard: arrow keys move focus between tiles; a printable key opens search
                // (LocalKeyboardShortcuts.onTypeToSearch, provided by the launcher root).
                .a11yDirectionalFocus()
                .a11yTypeToSearch(refocusKey = keyboardRefocusKey to editMode)
                .windowInsetsPadding(WindowInsets.systemBars)
                .onGloballyPositioned { coordinates ->
                    // Unscaled window position (edit mode draws the home scaled; see LocalEditHomeRoot).
                    val homeRoot = editHomeRoot?.coordinates
                    origin = if (homeRoot != null && homeRoot.isAttached && coordinates.isAttached) {
                        homeRoot.localPositionOf(coordinates, Offset.Zero)
                    } else {
                        coordinates.positionInWindow()
                    }
                }
                .then(rootSwipes),
        ) {
            if (flex) {
                val first = sortedPages.firstOrNull()
                val items = homePageItems(first, itemsForPage)
                val reportPage = rememberUpdatedState(onCurrentPageChanged)
                LaunchedEffect(first?.id) { reportPage.value?.invoke(first) }
                HomeFlexLayout(
                    ctx = ctx,
                    firstPage = first,
                    items = items,
                    originY = origin.y,
                    dock = dock,
                    searchBar = searchBar,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                HomePagedLayout(
                    ctx = ctx,
                    pages = sortedPages,
                    itemsForPage = itemsForPage,
                    dock = dock,
                    searchBar = searchBar,
                    hub = hub,
                    homeRequest = homeRequest,
                    onCurrentPageChanged = onCurrentPageChanged,
                    pageSelectRequest = pageSelectRequest,
                    hubSelectRequest = hubSelectRequest,
                    onHubChanged = { isHub ->
                        onHub = isHub
                        reportHub.value?.invoke(isHub)
                    },
                )
            }
        }
    }
}

/**
 * A request to scroll the home pager to [pageId]. [serial] makes consecutive requests for the same
 * page distinct, so each one is honoured.
 */
@Immutable
data class PageSelectRequest(val pageId: Long, val serial: Int)

@Composable
private fun HomePagedLayout(
    ctx: HomeContext,
    pages: List<HomePage>,
    itemsForPage: (Long) -> Flow<List<HomeItem>>,
    dock: @Composable () -> Unit,
    searchBar: @Composable (() -> Unit)?,
    hub: @Composable (() -> Unit)?,
    homeRequest: Int,
    onCurrentPageChanged: ((HomePage?) -> Unit)?,
    pageSelectRequest: PageSelectRequest?,
    hubSelectRequest: Int = 0,
    onHubChanged: (Boolean) -> Unit = {},
) {
    val hubOn = ctx.settings.hubOn
    val hubLeft = ctx.settings.hubPos == LauncherSettings.HUB_POS_LEFT
    val homeCount = max(1, pages.size)
    val total = homeCount + (if (hubOn) 1 else 0)
    val hubIndex = when {
        !hubOn -> -1
        hubLeft -> 0
        else -> total - 1
    }
    val firstHome = if (hubOn && hubLeft) 1 else 0

    val pagerState = rememberPagerState(initialPage = firstHome) { total }
    // Animated scroll to a page, instant while Settings > Accessibility > Reduce motion is on.
    val scrollToPage = rememberA11yPagerScroll(pagerState)
    LaunchedEffect(hubOn, hubLeft, total) {
        if (pagerState.currentPage >= total) pagerState.scrollToPage(firstHome)
    }
    // Home key / back press: return to the default home page. The view model's counters are never
    // reset, so each one is honoured once: the value seen when this layout enters composition (a
    // Flex <-> paged toggle, activity recreation) counts as handled and only later increments scroll.
    var handledHome by rememberSaveable { mutableIntStateOf(homeRequest) }
    LaunchedEffect(homeRequest) {
        if (homeRequest == handledHome || homeRequest <= 0) return@LaunchedEffect
        handledHome = homeRequest
        if (pagerState.currentPage != firstHome) scrollToPage(firstHome)
    }
    // Gesture action "Open the Hub": scroll to the hub page (a no-op while the hub is off).
    var handledHub by rememberSaveable { mutableIntStateOf(hubSelectRequest) }
    LaunchedEffect(hubSelectRequest) {
        if (hubSelectRequest == handledHub || hubSelectRequest <= 0 || hubIndex < 0) return@LaunchedEffect
        handledHub = hubSelectRequest
        if (pagerState.currentPage != hubIndex) scrollToPage(hubIndex)
    }
    // Report the settled page (null for the hub) whenever it, or the page list, changes.
    val reportPage = rememberUpdatedState(onCurrentPageChanged)
    LaunchedEffect(pagerState, pages, hubIndex, firstHome) {
        snapshotFlow { pagerState.currentPage }.collect { index ->
            reportPage.value?.invoke(if (index == hubIndex) null else pages.getOrNull(index - firstHome))
        }
    }
    // Edit overlay page arrows / edge drops. Each request is honoured once: `pages` stays in the
    // key only so a request for a page that has not arrived yet (addPage) is retried when the
    // list re-emits, never so an old request snaps the pager back after the user swiped away.
    var handledSerial by remember { mutableIntStateOf(0) }
    LaunchedEffect(pageSelectRequest, pages) {
        val request = pageSelectRequest ?: return@LaunchedEffect
        if (request.serial == handledSerial) return@LaunchedEffect
        val index = pages.indexOfFirst { it.id == request.pageId }
        if (index < 0) return@LaunchedEffect
        handledSerial = request.serial
        if (index + firstHome != pagerState.currentPage) {
            scrollToPage(index + firstHome)
        }
    }

    // The hub page has its own tab bar: the page indicator and the home dock are not shown on it.
    val onHubPage = hubIndex >= 0 && pagerState.currentPage == hubIndex
    val latestHubChanged = rememberUpdatedState(onHubChanged)
    LaunchedEffect(onHubPage) { latestHubChanged.value(onHubPage) }

    // Dragging an app (or tile) near the left / right edge for 600 ms flips to the next home page.
    val dragController = ctx.dragController
    var pagerBounds by remember { mutableStateOf(Rect.Zero) }
    val edgePx = with(LocalDensity.current) { PAGE_EDGE_ZONE.toPx() }
    if (dragController != null) {
        LaunchedEffect(dragController, pagerState, total, hubIndex) {
            snapshotFlow {
                if (dragController.payload.value == null) {
                    0
                } else {
                    val x = dragController.pointerInRoot.value.x
                    val bounds = pagerBounds
                    when {
                        bounds.width <= 0f -> 0
                        x <= bounds.left + edgePx -> -1
                        x >= bounds.right - edgePx -> 1
                        else -> 0
                    }
                }
            }.distinctUntilChanged().collectLatest { direction ->
                if (direction == 0) return@collectLatest
                while (true) {
                    delay(PAGE_EDGE_DELAY_MS)
                    val target = pagerState.currentPage + direction
                    if (target < 0 || target >= total || target == hubIndex) break
                    scrollToPage(target)
                }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .onGloballyPositioned { pagerBounds = it.boundsInRoot() },
            key = { index ->
                if (index == hubIndex) "hub" else (pages.getOrNull(index - firstHome)?.id?.toString() ?: "empty-$index")
            },
        ) { index ->
            if (index == hubIndex) {
                HomeHubPage(hub)
            } else {
                // Only the settled current page publishes its grid geometry, so a page sliding by
                // never reports a moving origin to the edit overlay.
                val settled = index == pagerState.currentPage && !pagerState.isScrollInProgress
                HomePageColumn(ctx, pages.getOrNull(index - firstHome), itemsForPage, searchBar, settled)
            }
        }

        val current = pagerState.currentPage
        AnimatedVisibility(
            visible = !onHubPage,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // Long press on the strip around the dock enters edit mode like the page does.
                    .homeLongPress { ctx.emptyLongPress(0L, -1, -1) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                HomePageIndicator(
                    pageCount = homeCount,
                    currentPage = if (current == hubIndex) -1 else current - firstHome,
                    hubOn = hubOn,
                    hubOnLeft = hubLeft,
                    hubSelected = current == hubIndex,
                    modifier = Modifier.padding(vertical = 10.dp),
                )
                dock()
            }
        }
    }
}

/** How close to the left / right edge a drag has to get before it starts flipping pages. */
private val PAGE_EDGE_ZONE = 44.dp

/** How long a drag has to rest at the edge before the pager flips (and between further flips). */
private const val PAGE_EDGE_DELAY_MS = 600L

@Composable
private fun HomePageColumn(
    ctx: HomeContext,
    page: HomePage?,
    itemsForPage: (Long) -> Flow<List<HomeItem>>,
    searchBar: @Composable (() -> Unit)?,
    publishGeometry: Boolean,
) {
    val items = homePageItems(page, itemsForPage)
    val pageId = page?.id ?: 0L
    Column(
        Modifier
            .fillMaxSize()
            // The swipe detectors (HomeScreen.pageModifier, swipe-up fallback) sit on the HomeScreen root.
            // Outside the item grid the cell is unknown (-1 / -1); the grid reports exact cells.
            .homeLongPress { ctx.emptyLongPress(pageId, -1, -1) }
            .padding(horizontal = HomeSidePadding, vertical = 10.dp),
    ) {
        if (searchBar != null) {
            searchBar()
            Spacer(Modifier.height(14.dp))
        }
        HomePageBody(ctx, page, items, Modifier.weight(1f).fillMaxWidth(), publishGeometry)
    }
}

/** Collects the items of [page] (empty list for a missing page). */
@Composable
private fun homePageItems(page: HomePage?, itemsForPage: (Long) -> Flow<List<HomeItem>>): List<HomeItem> {
    val pageId = page?.id
    val flow = remember(pageId) {
        if (pageId != null) itemsForPage(pageId) else flowOf<List<HomeItem>>(emptyList())
    }
    val items by flow.collectAsStateWithLifecycle(initialValue = emptyList())
    return items
}

/** The hub page, or a placeholder panel until the Hub agent provides one. */
@Composable
private fun HomeHubPage(hub: @Composable (() -> Unit)?) {
    if (hub != null) {
        Box(Modifier.fillMaxSize()) { hub() }
        return
    }
    val tokens = LocalFoldOsTokens.current
    Box(
        Modifier
            .fillMaxSize()
            .padding(horizontal = HomeSidePadding, vertical = 10.dp),
    ) {
        HomeSurfacePanel(Modifier.fillMaxSize()) {
            Text(
                text = "Hub",
                color = tokens.onSurface,
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Today, AI, Mail, Calendar and Contacts will live here.",
                color = tokens.onSurface.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
