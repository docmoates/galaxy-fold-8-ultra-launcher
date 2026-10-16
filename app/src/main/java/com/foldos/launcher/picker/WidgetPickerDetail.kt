package com.foldos.launcher.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.a11yHeading
import com.foldos.launcher.core.model.WidgetCategory
import com.foldos.launcher.core.model.WidgetEntry
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Test tag of the "Add to home" button. */
internal const val TAG_ADD: String = "widget_picker_add"

/** Width of one grid cell of the large preview before it is limited to the card. */
private val DetailPreviewCell: Dp = 100.dp

/** Up to this many widgets the page indicator is a row of dots; beyond it, "3 / 12". */
private const val MAX_DOTS: Int = 8

/**
 * Level 2 of the picker: the widgets of one app (or of FoldOS), one per page, with the preview
 * large and an "Add to home" button. [group] null shows a hint instead.
 *
 * @param showBack true on the cover sheet (a back arrow returns to the list); false on the inner
 *   card where the list stays visible next to this pane.
 * @param startEntryId widget the pager starts on; the first widget when null or unknown.
 * @param category when set, the pager only holds this app's widgets of that category (falls back to
 *   all of the app's widgets when it has none); the list's filter chip.
 * @param loading true while the catalog is still loading: nothing is shown instead of the hint.
 */
@Composable
internal fun WidgetDetailPane(
    group: WidgetAppGroup?,
    startEntryId: String?,
    showBack: Boolean,
    onBack: () -> Unit,
    onAdd: (WidgetEntry) -> Unit,
    modifier: Modifier = Modifier,
    category: WidgetCategory? = null,
    loading: Boolean = false,
) {
    val tokens = LocalFoldOsTokens.current
    if (group == null || group.entries.isEmpty()) {
        if (loading) {
            Box(modifier = modifier)
        } else {
            PickerEmptyState(text = "Pick an app to see its widgets", modifier = modifier)
        }
        return
    }
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (showBack) 6.dp else 20.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (showBack) {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back to widget list",
                        tint = tokens.onSurface,
                    )
                }
            }
            WidgetGroupIcon(group = group, size = 32.dp)
            Text(
                text = group.label,
                color = tokens.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .a11yHeading(),
            )
        }

        // A new app (or a new starting widget) gets a fresh pager.
        key(group.key, startEntryId) {
            val entries = remember(group, category) {
                if (category == null) {
                    group.entries
                } else {
                    group.entries.filter { it.category == category }.ifEmpty { group.entries }
                }
            }
            val startIndex = remember(group.key, startEntryId) {
                entries.indexOfFirst { it.id == startEntryId }.coerceAtLeast(0)
            }
            val pagerState = rememberPagerState(initialPage = startIndex, pageCount = { entries.size })

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                pageSpacing = 16.dp,
                key = { page -> entries.getOrNull(page)?.id ?: page },
            ) { page ->
                val entry = entries.getOrNull(page)
                if (entry != null) {
                    DetailPage(entry = entry, onAdd = { onAdd(entry) })
                }
            }

            PageIndicator(
                state = pagerState,
                count = entries.size,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp, bottom = 14.dp),
            )
        }
    }
}

/** One widget: large preview on a soft card, name, size and source, description, "Add to home". */
@Composable
private fun DetailPage(entry: WidgetEntry, onAdd: () -> Unit) {
    val tokens = LocalFoldOsTokens.current
    val description by rememberWidgetDescription(entry)
    val sourceLabel = if (entry.isBuiltIn) "Built in" else entry.source

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Read the available space once, here at the top of the scope.
        val pageWidth = maxWidth
        val pageHeight = maxHeight
        val cardPadding = 14.dp
        val cardMaxWidth = minOf(pageWidth * 0.7f, 420.dp)
        val aspect = previewAspect(entry)
        val wantedWidth = minOf(DetailPreviewCell * previewSpanX(entry), cardMaxWidth - cardPadding * 2)
        val maxInnerHeight = maxOf(80.dp, pageHeight * 0.42f - cardPadding * 2)
        val heightForWidth = wantedWidth / aspect
        val innerHeight = minOf(heightForWidth, maxInnerHeight)
        val innerWidth = if (heightForWidth > maxInnerHeight) maxInnerHeight * aspect else wantedWidth

        Column(modifier = Modifier.fillMaxSize()) {
            // Preview, name, size and description sit centred in all the height the button leaves.
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val cardShape = RoundedCornerShape(24.dp)
                    Box(
                        modifier = Modifier
                            .size(width = innerWidth + cardPadding * 2, height = innerHeight + cardPadding * 2)
                            .clip(cardShape)
                            .background(tokens.onSurface.copy(alpha = 0.07f))
                            .border(1.dp, tokens.surfaceBorder, cardShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        WidgetPreview(
                            entry = entry,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(cardPadding)
                                .clearAndSetSemantics { contentDescription = "Preview of ${entry.name}" },
                            contentScale = ContentScale.Inside,
                        )
                    }
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(
                        text = entry.name,
                        color = tokens.onSurface,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "Size ${sizeLabel(entry)} · $sourceLabel",
                        color = tokens.onSurface.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val text = description
                    if (!text.isNullOrBlank()) {
                        Text(
                            text = text,
                            color = tokens.onSurface.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            // The button is anchored to the bottom of the sheet with 16dp around it.
            Button(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                    .heightIn(min = 52.dp)
                    .testTag(TAG_ADD),
                colors = ButtonDefaults.buttonColors(
                    containerColor = tokens.accent,
                    contentColor = tokens.onAccent,
                ),
            ) {
                Text(text = "Add to home", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** Dots under the pager, "3 / 12" for long lists, nothing for a single widget. */
@Composable
private fun PageIndicator(state: PagerState, count: Int, modifier: Modifier = Modifier) {
    if (count <= 1) return
    val tokens = LocalFoldOsTokens.current
    val current = state.currentPage.coerceIn(0, count - 1)
    Row(
        modifier = modifier.semantics { contentDescription = "Widget ${current + 1} of $count" },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (count <= MAX_DOTS) {
            for (index in 0 until count) {
                val active = index == current
                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .size(if (active) 9.dp else 7.dp)
                        .clip(CircleShape)
                        .background(if (active) tokens.accent else tokens.onSurface.copy(alpha = 0.25f)),
                )
            }
        } else {
            Text(
                text = "${current + 1} / $count",
                color = tokens.onSurface.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

// ---- descriptions -----------------------------------------------------------------------------

/**
 * One-line description of [entry]: the provider's own description for an app widget (loaded off the
 * main thread, null when it ships none), a short hand-written line for a FoldOS built-in.
 */
@Composable
private fun rememberWidgetDescription(entry: WidgetEntry): State<String?> {
    return produceState<String?>(
        initialValue = if (entry.isBuiltIn) builtInDescription(entry.id) else null,
        key1 = entry.id,
    ) {
        if (entry.isBuiltIn) return@produceState
        val loader = entry.descriptionLoader ?: return@produceState
        value = withContext(Dispatchers.IO) {
            runCatching { loader() }.getOrNull()
        }
    }
}

private fun builtInDescription(id: String): String = when (id.removePrefix("foldos.")) {
    "clock" -> "A clock for your home screen."
    "glance" -> "A glanceable summary of your day."
    "weather" -> "Current weather at a glance."
    "calendar" -> "Your upcoming events."
    "devices" -> "Battery levels of your devices."
    "todo" -> "Your to-do list on the home screen."
    "media" -> "Controls for the media that is playing."
    "focus" -> "A timer to help you focus."
    "photos" -> "Photos from your gallery."
    "news" -> "Headlines from your news sources."
    else -> "A FoldOS widget. Resize it after adding."
}
