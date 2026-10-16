@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.foldos.launcher.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.a11yGreyscale
import com.foldos.launcher.a11y.a11yHeading
import com.foldos.launcher.a11y.a11yLabel
import com.foldos.launcher.core.model.ColorSchemeConfig

/** Swatches offered for each of the three custom colors; the hex field under them takes anything else. */
private val CUSTOM_PALETTE: List<Long> = listOf(
    0xFFEF4444, 0xFFF97316, 0xFFF59E0B, 0xFFEAB308, 0xFF84CC16, 0xFF22C55E, 0xFF14B8A6, 0xFF06B6D4,
    0xFF0EA5E9, 0xFF3B82F6, 0xFF6366F1, 0xFF8B5CF6, 0xFFA855F7, 0xFFEC4899, 0xFF64748B, 0xFF1F2937,
    0xFFF3F4F6,
)

/**
 * The three custom-color pickers (accent, wallpaper color 1, wallpaper color 2): a strip of swatches
 * each, plus a hex field for an exact value. Every change is applied at once through [onChange], which
 * also keeps the scheme on "custom".
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomColorsSheet(
    colors: ColorSchemeConfig,
    onChange: ((ColorSchemeConfig) -> ColorSchemeConfig) -> Unit,
    onClose: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onClose,
        sheetState = sheetState,
        modifier = Modifier.a11yGreyscale(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 8.dp),
        ) {
            Text(
                text = "Custom colors",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.a11yHeading().padding(horizontal = 24.dp, vertical = 4.dp),
            )
            ColorPickerBlock(
                title = "Accent",
                subtitle = "Buttons, highlights, active tabs",
                value = colors.accent,
                onPick = { argb -> onChange { it.copy(scheme = ColorSchemeConfig.SCHEME_CUSTOM, accent = argb) } },
            )
            ColorPickerBlock(
                title = "Wallpaper color 1",
                subtitle = "Top of the wallpaper gradient",
                value = colors.wall1,
                onPick = { argb -> onChange { it.copy(scheme = ColorSchemeConfig.SCHEME_CUSTOM, wall1 = argb) } },
            )
            ColorPickerBlock(
                title = "Wallpaper color 2",
                subtitle = "Bottom of the wallpaper gradient",
                value = colors.wall2,
                onPick = { argb -> onChange { it.copy(scheme = ColorSchemeConfig.SCHEME_CUSTOM, wall2 = argb) } },
            )
            Spacer(modifier = Modifier.height(16.dp))
            Spacer(modifier = Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))
        }
    }
}

@Composable
private fun ColorPickerBlock(
    title: String,
    subtitle: String,
    value: Long,
    onPick: (Long) -> Unit,
) {
    val outline = MaterialTheme.colorScheme.outline
    val primary = MaterialTheme.colorScheme.primary
    ColorRow(
        info = SettingsRowInfo(title, subtitle),
        value = value,
        onValueChange = onPick,
    )
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CUSTOM_PALETTE.forEach { argb ->
            val selected = argb == value
            Spacer(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Color(argb))
                    .border(
                        width = if (selected) 3.dp else 1.dp,
                        color = if (selected) primary else outline.copy(alpha = 0.5f),
                        shape = CircleShape,
                    )
                    .a11yLabel("$title ${hexOfColor(argb)}")
                    .clickable(role = Role.Button) { onPick(argb) },
            )
        }
    }
}
