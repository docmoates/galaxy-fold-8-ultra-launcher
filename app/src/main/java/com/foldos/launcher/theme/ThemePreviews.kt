package com.foldos.launcher.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.Appearance
import com.foldos.launcher.core.model.ColorSchemeConfig
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ThemeId

/** Sample screen used by every theme preview: wallpaper, a widget card, a highlight card, icons, a button. */
@Composable
private fun ThemePreviewContent(label: String) {
    val tokens = LocalFoldOsTokens.current
    Box(modifier = Modifier.fillMaxSize()) {
        Wallpaper()
        Column(
            modifier = Modifier.fillMaxSize().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleLarge,
                color = tokens.onSurface,
            )
            FoldOsSurface(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "Widget card",
                        fontWeight = FontWeight.SemiBold,
                        color = tokens.onSurface,
                    )
                    Text(
                        text = "surface, border, radius ${tokens.radius.value.toInt()}dp",
                        style = MaterialTheme.typography.bodyMedium,
                        color = tokens.onSurface.copy(alpha = 0.7f),
                    )
                }
            }
            FoldOsSurface(
                modifier = Modifier.fillMaxWidth(),
                color = tokens.highlight,
                borderColor = Color.Transparent,
                contentPadding = PaddingValues(16.dp),
            ) {
                Text(
                    text = "Highlight card",
                    fontWeight = FontWeight.SemiBold,
                    color = tokens.onHighlight,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val percent = (tokens.iconCornerFraction * 100f).toInt()
                repeat(4) { index ->
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(percent = percent))
                            .background(tokens.accent.copy(alpha = 0.35f + index * 0.2f)),
                    )
                }
            }
            Button(onClick = {}) {
                Text(text = "Accent button")
            }
        }
    }
}

@Composable
private fun ThemePreview(
    theme: ThemeId,
    appearance: Appearance,
    colors: ColorSchemeConfig = ColorSchemeConfig(),
) {
    val settings = LauncherSettings(theme = theme, appearance = appearance, colors = colors)
    FoldOsTheme(settings = settings) {
        val mode = if (appearance == Appearance.DARK) "dark" else "light"
        val scheme = if (colors.scheme == ColorSchemeConfig.SCHEME_THEME) "" else " / ${colors.scheme}"
        ThemePreviewContent(label = "${theme.name.lowercase()} $mode$scheme")
    }
}

private const val PREVIEW_W = 360
private const val PREVIEW_H = 520

@Preview(name = "Material dark", widthDp = PREVIEW_W, heightDp = PREVIEW_H)
@Composable
private fun PreviewMaterialDark() {
    ThemePreview(ThemeId.MATERIAL, Appearance.DARK)
}

@Preview(name = "Material light", widthDp = PREVIEW_W, heightDp = PREVIEW_H)
@Composable
private fun PreviewMaterialLight() {
    ThemePreview(ThemeId.MATERIAL, Appearance.LIGHT)
}

@Preview(name = "Glass dark", widthDp = PREVIEW_W, heightDp = PREVIEW_H)
@Composable
private fun PreviewGlassDark() {
    ThemePreview(ThemeId.GLASS, Appearance.DARK)
}

@Preview(name = "Glass light", widthDp = PREVIEW_W, heightDp = PREVIEW_H)
@Composable
private fun PreviewGlassLight() {
    ThemePreview(ThemeId.GLASS, Appearance.LIGHT)
}

@Preview(name = "One UI dark", widthDp = PREVIEW_W, heightDp = PREVIEW_H)
@Composable
private fun PreviewOneUiDark() {
    ThemePreview(ThemeId.ONEUI, Appearance.DARK)
}

@Preview(name = "One UI light", widthDp = PREVIEW_W, heightDp = PREVIEW_H)
@Composable
private fun PreviewOneUiLight() {
    ThemePreview(ThemeId.ONEUI, Appearance.LIGHT)
}

@Preview(name = "Bold dark", widthDp = PREVIEW_W, heightDp = PREVIEW_H)
@Composable
private fun PreviewBoldDark() {
    ThemePreview(ThemeId.BOLD, Appearance.DARK)
}

@Preview(name = "Bold light", widthDp = PREVIEW_W, heightDp = PREVIEW_H)
@Composable
private fun PreviewBoldLight() {
    ThemePreview(ThemeId.BOLD, Appearance.LIGHT)
}

@Preview(name = "Glass dark / Ocean", widthDp = PREVIEW_W, heightDp = PREVIEW_H)
@Composable
private fun PreviewGlassOcean() {
    ThemePreview(ThemeId.GLASS, Appearance.DARK, ColorSchemeConfig(scheme = "ocean"))
}

@Preview(name = "Material light / Sunset", widthDp = PREVIEW_W, heightDp = PREVIEW_H)
@Composable
private fun PreviewMaterialSunset() {
    ThemePreview(ThemeId.MATERIAL, Appearance.LIGHT, ColorSchemeConfig(scheme = "sunset"))
}

@Preview(name = "Bold dark / Custom", widthDp = PREVIEW_W, heightDp = PREVIEW_H)
@Composable
private fun PreviewBoldCustom() {
    ThemePreview(
        ThemeId.BOLD,
        Appearance.DARK,
        ColorSchemeConfig(
            scheme = ColorSchemeConfig.SCHEME_CUSTOM,
            accent = 0xFF7AD1FF,
            wall1 = 0xFF4F46E5,
            wall2 = 0xFFDB2777,
        ),
    )
}
