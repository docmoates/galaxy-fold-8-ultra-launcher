package com.foldos.launcher.home

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
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
 * Built once per [HomeScreen] composition.
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
) {
    val cols: Int get() = settings.colsFor(screen).coerceAtLeast(1)
    val iconSize: Dp get() = settings.iconSizeDp.dp
    val labels: Boolean get() = settings.labels
    val isInner: Boolean get() = screen != ScreenKind.COVER

    /** Settings > Home > Placement is "Free placement": items with dp bounds leave the grid. */
    val freeMode: Boolean get() = settings.gridMode == GridMode.FREE

    /** Widget stacks flip; when the feature is off only the front item of a stack shows. */
    val stacksOn: Boolean get() = settings.feature(LauncherSettings.FEATURE_STACKS)

    /** The style a page renders with. */
    fun styleFor(page: HomePage?): HomeStyle =
        if (perPageStyle && page != null) page.style else settings.styleFor(screen)

    /** Long press on an item: the edit hook when provided, otherwise the Phase 1 action. */
    fun longPress(item: HomeItem) {
        val hook = callbacks.onItemLongPress
        if (hook != null) hook(item) else onLongPressHome()
    }

    /** Long press on empty space; col/row are -1 when the cell is unknown. */
    fun emptyLongPress(pageId: Long, col: Int, row: Int) {
        val hook = callbacks.onEmptyLongPress
        if (hook != null) hook(pageId, col, row) else onLongPressHome()
    }

    /** Long press on an app shown from the favorites of [items] (list / hybrid / flex styles). */
    fun longPressApp(app: AppInfo, items: List<HomeItem>) {
        val item = items.firstOrNull { it.kind == ItemKind.APP && appFor(it)?.key == app.key }
        if (item != null) longPress(item) else onLongPressHome()
    }

    fun appFor(item: HomeItem): AppInfo? {
        val pkg = item.packageName ?: return null
        val activity = item.activityName
        return if (activity != null) {
            appsByKey["$pkg/$activity"] ?: apps.firstOrNull { it.packageName == pkg }
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

/** Soft shadow behind text drawn directly on the wallpaper (mock-up `--tsh`). */
internal fun homeTextShadow(): Shadow = Shadow(
    color = Color.Black.copy(alpha = 0.45f),
    offset = Offset(0f, 1f),
    blurRadius = 3f,
)

/** Tap and optional long-press without a ripple; consumes the gesture so parents do not see it. */
internal fun Modifier.homeTapGestures(onTap: () -> Unit, onLongPress: (() -> Unit)?): Modifier =
    pointerInput(onTap, onLongPress) {
        val longPress: ((Offset) -> Unit)? = onLongPress?.let { callback -> { _: Offset -> callback() } }
        detectTapGestures(
            onTap = { onTap() },
            onLongPress = longPress,
        )
    }

/** Long-press on empty page space opens edit mode. */
internal fun Modifier.homeLongPress(onLongPress: () -> Unit): Modifier =
    pointerInput(onLongPress) {
        detectTapGestures(onLongPress = { onLongPress() })
    }

/** A clear upward swipe on the page opens the app drawer. */
internal fun Modifier.homeSwipeUp(onSwipeUp: () -> Unit): Modifier =
    pointerInput(onSwipeUp) {
        val threshold = 72.dp.toPx()
        var total = 0f
        detectVerticalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = { if (total < -threshold) onSwipeUp() },
            onDragCancel = { total = 0f },
            onVerticalDrag = { _, dragAmount -> total += dragAmount },
        )
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
