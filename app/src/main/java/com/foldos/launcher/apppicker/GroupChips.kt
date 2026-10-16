package com.foldos.launcher.apppicker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.a11yButton
import com.foldos.launcher.a11y.a11yMinTouchTarget
import com.foldos.launcher.drawer.DrawerAppIcon
import com.foldos.launcher.theme.LocalFoldOsTokens

/**
 * The group folders at the top of the app pop-up: "All", the automatic groups (Work, Media, Social,
 * Tools, Games, Hidden), the user's groups and a dashed "New group" card. Tap selects; long press on a
 * user group opens its manage dialog ([onManageGroup]).
 */
@Composable
fun GroupChipRow(
    model: PickerModel,
    onSelect: (PickerGroup) -> Unit,
    onNewGroup: () -> Unit,
    onManageGroup: (PickerGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .selectableGroup()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        model.groups.forEach { group ->
            GroupCard(
                group = group,
                selected = group.id == model.selected.id,
                onClick = { onSelect(group) },
                onLongClick = if (group.kind == PickerGroup.Kind.USER) ({ onManageGroup(group) }) else null,
            )
        }
        NewGroupCard(onClick = onNewGroup)
    }
}

@Composable
private fun GroupCard(
    group: PickerGroup,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(18.dp)
    val background = if (selected) tokens.accent else tokens.surface
    val foreground = if (selected) tokens.onAccent else tokens.onSurface
    val borderColor = if (selected) Color.Transparent else tokens.surfaceBorder
    val count = group.apps.size
    val latestClick = rememberUpdatedState(onClick)
    val latestLongClick = rememberUpdatedState(onLongClick)
    Row(
        modifier = Modifier
            .a11yMinTouchTarget()
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            // A tab of the group strip: read as "Media, 12 apps, tab, selected".
            .a11yButton(
                label = "${group.title}, $count ${if (count == 1) "app" else "apps"}",
                onActivate = onClick,
                onMenu = onLongClick,
                menuLabel = "Manage group",
                role = Role.Tab,
                selected = selected,
            )
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { latestClick.value() },
                    onLongPress = { latestLongClick.value?.invoke() },
                )
            }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        GroupGlyph(group = group, foreground = foreground, selected = selected)
        Column {
            Text(
                text = group.title,
                color = foreground,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 2.dp),
            )
            Text(
                text = if (count == 1) "1 app" else "$count apps",
                color = foreground.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/** The 30dp glyph of a group card: a 2 x 2 collage of its first apps, or an icon for "All" / "Hidden". */
@Composable
private fun GroupGlyph(group: PickerGroup, foreground: Color, selected: Boolean) {
    val tokens = LocalFoldOsTokens.current
    val plate = if (selected) tokens.onAccent.copy(alpha = 0.16f) else tokens.onSurface.copy(alpha = 0.10f)
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(plate),
        contentAlignment = Alignment.Center,
    ) {
        when {
            group.kind == PickerGroup.Kind.ALL -> Icon(
                imageVector = Icons.Rounded.Apps,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(20.dp),
            )
            group.kind == PickerGroup.Kind.HIDDEN -> Icon(
                imageVector = Icons.Rounded.VisibilityOff,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(20.dp),
            )
            group.apps.isEmpty() -> Box(
                Modifier
                    .size(12.dp)
                    .background(foreground.copy(alpha = 0.35f), CircleShape),
            )
            else -> Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                for (row in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        for (col in 0 until 2) {
                            val app = group.apps.getOrNull(row * 2 + col)
                            if (app != null) {
                                DrawerAppIcon(app = app, size = 12.dp)
                            } else {
                                Box(Modifier.size(12.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Dashed "New group" card at the end of the strip. */
@Composable
private fun NewGroupCard(onClick: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(18.dp)
    val dashColor = tokens.onSurface.copy(alpha = 0.45f)
    val latestClick = rememberUpdatedState(onClick)
    Row(
        modifier = Modifier
            .a11yMinTouchTarget()
            .clip(shape)
            .drawBehind {
                drawRoundRect(
                    color = dashColor,
                    cornerRadius = CornerRadius(18.dp.toPx()),
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10.dp.toPx(), 7.dp.toPx())),
                    ),
                )
            }
            .a11yButton(label = "New group", onActivate = onClick)
            .pointerInput(Unit) { detectTapGestures(onTap = { latestClick.value() }) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Add,
            contentDescription = null,
            tint = tokens.onSurface,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = "New group",
            color = tokens.onSurface,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
