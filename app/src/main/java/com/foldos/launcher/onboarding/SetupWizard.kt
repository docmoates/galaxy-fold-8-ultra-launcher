package com.foldos.launcher.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Devices
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.model.ColorSchemeConfig
import com.foldos.launcher.core.model.ColorSchemes
import com.foldos.launcher.core.model.DockPresets
import com.foldos.launcher.core.model.HomeStyle
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenMode
import com.foldos.launcher.core.model.ThemeId
import com.foldos.launcher.core.model.matchingPresetId
import com.foldos.launcher.theme.LocalFoldOsTokens
import com.foldos.launcher.theme.Wallpaper
import com.foldos.launcher.theme.tokensFor

/** True until the first-run wizard has finished or been skipped (`LauncherSettings.wizardDone`). */
fun shouldShowWizard(settings: LauncherSettings): Boolean = !settings.wizardDone

/** Number of wizard steps. */
const val WIZARD_STEPS: Int = 5

private val STEP_TITLES = listOf(
    "How should your two screens work?",
    "Pick a home style",
    "Pick a look",
    "Pick your colors",
    "Pick a dock",
)

/** The six dock presets the wizard offers (mock-up wizard step 5); the other 14 live in Settings > Dock. */
private val WIZARD_DOCKS = listOf("pill", "shelf", "search", "center", "media", "text")

/**
 * First-run setup wizard (PLAN.md 2.9 / 5.3, mock-up `wizard()`): five steps, every question
 * answered by tapping a live preview. Must be composed inside `FoldOsTheme(settings)`; it draws
 * the wallpaper itself and fills its parent.
 *
 * Every tap is applied at once through [onUpdate], so the previews (and the launcher behind the
 * wizard, if it is shown over it) follow each choice. The wizard also keeps an optimistic copy of
 * the settings so a tap is reflected before the DataStore round trip completes. Finish and Skip
 * both set `wizardDone = true` through [onUpdate] and then call [onFinish].
 *
 * @param settings the current launcher settings (collected from `SettingsRepository.settings`).
 * @param onUpdate applies a transform to the persisted settings (`repo.update(transform)`).
 * @param onFinish called after the final step or Skip; the host closes the wizard.
 */
@Composable
fun SetupWizard(
    settings: LauncherSettings,
    onUpdate: ((LauncherSettings) -> LauncherSettings) -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var step by rememberSaveable { mutableIntStateOf(1) }
    var local by remember(settings) { mutableStateOf(settings) }
    val change: ((LauncherSettings) -> LauncherSettings) -> Unit = { transform ->
        local = transform(local)
        onUpdate(transform)
    }
    val finish: () -> Unit = {
        change { it.copy(wizardDone = true) }
        onFinish()
    }

    BackHandler(enabled = step > 1) { step -= 1 }

    val tokens = LocalFoldOsTokens.current
    Box(modifier = modifier.fillMaxSize()) {
        Wallpaper()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WizardProgress(step = step, total = WIZARD_STEPS)
            Text(
                text = "Step $step of $WIZARD_STEPS".uppercase(),
                color = tokens.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
            )
            Text(
                text = STEP_TITLES[step - 1],
                color = tokens.onSurface,
                fontSize = 30.sp,
                fontWeight = FontWeight.Medium,
                lineHeight = 34.sp,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (step) {
                    1 -> ScreensStep(local, change)
                    2 -> HomeStyleStep(local, change)
                    3 -> LookStep(local, change)
                    4 -> ColorsStep(local, change)
                    else -> DockStep(local, change)
                }
            }
            WizardNav(
                leftLabel = if (step > 1) "Back" else "Skip",
                onLeft = { if (step > 1) step -= 1 else finish() },
                rightLabel = if (step < WIZARD_STEPS) "Continue" else "Finish",
                onRight = { if (step < WIZARD_STEPS) step += 1 else finish() },
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Step 1: Screens                                                                             */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun ScreensStep(local: LauncherSettings, change: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    WizardSubtitle("You can change this at any time in Settings › Screens.")
    WizardOption(
        icon = Icons.Rounded.Smartphone,
        title = "Same layout",
        subtitle = "One home screen, scaled up when you unfold.",
        selected = local.screenMode == ScreenMode.SAME,
        onClick = { change { it.copy(screenMode = ScreenMode.SAME) } },
    )
    WizardOption(
        icon = Icons.Rounded.Devices,
        title = "Different layouts",
        subtitle = "Quick phone setup outside, a productivity desk inside.",
        selected = local.screenMode == ScreenMode.DIFFERENT,
        onClick = { change { it.copy(screenMode = ScreenMode.DIFFERENT) } },
    )
    WizardOption(
        icon = Icons.Rounded.Fullscreen,
        title = "Continuity",
        subtitle = "Unfolding expands the page you were on.",
        selected = local.screenMode == ScreenMode.CONTINUITY,
        onClick = { change { it.copy(screenMode = ScreenMode.CONTINUITY) } },
    )
}

/* ------------------------------------------------------------------------------------------ */
/* Step 2: Home style                                                                          */
/* ------------------------------------------------------------------------------------------ */

private val STYLE_NAMES = listOf(
    HomeStyle.GRID to "Classic grid",
    HomeStyle.DASHBOARD to "Widget dashboard",
    HomeStyle.LIST to "Minimal list",
    HomeStyle.HYBRID to "Hybrid panels",
)

@Composable
private fun HomeStyleStep(local: LauncherSettings, change: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    WizardSubtitle("Tap a preview. You can mix styles page by page later.")
    TwoColumnGrid(STYLE_NAMES) { (style, name) ->
        WizardPreviewCard(
            name = name,
            selected = local.styleCover == style,
            onClick = { change { it.copy(styleCover = style, styleInner = style) } },
        ) {
            SampleHomePreview(
                style = style,
                theme = local.theme,
                colors = local.colors,
                modifier = Modifier.fillMaxWidth().height(250.dp),
                base = local,
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Step 3: Look                                                                               */
/* ------------------------------------------------------------------------------------------ */

private val THEME_NAMES = listOf(
    ThemeId.MATERIAL to "Material You",
    ThemeId.GLASS to "Dark Glass",
    ThemeId.ONEUI to "One UI",
    ThemeId.BOLD to "Bold",
)

@Composable
private fun LookStep(local: LauncherSettings, change: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    WizardSubtitle("Each look has a light and a dark version.")
    TwoColumnGrid(THEME_NAMES) { (theme, name) ->
        val radius = Presets.radiusFor(theme)
        WizardPreviewCard(
            name = name,
            selected = local.theme == theme,
            onClick = { change { it.copy(theme = theme, radiusDp = radius) } },
        ) {
            SampleHomePreview(
                style = HomeStyle.DASHBOARD,
                theme = theme,
                colors = local.colors,
                modifier = Modifier.fillMaxWidth().height(250.dp),
                base = local,
                radiusDp = radius,
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Step 4: Colors                                                                             */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun ColorsStep(local: LauncherSettings, change: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    val tokens = LocalFoldOsTokens.current
    WizardSubtitle("Pick a palette or choose your own accent and wallpaper colors.")
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.78f)
                .height(380.dp),
        ) {
            SampleHomePreview(
                style = HomeStyle.DASHBOARD,
                theme = local.theme,
                colors = local.colors,
                modifier = Modifier.fillMaxSize(),
                base = local,
            )
        }
    }

    val scheme = local.colors.scheme
    val pick: (String) -> Unit = { id -> change { it.copy(colors = it.colors.copy(scheme = id)) } }

    // Theme colors: a swatch built from the theme's own palette so it is never a blank circle.
    val themeTokens = remember(local.theme, tokens.isDark) {
        tokensFor(local.theme, tokens.isDark, ColorSchemeConfig(), null)
    }
    val base = if (tokens.isDark) Color(0xFF0C0D12) else Color(0xFFEEF0F5)
    val themeBrush = remember(themeTokens, base) {
        thirds(themeTokens.accent, themeTokens.highlight.compositeOver(base), themeTokens.surface.compositeOver(base))
    }
    val customBrush = remember {
        Brush.sweepGradient(
            listOf(
                Color(0xFFF43F5E), Color(0xFFF59E0B), Color(0xFF22C55E),
                Color(0xFF0EA5E9), Color(0xFF8B5CF6), Color(0xFFF43F5E),
            ),
        )
    }

    val swatches = ArrayList<Swatch>(8)
    swatches.add(Swatch(ColorSchemeConfig.SCHEME_THEME, "Theme colors", themeBrush))
    ColorSchemes.presets.forEach { preset ->
        swatches.add(Swatch(preset.id, preset.name, thirds(Color(preset.accent), Color(preset.wall1), Color(preset.wall2))))
    }
    swatches.add(Swatch(ColorSchemeConfig.SCHEME_CUSTOM, "Custom", customBrush))

    swatches.chunked(4).forEach { rowItems ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            rowItems.forEach { swatch ->
                ColorSwatch(
                    name = swatch.name,
                    brush = swatch.brush,
                    selected = scheme == swatch.id,
                    onClick = { pick(swatch.id) },
                    modifier = Modifier.weight(1f),
                )
            }
            repeat(4 - rowItems.size) { Box(Modifier.weight(1f)) }
        }
    }

    if (scheme == ColorSchemeConfig.SCHEME_CUSTOM) {
        WizardGap(4)
        HexColorField(
            label = "Accent",
            value = local.colors.accent,
            onChange = { v -> change { it.copy(colors = it.colors.copy(accent = v)) } },
            modifier = Modifier.fillMaxWidth(),
        )
        HexColorField(
            label = "Wallpaper color 1",
            value = local.colors.wall1,
            onChange = { v -> change { it.copy(colors = it.colors.copy(wall1 = v)) } },
            modifier = Modifier.fillMaxWidth(),
        )
        HexColorField(
            label = "Wallpaper color 2",
            value = local.colors.wall2,
            onChange = { v -> change { it.copy(colors = it.colors.copy(wall2 = v)) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** One palette circle on the Colors step. */
private class Swatch(val id: String, val name: String, val brush: Brush)

/** CSS `conic-gradient(a 0 120deg, b 0 240deg, c 0)`: three hard-edged thirds. */
private fun thirds(a: Color, b: Color, c: Color): Brush = Brush.sweepGradient(
    0f to a, 0.3333f to a,
    0.3333f to b, 0.6667f to b,
    0.6667f to c, 1f to c,
)

/* ------------------------------------------------------------------------------------------ */
/* Step 5: Dock                                                                                */
/* ------------------------------------------------------------------------------------------ */

@Composable
private fun DockStep(local: LauncherSettings, change: ((LauncherSettings) -> LauncherSettings) -> Unit) {
    WizardSubtitle("Start from a style. You can mix dock features later in Settings › Dock.")
    val presets = remember { WIZARD_DOCKS.mapNotNull { DockPresets.byId(it) } }
    val current = local.dockCover.matchingPresetId()
    TwoColumnGrid(presets) { preset ->
        WizardPreviewCard(
            name = preset.name,
            selected = current == preset.id,
            onClick = { change { it.copy(dockCover = preset.config, dockInner = preset.config) } },
        ) {
            SampleDockPreview(
                dock = preset.config,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(PREVIEW_COVER_WIDTH.value / PREVIEW_DOCK_STRIP_HEIGHT.value),
                base = local,
            )
        }
    }
}

/* ------------------------------------------------------------------------------------------ */
/* Layout helper                                                                               */
/* ------------------------------------------------------------------------------------------ */

/** Mock-up `.wgrid`: two equal columns, 10dp gaps, rows added as needed. */
@Composable
private fun <T> TwoColumnGrid(items: List<T>, content: @Composable (T) -> Unit) {
    items.chunked(2).forEach { pair ->
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            pair.forEach { item ->
                Box(modifier = Modifier.weight(1f)) { content(item) }
            }
            if (pair.size == 1) Box(Modifier.weight(1f))
        }
    }
}
