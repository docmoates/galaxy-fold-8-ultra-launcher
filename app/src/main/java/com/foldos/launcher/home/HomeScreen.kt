package com.foldos.launcher.home

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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.core.fold.FoldState
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.math.max

/**
 * The launcher home: a horizontal pager over the hub page (when enabled, on the configured side)
 * and the home pages of [screen], with the page indicator and [dock] below. In Flex Mode
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
 * @param perPageStyle when true each page renders with its own [HomePage.style] (mix and match);
 *   when false (default) every page follows Settings > Home > Cover / Inner screen style.
 * @param onCurrentPageChanged reports the home page the pager has settled on (null while the hub
 *   page is showing), so edit mode and the widget picker know which page they work on.
 * @param pageSelectRequest scrolls the pager to the requested page whenever a new request arrives
 *   (the edit overlay's page arrows and edge drops).
 *
 * Edit-mode hooks are read from [LocalHomeCallbacks]; provide it around this composable.
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
    perPageStyle: Boolean = false,
    onCurrentPageChanged: ((HomePage?) -> Unit)? = null,
    pageSelectRequest: PageSelectRequest? = null,
) {
    val appsByKey = remember(apps) { apps.associateBy { it.key } }
    val sortedApps = remember(apps) { apps.sortedBy { it.label.lowercase() } }
    val sortedPages = remember(pages) { pages.sortedBy { it.index } }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val callbacks = LocalHomeCallbacks.current

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
        perPageStyle = perPageStyle,
    )

    Box(
        modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .onGloballyPositioned { origin = it.positionInWindow() },
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
            )
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
    LaunchedEffect(hubOn, hubLeft, total) {
        if (pagerState.currentPage >= total) pagerState.scrollToPage(firstHome)
    }
    // Home key / back press: return to the default home page.
    LaunchedEffect(homeRequest) {
        if (homeRequest > 0 && pagerState.currentPage != firstHome) pagerState.animateScrollToPage(firstHome)
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
            pagerState.animateScrollToPage(index + firstHome)
        }
    }

    Column(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
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
        HomePageIndicator(
            pageCount = homeCount,
            currentPage = if (current == hubIndex) -1 else current - firstHome,
            hubOn = hubOn,
            hubOnLeft = hubLeft,
            hubSelected = current == hubIndex,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 10.dp),
        )
        dock()
    }
}

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
            // Outside the item grid the cell is unknown (-1 / -1); the grid reports exact cells.
            .homeLongPress { ctx.emptyLongPress(pageId, -1, -1) }
            .homeSwipeUp(ctx.onOpenDrawer)
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
