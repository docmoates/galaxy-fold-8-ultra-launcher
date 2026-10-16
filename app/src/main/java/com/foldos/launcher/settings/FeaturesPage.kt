package com.foldos.launcher.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.runtime.Composable
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.notifications.NotificationBadgeSettingsRows
import com.foldos.launcher.notifications.NotificationSettingsRowInfos
import com.foldos.launcher.notifications.QuietDrawerSettingsRows

/** One feature switch: settings key plus the mock-up's label and subtitle. */
data class FeatureRowInfo(val key: String, val info: SettingsRowInfo)

object FeaturesRows {
    val features: List<FeatureRowInfo> = listOf(
        FeatureRowInfo(LauncherSettings.FEATURE_SEARCH, SettingsRowInfo("Universal search", "Apps, contacts, files, settings, web, calculator")),
        FeatureRowInfo(LauncherSettings.FEATURE_GESTURES, SettingsRowInfo("Home gestures", "Swipes, pinch, double-tap, hinge actions")),
        FeatureRowInfo(LauncherSettings.FEATURE_DRAWER_TABS, SettingsRowInfo("Smart drawer tabs", "AI categories and hidden apps")),
        FeatureRowInfo(LauncherSettings.FEATURE_PAIRS, SettingsRowInfo("App pairs", "Split-screen pairs and pop-up windows")),
        FeatureRowInfo(LauncherSettings.FEATURE_BADGES, SettingsRowInfo("Notification badges")),
        FeatureRowInfo(LauncherSettings.FEATURE_AI, SettingsRowInfo("AI features", "Summaries, suggested replies and actions")),
        FeatureRowInfo(LauncherSettings.FEATURE_DOCK, SettingsRowInfo("Dock", "Favorites bar on every home page")),
        FeatureRowInfo(LauncherSettings.FEATURE_STACKS, SettingsRowInfo("Widget stacks", "Swipe between widgets in one slot")),
        FeatureRowInfo(LauncherSettings.FEATURE_SMART, SettingsRowInfo("Smart widgets", "Rules by time, place, fold state, device")),
    )
    val noNetwork = SettingsRowInfo(
        "No-network mode",
        "Gmail, web search results and any online AI stay off; the launcher makes no network requests of its own",
    )
    val backup = SettingsRowInfo("Backup", "Included in Google device backup; export to file", advanced = true)
    val locks = SettingsRowInfo("Lock layout, app lock, private space", advanced = true)

    val page = SettingsPageInfo(
        route = SettingsRoutes.FEATURES,
        title = "Features",
        subtitle = "Every launcher feature has an on/off switch",
        icon = Icons.Outlined.ToggleOn,
        // Notification rows (badge style, previews, quiet drawer) live on this page too.
        rows = features.map { it.info } + NotificationSettingsRowInfos.all + listOf(noNetwork, backup, locks),
    )
}

/**
 * Settings > Features: one switch per feature, then the notification section (badge style,
 * long-press previews, quiet notification drawer and its apps, notification access) from
 * `notifications/`, then the advanced placeholders.
 */
@Composable
fun FeaturesPage(
    settings: LauncherSettings,
    update: SettingsUpdater,
    onBack: () -> Unit,
) {
    SettingsPageScaffold(title = FeaturesRows.page.title, onBack = onBack) {
        FeaturesRows.features.forEach { feature ->
            ToggleRow(
                info = feature.info,
                checked = settings.feature(feature.key),
                onCheckedChange = { on -> update { it.withFeature(feature.key, on) } },
            )
        }
        SectionHeader(text = "Notifications")
        NotificationBadgeSettingsRows(settings = settings, update = update)
        QuietDrawerSettingsRows()
        SectionHeader(text = "Privacy")
        ToggleRow(
            info = FeaturesRows.noNetwork,
            checked = settings.noNetwork,
            onCheckedChange = { on -> update { it.copy(noNetwork = on) } },
        )
        StaticRow(info = FeaturesRows.backup)
        StaticRow(info = FeaturesRows.locks)
    }
}
