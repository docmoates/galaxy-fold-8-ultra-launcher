package com.foldos.launcher.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.theme.LocalFoldOsTokens

/** `Modifier.testTag` values of every edit-mode control (UI tests and accessibility tooling). */
object EditTags {
    const val TOP_BAR: String = "edit_top_bar"
    const val STYLE_SEGMENTS: String = "edit_style_segments"
    const val PAGE_STRIP: String = "edit_page_strip"
    const val PAGE_PREV: String = "edit_page_prev"
    const val PAGE_NEXT: String = "edit_page_next"
    const val PAGE_LABEL: String = "edit_page_label"
    const val ADD_PAGE: String = "edit_add_page"
    const val DELETE_PAGE: String = "edit_delete_page"
    const val DONE: String = "edit_done"
    const val BOTTOM_BAR: String = "edit_bottom_bar"
    const val ACTION_WALLPAPER: String = "edit_action_wallpaper"
    const val ACTION_THEMES: String = "edit_action_themes"
    const val ACTION_WIDGETS: String = "edit_action_widgets"
    const val ACTION_DOCK: String = "edit_action_dock"
    const val ACTION_SETTINGS: String = "edit_action_settings"
    const val GRID_LAYER: String = "edit_grid_layer"
    const val REMOVE_ITEM: String = "edit_remove_item"
    const val CONFIRM_REMOVE_FOLDER: String = "edit_confirm_remove_folder"
    const val UNDO_SNACKBAR: String = "edit_undo_snackbar"
    const val UNDO_BUTTON: String = "edit_undo_button"

    /** `edit_style_grid`, `edit_style_dashboard`, `edit_style_list`, `edit_style_hybrid`. */
    fun styleSegment(style: HomeStyle): String = "edit_style_" + style.name.lowercase()

    /** `edit_resize_top_left`, ... for the four corner handles of the selected widget. */
    fun resizeHandle(corner: String): String = "edit_resize_" + corner.lowercase()
}

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

/**
 * Mock-up `.eseg`: a pill with one equal-width segment per home style; the current one is filled in
 * the accent. Give it a fixed height (the edit top bar uses 44dp) and `fillMaxWidth()`.
 */
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
            .padding(4.dp)
            .testTag(EditTags.STYLE_SEGMENTS),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EditStyleOptions.forEach { (value, label) ->
            val on = value == style
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(pill)
                    .background(if (on) tokens.accent else Color.Transparent, pill)
                    .clickable { onSelect(value) }
                    .testTag(EditTags.styleSegment(value)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    color = if (on) tokens.onAccent else tokens.onSurface,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Clip,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/** One entry of the bottom action bar; [tag] is its `testTag`. */
internal data class EditAction(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit,
    val tag: String = "",
)

/**
 * Mock-up `.ebar`: 50dp frosted circles with a small label under each, in equal-width columns
 * across the width (so six entries still fit a 360dp cover screen).
 */
@Composable
internal fun EditActionBar(actions: List<EditAction>, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val labelSize = if (actions.size > 5) 11.sp else 12.sp
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.Top,
    ) {
        actions.forEach { action ->
            val interaction = remember { MutableInteractionSource() }
            var itemModifier = Modifier
                .weight(1f)
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = action.onClick,
                )
            if (action.tag.isNotBlank()) itemModifier = itemModifier.testTag(action.tag)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = itemModifier,
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
                    fontSize = labelSize,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(shadow = editTextShadow()),
                )
            }
        }
    }
}

/**
 * Small frosted pill (page label, "Done", confirmations). [accent] fills it in the accent color;
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
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A round icon-only chip (page arrows, add / delete page). [accent] fills it in the accent color. */
@Composable
internal fun EditRoundButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    accent: Boolean = false,
) {
    val tokens = LocalFoldOsTokens.current
    val foreground = (if (accent) tokens.onAccent else tokens.onSurface).copy(alpha = if (enabled) 1f else 0.4f)
    Box(
        modifier = modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(if (accent) tokens.accent else tokens.drawerScrim, CircleShape)
            .border(1.dp, if (accent) Color.Transparent else tokens.surfaceBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = foreground,
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * The remove confirmation: a pill with a message and, when [actionLabel] is set, an action
 * ("Undo"). Anchored by the caller just above the bottom bar.
 */
@Composable
internal fun EditSnackbar(
    message: String,
    actionLabel: String?,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val pill = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .clip(pill)
            .background(tokens.drawerScrim, pill)
            .border(1.dp, tokens.surfaceBorder, pill)
            .padding(start = 18.dp, end = if (actionLabel != null) 6.dp else 18.dp)
            .height(48.dp)
            .testTag(EditTags.UNDO_SNACKBAR),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = message,
            color = tokens.onSurface,
            fontSize = 13.5.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (actionLabel != null) {
            Box(
                modifier = Modifier
                    .height(40.dp)
                    .clip(pill)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 14.dp)
                    .testTag(EditTags.UNDO_BUTTON),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = actionLabel,
                    color = tokens.accent,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
        }
    }
}
