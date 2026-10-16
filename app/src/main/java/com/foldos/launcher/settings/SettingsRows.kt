@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.foldos.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.a11yHeading
import com.foldos.launcher.a11y.a11yLabel
import com.foldos.launcher.a11y.a11yMergedRow
import com.foldos.launcher.a11y.a11ySelectable
import kotlin.math.roundToInt

/** Title, subtitle and advanced flag of one settings row. Also feeds settings search. */
data class SettingsRowInfo(
    val title: String,
    val subtitle: String? = null,
    val advanced: Boolean = false,
) {
    fun matches(query: String): Boolean {
        if (query.isBlank()) return true
        if (title.contains(query, ignoreCase = true)) return true
        return subtitle?.contains(query, ignoreCase = true) == true
    }
}

/** Current search query and Advanced switch; rows hide themselves when they do not pass. */
data class SettingsRowFilter(
    val query: String = "",
    val advanced: Boolean = false,
) {
    fun shows(info: SettingsRowInfo): Boolean = (!info.advanced || advanced) && info.matches(query)
}

val LocalSettingsRowFilter: ProvidableCompositionLocal<SettingsRowFilter> =
    compositionLocalOf { SettingsRowFilter() }

@Composable
private fun rowVisible(info: SettingsRowInfo, visible: Boolean): Boolean =
    visible && LocalSettingsRowFilter.current.shows(info)

private fun subtitleContent(subtitle: String?): (@Composable () -> Unit)? =
    if (subtitle == null) null else { { Text(text = subtitle) } }

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        // Section titles are headings so a screen reader can jump between them.
        modifier = Modifier.a11yHeading().padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 4.dp),
    )
}

@Composable
fun ToggleRow(
    info: SettingsRowInfo,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    visible: Boolean = true,
    enabled: Boolean = true,
    leadingContent: (@Composable () -> Unit)? = null,
) {
    if (!rowVisible(info, visible)) return
    // One toggleable row (role Switch, on / off state): the Switch itself is only the visual, so a
    // screen reader finds a single element instead of a row plus a nested switch.
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        leadingContent = leadingContent,
        headlineContent = { Text(text = info.title) },
        supportingContent = subtitleContent(info.subtitle),
        trailingContent = {
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        },
    )
}

@Composable
fun <T> SelectRow(
    info: SettingsRowInfo,
    value: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    visible: Boolean = true,
) {
    if (!rowVisible(info, visible)) return
    var expanded by remember { mutableStateOf(false) }
    val currentLabel = options.firstOrNull { it.first == value }?.second ?: ""
    ListItem(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Choose ${info.title}", role = Role.DropdownList) { expanded = true },
        headlineContent = { Text(text = info.title) },
        supportingContent = subtitleContent(info.subtitle),
        trailingContent = {
            Box {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = currentLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Icon(
                        imageVector = Icons.Filled.ArrowDropDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    options.forEach { option ->
                        val selected = option.first == value
                        DropdownMenuItem(
                            text = { Text(text = option.second) },
                            modifier = Modifier.a11ySelectable(selected = selected, role = Role.RadioButton),
                            onClick = {
                                expanded = false
                                onSelect(option.first)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ChipsRow(
    info: SettingsRowInfo,
    value: T,
    options: List<Pair<T, String>>,
    onSelect: (T) -> Unit,
    visible: Boolean = true,
) {
    if (!rowVisible(info, visible)) return
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(text = info.title) },
            supportingContent = subtitleContent(info.subtitle),
        )
        FlowRow(
            modifier = Modifier
                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { option ->
                FilterChip(
                    selected = option.first == value,
                    onClick = { onSelect(option.first) },
                    label = { Text(text = option.second) },
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
    }
}

@Composable
fun SliderRow(
    info: SettingsRowInfo,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    visible: Boolean = true,
    unit: String = "",
) {
    if (!rowVisible(info, visible)) return
    var local by remember(value) { mutableFloatStateOf(value.toFloat()) }
    val stepCount = (range.last - range.first - 1).coerceAtLeast(0)
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            modifier = Modifier.a11yMergedRow(),
            headlineContent = { Text(text = info.title) },
            supportingContent = subtitleContent(info.subtitle),
            trailingContent = {
                Text(
                    text = "${local.roundToInt()}$unit",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Slider(
            value = local,
            onValueChange = { next ->
                local = next
                val rounded = next.roundToInt()
                if (rounded != value) onValueChange(rounded)
            },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = stepCount,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                // The slider is read as "<title>, <value>", e.g. "Icon size, 58 dp".
                .semantics {
                    contentDescription = info.title
                    stateDescription = "${local.roundToInt()}${unit}"
                },
        )
    }
}

@Composable
fun StaticRow(
    info: SettingsRowInfo,
    onClick: (() -> Unit)? = null,
    visible: Boolean = true,
    showChevron: Boolean = true,
    trailingText: String? = null,
) {
    if (!rowVisible(info, visible)) return
    val base = Modifier.fillMaxWidth()
    val modifier = if (onClick != null) base.clickable(role = Role.Button) { onClick() } else base.a11yMergedRow()
    ListItem(
        modifier = modifier,
        headlineContent = { Text(text = info.title) },
        supportingContent = subtitleContent(info.subtitle),
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (trailingText != null) {
                    Text(
                        text = trailingText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (showChevron) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

/** ARGB Long as "#RRGGBB". */
fun hexOfColor(value: Long): String = "#%06X".format(value and 0xFFFFFFL)

/** Parses "#RRGGBB" or "RRGGBB" into an opaque ARGB Long, or null when malformed. */
fun parseHexColor(text: String): Long? {
    val digits = text.trim().removePrefix("#")
    if (digits.length != 6) return null
    val rgb = digits.toLongOrNull(16) ?: return null
    return 0xFF000000L or rgb
}

@Composable
fun ColorRow(
    info: SettingsRowInfo,
    value: Long,
    onValueChange: (Long) -> Unit,
    visible: Boolean = true,
) {
    if (!rowVisible(info, visible)) return
    var text by remember(value) { mutableStateOf(hexOfColor(value)) }
    val valid = parseHexColor(text) != null
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        headlineContent = { Text(text = info.title) },
        supportingContent = subtitleContent(info.subtitle),
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(Color(value))
                        .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape),
                )
                Spacer(modifier = Modifier.width(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { next ->
                        text = next
                        val parsed = parseHexColor(next)
                        if (parsed != null && parsed != value) onValueChange(parsed)
                    },
                    singleLine = true,
                    isError = !valid,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .width(132.dp)
                        .a11yLabel("${info.title} hex value"),
                )
            }
        },
    )
}
