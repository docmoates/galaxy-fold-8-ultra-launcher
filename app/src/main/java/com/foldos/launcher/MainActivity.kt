package com.foldos.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.a11y.A11yRoot
import com.foldos.launcher.builtins.LocalPermissionRequester
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.hub.FoldOsHubRepository
import com.foldos.launcher.onboarding.SystemBarsEffect
import com.foldos.launcher.onboarding.enableLauncherEdgeToEdge
import com.foldos.launcher.perf.ReportFullyDrawnOnFirstFrame
import com.foldos.launcher.perf.StartupTracker
import com.foldos.launcher.theme.FoldOsTheme
import com.foldos.launcher.theme.isDarkAppearance
import com.foldos.launcher.widgets.WidgetHostController

/**
 * The launcher (ROLE_HOME) activity. Edge to edge, themed by [FoldOsTheme] from the live
 * settings, and driven by [LauncherViewModel] so the home state survives recreation.
 *
 * Phase 2+3: provides [LocalPermissionRequester] (runtime permissions the built-in widgets ask
 * for), forwards the deprecated `onActivityResult` to the widget host controller (app-widget
 * configure activities can only report back this way) and starts / stops the `AppWidgetHost`
 * with the activity, like Launcher3.
 *
 * Phase 4+5: the Hub tabs and universal search reuse the same permission requester (calendar,
 * call log, contacts); the home key double press is detected in [onNewIntent].
 */
class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    /** Result of the "set as default home" system dialog; nothing to do with it either way. */
    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        StartupTracker.onActivityCreate()
        super.onCreate(savedInstanceState)
        // Transparent bars; the icon colors follow the launcher's own Appearance (SystemBarsEffect below),
        // not the system dark mode that enableEdgeToEdge() would use.
        enableLauncherEdgeToEdge()
        val graph = FoldOsApplication.from(this).graph
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            val loadedSettings by viewModel.loadedSettings.collectAsStateWithLifecycle()
            // Until DataStore has answered, `settings` is the default (Dark) and would flip the icons of a
            // Light launcher for a few frames; the dark starting style from enableLauncherEdgeToEdge stays.
            if (loadedSettings != null) SystemBarsEffect(dark = isDarkAppearance(settings.appearance) || settings.spaceBlack)
            // Built-in widgets (calendar, photos, Bluetooth battery) ask for one permission at a
            // time through this; the widget re-checks the grant when the activity resumes, and the
            // Hub's provider flows re-check on this bump (they never poll).
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
                    // Accessibility (Settings > Accessibility): LocalReduceMotion / LocalHighContrast /
                    // LocalGreyscale / LocalLargeIcons, the text-size density and the greyscale effect.
                    // High contrast itself is applied by FoldOsTheme. Composed once for the window.
                    A11yRoot(settings = settings) {
                        LauncherRoot(viewModel = viewModel)
                    }
                }
                // Logs the cold-start time and calls reportFullyDrawn() once the first frame with the
                // loaded settings (the real home or the wizard, not the bare wallpaper) is up.
                if (loadedSettings != null) ReportFullyDrawnOnFirstFrame(this@MainActivity)
            }
        }
        if (savedInstanceState == null) maybeRequestHomeRole()
    }

    override fun onStart() {
        super.onStart()
        try {
            FoldOsApplication.from(this).graph.appWidgetHost.startListening()
        } catch (e: RuntimeException) {
            Log.w(TAG, "AppWidgetHost.startListening failed", e)
        }
    }

    override fun onStop() {
        try {
            FoldOsApplication.from(this).graph.appWidgetHost.stopListening()
        } catch (e: RuntimeException) {
            Log.w(TAG, "AppWidgetHost.stopListening failed", e)
        }
        super.onStop()
    }

    /**
     * App-widget configure activities are started through `AppWidgetHost
     * .startAppWidgetConfigureActivityForResult`, which has no ActivityResultContract equivalent,
     * so their result arrives here and is handed to the process-wide [WidgetHostController].
     */
    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        WidgetHostController.from(this, FoldOsApplication.from(this).graph).onActivityResult(requestCode, resultCode, data)
    }

    /**
     * The home key while already on the launcher: close the drawer / search / edit mode and return
     * to the default page; a second press within 400 ms performs the HOME_DOUBLE gesture binding.
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.onHomeKeyPressed(this)
    }

    /** Asks once to become the default home when another launcher currently holds the role. */
    private fun maybeRequestHomeRole() {
        val roleManager = getSystemService(RoleManager::class.java) ?: return
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return
        if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_HOME_ROLE_PROMPTED, false)) return
        prefs.edit().putBoolean(KEY_HOME_ROLE_PROMPTED, true).apply()
        runCatching {
            homeRoleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME))
        }
    }

    private companion object {
        const val TAG = "MainActivity"
        const val PREFS_NAME = "foldos_launcher"
        const val KEY_HOME_ROLE_PROMPTED = "home_role_prompted"
    }
}
