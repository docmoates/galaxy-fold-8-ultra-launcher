package com.foldos.launcher.home

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.fold.FoldState
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.GridMode
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Width of the gap left over the hinge on the inner screen (mock-up `.hinge` is 18dp wide). */
internal val HingeGutterWidth: Dp = 18.dp

/** Horizontal page padding (mock-up `.home` padding 16px). */
internal val HomeSidePadding: Dp = 16.dp

/**
 * Everything a page needs to render, bundled so the page composables stay short.
 * Built once per [HomeScreen] composition; the two [MutableState]s are remembered by [HomeScreen].
 */
internal class HomeContext(
    val screen: ScreenKind,
    val fold: FoldState,
    val settings: LauncherSettings,
    val apps: List<AppInfo>,
    val appsByKey: Map<String, AppInfo>,
    val sortedApps: List<AppInfo>,
    val onLaunch: (AppInfo) -> Unit,
    val onOpenDrawer: () -> Unit,
    val onLongPressHome: () -> Unit,
    /** 0.dp when no gutter should be left for the hinge. */
    val hingeGutter: Dp,
    /** Edit-mode hooks from [LocalHomeCallbacks]; every hook falls back to [onLongPressHome]. */
    val callbacks: HomeCallbacks = HomeCallbacks(),
    /** True when [HomePage.style] decides a page's style instead of the per-screen setting. */
    val perPageStyle: Boolean = false,
    /** Folder / pair / context-menu slots from [LocalHomeItemHooks]; null slots use the home fallbacks. */
    val hooks: HomeItemHooks = HomeItemHooks(),
    /** `BadgeRepository.counts` (package -> unread count); empty while badges are off. */
    val badges: Map<String, Int> = emptyMap(),
    /** True while the live-edit overlay is up: an item long press goes straight to the edit hooks. */
    val editMode: Boolean = false,
    /** The item whose long-press popup (notification preview + context menu) is open. */
    val pressed: MutableState<HomePressedItem?> = mutableStateOf(null),
    /** The folder whose `items.FolderSheet` is open (only used while [HomeItemHooks.onOpenFolder] is null). */
    val folderSheet: MutableState<HomeItem?> = mutableStateOf(null),
    /** Applied to every page container (the `gestures/` detector from `HomeScreen.pageModifier`). */
    val pageModifier: Modifier = Modifier,
    /**
     * True when the page's own swipe-up detector opens the drawer (Phase 1 behaviour). The host sets
     * it false while the `gestures/` detector in [pageModifier] owns the swipes, so SWIPE_UP does
     * not fire twice.
     */
    val swipeUpOpensDrawer: Boolean = true,
) {
    // Easy mode (settings.largeIcons) swaps in the larger icons, fewer columns and always-on labels.
    val cols: Int get() = settings.effectiveColsFor(screen).coerceAtLeast(1)
    val iconSize: Dp get() = settings.effectiveIconSizeDp.dp
    val labels: Boolean get() = settings.effectiveLabels
    val isInner: Boolean get() = screen != ScreenKind.COVER

    /** Settings > Home > Placement is "Free placement": items with dp bounds leave the grid. */
    val freeMode: Boolean get() = settings.gridMode == GridMode.FREE

    /** Widget stacks flip; when the feature is off only the front item of a stack shows. */
    val stacksOn: Boolean get() = settings.feature(LauncherSettings.FEATURE_STACKS)

    /** Smart rules (`rule:HH:MM-HH:MM` on stack members) pick the stack face by time of day. */
    val smartOn: Boolean get() = settings.feature(LauncherSettings.FEATURE_SMART)

    /** App pairs launch both apps; when off a pair tile launches its first app only. */
    val pairsOn: Boolean get() = settings.feature(LauncherSettings.FEATURE_PAIRS)

    /** Settings > Notifications > Badges on, and a style other than "off". */
    val badgesOn: Boolean
        get() = settings.feature(LauncherSettings.FEATURE_BADGES) && settings.badgeStyle != LauncherSettings.BADGE_OFF

    /** `BADGE_COUNT` / `BADGE_DOT` / `BADGE_OFF`, passed to [HomeBadgedBox]. */
    val badgeStyle: String get() = settings.badgeStyle

    /** Long-press notification previews (Settings > Notifications) need the badges feature too. */
    val previewOn: Boolean
        get() = settings.notificationPreview && settings.feature(LauncherSettings.FEATURE_BADGES)

    /** Unread count for one package; 0 when badges are off. */
    fun badgeFor(packageName: String?): Int =
        if (!badgesOn || packageName == null) 0 else badges[packageName] ?: 0

    /** Unread count for an item (app, or both apps of a pair); 0 for widgets and when badges are off. */
    fun badgeFor(item: HomeItem): Int = if (badgesOn) homeBadgeCount(badges, item) else 0

    /** Unread count summed over a folder's children; 0 when badges are off. */
    fun badgeFor(children: List<HomeItem>): Int = if (badgesOn) homeBadgeCount(badges, children) else 0

    /** The style a page renders with. */
    fun styleFor(page: HomePage?): HomeStyle =
        if (perPageStyle && page != null) page.style else settings.styleFor(screen)

    /**
     * Long press on an item. In edit mode (or when the items package takes it over through
     * [HomeItemHooks.onItemLongPress]) nothing more happens here; otherwise the popup with the
     * notification preview and the context menu opens at [anchor] (the tile's window bounds).
     */
    fun longPress(item: HomeItem, anchor: Rect? = null) {
        if (editMode) {
            editLongPress(item)
            return
        }
        val hook = hooks.onItemLongPress
        if (hook != null && hook(item, anchor)) return
        pressed.value = HomePressedItem(item, anchor, appFor(item))
    }

    /** The Phase 1 long press: the edit hook when provided, otherwise [onLongPressHome]. */
    fun editLongPress(item: HomeItem) {
        val hook = callbacks.onItemLongPress
        if (hook != null) hook(item) else onLongPressHome()
    }

    /** Long press on empty space; col/row are -1 when the cell is unknown. */
    fun emptyLongPress(pageId: Long, col: Int, row: Int) {
        val hook = callbacks.onEmptyLongPress
        if (hook != null) hook(pageId, col, row) else onLongPressHome()
    }

    /** Long press on an app shown from the favorites of [items] (list / hybrid / flex styles). */
    fun longPressApp(app: AppInfo, items: List<HomeItem>, anchor: Rect? = null) {
        longPress(itemFor(app, items), anchor)
    }

    /**
     * The APP item of [items] that shows [app], or an unsaved (id -1) stand-in when the app comes
     * from the alphabetical fallback list; the same stand-in every time, so [isPressed] matches it.
     */
    fun itemFor(app: AppInfo, items: List<HomeItem>): HomeItem =
        items.firstOrNull { it.kind == ItemKind.APP && appFor(it)?.key == app.key }
            ?: HomeItem(
                id = -1L,
                pageId = items.firstOrNull()?.pageId ?: 0L,
                kind = ItemKind.APP,
                col = -1,
                row = -1,
                packageName = app.packageName,
                activityName = app.activityName,
                title = app.label,
            )

    /** True when [item] is the one whose long-press popup is open (see [HomeItemPressOverlay]). */
    fun isPressed(item: HomeItem): Boolean {
        val current = pressed.value?.item ?: return false
        return current.homePressKey() == item.homePressKey()
    }

    /** Tap on a folder: [HomeItemHooks.onOpenFolder] when provided, else `items.FolderSheet` at the tile. */
    fun openFolder(item: HomeItem) {
        val hook = hooks.onOpenFolder
        if (hook != null) hook(item) else folderSheet.value = item
    }

    /** Tap on a pair: [HomeItemHooks.onLaunchPair] when provided, else `items.AppPairs.launch`. */
    fun launchPair(item: HomeItem, context: Context) {
        val hook = hooks.onLaunchPair
        if (hook != null) hook(item) else launchHomePair(context, this, item)
    }

    /** True when the item's app is in `LauncherSettings.hiddenApps` (so its cell stays empty). */
    fun isHiddenItem(item: HomeItem): Boolean {
        val pkg = item.packageName ?: return false
        return settings.isHidden(pkg, item.activityName)
    }

    fun appFor(item: HomeItem): AppInfo? = appFor(item.packageName, item.activityName)

    /** The second app of a PAIR item. */
    fun appFor2(item: HomeItem): AppInfo? = appFor(item.packageName2, item.activityName2)

    private fun appFor(packageName: String?, activityName: String?): AppInfo? {
        val pkg = packageName ?: return null
        return if (activityName != null) {
            appsByKey["$pkg/$activityName"] ?: apps.firstOrNull { it.packageName == pkg }
        } else {
            apps.firstOrNull { it.packageName == pkg }
        }
    }

    /**
     * The apps a page treats as favorites: its APP items in reading order, falling back to the
     * alphabetical app list when the page has no items yet.
     */
    fun favorites(items: List<HomeItem>, max: Int): List<AppInfo> {
        val fromItems = items
            .filter { it.kind == ItemKind.APP }
            .sortedWith(compareBy<HomeItem> { it.row }.thenBy { it.col })
            .mapNotNull { appFor(it) }
            .distinctBy { it.key }
        val source = if (fromItems.isEmpty()) sortedApps else fromItems
        return source.take(max.coerceAtLeast(0))
    }

    /** Synthetic grid items filling the page row by row when the repository has none for it. */
    fun autoGridItems(pageId: Long, cols: Int, maxRows: Int): List<HomeItem> =
        sortedApps.take(cols * maxRows).mapIndexed { index, app ->
            HomeItem(
                id = -(index + 1L),
                pageId = pageId,
                kind = ItemKind.APP,
                col = index % cols,
                row = index / cols,
                packageName = app.packageName,
                activityName = app.activityName,
                title = app.label,
            )
        }
}

/**
 * Remembers where a tile was laid out so a long press can report the tile's window bounds (the
 * anchor of the long-press popup). Attach with [homeAnchor]; read with [bounds].
 */
internal class HomeAnchorHolder {
    var coordinates: LayoutCoordinates? = null

    /** The tile's bounds in window px, or null before it was positioned / after it left. */
    fun bounds(): Rect? = coordinates?.takeIf { it.isAttached }?.boundsInWindow()
}

/** Records this node's layout coordinates into [holder]. */
internal fun Modifier.homeAnchor(holder: HomeAnchorHolder): Modifier =
    onGloballyPositioned { holder.coordinates = it }

/** Soft shadow behind text drawn directly on the wallpaper (mock-up `--tsh`). */
internal fun homeTextShadow(): Shadow = Shadow(
    color = Color.Black.copy(alpha = 0.45f),
    offset = Offset(0f, 1f),
    blurRadius = 3f,
)

/** Tap and optional long-press without a ripple; consumes the gesture so parents do not see it. */
internal fun Modifier.homeTapGestures(onTap: () -> Unit, onLongPress: (() -> Unit)?): Modifier = composed {
    // Callers pass fresh lambdas on every recomposition; keying the pointer input on them would
    // restart the detector (and drop a pending long press) each time. Read the latest instead.
    val latestTap by rememberUpdatedState(onTap)
    val latestLongPress by rememberUpdatedState(onLongPress)
    val hasLongPress = onLongPress != null
    Modifier.pointerInput(hasLongPress) {
        val dispatchLongPress: (Offset) -> Unit = { latestLongPress?.invoke() }
        detectTapGestures(
            onTap = { latestTap() },
            onLongPress = if (hasLongPress) dispatchLongPress else null,
        )
    }
}

/** Long-press on empty page space opens edit mode. */
internal fun Modifier.homeLongPress(onLongPress: () -> Unit): Modifier = composed {
    val latestLongPress by rememberUpdatedState(onLongPress)
    Modifier.pointerInput(Unit) {
        detectTapGestures(onLongPress = { latestLongPress() })
    }
}

/** [homeSwipeUp] while [enabled]; otherwise this modifier unchanged (the gestures package owns the swipes). */
internal fun Modifier.homeSwipeUp(enabled: Boolean, onSwipeUp: () -> Unit): Modifier =
    if (enabled) homeSwipeUp(onSwipeUp) else this

/** A clear upward swipe on the page opens the app drawer. */
internal fun Modifier.homeSwipeUp(onSwipeUp: () -> Unit): Modifier = composed {
    val latestSwipeUp by rememberUpdatedState(onSwipeUp)
    Modifier.pointerInput(Unit) {
        val threshold = 72.dp.toPx()
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (total < -threshold) latestSwipeUp() },
            onDragCancel = { total = 0f },
            onVerticalDrag = { _, dragAmount -> total += dragAmount },
        )
    }
}

/** Wall clock that recomposes once a minute. */
@Composable
internal fun rememberHomeClock(): LocalDateTime {
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

internal fun LocalDateTime.homeTimeText(): String =
    format(DateTimeFormatter.ofPattern("h:mm", Locale.getDefault()))

internal fun LocalDateTime.homeDateText(long: Boolean): String =
    format(DateTimeFormatter.ofPattern(if (long) "EEEE, MMM d" else "EEE, MMM d", Locale.getDefault()))

/** Frosted surface panel (mock-up `.pn` / `.surf`). */
@Composable
internal fun HomeSurfacePanel(
    modifier: Modifier = Modifier,
    contentPadding: Dp = 16.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(tokens.radius + 4.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(tokens.surface, shape)
            .border(1.dp, tokens.surfaceBorder, shape)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

/**
 * Placeholder widget card (mock-up `.w`): small uppercase title, icon, big body text and an
 * optional caption. [highlight] uses the theme highlight colors (mock-up `.hi`).
 */
@Composable
internal fun HomeDashCard(
    title: String,
    icon: ImageVector,
    body: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    highlight: Boolean = false,
    bodySize: Int = 30,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(tokens.radius)
    val background = if (highlight) tokens.highlight else tokens.surface
    val foreground = if (highlight) tokens.onHighlight else tokens.onSurface
    Column(
        modifier = modifier
            .clip(shape)
            .background(background, shape)
            .border(1.dp, if (highlight) Color.Transparent else tokens.surfaceBorder, shape)
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title.uppercase(Locale.getDefault()),
                color = foreground.copy(alpha = 0.72f),
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = foreground.copy(alpha = 0.8f),
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = body,
            color = foreground,
            fontSize = bodySize.sp,
            fontWeight = FontWeight.Light,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.headlineMedium,
        )
        if (caption != null) {
            Spacer(Modifier.height(2.dp))
            Text(
                text = caption,
                color = foreground.copy(alpha = 0.8f),
                fontSize = 13.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** A bordered empty box used for items whose app is no longer installed. */
@Composable
internal fun HomeMissingTile(iconSize: Dp, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(tokens.radius)
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(
            Modifier
                .padding(top = 4.dp)
                .size(iconSize)
                .border(1.dp, tokens.surfaceBorder, shape),
        )
    }
}

/** Fills the remaining width of a row (mock-up `.sp`). */
@Composable
internal fun ColumnScope.HomeFlexSpacer() {
    Spacer(Modifier.weight(1f).fillMaxWidth())
}
