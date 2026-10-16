package com.foldos.launcher.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.foldos.launcher.a11y.a11yGreyscale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.core.model.ColorSchemeConfig
import com.foldos.launcher.core.model.ColorSchemePreset
import com.foldos.launcher.core.model.ColorSchemes
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ThemeId
import com.foldos.launcher.theme.FoldOsTheme
import com.foldos.launcher.theme.LocalFoldOsTokens
import com.foldos.launcher.theme.Wallpaper
import kotlinx.coroutines.launch

/** The four themes in mock-up order with their labels. */
internal val ThemeQuickOptions: List<Pair<ThemeId, String>> = listOf(
    ThemeId.MATERIAL to "Material You",
    ThemeId.GLASS to "Dark Glass",
    ThemeId.ONEUI to "One UI",
    ThemeId.BOLD to "Bold",
)

/** Default corner radius per theme (mock-up `RAD`), applied when the theme changes. */
internal fun editDefaultRadiusDp(theme: ThemeId): Int = when (theme) {
    ThemeId.MATERIAL -> 28
    ThemeId.GLASS -> 22
    ThemeId.ONEUI -> 26
    ThemeId.BOLD -> 6
}

/**
 * Edit mode > Themes: tap one of the four themes (each shown as a live mini home preview in that
 * theme) and one color scheme (Theme colors + the six presets). Every tap writes straight to
 * `LauncherSettings` through `LocalAppGraph.current.settings`, so the launcher behind the sheet
 * restyles immediately; the full options stay in Settings > Look.
 *
 * @param settings the live settings, used for the selected state and as the base of each preview.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeQuickSheet(
    visible: Boolean,
    settings: LauncherSettings,
    onDismiss: () -> Unit,
) {
    if (!visible) return
    val graph = LocalAppGraph.current
    val update: ((LauncherSettings) -> LauncherSettings) -> Unit = { transform ->
        graph.appScope.launch { graph.settings.update(transform) }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, modifier = Modifier.a11yGreyscale()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(text = "Theme", style = MaterialTheme.typography.titleLarge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                ThemeQuickOptions.forEach { (theme, label) ->
                    ThemeQuickCard(
                        theme = theme,
                        label = label,
                        settings = settings,
                        selected = settings.theme == theme,
                        onClick = {
                            update { it.copy(theme = theme, radiusDp = editDefaultRadiusDp(theme)) }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(text = "Color scheme", style = MaterialTheme.typography.titleMedium)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                SchemeSwatch(
                    label = "Theme colors",
                    preset = null,
                    selected = settings.colors.scheme == ColorSchemeConfig.SCHEME_THEME,
                    onClick = { update { it.copy(colors = it.colors.copy(scheme = ColorSchemeConfig.SCHEME_THEME)) } },
                )
                ColorSchemes.presets.forEach { preset ->
                    SchemeSwatch(
                        label = preset.name,
                        preset = preset,
                        selected = settings.colors.scheme == preset.id,
                        onClick = {
                            update { current ->
                                current.copy(
                                    colors = current.colors.copy(
                                        scheme = preset.id,
                                        accent = preset.accent,
                                        wall1 = preset.wall1,
                                        wall2 = preset.wall2,
                                    ),
                                )
                            }
                        },
                    )
                }
            }
            Text(
                text = "Custom colors, dark/light/auto and icon shape are in Settings > Look.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A mini home screen rendered in [theme] (wallpaper, a highlight card, a surface card, icon dots). */
@Composable
private fun ThemeQuickCard(
    theme: ThemeId,
    label: String,
    settings: LauncherSettings,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val outer = LocalFoldOsTokens.current
    val frame = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(frame)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) outer.accent else outer.surfaceBorder,
                    shape = frame,
                ),
        ) {
            FoldOsTheme(settings = settings.copy(theme = theme, radiusDp = editDefaultRadiusDp(theme))) {
                val tokens = LocalFoldOsTokens.current
                val card = RoundedCornerShape((tokens.radius / 2.5f).coerceAtLeast(3.dp))
                Box(Modifier.fillMaxSize()) {
                    Wallpaper()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1.2f)
                                .clip(card)
                                .background(tokens.highlight, card),
                        )
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .weight(1f)
                                .clip(card)
                                .background(tokens.surface, card)
                                .border(1.dp, tokens.surfaceBorder, card),
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().height(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            val dot = RoundedCornerShape(percent = (tokens.iconCornerFraction * 100f).toInt().coerceIn(0, 50))
                            repeat(4) { index ->
                                Box(
                                    Modifier
                                        .fillMaxHeight()
                                        .width(14.dp)
                                        .clip(dot)
                                        .background(tokens.accent.copy(alpha = 0.45f + index * 0.15f), dot),
                                )
                            }
                        }
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            if (selected) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = outer.accent,
                    modifier = Modifier.size(14.dp),
                )
            }
            Text(
                text = label,
                fontSize = 12.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                color = if (selected) outer.accent else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * A 48dp disc split into the scheme's accent / wallpaper 1 / wallpaper 2 stripes with its name
 * under it. A null [preset] is "Theme colors", drawn from the current theme tokens.
 */
@Composable
private fun SchemeSwatch(
    label: String,
    preset: ColorSchemePreset?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val stripes: List<Color> = if (preset != null) {
        listOf(Color(preset.accent), Color(preset.wall1), Color(preset.wall2))
    } else {
        listOf(tokens.accent, tokens.highlight, tokens.surface)
    }
    Column(
        modifier = Modifier
            .width(64.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) tokens.accent else MaterialTheme.colorScheme.outline,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Row(Modifier.fillMaxSize()) {
                stripes.forEach { color ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(color),
                    )
                }
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(tokens.accent, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        tint = tokens.onAccent,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
        Text(
            text = label,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            color = if (selected) tokens.accent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
