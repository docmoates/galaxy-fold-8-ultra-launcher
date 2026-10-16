package com.foldos.launcher.builtins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckBox
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.CheckBoxOutlineBlank
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max

/**
 * Local to-do list (mock-up `WG.todo`): tap a row to complete it, "Add a task" opens an inline
 * field (Done on the keyboard saves). Items live in the `foldos_todo` DataStore via [TodoStore].
 * Single row shows the first open task with a checkbox and the open count.
 */
@Composable
fun TodoWidget(spanX: Int, spanY: Int, modifier: Modifier, store: TodoStore) {
    val one = spanY == 1
    BuiltInCard(modifier, one = one) {
        val ih = maxHeight.value
        val items by store.items.collectAsStateWithLifecycle(initialValue = emptyList())
        val scope = rememberCoroutineScope()
        val tokens = LocalFoldOsTokens.current
        val open = items.filter { !it.done }
        var adding by remember { mutableStateOf(false) }
        var draft by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }

        fun submit() {
            val text = draft
            draft = ""
            adding = false
            if (text.isNotBlank()) scope.launch { store.add(text) }
        }

        LaunchedEffect(adding) {
            if (adding) runCatching { focus.requestFocus() }
        }

        if (one) {
            val first = open.firstOrNull()
            Row(
                Modifier.fillMaxSize(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (adding) {
                    TodoInput(draft, { draft = it }, ::submit, focus, Modifier.weight(1f))
                } else {
                    Icon(
                        if (first == null) Icons.Outlined.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
                        contentDescription = "Complete",
                        tint = if (first == null) tokens.accent else LocalContentColor.current,
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .clickable(enabled = first != null) {
                                first?.let { scope.launch { store.setDone(it.id, true) } }
                            },
                    )
                    Column(Modifier.weight(1f)) {
                        WidgetTitleText(first?.text ?: if (items.isEmpty()) "Add your first task" else "All done", size = 13.5.sp)
                        if (spanX >= 2) WidgetDimText("${open.size} open · ${items.size - open.size} done")
                    }
                }
                if (spanX >= 2) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Add a task",
                        tint = tokens.accent,
                        modifier = Modifier.size(22.dp).clip(CircleShape).clickable { adding = !adding },
                    )
                }
            }
        } else {
            val ch = ih - WidgetHeaderHeight.value
            val rowHeight = 27f
            val visibleRows = max(1, floor((ch + 5f) / rowHeight).toInt() - 1) // last row = "Add a task"
            Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                WidgetHeader(Icons.Filled.CheckBox, "To-do", trailingText = "${open.size} open")
                Column(Modifier.fillMaxWidth().weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    if (items.isEmpty() && !adding) {
                        WidgetDimText("Nothing here yet. Add a task below.", size = 13.sp, maxLines = 2)
                    }
                    items.take(visibleRows).forEach { item ->
                        TodoRow(
                            item = item,
                            onToggle = { scope.launch { store.setDone(item.id, !item.done) } },
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth().height(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Outlined.Add,
                        contentDescription = "Add a task",
                        tint = tokens.accent,
                        modifier = Modifier.size(20.dp).clip(CircleShape).clickable { adding = !adding },
                    )
                    if (adding) {
                        TodoInput(draft, { draft = it }, ::submit, focus, Modifier.weight(1f))
                    } else {
                        Text(
                            "Add a task",
                            fontSize = 14.sp,
                            color = tokens.accent,
                            maxLines = 1,
                            modifier = Modifier.weight(1f).clickable { adding = true },
                        )
                    }
                }
            }
        }
    }
}

/** Mock-up `.todo > div`: checkbox glyph + text; completed rows dim and strike through. */
@Composable
private fun TodoRow(item: TodoItem, onToggle: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    Row(
        Modifier.fillMaxWidth().height(22.dp).clickable(onClick = onToggle),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            if (item.done) Icons.Filled.CheckBox else Icons.Outlined.CheckBoxOutlineBlank,
            contentDescription = if (item.done) "Completed" else "Open",
            tint = if (item.done) tokens.accent else LocalContentColor.current,
            modifier = Modifier.size(20.dp),
        )
        Text(
            item.text,
            fontSize = 14.sp,
            color = LocalContentColor.current.copy(alpha = if (item.done) 0.5f else 1f),
            textDecoration = if (item.done) TextDecoration.LineThrough else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Single-line inline editor; the keyboard's Done action submits. */
@Composable
private fun TodoInput(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = TextStyle(color = LocalContentColor.current, fontSize = 14.sp),
        cursorBrush = SolidColor(tokens.accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        modifier = modifier.focusRequester(focusRequester),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterStart) {
                if (value.isEmpty()) WidgetDimText("New task", size = 14.sp, alpha = 0.5f)
                inner()
            }
        },
    )
}
