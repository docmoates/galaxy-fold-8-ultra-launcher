package com.foldos.launcher.home

import com.foldos.launcher.core.model.AppInfo
import java.util.Locale

/** Most apps the first-run seeding puts on the first home page. */
const val HOME_SEED_APP_LIMIT: Int = 16

/**
 * Fragments tried in order to pick the first home page's apps on a fresh install. Each entry is one
 * app: its needles are tried in order, and the first needle that matches an installed app (in its
 * package name or its label, case-insensitive) decides which app that entry stands for.
 */
private val HOME_PRIORITY: List<List<String>> = listOf(
    listOf("chrome"),
    listOf("gmail"),
    listOf("photos"),
    listOf("maps"),
    listOf("youtube"),
    listOf("calendar"),
    listOf("drive"),
    listOf("keep"),
    listOf("spotify"),
    listOf("messages", "messaging"),
    listOf("clock"),
    listOf("calculator"),
    listOf("camera"),
    listOf("files"),
    listOf("com.android.settings", "settings"),
    listOf("com.android.vending", "play store"),
)

/**
 * Dock defaults: phone, messages, browser (Chrome first), camera, then a few more well-known apps.
 */
private val DOCK_PRIORITY_ORDER: List<List<String>> = listOf(
    listOf("dialer", "contacts.phone", ".phone"),
    listOf("messaging", "messages", ".mms"),
    listOf("chrome", "sbrowser", "browser"),
    listOf("camera"),
    listOf("photos", "gallery"),
    listOf("gmail", "email"),
    listOf("maps"),
    listOf("youtube"),
    listOf("spotify", "music"),
    listOf("calendar"),
    listOf("com.android.vending", "galaxyapps", "samsungapps"),
    listOf("android.settings"),
)

/** Package prefixes of the system / first-party apps that win a tie, best first (Google apps for the home page). */
private val HOME_VENDORS: List<String> = listOf("com.google.", "com.android.", "com.samsung.", "com.sec.")

/** The dock prefers the phone maker's own phone / messages / camera apps. */
private val DOCK_VENDORS: List<String> = listOf("com.samsung.", "com.sec.", "com.google.", "com.android.")

/** The launcher itself is never one of the preferred apps, and sorts last among the rest. */
private const val OWN_PACKAGE_PREFIX = "com.foldos.launcher"

/**
 * The apps the first home page is seeded with: up to `min(count, HOME_SEED_APP_LIMIT)`. First one
 * app per entry of the preference list (chrome, gmail, photos, maps, youtube, calendar, drive,
 * keep, spotify, messages, clock, calculator, camera, files, settings, play store, in that order;
 * where several installed apps match an entry the first-party one with the shortest package name
 * wins, so Google Photos beats Amazon Photos and YouTube beats YouTube Music), then the remaining
 * apps alphabetically by label.
 */
fun preferredHomeApps(apps: List<AppInfo>, count: Int): List<AppInfo> {
    val limit = minOf(count, HOME_SEED_APP_LIMIT)
    if (limit <= 0 || apps.isEmpty()) return emptyList()
    val picked = ArrayList<AppInfo>(limit)
    val used = HashSet<String>()
    // Short home needles ("files", "keep", "drive", "clock") must not pick a loosely matching unrelated app
    // (a label like "Profiles"), so only quality 0 and 1 hits count; the rest falls to the alphabetical fill.
    pickByPriority(apps, HOME_PRIORITY, HOME_VENDORS, limit, picked, used, allowLooseMatch = false)
    if (picked.size < limit) {
        val rest = apps
            .filter { it.key !in used }
            .sortedWith(
                compareBy<AppInfo>(
                    { it.packageName.startsWith(OWN_PACKAGE_PREFIX) },
                    { it.label.lowercase(Locale.ROOT) },
                    { it.key },
                ),
            )
        for (app in rest) {
            if (picked.size >= limit) break
            if (used.add(app.key)) picked.add(app)
        }
    }
    return picked
}

/**
 * The apps the dock shows until the user pins their own: [count] of them, phone, messages, browser
 * (Chrome before other browsers) and camera first, then a few well-known apps, then the remaining
 * apps alphabetically.
 */
fun preferredDockApps(apps: List<AppInfo>, count: Int): List<AppInfo> {
    if (count <= 0 || apps.isEmpty()) return emptyList()
    val picked = ArrayList<AppInfo>(count)
    val used = HashSet<String>()
    pickByPriority(apps, DOCK_PRIORITY_ORDER, DOCK_VENDORS, count, picked, used)
    if (picked.size < count) {
        val rest = apps
            .filter { it.key !in used }
            .sortedWith(
                compareBy<AppInfo>(
                    { it.packageName.startsWith(OWN_PACKAGE_PREFIX) },
                    { it.label.lowercase(Locale.ROOT) },
                    { it.key },
                ),
            )
        for (app in rest) {
            if (picked.size >= count) break
            if (used.add(app.key)) picked.add(app)
        }
    }
    return picked
}

private fun pickByPriority(
    apps: List<AppInfo>,
    priority: List<List<String>>,
    vendors: List<String>,
    limit: Int,
    picked: MutableList<AppInfo>,
    used: MutableSet<String>,
    allowLooseMatch: Boolean = true,
) {
    for (needles in priority) {
        if (picked.size >= limit) return
        for (needle in needles) {
            val best = apps
                .filter {
                    it.key !in used &&
                        !it.packageName.startsWith(OWN_PACKAGE_PREFIX) &&
                        it.matches(needle) &&
                        (allowLooseMatch || it.matchQuality(needle) < 2)
                }
                .minWithOrNull(
                    compareBy<AppInfo>(
                        { it.matchQuality(needle) },
                        { vendorRank(it.packageName, vendors) },
                        { it.packageName.length },
                        { it.label.lowercase(Locale.ROOT) },
                        { it.key },
                    ),
                )
            if (best != null) {
                used.add(best.key)
                picked.add(best)
                break
            }
        }
    }
}

private fun AppInfo.matches(needle: String): Boolean =
    packageName.lowercase(Locale.ROOT).contains(needle) || label.lowercase(Locale.ROOT).contains(needle)

/** Package names that are certain to be the app their entry stands for, whatever its label says. */
private val KNOWN_PACKAGES: Set<String> = setOf("com.android.chrome", "com.google.android.gm")

/**
 * How well this app matches [needle] (lower is better), ranked BEFORE the vendor so a loose substring
 * hit from a first-party vendor cannot beat the real app (the needle "chrome" also appears in Google
 * Home's package com.google.android.apps.chromecast.app). 0: the label equals the needle, the package
 * equals it or is a known id for it. 1: the package ends with ".needle" (or with the needle itself when
 * it starts with a dot) or the label starts with it. 2: any other substring match.
 */
private fun AppInfo.matchQuality(needle: String): Int {
    val pkg = packageName.lowercase(Locale.ROOT)
    val name = label.lowercase(Locale.ROOT)
    if (name == needle || pkg == needle || (pkg in KNOWN_PACKAGES && pkg.contains(needle))) return 0
    val suffix = if (needle.startsWith(".")) needle else ".$needle"
    if (pkg.endsWith(suffix) || name.startsWith(needle)) return 1
    return 2
}

private fun vendorRank(packageName: String, vendors: List<String>): Int {
    val index = vendors.indexOfFirst { packageName.startsWith(it) }
    return if (index >= 0) index else vendors.size
}
