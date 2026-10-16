package com.foldos.launcher.core.repo

import android.graphics.drawable.Drawable
import com.foldos.launcher.core.model.AppInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable

/** An installed icon pack (standard `appfilter.xml` format). */
data class IconPackInfo(
    val packageName: String,
    val label: String,
)

/** Adaptive icon mask applied to every app icon (Settings > Look > Icon shape). */
@Serializable
enum class IconShape { SYSTEM, CIRCLE, SQUIRCLE, ROUNDED, TEARDROP }

/**
 * Turns an [AppInfo] into the icon drawn on the home screen, dock and drawer: icon pack lookup,
 * per-app override, then shape masking. Implemented by the Icons agent; the Data agent ships a
 * passthrough that returns `app.iconLoader()`.
 */
interface IconResolver {
    /** The icon to draw for [app]; falls back to the app's own icon when no pack covers it. */
    fun icon(app: AppInfo): Drawable

    /** Installed icon packs, re-emitted on install or removal. */
    val packs: Flow<List<IconPackInfo>>

    /** Selects an icon pack by package name; null = system icons. Persists to `LauncherSettings.iconPack`. */
    suspend fun setPack(packageName: String?)

    /** Persists to `LauncherSettings.iconShape`. */
    suspend fun setShape(shape: IconShape)

    /**
     * Overrides one app's icon with [drawableName] from [packageName] (an icon pack or any app);
     * both null clears the override. [appKey] is `AppInfo.key` ("package/activity").
     */
    suspend fun override(appKey: String, packageName: String?, drawableName: String?)
}
