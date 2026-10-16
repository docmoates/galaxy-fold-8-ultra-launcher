package com.foldos.launcher.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.GestureAction
import com.foldos.launcher.core.model.GestureKind
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.gestures.GestureAppPickerDialog
import com.foldos.launcher.gestures.GestureLabels

object GesturesRows {
    val gestures = SettingsRowInfo("Home gestures", "Swipes, pinch, double-tap, hinge actions")

    /** One row per gesture; the row text doubles as its settings-search entry. */
    val byKind: Map<GestureKind, SettingsRowInfo> = GestureKind.entries.associateWith { kind ->
        SettingsRowInfo(
            title = GestureLabels.kindTitle(kind),
            subtitle = GestureLabels.kindDescription(kind),
            advanced = kind in GestureLabels.advancedKinds,
        )
    }

    val page = SettingsPageInfo(
        route = SettingsRoutes.GESTURES,
        title = "Gestures",
        subtitle = "Every gesture mapped to an action",
        icon = Icons.Outlined.Gesture,
        rows = listOf(gestures) +
            (GestureLabels.touchKinds + GestureLabels.twoFingerKinds + GestureLabels.hingeKinds).map { byKind.getValue(it) },
    )
}

/**
 * Settings > Gestures: the feature switch, then one row per [GestureKind] with a dropdown of
 * action types. Choosing "Open an app" opens [GestureAppPickerDialog]; every change is written with
 * `LauncherSettings.withGesture`. The app list comes from the app graph's `AppsRepository`
 * (read from the Application, because the Settings activity provides no `LocalAppGraph`).
 */
@Composable
fun GesturesPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val on = settings.feature(LauncherSettings.FEATURE_GESTURES)
    val context = LocalContext.current
    val graph = remember(context) { FoldOsApplication.from(context).graph }
    val apps by graph.apps.apps.collectAsState(initial = emptyList())
    var pickingFor by rememberSaveable { mutableStateOf<String?>(null) }

    SettingsPageScaffold(title = GesturesRows.page.title, onBack = onBack) {
        ToggleRow(
            info = GesturesRows.gestures,
            checked = on,
            onCheckedChange = { enabled -> update { it.withFeature(LauncherSettings.FEATURE_GESTURES, enabled) } },
        )
        GestureSection(
            title = "Touch",
            kinds = GestureLabels.touchKinds,
            settings = settings,
            apps = apps,
            visible = on,
            onSelect = { kind, action -> update { it.withGesture(kind, action) } },
            onPickApp = { kind -> pickingFor = kind.name },
        )
        GestureSection(
            title = "Two fingers",
            kinds = GestureLabels.twoFingerKinds,
            settings = settings,
            apps = apps,
            visible = on,
            onSelect = { kind, action -> update { it.withGesture(kind, action) } },
            onPickApp = { kind -> pickingFor = kind.name },
        )
        GestureSection(
            title = "Hinge and keys",
            kinds = GestureLabels.hingeKinds,
            settings = settings,
            apps = apps,
            visible = on,
            onSelect = { kind, action -> update { it.withGesture(kind, action) } },
            onPickApp = { kind -> pickingFor = kind.name },
        )
    }

    val pickingKind = pickingFor?.let { name -> GestureKind.entries.firstOrNull { it.name == name } }
    if (pickingKind != null) {
        val current = settings.gesture(pickingKind)
        GestureAppPickerDialog(
            apps = apps,
            title = "${GestureLabels.kindTitle(pickingKind)}: open an app",
            selectedKey = current.target.takeIf { current.type == GestureAction.TYPE_APP },
            onPick = { app ->
                update { it.withGesture(pickingKind, GestureAction.app(app.key)) }
                pickingFor = null
            },
            onDismiss = { pickingFor = null },
        )
    }
}

/** A section header plus the rows of [kinds]; the header hides with its rows when none shows. */
@Composable
private fun GestureSection(
    title: String,
    kinds: List<GestureKind>,
    settings: LauncherSettings,
    apps: List<AppInfo>,
    visible: Boolean,
    onSelect: (GestureKind, GestureAction) -> Unit,
    onPickApp: (GestureKind) -> Unit,
) {
    if (!visible) return
    val filter = LocalSettingsRowFilter.current
    val shown = kinds.filter { filter.shows(GesturesRows.byKind.getValue(it)) }
    if (shown.isEmpty()) return
    SectionHeader(text = title)
    shown.forEach { kind ->
        GestureActionRow(
            info = GesturesRows.byKind.getValue(kind),
            action = settings.gesture(kind),
            apps = apps,
            onSelectType = { type ->
                if (type == GestureAction.TYPE_APP) onPickApp(kind) else onSelect(kind, GestureAction(type))
            },
        )
    }
}

/**
 * One gesture row: title, description and the bound action as a dropdown. Picking "Open an app"
 * hands over to the caller (app picker); the current app shows as "Open <name>".
 */
@Composable
private fun GestureActionRow(
    info: SettingsRowInfo,
    action: GestureAction,
    apps: List<AppInfo>,
    onSelectType: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = GestureLabels.actionLabel(action, apps)
    val subtitle = info.subtitle
    val supporting: (@Composable () -> Unit)? = if (subtitle == null) null else { { Text(text = subtitle) } }
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = true },
        headlineContent = { Text(text = info.title) },
        supportingContent = supporting,
        trailingContent = {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 160.dp),
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    GestureLabels.actionOptions.forEach { (type, title) ->
                        val selected = type == action.type
                        DropdownMenuItem(
                            text = { Text(text = title) },
                            onClick = {
                                expanded = false
                                onSelectType(type)
                            },
                            trailingIcon = if (selected) {
                                { Icon(imageVector = Icons.Filled.Check, contentDescription = null) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        },
    )
}
