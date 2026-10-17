package com.foldos.launcher.edit

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Dock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.a11y.a11yButton
import com.foldos.launcher.a11y.a11yDuration
import com.foldos.launcher.apppicker.GroupChipRow
import com.foldos.launcher.apppicker.NewGroupRequest
import com.foldos.launcher.apppicker.AppPickerDialogs
import com.foldos.launcher.apppicker.rememberAppPickerState
import com.foldos.launcher.apppicker.rememberPickerModel
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.model.WidgetEntry
import com.foldos.launcher.core.ui.DragController
import com.foldos.launcher.core.ui.DragPayload
import com.foldos.launcher.core.ui.dragSource
import com.foldos.launcher.drawer.DrawerAppGrid
import com.foldos.launcher.drawer.DrawerGrabHandle
import com.foldos.launcher.drawer.DrawerSearchField
import com.foldos.launcher.home.ProvideLabelSize
import com.foldos.launcher.picker.PickerModel as WidgetPickerModel
import com.foldos.launcher.picker.WidgetPreview
import com.foldos.launcher.theme.LocalFoldOsTokens

private const val SHEET_ANIM_MS = 300
private val CollapsedStripHeight = 28.dp

/** The three content tabs of the sheet. */
internal enum class AddTab(val label: String) { APPS("Apps"), WIDGETS("Widgets"), CARDS("Cards") }

/**
 * "Add to home" (Round 2.0, mock-up of the Pixel / One UI long-press sheet). Long-pressing empty
 * page space (or the Add action of edit mode) opens it as a bottom sheet on the cover and a floating
 * card on the inner screen. Three tabs:
 * - **Apps**: search, the app pop-up's group chips and an A-Z grid. Tap an app to put it on the first
 *   free cell of the current page; long press and move to drag it onto the page, a card or the dock.
 * - **Widgets**: every widget of the catalog (FoldOS built-ins first, then one section per app) as a
 *   preview tile; tap places, long press and move drags.
 * - **Cards**: an App card, an App card in list style and a Widget card, with small previews; tap or drag.
 * Under the tabs an actions row: Wallpaper, Themes, Dock, Pages (edit mode's page strip) and Settings.
 *
 * While something is dragged out of the sheet it squashes into a thin strip at its edge (it stays
 * composed, as `Drag.kt` requires) and comes back after the drop. Tapping the scrim or Back closes it.
 */
@Composable
fun AddToHomeSheet(
    visible: Boolean,
    screen: ScreenKind,
    settings: LauncherSettings,
    apps: List<AppInfo>,
    dragController: DragController,
    onPlaceApp: (AppInfo) -> Unit,
    onPlaceWidget: (WidgetEntry) -> Unit,
    onPlaceCard: (cardStyle: String) -> Unit,
    onOpenWallpaper: () -> Unit,
    onOpenThemes: () -> Unit,
    onOpenDock: () -> Unit,
    onOpenPages: () -> Unit,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val isCover = screen == ScreenKind.COVER
    val animMs = a11yDuration(SHEET_ANIM_MS)
    var tab by rememberSaveable { mutableStateOf(AddTab.APPS.name) }
    val currentTab = AddTab.entries.firstOrNull { it.name == tab } ?: AddTab.APPS

    BackHandler(enabled = visible) { onDismiss() }

    // Collapse while one of our tiles is being dragged; come back once the drop is done.
    val dragging = dragController.payload.value != null
    var dragFromSheet by remember { mutableStateOf(false) }
    LaunchedEffect(dragController) {
        snapshotFlow { dragController.payload.value == null }.collect { idle -> if (idle) dragFromSheet = false }
    }
    val collapsed = dragging && dragFromSheet
    val collapse by animateFloatAsState(
        targetValue = if (collapsed) 1f else 0f,
        animationSpec = tween(durationMillis = a11yDuration(180)),
        label = "addSheetCollapse",
    )
    val onDragStarted: () -> Unit = { dragFromSheet = true }

    Box(modifier = modifier.fillMaxSize()) {
        // Scrim: dims the home, closes on tap; gone while a drag is in flight so the page is clear to drop on.
        AnimatedVisibility(
            visible = visible && !collapsed,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn(animationSpec = tween(animMs)),
            exit = fadeOut(animationSpec = tween(animMs)),
            label = "addSheetScrim",
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = if (tokens.isDark) 0.28f else 0.18f))
                    .a11yButton(label = "Close", onActivate = onDismiss)
                    .pointerInput(onDismiss) { detectTapGestures(onTap = { onDismiss() }) },
            )
        }

        val panelModifier = if (isCover) {
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.66f)
        } else {
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(0.62f)
                .fillMaxHeight(0.62f)
                .padding(bottom = 24.dp)
        }
        AnimatedVisibility(
            visible = visible,
            modifier = panelModifier.graphicsLayer {
                val p = collapse
                if (p > 0f) {
                    val minScaleY = (CollapsedStripHeight.toPx() / size.height).coerceIn(0.02f, 1f)
                    scaleY = 1f + (minScaleY - 1f) * p
                    scaleX = 1f - 0.5f * p
                    alpha = 1f - 0.65f * p
                    transformOrigin = TransformOrigin(0.5f, 1f)
                }
            },
            enter = fadeIn(animationSpec = tween(animMs / 2)) + slideInVertically(animationSpec = tween(animMs)) { it },
            exit = fadeOut(animationSpec = tween(animMs)) + slideOutVertically(animationSpec = tween(animMs)) { it },
            label = "addSheetPanel",
        ) {
            val shape = RoundedCornerShape(30.dp)
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                    .shadow(24.dp, shape, clip = false)
                    .clip(shape)
                    .background(tokens.drawerScrim)
                    .border(1.dp, tokens.surfaceBorder, shape)
                    .semantics { paneTitle = "Add to home" }
                    .pointerInput(Unit) { detectTapGestures(onTap = { }) }
                    .imePadding(),
            ) {
                ProvideLabelSize(settings) {
                    Column(
                        Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(top = 10.dp),
                    ) {
                        DrawerGrabHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Spacer(Modifier.height(8.dp))
                        AddTabs(currentTab, onSelect = { tab = it.name }, modifier = Modifier.padding(horizontal = 16.dp))
                        Spacer(Modifier.height(8.dp))
                        Box(Modifier.weight(1f).fillMaxWidth()) {
                            when (currentTab) {
                                AddTab.APPS -> AppsTab(settings, apps, visible, dragController, onDragStarted, onPlaceApp, onDismiss)
                                AddTab.WIDGETS -> WidgetsTab(dragController, onDragStarted, onPlaceWidget)
                                AddTab.CARDS -> CardsTab(dragController, onDragStarted, onPlaceCard)
                            }
                        }
                        EditActionBar(
                            actions = listOf(
                                EditAction("Wallpaper", Icons.Rounded.Wallpaper, onOpenWallpaper, EditTags.ACTION_WALLPAPER),
                                EditAction("Themes", Icons.Rounded.Palette, onOpenThemes, EditTags.ACTION_THEMES),
                                EditAction("Dock", Icons.Rounded.Dock, onOpenDock, EditTags.ACTION_DOCK),
                                EditAction("Pages", Icons.Rounded.ViewCarousel, onOpenPages, EditTags.ACTION_PAGES),
                                EditAction("Settings", Icons.Rounded.Settings, onOpenSettings, EditTags.ACTION_SETTINGS),
                            ),
                            modifier = Modifier.padding(top = 6.dp, bottom = 10.dp),
                        )
                    }
                }
            }
        }
    }
}

/** The Apps / Widgets / Cards pill tabs. */
@Composable
private fun AddTabs(current: AddTab, onSelect: (AddTab) -> Unit, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val pill = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(40.dp)
            .clip(pill)
            .background(tokens.surface, pill)
            .border(1.dp, tokens.surfaceBorder, pill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        AddTab.entries.forEach { entry ->
            val on = entry == current
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(pill)
                    .background(if (on) tokens.accent else Color.Transparent, pill)
                    .clickable { onSelect(entry) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = entry.label,
                    color = if (on) tokens.onAccent else tokens.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/* ---------------- Apps ---------------- */

@Composable
private fun AppsTab(
    settings: LauncherSettings,
    apps: List<AppInfo>,
    visible: Boolean,
    dragController: DragController,
    onDragStarted: () -> Unit,
    onPlaceApp: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    val state = rememberAppPickerState()
    val model = rememberPickerModel(state, apps, settings, visible)
    LaunchedEffect(visible) { if (!visible) state.reset() }
    val iconSize = settings.effectiveIconSizeDp.coerceIn(40, 64).dp
    val columns = if (settings.largeIcons) 4 else 5
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DrawerSearchField(
            query = state.query,
            onQueryChange = { state.query = it },
            appCount = apps.size,
            onSearch = { model.shown.firstOrNull()?.let(onPlaceApp) },
        )
        if (settings.feature(LauncherSettings.FEATURE_DRAWER_TABS)) {
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
        Text(
            text = "Tap to add to this page, or hold and drag it where you want it.",
            color = LocalFoldOsTokens.current.onSurface.copy(alpha = 0.6f),
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        DrawerAppGrid(
            apps = model.shown,
            columns = columns,
            iconSize = iconSize,
            onLaunch = onPlaceApp,
            dragController = dragController,
            onDragStarted = onDragStarted,
            onAppMenu = { app -> state.actionsApp = app },
            emptyText = if (state.query.isNotBlank()) "No apps match" else "No apps here",
            modifier = Modifier.fillMaxWidth().weight(1f),
        )
    }
    AppPickerDialogs(
        state = state,
        model = model,
        onAddToHome = onPlaceApp,
        onAddToDock = null,
        onDone = onDismiss,
    )
}

/* ---------------- Widgets ---------------- */

@Composable
private fun WidgetsTab(
    dragController: DragController,
    onDragStarted: () -> Unit,
    onPlaceWidget: (WidgetEntry) -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val graph = LocalAppGraph.current
    val entries by graph.widgets.entries.collectAsStateWithLifecycle(initialValue = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    val model = remember(entries) { WidgetPickerModel(entries = entries, recents = emptyList(), loading = false) }
    val filtered = remember(model, query) {
        val q = query.trim()
        model.groups.mapNotNull { group ->
            val matching = if (q.isEmpty()) {
                group.entries
            } else {
                group.entries.filter { it.name.contains(q, ignoreCase = true) || group.label.contains(q, ignoreCase = true) }
            }
            if (matching.isEmpty()) null else group to matching
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        DrawerSearchField(
            query = query,
            onQueryChange = { query = it },
            appCount = entries.size,
            onSearch = { filtered.firstOrNull()?.second?.firstOrNull()?.let(onPlaceWidget) },
        )
        if (entries.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(text = "Loading widgets", color = tokens.onSurface.copy(alpha = 0.7f))
            }
        } else LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 150.dp),
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            filtered.forEach { (group, widgets) ->
                item(key = "h:${group.key}", span = { GridItemSpan(maxLineSpan) }) {
                    Text(
                        text = group.label,
                        color = tokens.onSurface.copy(alpha = 0.75f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp, start = 2.dp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                items(widgets, key = { it.id }) { entry ->
                    WidgetTile(entry, dragController, onDragStarted, onPlaceWidget)
                }
            }
        }
    }
}

@Composable
private fun WidgetTile(
    entry: WidgetEntry,
    dragController: DragController,
    onDragStarted: () -> Unit,
    onPlaceWidget: (WidgetEntry) -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.surface, shape)
            .border(1.dp, tokens.surfaceBorder, shape)
            .a11yButton(label = "${entry.name}, ${entry.defaultSpanX} by ${entry.defaultSpanY}", onActivate = { onPlaceWidget(entry) })
            .clickable { onPlaceWidget(entry) }
            .dragSource(
                controller = dragController,
                payload = { DragPayload.Widget(entry) },
                onStarted = onDragStarted,
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        WidgetPreview(
            entry = entry,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.4f)
                .clip(RoundedCornerShape(12.dp)),
        )
        Text(
            text = entry.name,
            color = tokens.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${entry.defaultSpanX} x ${entry.defaultSpanY}",
            color = tokens.onSurface.copy(alpha = 0.6f),
            fontSize = 11.sp,
            maxLines = 1,
        )
    }
}

/* ---------------- Cards ---------------- */

private class CardChoice(val style: String, val title: String, val description: String)

private val CardChoices = listOf(
    CardChoice(HomeItem.CARD_APPS, "App card", "A rounded holder for app icons. Drop apps on it; it lays them out for you."),
    CardChoice(HomeItem.CARD_APPS_LIST, "App list card", "Apps as big names in rows, like the old Minimal list."),
    CardChoice(HomeItem.CARD_WIDGETS, "Widget card", "Holds one or more widgets; swipe through them like a stack."),
)

@Composable
private fun CardsTab(
    dragController: DragController,
    onDragStarted: () -> Unit,
    onPlaceCard: (String) -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(
                text = "Cards hold apps or widgets on the page. Tap to add one (2 x 2, resize it afterwards) or drag it into place.",
                color = tokens.onSurface.copy(alpha = 0.6f),
                fontSize = 12.sp,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        items(CardChoices, key = { it.style }) { choice ->
            val shape = RoundedCornerShape(18.dp)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(shape)
                    .background(tokens.surface, shape)
                    .border(1.dp, tokens.surfaceBorder, shape)
                    .a11yButton(label = choice.title, onActivate = { onPlaceCard(choice.style) })
                    .clickable { onPlaceCard(choice.style) }
                    .dragSource(
                        controller = dragController,
                        payload = { DragPayload.Card(choice.style) },
                        onStarted = onDragStarted,
                    )
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CardPreview(choice.style, Modifier.fillMaxWidth().aspectRatio(1.4f))
                Text(
                    text = choice.title,
                    color = tokens.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = choice.description,
                    color = tokens.onSurface.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** Small schematic of a card: dots for an apps grid, lines for the list, a wide block for widgets. */
@Composable
internal fun CardPreview(style: String, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .clip(shape)
            .background(tokens.onSurface.copy(alpha = 0.06f), shape)
            .border(1.dp, tokens.surfaceBorder, shape)
            .padding(10.dp),
        contentAlignment = Alignment.Center,
    ) {
        when (style) {
            HomeItem.CARD_WIDGETS -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(tokens.accent.copy(alpha = 0.55f)))
                Row(Modifier.align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(3) { i ->
                        Box(Modifier.size(4.dp).background(tokens.onSurface.copy(alpha = if (i == 0) 0.9f else 0.3f), RoundedCornerShape(2.dp)))
                    }
                }
            }
            HomeItem.CARD_APPS_LIST -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                repeat(3) { i ->
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(Modifier.size(10.dp).background(tokens.accent.copy(alpha = 0.8f), RoundedCornerShape(3.dp)))
                        Box(
                            Modifier
                                .height(6.dp)
                                .fillMaxWidth(0.5f + 0.15f * i)
                                .background(tokens.onSurface.copy(alpha = 0.5f), RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
            else -> Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceEvenly) {
                repeat(2) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        repeat(3) {
                            Box(Modifier.size(14.dp).background(tokens.accent.copy(alpha = 0.8f), RoundedCornerShape(4.dp)))
                        }
                    }
                }
            }
        }
    }
}
