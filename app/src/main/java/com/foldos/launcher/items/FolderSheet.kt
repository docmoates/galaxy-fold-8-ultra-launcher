package com.foldos.launcher.items

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ViewList
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** How far a child must be dragged (after a long press) before it leaves the folder. */
private val DRAG_OUT_DISTANCE: Dp = 90.dp

/** Longest folder title the editor accepts. */
private const val MAX_TITLE_LENGTH = 40

/**
 * An open folder: a pop-up card next to the folder icon (mock-up `.fpop`, drawn with the drawer
 * colour, the theme radius + 4dp and a 14dp shadow). Compose it inside the folder's tile so the
 * `Popup` anchors to the icon: the card centres on the icon and opens below it, or above it when
 * there is no room (see [NearAnchorPositionProvider]).
 *
 * - Header: the title (tap it, or the pencil, to edit in place; Done / the check commits through
 *   [onRename]), a grid / list / big style toggle that stores the per-folder override
 *   (`HomeItem.folderStyle`, cleared when it equals the global `LauncherSettings.folderStyle`).
 * - Body in the resolved style (`folder.folderStyle ?: settings.folderStyle`): a 4-column grid of
 *   46dp icons, a list with 36dp icons, or a 3-column "big" grid of 60dp icons. Tap launches
 *   ([onLaunch] for apps, [AppPairs.launch] for pair children) and closes the folder.
 * - Removal: long-press a child to select it and a "Remove from folder" chip appears; dragging a
 *   selected child more than 90dp (out of the card) removes it straight away. Both call
 *   [onRemove]; the caller places the item back on the grid with
 *   `LayoutRepository.removeFromFolder(item.id, pageId, col, row)`.
 *
 * Reads the settings and writes the style override through [LocalAppGraph].
 *
 * @param children the folder's contents (`LayoutRepository.children(folder.id)`).
 * @param apps installed apps keyed by `AppInfo.key`.
 */
@Composable
fun FolderSheet(
    folder: HomeItem,
    children: List<HomeItem>,
    apps: Map<String, AppInfo>,
    onLaunch: (AppInfo) -> Unit,
    onRename: (String) -> Unit,
    onRemove: (HomeItem) -> Unit,
    onDismiss: () -> Unit,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val tokens = LocalFoldOsTokens.current
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val settings by graph.settings.settings.collectAsState(initial = null)
    val globalStyle = settings?.folderStyle ?: LauncherSettings.FOLDER_GRID
    val style = folder.folderStyle?.takeIf { it in LauncherSettings.FOLDER_STYLES } ?: globalStyle

    val sortedChildren = remember(children) {
        children.sortedWith(compareBy<HomeItem> { it.row }.thenBy { it.col })
    }
    val storedTitle = folder.title ?: Folders.DEFAULT_TITLE

    var editing by remember { mutableStateOf(false) }
    var titleText by remember(folder.id, storedTitle) { mutableStateOf(storedTitle) }
    var selectedId by remember { mutableStateOf<Long?>(null) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val focusRequester = remember { FocusRequester() }

    // The gesture coroutines read the latest callbacks through these so they never go stale.
    val onLaunchState = rememberUpdatedState(onLaunch)
    val onRemoveState = rememberUpdatedState(onRemove)
    val onDismissState = rememberUpdatedState(onDismiss)
    val appsState = rememberUpdatedState(apps)
    val dragOutPx = with(density) { DRAG_OUT_DISTANCE.toPx() }

    val cols = when (style) {
        LauncherSettings.FOLDER_BIG -> 3
        LauncherSettings.FOLDER_LIST -> 1
        else -> 4
    }
    val iconSize: Dp = when (style) {
        LauncherSettings.FOLDER_BIG -> 60.dp
        LauncherSettings.FOLDER_LIST -> 36.dp
        else -> 46.dp
    }
    val cell: Dp = if (style == LauncherSettings.FOLDER_BIG) 88.dp else 66.dp
    val maxWidth: Dp = (configuration.screenWidthDp - 32).coerceAtLeast(200).dp
    val naturalWidth: Dp = if (style == LauncherSettings.FOLDER_LIST) 280.dp else cell * cols + 28.dp
    val cardWidth: Dp = if (naturalWidth > maxWidth) maxWidth else naturalWidth

    fun commitTitle() {
        editing = false
        val next = titleText.trim()
        if (next.isNotEmpty() && next != storedTitle) {
            onRename(next)
        } else {
            titleText = storedTitle
        }
    }

    fun launchChild(child: HomeItem) {
        val current = appsState.value
        if (child.isPair) {
            AppPairs.launch(context, child, current)
        } else {
            current.firstAppOf(child)?.let { onLaunchState.value(it) }
        }
        onDismissState.value()
    }

    LaunchedEffect(editing) {
        if (editing) {
            // The field lives in the popup subcomposition, which attaches a frame later.
            delay(16)
            runCatching { focusRequester.requestFocus() }
        }
    }

    val positionProvider = remember(density) {
        NearAnchorPositionProvider(
            gapPx = with(density) { 12.dp.roundToPx() },
            marginPx = with(density) { 16.dp.roundToPx() },
        )
    }
    val shape = RoundedCornerShape(tokens.radius + 4.dp)

    Popup(
        popupPositionProvider = positionProvider,
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        Column(
            modifier = Modifier
                .width(cardWidth)
                .shadow(14.dp, shape)
                .clip(shape)
                .background(tokens.drawerScrim)
                .border(1.dp, tokens.surfaceBorder, shape)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            /* ---- header: title (editable), style toggle, edit / done ---- */
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (editing) {
                    BasicTextField(
                        value = titleText,
                        onValueChange = { titleText = it.take(MAX_TITLE_LENGTH) },
                        singleLine = true,
                        textStyle = TextStyle(color = tokens.onSurface, fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        cursorBrush = SolidColor(tokens.accent),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction = ImeAction.Done,
                        ),
                        keyboardActions = KeyboardActions(onDone = { commitTitle() }),
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester)
                            .padding(start = 4.dp),
                    )
                } else {
                    Text(
                        text = titleText,
                        color = tokens.onSurface,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { editing = true }
                            .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
                    )
                }
                FolderStyleToggle(
                    style = style,
                    onSelect = { picked ->
                        // Picking the global default clears the override so the folder keeps following it.
                        val override = if (picked == globalStyle) null else picked
                        if (folder.id > 0) {
                            graph.appScope.launch { Folders.setStyle(graph.layouts, folder, override) }
                        }
                    },
                )
                SmallRoundButton(
                    icon = if (editing) Icons.Rounded.Check else Icons.Rounded.Edit,
                    contentDescription = if (editing) "Save name" else "Rename folder",
                    onClick = { if (editing) commitTitle() else editing = true },
                )
            }

            /* ---- body ---- */
            Column(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(if (style == LauncherSettings.FOLDER_LIST) 2.dp else 8.dp),
            ) {
                if (sortedChildren.isEmpty()) {
                    Text(
                        text = "This folder is empty",
                        color = tokens.onSurface.copy(alpha = 0.6f),
                        fontSize = 13.sp,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 18.dp),
                        textAlign = TextAlign.Center,
                    )
                } else if (style == LauncherSettings.FOLDER_LIST) {
                    sortedChildren.forEach { child ->
                        FolderListRow(
                            child = child,
                            apps = apps,
                            iconSize = iconSize,
                            selected = child.id == selectedId,
                            modifier = Modifier.childGestures(
                                child = child,
                                dragOutPx = dragOutPx,
                                isDragging = { draggingId == child.id },
                                dragOffset = { dragOffset },
                                onTap = { launchChild(child) },
                                onSelect = { selectedId = child.id },
                                onDragStart = { draggingId = child.id; dragOffset = Offset.Zero },
                                onDrag = { delta -> dragOffset += delta },
                                onDragEnd = { removed ->
                                    draggingId = null
                                    dragOffset = Offset.Zero
                                    if (removed) {
                                        selectedId = null
                                        onRemoveState.value(child)
                                    }
                                },
                            ),
                        )
                    }
                } else {
                    sortedChildren.chunked(cols).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            rowItems.forEach { child ->
                                Box(modifier = Modifier.weight(1f)) {
                                    FolderGridCell(
                                        child = child,
                                        apps = apps,
                                        iconSize = iconSize,
                                        selected = child.id == selectedId,
                                        modifier = Modifier.childGestures(
                                            child = child,
                                            dragOutPx = dragOutPx,
                                            isDragging = { draggingId == child.id },
                                            dragOffset = { dragOffset },
                                            onTap = { launchChild(child) },
                                            onSelect = { selectedId = child.id },
                                            onDragStart = { draggingId = child.id; dragOffset = Offset.Zero },
                                            onDrag = { delta -> dragOffset += delta },
                                            onDragEnd = { removed ->
                                                draggingId = null
                                                dragOffset = Offset.Zero
                                                if (removed) {
                                                    selectedId = null
                                                    onRemoveState.value(child)
                                                }
                                            },
                                        ),
                                    )
                                }
                            }
                            repeat(cols - rowItems.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
            }

            /* ---- footer: remove chip for the selected child, else the hint ---- */
            val selected = sortedChildren.firstOrNull { it.id == selectedId }
            if (selected != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FolderChip(
                        label = "Remove from folder",
                        accent = true,
                        onClick = {
                            selectedId = null
                            onRemove(selected)
                        },
                    )
                    FolderChip(label = "Cancel", accent = false, onClick = { selectedId = null })
                }
            } else if (sortedChildren.isNotEmpty()) {
                Text(
                    text = "Hold an app to remove it, or drag it out",
                    color = tokens.onSurface.copy(alpha = 0.55f),
                    fontSize = 11.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
}

/**
 * Tap to launch; long press selects (and starts a drag that removes the child once it travels
 * [dragOutPx]). The tap detector is declared first so the drag detector, which consumes the
 * release after a long press, is processed before it and a long press never counts as a tap.
 */
private fun Modifier.childGestures(
    child: HomeItem,
    dragOutPx: Float,
    isDragging: () -> Boolean,
    dragOffset: () -> Offset,
    onTap: () -> Unit,
    onSelect: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: (removed: Boolean) -> Unit,
): Modifier = this
    .graphicsLayer {
        if (isDragging()) {
            val offset = dragOffset()
            translationX = offset.x
            translationY = offset.y
            alpha = 0.85f
        }
    }
    .pointerInput(child.id) {
        detectTapGestures(onTap = { onTap() })
    }
    .pointerInput(child.id, dragOutPx) {
        var travelled = Offset.Zero
        detectDragGesturesAfterLongPress(
            onDragStart = {
                travelled = Offset.Zero
                onSelect()
                onDragStart()
            },
            onDrag = { change, delta ->
                change.consume()
                travelled += delta
                onDrag(delta)
            },
            onDragEnd = { onDragEnd(travelled.getDistance() > dragOutPx) },
            onDragCancel = { onDragEnd(false) },
        )
    }

/** One child in the grid / big layouts: icon over a single-line label. */
@Composable
private fun FolderGridCell(
    child: HomeItem,
    apps: Map<String, AppInfo>,
    iconSize: Dp,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val cellShape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(cellShape)
            .background(if (selected) tokens.highlight else Color.Transparent)
            .padding(horizontal = 2.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FolderChildIcon(child, apps, iconSize)
        Spacer(Modifier.height(5.dp))
        Text(
            text = childLabel(child, apps),
            color = if (selected) tokens.onHighlight else tokens.onSurface,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** One child in the list layout: icon, then the label filling the row. */
@Composable
private fun FolderListRow(
    child: HomeItem,
    apps: Map<String, AppInfo>,
    iconSize: Dp,
    selected: Boolean,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val rowShape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(rowShape)
            .background(if (selected) tokens.highlight else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        FolderChildIcon(child, apps, iconSize)
        Text(
            text = childLabel(child, apps),
            color = if (selected) tokens.onHighlight else tokens.onSurface,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** App icon, pair icon or (for an uninstalled app) an outlined square. */
@Composable
private fun FolderChildIcon(child: HomeItem, apps: Map<String, AppInfo>, iconSize: Dp) {
    val tokens = LocalFoldOsTokens.current
    val app = apps.firstAppOf(child)
    when {
        child.isPair -> PairIcon(pair = child, a = app, b = apps.secondAppOf(child), sizeDp = iconSize.value.toInt())
        app != null -> ItemAppIcon(app = app, size = iconSize)
        else -> Box(
            Modifier
                .size(iconSize)
                .border(1.dp, tokens.surfaceBorder, itemCornerShape(iconSize, tokens)),
        )
    }
}

private fun childLabel(child: HomeItem, apps: Map<String, AppInfo>): String {
    val app = apps.firstAppOf(child)
    return when {
        child.isPair -> pairTitle(child, app, apps.secondAppOf(child))
        app != null -> app.label
        else -> child.title ?: "Not installed"
    }
}

/** Grid / list / big segmented toggle (28dp squares; the current one filled in the accent). */
@Composable
private fun FolderStyleToggle(style: String, onSelect: (String) -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val pill = RoundedCornerShape(999.dp)
    Row(
        modifier = Modifier
            .clip(pill)
            .background(tokens.surface)
            .border(1.dp, tokens.surfaceBorder, pill)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        StyleSegment(LauncherSettings.FOLDER_GRID, Icons.Rounded.Apps, style, onSelect)
        StyleSegment(LauncherSettings.FOLDER_LIST, Icons.Rounded.ViewList, style, onSelect)
        StyleSegment(LauncherSettings.FOLDER_BIG, Icons.Rounded.ZoomOutMap, style, onSelect)
    }
}

@Composable
private fun StyleSegment(value: String, icon: ImageVector, current: String, onSelect: (String) -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val on = value == current
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(if (on) tokens.accent else Color.Transparent)
            .clickable { onSelect(value) },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = Folders.styleName(value),
            tint = if (on) tokens.onAccent else tokens.onSurface.copy(alpha = 0.7f),
            modifier = Modifier.size(15.dp),
        )
    }
}

@Composable
private fun SmallRoundButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(tokens.surface)
            .border(1.dp, tokens.surfaceBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tokens.onSurface,
            modifier = Modifier.size(16.dp),
        )
    }
}

/** Small pill chip for the footer actions. */
@Composable
private fun FolderChip(label: String, accent: Boolean, onClick: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val pill = RoundedCornerShape(999.dp)
    Text(
        text = label,
        color = if (accent) tokens.onAccent else tokens.onSurface,
        fontSize = 12.5.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .clip(pill)
            .background(if (accent) tokens.accent else tokens.surface)
            .border(1.dp, if (accent) Color.Transparent else tokens.surfaceBorder, pill)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    )
}
