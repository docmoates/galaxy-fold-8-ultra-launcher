package com.foldos.launcher.items

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Brush
import androidx.compose.material.icons.rounded.Dashboard
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.VerticalSplit
import androidx.compose.material.icons.rounded.ViewModule
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomeItem
import com.foldos.launcher.core.model.ItemKind
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.icons.AppIconOverrideSheet
import com.foldos.launcher.theme.LocalFoldOsTokens
import com.foldos.launcher.widgets.WidgetHostController
import kotlinx.coroutines.launch

private const val TAG = "ItemContextMenu"

/**
 * The entries of [ItemContextMenu] (home long-press outside edit mode). [label] / [icon] are what
 * the menu row shows; [destructive] rows are tinted red.
 */
enum class ContextAction(val label: String, val icon: ImageVector) {
    /** Enter live-edit mode with this item selected (the caller handles it through `onAction`). */
    EDIT("Edit", Icons.Rounded.Edit),

    /** Remove the item from the home screen (`LayoutRepository.deleteItem`). */
    REMOVE("Remove", Icons.Rounded.RemoveCircleOutline),

    /** Open the system App info page (`Settings.ACTION_APPLICATION_DETAILS_SETTINGS`). */
    APP_INFO("App info", Icons.Rounded.Info),

    /** Ask the system to uninstall the app (`Intent.ACTION_DELETE`); not offered for system apps. */
    UNINSTALL("Uninstall", Icons.Rounded.Delete),

    /** Add the package to `LauncherSettings.hiddenApps`. */
    HIDE("Hide", Icons.Rounded.VisibilityOff),

    /** Open [PairEditorSheet] with this app as App A; the pair replaces the app's tile. */
    ADD_TO_PAIR("Add to pair", Icons.Rounded.VerticalSplit),

    /** Open `icons.AppIconOverrideSheet` for this app. */
    ICON_OVERRIDE("Icon override", Icons.Rounded.Brush),

    /** Widgets: same as [REMOVE] (the menu tries [REMOVE] when `onAction` does not take this one). */
    REMOVE_WIDGET("Remove widget", Icons.Rounded.RemoveCircleOutline),

    /**
     * Widgets: enter live-edit mode with the widget selected so its corner handles can resize it
     * (the menu tries [EDIT] when `onAction` does not take this one).
     */
    RESIZE("Resize", Icons.Rounded.AspectRatio),

    /** Widgets: enter live-edit mode (the menu tries [EDIT] when `onAction` does not take this one). */
    EDIT_HOME("Edit home", Icons.Rounded.Edit),

    /** Round 2.0: put this app / folder / pair / widget inside a card on its page (the caller picks the card). */
    MOVE_TO_CARD("Move to card", Icons.Rounded.Dashboard),

    /** Round 2.0: take this item out of its card back onto the page (the caller picks the cell). */
    TAKE_OUT("Take out of card", Icons.Rounded.OpenInNew),

    /** Round 2.0, cards: rename (an empty name removes the title). */
    RENAME_CARD("Rename card", Icons.Rounded.Edit),

    /** Round 2.0, cards: toggle the rounded surface behind the contents. */
    CARD_SURFACE("Card background", Icons.Rounded.Layers),

    /** Round 2.0, cards: move the contents back onto the page and remove the empty card (the caller places them). */
    DISSOLVE_CARD("Move contents to page", Icons.Rounded.ViewModule);

    val destructive: Boolean get() = this == UNINSTALL

    /** The older action that means the same to a caller that predates the widget entries, or null. */
    internal val legacyAlias: ContextAction?
        get() = when (this) {
            REMOVE_WIDGET -> REMOVE
            RESIZE, EDIT_HOME -> EDIT
            else -> null
        }

    companion object {
        /**
         * The actions a long press offers for [item]: everything for an app ("Add to pair" only
         * while `FEATURE_PAIRS` is on), "Remove widget" / "Resize" / "Edit home" for widgets, Edit /
         * Remove for folders and pairs. The menu itself still drops entries that do not apply (no
         * package, system app for Uninstall).
         */
        fun defaultsFor(item: HomeItem, settings: LauncherSettings): List<ContextAction> = when (item.kind) {
            ItemKind.APP -> buildList {
                add(EDIT)
                add(REMOVE)
                add(if (item.inFolder) TAKE_OUT else MOVE_TO_CARD)
                add(APP_INFO)
                add(UNINSTALL)
                add(HIDE)
                if (settings.feature(LauncherSettings.FEATURE_PAIRS)) add(ADD_TO_PAIR)
                add(ICON_OVERRIDE)
            }
            ItemKind.WIDGET -> buildList {
                add(REMOVE_WIDGET)
                if (!item.inFolder) add(RESIZE)
                add(if (item.inFolder) TAKE_OUT else MOVE_TO_CARD)
                add(EDIT_HOME)
            }
            ItemKind.FOLDER, ItemKind.PAIR -> listOf(EDIT, REMOVE, if (item.inFolder) TAKE_OUT else MOVE_TO_CARD)
            ItemKind.CARD -> listOf(RESIZE, RENAME_CARD, CARD_SURFACE, DISSOLVE_CARD, REMOVE, EDIT_HOME)
        }
    }
}

/** Which full-screen sheet the menu has handed over to. */
private enum class MenuSheet { NONE, ICON_OVERRIDE, PAIR, RENAME }

/**
 * Long-press menu for a home item when not in edit mode (PLAN.md 5.2 "per-element context menu"):
 * a 280dp card next to the tile (compose it inside the tile so the `Popup` anchors there) with the
 * item's icon and name on top and one row per action in [actions], filtered to the ones that apply.
 *
 * Every action has a default implementation through [LocalAppGraph] and standard intents, except
 * [ContextAction.EDIT] (and its widget twins [ContextAction.RESIZE] / [ContextAction.EDIT_HOME]),
 * which need the launcher's edit mode: pass [onAction] and return true for the actions you handle
 * yourself (the menu then closes), false to get the default. For [ContextAction.REMOVE_WIDGET],
 * [ContextAction.RESIZE] and [ContextAction.EDIT_HOME] the menu calls [onAction] with the action
 * itself first and, when that returns false, once more with its older alias (REMOVE / EDIT), so a
 * handler that only knows REMOVE and EDIT keeps working:
 *
 * - REMOVE / REMOVE_WIDGET: `layouts.deleteItem(item.id)` on `appScope`; an app widget's host id is
 *   released first through `WidgetHostController`.
 * - APP_INFO / UNINSTALL: `Settings.ACTION_APPLICATION_DETAILS_SETTINGS` / `Intent.ACTION_DELETE`
 *   for the item's package (Uninstall is hidden for system apps).
 * - HIDE: adds the package to `LauncherSettings.hiddenApps`.
 * - ADD_TO_PAIR: shows [PairEditorSheet] with this app as App A; on create, the APP item is deleted
 *   and `layouts.createPair(item.pageId, item.col, item.row, a, b)` takes its cell.
 * - ICON_OVERRIDE: shows `icons.AppIconOverrideSheet` for the app.
 *
 * The sheets call [onDismiss] when they close, so the caller only needs to drop the menu then.
 */
@Composable
fun ItemContextMenu(
    item: HomeItem,
    actions: List<ContextAction>,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    onAction: (ContextAction) -> Boolean = { false },
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val tokens = LocalFoldOsTokens.current
    val density = LocalDensity.current
    val allApps by graph.apps.allApps.collectAsStateWithLifecycle(initialValue = emptyList())
    val visibleApps by graph.apps.apps.collectAsStateWithLifecycle(initialValue = emptyList())
    val appsByKey = remember(allApps) { allApps.associateBy { it.key } }
    val packageName = item.packageName

    val app: AppInfo? = remember(item.packageName, item.activityName, appsByKey) {
        appsByKey.firstAppOf(item) ?: packageName?.let { fallbackApp(context, it, item.activityName) }
    }
    val secondApp: AppInfo? = remember(item.packageName2, item.activityName2, appsByKey) {
        if (item.kind == ItemKind.PAIR) appsByKey.secondAppOf(item) else null
    }
    val isSystemApp = remember(packageName) { packageName != null && isSystemPackage(context, packageName) }

    val shown: List<ContextAction> = remember(actions, item.kind, packageName, isSystemApp, app) {
        actions.distinct().filter { action ->
            when (action) {
                ContextAction.EDIT, ContextAction.REMOVE -> true
                ContextAction.REMOVE_WIDGET -> item.kind == ItemKind.WIDGET
                ContextAction.RESIZE, ContextAction.EDIT_HOME -> item.kind == ItemKind.WIDGET || item.kind == ItemKind.CARD
                ContextAction.APP_INFO, ContextAction.HIDE -> item.kind == ItemKind.APP && packageName != null
                ContextAction.UNINSTALL -> item.kind == ItemKind.APP && packageName != null && !isSystemApp
                ContextAction.ADD_TO_PAIR, ContextAction.ICON_OVERRIDE -> item.kind == ItemKind.APP && app != null
                ContextAction.MOVE_TO_CARD -> item.id > 0 && item.kind != ItemKind.CARD && !item.inFolder
                ContextAction.TAKE_OUT -> item.id > 0 && item.kind != ItemKind.CARD && item.inFolder
                ContextAction.RENAME_CARD, ContextAction.CARD_SURFACE, ContextAction.DISSOLVE_CARD -> item.kind == ItemKind.CARD && item.id > 0
            }
        }
    }

    var sheet by remember { mutableStateOf(MenuSheet.NONE) }

    fun perform(action: ContextAction) {
        // A caller that only knows EDIT / REMOVE still handles the widget entries through their alias.
        val alias = action.legacyAlias
        if (onAction(action) || (alias != null && onAction(alias))) {
            onDismiss()
            return
        }
        when (action) {
            ContextAction.EDIT, ContextAction.EDIT_HOME, ContextAction.RESIZE -> onDismiss()
            ContextAction.REMOVE, ContextAction.REMOVE_WIDGET -> {
                if (item.id > 0) {
                    // A hosted widget gives its host id back, otherwise the host keeps it forever.
                    item.appWidgetId?.let { id ->
                        runCatching { WidgetHostController.from(context, graph).deleteId(id) }
                    }
                    graph.appScope.launch { graph.layouts.deleteItem(item.id) }
                }
                onDismiss()
            }
            ContextAction.APP_INFO -> {
                if (packageName != null) openAppInfo(context, packageName)
                onDismiss()
            }
            ContextAction.UNINSTALL -> {
                if (packageName != null) requestUninstall(context, packageName)
                onDismiss()
            }
            ContextAction.HIDE -> {
                if (packageName != null) {
                    graph.appScope.launch {
                        graph.settings.update { it.copy(hiddenApps = it.hiddenApps + packageName) }
                    }
                }
                onDismiss()
            }
            ContextAction.ADD_TO_PAIR -> if (app != null) sheet = MenuSheet.PAIR else onDismiss()
            ContextAction.ICON_OVERRIDE -> if (app != null) sheet = MenuSheet.ICON_OVERRIDE else onDismiss()
            ContextAction.RENAME_CARD -> sheet = MenuSheet.RENAME
            ContextAction.CARD_SURFACE -> {
                if (item.isCard && item.id > 0) {
                    graph.appScope.launch { graph.layouts.upsertItem(item.copy(cardClear = !item.cardClear)) }
                }
                onDismiss()
            }
            // Placing the contents needs the page grid, which only the home knows: handled through onAction.
            ContextAction.MOVE_TO_CARD, ContextAction.TAKE_OUT, ContextAction.DISSOLVE_CARD -> onDismiss()
        }
    }

    when (sheet) {
        MenuSheet.RENAME -> CardRenameDialog(
            initial = item.title.orEmpty(),
            onRename = { title ->
                graph.appScope.launch {
                    graph.layouts.upsertItem(item.copy(title = title.trim().takeIf { it.isNotEmpty() }))
                }
            },
            onDismiss = onDismiss,
        )
        MenuSheet.ICON_OVERRIDE -> {
            val target = app
            if (target != null) AppIconOverrideSheet(app = target, onClose = onDismiss)
        }
        MenuSheet.PAIR -> PairEditorSheet(
            apps = visibleApps,
            initial = app,
            onCreate = { a, b ->
                graph.appScope.launch {
                    if (item.kind == ItemKind.APP && item.id > 0) graph.layouts.deleteItem(item.id)
                    graph.layouts.createPair(item.pageId, item.col, item.row, a, b)
                }
            },
            onDismiss = onDismiss,
        )
        MenuSheet.NONE -> {
            val positionProvider = remember(density) {
                NearAnchorPositionProvider(
                    gapPx = with(density) { 10.dp.roundToPx() },
                    marginPx = with(density) { 16.dp.roundToPx() },
                )
            }
            val shape = RoundedCornerShape(tokens.radius)
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = onDismiss,
                properties = PopupProperties(focusable = true),
            ) {
                Column(
                    modifier = modifier
                        .width(280.dp)
                        .shadow(16.dp, shape)
                        .clip(shape)
                        .background(tokens.drawerScrim)
                        .border(1.dp, tokens.surfaceBorder, shape)
                        .padding(vertical = 6.dp),
                ) {
                    MenuHeader(item, app, secondApp)
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 4.dp)
                            .height(1.dp)
                            .background(tokens.surfaceBorder.takeIf { it != Color.Transparent } ?: tokens.onSurface.copy(alpha = 0.12f)),
                    )
                    shown.forEach { action -> MenuRow(action = action, onClick = { perform(action) }) }
                }
            }
        }
    }
}

/** Rename dialog of a card; an empty name removes the title. */
@Composable
private fun CardRenameDialog(initial: String, onRename: (String) -> Unit, onDismiss: () -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(text = "Card title") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                placeholder = { Text(text = "No title") },
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onRename(text)
                    onDismiss()
                },
            ) { Text(text = "Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(text = "Cancel") } },
    )
}

/** Icon (app / folder / pair / widget / card), the item's name and its kind. */
@Composable
private fun MenuHeader(item: HomeItem, app: AppInfo?, secondApp: AppInfo?) {
    val tokens = LocalFoldOsTokens.current
    val title = when (item.kind) {
        ItemKind.APP -> app?.label ?: item.title ?: item.packageName ?: "App"
        ItemKind.FOLDER -> item.title ?: Folders.DEFAULT_TITLE
        ItemKind.PAIR -> pairTitle(item, app, secondApp)
        ItemKind.WIDGET -> item.title ?: "Widget"
        ItemKind.CARD -> item.title ?: if (item.isWidgetsCard) "Widget card" else "App card"
    }
    val kindName = when (item.kind) {
        ItemKind.APP -> "App"
        ItemKind.FOLDER -> "Folder"
        ItemKind.PAIR -> "App pair"
        ItemKind.WIDGET -> "Widget"
        ItemKind.CARD -> if (item.isWidgetsCard) "Widget card" else if (item.isListCard) "App card (list)" else "App card"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when {
            item.kind == ItemKind.PAIR -> PairIcon(pair = item, a = app, b = secondApp, sizeDp = 36)
            app != null -> ItemAppIcon(app = app, size = 36.dp)
            else -> {
                val shape = itemCornerShape(36.dp, tokens)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(shape)
                        .background(tokens.surface)
                        .border(1.dp, tokens.surfaceBorder, shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = when (item.kind) {
                            ItemKind.FOLDER -> Icons.Rounded.Folder
                            ItemKind.CARD -> Icons.Rounded.Dashboard
                            else -> Icons.Rounded.Widgets
                        },
                        contentDescription = null,
                        tint = tokens.onSurface.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = tokens.onSurface,
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = kindName,
                color = tokens.onSurface.copy(alpha = 0.65f),
                fontSize = 11.5.sp,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MenuRow(action: ContextAction, onClick: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val tint = if (action.destructive) Color(0xFFEF5350) else tokens.onSurface
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            // 48dp minimum touch target.
            .heightIn(min = 48.dp)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = action.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = action.label,
            color = tint,
            fontSize = 14.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/* ------------------------------------------------------------------------------------------ */
/* System helpers                                                                              */
/* ------------------------------------------------------------------------------------------ */

private fun isSystemPackage(context: Context, packageName: String): Boolean = try {
    val info = context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0L))
    (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0
} catch (e: Exception) {
    false
}

/** Opens Settings > Apps > [packageName]. */
private fun openAppInfo(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.w(TAG, "Could not open app info for $packageName", e)
    }
}

/** Shows the system uninstall confirmation for [packageName]. */
private fun requestUninstall(context: Context, packageName: String) {
    val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: Exception) {
        Log.w(TAG, "Could not start uninstall for $packageName", e)
    }
}
