package com.foldos.launcher.icons

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.util.Log
import android.util.LruCache
import com.foldos.launcher.core.model.AppInfo
import com.foldos.launcher.core.repo.IconPackInfo
import com.foldos.launcher.core.repo.IconResolver
import com.foldos.launcher.core.repo.IconShape
import com.foldos.launcher.core.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * The FoldOS [IconResolver]: icon pack lookup (standard `appfilter.xml`), per-app overrides from
 * [IconOverrideStore], then [IconShape] masking through [IconShaper]. Resolved drawables are kept in
 * an LRU cache keyed by app key + pack + shape + override, which is cleared whenever any of those
 * change. Replaces `PassthroughIconResolver` in `AppGraphImpl.icons`.
 *
 * [icon] never calls `AppInfo.iconLoader`; the raw icon comes from LauncherApps directly, so the
 * apps repository may route its `iconLoader` through this resolver without recursion.
 *
 * Resolution order for an app:
 * 1. a per-app override (`drawableName` from `packageName`, or from the selected pack when null)
 * 2. the selected pack's drawable for the exact component, then for the package
 * 3. the selected pack's iconback / iconmask / iconupon composition, when the pack defines them
 * 4. the app's own icon masked to the selected shape (SYSTEM = untouched)
 */
class FoldOsIconResolver(
    context: Context,
    private val settingsRepository: SettingsRepository,
) : IconResolver {

    private val appContext: Context = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val launcherApps: LauncherApps? = appContext.getSystemService(LauncherApps::class.java)
    private val densityDpi: Int = appContext.resources.displayMetrics.densityDpi

    /** Shared with the picker sheets so packs are parsed once per process. */
    val loader: IconPackLoader = IconPackLoader.get(appContext)

    /** Exposed for previews (Settings > Look > Icon shape). */
    val shaper: IconShaper = IconShaper(appContext)

    private val overrideStore = IconOverrideStore(appContext)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val cache = LruCache<String, Drawable>(CACHE_ENTRIES)

    private data class State(
        val pack: String?,
        val shape: IconShape,
        val overrides: Map<String, IconOverride>,
    )

    @Volatile
    private var state: State = State(pack = null, shape = IconShape.SYSTEM, overrides = emptyMap())

    private val generationFlow = MutableStateFlow(0)

    /**
     * Increments whenever the pack, shape, an override or an installed pack changes, i.e. whenever
     * icons returned by [icon] may differ from before. UI caches keyed by app key should include
     * this value (or collect it and drop their bitmaps when it changes).
     */
    val generation: StateFlow<Int> = generationFlow.asStateFlow()

    /** The currently selected pack's parsed data, or null for system icons. */
    val currentPack: IconPackData?
        get() = state.pack?.let { loader.load(it) }

    init {
        scope.launch {
            val choice = settingsRepository.settings
                .map { it.iconPack to it.iconShape }
                .distinctUntilChanged()
            combine(choice, overrideStore.overrides) { (pack, shape), overrides ->
                State(pack = pack, shape = shape, overrides = overrides)
            }
                .distinctUntilChanged()
                .collect { next ->
                    state = next
                    cache.evictAll()
                    bump()
                    val pack = next.pack
                    if (pack != null) {
                        try {
                            loader.load(pack)
                        } catch (e: Exception) {
                            Log.w(TAG, "Could not parse icon pack $pack", e)
                        }
                    }
                }
        }
        scope.launch {
            loader.packageChanges().collect { changedPackage ->
                val current = state
                val affected = changedPackage == null ||
                    changedPackage == current.pack ||
                    current.overrides.values.any { it.packageName == changedPackage }
                if (affected) {
                    cache.evictAll()
                    bump()
                }
            }
        }
    }

    override fun icon(app: AppInfo): Drawable {
        val current = state
        val override = current.overrides[app.key]
        val key = cacheKey(app, current, override)
        cache.get(key)?.let { return detached(it) }
        val resolved = try {
            resolve(app, current, override)
        } catch (e: Exception) {
            Log.w(TAG, "Icon resolution failed for ${app.key}", e)
            rawIcon(app)
        }
        cache.put(key, resolved)
        return detached(resolved)
    }

    override val packs: Flow<List<IconPackInfo>> = loader.packsFlow()

    override suspend fun setPack(packageName: String?) {
        settingsRepository.update { it.copy(iconPack = packageName) }
    }

    override suspend fun setShape(shape: IconShape) {
        settingsRepository.update { it.copy(iconShape = shape) }
    }

    override suspend fun override(appKey: String, packageName: String?, drawableName: String?) {
        if (packageName == null && drawableName == null) {
            overrideStore.clear(appKey)
        } else {
            overrideStore.set(appKey, IconOverride(packageName = packageName, drawableName = drawableName))
        }
    }

    /** The override stored for [appKey], if any (for the per-app sheet). */
    fun overrideFor(appKey: String): IconOverride? = state.overrides[appKey]

    /** The app's own icon as the system reports it, without pack or shape. */
    fun rawIcon(app: AppInfo): Drawable {
        val apps = launcherApps
        if (apps != null) {
            val component = ComponentName(app.packageName, app.activityName)
            val info = try {
                apps.resolveActivity(Intent(Intent.ACTION_MAIN).setComponent(component), app.user)
            } catch (e: Exception) {
                null
            }
            if (info != null) {
                try {
                    return info.getIcon(densityDpi)
                } catch (e: Exception) {
                    Log.w(TAG, "LauncherActivityInfo.getIcon failed for ${app.key}", e)
                }
            }
        }
        return try {
            packageManager.getApplicationIcon(app.packageName)
        } catch (e: Exception) {
            packageManager.defaultActivityIcon
        }
    }

    private fun resolve(app: AppInfo, current: State, override: IconOverride?): Drawable {
        if (override != null) {
            val drawableName = override.drawableName
            val sourcePackage = override.packageName ?: current.pack
            if (drawableName != null && sourcePackage != null) {
                loader.drawable(sourcePackage, drawableName)?.let { return it }
            }
        }
        val packName = current.pack
        if (packName != null) {
            val pack = loader.load(packName)
            if (pack != null) {
                val name = pack.drawableFor(app.packageName, app.activityName)
                if (name != null) loader.drawable(pack.packageName, name)?.let { return it }
                shaper.composeWithPack(rawIcon(app), pack, loader, app.key.hashCode())?.let { return it }
            }
        }
        return shaper.shape(rawIcon(app), current.shape)
    }

    private fun cacheKey(app: AppInfo, current: State, override: IconOverride?): String = buildString {
        append(app.key)
        append('|')
        append(current.pack ?: "")
        append('|')
        append(current.shape.name)
        if (override != null) {
            append('|')
            append(override.packageName ?: "")
            append('/')
            append(override.drawableName ?: "")
        }
    }

    /**
     * A fresh drawable sharing the cached one's pixels, so concurrent callers setting bounds while
     * rasterising do not interfere with each other.
     */
    private fun detached(drawable: Drawable): Drawable =
        drawable.constantState?.newDrawable(appContext.resources)?.mutate() ?: drawable

    private fun bump() {
        generationFlow.value = generationFlow.value + 1
    }

    private companion object {
        const val TAG = "FoldOsIconResolver"
        const val CACHE_ENTRIES = 512
    }
}
