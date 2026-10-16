package com.foldos.launcher

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foldos.launcher.core.AppGraph
import com.foldos.launcher.core.FoldOsApplication
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.model.HomePage
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.model.ScreenKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * State holder for [MainActivity]. Survives configuration changes (fold / unfold recreates the
 * activity when the manifest does not swallow the change) and keeps the hot flows alive so the
 * home screen does not flash empty on every recreation.
 */
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    val graph: AppGraph = FoldOsApplication.from(application).graph

    /** Current launcher settings; defaults until DataStore has been read. */
    val settings: StateFlow<LauncherSettings> = graph.settings.settings
        .stateIn(viewModelScope, SharingStarted.Eagerly, LauncherSettings())

    /** All launchable apps, independent of the fold posture. */
    val apps: StateFlow<List<AppInfo>> = graph.apps.apps
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val pageFlows = HashMap<ScreenKind, StateFlow<List<HomePage>>>()

    /** Home pages of one layout store, shared for the life of the view model. */
    fun pages(screen: ScreenKind): StateFlow<List<HomePage>> = pageFlows.getOrPut(screen) {
        graph.layouts.pages(screen).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    }

    private val _drawerOpen = MutableStateFlow(false)
    val drawerOpen: StateFlow<Boolean> = _drawerOpen.asStateFlow()

    fun openDrawer() {
        _drawerOpen.value = true
    }

    fun closeDrawer() {
        _drawerOpen.value = false
    }

    private val _homeRequests = MutableStateFlow(0)

    /** Increments every time the user asks to go to the default page (home key, back press). */
    val homeRequests: StateFlow<Int> = _homeRequests.asStateFlow()

    fun goHome() {
        _drawerOpen.value = false
        _homeRequests.value = _homeRequests.value + 1
    }

    private val ensuredScreens = HashSet<ScreenKind>()

    /**
     * Writes the default pages and items for [screen] once the app list is known, laid out for the
     * column count currently configured for that screen. Runs at most once per screen per process
     * unless the write fails.
     */
    fun ensureDefaultLayout(screen: ScreenKind) {
        if (!ensuredScreens.add(screen)) return
        viewModelScope.launch {
            val list = apps.first { it.isNotEmpty() }
            val columns = settings.value.colsFor(screen)
            val result = runCatching { graph.layouts.ensureDefaultLayout(screen, list, columns) }
            if (result.isFailure) ensuredScreens.remove(screen)
        }
    }

    fun launch(app: AppInfo) {
        graph.apps.launch(app)
    }
}
