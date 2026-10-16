package com.foldos.launcher

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.LocalAppGraph
import com.foldos.launcher.theme.FoldOsTheme

/**
 * The launcher (ROLE_HOME) activity. Edge to edge, themed by [FoldOsTheme] from the live
 * settings, and driven by [LauncherViewModel] so the home state survives recreation.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: LauncherViewModel by viewModels()

    /** Result of the "set as default home" system dialog; nothing to do with it either way. */
    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = FoldOsApplication.from(this).graph
        setContent {
            val settings by viewModel.settings.collectAsStateWithLifecycle()
            CompositionLocalProvider(LocalAppGraph provides graph) {
                FoldOsTheme(settings = settings) {
                    LauncherRoot(viewModel = viewModel)
                }
            }
        }
        if (savedInstanceState == null) maybeRequestHomeRole()
    }

    /** The home key while already on the launcher: close the drawer and return to the default page. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        viewModel.goHome()
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
        const val PREFS_NAME = "foldos_launcher"
        const val KEY_HOME_ROLE_PROMPTED = "home_role_prompted"
    }
}
