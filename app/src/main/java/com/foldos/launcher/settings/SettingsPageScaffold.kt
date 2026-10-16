package com.foldos.launcher.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Navigation routes of the settings graph. */
object SettingsRoutes {
    const val ROOT: String = "root"
    const val QUICK_SETUP: String = "quick_setup"
    const val SCREENS: String = "screens"
    const val HOME: String = "home"
    const val DOCK: String = "dock"
    const val HUB: String = "hub"
    const val LOOK: String = "look"
    const val WIDGETS: String = "widgets"
    const val GESTURES: String = "gestures"
    const val PROFILES: String = "profiles"
    const val AI: String = "ai"
    const val FEATURES: String = "features"
    const val ABOUT: String = "about"
}

/** One settings page: where it lives, how the root list shows it, and its searchable rows. */
data class SettingsPageInfo(
    val route: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val rows: List<SettingsRowInfo>,
)

/**
 * Applies a settings change through [SettingsRepository.update] on the given scope. Pass a scope
 * that outlives the composition (the app graph's `appScope`): DataStore runs the transform in the
 * caller's context, so a write launched on a composition scope is cancelled, and lost, when the
 * screen leaves while it is in flight.
 */
class SettingsUpdater(
    private val scope: CoroutineScope,
    private val repo: SettingsRepository,
) {
    operator fun invoke(transform: (LauncherSettings) -> LauncherSettings) {
        scope.launch { repo.update(transform) }
    }
}

/** Top bar with back arrow plus a scrolling column of rows. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            content()
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
