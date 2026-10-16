package com.foldos.launcher.widgets

import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.util.Log
import android.util.SizeF
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.foldos.launcher.core.LocalAppGraph

/**
 * Shows the hosted app widget [appWidgetId] through `graph.appWidgetHost.createView`.
 *
 * [provider] is the bound provider when the caller has it; null makes the view look it up with
 * `AppWidgetManager.getAppWidgetInfo`. [widthDp] x [heightDp] is the box the widget fills; it is
 * reported to the provider with `AppWidgetHostView.updateAppWidgetSize` (min/max width/height
 * options plus the Android 12 size list) whenever it changes. A new `AppWidgetHostView` is created
 * when [appWidgetId] changes.
 */
@Composable
fun AppWidgetView(
    appWidgetId: Int,
    provider: AppWidgetProviderInfo?,
    widthDp: Int,
    heightDp: Int,
    modifier: Modifier = Modifier,
) {
    val host = LocalAppGraph.current.appWidgetHost
    val context = LocalContext.current
    val resolved: AppWidgetProviderInfo? = remember(appWidgetId, provider) {
        provider ?: try {
            AppWidgetManager.getInstance(context)?.getAppWidgetInfo(appWidgetId)
        } catch (e: RuntimeException) {
            Log.w(TAG, "getAppWidgetInfo($appWidgetId) failed", e)
            null
        }
    }
    key(appWidgetId) {
        val lastSize = remember { IntArray(2) }
        AndroidView(
            factory = { viewContext ->
                val view: AppWidgetHostView = host.createView(viewContext, appWidgetId, resolved)
                view.setPadding(0, 0, 0, 0)
                view
            },
            modifier = modifier,
            update = { view ->
                if (widthDp > 0 && heightDp > 0 && (lastSize[0] != widthDp || lastSize[1] != heightDp)) {
                    lastSize[0] = widthDp
                    lastSize[1] = heightDp
                    try {
                        view.updateAppWidgetSize(
                            WidgetSizing.sizeOptions(widthDp, heightDp),
                            listOf(SizeF(widthDp.toFloat(), heightDp.toFloat())),
                        )
                    } catch (e: RuntimeException) {
                        Log.w(TAG, "updateAppWidgetSize($appWidgetId) failed", e)
                    }
                }
            },
        )
    }
}

private const val TAG = "AppWidgetView"
