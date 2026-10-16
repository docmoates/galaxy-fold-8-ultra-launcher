package com.foldos.launcher.apppicker

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.theme.LocalFoldOsTokens

/**
 * The dialogs of the app pop-up, driven by [state]: the long-press menu of an app, the "Add to group"
 * list, the "New group" name dialog and the manage dialog (rename / delete) of a user group.
 *
 * @param onAddToHome null hides "Add to home screen"; called with the app, then the pop-up is dismissed.
 * @param onAddToDock null hides "Add to dock".
 * @param onDone called after an action that finishes with the pop-up (add to home / dock).
 */
@Composable
fun AppPickerDialogs(
    state: AppPickerState,
    model: PickerModel,
    onAddToHome: ((AppInfo) -> Unit)?,
    onAddToDock: ((AppInfo) -> Unit)?,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val tokens = LocalFoldOsTokens.current

    val actionsApp = state.actionsApp
    if (actionsApp != null) {
        val inUserGroup = model.selected.kind == PickerGroup.Kind.USER &&
            model.membership[actionsApp.key]?.contains(model.selected.userName) == true
        AlertDialog(
            onDismissRequest = { state.actionsApp = null },
            containerColor = tokens.drawerScrim,
            titleContentColor = tokens.onSurface,
            textContentColor = tokens.onSurface,
            title = { Text(actionsApp.label, maxLines = 1) },
            text = {
                Column {
                    if (onAddToHome != null) {
                        ActionRow(Icons.Rounded.Home, "Add to home screen") {
                            state.actionsApp = null
                            onAddToHome(actionsApp)
                            onDone()
                        }
                    }
                    if (onAddToDock != null) {
                        ActionRow(Icons.Rounded.Apps, "Add to dock") {
                            state.actionsApp = null
                            onAddToDock(actionsApp)
                            onDone()
                        }
                    }
                    ActionRow(Icons.Rounded.Folder, "Add to group…") {
                        state.actionsApp = null
                        state.groupPickApp = actionsApp
                    }
                    if (inUserGroup) {
                        ActionRow(Icons.Rounded.RemoveCircleOutline, "Remove from ${model.selected.title}") {
                            state.actionsApp = null
                            state.removeFromGroup(model.selected.userName, actionsApp)
                        }
                    }
                    ActionRow(Icons.Rounded.Info, "App info") {
                        state.actionsApp = null
                        openAppInfo(context, actionsApp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { state.actionsApp = null }) { Text("Close", color = tokens.accent) }
            },
        )
    }

    val pickApp = state.groupPickApp
    if (pickApp != null) {
        val memberOf = model.membership[pickApp.key].orEmpty()
        AlertDialog(
            onDismissRequest = { state.groupPickApp = null },
            containerColor = tokens.drawerScrim,
            titleContentColor = tokens.onSurface,
            textContentColor = tokens.onSurface,
            title = { Text("Add ${pickApp.label} to a group", maxLines = 2) },
            text = {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    model.userGroupNames.forEach { name ->
                        val isMember = name in memberOf
                        ActionRow(
                            icon = if (isMember) Icons.Rounded.CheckBox else Icons.Rounded.CheckBoxOutlineBlank,
                            label = name,
                        ) {
                            if (isMember) state.removeFromGroup(name, pickApp) else state.addToGroup(name, pickApp)
                        }
                    }
                    ActionRow(Icons.Rounded.Add, "New group…") {
                        state.groupPickApp = null
                        state.newGroup = NewGroupRequest(andAdd = pickApp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { state.groupPickApp = null }) { Text("Done", color = tokens.accent) }
            },
        )
    }

    val newGroup = state.newGroup
    if (newGroup != null) {
        NameDialog(
            title = "New group",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { state.newGroup = null },
            onConfirm = { name ->
                state.newGroup = null
                state.createGroup(name, newGroup.andAdd)
            },
        )
    }

    val manageName = state.manageGroupName
    if (manageName != null) {
        NameDialog(
            title = "Rename group",
            initial = manageName,
            confirmLabel = "Save",
            onDismiss = { state.manageGroupName = null },
            onConfirm = { name ->
                state.manageGroupName = null
                if (name != manageName) state.renameGroup(manageName, name)
            },
            extra = {
                ActionRow(Icons.Rounded.Delete, "Delete group (apps stay installed)") {
                    state.manageGroupName = null
                    state.deleteGroup(manageName)
                }
            },
        )
    }
}

/** One tappable row of a dialog: icon and label. */
@Composable
private fun ActionRow(icon: ImageVector, label: String, onClick: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tokens.onSurface,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            color = tokens.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
        )
    }
}

/** A one-field dialog that asks for a group name ("New group", "Rename group"). */
@Composable
private fun NameDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    extra: (@Composable () -> Unit)? = null,
) {
    val tokens = LocalFoldOsTokens.current
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    val valid = GroupStore.cleanName(text).isNotEmpty()
    LaunchedEffect(Unit) {
        runCatching { focus.requestFocus() }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = tokens.drawerScrim,
        titleContentColor = tokens.onSurface,
        textContentColor = tokens.onSurface,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = text,
                    onValueChange = { text = it.take(GroupStore.MAX_NAME_LENGTH) },
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    placeholder = { Text("Group name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (valid) onConfirm(text) }),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = tokens.surface,
                        unfocusedContainerColor = tokens.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = tokens.accent,
                        focusedTextColor = tokens.onSurface,
                        unfocusedTextColor = tokens.onSurface,
                        focusedPlaceholderColor = tokens.onSurface.copy(alpha = 0.7f),
                        unfocusedPlaceholderColor = tokens.onSurface.copy(alpha = 0.7f),
                    ),
                )
                if (extra != null) extra()
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }, enabled = valid) {
                Text(confirmLabel, color = if (valid) tokens.accent else tokens.onSurface.copy(alpha = 0.4f))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = tokens.onSurface) }
        },
    )
}
