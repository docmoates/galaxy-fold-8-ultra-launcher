package com.foldos.launcher.gestures

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.home.rememberHomeAppIconPainter

/**
 * "Open an app" target picker: a searchable list of [apps] with their icons. Works inside Settings
 * (no `FoldOsTheme` needed: icons come from `rememberHomeAppIconPainter`, which only needs the
 * icon cache). [selectedKey] marks the current target (`AppInfo.key`).
 */
@Composable
fun GestureAppPickerDialog(
    apps: List<AppInfo>,
    onPick: (AppInfo) -> Unit,
    onDismiss: () -> Unit,
    title: String = "Open an app",
    selectedKey: String? = null,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val shown = remember(apps, query) {
        val trimmed = query.trim()
        val source = apps.sortedBy { it.label.lowercase() }
        if (trimmed.isEmpty()) {
            source
        } else {
            source.filter {
                it.label.contains(trimmed, ignoreCase = true) || it.packageName.contains(trimmed, ignoreCase = true)
            }
        }
    }
    val iconPx = with(LocalDensity.current) { 40.dp.roundToPx() }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } },
        title = { Text(text = title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    placeholder = { Text(text = "Search apps") },
                    leadingIcon = { Icon(imageVector = Icons.Filled.Search, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (shown.isEmpty()) {
                    Text(
                        text = if (apps.isEmpty()) "Loading apps..." else "No app matches \"$query\"",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                } else {
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                        items(items = shown, key = { it.key }) { app ->
                            val selected = app.key == selectedKey
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(app) },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                headlineContent = {
                                    Text(text = app.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    Text(text = app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                leadingContent = { GestureAppIcon(app = app, sizePx = iconPx) },
                                trailingContent = if (selected) {
                                    { Icon(imageVector = Icons.Filled.Check, contentDescription = "Selected") }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        },
    )
}

/** A 40 dp app icon rasterised through the launcher's icon cache; a surface tile while loading. */
@Composable
private fun GestureAppIcon(app: AppInfo, sizePx: Int) {
    val painter = rememberHomeAppIconPainter(app, sizePx)
    val shape = RoundedCornerShape(percent = 24)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(if (painter == null) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent),
    ) {
        if (painter != null) {
            Image(painter = painter, contentDescription = null, modifier = Modifier.size(40.dp))
        }
    }
}
