package com.foldos.launcher.items

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import com.foldos.launcher.a11y.a11yGreyscale
import com.foldos.launcher.a11y.a11yMinTouchTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.theme.LocalFoldOsTokens

/**
 * Bottom sheet that builds an app pair: two slots (App A / App B) with a live [PairIcon] preview
 * and a swap button between them, a search field and a grid of [apps]. Tapping an app fills the
 * active slot (the empty one, or the one tapped last); "Create pair" is enabled once both slots
 * hold different apps and calls [onCreate] then [onDismiss]. The caller persists the pair with
 * `LayoutRepository.createPair(pageId, col, row, a, b)`.
 *
 * @param apps the apps to pick from (normally `graph.apps.apps`, hidden apps excluded).
 * @param initial pre-fills App A (the "Add to pair" context action passes the long-pressed app).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PairEditorSheet(
    apps: List<AppInfo>,
    onCreate: (AppInfo, AppInfo) -> Unit,
    onDismiss: () -> Unit,
    initial: AppInfo? = null,
) {
    val tokens = LocalFoldOsTokens.current
    var first by remember(initial) { mutableStateOf(initial) }
    var second by remember { mutableStateOf<AppInfo?>(null) }
    var activeSlot by remember(initial) { mutableIntStateOf(if (initial == null) 0 else 1) }
    var query by rememberSaveable { mutableStateOf("") }

    val filtered: List<AppInfo> = remember(apps, query) {
        val q = query.trim()
        if (q.isEmpty()) apps else apps.filter { it.label.contains(q, ignoreCase = true) || it.packageName.contains(q, ignoreCase = true) }
    }
    val a = first
    val b = second
    val ready = a != null && b != null && a.key != b.key
    val previewItem = remember { HomeItem(id = 0L, pageId = 0L, kind = ItemKind.PAIR, col = 0, row = 0) }

    fun pick(app: AppInfo) {
        if (activeSlot == 0) {
            first = app
            if (second == null) activeSlot = 1
        } else {
            second = app
            if (first == null) activeSlot = 0
        }
    }

    ModalBottomSheet(
        modifier = Modifier.a11yGreyscale(),
        onDismissRequest = onDismiss,
        containerColor = tokens.drawerScrim,
        contentColor = tokens.onSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "New app pair",
                    color = tokens.onSurface,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Pick two apps to open side by side",
                    color = tokens.onSurface.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            /* ---- slots + preview ---- */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PairSlot(
                    caption = "App A",
                    app = a,
                    active = activeSlot == 0,
                    onClick = { activeSlot = 0 },
                    onClear = {
                        first = null
                        activeSlot = 0
                    },
                    modifier = Modifier.weight(1f),
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    PairIcon(pair = previewItem, a = a, b = b, sizeDp = 56)
                    Box(
                        modifier = Modifier
                            .a11yMinTouchTarget()
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(tokens.surface)
                            .border(1.dp, tokens.surfaceBorder, CircleShape)
                            .clickable(onClickLabel = "Swap app A and app B", role = Role.Button) {
                                val swap = first
                                first = second
                                second = swap
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SwapHoriz,
                            contentDescription = "Swap apps",
                            tint = tokens.onSurface,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
                PairSlot(
                    caption = "App B",
                    app = b,
                    active = activeSlot == 1,
                    onClick = { activeSlot = 1 },
                    onClear = {
                        second = null
                        activeSlot = 1
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            /* ---- search ---- */
            TextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(text = "Search apps", color = tokens.onSurface.copy(alpha = 0.55f)) },
                leadingIcon = {
                    Icon(imageVector = Icons.Rounded.Search, contentDescription = null, tint = tokens.onSurface.copy(alpha = 0.75f))
                },
                shape = RoundedCornerShape(16.dp),
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
                ),
                modifier = Modifier.fillMaxWidth(),
            )

            /* ---- app grid ---- */
            if (filtered.isEmpty()) {
                Text(
                    text = if (query.isBlank()) "No apps to choose from" else "No apps match \"${query.trim()}\"",
                    color = tokens.onSurface.copy(alpha = 0.65f),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                    textAlign = TextAlign.Center,
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 72.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 340.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    items(items = filtered, key = { it.key }) { app ->
                        PairPickTile(
                            app = app,
                            selected = app.key == a?.key || app.key == b?.key,
                            onClick = { pick(app) },
                        )
                    }
                }
            }

            Button(
                onClick = {
                    val chosenA = first
                    val chosenB = second
                    if (chosenA != null && chosenB != null && chosenA.key != chosenB.key) {
                        onCreate(chosenA, chosenB)
                        onDismiss()
                    }
                },
                enabled = ready,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.accent,
                    contentColor = tokens.onAccent,
                    disabledContainerColor = tokens.surface,
                    disabledContentColor = tokens.onSurface.copy(alpha = 0.45f),
                ),
            ) {
                Text(
                    text = if (ready && a != null && b != null) "Create ${a.label} & ${b.label}" else "Create pair",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** One slot: the picked app's icon with a clear button, or a dashed "+" placeholder; the caption under it. */
@Composable
private fun PairSlot(
    caption: String,
    app: AppInfo?,
    active: Boolean,
    onClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (active) tokens.highlight else tokens.surface)
            .border(
                width = if (active) 2.dp else 1.dp,
                color = if (active) tokens.accent else tokens.surfaceBorder,
                shape = shape,
            )
            .clickable(role = Role.Button, onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.size(56.dp)) {
            if (app != null) {
                ItemAppIcon(app = app, size = 48.dp, modifier = Modifier.align(Alignment.BottomStart))
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(tokens.drawerScrim)
                        .border(1.dp, tokens.surfaceBorder, CircleShape)
                        .clickable(role = Role.Button, onClick = onClear),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = "Clear $caption",
                        tint = tokens.onSurface,
                        modifier = Modifier.size(12.dp),
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(48.dp)
                        .clip(itemCornerShape(48.dp, tokens))
                        .border(1.dp, tokens.onSurface.copy(alpha = 0.35f), itemCornerShape(48.dp, tokens)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        tint = tokens.onSurface.copy(alpha = 0.6f),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        }
        Text(
            text = app?.label ?: "Choose an app",
            color = if (active) tokens.onHighlight else tokens.onSurface,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = caption,
            color = (if (active) tokens.onHighlight else tokens.onSurface).copy(alpha = 0.6f),
            fontSize = 10.5.sp,
            maxLines = 1,
        )
    }
}

/** One app in the picker grid; the two chosen apps are highlighted. */
@Composable
private fun PairPickTile(app: AppInfo, selected: Boolean, onClick: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) tokens.highlight else Color.Transparent)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItemAppIcon(app = app, size = 44.dp)
        Spacer(Modifier.height(5.dp))
        Text(
            text = app.label,
            color = if (selected) tokens.onHighlight else tokens.onSurface,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
