package com.foldos.launcher.apppicker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.LauncherSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ids of the groups shown as folder chips at the top of the app pop-up. */
object PickerGroupIds {
    const val ALL: String = "all"
    const val HIDDEN: String = "auto:hidden"

    fun auto(category: AppCategory): String = "auto:${category.name.lowercase()}"

    fun user(name: String): String = "user:$name"
}

/** One group folder: "All", an automatic group (Work, Media, ...), "Hidden", or a user group. */
@Immutable
data class PickerGroup(
    val id: String,
    val title: String,
    val kind: Kind,
    /** The apps in the group, sorted by label (hidden apps for [Kind.HIDDEN]). */
    val apps: List<AppInfo>,
) {
    enum class Kind { ALL, AUTO, HIDDEN, USER }

    /** The user group's name (the key in [GroupStore]); only meaningful for [Kind.USER]. */
    val userName: String get() = title
}

/** What the pop-up shows right now: the group chips, the selected group and the filtered tiles. */
@Immutable
class PickerModel(
    val groups: List<PickerGroup>,
    val selected: PickerGroup,
    /** The selected group's apps, or every app while a search query is typed. */
    val shown: List<AppInfo>,
    val userGroupNames: List<String>,
    /** Names of the user groups that contain each app key. */
    val membership: Map<String, Set<String>>,
)

/** A pending "create group" dialog; [andAdd] is added to the new group when it is created. */
@Immutable
class NewGroupRequest(val andAdd: AppInfo?)

/**
 * Pop-up UI state that does not come from data: the search text, the selected chip and the open
 * dialogs, plus the operations that write [GroupStore]. Create with [rememberAppPickerState].
 */
@Stable
class AppPickerState internal constructor(
    internal val store: GroupStore,
    private val scope: CoroutineScope,
) {
    var query: String by mutableStateOf("")
    var selectedId: String by mutableStateOf(PickerGroupIds.ALL)

    /** App whose long-press menu is open. */
    var actionsApp: AppInfo? by mutableStateOf(null)

    /** App for which the "Add to group" list is open. */
    var groupPickApp: AppInfo? by mutableStateOf(null)

    /** Non-null while the "New group" dialog is open. */
    var newGroup: NewGroupRequest? by mutableStateOf(null)

    /** User group whose manage dialog (rename / delete) is open. */
    var manageGroupName: String? by mutableStateOf(null)

    /** Back to the clean state (search cleared, "All" selected, dialogs closed); called when the pop-up closes. */
    fun reset() {
        query = ""
        selectedId = PickerGroupIds.ALL
        actionsApp = null
        groupPickApp = null
        newGroup = null
        manageGroupName = null
    }

    fun createGroup(name: String, andAdd: AppInfo?) {
        scope.launch {
            val created = store.createGroup(name, andAdd?.key)
            if (created != null) selectedId = PickerGroupIds.user(created)
        }
    }

    fun deleteGroup(name: String) {
        scope.launch {
            store.deleteGroup(name)
            if (selectedId == PickerGroupIds.user(name)) selectedId = PickerGroupIds.ALL
        }
    }

    fun renameGroup(oldName: String, newName: String) {
        scope.launch {
            val wasSelected = selectedId == PickerGroupIds.user(oldName)
            if (store.renameGroup(oldName, newName) && wasSelected) {
                selectedId = PickerGroupIds.user(GroupStore.cleanName(newName))
            }
        }
    }

    fun addToGroup(groupName: String, app: AppInfo) {
        scope.launch { store.addApp(groupName, app.key) }
    }

    fun removeFromGroup(groupName: String, app: AppInfo) {
        scope.launch { store.removeApp(groupName, app.key) }
    }
}

@Composable
fun rememberAppPickerState(): AppPickerState {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(context, scope) { AppPickerState(GroupStore(context), scope) }
}

/**
 * Builds the [PickerModel] for [apps] (the launcher's visible apps): categories are resolved off the
 * main thread, the user groups come from [AppPickerState.store], the Hidden group from
 * `graph.apps.allApps` (only collected while [visible]).
 */
@Composable
fun rememberPickerModel(
    state: AppPickerState,
    apps: List<AppInfo>,
    settings: LauncherSettings,
    visible: Boolean,
): PickerModel {
    val context = LocalContext.current
    val graph = LocalAppGraph.current

    val userGroups by state.store.groups.collectAsStateWithLifecycle(initialValue = emptyList())
    val categories by produceState<Map<String, AppCategory>>(emptyMap(), apps, visible) {
        // Resolved when the pop-up opens (and when the app list changes while it is open); the last
        // result stays in place while it is closed.
        if (!visible) return@produceState
        value = withContext(Dispatchers.Default) { AppCategories.classify(context.packageManager, apps) }
    }
    val everyApp by remember(graph, visible) {
        if (visible) graph.apps.allApps else flowOf(emptyList<AppInfo>())
    }.collectAsStateWithLifecycle(initialValue = emptyList())

    val sorted = remember(apps) { apps.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label }) }
    val hidden = remember(everyApp, settings.hiddenApps) {
        everyApp
            .filter { settings.isHidden(it.packageName, it.activityName) }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    val groups = remember(sorted, categories, userGroups, hidden) {
        buildList<PickerGroup> {
            add(PickerGroup(PickerGroupIds.ALL, "All", PickerGroup.Kind.ALL, sorted))
            for (category in AppCategory.entries) {
                val inCategory = sorted.filter { categories[it.key] == category }
                if (inCategory.isNotEmpty()) {
                    add(PickerGroup(PickerGroupIds.auto(category), category.title, PickerGroup.Kind.AUTO, inCategory))
                }
            }
            if (hidden.isNotEmpty()) {
                add(PickerGroup(PickerGroupIds.HIDDEN, "Hidden", PickerGroup.Kind.HIDDEN, hidden))
            }
            for (group in userGroups) {
                val keys = group.appKeys.toSet()
                add(
                    PickerGroup(
                        PickerGroupIds.user(group.name),
                        group.name,
                        PickerGroup.Kind.USER,
                        sorted.filter { it.key in keys },
                    ),
                )
            }
        }
    }
    val membership = remember(userGroups) {
        val map = HashMap<String, MutableSet<String>>()
        for (group in userGroups) {
            for (key in group.appKeys) map.getOrPut(key) { HashSet() }.add(group.name)
        }
        map
    }
    val userNames = remember(userGroups) { userGroups.map { it.name } }

    val selected = groups.firstOrNull { it.id == state.selectedId } ?: groups.first()
    val query = state.query.trim()
    val shown = remember(selected, sorted, query) {
        if (query.isEmpty()) {
            selected.apps
        } else {
            sorted.filter { it.label.contains(query, ignoreCase = true) }
        }
    }
    return remember(groups, selected, shown, userNames, membership) {
        PickerModel(groups, selected, shown, userNames, membership)
    }
}

/** Opens the system "App info" screen of [app]. Safe to call when the screen is missing. */
fun openAppInfo(context: Context, app: AppInfo) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.fromParts("package", app.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (e: RuntimeException) {
        // No settings activity to handle it: nothing else to do.
    }
}
