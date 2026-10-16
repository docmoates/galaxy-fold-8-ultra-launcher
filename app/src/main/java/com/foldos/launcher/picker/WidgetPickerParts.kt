package com.foldos.launcher.picker

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.a11yButton
import com.foldos.launcher.a11y.a11yDuration
import com.foldos.launcher.a11y.a11yMinTouchTarget
import com.foldos.launcher.theme.LocalFoldOsTokens

/** Duration of the sheet / card enter and exit animations. */
internal const val PICKER_ANIM_MS: Int = 320

/**
 * Dimming layer behind the picker; a tap anywhere on it dismisses. Same colours as the drawer's
 * scrim: black 38% without blur, black 22% with blur, white 18% with blur in light mode.
 */
@Composable
internal fun WidgetPickerScrim(visible: Boolean, blur: Boolean, onDismiss: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val animMs = a11yDuration(PICKER_ANIM_MS)
    // The caller passes a fresh lambda every recomposition; read the latest one without restarting the
    // tap detector (a restart could drop a tap in progress).
    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val scrimColor = when {
        !blur -> Color.Black.copy(alpha = 0.38f)
        tokens.isDark -> Color.Black.copy(alpha = 0.22f)
        else -> Color.White.copy(alpha = 0.18f)
    }
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.fillMaxSize(),
        enter = fadeIn(animationSpec = tween(animMs)),
        exit = fadeOut(animationSpec = tween(animMs)),
        label = "widgetPickerScrim",
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(scrimColor)
                // TalkBack: the dimmed backdrop is a "Close" button.
                .a11yButton(label = "Close widget picker", onActivate = onDismiss)
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { currentOnDismiss() })
                },
        )
    }
}

/** Panel container: themed sheet fill, hairline border, shadow, and a tap sink. */
@Composable
internal fun WidgetPickerPanel(
    shape: Shape,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    Box(
        modifier = modifier
            .shadow(elevation = 24.dp, shape = shape, clip = false)
            .clip(shape)
            .background(tokens.drawerScrim)
            .border(1.dp, tokens.surfaceBorder, shape)
            .semantics { paneTitle = "Add a widget" }
            .pointerInput(Unit) {
                detectTapGestures(onTap = { /* swallow: keep taps inside the panel */ })
            },
    ) {
        content()
    }
}

@Composable
internal fun WidgetPickerGrabHandle(modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Box(
        modifier = modifier
            .width(40.dp)
            .height(5.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(tokens.onSurface.copy(alpha = 0.35f)),
    )
}

/** Compact pill chip. */
@Composable
internal fun PickerChip(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(50)
    val background = if (selected) tokens.accent else tokens.surface
    val foreground = if (selected) tokens.onAccent else tokens.onSurface
    val borderColor = if (selected) Color.Transparent else tokens.surfaceBorder
    Text(
        text = title,
        color = foreground,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .a11yMinTouchTarget()
            .clip(shape)
            .background(background)
            .border(1.dp, borderColor, shape)
            .selectable(selected = selected, role = Role.Tab, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/** Pill search field. */
@Composable
internal fun WidgetPickerSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(26.dp)
    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, tokens.surfaceBorder, shape),
        placeholder = {
            Text(text = placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingIcon = {
            Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        shape = shape,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { /* results already filter live */ }),
        colors = TextFieldDefaults.colors(
            focusedContainerColor = tokens.surface,
            unfocusedContainerColor = tokens.surface,
            disabledContainerColor = tokens.surface,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
            disabledIndicatorColor = Color.Transparent,
            cursorColor = tokens.accent,
            focusedTextColor = tokens.onSurface,
            unfocusedTextColor = tokens.onSurface,
            focusedPlaceholderColor = tokens.onSurface.copy(alpha = 0.7f),
            unfocusedPlaceholderColor = tokens.onSurface.copy(alpha = 0.7f),
            focusedLeadingIconColor = tokens.onSurface,
            unfocusedLeadingIconColor = tokens.onSurface,
            focusedTrailingIconColor = tokens.onSurface,
            unfocusedTrailingIconColor = tokens.onSurface,
        ),
    )
}

@Composable
internal fun PickerEmptyState(text: String, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Text(
            text = text,
            color = tokens.onSurface.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/**
 * The icon of a widget group: the app's launcher icon, or an accent tile with a widgets glyph for
 * the FoldOS built-ins (and while an icon is still loading).
 */
@Composable
internal fun WidgetGroupIcon(group: WidgetAppGroup, size: Dp, modifier: Modifier = Modifier) {
    val tokens = LocalFoldOsTokens.current
    val sizePx = with(LocalDensity.current) { size.roundToPx() }.coerceAtLeast(1)
    val bitmap = if (group.isBuiltIn) null else rememberAppIconBitmap(group.key, sizePx)
    if (bitmap != null) {
        Image(
            bitmap = bitmap,
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size),
        )
    } else {
        val shape = RoundedCornerShape(size * 0.28f)
        Box(
            modifier = modifier
                .size(size)
                .clip(shape)
                .background(if (group.isBuiltIn) tokens.accent else tokens.onSurface.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Widgets,
                contentDescription = null,
                tint = if (group.isBuiltIn) tokens.onAccent else tokens.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.size(size * 0.55f),
            )
        }
    }
}
