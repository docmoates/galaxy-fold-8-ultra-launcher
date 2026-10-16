package com.foldos.launcher.settings

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

object AboutRows {
    val app = SettingsRowInfo("FoldOS", "Launcher for the Samsung Galaxy Z Fold 8 Ultra")
    val version = SettingsRowInfo("Version")
    val licenses = SettingsRowInfo("Open-source licenses")
    val plan = SettingsRowInfo("Design plan", "PLAN.md in the project repository", advanced = true)

    val page = SettingsPageInfo(
        route = SettingsRoutes.ABOUT,
        title = "About",
        subtitle = "Version and licenses",
        icon = Icons.Outlined.Info,
        rows = listOf(app, version, licenses, plan),
    )
}

private fun versionName(context: Context): String = runCatching {
    val info = context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.PackageInfoFlags.of(0L),
    )
    info.versionName ?: "unknown"
}.getOrDefault("unknown")

@Composable
fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember(context) { versionName(context) }
    SettingsPageScaffold(title = AboutRows.page.title, onBack = onBack) {
        StaticRow(info = AboutRows.app, showChevron = false)
        StaticRow(info = AboutRows.version, showChevron = false, trailingText = version)
        StaticRow(info = AboutRows.licenses)
        StaticRow(info = AboutRows.plan, showChevron = false)
    }
}
