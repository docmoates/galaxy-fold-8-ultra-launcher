package com.foldos.launcher.settings

import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.foldos.launcher.a11y.A11yRoot
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.Appearance
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.repo.SettingsRepository

/**
 * Settings entry point. Uses the [SettingsRepository] from the app graph, which
 * [FoldOsApplication] builds in `onCreate`, so the launcher and Settings always read and write the
 * same DataStore file.
 */
class SettingsActivity : ComponentActivity() {

    private val homeRoleRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = FoldOsApplication.from(this).graph
        val repo: SettingsRepository = graph.settings
        val startRoute: String? = intent?.getStringExtra(EXTRA_ROUTE)
        setContent {
            SettingsAppTheme(repo = repo) {
                SettingsRoot(repo = repo, writeScope = graph.appScope, onClose = { finish() }, startRoute = startRoute)
            }
        }
        // This activity carries the app icon, so opening "FoldOS" from the app list lands here;
        // offer the home role once when another launcher still holds it.
        if (savedInstanceState == null) maybeRequestHomeRole()
    }

    private fun maybeRequestHomeRole() {
        val roleManager = getSystemService(RoleManager::class.java) ?: return
        if (!roleManager.isRoleAvailable(RoleManager.ROLE_HOME)) return
        if (roleManager.isRoleHeld(RoleManager.ROLE_HOME)) return
        val prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_PROMPTED, false)) return
        prefs.edit().putBoolean(KEY_PROMPTED, true).apply()
        runCatching { homeRoleRequest.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_HOME)) }
    }

    companion object {
        private const val PREFS_NAME = "foldos_launcher"
        private const val KEY_PROMPTED = "home_role_prompted_settings"

        /** Optional [SettingsRoutes] value: the page to open on top of the root list. */
        const val EXTRA_ROUTE: String = "com.foldos.launcher.settings.ROUTE"

        /** Intent that opens Settings, at [route] (a [SettingsRoutes] constant) when given. */
        fun intent(context: Context, route: String? = null): Intent {
            val intent = Intent(context, SettingsActivity::class.java)
            if (route != null && route != SettingsRoutes.ROOT) intent.putExtra(EXTRA_ROUTE, route)
            return intent
        }
    }
}

/**
 * Material 3 dynamic color following the launcher's Appearance setting. The Theme agent's
 * FoldOsTheme can replace this wrapper once it exists.
 */
@Composable
private fun SettingsAppTheme(
    repo: SettingsRepository,
    content: @Composable () -> Unit,
) {
    // The whole settings object, not just the appearance: the accessibility switches (text size,
    // reduce motion, greyscale, high contrast) apply to this window too.
    val stored by produceState<LauncherSettings?>(initialValue = null, key1 = repo) {
        repo.settings.collect { value = it }
    }
    val a11ySettings = stored ?: LauncherSettings()
    val appearance = stored?.appearance ?: Appearance.AUTO
    val systemDark = isSystemInDarkTheme()
    val dark = when (appearance) {
        Appearance.DARK -> true
        Appearance.LIGHT -> false
        Appearance.AUTO -> systemDark
    }
    val context = LocalContext.current
    val dynamicScheme = remember(context, dark) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    val colorScheme = if (a11ySettings.highContrast) highContrastScheme(dynamicScheme, dark) else dynamicScheme
    A11yRoot(settings = a11ySettings) {
        MaterialTheme(colorScheme = colorScheme) {
            Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                content()
            }
        }
    }
}

/** Settings > Accessibility > High contrast for the Material 3 settings window: black / white text and outlines. */
private fun highContrastScheme(base: ColorScheme, dark: Boolean): ColorScheme {
    val paper = if (dark) Color.Black else Color.White
    val ink = if (dark) Color.White else Color.Black
    return base.copy(
        background = paper,
        surface = paper,
        onBackground = ink,
        onSurface = ink,
        onSurfaceVariant = ink,
        outline = ink,
        outlineVariant = ink.copy(alpha = 0.6f),
    )
}
