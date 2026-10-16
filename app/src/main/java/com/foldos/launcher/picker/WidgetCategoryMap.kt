package com.foldos.launcher.picker

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.util.Log
import com.foldos.launcher.core.model.WidgetCategory
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

/**
 * Assigns a [WidgetCategory] to an AppWidget provider.
 *
 * Order of precedence:
 * 1. the bundled `assets/widget_categories.json` map (package name -> enum name), built from the
 *    sources in `docs/widget-catalog.json`;
 * 2. a conservative keyword pass over the provider label and package name (so an unlisted
 *    "... Clock" widget still lands in Clock & Time);
 * 3. the app's own [ApplicationInfo.category] (AUDIO / VIDEO -> MEDIA, IMAGE -> PHOTOS, SOCIAL ->
 *    SOCIAL, NEWS -> NEWS, MAPS -> TRAVEL, PRODUCTIVITY -> PRODUCTIVITY, GAME -> FUN);
 * 4. [WidgetCategory.UTILITIES].
 */
class WidgetCategoryMap(context: Context) {

    private val appContext: Context = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager

    /** Lazily parsed asset; an unreadable or malformed asset simply yields an empty map. */
    private val byPackage: Map<String, WidgetCategory> by lazy { loadAsset() }

    /** Category for the provider [packageName] with the user-visible [label]. */
    fun categoryFor(packageName: String, label: String?): WidgetCategory {
        byPackage[packageName]?.let { return it }
        keywordCategory(packageName, label)?.let { return it }
        return applicationInfoCategory(packageName) ?: WidgetCategory.UTILITIES
    }

    private fun loadAsset(): Map<String, WidgetCategory> {
        val text = try {
            appContext.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        } catch (e: Exception) {
            Log.w(TAG, "Could not read $ASSET_NAME", e)
            return emptyMap()
        }
        return try {
            val root = Json.parseToJsonElement(text).jsonObject
            buildMap {
                for ((key, value) in root) {
                    if (key.startsWith("_")) continue
                    val name = (value as? JsonPrimitive)?.content ?: continue
                    val category = WidgetCategory.entries.firstOrNull { it.name == name } ?: continue
                    put(key, category)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Malformed $ASSET_NAME", e)
            emptyMap()
        }
    }

    private fun keywordCategory(packageName: String, label: String?): WidgetCategory? {
        val haystack = ((label ?: "") + " " + packageName).lowercase()
        for ((keywords, category) in KEYWORDS) {
            if (keywords.any { haystack.contains(it) }) return category
        }
        return null
    }

    private fun applicationInfoCategory(packageName: String): WidgetCategory? {
        val info: ApplicationInfo = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        }
        return when (info.category) {
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> WidgetCategory.MEDIA
            ApplicationInfo.CATEGORY_IMAGE -> WidgetCategory.PHOTOS
            ApplicationInfo.CATEGORY_SOCIAL -> WidgetCategory.SOCIAL
            ApplicationInfo.CATEGORY_NEWS -> WidgetCategory.NEWS
            ApplicationInfo.CATEGORY_MAPS -> WidgetCategory.TRAVEL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> WidgetCategory.PRODUCTIVITY
            ApplicationInfo.CATEGORY_GAME -> WidgetCategory.FUN
            else -> null
        }
    }

    companion object {
        const val ASSET_NAME: String = "widget_categories.json"
        private const val TAG = "WidgetCategoryMap"

        /** Keyword -> category, checked in order; the first hit wins. Deliberately short. */
        private val KEYWORDS: List<Pair<List<String>, WidgetCategory>> = listOf(
            listOf("weather", "forecast", "radar") to WidgetCategory.WEATHER,
            listOf("clock", "alarm", "stopwatch", "timer") to WidgetCategory.CLOCK,
            listOf("calendar", "agenda", "task", "todo", "to-do", "reminder") to WidgetCategory.CALENDAR,
            listOf("battery", "storage", "data usage", "wifi", "wi-fi", "network", "device care") to WidgetCategory.DEVICE,
            listOf("photo", "gallery") to WidgetCategory.PHOTOS,
            listOf("music", "podcast", "radio", "player", "playlist") to WidgetCategory.MEDIA,
            listOf("mail", "message", "contact", "chat") to WidgetCategory.COMMUNICATION,
            listOf("note", "memo", "notebook") to WidgetCategory.PRODUCTIVITY,
            listOf("health", "fitness", "steps", "sleep", "workout") to WidgetCategory.HEALTH,
            listOf("map", "transit", "navigation", "flight") to WidgetCategory.TRAVEL,
            listOf("news", "headline", "feed") to WidgetCategory.NEWS,
            listOf("stock", "crypto", "wallet", "bank", "budget", "finance") to WidgetCategory.FINANCE,
            listOf("smartthings", "thermostat", "lights", "smart home") to WidgetCategory.SMART_HOME,
            listOf("search") to WidgetCategory.SEARCH,
        )
    }
}
