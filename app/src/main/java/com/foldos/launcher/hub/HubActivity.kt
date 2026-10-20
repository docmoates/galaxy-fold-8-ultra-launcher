package com.foldos.launcher.hub

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.foldos.launcher.a11y.A11yRoot
import com.foldos.launcher.builtins.LocalPermissionRequester
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.fold.effectiveScreen
import com.foldos.launcher.fold.rememberFoldState
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.onboarding.SystemBarsEffect
import com.foldos.launcher.onboarding.enableLauncherEdgeToEdge
import com.foldos.launcher.theme.FoldOsTheme
import com.foldos.launcher.theme.LocalFoldOsTokens
import com.foldos.launcher.theme.isDarkAppearance

/**
 * "FoldOS Hub" (1.7.0): the Hub page as an ordinary app, for people who keep the Pixel or One UI launcher as
 * their home screen. It has its own launcher icon, shows the Hub with the Launch tab first (see
 * [HubTab.standalone]) whether or not the Hub is switched on in Settings, and shares the Hub's themes, data
 * and permission prompts with the FoldOS home. Settings > Hub > "Show Hub app icon" hides the icon.
 */
class HubActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLauncherEdgeToEdge()
        val graph = FoldOsApplication.from(this).graph
        setContent {
            val loaded by produceState<LauncherSettings?>(initialValue = null, key1 = graph) {
                graph.settings.settings.collect { value = it }
            }
            val settings = loaded ?: LauncherSettings()
            SystemBarsEffect(dark = isDarkAppearance(settings.appearance))
            val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
                (graph.hub as? FoldOsHubRepository)?.notifyPermissionsChanged()
            }
            val requestPermission: (String) -> Unit = remember(permissionLauncher) {
                val request: (String) -> Unit = { permission ->
                    runCatching { permissionLauncher.launch(permission) }
                        .onFailure { Log.w(TAG, "Could not request $permission", it) }
                    Unit
                }
                request
            }
            CompositionLocalProvider(
                LocalAppGraph provides graph,
                LocalPermissionRequester provides requestPermission,
            ) {
                FoldOsTheme(settings = settings) {
                    A11yRoot(settings = settings) {
                        // Until DataStore has answered the page stays empty rather than flashing defaults.
                        if (loaded != null) {
                            val screen = effectiveScreen(settings, rememberFoldState())
                            Box(
                                Modifier
                                    .fillMaxSize()
                                    .background(LocalFoldOsTokens.current.wallpaper)
                                    .systemBarsPadding(),
                            ) {
                                HubPage(screen = screen, settings = settings, standalone = true)
                            }
                        }
                    }
                }
            }
        }
    }

    private companion object {
        const val TAG = "HubActivity"
    }
}

/** True while the "FoldOS Hub" launcher icon is enabled (the manifest default counts as enabled). */
internal fun hubAppIconShown(context: Context): Boolean =
    context.packageManager.getComponentEnabledSetting(ComponentName(context, HubActivity::class.java)) !=
        PackageManager.COMPONENT_ENABLED_STATE_DISABLED

/** Shows or hides the "FoldOS Hub" launcher icon (Settings > Hub > Show Hub app icon). */
internal fun setHubAppIconShown(context: Context, shown: Boolean) {
    val state = if (shown) PackageManager.COMPONENT_ENABLED_STATE_ENABLED else PackageManager.COMPONENT_ENABLED_STATE_DISABLED
    context.packageManager.setComponentEnabledSetting(
        ComponentName(context, HubActivity::class.java),
        state,
        PackageManager.DONT_KILL_APP,
    )
}
