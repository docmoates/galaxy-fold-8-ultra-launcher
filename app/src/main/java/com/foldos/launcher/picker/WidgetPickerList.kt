package com.foldos.launcher.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.a11yButton
import com.foldos.launcher.a11y.a11yHeading
import com.foldos.launcher.core.model.WidgetCategory
import com.foldos.launcher.core.model.WidgetEntry
import com.foldos.launcher.core.repo.WidgetCatalog
import com.foldos.launcher.theme.LocalFoldOsTokens

/** Test tag of the search field. */
internal const val TAG_SEARCH: String = "widget_picker_search"

/** Test tag of an app row: `widget_picker_app_<package>` ([BUILT_IN_GROUP_KEY] for the FoldOS row). */
internal fun appRowTag(groupKey: String): String = "widget_picker_app_$groupKey"

private val FeaturedColumnGap = 10.dp

/** One row of the Apps list: an alphabet header or an app. */
private sealed interface AppRowModel {
    val key: String

    data class Letter(val letter: Char, val index: Int) : AppRowModel {
        override val key: String get() = "letter_${index}_$letter"
    }

    data class App(val group: WidgetAppGroup) : AppRowModel {
        override val key: String get() = "app_${group.key}"
    }
}

private fun buildAppRows(groups: List<WidgetAppGroup>, alphabet: Boolean): List<AppRowModel> {
    val rows = ArrayList<AppRowModel>(groups.size + 27)
    var last: Char? = null
    for (group in groups) {
        if (group.isBuiltIn || !alphabet) {
            rows.add(AppRowModel.App(group))
            continue
        }
        if (group.letter != last) {
            rows.add(AppRowModel.Letter(group.letter, rows.size))
            last = group.letter
        }
        rows.add(AppRowModel.App(group))
    }
    return rows
}

/**
 * Level 1 of the picker: header with search, a "Featured" grid of FoldOS widgets, and the "Apps"
 * list (or the flat search results). Picking anything calls [onOpen] with the app and widget to
 * show on level 2.
 *
 * @param chip name of the active [WidgetCategory] filter chip, "" = All.
 * @param selected group / widget currently shown on the other pane (inner screen), highlighted.
 */
@Composable
internal fun WidgetListPane(
    model: PickerModel,
    groupMode: String,
    query: String,
    onQueryChange: (String) -> Unit,
    chip: String,
    onChipChange: (String) -> Unit,
    selected: DetailTarget?,
    onOpen: (DetailTarget) -> Unit,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val searching = query.isNotBlank()
    val category: WidgetCategory? = remember(chip) { WidgetCategory.entries.firstOrNull { it.name == chip } }
    val appGroups = remember(model, category) { model.groupsFor(category) }
    val showAlphabet = remember(appGroups) { appGroups.count { !it.isBuiltIn } > ALPHABET_HEADER_FROM }
    val appRows = remember(appGroups, showAlphabet) { buildAppRows(appGroups, showAlphabet) }
    val results = remember(model, query) { if (searching) model.search(query) else emptyList() }
    val featuredRows = remember(model) { model.featured.chunked(2) }
    val showRecents = groupMode == WidgetCatalog.GROUP_RECENT && model.recents.isNotEmpty() && category == null
    val showChips = groupMode != WidgetCatalog.GROUP_APP &&
        (model.chipCategories.isNotEmpty() || model.moreCategories.isNotEmpty())
    val selectedGroupKey = selected?.groupKey
    val selectedEntryId = selected?.startEntryId

    Column(modifier = modifier) {
        Column(
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "Widgets",
                color = tokens.onSurface,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.a11yHeading(),
            )
            WidgetPickerSearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = "Search widgets",
                modifier = Modifier.testTag(TAG_SEARCH),
            )
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (model.loading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 220.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = tokens.accent,
                            strokeWidth = 3.dp,
                        )
                    }
                }
            } else if (searching) {
                if (results.isEmpty()) {
                    item(key = "empty_results") {
                        PickerEmptyState(
                            text = "No widgets match",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 160.dp),
                        )
                    }
                } else {
                    items(items = results, key = { "res_${it.id}" }) { entry ->
                        WidgetResultRow(
                            entry = entry,
                            selected = entry.id == selectedEntryId,
                            onClick = { onOpen(DetailTarget(groupKeyOf(entry), entry.id)) },
                        )
                    }
                }
            } else {
                if (showRecents) {
                    item(key = "hdr_recent") { ListSectionTitle(WidgetCatalog.RECENT_GROUP_TITLE) }
                    items(items = model.recents.take(RECENT_ROWS_MAX), key = { "rec_${it.id}" }) { entry ->
                        WidgetResultRow(
                            entry = entry,
                            selected = entry.id == selectedEntryId,
                            onClick = { onOpen(DetailTarget(groupKeyOf(entry), entry.id)) },
                        )
                    }
                }

                if (featuredRows.isNotEmpty()) {
                    item(key = "hdr_featured") { ListSectionTitle("Featured") }
                    items(items = featuredRows, key = { row -> "feat_${row.first().id}" }) { row ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(FeaturedColumnGap),
                        ) {
                            row.forEach { entry ->
                                FeaturedCard(
                                    entry = entry,
                                    selected = entry.id == selectedEntryId,
                                    onClick = { onOpen(DetailTarget(BUILT_IN_GROUP_KEY, entry.id)) },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            if (row.size < 2) Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                item(key = "hdr_apps") { ListSectionTitle("Apps") }
                if (showChips) {
                    item(key = "chips") {
                        CategoryChips(
                            model = model,
                            chip = chip,
                            onChipChange = onChipChange,
                        )
                    }
                }
                if (appRows.isEmpty()) {
                    item(key = "empty_apps") {
                        PickerEmptyState(
                            text = "No apps with these widgets",
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 120.dp),
                        )
                    }
                } else {
                    items(items = appRows, key = { it.key }) { row ->
                        when (row) {
                            is AppRowModel.Letter -> LetterHeader(row.letter)
                            is AppRowModel.App -> AppRow(
                                group = row.group,
                                selected = row.group.key == selectedGroupKey,
                                onClick = { onOpen(DetailTarget(row.group.key, null)) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ListSectionTitle(text: String) {
    val tokens = LocalFoldOsTokens.current
    Text(
        text = text,
        color = tokens.onSurface.copy(alpha = 0.75f),
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 2.dp)
            .a11yHeading(),
    )
}

@Composable
private fun LetterHeader(letter: Char) {
    val tokens = LocalFoldOsTokens.current
    Text(
        text = letter.toString(),
        color = tokens.onSurface.copy(alpha = 0.5f),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, top = 8.dp, bottom = 0.dp),
    )
}

/** A card of the Featured grid: a 4:3 preview box (never cropped), then the name and the size. */
@Composable
private fun FeaturedCard(
    entry: WidgetEntry,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(tokens.surface)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) tokens.accent else tokens.surfaceBorder,
                shape = shape,
            )
            // One TalkBack element; the live preview inside is not read out.
            .a11yButton(
                label = "${entry.name}, ${sizeLabel(entry)}",
                onActivate = onClick,
                activateLabel = "Show widget",
            )
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .background(tokens.onSurface.copy(alpha = 0.06f)),
        ) {
            WidgetPreview(
                entry = entry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(10.dp),
                onClick = onClick,
            )
        }
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = entry.name,
                color = tokens.onSurface,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = sizeLabel(entry),
                color = tokens.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
            )
        }
    }
}

/** One app in the Apps list: icon (40dp), name, "N widgets", chevron. */
@Composable
private fun AppRow(
    group: WidgetAppGroup,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(appRowTag(group.key))
            .clip(shape)
            .background(if (selected) tokens.accent.copy(alpha = 0.18f) else Color.Transparent)
            .a11yButton(
                label = "${group.label}, ${group.countLabel}",
                onActivate = onClick,
                activateLabel = "Show widgets",
            )
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WidgetGroupIcon(group = group, size = 40.dp)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = group.label,
                color = tokens.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = group.countLabel,
                color = tokens.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = tokens.onSurface.copy(alpha = 0.5f),
            modifier = Modifier.size(22.dp),
        )
    }
}

/** A widget in a flat list (search results, recently used): 56dp preview, name, app and size. */
@Composable
private fun WidgetResultRow(
    entry: WidgetEntry,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(16.dp)
    val previewShape = RoundedCornerShape(12.dp)
    val sourceLabel = if (entry.isBuiltIn) "Built in" else entry.source
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) tokens.accent.copy(alpha = 0.18f) else Color.Transparent)
            .a11yButton(
                label = "${entry.name}, $sourceLabel, ${sizeLabel(entry)}",
                onActivate = onClick,
                activateLabel = "Show widget",
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(previewShape)
                .background(tokens.surface)
                .border(1.dp, tokens.surfaceBorder, previewShape),
        ) {
            WidgetPreview(
                entry = entry,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(4.dp),
                onClick = onClick,
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = entry.name,
                color = tokens.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "$sourceLabel · ${sizeLabel(entry)}",
                color = tokens.onSurface.copy(alpha = 0.6f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One row of compact filter chips: All, the common categories, and "More" for the rest. */
@Composable
private fun CategoryChips(
    model: PickerModel,
    chip: String,
    onChipChange: (String) -> Unit,
) {
    var moreOpen by remember { mutableStateOf(false) }
    val moreSelected = model.moreCategories.firstOrNull { it.name == chip }
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "chip_all") {
            PickerChip(title = "All", selected = chip.isEmpty(), onClick = { onChipChange("") })
        }
        items(items = model.chipCategories, key = { "chip_${it.name}" }) { category ->
            PickerChip(
                title = category.displayName.substringBefore(" &"),
                selected = chip == category.name,
                onClick = { onChipChange(category.name) },
            )
        }
        if (model.moreCategories.isNotEmpty()) {
            item(key = "chip_more") {
                Box {
                    PickerChip(
                        title = moreSelected?.displayName?.substringBefore(" &") ?: "More",
                        selected = moreSelected != null,
                        onClick = { moreOpen = true },
                    )
                    DropdownMenu(expanded = moreOpen, onDismissRequest = { moreOpen = false }) {
                        model.moreCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(text = category.displayName) },
                                onClick = {
                                    moreOpen = false
                                    onChipChange(category.name)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
