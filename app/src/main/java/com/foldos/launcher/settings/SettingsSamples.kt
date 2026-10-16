package com.foldos.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.a11yHeading
import com.foldos.launcher.onboarding.PREVIEW_COVER_HEIGHT
import com.foldos.launcher.onboarding.PREVIEW_COVER_WIDTH

/*
 * Building blocks of the sample-based Settings (Fix round 1.1): sections of tappable preview cards, the
 * current one outlined. They use the Material colors of the Settings window; the previews inside the
 * cards are the shared `onboarding` samples, so Settings and the setup wizard look the same.
 */

/** Widest the sample sections grow, so the cards do not balloon on the inner screen. */
internal val SampleMaxWidth: Dp = 840.dp

/** Width/height of a whole cover-home preview (411 x 700). */
internal val PreviewRatio: Float = PREVIEW_COVER_WIDTH.value / PREVIEW_COVER_HEIGHT.value

/** Width/height of the dock-only strip preview. */
internal val DockStripRatio: Float = PREVIEW_COVER_WIDTH.value / 190f

/** One Settings section: a heading, an optional one-line explanation, and the sample cards. */
@Composable
internal fun SampleSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier
                .widthIn(max = SampleMaxWidth)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.a11yHeading(),
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            content()
        }
    }
}

/**
 * A card holding a [preview] with its [label] under it. The whole card is one tap target (a selectable
 * overlay sits above the preview so live composables inside never swallow the tap); when [selected] it
 * is outlined in the primary color and carries a check mark.
 */
@Composable
internal fun SampleCard(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    description: String? = null,
    previewBackground: Color = Color.Black.copy(alpha = 0.18f),
    preview: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) colors.primary else colors.outlineVariant,
                shape = shape,
            ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 8.dp, top = 8.dp, bottom = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(previewBackground),
                contentAlignment = Alignment.Center,
            ) {
                preview()
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) colors.primary else colors.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
            if (description != null) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
            }
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(14.dp)
                    .size(24.dp)
                    .background(colors.surface, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = colors.primary,
                )
            }
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .semantics { contentDescription = label }
                .selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        )
    }
}

/**
 * Lays [items] out in equal columns: [columns] on a phone-wide area, [wideColumns] when the area is at
 * least [wideFrom] wide (the inner screen). Rows are added as needed, 10dp apart.
 */
@Composable
internal fun <T> SampleGrid(
    items: List<T>,
    columns: Int = 2,
    wideColumns: Int = 4,
    wideFrom: Dp = 560.dp,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val count = if (maxWidth >= wideFrom) wideColumns else columns
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items.chunked(count).forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    rowItems.forEach { item ->
                        Box(modifier = Modifier.weight(1f)) { content(item) }
                    }
                    repeat(count - rowItems.size) { Box(modifier = Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** One color tile on the Colors section: a swatch (already drawn by the caller) with its name under it. */
@Composable
internal fun ColorTile(
    name: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    swatch: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) colors.primary else colors.outlineVariant,
                    shape = RoundedCornerShape(18.dp),
                )
                .padding(3.dp),
        ) {
            swatch()
        }
        Text(
            text = name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) colors.primary else colors.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

/** A small fixed gap. */
@Composable
internal fun SampleGap(height: Dp = 8.dp) {
    Spacer(modifier = Modifier.height(height))
}

/** A clickable wide row used for "More docks", "More settings" and similar entries below sample cards. */
@Composable
internal fun SampleLinkRow(
    title: String,
    subtitle: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainerHigh)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = colors.onSurfaceVariant,
        )
    }
}
