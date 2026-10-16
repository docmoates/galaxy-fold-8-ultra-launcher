package com.foldos.launcher.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContract

/** Input of [BindAppWidgetContract]: bind [provider] to the already allocated [appWidgetId]. */
data class BindAppWidgetRequest(
    val appWidgetId: Int,
    val provider: AppWidgetProviderInfo,
    /** Initial widget options, normally [WidgetSizing.sizeOptions]; null for none. */
    val options: Bundle? = null,
)

/**
 * Output of [BindAppWidgetContract]. [appWidgetId] is the id from the result intent, or
 * `AppWidgetManager.INVALID_APPWIDGET_ID` when the system did not echo it (use the request's id
 * then). [bound] is true when the user allowed the binding.
 */
data class BindAppWidgetResult(
    val appWidgetId: Int,
    val bound: Boolean,
)

/**
 * `ActivityResultContract` for the system "Allow FoldOS to create widgets?" dialog
 * (`AppWidgetManager.ACTION_APPWIDGET_BIND`). Use it with `rememberLauncherForActivityResult` or
 * `ComponentActivity.registerForActivityResult`; no Activity of our own is needed.
 */
class BindAppWidgetContract : ActivityResultContract<BindAppWidgetRequest, BindAppWidgetResult>() {

    override fun createIntent(context: Context, input: BindAppWidgetRequest): Intent {
        val intent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, input.appWidgetId)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, input.provider.provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, input.provider.profile)
        val options = input.options
        if (options != null) intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_OPTIONS, options)
        return intent
    }

    override fun parseResult(resultCode: Int, intent: Intent?): BindAppWidgetResult {
        val id = intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        return BindAppWidgetResult(appWidgetId = id, bound = resultCode == Activity.RESULT_OK)
    }
}
