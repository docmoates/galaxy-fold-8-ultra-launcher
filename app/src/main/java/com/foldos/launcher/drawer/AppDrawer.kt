package com.foldos.launcher.drawer

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.a11y.a11yButton
import com.foldos.launcher.a11y.a11yDuration
import com.foldos.launcher.core.LocalAppGraph
import kotlinx.coroutines.flow.flowOf
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.DrawerStyle
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.theme.LocalFoldOsTokens

/** Blur radius (dp) applied to the home content behind the pop-up drawer (mock-up `.scrim.blur`). */
val DrawerBackdropBlurRadius: Dp = 18.dp

private const val DRAWER_ANIM_MS = 320

/**
 * Modifier the caller puts on the home content (NOT on [AppDrawer]) so the home blurs while the
 * pop-up drawer is open. Returns [Modifier] unchanged when the drawer is hidden, blur is off, or the
 * device is below API 31. Non-composable; for an animated radius use [rememberDrawerBackdropModifier].
 */
fun drawerBackdropModifier(visible: Boolean, blur: Boolean): Modifier {
    if (!visible || !blur || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Modifier
    return Modifier.graphicsLayer {
        val radiusPx = DrawerBackdropBlurRadius.toPx()
        renderEffect = RenderEffect
            .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
            .asComposeRenderEffect()
    }
}

/**
 * Composable variant of [drawerBackdropModifier] whose blur radius animates in and out together
 * with the drawer. Same API 31 guard.
 */
@Composable
fun rememberDrawerBackdropModifier(visible: Boolean, blur: Boolean): Modifier {
    val animMs = a11yDuration(DRAWER_ANIM_MS)
    val target = if (visible && blur) 1f else 0f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = animMs),
        label = "drawerBackdropBlur",
    )
    if (!blur || Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return Modifier
    return Modifier.graphicsLayer {
        val radiusPx = DrawerBackdropBlurRadius.toPx() * progress
        renderEffect = if (radiusPx > 0.5f) {
            RenderEffect
                .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                .asComposeRenderEffect()
        } else {
            null
        }
    }
}

/**
 * The FoldOS app drawer (PLAN.md 2.7). Place it as the top-most layer of the home Box, filling the
 * screen; it draws nothing while [visible] is false.
 *
 * - [DrawerStyle.POPUP] on [ScreenKind.COVER]: bottom sheet rising to ~78% height, 30dp top corners,
 *   grab handle, slide-up animation.
 * - [DrawerStyle.POPUP] on [ScreenKind.INNER] / [ScreenKind.FLEX]: centered floating card (9% side
 *   margins, 64dp top, 40dp bottom, 32dp corners), scale+fade animation.
 * - [DrawerStyle.FULL]: full-screen panel.
 *
 * Behind the pop-up a scrim dims the home; the blur itself is applied by the caller through
 * [drawerBackdropModifier] / [rememberDrawerBackdropModifier]. Tapping the scrim or pressing back
 * calls [onDismiss]; tapping an app calls [onLaunch] then [onDismiss].
 */
@Composable
fun AppDrawer(
    visible: Boolean,
    screen: ScreenKind,
    settings: LauncherSettings,
    apps: List<AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isPopup = settings.drawerStyle == DrawerStyle.POPUP
    val isCover = screen == ScreenKind.COVER

    var query by rememberSaveable { mutableStateOf("") }
    var category by rememberSaveable { mutableStateOf(DrawerCategory.ALL) }

    // Reset the search when the drawer closes so it reopens clean.
    LaunchedEffect(visible) {
        if (!visible) {
            query = ""
            category = DrawerCategory.ALL
        }
    }

    BackHandler(enabled = visible) { onDismiss() }

    val sortedApps = remember(apps) {
        apps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }
    val filteredApps = remember(sortedApps, query, category) {
        val base = category.filter(sortedApps)
        val q = query.trim()
        if (q.isEmpty()) base else base.filter { it.label.contains(q, ignoreCase = true) }
    }

    // Easy mode (settings.largeIcons): bigger icons, so fewer columns.
    val easy = settings.largeIcons
    val animMs = a11yDuration(DRAWER_ANIM_MS)
    val columns = when {
        isCover -> if (easy) 3 else 4
        isPopup -> if (easy) 5 else 7
        else -> if (easy) 6 else 8
    }
    val iconSize = settings.effectiveIconSizeDp.coerceIn(40, if (easy) 80 else 64).dp

    // Unread counts are only spoken to screen readers here (the drawer draws no badges), so they
    // are collected while the drawer is open and the badges feature is on.
    val graph = LocalAppGraph.current
    val badgesOn = visible && settings.feature(LauncherSettings.FEATURE_BADGES) &&
        settings.badgeStyle != LauncherSettings.BADGE_OFF
    val countsFlow = remember(graph, badgesOn) {
        if (badgesOn) graph.badges.counts else flowOf(emptyMap<String, Int>())
    }
    val badgeCounts by countsFlow.collectAsStateWithLifecycle(initialValue = emptyMap())
    val showChips = settings.feature(LauncherSettings.FEATURE_DRAWER_TABS)

    val launchAndDismiss: (AppInfo) -> Unit = { app ->
        onLaunch(app)
        onDismiss()
    }
    val launchFirst: () -> Unit = {
        filteredApps.firstOrNull()?.let(launchAndDismiss)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isPopup) {
            DrawerScrim(visible = visible, blur = settings.popBlur, onDismiss = onDismiss)
        }

        if (!isPopup) {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier.fillMaxSize(),
                enter = fadeIn(animationSpec = tween(animMs)) +
                    slideInVertically(animationSpec = tween(animMs)) { it / 8 },
                exit = fadeOut(animationSpec = tween(animMs)) +
                    slideOutVertically(animationSpec = tween(animMs)) { it / 8 },
                label = "fullDrawer",
            ) {
                DrawerPanel(
                    shape = RoundedCornerShape(0.dp),
                    showBorder = false,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    DrawerBody(
                        query = query,
                        onQueryChange = { query = it },
                        category = category,
                        onCategoryChange = { category = it },
                        showChips = showChips,
                        showGrabHandle = false,
                        apps = filteredApps,
                        totalCount = apps.size,
                        columns = columns,
                        iconSize = iconSize,
                        onLaunch = launchAndDismiss,
                        onSearch = launchFirst,
                        badgeCounts = badgeCounts,
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 30.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .imePadding(),
                    )
                }
            }
        } else if (isCover) {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f),
                enter = fadeIn(animationSpec = tween(animMs / 2)) +
                    slideInVertically(animationSpec = tween(animMs)) { it },
                exit = fadeOut(animationSpec = tween(animMs)) +
                    slideOutVertically(animationSpec = tween(animMs)) { it },
                label = "coverDrawerSheet",
            ) {
                DrawerPanel(
                    shape = RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp, bottomStart = 30.dp, bottomEnd = 30.dp),
                    showBorder = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                        .imePadding(),
                ) {
                    DrawerBody(
                        query = query,
                        onQueryChange = { query = it },
                        category = category,
                        onCategoryChange = { category = it },
                        showChips = showChips,
                        showGrabHandle = true,
                        apps = filteredApps,
                        totalCount = apps.size,
                        columns = columns,
                        iconSize = iconSize,
                        onLaunch = launchAndDismiss,
                        onSearch = launchFirst,
                        badgeCounts = badgeCounts,
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 26.dp),
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                    )
                }
            }
        } else {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth(0.82f)
                    .fillMaxHeight()
                    .padding(top = 64.dp, bottom = 40.dp),
                enter = fadeIn(animationSpec = tween(animMs)) +
                    scaleIn(animationSpec = tween(animMs), initialScale = 0.92f),
                exit = fadeOut(animationSpec = tween(animMs)) +
                    scaleOut(animationSpec = tween(animMs), targetScale = 0.94f),
                label = "innerDrawerCard",
            ) {
                DrawerPanel(
                    shape = RoundedCornerShape(32.dp),
                    showBorder = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                ) {
                    DrawerBody(
                        query = query,
                        onQueryChange = { query = it },
                        category = category,
                        onCategoryChange = { category = it },
                        showChips = showChips,
                        showGrabHandle = false,
                        apps = filteredApps,
                        totalCount = apps.size,
                        columns = columns,
                        iconSize = iconSize,
                        onLaunch = launchAndDismiss,
                        onSearch = launchFirst,
                        badgeCounts = badgeCounts,
                        contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 20.dp),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/**
 * Dimming layer behind the pop-up; a tap anywhere on it dismisses the drawer. Colours follow the
 * mock-up's `.scrim`: black 38% without blur, black 22% with blur, white 18% with blur in light mode.
 */
@Composable
private fun DrawerScrim(visible: Boolean, blur: Boolean, onDismiss: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val animMs = a11yDuration(DRAWER_ANIM_MS)
    val scrimColor = when {
        !blur -> Color.Black.copy(alpha = 0.38f)
        tokens.isDark -> Color.Black.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.18f)
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(animationSpec = tween(animMs)),
        exit = fadeOut(animationSpec = tween(animMs)),
        label = "drawerScrim",
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                // TalkBack: the dimmed backdrop is a "Close" button.
                .a11yButton(label = "Close app drawer", onActivate = onDismiss)
                .pointerInput(onDismiss) {
                    detectTapGestures(onTap = { onDismiss() })
                },
        )
    }
}

/**
 * The drawer's container: themed panel colour, optional hairline border, shadow, and a pointer
 * sink so taps inside the panel never fall through to the scrim.
 */
@Composable
private fun DrawerPanel(
    shape: Shape,
    showBorder: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    // FoldOsTokens.drawerScrim is the mock-up's `--drawer`: the near-opaque sheet fill.
    val panelColor = tokens.drawerScrim
    val borderColor = if (showBorder) tokens.surfaceBorder else Color.Transparent
    Box(
        modifier = modifier
            .shadow(elevation = 24.dp, shape = shape, clip = false)
            .clip(shape)
            .background(panelColor)
            .border(1.dp, borderColor, shape)
            .semantics { paneTitle = "App drawer" }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { /* swallow: keep taps inside the panel */ })
            },
    ) {
        content()
    }
}

/** Search field, chips and grid stacked inside a panel. */
@Composable
private fun DrawerBody(
    query: String,
    onQueryChange: (String) -> Unit,
    category: DrawerCategory,
    onCategoryChange: (DrawerCategory) -> Unit,
    showChips: Boolean,
    showGrabHandle: Boolean,
    apps: List<AppInfo>,
    totalCount: Int,
    columns: Int,
    iconSize: Dp,
    onLaunch: (AppInfo) -> Unit,
    onSearch: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    badgeCounts: Map<String, Int> = emptyMap(),
) {
    Column(
        modifier = modifier.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showGrabHandle) {
            DrawerGrabHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        DrawerSearchField(
            query = query,
            onQueryChange = onQueryChange,
            appCount = totalCount,
            onSearch = onSearch,
        )
        if (showChips) {
            DrawerCategoryChips(selected = category, onSelect = onCategoryChange)
        }
        DrawerAppGrid(
            apps = apps,
            columns = columns,
            iconSize = iconSize,
            onLaunch = onLaunch,
            badgeCounts = badgeCounts,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}
