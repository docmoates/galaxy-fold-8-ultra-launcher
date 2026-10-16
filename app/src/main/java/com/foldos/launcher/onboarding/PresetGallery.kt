package com.foldos.launcher.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.theme.LocalFoldOsTokens
import com.foldos.launcher.theme.Wallpaper

/**
 * Settings > Quick setup > Preset gallery (PLAN.md 5.3): one card per [Presets.all] entry with a
 * live cover-home preview of the preset applied to the user's current [settings]. Tapping the
 * preview or its Apply button calls [onApply]; the card whose id equals `settings.preset` shows
 * as applied. Must be composed inside `FoldOsTheme(settings)`; draws its own wallpaper.
 *
 * @param onApply the host persists the preset: `repo.update(preset.apply)` (the transform also
 *   records `preset = preset.id`).
 */
@Composable
fun PresetGallery(
    settings: LauncherSettings,
    onApply: (Preset) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tokens = LocalFoldOsTokens.current
    Box(modifier = modifier.fillMaxSize()) {
        Wallpaper()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(start = 22.dp, end = 22.dp, top = 20.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Preset gallery",
                        color = tokens.onSurface,
                        fontSize = 30.sp,
                        fontWeight = FontWeight.Medium,
                        lineHeight = 34.sp,
                    )
                    WizardSubtitle("Tap a card to apply it to both screens. Fine-tune anything afterwards in Settings.")
                }
                IconButton(onClick = onClose) {
                    Icon(imageVector = Icons.Rounded.Close, contentDescription = "Close", tint = tokens.onSurface)
                }
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = PaddingValues(bottom = 24.dp),
            ) {
                items(Presets.all, key = { it.id }) { preset ->
                    PresetCard(
                        preset = preset,
                        settings = settings,
                        applied = settings.preset == preset.id,
                        onApply = { onApply(preset) },
                    )
                }
            }
        }
    }
}

/** One gallery card: preview of the preset applied to the current settings, name, description, Apply. */
@Composable
private fun PresetCard(
    preset: Preset,
    settings: LauncherSettings,
    applied: Boolean,
    onApply: () -> Unit,
) {
    val tokens = LocalFoldOsTokens.current
    val candidate = remember(preset, settings) { preset.apply(settings) }
    val shape = RoundedCornerShape(tokens.radius)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(tokens.surface)
            .border(
                width = if (applied) 2.dp else 1.dp,
                color = if (applied) tokens.accent else tokens.surfaceBorder,
                shape = shape,
            )
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black.copy(alpha = 0.25f)),
        ) {
            SampleHomePreview(
                style = candidate.styleCover,
                theme = candidate.theme,
                colors = candidate.colors,
                modifier = Modifier.fillMaxWidth().height(220.dp),
                base = candidate,
            )
            Box(modifier = Modifier.matchParentSize().clickable(onClick = onApply))
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(text = preset.name, color = tokens.onSurface, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    text = preset.description,
                    color = tokens.onSurface.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                )
            }
            if (applied) {
                OutlinedButton(onClick = onApply, shape = CircleShape) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = tokens.accent,
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(text = "Applied", color = tokens.accent, fontWeight = FontWeight.SemiBold)
                }
            } else {
                AccentPillButton(label = "Apply", onClick = onApply)
            }
        }
    }
}
