package com.foldos.launcher.icons

import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.os.UserHandle
import android.util.Log
import android.util.Xml
import com.foldos.launcher.core.repo.IconPackInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream

/**
 * Parsed contents of one icon pack (the standard ADW / Nova / Apex `appfilter.xml` format).
 *
 * @property components "package/activity" -> drawable name, from `<item component="ComponentInfo{...}" drawable="..."/>`
 * @property byPackage package -> drawable name of the first entry seen for that package (used when
 *   the exact activity is not listed)
 * @property iconBacks `<iconback img1=.../>` drawables layered under icons the pack does not cover
 * @property iconMasks `<iconmask img1=.../>` drawables punched out of those icons (DST_OUT)
 * @property iconUpons `<iconupon img1=.../>` drawables layered over them
 * @property scale `<scale factor="..."/>`: how much an uncovered app icon shrinks inside the back
 * @property drawables every drawable name the pack lists in `drawable.xml`, or the distinct
 *   appfilter values when it ships none
 */
class IconPackData(
    val packageName: String,
    val label: String,
    val components: Map<String, String>,
    val byPackage: Map<String, String>,
    val iconBacks: List<String>,
    val iconMasks: List<String>,
    val iconUpons: List<String>,
    val scale: Float,
    val drawables: List<String>,
) {
    /** True when the pack wants uncovered icons composed with its back / mask / upon layers. */
    val composesUncovered: Boolean
        get() = iconBacks.isNotEmpty() || iconMasks.isNotEmpty() || iconUpons.isNotEmpty()

    /** The drawable name the pack assigns to this activity, by exact component first, then by package. */
    fun drawableFor(packageName: String, activityName: String): String? =
        components["$packageName/$activityName"] ?: byPackage[packageName]
}

/**
 * Discovers installed icon packs and parses them on demand. One instance per process (see [get]);
 * parsed packs and their [Resources] are cached until the package changes.
 */
class IconPackLoader private constructor(context: Context) {

    private val appContext: Context = context.applicationContext
    private val packageManager: PackageManager = appContext.packageManager
    private val launcherApps: LauncherApps? = appContext.getSystemService(LauncherApps::class.java)

    private val lock = Any()
    private val packs = HashMap<String, IconPackData>()
    private val resources = HashMap<String, Resources>()

    /** Installed icon packs, A-Z by label. Queries the four theme intents; each package once. */
    fun installedPacks(): List<IconPackInfo> {
        val found = LinkedHashMap<String, IconPackInfo>()
        for (action in THEME_ACTIONS) {
            val matches = try {
                packageManager.queryIntentActivities(Intent(action), PackageManager.ResolveInfoFlags.of(0L))
            } catch (e: Exception) {
                Log.w(TAG, "queryIntentActivities($action) failed", e)
                emptyList()
            }
            for (match in matches) {
                val activity = match.activityInfo ?: continue
                val packageName = activity.packageName ?: continue
                if (packageName == appContext.packageName || found.containsKey(packageName)) continue
                val label = try {
                    packageManager.getApplicationLabel(activity.applicationInfo).toString()
                } catch (e: Exception) {
                    packageName
                }
                found[packageName] = IconPackInfo(packageName = packageName, label = label)
            }
        }
        return found.values.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.label })
    }

    /** [installedPacks] now and again after every package install, removal or update. */
    fun packsFlow(): Flow<List<IconPackInfo>> = packageChanges()
        .onStart { emit(null) }
        .map { installedPacks() }
        .distinctUntilChanged()
        .flowOn(Dispatchers.IO)

    /**
     * Emits the package name of every package that was added, removed, changed or replaced (null
     * when LauncherApps did not say which). Parsed data for that package is dropped first, so the
     * next [load] re-reads it.
     */
    fun packageChanges(): Flow<String?> = callbackFlow {
        val apps = launcherApps
        if (apps == null) {
            awaitClose { }
            return@callbackFlow
        }
        val callback = object : LauncherApps.Callback() {
            override fun onPackageRemoved(packageName: String?, user: UserHandle?) = changed(packageName)

            override fun onPackageAdded(packageName: String?, user: UserHandle?) = changed(packageName)

            override fun onPackageChanged(packageName: String?, user: UserHandle?) = changed(packageName)

            override fun onPackagesAvailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
                if (packageNames.isNullOrEmpty()) changed(null) else packageNames.forEach { changed(it) }
            }

            override fun onPackagesUnavailable(packageNames: Array<out String>?, user: UserHandle?, replacing: Boolean) {
                if (packageNames.isNullOrEmpty()) changed(null) else packageNames.forEach { changed(it) }
            }

            private fun changed(packageName: String?) {
                invalidate(packageName)
                trySend(packageName)
            }
        }
        apps.registerCallback(callback, Handler(Looper.getMainLooper()))
        awaitClose { apps.unregisterCallback(callback) }
    }.conflate()

    /** The parsed pack, or null when [packageName] is not installed or has no readable resources. */
    fun load(packageName: String): IconPackData? {
        synchronized(lock) { packs[packageName]?.let { return it } }
        val parsed = parse(packageName) ?: return null
        synchronized(lock) { packs[packageName] = parsed }
        return parsed
    }

    /** A drawable from [packageName]'s resources by name, or null when missing. Works for any installed app. */
    fun drawable(packageName: String, drawableName: String): Drawable? {
        val res = resourcesFor(packageName) ?: return null
        val id = res.getIdentifier(drawableName, "drawable", packageName)
        if (id == 0) return null
        return try {
            res.getDrawable(id, null)
        } catch (e: Exception) {
            Log.w(TAG, "Could not load $packageName:$drawableName", e)
            null
        }
    }

    /** Forgets cached data for one package, or for every package when [packageName] is null. */
    fun invalidate(packageName: String?) {
        synchronized(lock) {
            if (packageName == null) {
                packs.clear()
                resources.clear()
            } else {
                packs.remove(packageName)
                resources.remove(packageName)
            }
        }
    }

    private fun resourcesFor(packageName: String): Resources? {
        synchronized(lock) { resources[packageName]?.let { return it } }
        val res = try {
            packageManager.getResourcesForApplication(packageName)
        } catch (e: Exception) {
            return null
        }
        synchronized(lock) { resources[packageName] = res }
        return res
    }

    private fun parse(packageName: String): IconPackData? {
        val res = resourcesFor(packageName) ?: return null
        val label = try {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString()
        } catch (e: Exception) {
            packageName
        }

        val components = LinkedHashMap<String, String>()
        val byPackage = HashMap<String, String>()
        val backs = ArrayList<String>()
        val masks = ArrayList<String>()
        val upons = ArrayList<String>()
        var scale = 1f

        openXml(res, packageName, "appfilter")?.use { parser ->
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when (parser.name) {
                        "item" -> {
                            val component = parser.getAttributeValue(null, "component")
                            val drawable = parser.getAttributeValue(null, "drawable")
                            if (!component.isNullOrEmpty() && !drawable.isNullOrEmpty()) {
                                val key = componentKey(component)
                                if (key != null) {
                                    val pkg = key.substringBefore('/')
                                    if (key.contains('/')) {
                                        if (!components.containsKey(key)) components[key] = drawable
                                    }
                                    if (!byPackage.containsKey(pkg)) byPackage[pkg] = drawable
                                }
                            }
                        }
                        "iconback" -> readImages(parser, backs)
                        "iconmask" -> readImages(parser, masks)
                        "iconupon" -> readImages(parser, upons)
                        "scale" -> parser.getAttributeValue(null, "factor")?.toFloatOrNull()?.let { factor ->
                            if (factor > 0f && factor <= 1.5f) scale = factor
                        }
                    }
                }
                event = parser.next()
            }
        }

        val drawables = LinkedHashSet<String>()
        openXml(res, packageName, "drawable")?.use { parser ->
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name == "item") {
                    parser.getAttributeValue(null, "drawable")?.takeIf { it.isNotEmpty() }?.let { drawables.add(it) }
                }
                event = parser.next()
            }
        }
        if (drawables.isEmpty()) drawables.addAll(components.values)

        return IconPackData(
            packageName = packageName,
            label = label,
            components = components,
            byPackage = byPackage,
            iconBacks = backs,
            iconMasks = masks,
            iconUpons = upons,
            scale = scale,
            drawables = drawables.toList(),
        )
    }

    private fun readImages(parser: XmlPullParser, into: MutableList<String>) {
        for (i in 0 until parser.attributeCount) {
            val name = parser.getAttributeName(i) ?: continue
            if (name.startsWith("img")) {
                val value = parser.getAttributeValue(i)
                if (!value.isNullOrEmpty()) into.add(value)
            }
        }
    }

    /**
     * Opens `res/xml/<name>.xml` first, then `assets/<name>.xml`. The returned source wraps the
     * parser and the stream or resource parser to close.
     */
    private fun openXml(res: Resources, packageName: String, name: String): XmlSource? {
        val id = res.getIdentifier(name, "xml", packageName)
        if (id != 0) {
            try {
                val parser = res.getXml(id)
                return XmlSource(parser) { parser.close() }
            } catch (e: Exception) {
                Log.w(TAG, "res/xml/$name.xml unreadable in $packageName", e)
            }
        }
        val stream: InputStream = try {
            res.assets.open("$name.xml")
        } catch (e: Exception) {
            return null
        }
        return try {
            val parser = Xml.newPullParser()
            parser.setInput(stream, null)
            XmlSource(parser) { stream.close() }
        } catch (e: Exception) {
            try {
                stream.close()
            } catch (ignored: Exception) {
            }
            null
        }
    }

    private class XmlSource(val parser: XmlPullParser, private val onClose: () -> Unit) {
        fun <R> use(block: (XmlPullParser) -> R): R? {
            return try {
                block(parser)
            } catch (e: Exception) {
                Log.w(TAG, "Icon pack XML parse failed", e)
                null
            } finally {
                try {
                    onClose()
                } catch (ignored: Exception) {
                }
            }
        }
    }

    companion object {
        private const val TAG = "IconPackLoader"

        /** Intents an icon pack declares to advertise itself to launchers. Mirrored in the manifest `<queries>`. */
        val THEME_ACTIONS: List<String> = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME",
        )

        @Volatile
        private var instance: IconPackLoader? = null

        /** The process-wide loader, so the resolver and the picker sheets share one parse cache. */
        fun get(context: Context): IconPackLoader {
            instance?.let { return it }
            synchronized(this) {
                instance?.let { return it }
                val created = IconPackLoader(context)
                instance = created
                return created
            }
        }

        /** "ComponentInfo{pkg/activity}" -> "pkg/activity"; relative activity names (".Main") are expanded. */
        fun componentKey(component: String): String? {
            val start = component.indexOf('{')
            val end = component.lastIndexOf('}')
            val inner = if (start >= 0 && end > start) component.substring(start + 1, end) else component
            val trimmed = inner.trim()
            if (trimmed.isEmpty()) return null
            val slash = trimmed.indexOf('/')
            if (slash < 0) return trimmed
            val pkg = trimmed.substring(0, slash)
            val activity = trimmed.substring(slash + 1)
            if (pkg.isEmpty()) return null
            if (activity.isEmpty()) return pkg
            return if (activity.startsWith(".")) "$pkg/$pkg$activity" else "$pkg/$activity"
        }
    }
}
