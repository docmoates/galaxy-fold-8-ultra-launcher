package com.foldos.launcher.onboarding

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.foldos.launcher.a11y.A11yRoot
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.theme.FoldOsTheme
import kotlinx.coroutines.launch

/**
 * Hosts the [SetupWizard] (default) or the [PresetGallery] ([MODE_GALLERY]) as a full-screen
 * activity over the launcher. Reads and writes settings through the app graph so the launcher
 * behind it updates live; writes go through `appScope`, which outlives this activity.
 *
 * Needs a manifest entry: `<activity android:name=".onboarding.OnboardingActivity"
 * android:exported="false" android:theme="@style/Theme.FoldOS" />`.
 */
class OnboardingActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val graph = FoldOsApplication.from(this).graph
        val gallery = intent?.getStringExtra(EXTRA_MODE) == MODE_GALLERY
        val update: ((LauncherSettings) -> LauncherSettings) -> Unit = { transform ->
            graph.appScope.launch { graph.settings.update(transform) }
        }
        setContent {
            val settings by produceState<LauncherSettings?>(initialValue = null, key1 = graph) {
                graph.settings.settings.collect { value = it }
            }
            val current = settings
            if (current != null) {
                FoldOsTheme(settings = current) {
                    A11yRoot(settings = current) {
                        if (gallery) {
                            PresetGallery(
                                settings = current,
                                onApply = { preset -> update(preset.apply) },
                                onClose = { finish() },
                            )
                        } else {
                            SetupWizard(
                                settings = current,
                                onUpdate = update,
                                onFinish = { finish() },
                            )
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_MODE: String = "com.foldos.launcher.onboarding.MODE"
        const val MODE_WIZARD: String = "wizard"
        const val MODE_GALLERY: String = "gallery"

        /** Opens the five-step setup wizard (Settings > Quick setup > Run setup wizard, or first run). */
        fun wizardIntent(context: Context): Intent =
            Intent(context, OnboardingActivity::class.java).putExtra(EXTRA_MODE, MODE_WIZARD)

        /** Opens the preset gallery (Settings > Quick setup > Preset gallery). */
        fun galleryIntent(context: Context): Intent =
            Intent(context, OnboardingActivity::class.java).putExtra(EXTRA_MODE, MODE_GALLERY)
    }
}
