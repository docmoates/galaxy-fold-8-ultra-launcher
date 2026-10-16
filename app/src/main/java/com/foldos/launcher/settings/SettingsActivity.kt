package com.foldos.launcher.settings

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.Appearance
import com.foldos.launcher.core.repo.SettingsRepository

/**
 * Settings entry point. Uses the [SettingsRepository] from the app graph, which
 * [FoldOsApplication] builds in `onCreate`, so the launcher and Settings always read and write the
 * same DataStore file.
 */
class SettingsActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = FoldOsApplication.from(this).graph
        val repo: SettingsRepository = graph.settings
        setContent {
            SettingsAppTheme(repo = repo) {
                SettingsRoot(repo = repo, writeScope = graph.appScope, onClose = { finish() })
            }
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
    val appearance by produceState(initialValue = Appearance.AUTO, key1 = repo) {
        repo.settings.collect { value = it.appearance }
    }
    val systemDark = isSystemInDarkTheme()
    val dark = when (appearance) {
        Appearance.DARK -> true
        Appearance.LIGHT -> false
        Appearance.AUTO -> systemDark
    }
    val context = LocalContext.current
    val colorScheme = remember(context, dark) {
        if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    }
    MaterialTheme(colorScheme = colorScheme) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            content()
        }
    }
}
