package com.foldos.launcher.icons

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.foldos.launcher.core.AppGraph
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.repo.IconShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/*
 * Settings > Look sheets for icons: pack picker, shape picker and the per-app override sheet.
 * They read the AppGraph from the Application, so they work from both the launcher and the
 * Settings activity without a composition local.
 */

@Composable
private fun rememberIconGraph(): AppGraph {
    val context = LocalContext.current
    return remember(context) { FoldOsApplication.from(context).graph }
}

@Composable
private fun rememberLauncherSettings(graph: AppGraph): LauncherSettings? {
    val settings by graph.settings.settings.collectAsState(initial = null)
    return settings
}

/** Rasterises [load]'s drawable off the main thread into a [sizePx] square. Null while loading or on failure. */
@Composable
private fun rememberDrawableBitmap(key: Any?, sizePx: Int, load: () -> Drawable?): ImageBitmap? {
    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = key, key2 = sizePx) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val px = sizePx.coerceAtLeast(1)
                load()?.toBitmap(px, px, Bitmap.Config.ARGB_8888)?.asImageBitmap()
            }.getOrNull()
        }
    }
    return bitmap
}

@Composable
private fun SheetHeader(title: String, subtitle: String?) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        if (subtitle != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun IconTile(bitmap: ImageBitmap?, size: Dp, contentDescription: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(percent = 24))
            .background(if (bitmap == null) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = contentDescription, modifier = Modifier.size(size))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Icon pack picker
// ---------------------------------------------------------------------------------------------

/**
 * Settings > Look > Icon pack: "System icons" plus every installed pack (Nova / ADW / Apex / Nova
 * theme intents), with the selected one marked. Selecting writes `LauncherSettings.iconPack`
 * through `graph.icons.setPack` on `graph.appScope`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconPackPicker(onClose: () -> Unit) {
    val graph = rememberIconGraph()
    val context = LocalContext.current
    val settings = rememberLauncherSettings(graph)
    val packs by graph.icons.packs.collectAsState(initial = emptyList())
    val iconPx = with(LocalDensity.current) { 40.dp.roundToPx() }
    val packageManager = context.packageManager
    val selected = settings?.iconPack

    fun choose(packageName: String?) {
        graph.appScope.launch { graph.icons.setPack(packageName) }
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        SheetHeader(title = "Icon pack", subtitle = "Any pack made for Nova, ADW, Apex or Lawnchair works")
        val systemBitmap = rememberDrawableBitmap(key = "system", sizePx = iconPx) {
            packageManager.getApplicationIcon(context.packageName)
        }
        PackRow(
            title = "System icons",
            subtitle = "The icons apps ship with, masked to the icon shape",
            bitmap = systemBitmap,
            selected = selected == null,
            onClick = { choose(null) },
        )
        packs.forEach { pack ->
            key(pack.packageName) {
                val bitmap = rememberDrawableBitmap(key = pack.packageName, sizePx = iconPx) {
                    packageManager.getApplicationIcon(pack.packageName)
                }
                PackRow(
                    title = pack.label,
                    subtitle = pack.packageName,
                    bitmap = bitmap,
                    selected = selected == pack.packageName,
                    onClick = { choose(pack.packageName) },
                )
            }
        }
        if (packs.isEmpty()) {
            Text(
                text = "No icon packs installed. Install any pack from the Play Store and it appears here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun PackRow(
    title: String,
    subtitle: String?,
    bitmap: ImageBitmap?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        leadingContent = { IconTile(bitmap = bitmap, size = 40.dp, contentDescription = null) },
        headlineContent = { Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = if (subtitle == null) null else {
            { Text(text = subtitle, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = { RadioButton(selected = selected, onClick = onClick) },
    )
}

// ---------------------------------------------------------------------------------------------
// Icon shape picker
// ---------------------------------------------------------------------------------------------

/**
 * Settings > Look > Icon shape: the five [IconShape]s previewed on the FoldOS icon. Selecting
 * writes `LauncherSettings.iconShape` through `graph.icons.setShape` on `graph.appScope`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IconShapePicker(onClose: () -> Unit) {
    val graph = rememberIconGraph()
    val context = LocalContext.current
    val settings = rememberLauncherSettings(graph)
    val selected = settings?.iconShape ?: IconShape.SYSTEM
    val shaper = remember(context) { IconShaper(context) }
    val previewPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    val packageManager = context.packageManager

    ModalBottomSheet(onDismissRequest = onClose) {
        SheetHeader(
            title = "Icon shape",
            subtitle = "Applies to app icons that the icon pack does not cover",
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            IconShape.entries.forEach { shape ->
                val bitmap = rememberDrawableBitmap(key = shape, sizePx = previewPx) {
                    val raw = packageManager.getApplicationIcon(context.packageName)
                    shaper.shape(raw, shape, previewPx)
                }
                val isSelected = shape == selected
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                            shape = RoundedCornerShape(16.dp),
                        )
                        .clickable { graph.appScope.launch { graph.icons.setShape(shape) } }
                        .padding(vertical = 12.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    IconTile(bitmap = bitmap, size = 56.dp, contentDescription = IconShapes.displayName(shape))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = IconShapes.displayName(shape),
                        style = MaterialTheme.typography.labelMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Text(
            text = IconShapes.description(selected),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

// ---------------------------------------------------------------------------------------------
// Per-app override
// ---------------------------------------------------------------------------------------------

/**
 * Per-app icon override for [app] (home or drawer long-press > Edit icon): the current icon with a
 * Reset button, a chip per installed pack to browse, a search field and a grid of that pack's
 * drawables (`drawable.xml`, or the appfilter values). Entries the pack lists for this app come first
 * as "Suggested". Tapping a tile stores the override through `graph.icons.override` and closes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppIconOverrideSheet(app: AppInfo, onClose: () -> Unit) {
    val graph = rememberIconGraph()
    val context = LocalContext.current
    val settings = rememberLauncherSettings(graph)
    val packs by graph.icons.packs.collectAsState(initial = emptyList())
    val loader = remember(context) { IconPackLoader.get(context) }
    val resolver = graph.icons as? FoldOsIconResolver
    val fallbackGeneration = remember { MutableStateFlow(0) }
    val generationFlow: StateFlow<Int> = resolver?.generation ?: fallbackGeneration
    val generation by generationFlow.collectAsState()

    var browsePack by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    val activePack: String? = browsePack ?: settings?.iconPack ?: packs.firstOrNull()?.packageName

    val currentIconPx = with(LocalDensity.current) { 56.dp.roundToPx() }
    val currentBitmap = rememberDrawableBitmap(key = "${app.key}@$generation", sizePx = currentIconPx) {
        graph.icons.icon(app)
    }

    val packData by produceState<IconPackData?>(initialValue = null, key1 = activePack) {
        value = if (activePack == null) null else withContext(Dispatchers.IO) { runCatching { loader.load(activePack) }.getOrNull() }
    }
    val suggested: List<String> = remember(packData, app.key) {
        val data = packData ?: return@remember emptyList<String>()
        val names = LinkedHashSet<String>()
        data.components["${app.packageName}/${app.activityName}"]?.let { names.add(it) }
        val prefix = app.packageName + "/"
        data.components.forEach { (component, drawable) -> if (component.startsWith(prefix)) names.add(drawable) }
        data.byPackage[app.packageName]?.let { names.add(it) }
        names.toList()
    }
    val filtered: List<String> = remember(packData, query) {
        val all = packData?.drawables ?: emptyList()
        val q = query.trim()
        if (q.isEmpty()) all else all.filter { it.contains(q, ignoreCase = true) }
    }

    fun apply(packageName: String?, drawableName: String?) {
        graph.appScope.launch { graph.icons.override(app.key, packageName, drawableName) }
        onClose()
    }

    ModalBottomSheet(onDismissRequest = onClose) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconTile(bitmap = currentBitmap, size = 56.dp, contentDescription = app.label)
            Spacer(modifier = Modifier.size(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = app.label, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = "Choose an icon from a pack",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = { apply(null, null) }) { Text(text = "Reset") }
        }

        if (packs.isEmpty()) {
            Text(
                text = "No icon packs installed. Install a pack to pick a different icon for ${app.label}.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
        } else {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                packs.forEach { pack ->
                    FilterChip(
                        selected = pack.packageName == activePack,
                        onClick = { browsePack = pack.packageName },
                        label = { Text(text = pack.label) },
                    )
                }
            }
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text(text = "Search icons") },
                leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
            val pack = activePack
            if (pack != null) {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 64.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 120.dp, max = 440.dp)
                        .padding(horizontal = 12.dp),
                ) {
                    if (suggested.isNotEmpty() && query.isBlank()) {
                        item(key = "suggested-header", span = { GridItemSpan(maxLineSpan) }) {
                            GridSectionLabel(text = "Suggested for ${app.label}")
                        }
                        items(items = suggested, key = { "s:$it" }) { name ->
                            PackDrawableTile(loader = loader, packageName = pack, drawableName = name) {
                                apply(pack, name)
                            }
                        }
                        item(key = "all-header", span = { GridItemSpan(maxLineSpan) }) {
                            GridSectionLabel(text = "All icons")
                        }
                    }
                    if (filtered.isEmpty()) {
                        item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = if (query.isBlank()) "This pack lists no icons" else "No icons match \"$query\"",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(12.dp),
                            )
                        }
                    }
                    items(items = filtered, key = { "d:$it" }) { name ->
                        PackDrawableTile(loader = loader, packageName = pack, drawableName = name) {
                            apply(pack, name)
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

@Composable
private fun GridSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 6.dp, end = 6.dp, top = 12.dp, bottom = 4.dp),
    )
}

@Composable
private fun PackDrawableTile(
    loader: IconPackLoader,
    packageName: String,
    drawableName: String,
    onClick: () -> Unit,
) {
    val px = with(LocalDensity.current) { 48.dp.roundToPx() }
    val bitmap = rememberDrawableBitmap(key = "$packageName:$drawableName", sizePx = px) {
        loader.drawable(packageName, drawableName)
    }
    Box(
        modifier = Modifier
            .padding(6.dp)
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = drawableName, modifier = Modifier.size(48.dp))
        }
    }
}
