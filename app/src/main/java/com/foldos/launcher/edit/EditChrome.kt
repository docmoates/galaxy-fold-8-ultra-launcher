package com.foldos.launcher.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.theme.LocalFoldOsTokens

/** Segmented-control entries (mock-up `editUI()`): value and label. */
internal val EditStyleOptions: List<Pair<HomeStyle, String>> = listOf(
    HomeStyle.GRID to "Grid",
    HomeStyle.DASHBOARD to "Dashboard",
    HomeStyle.LIST to "List",
    HomeStyle.HYBRID to "Panels",
)

/** Soft shadow under labels drawn on the wallpaper (mock-up `--tsh`). */
internal fun editTextShadow(): Shadow = Shadow(
    color = Color.Black.copy(alpha = 0.45f),
    offset = Offset(0f, 1f),
    blurRadius = 3f,
)

/** Mock-up `.eseg`: a pill with one segment per home style; the current one is filled in the accent. */
@Composable
internal fun EditStyleSegments(
    style: HomeStyle,
    onSelect: (HomeStyle) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val pill = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .clip(pill)
            .background(tokens.drawerScrim, pill)
            .border(1.dp, tokens.surfaceBorder, pill)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditStyleOptions.forEach { (value, label) ->
            val on = value == style
            Text(
                text = label,
                color = if (on) tokens.onAccent else tokens.onSurface,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .clip(pill)
                    .background(if (on) tokens.accent else Color.Transparent, pill)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** One entry of the bottom action bar. */
internal data class EditAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
)

/** Mock-up `.ebar`: 50dp frosted circles with a 12sp label under each, spread across the width. */
@Composable
internal fun EditActionBar(actions: List<EditAction>, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        actions.forEach { action ->
            val interaction = remember { MutableInteractionSource() }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = action.onClick,
                ),
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(tokens.drawerScrim, CircleShape)
                        .border(1.dp, tokens.surfaceBorder, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.label,
                        tint = tokens.onSurface,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    text = action.label,
                    color = tokens.onSurface,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    style = TextStyle(shadow = editTextShadow()),
                )
            }
        }
    }
}

/**
 * Small frosted pill (page chips, "Done", drop hints). [accent] fills it in the accent color;
 * a null [onClick] makes it a static label; [enabled] false dims it and ignores taps.
 */
@Composable
internal fun EditChip(
    label: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accent: Boolean = false,
    enabled: Boolean = true,
) {
    val tokens = LocalFoldOsTokens.current
    val pill = RoundedCornerShape(999.dp)
    val background = if (accent) tokens.accent else tokens.drawerScrim
    val foreground = (if (accent) tokens.onAccent else tokens.onSurface).copy(alpha = if (enabled) 1f else 0.45f)
    var base = modifier
        .clip(pill)
        .background(background, pill)
        .border(1.dp, if (accent) Color.Transparent else tokens.surfaceBorder, pill)
    if (onClick != null) base = base.clickable(enabled = enabled, onClick = onClick)
    Row(
        modifier = base
            .height(34.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = foreground,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = label,
            color = foreground,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

/** A round icon-only chip (page arrows). */
@Composable
internal fun EditRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val tokens = LocalFoldOsTokens.current
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(tokens.drawerScrim, CircleShape)
            .border(1.dp, tokens.surfaceBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tokens.onSurface.copy(alpha = if (enabled) 1f else 0.4f),
            modifier = Modifier.size(18.dp),
        )
    }
}
