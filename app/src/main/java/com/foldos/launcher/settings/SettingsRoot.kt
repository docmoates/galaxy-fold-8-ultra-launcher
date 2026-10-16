package com.foldos.launcher.settings

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.foldos.launcher.a11y.LocalReduceMotion
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope

/**
 * The whole Settings UI: the sample-based home ([SettingsHomePage], with a small search icon), "More settings"
 * (the detailed pages and the Advanced switch), the dock gallery, plus one route per detailed page.
 * Every change is written through [repo] immediately; the UI re-renders from [SettingsRepository.settings].
 *
 * @param writeScope a scope that outlives this composition (the app graph's `appScope`), so a
 *   change made right before the activity finishes is still written.
 * @param startRoute a [SettingsRoutes] page pushed on top of the root once, when the activity was
 *   opened for a specific page (edit mode > Dock); null opens the root list.
 */
@Composable
fun SettingsRoot(
    repo: SettingsRepository,
    writeScope: CoroutineScope,
    onClose: () -> Unit,
    startRoute: String? = null,
) {
    val settings by produceState<LauncherSettings?>(initialValue = null, key1 = repo) {
        repo.settings.collect { value = it }
    }
    val update = remember(repo, writeScope) { SettingsUpdater(writeScope, repo) }

    var query by rememberSaveable { mutableStateOf("") }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var startRouteHandled by rememberSaveable { mutableStateOf(false) }

    val current = settings
    if (current == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    val navController = rememberNavController()
    val onBack: () -> Unit = { navController.popBackStack() }

    LaunchedEffect(navController, startRoute) {
        if (!startRouteHandled) {
            startRouteHandled = true
            val known = SettingsPages.byRoute(startRoute ?: "") != null ||
                startRoute == SearchRows.ROUTE ||
                startRoute == MailSettingsRows.ROUTE ||
                startRoute == BackupRows.ROUTE ||
                startRoute == AccessibilityRows.ROUTE ||
                startRoute == SettingsRoutes.MORE ||
                startRoute == SettingsRoutes.DOCK_GALLERY
            if (startRoute != null && startRoute != SettingsRoutes.ROOT && known) {
                navController.navigate(startRoute) { launchSingleTop = true }
            }
        }
    }

    // Reduce motion: page changes are instant (these are NavHost's defaults otherwise: 700ms fades).
    val reduceMotion = LocalReduceMotion.current
    CompositionLocalProvider(LocalSettingsRowFilter provides SettingsRowFilter(query = query, advanced = advanced)) {
        NavHost(
            navController = navController,
            startDestination = SettingsRoutes.ROOT,
            enterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(animationSpec = tween(700)) },
            exitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(animationSpec = tween(700)) },
            popEnterTransition = { if (reduceMotion) EnterTransition.None else fadeIn(animationSpec = tween(700)) },
            popExitTransition = { if (reduceMotion) ExitTransition.None else fadeOut(animationSpec = tween(700)) },
        ) {
            composable(SettingsRoutes.ROOT) {
                SettingsHomePage(
                    settings = current,
                    update = update,
                    query = query,
                    onQueryChange = { query = it },
                    advanced = advanced,
                    onAdvancedChange = { advanced = it },
                    onOpen = { route -> navController.navigate(route) { launchSingleTop = true } },
                    onClose = onClose,
                )
            }
            composable(SettingsRoutes.MORE) {
                MoreSettingsPage(
                    advanced = advanced,
                    onAdvancedChange = { advanced = it },
                    onOpen = { route -> navController.navigate(route) { launchSingleTop = true } },
                    onBack = onBack,
                )
            }
            composable(SettingsRoutes.DOCK_GALLERY) { DockGalleryPage(settings = current, update = update, onBack = onBack) }
            composable(SettingsRoutes.QUICK_SETUP) {
                QuickSetupPage(
                    update = update,
                    onBack = onBack,
                    onOpenBackup = { navController.navigate(BackupRows.ROUTE) { launchSingleTop = true } },
                )
            }
            composable(SettingsRoutes.SCREENS) { ScreensPage(settings = current, update = update, onBack = onBack) }
            composable(SettingsRoutes.HOME) { HomeSettingsPage(settings = current, update = update, onBack = onBack) }
            composable(SettingsRoutes.DOCK) { DockPage(settings = current, update = update, onBack = onBack) }
            composable(SettingsRoutes.HUB) { HubSettingsPage(settings = current, update = update, onBack = onBack) }
            composable(SettingsRoutes.LOOK) { LookPage(settings = current, update = update, onBack = onBack) }
            composable(SettingsRoutes.WIDGETS) { WidgetsPage(settings = current, update = update, onBack = onBack) }
            composable(SettingsRoutes.GESTURES) { GesturesPage(settings = current, update = update, onBack = onBack) }
            composable(SettingsRoutes.PROFILES) { ProfilesPage(settings = current, update = update, onBack = onBack) }
            composable(SettingsRoutes.AI) { AiPage(settings = current, update = update, onBack = onBack) }
            composable(SettingsRoutes.FEATURES) { FeaturesPage(settings = current, update = update, onBack = onBack) }
            composable(SearchRows.ROUTE) { SearchSettingsPage(settings = current, update = update, onBack = onBack) }
            composable(MailSettingsRows.ROUTE) { MailSettingsPage(settings = current, update = update, onBack = onBack) }
            composable(BackupRows.ROUTE) { BackupPage(settings = current, update = update, onBack = onBack) }
            composable(AccessibilityRows.ROUTE) { AccessibilityPage(settings = current, update = update, onBack = onBack) }
            composable(SettingsRoutes.ABOUT) { AboutPage(onBack = onBack) }
        }
    }
}
