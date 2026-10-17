package com.foldos.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.a11yHeading
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.Appearance
import com.foldos.launcher.core.model.ColorSchemeConfig
import com.foldos.launcher.core.model.ColorSchemes
import com.foldos.launcher.core.model.DockPresets
import com.foldos.launcher.core.model.DrawerStyle
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenMode
import com.foldos.launcher.core.model.ThemeId
import com.foldos.launcher.core.model.matchingPresetId
import com.foldos.launcher.core.repo.IconShape
import com.foldos.launcher.icons.IconPackPicker
import com.foldos.launcher.home.AppLabel
import com.foldos.launcher.home.AppLabelStyle
import com.foldos.launcher.home.StarterLayouts
import com.foldos.launcher.icons.IconShapes
import com.foldos.launcher.onboarding.AppearanceSample
import com.foldos.launcher.onboarding.CustomColorSwatch
import com.foldos.launcher.onboarding.DrawerSample
import com.foldos.launcher.onboarding.GroupingKind
import com.foldos.launcher.onboarding.IconShapeSwatch
import com.foldos.launcher.onboarding.Presets
import com.foldos.launcher.onboarding.SampleDockPreview
import com.foldos.launcher.onboarding.SampleHomePreview
import com.foldos.launcher.onboarding.ScreenModeDiagram
import com.foldos.launcher.onboarding.SplitColorSwatch
import com.foldos.launcher.onboarding.ThemeColorsSwatch
import com.foldos.launcher.onboarding.WIZARD_DOCKS
import com.foldos.launcher.onboarding.WidgetGroupingSample
import com.foldos.launcher.onboarding.colorsForCustom
import com.foldos.launcher.onboarding.colorsForFamily
import com.foldos.launcher.theme.isDarkAppearance
import kotlinx.coroutines.launch

/**
 * Settings, redesigned as "pick from samples" (Fix round 1.1). One scrolling page with a section per
 * decision; every section is a row or grid of live preview cards, the current choice outlined, and a tap
 * applies it at once. There are no switches, dropdowns, sliders or hex fields here: all of that lives
 * behind the single "More settings" entry at the bottom (the detailed pages).
 *
 * Settings search is a small icon in the top bar; it opens a search field and replaces the sections with
 * matching rows of every detailed page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsHomePage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    query: String,
    onQueryChange: (String) -> Unit,
    advanced: Boolean,
    onAdvancedChange: (Boolean) -> Unit,
    onOpen: (route: String) -> Unit,
    onClose: () -> Unit,
) {
    var searching by rememberSaveable { mutableStateOf(query.isNotEmpty()) }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(searching) {
        if (searching) runCatching { focusRequester.requestFocus() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (searching) {
                        TextField(
                            value = query,
                            onValueChange = onQueryChange,
                            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                            placeholder = { Text(text = "Search settings") },
                            singleLine = true,
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                            ),
                        )
                    } else {
                        Text(text = "Settings", modifier = Modifier.a11yHeading())
                    }
                },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (searching) {
                                searching = false
                                onQueryChange("")
                            } else {
                                onClose()
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (searching) Icons.AutoMirrored.Filled.ArrowBack else Icons.Filled.Close,
                            contentDescription = if (searching) "Close search" else "Close",
                        )
                    }
                },
                actions = {
                    if (!searching) {
                        IconButton(onClick = { searching = true }) {
                            Icon(imageVector = Icons.Filled.Search, contentDescription = "Search settings")
                        }
                    } else if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(imageVector = Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        if (query.isNotBlank()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState()),
            ) {
                SearchResults(query = query, advanced = advanced, onAdvancedChange = onAdvancedChange, onOpen = onOpen)
            }
        } else {
            SettingsSamples(
                settings = settings,
                update = update,
                onOpen = onOpen,
                contentPadding = innerPadding,
            )
        }
    }
}

@Composable
private fun SettingsSamples(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onOpen: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val isDark = isDarkAppearance(settings.appearance)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "screens") { ScreensSection(settings, update) }
        item(key = "starter-layouts") { HomeStyleSection(settings, update) }
        item(key = "look") { LookSection(settings, update) }
        item(key = "colors") { ColorsSection(settings, update, isDark) }
        item(key = "dock") { DockSection(settings, update, onOpen) }
        item(key = "drawer") { DrawerSection(settings, update) }
        item(key = "widgets") { WidgetsSection(settings, update) }
        item(key = "icons") { IconsSection(settings, update) }
        item(key = "more") {
            SampleSection(title = "Everything else") {
                SampleLinkRow(
                    title = "More settings",
                    subtitle = "Gestures, search, hidden apps, mail, AI, backup, accessibility and every other option",
                    onClick = { onOpen(SettingsRoutes.MORE) },
                )
            }
        }
        item(key = "end") { SampleGap(height = 24.dp) }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Screens                                                                                     */
/* ------------------------------------------------------------------------------------------ */

private val SCREEN_MODES = listOf(
    ScreenMode.SAME to "Same",
    ScreenMode.DIFFERENT to "Different",
    ScreenMode.CONTINUITY to "Continuity",
)

@Composable
private fun ScreensSection(settings: LauncherSettings, update: SettingsUpdater) {
    val colors = MaterialTheme.colorScheme
    SampleSection(
        title = "Screens",
        subtitle = "Same layout scaled up, a separate layout inside, or one page that grows when you unfold.",
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            SCREEN_MODES.forEach { (mode, name) ->
                SampleCard(
                    label = name,
                    selected = settings.screenMode == mode,
                    onClick = { update { it.copy(screenMode = mode) } },
                    modifier = Modifier.weight(1f),
                    previewBackground = colors.surface,
                ) {
                    ScreenModeDiagram(
                        mode = mode,
                        ink = colors.onSurfaceVariant,
                        panel = colors.surfaceContainerHighest,
                        accent = colors.primary,
                        modifier = Modifier.padding(6.dp),
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Home style                                                                                  */
/* ------------------------------------------------------------------------------------------ */

/**
 * Round 2.0: the four starter layouts. A tap asks "Replace first page / Add as new page" for the
 * screen Settings is open on and seeds that page with ordinary draggable content ([StarterLayouts]);
 * nothing is "selected" afterwards, every page is the same free canvas.
 */
@Composable
private fun HomeStyleSection(settings: LauncherSettings, update: SettingsUpdater) {
    val screen = rememberSettingsScreen(settings)
    val applyStarter = rememberStarterApplier()
    var pending by rememberSaveable { mutableStateOf<String?>(null) }
    val pendingStarter = pending?.let { name -> HomeStyle.entries.firstOrNull { it.name == name } }
    SampleSection(
        title = "Starter layouts",
        subtitle = "Tap one to fill the ${screenWord(screen)} screen's first page, or add it as a new page. " +
            "Afterwards drag, resize and remove anything: long-press empty space to add apps, widgets and cards.",
    ) {
        SampleGrid(items = StarterLayouts.all) { (style, name) ->
            SampleCard(
                label = name,
                selected = false,
                onClick = { pending = style.name },
                description = StarterLayouts.description(style),
            ) {
                SampleHomePreview(
                    style = style,
                    theme = settings.theme,
                    colors = settings.colors,
                    modifier = Modifier.fillMaxWidth().aspectRatio(PreviewRatio),
                    base = settings,
                )
            }
        }
        LabelSizeCards(settings, update)
    }
    if (pendingStarter != null) {
        StarterApplyDialog(
            starter = pendingStarter,
            screen = screen,
            onReplaceFirst = { applyStarter(screen, pendingStarter, true) },
            onAddPage = { applyStarter(screen, pendingStarter, false) },
            onDismiss = { pending = null },
        )
    }
}

/** Label size choices: stored value, card title. The sizes themselves live in [AppLabelStyle]. */
private val LABEL_SIZES = listOf(
    AppLabelStyle.SIZE_SMALL to "Small",
    AppLabelStyle.SIZE_MEDIUM to "Medium",
    AppLabelStyle.SIZE_LARGE to "Large",
)

/**
 * App label size under icons (home, dock, drawer, folders): three preview cards that draw "Messages"
 * and "Amazon Photos" with the shared [AppLabel] at 11 / 12 / 14sp in a 92dp cell (the cover grid's
 * cell width minus the label inset). A hint shows while labels are switched off (they stay hidden
 * until App labels is turned on, except in Easy mode where they are always shown).
 */
@Composable
private fun LabelSizeCards(settings: LauncherSettings, update: SettingsUpdater) {
    val colors = MaterialTheme.colorScheme
    Text(
        text = "Label size",
        style = MaterialTheme.typography.titleMedium,
        color = colors.onSurface,
        modifier = Modifier.padding(top = 6.dp).a11yHeading(),
    )
    Text(
        text = if (settings.effectiveLabels) {
            "App names under icons. Long names wrap to two lines."
        } else {
            "App names are hidden right now (More settings > Home > App labels); the size applies once they show."
        },
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
    ) {
        LABEL_SIZES.forEach { (key, name) ->
            SampleCard(
                label = name,
                selected = settings.labelSize == key,
                onClick = { update { it.copy(labelSize = key) } },
                modifier = Modifier.weight(1f),
                previewBackground = colors.surface,
            ) {
                LabelSizePreview(
                    sizeSp = AppLabelStyle.sizeSpFor(key),
                    ink = colors.onSurface,
                    accent = colors.primary,
                )
            }
        }
    }
}

/** Two sample cells ("Messages", "Amazon Photos") drawn with [AppLabel] at [sizeSp]. */
@Composable
private fun LabelSizePreview(sizeSp: Float, ink: Color, accent: Color) {
    Column(
        modifier = Modifier.widthIn(max = 92.dp).fillMaxWidth().padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("Messages", "Amazon Photos").forEach { sample ->
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(accent.copy(alpha = 0.85f)),
                )
                AppLabel(
                    text = sample,
                    sizeSp = sizeSp,
                    color = ink,
                    onWallpaper = false,
                    reserveLines = false,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Look                                                                                        */
/* ------------------------------------------------------------------------------------------ */

private val THEMES = listOf(
    ThemeId.MATERIAL to "Material You",
    ThemeId.GLASS to "Dark Glass",
    ThemeId.ONEUI to "One UI",
    ThemeId.BOLD to "Bold",
)

@Composable
private fun LookSection(settings: LauncherSettings, update: SettingsUpdater) {
    SampleSection(
        title = "Look",
        subtitle = "Each look has a light and a dark version; the previews show the current one.",
    ) {
        SampleGrid(items = THEMES) { (theme, name) ->
            val radius = Presets.radiusFor(theme)
            SampleCard(
                label = name,
                selected = settings.theme == theme,
                onClick = { update { it.copy(theme = theme, radiusDp = radius) } },
            ) {
                SampleHomePreview(
                    style = HomeStyle.DASHBOARD,
                    theme = theme,
                    colors = settings.colors,
                    modifier = Modifier.fillMaxWidth().aspectRatio(PreviewRatio),
                    base = settings,
                    radiusDp = radius,
                )
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Colors                                                                                      */
/* ------------------------------------------------------------------------------------------ */

private class ColorTileSpec(
    val name: String,
    val selected: Boolean,
    val onClick: () -> Unit,
    val swatch: @Composable () -> Unit,
)

private val APPEARANCES = listOf(
    Appearance.DARK to "Dark",
    Appearance.LIGHT to "Light",
    Appearance.AUTO to "Auto",
)

@Composable
private fun ColorsSection(settings: LauncherSettings, update: SettingsUpdater, isDark: Boolean) {
    var showCustom by rememberSaveable { mutableStateOf(false) }
    val scheme = settings.colors.scheme
    val selectedFamily = ColorSchemes.familyOfScheme(scheme)?.id

    SampleSection(
        title = "Colors",
        subtitle = "Every color has a light and a dark version. Tap one: the preview and your home follow, in " +
            (if (isDark) "dark" else "light") + " mode.",
    ) {
        // Live preview of the tapped color, in the current mode.
        // Capped at 200-220dp wide (about 370dp tall): on the inner screen it used to stretch to 60% of the
        // 840dp section and became a giant portrait card.
        androidx.compose.foundation.layout.BoxWithConstraints(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val previewWidth = minOf(maxWidth * 0.6f, if (maxWidth >= 600.dp) 200.dp else 220.dp)
            Box(
                modifier = Modifier
                    .width(previewWidth)
                    .clip(RoundedCornerShape(18.dp)),
            ) {
                SampleHomePreview(
                    style = HomeStyle.DASHBOARD,
                    theme = settings.theme,
                    colors = settings.colors,
                    modifier = Modifier.fillMaxWidth().aspectRatio(PreviewRatio),
                    base = settings,
                )
            }
        }

        // Appearance: three preview cards.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        ) {
            APPEARANCES.forEach { (appearance, name) ->
                SampleCard(
                    label = name,
                    selected = settings.appearance == appearance,
                    onClick = { update { it.copy(appearance = appearance) } },
                    modifier = Modifier.weight(1f),
                ) {
                    AppearanceSample(
                        appearance = appearance,
                        base = settings,
                        modifier = Modifier.fillMaxWidth().aspectRatio(PreviewRatio),
                    )
                }
            }
        }

        // One split (light / dark) tile per primary color, plus Theme colors and Custom.
        val tiles = ArrayList<ColorTileSpec>(ColorSchemes.families.size + 2)
        tiles.add(
            ColorTileSpec(
                name = "Default",
                selected = scheme == ColorSchemeConfig.SCHEME_THEME,
                onClick = { update { it.copy(colors = it.colors.copy(scheme = ColorSchemeConfig.SCHEME_THEME)) } },
                swatch = { ThemeColorsSwatch(theme = settings.theme) },
            ),
        )
        ColorSchemes.families.forEach { family ->
            tiles.add(
                ColorTileSpec(
                    name = family.name,
                    selected = selectedFamily == family.id,
                    onClick = { update { it.copy(colors = colorsForFamily(it.colors, family, isDark)) } },
                    swatch = { SplitColorSwatch(family = family) },
                ),
            )
        }
        tiles.add(
            ColorTileSpec(
                name = "Custom",
                selected = scheme == ColorSchemeConfig.SCHEME_CUSTOM,
                onClick = {
                    update { it.copy(colors = colorsForCustom(it.colors, isDark)) }
                    showCustom = true
                },
                swatch = { CustomColorSwatch() },
            ),
        )
        SampleGrid(items = tiles, columns = 4, wideColumns = 7) { tile ->
            ColorTile(
                name = tile.name,
                selected = tile.selected,
                onClick = tile.onClick,
                modifier = Modifier.fillMaxWidth(),
                swatch = tile.swatch,
            )
        }
        if (scheme == ColorSchemeConfig.SCHEME_CUSTOM) {
            SampleLinkRow(
                title = "Edit custom colors",
                subtitle = "Accent and the two wallpaper colors",
                onClick = { showCustom = true },
            )
        }
    }

    if (showCustom) {
        CustomColorsSheet(
            colors = settings.colors,
            onChange = { transform -> update { it.copy(colors = transform(it.colors)) } },
            onClose = { showCustom = false },
        )
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Dock                                                                                        */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun DockSection(settings: LauncherSettings, update: SettingsUpdater, onOpen: (String) -> Unit) {
    val starters = remember { WIZARD_DOCKS.mapNotNull { DockPresets.byId(it) } }
    val current = settings.dockCover.matchingPresetId()
    SampleSection(
        title = "Dock",
        subtitle = if (current == null) {
            "Your dock is a custom mix. Pick a style to start over, or customize it."
        } else {
            "Start from a style; More docks has all 20."
        },
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(starters, key = { it.id }) { preset ->
                SampleCard(
                    label = preset.name,
                    selected = current == preset.id,
                    onClick = { update { it.copy(dockCover = preset.config, dockInner = preset.config) } },
                    modifier = Modifier.width(230.dp),
                ) {
                    SampleDockPreview(
                        dock = preset.config,
                        modifier = Modifier.fillMaxWidth().aspectRatio(DockStripRatio),
                        base = settings,
                    )
                }
            }
        }
        SampleLinkRow(
            title = "More docks",
            subtitle = "All 20 styles as previews",
            onClick = { onOpen(SettingsRoutes.DOCK_GALLERY) },
        )
        SampleLinkRow(
            title = "Customize",
            subtitle = "Background, position, layout and add-ons, picked with chips",
            onClick = { onOpen(SettingsRoutes.DOCK) },
        )
    }
}

/* ------------------------------------------------------------------------------------------ */
/* App drawer                                                                                  */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun DrawerSection(settings: LauncherSettings, update: SettingsUpdater) {
    SampleSection(
        title = "App drawer",
        subtitle = "Apps grouped into folders; drag any app onto your home screen or dock.",
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 520.dp),
        ) {
            listOf(true to "Pop-up", false to "Full screen").forEach { (popup, name) ->
                val style = if (popup) DrawerStyle.POPUP else DrawerStyle.FULL
                SampleCard(
                    label = name,
                    selected = settings.drawerStyle == style,
                    onClick = { update { it.copy(drawerStyle = style) } },
                    modifier = Modifier.weight(1f),
                ) {
                    DrawerSample(
                        popup = popup,
                        base = settings,
                        modifier = Modifier.fillMaxWidth().aspectRatio(PreviewRatio),
                    )
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Widget picker grouping                                                                      */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun WidgetsSection(settings: LauncherSettings, update: SettingsUpdater) {
    val colors = MaterialTheme.colorScheme
    val options = listOf(
        Triple(LauncherSettings.WP_GROUP_CATEGORY, "By category", GroupingKind.CATEGORY),
        Triple(LauncherSettings.WP_GROUP_APP, "By app", GroupingKind.APP),
        Triple(LauncherSettings.WP_GROUP_RECENT, "Recent first", GroupingKind.RECENT),
    )
    SampleSection(
        title = "Widget picker",
        subtitle = "How \"Add a widget\" is organized.",
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
        ) {
            options.forEach { (group, name, kind) ->
                SampleCard(
                    label = name,
                    selected = settings.wpGroup == group,
                    onClick = { update { it.copy(wpGroup = group) } },
                    modifier = Modifier.weight(1f),
                    previewBackground = colors.surface,
                ) {
                    WidgetGroupingSample(kind = kind, ink = colors.onSurface, accent = colors.primary)
                }
            }
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Icons                                                                                       */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun IconsSection(settings: LauncherSettings, update: SettingsUpdater) {
    val context = LocalContext.current
    val graph = remember(context) { FoldOsApplication.from(context).graph }
    val colors = MaterialTheme.colorScheme
    var showPackPicker by rememberSaveable { mutableStateOf(false) }

    SampleSection(
        title = "Icons",
        subtitle = "The shape of every app icon, and an optional icon pack.",
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 2.dp),
        ) {
            items(IconShape.entries.toList(), key = { it.name }) { shape ->
                SampleCard(
                    label = IconShapes.displayName(shape),
                    selected = settings.iconShape == shape,
                    // setShape persists to settings and refreshes the icon caches.
                    onClick = { graph.appScope.launch { graph.icons.setShape(shape) } },
                    modifier = Modifier.width(96.dp),
                    previewBackground = colors.surface,
                ) {
                    Box(modifier = Modifier.padding(vertical = 14.dp), contentAlignment = Alignment.Center) {
                        IconShapeSwatch(shape = shape, color = colors.primary)
                    }
                }
            }
        }
        SampleLinkRow(
            title = "Icon pack",
            subtitle = settings.iconPack?.substringAfterLast('.') ?: "System icons",
            onClick = { showPackPicker = true },
        )
    }
    if (showPackPicker) IconPackPicker(onClose = { showPackPicker = false })
}
