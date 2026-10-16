package com.foldos.launcher.search

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CenterFocusWeak
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.builtins.LocalPermissionRequester
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import com.foldos.launcher.theme.FoldOsSurface
import com.foldos.launcher.theme.LocalFoldOsTokens
import kotlinx.coroutines.delay

/**
 * Universal search (PLAN.md section 4): a sheet anchored to the top of the screen with the search
 * pill (mock-up `.srch`), results grouped in sections below it and the keyboard open. On the inner
 * screen it is a centered card (max 640dp wide); on the cover it spans the width.
 *
 * - The text field takes focus when the sheet opens; Search on the keyboard activates the
 *   highlighted result (the first one by default), arrow keys move the highlight, Escape closes.
 * - Results come from [SearchIndex] with the providers enabled in [settings] (`searchProviders`);
 *   apps go through [onLaunch], settings rows through [onOpenSettingsRoute] (a `SettingsRoutes`
 *   value), everything else is opened by [SearchActions]. Permission rows ask through
 *   `LocalPermissionRequester`.
 * - With an empty query the sheet lists recent queries and the Lens / web quick actions.
 * - [initialQuery] (hardware keyboard type-to-search) pre-fills the query while the sheet opens.
 *
 * Compose it last inside the launcher `Box` (above the drawer) so it draws on top; it is a no-op
 * while [visible] is false apart from its enter / exit animation.
 */
@Composable
fun UniversalSearch(
    visible: Boolean,
    screen: ScreenKind,
    settings: LauncherSettings,
    onDismiss: () -> Unit,
    onLaunch: (AppInfo) -> Unit,
    onOpenSettingsRoute: (String) -> Unit,
    modifier: Modifier = Modifier,
    initialQuery: String? = null,
) {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    val requestPermission = LocalPermissionRequester.current
    LaunchedEffect(graph) { SearchIndex.attach(context, graph) }

    val apps by graph.apps.apps.collectAsStateWithLifecycle(initialValue = emptyList())
    val recentFlow = remember(visible) { SearchIndex.recentQueries() }
    val recent by recentFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SearchResult>>(emptyList()) }
    var highlighted by remember { mutableIntStateOf(0) }

    LaunchedEffect(visible) {
        if (!visible) {
            query = ""
            results = emptyList()
            highlighted = 0
        }
    }
    // Type-to-search: the character typed on the home screen becomes the start of the query.
    LaunchedEffect(visible, initialQuery) {
        if (visible && !initialQuery.isNullOrEmpty()) query = initialQuery
    }
    LaunchedEffect(query, apps, settings, visible) {
        if (!visible) return@LaunchedEffect
        if (query.isBlank()) {
            results = emptyList()
            highlighted = 0
            return@LaunchedEffect
        }
        delay(50) // debounce fast typing
        results = SearchIndex.query(query, settings, apps)
        highlighted = 0
    }
    BackHandler(enabled = visible) { onDismiss() }

    val activate: (SearchResult) -> Unit = { result ->
        SearchIndex.recordPick(result, query)
        val close = SearchActions.activate(context, result, onLaunch, onOpenSettingsRoute, requestPermission)
        if (close) onDismiss()
    }

    // Full-screen takeover: one opaque surface that reaches every window edge (status bar included),
    // so nothing of the home screen can show as a strip. Only a quick fade runs on the surface; the
    // sheet content slides a few dp inside it (see SearchSheet).
    Box(modifier = modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(140)),
            exit = fadeOut(tween(110)),
            modifier = Modifier.fillMaxSize(),
        ) {
            val tokens = LocalFoldOsTokens.current
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Wallpaper first so a translucent drawerScrim still ends up opaque.
                    .background(tokens.wallpaper)
                    .background(tokens.drawerScrim)
                    .pointerInput(onDismiss) { detectTapGestures { onDismiss() } },
            ) {
                SearchSheet(
                    screen = screen,
                    settings = settings,
                    query = query,
                    onQueryChange = { query = it },
                    results = results,
                    recent = recent,
                    highlighted = highlighted,
                    onHighlight = { highlighted = it },
                    onActivate = activate,
                    onRecent = { query = it },
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

/** One lazy-list row of the sheet; keeps the keyboard highlight and the list in step. */
private sealed class SheetRow {
    data class Header(val title: String) : SheetRow()
    data class Apps(val apps: List<SearchResult.App>, val firstIndex: Int) : SheetRow()
    data class Single(val result: SearchResult, val index: Int) : SheetRow()
    data class Recent(val query: String, val index: Int) : SheetRow()
}

private fun buildRows(results: List<SearchResult>, recent: List<String>, settings: LauncherSettings): Pair<List<SheetRow>, List<Any>> {
    val rows = ArrayList<SheetRow>()
    val targets = ArrayList<Any>() // what each highlight index activates: SearchResult or String
    if (results.isEmpty()) {
        if (recent.isNotEmpty()) {
            rows += SheetRow.Header("Recent")
            for (q in recent.take(6)) {
                rows += SheetRow.Recent(q, targets.size)
                targets += q
            }
        }
        if (settings.webSearchOn()) {
            rows += SheetRow.Header("Quick actions")
            val visual = SearchResult.Visual("")
            rows += SheetRow.Single(visual, targets.size)
            targets += visual
        }
        return rows to targets
    }
    var index = 0
    var section: SearchSection? = null
    var i = 0
    while (i < results.size) {
        val result = results[i]
        if (result.section != section) {
            section = result.section
            rows += SheetRow.Header(section.title)
        }
        if (result is SearchResult.App) {
            val group = ArrayList<SearchResult.App>()
            while (i < results.size && results[i] is SearchResult.App) {
                group += results[i] as SearchResult.App
                i++
            }
            rows += SheetRow.Apps(group, index)
            targets.addAll(group)
            index += group.size
            continue
        }
        rows += SheetRow.Single(result, index)
        targets += result
        index++
        i++
    }
    return rows to targets
}

@Composable
private fun AnimatedVisibilityScope.SearchSheet(
    screen: ScreenKind,
    settings: LauncherSettings,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<SearchResult>,
    recent: List<String>,
    highlighted: Int,
    onHighlight: (Int) -> Unit,
    onActivate: (SearchResult) -> Unit,
    onRecent: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val context = LocalContext.current
    val wide = screen != ScreenKind.COVER
    val (rows, targets) = remember(results, recent, settings) { buildRows(results, recent, settings) }
    val listState = rememberLazyListState()

    val activateHighlighted: () -> Unit = {
        when (val target = targets.getOrNull(highlighted.coerceIn(0, (targets.size - 1).coerceAtLeast(0)))) {
            is SearchResult -> onActivate(target)
            is String -> onRecent(target)
            else -> if (query.isNotBlank() && settings.webSearchOn()) {
                val resolved = WebSearch.resolve(query, settings.searchEngine)
                SearchActions.openUrl(context, resolved.engine.url(resolved.query.ifEmpty { query }))
                onDismiss()
            }
        }
    }
    val move: (Int) -> Unit = { delta ->
        if (targets.isNotEmpty()) onHighlight((highlighted + delta).mod(targets.size))
    }
    // Keep the keyboard-selected row visible.
    LaunchedEffect(highlighted, rows) {
        val rowIndex = rows.indexOfFirst { row ->
            when (row) {
                is SheetRow.Single -> row.index == highlighted
                is SheetRow.Recent -> row.index == highlighted
                is SheetRow.Apps -> highlighted >= row.firstIndex && highlighted < row.firstIndex + row.apps.size
                is SheetRow.Header -> false
            }
        }
        if (rowIndex > 0) {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.none { it.index == rowIndex }) listState.animateScrollToItem((rowIndex - 1).coerceAtLeast(0))
        }
    }

    // Edge-to-edge: the window is not resized (decorFitsSystemWindows=false), the IME inset is delivered
    // (adjustResize) and the keyboard is handled here.
    // Top: status bar. Bottom: keyboard or navigation bar, whichever is taller, so the results list
    // shrinks and scrolls above the keyboard.
    val insets = WindowInsets.statusBars.union(WindowInsets.ime).union(WindowInsets.navigationBars)
    Box(modifier = Modifier.fillMaxSize().windowInsetsPadding(insets)) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .padding(top = 10.dp, bottom = 10.dp, start = 12.dp, end = 12.dp)
                .then(if (wide) Modifier.widthIn(max = 640.dp) else Modifier.fillMaxWidth())
                // Only the sheet content slides (a few dp); the surface behind it only fades.
                .animateEnterExit(
                    enter = slideInVertically(tween(180)) { -it / 14 } + fadeIn(tween(180)),
                    exit = slideOutVertically(tween(110)) { -it / 14 } + fadeOut(tween(110)),
                ),
        ) {
            SearchField(
                query = query,
                onQueryChange = onQueryChange,
                placeholder = searchPlaceholder(settings),
                showLens = settings.webSearchOn(),
                onSearch = activateHighlighted,
                onMove = move,
                onDismiss = onDismiss,
                onLens = {
                    SearchActions.visualSearch(context, query)
                    onDismiss()
                },
            )
            if (rows.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FoldOsSurface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        // Swallow taps so they do not fall through to the dismiss area behind.
                        .pointerInput(Unit) { detectTapGestures { } },
                    shape = RoundedCornerShape(tokens.radius),
                ) {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(vertical = 8.dp),
                    ) {
                        for ((rowIndex, row) in rows.withIndex()) {
                            when (row) {
                                is SheetRow.Header -> item(key = "h$rowIndex") { SearchSectionHeader(row.title) }
                                is SheetRow.Apps -> item(key = "apps$rowIndex") {
                                    val local = highlighted - row.firstIndex
                                    SearchAppsRow(
                                        apps = row.apps,
                                        highlightedIndex = if (local in row.apps.indices) local else null,
                                        onActivate = onActivate,
                                    )
                                }
                                is SheetRow.Single -> item(key = row.result.key + rowIndex) {
                                    SearchResultRow(
                                        result = row.result,
                                        highlighted = row.index == highlighted,
                                        onActivate = { onActivate(row.result) },
                                    )
                                }
                                is SheetRow.Recent -> item(key = "r" + row.query + rowIndex) {
                                    SearchRecentRow(
                                        query = row.query,
                                        highlighted = row.index == highlighted,
                                        onActivate = { onRecent(row.query) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** The search pill (mock-up `.srch`, 52dp, radius 26) as a focused text field. */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun SearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    showLens: Boolean,
    onSearch: () -> Unit,
    onMove: (Int) -> Unit,
    onDismiss: () -> Unit,
    onLens: () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val shape = RoundedCornerShape(26.dp)
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // Keep the caret at the end when the query is set from outside (type-to-search seed, recent query, clear).
    var typed by remember { mutableStateOf(TextFieldValue(query, TextRange(query.length))) }
    val fieldValue = if (typed.text == query) typed else TextFieldValue(query, TextRange(query.length))
    val setFieldValue: (TextFieldValue) -> Unit = { typed = it }
    LaunchedEffect(Unit) {
        // Let the enter animation start before the IME pushes the layout around.
        delay(60)
        runCatching { focusRequester.requestFocus() }
        keyboard?.show()
    }
    TextField(
        value = fieldValue,
        onValueChange = { next ->
            setFieldValue(next)
            if (next.text != query) onQueryChange(next.text)
        },
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, tokens.surfaceBorder, shape)
            .focusRequester(focusRequester)
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.DirectionDown, Key.Tab -> {
                        onMove(1)
                        true
                    }
                    Key.DirectionUp -> {
                        onMove(-1)
                        true
                    }
                    Key.Enter, Key.NumPadEnter -> {
                        onSearch()
                        true
                    }
                    Key.Escape -> {
                        onDismiss()
                        true
                    }
                    else -> false
                }
            },
        placeholder = {
            Text(text = placeholder, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        leadingIcon = {
            Icon(imageVector = Icons.Rounded.Search, contentDescription = null)
        },
        trailingIcon = {
            Row {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(imageVector = Icons.Rounded.Close, contentDescription = "Clear search")
                    }
                }
                if (showLens) {
                    IconButton(onClick = onLens) {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusWeak,
                            contentDescription = "Visual search",
                            tint = tokens.accent,
                        )
                    }
                }
            }
        },
        singleLine = true,
        shape = shape,
        textStyle = MaterialTheme.typography.bodyLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
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
