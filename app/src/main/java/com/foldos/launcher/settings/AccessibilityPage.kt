package com.foldos.launcher.settings

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Accessibility
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.foldos.launcher.a11y.A11Y_MAX_TEXT_SCALE
import com.foldos.launcher.a11y.A11Y_MIN_TEXT_SCALE
import com.foldos.launcher.core.model.LauncherSettings
import kotlin.math.roundToInt

/**
 * Settings > Accessibility (Phase 6): high contrast, reduce motion, text size, Easy mode (large
 * icons) and greyscale, plus a hint for the hardware-keyboard shortcuts and a shortcut to Android's
 * own accessibility settings. Owned by the Accessibility agent; the Integrate agent adds [ROUTE]
 * to `SettingsRoot` and [page] to `SettingsPages.all`.
 */
object AccessibilityRows {
    /** Navigation route of this page; not in `SettingsRoutes` because that file belongs to Settings. */
    const val ROUTE: String = "accessibility"

    val highContrast = SettingsRowInfo("High contrast", "Opaque panels, strong outlines and pure black or white text")
    val reduceMotion = SettingsRowInfo("Reduce motion", "No sliding or fading: pages, sheets and the drawer change instantly")
    val textSize = SettingsRowInfo("Text size", "Scales launcher text on top of the system font size")
    val largeIcons = SettingsRowInfo(
        "Easy mode (large icons)",
        "Icons 20% bigger, labels always shown, 3 columns on the cover screen",
    )
    val greyscale = SettingsRowInfo("Greyscale", "Show the launcher in black and white")
    val keyboard = SettingsRowInfo(
        "Hardware keyboard",
        "Type on the home screen to search; arrow keys move between icons, Enter opens, Menu key shows options",
    )
    val system = SettingsRowInfo("Android accessibility settings", "TalkBack, font and display size, color correction")

    val page = SettingsPageInfo(
        route = ROUTE,
        title = "Accessibility",
        subtitle = "Contrast, motion, text size, large icons, greyscale",
        icon = Icons.Outlined.Accessibility,
        rows = listOf(highContrast, reduceMotion, textSize, largeIcons, greyscale, keyboard, system),
    )
}

@Composable
fun AccessibilityPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    SettingsPageScaffold(title = AccessibilityRows.page.title, onBack = onBack) {
        SectionHeader(text = "Display")
        ToggleRow(
            info = AccessibilityRows.highContrast,
            checked = settings.highContrast,
            onCheckedChange = { on -> update { it.copy(highContrast = on) } },
        )
        ToggleRow(
            info = AccessibilityRows.greyscale,
            checked = settings.greyscale,
            onCheckedChange = { on -> update { it.copy(greyscale = on) } },
        )
        TextScaleRow(
            info = AccessibilityRows.textSize,
            value = settings.textScale,
            onCommit = { scale -> update { it.copy(textScale = scale) } },
        )
        SectionHeader(text = "Motion and size")
        ToggleRow(
            info = AccessibilityRows.reduceMotion,
            checked = settings.reduceMotion,
            onCheckedChange = { on -> update { it.copy(reduceMotion = on) } },
        )
        ToggleRow(
            info = AccessibilityRows.largeIcons,
            checked = settings.largeIcons,
            onCheckedChange = { on -> update { it.copy(largeIcons = on) } },
        )
        SectionHeader(text = "Input and system")
        StaticRow(info = AccessibilityRows.keyboard, showChevron = false)
        StaticRow(
            info = AccessibilityRows.system,
            onClick = {
                runCatching {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
        )
    }
}

private fun subtitleOf(subtitle: String?): (@Composable () -> Unit)? =
    if (subtitle == null) null else { { Text(text = subtitle) } }

/**
 * Text size slider: 85% to 160% in 5% steps. The value is written when the finger lifts (or a
 * screen reader's adjust action ends), not on every move, because the whole window re-lays out
 * with the new size.
 */
@Composable
private fun TextScaleRow(
    info: SettingsRowInfo,
    value: Float,
    onCommit: (Float) -> Unit,
) {
    if (!LocalSettingsRowFilter.current.shows(info)) return
    val start = value.coerceIn(A11Y_MIN_TEXT_SCALE, A11Y_MAX_TEXT_SCALE)
    var local by remember(value) { mutableFloatStateOf(start) }
    val percent = (local * 100f).roundToInt()
    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = { Text(text = info.title) },
            supportingContent = subtitleOf(info.subtitle),
            trailingContent = {
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            },
        )
        Slider(
            value = local,
            onValueChange = { next -> local = next },
            onValueChangeFinished = {
                val snapped = (local * 20f).roundToInt() / 20f
                local = snapped
                if (snapped != value) onCommit(snapped)
            },
            valueRange = A11Y_MIN_TEXT_SCALE..A11Y_MAX_TEXT_SCALE,
            // 0.05 per step between 0.85 and 1.6: 15 intervals, 14 steps in between.
            steps = 14,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .semantics {
                    contentDescription = info.title
                    stateDescription = "$percent percent"
                },
        )
    }
}
