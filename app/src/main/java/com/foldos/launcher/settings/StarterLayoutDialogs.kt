package com.foldos.launcher.settings

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.foldos.launcher.backup.StyleRestoreGate
import com.foldos.launcher.core.AppGraph
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.core.model.ScreenMode
import com.foldos.launcher.fold.effectiveScreen
import com.foldos.launcher.fold.rememberFoldState
import com.foldos.launcher.home.StarterLayouts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Round 2.0: Settings applies a starter layout ([StarterLayouts]) to a screen. "Replace the first
 * page" clears that screen's first page and seeds it; "Add as a new page" appends a seeded page.
 * The chosen starter is remembered in `styleCover` / `styleInner` without waking the launcher's
 * starter watcher (which would seed the first page a second time).
 */
internal object StarterApply {

    /** Seeds [starter] on [screen] through the graph and records it in the settings. */
    suspend fun apply(graph: AppGraph, screen: ScreenKind, starter: HomeStyle, replaceFirst: Boolean) {
        val current = graph.settings.settings.first()
        val apps = graph.apps.apps.first()
        val pages = graph.layouts.pages(screen).first().sortedBy { it.index }
        val replaceId = if (replaceFirst) pages.firstOrNull()?.id else null
        StarterLayouts.apply(
            layouts = graph.layouts,
            screen = screen,
            starter = starter,
            apps = apps,
            cols = current.effectiveColsFor(screen),
            rows = StarterLayouts.DEFAULT_ROWS,
            replacePageId = replaceId,
        )
        val next = when (screen) {
            ScreenKind.COVER -> current.copy(styleCover = starter)
            ScreenKind.INNER, ScreenKind.FLEX -> current.copy(styleInner = starter)
        }
        if (next != current) {
            StyleRestoreGate.arm(current, next)
            graph.settings.update { s ->
                when (screen) {
                    ScreenKind.COVER -> s.copy(styleCover = starter)
                    ScreenKind.INNER, ScreenKind.FLEX -> s.copy(styleInner = starter)
                }
            }
        }
    }
}

/** The screen Settings is showing on, as the launcher would read it (`effectiveScreen`): COVER or INNER. */
@Composable
internal fun rememberSettingsScreen(settings: LauncherSettings): ScreenKind {
    val fold = rememberFoldState()
    return effectiveScreen(settings, fold)
}

/** Runs [StarterApply.apply] on the app scope with a toast when it is done. */
@Composable
internal fun rememberStarterApplier(): (ScreenKind, HomeStyle, Boolean) -> Unit {
    val context = LocalContext.current
    val graph = remember(context) { FoldOsApplication.from(context).graph }
    return remember(graph) {
        { screen: ScreenKind, starter: HomeStyle, replaceFirst: Boolean ->
            graph.appScope.launch {
                val result = runCatching { StarterApply.apply(graph, screen, starter, replaceFirst) }
                withContext(Dispatchers.Main) {
                    val text = if (result.isSuccess) {
                        "${StarterLayouts.name(starter)} applied to the ${screenWord(screen)} screen"
                    } else {
                        "Could not apply the starter layout"
                    }
                    Toast.makeText(context, text, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}

internal fun screenWord(screen: ScreenKind): String = if (screen == ScreenKind.COVER) "cover" else "inner"

/**
 * "Apply to the current page / Add as a new page" for a tapped starter. Settings has no pager, so
 * "current page" is the first page of [screen]; the dialog says so.
 */
@Composable
internal fun StarterApplyDialog(
    starter: HomeStyle,
    screen: ScreenKind,
    onReplaceFirst: () -> Unit,
    onAddPage: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = StarterLayouts.name(starter)) },
        text = {
            Column {
                Text(text = StarterLayouts.description(starter), style = MaterialTheme.typography.bodyMedium)
                Text(
                    text = "Replace what is on the first ${screenWord(screen)} page, or add it as a new page? " +
                        "Everything it adds can be moved, resized and removed afterwards.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onReplaceFirst(); onDismiss() }) { Text(text = "Replace first page") }
        },
        dismissButton = {
            TextButton(onClick = { onAddPage(); onDismiss() }) { Text(text = "Add as new page") }
        },
    )
}

/** Picks one of the four starters for [screen] (More settings > Home > Starter layout rows). */
@Composable
internal fun StarterChooserDialog(
    screen: ScreenKind,
    onPick: (HomeStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Starter layout for the ${screenWord(screen)} screen") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                StarterLayouts.all.forEach { (starter, name) ->
                    ListItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(starter) },
                        headlineContent = { Text(text = name) },
                        supportingContent = { Text(text = StarterLayouts.description(starter)) },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } },
    )
}

/** True when the inner screen has its own layout (Settings > Screens is not "Same"). */
internal fun LauncherSettings.innerHasOwnLayout(): Boolean = screenMode != ScreenMode.SAME
