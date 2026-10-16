package com.foldos.launcher.drawer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import com.foldos.launcher.a11y.LocalLargeIcons
import com.foldos.launcher.a11y.a11yAppLabel
import com.foldos.launcher.a11y.a11yButton
import com.foldos.launcher.a11y.a11yDirectionalFocus
import com.foldos.launcher.a11y.a11yLiveRegion
import com.foldos.launcher.a11y.a11yMinTouchTarget
import com.foldos.launcher.core.model.AppInfo
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

/** Horizontally scrolling category chips (mock-up `.dtabs`). */
@Composable
internal fun DrawerCategoryChips(
    selected: DrawerCategory,
    onSelect: (DrawerCategory) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        DrawerCategory.entries.forEach { category ->
            DrawerChip(
                category = category,
                selected = category == selected,
                onClick = { onSelect(category) },
            )
        }
    }
}

@Composable
private fun DrawerChip(
    category: DrawerCategory,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(50)
    val background = if (selected) tokens.accent else tokens.surface
    val foreground = if (selected) tokens.onAccent else tokens.onSurface
    val borderColor = if (selected) Color.Transparent else tokens.surfaceBorder
    Row(
        modifier = Modifier
            .a11yMinTouchTarget()
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            // A tab of the category strip: read as "Media, tab, selected".
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        if (category.locked) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = category.title,
            color = foreground,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
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
) {
    if (apps.isEmpty()) {
        DrawerEmptyState(modifier = modifier)
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
            )
        }
    }
}

@Composable
private fun DrawerAppTile(
    app: AppInfo,
    iconSize: Dp,
    onClick: () -> Unit,
    badge: Int = 0,
) {
    val tokens = LocalFoldOsTokens.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            // One TalkBack element: "Gmail, 3 notifications" (icon description and label merged).
            .a11yButton(label = a11yAppLabel(app.label, badge), onActivate = onClick)
            .clickable(onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DrawerAppIcon(app = app, size = iconSize)
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = app.label,
            color = tokens.onSurface,
            style = MaterialTheme.typography.labelMedium,
            // Easy mode: larger labels.
            fontSize = if (LocalLargeIcons.current) 15.sp else TextUnit.Unspecified,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun DrawerEmptyState(modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No apps match",
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
