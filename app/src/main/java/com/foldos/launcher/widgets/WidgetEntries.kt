package com.foldos.launcher.widgets

import android.appwidget.AppWidgetProviderInfo
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import com.foldos.launcher.core.model.WidgetCategory
import com.foldos.launcher.core.model.WidgetEntry

/**
 * Turns an installed provider into a gallery row.
 *
 * - `id` / `provider` = the provider component (`flattenToString()`); [categoryOf] receives that
 *   same string (`"package/class"`) and returns the category to file the widget under.
 * - `name` = `loadLabel(pm)`, `source` = the owning app's label (package name when unknown).
 * - Spans: `targetCellWidth/Height` when the provider declares them (Android 12+), else
 *   `minWidth/minHeight` on [WidgetSizing.DEFAULT_CELL_DP] (76dp) cells; minimum spans come from
 *   `minResizeWidth/Height` when the matching `resizeMode` bit is set, never above the default.
 * - `previewLoader` loads `previewImage`, then the provider `icon`, then the app icon, through
 *   [PackageManager.getDrawable] so no Context is needed.
 */
fun AppWidgetProviderInfo.toWidgetEntry(pm: PackageManager, categoryOf: (String) -> WidgetCategory): WidgetEntry {
    val component = provider
    val packageName = component.packageName
    val appInfo: ApplicationInfo? = try {
        pm.getApplicationInfo(packageName, 0)
    } catch (e: PackageManager.NameNotFoundException) {
        null
    }
    val source = appInfo?.let { info -> pm.getApplicationLabel(info).toString() } ?: packageName
    val name: String = try {
        loadLabel(pm)
    } catch (e: RuntimeException) {
        null
    } ?: packageName

    val defaultX = if (targetCellWidth > 0) targetCellWidth else WidgetSizing.spansFor(minWidth)
    val defaultY = if (targetCellHeight > 0) targetCellHeight else WidgetSizing.spansFor(minHeight)
    val resizesHorizontally = (resizeMode and AppWidgetProviderInfo.RESIZE_HORIZONTAL) != 0
    val resizesVertically = (resizeMode and AppWidgetProviderInfo.RESIZE_VERTICAL) != 0
    val minX = if (resizesHorizontally && minResizeWidth > 0) WidgetSizing.spansFor(minResizeWidth).coerceAtMost(defaultX) else defaultX
    val minY = if (resizesVertically && minResizeHeight > 0) WidgetSizing.spansFor(minResizeHeight).coerceAtMost(defaultY) else defaultY

    val id = component.flattenToString()
    val previewRes = previewImage
    val iconRes = icon
    return WidgetEntry(
        id = id,
        name = name,
        source = source,
        category = categoryOf(id),
        minSpanX = minX.coerceAtLeast(1),
        minSpanY = minY.coerceAtLeast(1),
        defaultSpanX = defaultX.coerceAtLeast(1),
        defaultSpanY = defaultY.coerceAtLeast(1),
        isBuiltIn = false,
        provider = component,
        previewLoader = {
            loadPackageDrawable(pm, packageName, appInfo, previewRes)
                ?: loadPackageDrawable(pm, packageName, appInfo, iconRes)
                ?: try {
                    pm.getApplicationIcon(packageName)
                } catch (e: PackageManager.NameNotFoundException) {
                    null
                }
        },
    )
}

/** True when the provider may be placed on a home screen (not keyguard-only or search-box-only). */
val AppWidgetProviderInfo.supportsHomeScreen: Boolean
    get() = (widgetCategory and AppWidgetProviderInfo.WIDGET_CATEGORY_HOME_SCREEN) != 0

/** Largest spans the provider allows when resizing (Android 12 `maxResizeWidth/Height`), or null for unlimited. */
val AppWidgetProviderInfo.maxSpans: Pair<Int, Int>?
    get() {
        if (maxResizeWidth <= 0 && maxResizeHeight <= 0) return null
        val maxX = if (maxResizeWidth > 0) WidgetSizing.spansFor(maxResizeWidth) else Int.MAX_VALUE
        val maxY = if (maxResizeHeight > 0) WidgetSizing.spansFor(maxResizeHeight) else Int.MAX_VALUE
        return maxX to maxY
    }

private fun loadPackageDrawable(
    pm: PackageManager,
    packageName: String,
    appInfo: ApplicationInfo?,
    resId: Int,
): Drawable? {
    if (resId == 0 || appInfo == null) return null
    return try {
        pm.getDrawable(packageName, resId, appInfo)
    } catch (e: RuntimeException) {
        null
    }
}
