package com.foldos.launcher.picker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.a11y.a11yDuration
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.model.WidgetCategory
import com.foldos.launcher.core.model.WidgetEntry
import com.foldos.launcher.core.repo.WidgetCatalog
import com.foldos.launcher.drawer.rememberDrawerBackdropModifier
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Width of the list pane on the inner / flex card. */
private val InnerListPaneWidth = 320.dp

/**
 * Modifier the caller puts on the home content (NOT on [WidgetPicker]) so the home blurs while
 * the picker is open. Same behaviour and API 31 guard as the drawer's backdrop; when both the
 * drawer and the picker can be open, pass `drawerOpen || pickerOpen` as [visible].
 */
@Composable
fun rememberWidgetPickerBackdropModifier(visible: Boolean, blur: Boolean): Modifier =
    rememberDrawerBackdropModifier(visible = visible, blur = blur)

/**
 * The "Widgets" picker, a clean two-level chooser. Place it as a top layer of the home Box, filling
 * the screen; it draws nothing while [visible] is false.
 *
 * Level 1 is a list: header with search, a "Featured" grid of FoldOS widgets, and the "Apps" list
 * (one row per widget-providing app, filter chips by category). Level 2 shows the widgets of one
 * app (or of FoldOS) one per page in a pager with a large preview and an "Add to home" button.
 *
 * - [ScreenKind.COVER]: bottom sheet at 84% height with a grab handle; level 2 replaces level 1 in
 *   the same sheet, with a back arrow (and the back button) returning to the list.
 * - [ScreenKind.INNER] / [ScreenKind.FLEX]: floating card (9% side margins, 64dp top, 40dp bottom)
 *   with the list in a 320dp left pane and the pager in the right pane. The first featured widget
 *   is selected by default.
 *
 * `settings.wpGroup` picks the default: category shows the filter chips, app hides them, recent
 * adds a "Recently used" section first. Adding a widget records it as recently used, calls [onPick]
 * and then [onDismiss]; the scrim tap and the back button call [onDismiss]. Reads the catalog from
 * [LocalAppGraph]; the caller applies [rememberWidgetPickerBackdropModifier] to the home content.
 */
@Composable
fun WidgetPicker(
    visible: Boolean,
    screen: ScreenKind,
    settings: LauncherSettings,
    onPick: (WidgetEntry) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val graph = LocalAppGraph.current
    val catalog = graph.widgets
    val animMs = a11yDuration(PICKER_ANIM_MS)

    // The catalog loads eagerly in the background and replays its last list, so subscribing while the
    // picker is hidden costs nothing and the first open already has the widgets. null until the first
    // list arrives: the UI shows a spinner then, not "no widgets".
    val entriesFlow = remember(catalog) { catalog.entries.map<List<WidgetEntry>, List<WidgetEntry>?> { it } }
    val loadedEntries: List<WidgetEntry>? by entriesFlow.collectAsStateWithLifecycle(initialValue = null)

    // Recents are only subscribed to while the picker is up (the composable stays in the tree, hidden):
    // a completed empty flow keeps the last values in the state, so the exit animation still has content.
    val recentsFlow = remember(catalog, settings.wpGroup, visible) {
        if (visible && settings.wpGroup == WidgetCatalog.GROUP_RECENT) {
            catalog.grouped(WidgetCatalog.GROUP_RECENT)
        } else {
            emptyFlow<List<Pair<String, List<WidgetEntry>>>>()
        }
    }
    val recentGroups by recentsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val recents = remember(recentGroups, settings.wpGroup) {
        if (settings.wpGroup == WidgetCatalog.GROUP_RECENT) {
            recentGroups.firstOrNull { it.first == WidgetCatalog.RECENT_GROUP_TITLE }?.second ?: emptyList()
        } else {
            emptyList()
        }
    }
    val model = remember(loadedEntries, recents) {
        PickerModel(entries = loadedEntries ?: emptyList(), recents = recents, loading = loadedEntries == null)
    }

    var query by rememberSaveable { mutableStateOf("") }
    // Name of the active WidgetCategory filter chip; "" = All.
    var chip by rememberSaveable { mutableStateOf("") }
    // Level 2: the open app (group key) and the widget the pager starts on. null = level 1 (cover).
    var detailGroupKey by rememberSaveable { mutableStateOf<String?>(null) }
    var detailStartId by rememberSaveable { mutableStateOf<String?>(null) }
    val listState = rememberLazyListState()

    // Reset to the default state only after the exit animation has finished, so the sheet / card does
    // not visibly jump back to the list while it slides away. Reopening before the delay ends cancels
    // the reset (the effect restarts on every change of [visible]).
    LaunchedEffect(visible) {
        if (!visible) {
            delay(animMs.toLong() + 50L)
            query = ""
            chip = ""
            detailGroupKey = null
            detailStartId = null
            listState.scrollToItem(0)
            WidgetPickerIconCache.clear()
        }
    }

    // The category chip narrows an app's pager too, but only when the app row was opened (no specific
    // widget requested); a Featured / search pick always shows the widget that was picked.
    val detailCategory: WidgetCategory? = if (detailStartId == null) {
        WidgetCategory.entries.firstOrNull { it.name == chip }
    } else {
        null
    }

    val explicitTarget: DetailTarget? = detailGroupKey?.let { DetailTarget(it, detailStartId) }
    val defaultTarget: DetailTarget? = remember(model) {
        model.featured.firstOrNull()?.let { DetailTarget(BUILT_IN_GROUP_KEY, it.id) }
            ?: model.groups.firstOrNull()?.let { DetailTarget(it.key, null) }
    }
    // Inner / flex: the right pane always shows something. Cover: only after the user opened an app.
    val innerTarget: DetailTarget? = explicitTarget ?: defaultTarget
    val coverGroup: WidgetAppGroup? = explicitTarget?.let { model.groupByKey[it.groupKey] }

    val open: (DetailTarget) -> Unit = { target ->
        detailGroupKey = target.groupKey
        detailStartId = target.startEntryId
    }
    val closeDetail: () -> Unit = {
        detailGroupKey = null
        detailStartId = null
    }
    val pick: (WidgetEntry) -> Unit = { entry ->
        graph.appScope.launch { catalog.markUsed(entry.id) }
        onPick(entry)
        onDismiss()
    }

    BackHandler(enabled = visible) {
        if (screen == ScreenKind.COVER && coverGroup != null) closeDetail() else onDismiss()
    }

    Box(modifier = modifier.fillMaxSize()) {
        WidgetPickerScrim(visible = visible, blur = settings.popBlur, onDismiss = onDismiss)

        if (screen == ScreenKind.COVER) {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.84f),
                enter = fadeIn(animationSpec = tween(animMs / 2)) +
                    slideInVertically(animationSpec = tween(animMs)) { it },
                exit = fadeOut(animationSpec = tween(animMs)) +
                    slideOutVertically(animationSpec = tween(animMs)) { it },
                label = "coverWidgetSheet",
            ) {
                WidgetPickerPanel(
                    shape = RoundedCornerShape(30.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(start = 6.dp, end = 6.dp, bottom = 6.dp)
                        .imePadding(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .navigationBarsPadding()
                            .padding(top = 10.dp),
                    ) {
                        WidgetPickerGrabHandle(modifier = Modifier.align(Alignment.CenterHorizontally))
                        Crossfade(
                            targetState = coverGroup?.key,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            animationSpec = tween(animMs / 2),
                            label = "widgetPickerLevel",
                        ) { openKey ->
                            if (openKey == null) {
                                WidgetListPane(
                                    model = model,
                                    groupMode = settings.wpGroup,
                                    query = query,
                                    onQueryChange = { query = it },
                                    chip = chip,
                                    onChipChange = { chip = it },
                                    selected = null,
                                    onOpen = open,
                                    listState = listState,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                WidgetDetailPane(
                                    group = model.groupByKey[openKey],
                                    startEntryId = detailStartId,
                                    showBack = true,
                                    onBack = closeDetail,
                                    onAdd = pick,
                                    category = detailCategory,
                                    loading = model.loading,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                        }
                    }
                }
            }
        } else {
            AnimatedVisibility(
                visible = visible,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth(0.82f)
                    .fillMaxHeight()
                    .padding(top = 64.dp, bottom = 40.dp),
                enter = fadeIn(animationSpec = tween(animMs)) +
                    scaleIn(animationSpec = tween(animMs), initialScale = 0.92f),
                exit = fadeOut(animationSpec = tween(animMs)) +
                    scaleOut(animationSpec = tween(animMs), targetScale = 0.94f),
                label = "innerWidgetCard",
            ) {
                WidgetPickerPanel(
                    shape = RoundedCornerShape(32.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding(),
                ) {
                    InnerCardBody(
                        model = model,
                        groupMode = settings.wpGroup,
                        query = query,
                        onQueryChange = { query = it },
                        chip = chip,
                        onChipChange = { chip = it },
                        target = innerTarget,
                        onOpen = open,
                        onAdd = pick,
                        detailCategory = detailCategory,
                        listState = listState,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

/** Inner / flex layout: the list on the left, the pager of the selected app on the right. */
@Composable
private fun InnerCardBody(
    model: PickerModel,
    groupMode: String,
    query: String,
    onQueryChange: (String) -> Unit,
    chip: String,
    onChipChange: (String) -> Unit,
    target: DetailTarget?,
    onOpen: (DetailTarget) -> Unit,
    onAdd: (WidgetEntry) -> Unit,
    detailCategory: WidgetCategory?,
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    Row(modifier = modifier) {
        WidgetListPane(
            model = model,
            groupMode = groupMode,
            query = query,
            onQueryChange = onQueryChange,
            chip = chip,
            onChipChange = onChipChange,
            selected = target,
            onOpen = onOpen,
            listState = listState,
            modifier = Modifier
                .width(InnerListPaneWidth)
                .fillMaxHeight()
                .padding(top = 18.dp),
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(tokens.surfaceBorder),
        )
        WidgetDetailPane(
            group = target?.let { model.groupByKey[it.groupKey] },
            startEntryId = target?.startEntryId,
            showBack = false,
            onBack = {},
            onAdd = onAdd,
            category = detailCategory,
            loading = model.loading,
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(top = 18.dp),
        )
    }
}
