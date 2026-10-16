package com.foldos.launcher.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.a11yAppLabel
import com.foldos.launcher.a11y.a11yButton
import com.foldos.launcher.a11y.a11yDirectionalFocus
import com.foldos.launcher.a11y.a11yLiveRegion
import com.foldos.launcher.apppicker.appTileGestures
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.ui.DragController
import com.foldos.launcher.core.ui.DragPayload
import com.foldos.launcher.home.AppLabel
import com.foldos.launcher.home.AppLabelStyle
import com.foldos.launcher.theme.LocalFoldOsTokens

/** Pill search field at the top of the drawer (mock-up `.srch`). */
@Composable
internal fun DrawerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    appCount: Int,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
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
            Text(
                text = "Search $appCount apps",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = shape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
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

/** A-Z app grid; the bottom 15% fades out like the mock-up's masked `.ag`. */
@Composable
internal fun DrawerAppGrid(
    apps: List<AppInfo>,
    columns: Int,
    iconSize: Dp,
    onLaunch: (AppInfo) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(bottom = 24.dp),
    badgeCounts: Map<String, Int> = emptyMap(),
    /** Null disables drag and drop (the long press then only opens [onAppMenu]). */
    dragController: DragController? = null,
    onDragStarted: () -> Unit = {},
    /** Long press without a drag: the app's menu (Add to group, App info, ...). */
    onAppMenu: ((AppInfo) -> Unit)? = null,
    emptyText: String = "No apps match",
) {
    if (apps.isEmpty()) {
        DrawerEmptyState(text = emptyText, modifier = modifier)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns.coerceAtLeast(1)),
        modifier = modifier
            .fillMaxSize()
            // Hardware keyboard: arrow keys move focus between the app tiles.
            .a11yDirectionalFocus()
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = Brush.verticalGradient(
                        0.85f to Color.Black,
                        1.0f to Color.Transparent,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            },
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(22.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        items(items = apps, key = { it.key }) { app ->
            DrawerAppTile(
                app = app,
                iconSize = iconSize,
                badge = badgeCounts[app.packageName] ?: 0,
                onClick = { onLaunch(app) },
                dragController = dragController,
                onDragStarted = onDragStarted,
                onMenu = onAppMenu?.let { menu -> { menu(app) } },
            )
        }
    }
}

@Composable
private fun DrawerAppTile(
    app: AppInfo,
    iconSize: Dp,
    onClick: () -> Unit,
    dragController: DragController?,
    onDragStarted: () -> Unit,
    onMenu: (() -> Unit)?,
    badge: Int = 0,
) {
    val tokens = LocalFoldOsTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            // One TalkBack element: "Gmail, 3 notifications" (icon description and label merged);
            // the long press (menu / drag) is also offered as the "App options" action.
            .a11yButton(
                label = a11yAppLabel(app.label, badge),
                onActivate = onClick,
                onMenu = onMenu,
                menuLabel = "App options",
            )
            .clickable(onClick = onClick)
            // Inside the clickable: long press then drag moves the app to the home screen or the dock,
            // long press and release opens the menu.
            .appTileGestures(
                controller = dragController,
                payload = { DragPayload.App(app) },
                onLongPress = onMenu,
                onDragStarted = onDragStarted,
            )
            .padding(horizontal = 1.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DrawerAppIcon(app = app, size = iconSize)
        Spacer(modifier = Modifier.height(AppLabelStyle.ICON_GAP))
        // Shared label style (two lines, medium weight); Easy mode and the size from Settings > Home apply inside.
        AppLabel(text = app.label, color = tokens.onSurface, onWallpaper = false)
    }
}

@Composable
private fun DrawerEmptyState(text: String, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            textAlign = TextAlign.Center,
            // Announced when a search leaves no results.
            modifier = Modifier.a11yLiveRegion(),
            color = tokens.onSurface.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/** The grab handle shown on the cover-screen sheet (mock-up `.grab`). */
@Composable
internal fun DrawerGrabHandle(modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Box(
        modifier = modifier
            .width(40.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(tokens.onSurface.copy(alpha = 0.35f)),
    )
}
