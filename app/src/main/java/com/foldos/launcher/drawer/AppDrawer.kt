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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
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
import com.foldos.launcher.apppicker.AppPickerDialogs
import com.foldos.launcher.apppicker.AppPickerState
import com.foldos.launcher.apppicker.GroupChipRow
import com.foldos.launcher.apppicker.NewGroupRequest
import com.foldos.launcher.apppicker.PickerGroup
import com.foldos.launcher.apppicker.PickerModel
import com.foldos.launcher.apppicker.rememberAppPickerState
import com.foldos.launcher.apppicker.rememberPickerModel
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.DrawerStyle
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.ui.DragController
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.flow.flowOf

/** Blur radius (dp) applied to the home content behind the pop-up drawer (mock-up `.scrim.blur`). */
val DrawerBackdropBlurRadius: Dp = 18.dp

private const val DRAWER_ANIM_MS = 320

/** Height (dp) of the strip the pop-up shrinks to while one of its apps is being dragged. */
private val CollapsedHandleHeight: Dp = 30.dp

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
 * with the drawer. Same API 31 guard. While an app is dragged out of the pop-up the caller should
 * pass `visible = drawerOpen && !dragController.isDragging` so the home is sharp to drop on.
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

/** Remembers whether a drag that started in the pop-up ended over a drop target. */
private class DropLanding {
    var sawTarget: Boolean = false
}

/**
 * The FoldOS app pop-up (PLAN.md 2.7): the place users manage their apps. Place it as the top-most
 * layer of the home Box, filling the screen; it draws nothing while [visible] is false.
 *
 * - [DrawerStyle.POPUP] on [ScreenKind.COVER]: bottom sheet rising to ~78% height, 30dp top corners,
 *   grab handle, slide-up animation.
 * - [DrawerStyle.POPUP] on [ScreenKind.INNER] / [ScreenKind.FLEX]: centered floating card (9% side
 *   margins, 64dp top, 40dp bottom, 32dp corners), scale+fade animation.
 * - [DrawerStyle.FULL]: full-screen panel.
 *
 * Inside: a search field, the group folders (All, Work, Media, Social, Tools, Games, Hidden and the
 * user's own groups, "New group") and the app grid. Tapping an app calls [onLaunch] then [onDismiss].
 * Long press on an app and release opens its menu (Add to group, App info, ...); long press and drag
 * starts a drag with [DragPayload.App][com.foldos.launcher.core.ui.DragPayload.App] on [dragController]:
 * the pop-up then fades to 30% and shrinks to a strip so the home screen and the dock can be dropped
 * on, closes ([onDismiss]) when the drop landed on a target, and restores itself otherwise.
 *
 * Behind the pop-up a scrim dims the home; the blur itself is applied by the caller through
 * [drawerBackdropModifier] / [rememberDrawerBackdropModifier]. Tapping the scrim or pressing back
 * calls [onDismiss].
 *
 * @param dragController the launcher-wide controller (`LocalDragController.current`); null turns
 *   dragging off. The drawer stays composed until the drop, as `Drag.kt` requires.
 * @param onAddToHome adds the app to the current home page ("Add to home screen" in the menu); null hides the entry.
 * @param onAddToDock adds the app to the dock ("Add to dock" in the menu); null hides the entry.
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
    dragController: DragController? = null,
    onAddToHome: ((AppInfo) -> Unit)? = null,
    onAddToDock: ((AppInfo) -> Unit)? = null,
) {
    val isPopup = settings.drawerStyle == DrawerStyle.POPUP
    val isCover = screen == ScreenKind.COVER

    val pickerState = rememberAppPickerState()
    val model = rememberPickerModel(pickerState, apps, settings, visible)

    // Reset the search, the selected group and the dialogs when the drawer closes so it reopens clean.
    LaunchedEffect(visible) {
        if (!visible) pickerState.reset()
    }

    BackHandler(enabled = visible) { onDismiss() }

    // Drag and drop out of the pop-up: it shrinks to a strip while one of its apps is dragged,
    // closes when the drop hit a target (dock / home) and restores itself when it did not.
    val dragging = dragController?.payload?.value != null
    var dragFromSheet by remember { mutableStateOf(false) }
    val landing = remember { DropLanding() }
    if (dragController != null) {
        LaunchedEffect(dragController) {
            snapshotFlow { dragController.hoverTargetId.value }.collect { target ->
                // Only while the drag is live: the clear on drop also reports null and must not undo it.
                if (dragController.isDragging) landing.sawTarget = target != null
            }
        }
    }
    LaunchedEffect(dragging) {
        if (!dragging && dragFromSheet) {
            dragFromSheet = false
            val landed = landing.sawTarget
            landing.sawTarget = false
            if (landed) onDismiss()
        }
    }
    val sheetCollapsed = dragging && dragFromSheet
    val animMs = a11yDuration(DRAWER_ANIM_MS)
    val collapse = animateFloatAsState(
        targetValue = if (sheetCollapsed) 1f else 0f,
        animationSpec = tween(durationMillis = a11yDuration(200)),
        label = "drawerDragCollapse",
    )
    val onDragStarted: () -> Unit = {
        landing.sawTarget = false
        dragFromSheet = true
    }

    // Easy mode (settings.largeIcons): bigger icons, so fewer columns.
    val easy = settings.largeIcons
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
        model.shown.firstOrNull()?.let(launchAndDismiss)
    }

    val body: @Composable (Modifier, PaddingValues, Boolean) -> Unit = { bodyModifier, padding, grabHandle ->
        DrawerBody(
            state = pickerState,
            model = model,
            showChips = showChips,
            showGrabHandle = grabHandle,
            totalCount = apps.size,
            columns = columns,
            iconSize = iconSize,
            onLaunch = launchAndDismiss,
            onSearch = launchFirst,
            badgeCounts = badgeCounts,
            contentPadding = padding,
            dragController = dragController,
            onDragStarted = onDragStarted,
            modifier = bodyModifier,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (isPopup) {
            DrawerScrim(visible = visible && !sheetCollapsed, blur = settings.popBlur, onDismiss = onDismiss)
        }

        if (!isPopup) {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier
                    .fillMaxSize()
                    .dragCollapse { collapse.value },
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
                    body(
                        Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding()
                            .imePadding(),
                        PaddingValues(start = 16.dp, end = 16.dp, top = 48.dp, bottom = 30.dp),
                        false,
                    )
                }
            }
        } else if (isCover) {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.78f)
                    .dragCollapse { collapse.value },
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
                    body(
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding(),
                        PaddingValues(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 26.dp),
                        true,
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
                    .padding(top = 64.dp, bottom = 40.dp)
                    .dragCollapse { collapse.value },
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
                    body(
                        Modifier.fillMaxSize(),
                        PaddingValues(start = 24.dp, end = 24.dp, top = 22.dp, bottom = 20.dp),
                        false,
                    )
                }
            }
        }

        AppPickerDialogs(
            state = pickerState,
            model = model,
            onAddToHome = onAddToHome,
            onAddToDock = onAddToDock,
            onDone = onDismiss,
        )
    }
}

/**
 * While an app is dragged out of the pop-up, [progress] goes 0 -> 1: the sheet fades to 30% and
 * squashes into a [CollapsedHandleHeight] strip at its top edge (a handle), so the home screen and
 * the dock behind it are free to drop on. The node stays composed, as the drag requires.
 */
private fun Modifier.dragCollapse(progress: () -> Float): Modifier = this.graphicsLayer {
    val p = progress()
    if (p > 0f) {
        val minScaleY = (CollapsedHandleHeight.toPx() / size.height).coerceIn(0.02f, 1f)
        scaleY = 1f + (minScaleY - 1f) * p
        scaleX = 1f - 0.6f * p
        alpha = 1f - 0.7f * p
        transformOrigin = TransformOrigin(0.5f, 0f)
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

/** Search field, group folders and grid stacked inside a panel. */
@Composable
private fun DrawerBody(
    state: AppPickerState,
    model: PickerModel,
    showChips: Boolean,
    showGrabHandle: Boolean,
    totalCount: Int,
    columns: Int,
    iconSize: Dp,
    onLaunch: (AppInfo) -> Unit,
    onSearch: () -> Unit,
    contentPadding: PaddingValues,
    dragController: DragController?,
    onDragStarted: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCounts: Map<String, Int> = emptyMap(),
) {
    val emptyText = when {
        state.query.isNotBlank() -> "No apps match"
        model.selected.kind == PickerGroup.Kind.USER ->
            "This group is empty.\nLong-press an app and choose “Add to group”."
        else -> "No apps here"
    }
    Column(
        modifier = modifier.padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        if (showGrabHandle) {
            DrawerGrabHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
        }
        DrawerSearchField(
            query = state.query,
            onQueryChange = { state.query = it },
            appCount = totalCount,
            onSearch = onSearch,
        )
        if (showChips) {
            GroupChipRow(
                model = model,
                onSelect = { group ->
                    state.query = ""
                    state.selectedId = group.id
                },
                onNewGroup = { state.newGroup = NewGroupRequest(andAdd = null) },
                onManageGroup = { group -> state.manageGroupName = group.userName },
            )
        }
        DrawerAppGrid(
            apps = model.shown,
            columns = columns,
            iconSize = iconSize,
            onLaunch = onLaunch,
            badgeCounts = badgeCounts,
            dragController = dragController,
            onDragStarted = onDragStarted,
            onAppMenu = { app -> state.actionsApp = app },
            emptyText = emptyText,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        )
    }
}
