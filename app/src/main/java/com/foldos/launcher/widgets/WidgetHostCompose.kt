package com.foldos.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.foldos.launcher.core.LocalAppGraph

/** The process-wide [WidgetHostController] for `LocalAppGraph.current.appWidgetHost`. */
@Composable
fun rememberWidgetHostController(): WidgetHostController {
    val graph = LocalAppGraph.current
    val context = LocalContext.current
    return remember(graph) { WidgetHostController.shared(context, graph.appWidgetHost) }
}

/** Outcome of [rememberAddAppWidgetFlow]. */
sealed interface AddAppWidgetResult {
    /**
     * [appWidgetId] is bound to [provider] and configured (when the provider needed it). The caller
     * now persists a `HomeItem(kind = WIDGET, appWidgetId = appWidgetId, ...)`.
     */
    data class Ready(val appWidgetId: Int, val provider: AppWidgetProviderInfo) : AddAppWidgetResult

    /** The user declined binding or configuration; the id has already been released. */
    data class Cancelled(val appWidgetId: Int, val reason: String) : AddAppWidgetResult
}

/** Starts the add-widget flow for a provider; returned by [rememberAddAppWidgetFlow]. */
@Stable
fun interface AddAppWidgetLauncher {
    /**
     * Allocates an id, binds [provider] (asking permission when needed) and runs its configure
     * activity. [widthDp] / [heightDp] are the box the widget will be shown in (0 = unknown); they
     * are passed as the initial size options so the first update is already right.
     */
    fun launch(provider: AppWidgetProviderInfo, widthDp: Int, heightDp: Int)
}

/**
 * The complete "add an app widget" flow as one launcher: allocate -> bind (permission dialog via
 * [BindAppWidgetContract] when needed) -> configure (through the host and the hosting Activity's
 * `onActivityResult`, forwarded to [WidgetHostController.onActivityResult]) -> [onResult].
 *
 * The configure step needs an Activity in the context chain of this composition; without one the
 * widget is delivered unconfigured. If the configure activity cannot be started at all the widget
 * is still delivered as [AddAppWidgetResult.Ready] (most providers show a "tap to set up" state).
 */
@Composable
fun rememberAddAppWidgetFlow(
    controller: WidgetHostController = rememberWidgetHostController(),
    onResult: (AddAppWidgetResult) -> Unit,
): AddAppWidgetLauncher {
    val context = LocalContext.current
    val activity = remember(context) { context.widgetHostActivity() }
    val latestOnResult = rememberUpdatedState(onResult)
    val flow = remember(controller, activity) { AddAppWidgetFlow(controller, activity) }
    val bindLauncher = rememberLauncherForActivityResult(BindAppWidgetContract()) { result ->
        flow.onBindResult(result)
    }
    SideEffect {
        flow.onResult = { result -> latestOnResult.value(result) }
        flow.requestBind = { request -> bindLauncher.launch(request) }
    }
    return remember(flow) {
        AddAppWidgetLauncher { provider, widthDp, heightDp -> flow.start(provider, widthDp, heightDp) }
    }
}

/** Plain state machine behind [rememberAddAppWidgetFlow]; main thread only. */
private class AddAppWidgetFlow(
    private val controller: WidgetHostController,
    private val activity: Activity?,
) {
    var onResult: (AddAppWidgetResult) -> Unit = {}
    var requestBind: (BindAppWidgetRequest) -> Unit = {}

    private var pendingBindId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
    private var pendingBindProvider: AppWidgetProviderInfo? = null

    fun start(provider: AppWidgetProviderInfo, widthDp: Int, heightDp: Int) {
        val appWidgetId = controller.allocateId()
        val options = if (widthDp > 0 && heightDp > 0) WidgetSizing.sizeOptions(widthDp, heightDp) else null
        val bound = controller.bindOrRequest(appWidgetId, provider, options) { _ ->
            pendingBindId = appWidgetId
            pendingBindProvider = provider
            requestBind(BindAppWidgetRequest(appWidgetId, provider, options))
        }
        if (bound) afterBound(appWidgetId, provider)
    }

    fun onBindResult(result: BindAppWidgetResult) {
        val appWidgetId = if (result.appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) result.appWidgetId else pendingBindId
        val provider = pendingBindProvider
        pendingBindId = AppWidgetManager.INVALID_APPWIDGET_ID
        pendingBindProvider = null
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        if (!result.bound) {
            controller.deleteId(appWidgetId)
            onResult(AddAppWidgetResult.Cancelled(appWidgetId, "Widget permission was not granted"))
            return
        }
        afterBound(appWidgetId, provider)
    }

    private fun afterBound(appWidgetId: Int, providerHint: AppWidgetProviderInfo?) {
        val provider = controller.providerInfo(appWidgetId) ?: providerHint
        if (provider == null) {
            controller.deleteId(appWidgetId)
            onResult(AddAppWidgetResult.Cancelled(appWidgetId, "Widget provider is not available"))
            return
        }
        if (activity != null && controller.needsConfiguration(provider)) {
            val started = controller.startConfigure(activity, appWidgetId) { configuredId, configured ->
                if (configured) {
                    onResult(AddAppWidgetResult.Ready(configuredId, provider))
                } else {
                    controller.deleteId(configuredId)
                    onResult(AddAppWidgetResult.Cancelled(configuredId, "Widget setup was cancelled"))
                }
            }
            if (started) return
        }
        onResult(AddAppWidgetResult.Ready(appWidgetId, provider))
    }
}

/** Walks the `ContextWrapper` chain to the hosting Activity; null when there is none. */
internal fun Context.widgetHostActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
