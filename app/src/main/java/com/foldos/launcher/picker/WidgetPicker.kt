package com.foldos.launcher.picker

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import com.foldos.launcher.a11y.a11yButton
import com.foldos.launcher.a11y.a11yDuration
import com.foldos.launcher.a11y.a11yHeading
import com.foldos.launcher.a11y.a11yMinTouchTarget
import com.foldos.launcher.a11y.rememberA11yListScroll
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.model.WidgetEntry
import com.foldos.launcher.core.repo.BuiltInWidgetRegistry
import com.foldos.launcher.core.repo.WidgetCatalog
import com.foldos.launcher.drawer.rememberDrawerBackdropModifier
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.launch

private const val PICKER_ANIM_MS = 320

/** Preview cell size on the cover sheet (mock-up zoom .4 of a ~90dp cell). */
private val CoverPreviewCell: Dp = 36.dp

/** Preview cell size on the inner card (mock-up zoom .44). */
private val InnerPreviewCell: Dp = 40.dp

/** A group as returned by [WidgetCatalog.grouped]: title to entries. */
private typealias WidgetGroup = Pair<String, List<WidgetEntry>>

/**
 * Modifier the caller puts on the home content (NOT on [WidgetPicker]) so the home blurs while
 * the picker is open. Same behaviour and API 31 guard as the drawer's backdrop; when both the
 * drawer and the picker can be open, pass `drawerOpen || pickerOpen` as [visible].
 */
@Composable
fun rememberWidgetPickerBackdropModifier(visible: Boolean, blur: Boolean): Modifier =
    rememberDrawerBackdropModifier(visible = visible, blur = blur)

/**
 * The "Add a widget" picker (PLAN.md 2.10, mock-up `widgetPicker()` / `.wpick`). Place it as a
 * top layer of the home Box, filling the screen; it draws nothing while [visible] is false.
 *
 * - [ScreenKind.COVER]: bottom sheet at 84% height with a grab handle, "Add a widget" title with
 *   the total count, search field, horizontal group chips, and one collapsible section per group,
 *   each a horizontal row of previews. Tapping a chip jumps to that section.
 * - [ScreenKind.INNER] / [ScreenKind.FLEX]: wide floating card with the group list on the left
 *   (icon, name, count; selected highlighted) and a grid of previews on the right.
 *
 * Groups follow `settings.wpGroup` (category / app / recent). Search filters names, sources and
 * categories across every group. Tapping a preview records it as recently used, calls [onPick]
 * and then [onDismiss]; the scrim tap and the back button call [onDismiss]. Reads the catalog and
 * built-ins from [LocalAppGraph]; the caller applies [rememberWidgetPickerBackdropModifier] to
 * the home content for the blur.
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
    val builtIns = graph.builtIns
    val animMs = a11yDuration(PICKER_ANIM_MS)

    val groupsFlow = remember(catalog, settings.wpGroup) { catalog.grouped(settings.wpGroup) }
    val groups by groupsFlow.collectAsState(initial = emptyList())

    var query by rememberSaveable { mutableStateOf("") }
    // Inner card: selected group title; "" = first group. Cover: chip title; "" = All.
    var selectedTitle by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(visible) {
        if (!visible) {
            query = ""
            selectedTitle = ""
        }
    }

    BackHandler(enabled = visible) { onDismiss() }

    val totalCount = remember(groups) {
        groups.filter { it.first != WidgetCatalog.RECENT_GROUP_TITLE }.sumOf { it.second.size }
    }
    val filteredGroups = remember(groups, query) { filterGroups(groups, query) }

    val pick: (WidgetEntry) -> Unit = { entry ->
        graph.appScope.launch { catalog.markUsed(entry.id) }
        onPick(entry)
        onDismiss()
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
                    CoverSheetBody(
                        groups = filteredGroups,
                        totalCount = totalCount,
                        query = query,
                        onQueryChange = { query = it },
                        activeChip = selectedTitle,
                        onChipSelected = { selectedTitle = it },
                        builtIns = builtIns,
                        onPick = pick,
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
                    .fillMaxWidth(0.9f)
                    .fillMaxHeight()
                    .padding(top = 50.dp, bottom = 34.dp),
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
                        groups = groups,
                        filteredGroups = filteredGroups,
                        totalCount = totalCount,
                        groupMode = settings.wpGroup,
                        query = query,
                        onQueryChange = { query = it },
                        selectedTitle = selectedTitle,
                        onSelectGroup = { selectedTitle = it },
                        builtIns = builtIns,
                        onPick = pick,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

// ---- cover sheet ------------------------------------------------------------------------------

@Composable
private fun CoverSheetBody(
    groups: List<WidgetGroup>,
    totalCount: Int,
    query: String,
    onQueryChange: (String) -> Unit,
    activeChip: String,
    onChipSelected: (String) -> Unit,
    builtIns: BuiltInWidgetRegistry,
    onPick: (WidgetEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollToItem = rememberA11yListScroll(listState)
    val collapsed = remember { mutableStateMapOf<String, Boolean>() }

    Column(
        modifier = modifier.padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        WidgetPickerGrabHandle(modifier = Modifier.align(Alignment.CenterHorizontally))

        PickerTitle(
            title = "Add a widget",
            count = totalCount,
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        WidgetPickerSearchField(
            query = query,
            onQueryChange = onQueryChange,
            placeholder = "Search widgets, apps or features",
            modifier = Modifier.padding(horizontal = 16.dp),
        )

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "all") {
                PickerChip(
                    title = "All",
                    selected = activeChip.isEmpty(),
                    onClick = {
                        onChipSelected("")
                        scope.launch { scrollToItem(0) }
                    },
                )
            }
            itemsIndexed(items = groups, key = { _, group -> group.first }) { index, group ->
                PickerChip(
                    title = group.first,
                    selected = activeChip == group.first,
                    onClick = {
                        onChipSelected(group.first)
                        collapsed[group.first] = false
                        scope.launch { scrollToItem(index) }
                    },
                )
            }
        }

        if (groups.isEmpty()) {
            PickerEmptyState(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 26.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(items = groups, key = { it.first }) { group ->
                    val isCollapsed = collapsed[group.first] == true
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        SectionHeader(
                            icon = WidgetPickerIcons.forGroup(group.first),
                            title = group.first,
                            count = group.second.size,
                            trailing = {
                                Icon(
                                    imageVector = if (isCollapsed) Icons.Rounded.ExpandMore else Icons.Rounded.ExpandLess,
                                    contentDescription = if (isCollapsed) "Expand" else "Collapse",
                                    tint = tokens.onSurface.copy(alpha = 0.6f),
                                    modifier = Modifier.size(20.dp),
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable(onClickLabel = if (isCollapsed) "Expand section" else "Collapse section", role = Role.Button) { collapsed[group.first] = !isCollapsed }
                                .padding(vertical = 4.dp),
                        )
                        if (!isCollapsed) {
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                items(items = group.second, key = { it.id }) { entry ->
                                    WidgetPreviewTile(
                                        entry = entry,
                                        builtIn = if (entry.isBuiltIn) builtIns.byId(entry.id) else null,
                                        cellSize = CoverPreviewCell,
                                        onClick = { onPick(entry) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- inner / flex card ------------------------------------------------------------------------

@Composable
private fun InnerCardBody(
    groups: List<WidgetGroup>,
    filteredGroups: List<WidgetGroup>,
    totalCount: Int,
    groupMode: String,
    query: String,
    onQueryChange: (String) -> Unit,
    selectedTitle: String,
    onSelectGroup: (String) -> Unit,
    builtIns: BuiltInWidgetRegistry,
    onPick: (WidgetEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val searching = query.isNotBlank()
    val selected: WidgetGroup? = remember(groups, selectedTitle) {
        groups.firstOrNull { it.first == selectedTitle } ?: groups.firstOrNull()
    }
    // While searching the grid shows every match across the groups.
    val shown: List<WidgetEntry> = remember(filteredGroups, selected, searching) {
        if (searching) filteredGroups.flatMap { it.second }.distinctBy { it.id }
        else selected?.second ?: emptyList()
    }
    val headerTitle = if (searching) "Results" else selected?.first ?: "Widgets"
    val headerIcon = if (searching) Icons.Rounded.Search else WidgetPickerIcons.forGroup(headerTitle)
    val modeLabel = when (groupMode) {
        WidgetCatalog.GROUP_APP -> "By app"
        WidgetCatalog.GROUP_RECENT -> "Recent first"
        else -> "By category"
    }

    Row(modifier = modifier) {
        // Group list (mock-up .wp-nav)
        Column(
            modifier = Modifier
                .width(262.dp)
                .fillMaxHeight()
                .padding(vertical = 20.dp, horizontal = 12.dp),
        ) {
            Text(
                text = "Add a widget",
                color = tokens.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                fontSize = 20.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 12.dp),
            )
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(items = groups, key = { it.first }) { group ->
                    NavItem(
                        icon = WidgetPickerIcons.forGroup(group.first),
                        title = group.first,
                        count = group.second.size,
                        selected = !searching && group === selected,
                        onClick = { onSelectGroup(group.first) },
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(tokens.surfaceBorder),
        )

        // Previews (mock-up .wp-main)
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 20.dp, horizontal = 22.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            WidgetPickerSearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = "Search $totalCount widgets",
                trailingWhenEmpty = {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = tokens.onSurface.copy(alpha = 0.7f),
                    )
                },
            )

            SectionHeader(
                icon = headerIcon,
                title = headerTitle,
                count = shown.size,
                trailing = { ModePill(text = modeLabel) },
                modifier = Modifier.fillMaxWidth(),
            )

            if (shown.isEmpty()) {
                PickerEmptyState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = widgetTileWidth(InnerPreviewCell) + 8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    items(items = shown, key = { it.id }) { entry ->
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
                            WidgetPreviewTile(
                                entry = entry,
                                builtIn = if (entry.isBuiltIn) builtIns.byId(entry.id) else null,
                                cellSize = InnerPreviewCell,
                                onClick = { onPick(entry) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavItem(
    icon: ImageVector,
    title: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(14.dp)
    val foreground = if (selected) tokens.onAccent else tokens.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .a11yMinTouchTarget()
            .clip(shape)
            .background(if (selected) tokens.accent else Color.Transparent)
            // Widget group list: read as "Clock & Time, tab, selected".
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = foreground.copy(alpha = 0.8f),
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            color = foreground,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            color = foreground.copy(alpha = if (selected) 0.8f else 0.55f),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
    }
}

// ---- shared pieces ----------------------------------------------------------------------------

/**
 * Dimming layer behind the picker; a tap anywhere on it dismisses. Same colours as the drawer's
 * scrim: black 38% without blur, black 22% with blur, white 18% with blur in light mode.
 */
@Composable
private fun WidgetPickerScrim(visible: Boolean, blur: Boolean, onDismiss: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val animMs = a11yDuration(PICKER_ANIM_MS)
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
        label = "widgetPickerScrim",
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                // TalkBack: the dimmed backdrop is a "Close" button.
                .a11yButton(label = "Close widget picker", onActivate = onDismiss)
                .pointerInput(onDismiss) {
                    detectTapGestures(onTap = { onDismiss() })
                },
        )
    }
}

/** Panel container: themed sheet fill, hairline border, shadow, and a tap sink. */
@Composable
private fun WidgetPickerPanel(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    Box(
        modifier = modifier
            .shadow(elevation = 24.dp, shape = shape, clip = false)
            .clip(shape)
            .background(tokens.drawerScrim)
            .border(1.dp, tokens.surfaceBorder, shape)
            .semantics { paneTitle = "Add a widget" }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { /* swallow: keep taps inside the panel */ })
            },
    ) {
        content()
    }
}

@Composable
private fun WidgetPickerGrabHandle(modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Box(
        modifier = modifier
            .width(40.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(tokens.onSurface.copy(alpha = 0.35f)),
    )
}

/** "Add a widget" with the total count in a small pill (mock-up `.wp-t` + `.cnt`). */
@Composable
private fun PickerTitle(title: String, count: Int, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            color = tokens.onSurface,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.a11yHeading(),
        )
        ModePill(text = count.toString())
    }
}

/** Small outlined pill (mock-up `.cnt` / `.wp-h em`). */
@Composable
private fun ModePill(text: String) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        color = tokens.onSurface,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(tokens.surface)
            .border(1.dp, tokens.surfaceBorder, shape)
            .padding(horizontal = 10.dp, vertical = 3.dp),
    )
}

/** Section / grid header: accent icon, title, faint count, then [trailing] at the end (mock-up `.wp-h`). */
@Composable
private fun SectionHeader(
    icon: ImageVector,
    title: String,
    count: Int,
    trailing: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.accent,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = title,
            color = tokens.onSurface,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            fontSize = 15.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.a11yHeading(),
        )
        Text(
            text = count.toString(),
            color = tokens.onSurface.copy(alpha = 0.6f),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
        )
        Spacer(modifier = Modifier.weight(1f))
        trailing()
    }
}

/** Pill chip (mock-up `.dtabs span` / `.wp-chips`). */
@Composable
private fun PickerChip(title: String, selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(50)
    val background = if (selected) tokens.accent else tokens.surface
    val foreground = if (selected) tokens.onAccent else tokens.onSurface
    val borderColor = if (selected) Color.Transparent else tokens.surfaceBorder
    Text(
        text = title,
        color = foreground,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .a11yMinTouchTarget()
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** Pill search field (mock-up `.srch`); [trailingWhenEmpty] shows while there is no query. */
@Composable
private fun WidgetPickerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    trailingWhenEmpty: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(26.dp)
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, tokens.surfaceBorder, shape),
        placeholder = {
            Text(text = placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingIcon = {
            Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear search")
                }
            } else if (trailingWhenEmpty != null) {
                trailingWhenEmpty()
            }
        },
        singleLine = true,
        shape = shape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { /* results already filter live */ }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = tokens.surface,
            unfocusedContainerColor = tokens.surface,
            disabledContainerColor = tokens.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = tokens.accent,
            focusedTextColor = tokens.onSurface,
            unfocusedTextColor = tokens.onSurface,
            focusedPlaceholderColor = tokens.onSurface.copy(alpha = 0.7f),
            unfocusedPlaceholderColor = tokens.onSurface.copy(alpha = 0.7f),
            focusedLeadingIconColor = tokens.onSurface,
            unfocusedLeadingIconColor = tokens.onSurface,
            focusedTrailingIconColor = tokens.onSurface,
            unfocusedTrailingIconColor = tokens.onSurface,
        ),
    )
}

@Composable
private fun PickerEmptyState(modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = "No widgets match",
            color = tokens.onSurface.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * Keeps the entries whose name, source or category contains [query] (case-insensitive) and drops
 * groups that end up empty; the unfiltered list for a blank query.
 */
private fun filterGroups(groups: List<WidgetGroup>, query: String): List<WidgetGroup> {
    val q = query.trim()
    if (q.isEmpty()) return groups
    return groups.mapNotNull { (title, entries) ->
        val matching = entries.filter { entry ->
            entry.name.contains(q, ignoreCase = true) ||
                entry.source.contains(q, ignoreCase = true) ||
                entry.category.displayName.contains(q, ignoreCase = true)
        }
        if (matching.isEmpty()) null else title to matching
    }
}
