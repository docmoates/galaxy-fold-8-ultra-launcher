package com.foldos.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import com.foldos.launcher.core.AppGraph

/**
 * Thin, main-thread controller around the process-wide [AppWidgetHost] (`graph.appWidgetHost`)
 * and [AppWidgetManager]. It owns no UI and no Activity; the pieces that need one take it as a
 * parameter.
 *
 * Adding a third-party widget is a three-step dance, each step exposed here:
 * 1. [allocateId] reserves an app-widget id.
 * 2. [bindOrRequest] binds the provider to the id if the user already allowed FoldOS to bind
 *    widgets; otherwise it hands back the `ACTION_APPWIDGET_BIND` intent that asks them (launch it
 *    with [BindAppWidgetContract] / `rememberLauncherForActivityResult`, or `startActivityForResult`
 *    with [REQUEST_BIND_APPWIDGET]).
 * 3. If [needsConfiguration] is true, [startConfigure] opens the provider's configuration
 *    activity through the host (required on Android 12+; it grants the temporary permission the
 *    configure activity needs). Its result comes back through `Activity.onActivityResult`, which
 *    the hosting activity forwards to [onActivityResult].
 *
 * [deleteId] releases an id again (cancelled flow, item removed). Use [shared] /
 * `rememberWidgetHostController()` so pending configure callbacks survive recomposition.
 */
class WidgetHostController(
    context: Context,
    val host: AppWidgetHost,
    val manager: AppWidgetManager,
) {
    private val appContext: Context = context.applicationContext

    private class PendingConfigure(
        val appWidgetId: Int,
        val onResult: (appWidgetId: Int, configured: Boolean) -> Unit,
    )

    /** Configure requests waiting for `onActivityResult`, keyed by request code. Main thread only. */
    private val pendingConfigure = HashMap<Int, PendingConfigure>()

    /** The id handed to the last bind-permission request; used when the result intent lacks it. */
    var lastRequestedBindId: Int = AppWidgetManager.INVALID_APPWIDGET_ID
        private set

    // -----------------------------------------------------------------------------------------
    // Ids
    // -----------------------------------------------------------------------------------------

    /** Reserves a new app-widget id from the host. */
    fun allocateId(): Int = host.allocateAppWidgetId()

    /** Releases [appWidgetId] (and the provider's binding to it). Safe to call twice. */
    fun deleteId(appWidgetId: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        try {
            host.deleteAppWidgetId(appWidgetId)
        } catch (e: RuntimeException) {
            Log.w(TAG, "deleteAppWidgetId($appWidgetId) failed", e)
        }
    }

    // -----------------------------------------------------------------------------------------
    // Providers
    // -----------------------------------------------------------------------------------------

    /** The provider currently bound to [appWidgetId], or null when the id is unbound or stale. */
    fun providerInfo(appWidgetId: Int): AppWidgetProviderInfo? {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return null
        return try {
            manager.getAppWidgetInfo(appWidgetId)
        } catch (e: RuntimeException) {
            Log.w(TAG, "getAppWidgetInfo($appWidgetId) failed", e)
            null
        }
    }

    /** True when a provider is bound to [appWidgetId]. */
    fun isBound(appWidgetId: Int): Boolean = providerInfo(appWidgetId) != null

    /** Every installed home-screen widget provider visible to this user. */
    fun installedProviders(): List<AppWidgetProviderInfo> = try {
        manager.installedProviders ?: emptyList()
    } catch (e: RuntimeException) {
        Log.w(TAG, "getInstalledProviders failed", e)
        emptyList()
    }

    /** Finds the installed provider for [component]; null when that app or widget is gone. */
    fun providerFor(component: ComponentName): AppWidgetProviderInfo? =
        installedProviders().firstOrNull { it.provider == component }

    // -----------------------------------------------------------------------------------------
    // Binding
    // -----------------------------------------------------------------------------------------

    /**
     * Binds [provider] to [appWidgetId] when FoldOS already holds the bind permission. Returns true
     * when the id is bound now. Returns false after calling [onNeedsPermission] with the
     * `ACTION_APPWIDGET_BIND` intent the caller must launch for a result; `RESULT_OK` means the
     * user allowed it and the id is bound.
     */
    fun bindOrRequest(appWidgetId: Int, provider: AppWidgetProviderInfo, onNeedsPermission: (Intent) -> Unit): Boolean =
        bindOrRequest(appWidgetId, provider, null, onNeedsPermission)

    /**
     * Same as the three-argument [bindOrRequest] but also passes [options] (normally
     * [WidgetSizing.sizeOptions]) so the provider's first update already knows its size.
     */
    fun bindOrRequest(
        appWidgetId: Int,
        provider: AppWidgetProviderInfo,
        options: Bundle?,
        onNeedsPermission: (Intent) -> Unit,
    ): Boolean {
        val bound = try {
            manager.bindAppWidgetIdIfAllowed(appWidgetId, provider.profile, provider.provider, options)
        } catch (e: RuntimeException) {
            Log.w(TAG, "bindAppWidgetIdIfAllowed($appWidgetId, ${provider.provider}) failed", e)
            false
        }
        if (bound) return true
        lastRequestedBindId = appWidgetId
        onNeedsPermission(bindIntent(appWidgetId, provider, options))
        return false
    }

    /** The `ACTION_APPWIDGET_BIND` intent asking the user to let FoldOS bind [provider] to [appWidgetId]. */
    fun bindIntent(appWidgetId: Int, provider: AppWidgetProviderInfo, options: Bundle? = null): Intent =
        bindIntent(appWidgetId, provider.provider, provider.profile, options)

    /** [bindIntent] from a bare component (profile null = the current user). */
    fun bindIntent(
        appWidgetId: Int,
        provider: ComponentName,
        profile: android.os.UserHandle? = null,
        options: Bundle? = null,
    ): Intent {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        if (profile != null) intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, profile)
        if (options != null) intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
        return intent
    }

    /**
     * Reads the id out of a bind-permission result. Falls back to [lastRequestedBindId] when the
     * result intent does not carry `EXTRA_APPWIDGET_ID`.
     */
    fun bindResultId(data: Intent?): Int {
        val fromIntent = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        return if (fromIntent != AppWidgetManager.INVALID_APPWIDGET_ID) fromIntent else lastRequestedBindId
    }

    // -----------------------------------------------------------------------------------------
    // Configuration
    // -----------------------------------------------------------------------------------------

    /**
     * True when [provider] declares a configuration activity that must run before the widget is
     * usable (`configure != null` and not `WIDGET_FEATURE_CONFIGURATION_OPTIONAL`).
     */
    fun needsConfiguration(provider: AppWidgetProviderInfo): Boolean =
        provider.configure != null &&
            (provider.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL) == 0

    /** True when [provider] can be reconfigured from a context menu after it was added. */
    fun isReconfigurable(provider: AppWidgetProviderInfo): Boolean =
        provider.configure != null &&
            (provider.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0

    /**
     * Opens the configuration activity of the provider bound to [appWidgetId] through the host
     * (`AppWidgetHost.startAppWidgetConfigureActivityForResult`). The result arrives in
     * [activity]'s `onActivityResult(requestCode, resultCode, data)`, which must be forwarded to
     * [onActivityResult]; [onResult] then runs with `configured == true` on `RESULT_OK`.
     *
     * Returns false (and does not register [onResult]) when the activity could not be started,
     * e.g. the provider has no configure activity any more.
     */
    fun startConfigure(
        activity: Activity,
        appWidgetId: Int,
        requestCode: Int = REQUEST_CONFIGURE_APPWIDGET,
        onResult: ((appWidgetId: Int, configured: Boolean) -> Unit)? = null,
    ): Boolean {
        if (onResult != null) pendingConfigure[requestCode] = PendingConfigure(appWidgetId, onResult)
        return try {
            host.startAppWidgetConfigureActivityForResult(activity, appWidgetId, 0, requestCode, null)
            true
        } catch (e: ActivityNotFoundException) {
            Log.w(TAG, "No configure activity for app widget $appWidgetId", e)
            pendingConfigure.remove(requestCode)
            false
        } catch (e: RuntimeException) {
            Log.w(TAG, "startAppWidgetConfigureActivityForResult($appWidgetId) failed", e)
            pendingConfigure.remove(requestCode)
            false
        }
    }

    /**
     * Plain `ACTION_APPWIDGET_CONFIGURE` intent for [provider]'s configure activity, for callers
     * that cannot go through [startConfigure]. Only exported configure activities accept it; prefer
     * [startConfigure].
     */
    fun configureIntent(appWidgetId: Int, provider: AppWidgetProviderInfo): Intent? {
        val component = provider.configure ?: return null
        return Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE)
            .setComponent(component)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
    }

    /**
     * Forward every `Activity.onActivityResult` here. Returns true when [requestCode] belonged to
     * a [startConfigure] call, after invoking its callback. When a configure result arrives with
     * no registered callback (the composition that started it is gone), a cancelled result deletes
     * the id so no orphaned binding is left behind.
     */
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        val pending = pendingConfigure.remove(requestCode)
        val configured = resultCode == Activity.RESULT_OK
        if (pending == null) {
            if (requestCode == REQUEST_CONFIGURE_APPWIDGET && !configured) {
                val id = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
                    ?: AppWidgetManager.INVALID_APPWIDGET_ID
                deleteId(id)
                return true
            }
            return false
        }
        val fromIntent = data?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        val id = if (fromIntent != AppWidgetManager.INVALID_APPWIDGET_ID) fromIntent else pending.appWidgetId
        pending.onResult(id, configured)
        return true
    }

    // -----------------------------------------------------------------------------------------
    // Sizing
    // -----------------------------------------------------------------------------------------

    /** Tells the provider of [appWidgetId] that it is shown in a [widthDp] x [heightDp] box. */
    fun updateSize(appWidgetId: Int, widthDp: Int, heightDp: Int) {
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return
        try {
            val current = manager.getAppWidgetOptions(appWidgetId)
            manager.updateAppWidgetOptions(appWidgetId, WidgetSizing.sizeOptions(widthDp, heightDp, current))
        } catch (e: RuntimeException) {
            Log.w(TAG, "updateAppWidgetOptions($appWidgetId) failed", e)
        }
    }

    companion object {
        private const val TAG = "WidgetHost"

        /** Request code for the `ACTION_APPWIDGET_BIND` permission dialog when using `startActivityForResult`. */
        const val REQUEST_BIND_APPWIDGET: Int = 0x4F10

        /** Default request code for [startConfigure]. */
        const val REQUEST_CONFIGURE_APPWIDGET: Int = 0x4F11

        @Volatile
        private var sharedInstance: WidgetHostController? = null

        /**
         * The process-wide controller for [host]; one instance per host so pending configure
         * callbacks are shared between the activity and the composition that started them.
         */
        fun shared(context: Context, host: AppWidgetHost): WidgetHostController {
            val existing = sharedInstance
            if (existing != null && existing.host === host) return existing
            return synchronized(this) {
                val again = sharedInstance
                if (again != null && again.host === host) {
                    again
                } else {
                    val appContext = context.applicationContext
                    val created = WidgetHostController(appContext, host, AppWidgetManager.getInstance(appContext))
                    sharedInstance = created
                    created
                }
            }
        }

        /** [shared] for the graph's host. */
        fun from(context: Context, graph: AppGraph): WidgetHostController = shared(context, graph.appWidgetHost)
    }
}
