package com.foldos.launcher.profiles

import android.text.format.DateFormat
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.Profile
import com.foldos.launcher.home.rememberHomeAppIconPainter
import kotlinx.coroutines.launch

/**
 * Full-screen editor for one [Profile]: name, time window (start / end pickers, days), the Do Not
 * Disturb rule, the hidden-apps multi-select and "Update from current settings" (re-captures the
 * snapshot). Save writes through `ProfileRepository.save` (the active profile is re-applied by the
 * repository); Delete removes it. Drawn as a dialog window so it works over the launcher as well as
 * inside Settings.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileEditor(profile: Profile, onDismiss: () -> Unit) {
    val graph = rememberProfileGraph()
    val context = LocalContext.current
    val settings by graph.settings.settings.collectAsState(initial = null)

    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var scheduled by rememberSaveable(profile.id) { mutableStateOf(profile.scheduled) }
    var startMinute by rememberSaveable(profile.id) { mutableStateOf(profile.startMinute ?: 9 * 60) }
    var endMinute by rememberSaveable(profile.id) { mutableStateOf(profile.endMinute ?: 17 * 60) }
    var days by remember(profile.id) { mutableStateOf(profile.days) }
    var onDnd by rememberSaveable(profile.id) { mutableStateOf(profile.onDnd) }
    var hidden by remember(profile.id) { mutableStateOf(profile.hiddenApps) }
    var settingsJson by remember(profile.id) { mutableStateOf(profile.settingsJson) }

    var pickingStart by rememberSaveable { mutableStateOf(false) }
    var pickingEnd by rememberSaveable { mutableStateOf(false) }
    var pickingHidden by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    val isActive = settings?.activeProfile == profile.id

    fun save() {
        val updated = profile.copy(
            name = name.trim().ifEmpty { profile.name },
            settingsJson = settingsJson,
            hiddenApps = hidden,
            startMinute = if (scheduled) startMinute else null,
            endMinute = if (scheduled) endMinute else null,
            days = if (scheduled) days else emptySet(),
            onDnd = onDnd,
        )
        graph.appScope.launch { graph.profiles.save(updated) }
        onDismiss()
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(text = "Edit profile", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                            }
                        },
                        actions = {
                            TextButton(onClick = { save() }) { Text(text = "Save") }
                        },
                    )
                },
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .verticalScroll(rememberScrollState()),
                ) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(text = "Name") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    if (isActive) {
                        Text(
                            text = "This profile is active: changes you make in Settings while it is on are saved into it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                    }

                    EditorSectionLabel(text = "Switch on automatically")
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { scheduled = !scheduled },
                        leadingContent = { Icon(imageVector = Icons.Outlined.Schedule, contentDescription = null) },
                        headlineContent = { Text(text = "By time") },
                        supportingContent = {
                            Text(
                                text = if (scheduled) {
                                    "${formatMinuteOfDay(startMinute)} – ${formatMinuteOfDay(endMinute)} · ${daysSummary(days)}"
                                } else {
                                    "Pick a start and end time, and the days"
                                },
                            )
                        },
                        trailingContent = { Switch(checked = scheduled, onCheckedChange = { scheduled = it }) },
                    )
                    if (scheduled) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            TimeCard(
                                label = "Starts",
                                minute = startMinute,
                                onClick = { pickingStart = true },
                                modifier = Modifier.weight(1f),
                            )
                            TimeCard(
                                label = "Ends",
                                minute = endMinute,
                                onClick = { pickingEnd = true },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (endMinute <= startMinute) {
                            Text(
                                text = if (endMinute == startMinute) {
                                    "Start and end are the same, so the window never applies."
                                } else {
                                    "Ends before it starts: the window runs overnight into the next day."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
                        Text(
                            text = "Days",
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            PROFILE_DAY_ORDER.forEach { day ->
                                val selected = days.isEmpty() || day in days
                                FilterChip(
                                    selected = selected,
                                    onClick = {
                                        // An empty set means every day; the first tap turns it explicit.
                                        val explicit = if (days.isEmpty()) PROFILE_DAY_ORDER.toSet() else days
                                        days = if (day in explicit) explicit - day else explicit + day
                                    },
                                    label = { Text(text = dayShortName(day).take(2)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Text(
                            text = if (days.isEmpty()) "Every day" else daysSummary(days),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        )
                    }
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onDnd = !onDnd },
                        leadingContent = { Icon(imageVector = Icons.Outlined.VisibilityOff, contentDescription = null) },
                        headlineContent = { Text(text = "With Do Not Disturb") },
                        supportingContent = { Text(text = "Applies while any Do Not Disturb mode is on") },
                        trailingContent = { Switch(checked = onDnd, onCheckedChange = { onDnd = it }) },
                    )

                    EditorSectionLabel(text = "What it changes")
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { pickingHidden = true },
                        headlineContent = { Text(text = "Hidden apps") },
                        supportingContent = {
                            Text(
                                text = when (hidden.size) {
                                    0 -> "Nothing hidden"
                                    1 -> "1 app hidden from the drawer and search"
                                    else -> "${hidden.size} apps hidden from the drawer and search"
                                },
                            )
                        },
                        trailingContent = {
                            Text(
                                text = "Choose",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        },
                    )
                    ListItem(
                        modifier = Modifier.fillMaxWidth(),
                        headlineContent = { Text(text = "Saved settings") },
                        supportingContent = {
                            Text(text = "Look, home styles, dock, gestures, search and widgets, as saved in this profile")
                        },
                        trailingContent = {
                            TextButton(
                                onClick = {
                                    val current = settings
                                    if (current != null) {
                                        settingsJson = ProfileJson.encodeSettings(current)
                                        hidden = current.hiddenApps
                                        Toast.makeText(context, "Updated from the current settings", Toast.LENGTH_SHORT).show()
                                    }
                                },
                            ) { Text(text = "Update") }
                        },
                    )

                    Spacer(modifier = Modifier.height(24.dp))
                    OutlinedButton(
                        onClick = { confirmDelete = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                    ) {
                        Icon(imageVector = Icons.Outlined.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(text = "Delete profile")
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    if (pickingStart) {
        MinutePickerDialog(
            title = "Starts at",
            minute = startMinute,
            onDismiss = { pickingStart = false },
            onConfirm = { startMinute = it; pickingStart = false },
        )
    }
    if (pickingEnd) {
        MinutePickerDialog(
            title = "Ends at",
            minute = endMinute,
            onDismiss = { pickingEnd = false },
            onConfirm = { endMinute = it; pickingEnd = false },
        )
    }
    if (pickingHidden) {
        HiddenAppsPicker(
            selected = hidden,
            onDismiss = { pickingHidden = false },
            onConfirm = { hidden = it; pickingHidden = false },
        )
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(text = "Delete ${profile.name}?") },
            text = { Text(text = "Its saved settings and schedule are removed. The current look stays as it is.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        graph.appScope.launch { graph.profiles.delete(profile.id) }
                        onDismiss()
                    },
                ) { Text(text = "Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(text = "Cancel") } },
        )
    }
}

@Composable
private fun EditorSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
private fun TimeCard(label: String, minute: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = formatMinuteOfDay(minute),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
        )
    }
}

/** Material 3 time picker in a dialog; confirms with minutes after midnight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MinutePickerDialog(
    title: String,
    minute: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val context = LocalContext.current
    val state = rememberTimePickerState(
        initialHour = (minute / 60).coerceIn(0, 23),
        initialMinute = (minute % 60).coerceIn(0, 59),
        is24Hour = DateFormat.is24HourFormat(context),
    )
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(20.dp))
                TimePicker(state = state)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(text = "Cancel") }
                    TextButton(onClick = { onConfirm(state.hour * 60 + state.minute) }) { Text(text = "OK") }
                }
            }
        }
    }
}

/**
 * Full-screen multi-select of every launchable app (hidden ones included) with search. Hidden
 * entries are stored as package names, which is what `LauncherSettings.hiddenApps` expects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HiddenAppsPicker(
    selected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit,
) {
    val graph = rememberProfileGraph()
    val apps by graph.apps.allApps.collectAsState(initial = emptyList())
    var query by rememberSaveable { mutableStateOf("") }
    var picked by remember { mutableStateOf(selected) }
    val filtered: List<AppInfo> = remember(apps, query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) apps
        else apps.filter { it.label.contains(trimmed, ignoreCase = true) || it.packageName.contains(trimmed, ignoreCase = true) }
    }
    val iconPx = with(LocalDensity.current) { 40.dp.roundToPx() }

    fun isPicked(app: AppInfo): Boolean = app.packageName in picked || app.key in picked

    fun toggle(app: AppInfo) {
        picked = if (isPicked(app)) picked - app.packageName - app.key else picked + app.packageName
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(text = "Hidden apps") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(imageVector = Icons.Filled.Close, contentDescription = "Close")
                            }
                        },
                        actions = {
                            TextButton(onClick = { onConfirm(picked) }) { Text(text = "Done") }
                        },
                    )
                },
            ) { innerPadding ->
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text(text = "Search apps") },
                        leadingIcon = { Icon(imageVector = Icons.Outlined.Search, contentDescription = null) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Text(
                        text = when (picked.size) {
                            0 -> "Nothing hidden"
                            1 -> "1 app hidden"
                            else -> "${picked.size} apps hidden"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                    LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                        items(items = filtered, key = { it.key }) { app ->
                            val checked = isPicked(app)
                            ListItem(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggle(app) },
                                leadingContent = { AppIconTile(app = app, sizePx = iconPx) },
                                headlineContent = { Text(text = app.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(text = app.packageName, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                trailingContent = { Checkbox(checked = checked, onCheckedChange = { toggle(app) }) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppIconTile(app: AppInfo, sizePx: Int) {
    val painter = rememberHomeAppIconPainter(app, sizePx)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (painter == null) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center,
    ) {
        if (painter != null) {
            Image(
                painter = painter,
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                contentScale = ContentScale.Fit,
            )
        }
    }
}
