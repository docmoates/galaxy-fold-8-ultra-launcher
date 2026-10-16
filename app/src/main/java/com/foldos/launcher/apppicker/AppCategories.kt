package com.foldos.launcher.apppicker

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import com.foldos.launcher.core.model.AppInfo

/**
 * The automatic groups of the app pop-up. Hidden is not a category of an app: it lists the apps the
 * user hid in Settings (see [AppPickerState]).
 */
enum class AppCategory(val title: String) {
    WORK("Work"),
    MEDIA("Media"),
    SOCIAL("Social"),
    TOOLS("Tools"),
    GAMES("Games"),
}

/**
 * Sorts apps into [AppCategory]s: first a small package map of well-known apps, then the category the
 * app declares (`ApplicationInfo.category`), then keywords in the package name. Apps that match
 * nothing have no category and only show under "All".
 */
object AppCategories {

    private val packageMap: Map<String, AppCategory> = mapOf(
        // Social and messaging
        "com.whatsapp" to AppCategory.SOCIAL,
        "org.telegram.messenger" to AppCategory.SOCIAL,
        "org.thoughtcrime.securesms" to AppCategory.SOCIAL,
        "com.facebook.katana" to AppCategory.SOCIAL,
        "com.facebook.orca" to AppCategory.SOCIAL,
        "com.instagram.android" to AppCategory.SOCIAL,
        "com.twitter.android" to AppCategory.SOCIAL,
        "com.snapchat.android" to AppCategory.SOCIAL,
        "com.discord" to AppCategory.SOCIAL,
        "com.reddit.frontpage" to AppCategory.SOCIAL,
        "com.zhiliaoapp.musically" to AppCategory.SOCIAL,
        "com.linkedin.android" to AppCategory.SOCIAL,
        "com.pinterest" to AppCategory.SOCIAL,
        "com.google.android.apps.messaging" to AppCategory.SOCIAL,
        "com.samsung.android.messaging" to AppCategory.SOCIAL,
        "com.google.android.apps.tachyon" to AppCategory.SOCIAL,
        // Media
        "com.spotify.music" to AppCategory.MEDIA,
        "com.google.android.youtube" to AppCategory.MEDIA,
        "com.google.android.apps.youtube.music" to AppCategory.MEDIA,
        "com.netflix.mediaclient" to AppCategory.MEDIA,
        "com.google.android.apps.photos" to AppCategory.MEDIA,
        "com.sec.android.gallery3d" to AppCategory.MEDIA,
        "com.sec.android.app.camera" to AppCategory.MEDIA,
        "com.android.camera" to AppCategory.MEDIA,
        "com.google.android.GoogleCamera" to AppCategory.MEDIA,
        "com.amazon.avod.thirdpartyclient" to AppCategory.MEDIA,
        "com.disney.disneyplus" to AppCategory.MEDIA,
        // Work
        "com.google.android.gm" to AppCategory.WORK,
        "com.google.android.calendar" to AppCategory.WORK,
        "com.samsung.android.calendar" to AppCategory.WORK,
        "com.samsung.android.email.provider" to AppCategory.WORK,
        "com.google.android.apps.docs" to AppCategory.WORK,
        "com.google.android.keep" to AppCategory.WORK,
        "com.microsoft.office.outlook" to AppCategory.WORK,
        "com.microsoft.teams" to AppCategory.WORK,
        "com.microsoft.office.word" to AppCategory.WORK,
        "com.microsoft.office.excel" to AppCategory.WORK,
        "com.microsoft.office.powerpoint" to AppCategory.WORK,
        "com.microsoft.office.onenote" to AppCategory.WORK,
        "com.slack" to AppCategory.WORK,
        "us.zoom.videomeetings" to AppCategory.WORK,
        "com.notion.id" to AppCategory.WORK,
        "com.google.android.apps.meetings" to AppCategory.WORK,
        // Tools
        "com.android.settings" to AppCategory.TOOLS,
        "com.google.android.calculator" to AppCategory.TOOLS,
        "com.sec.android.app.popupcalculator" to AppCategory.TOOLS,
        "com.google.android.deskclock" to AppCategory.TOOLS,
        "com.sec.android.app.clockpackage" to AppCategory.TOOLS,
        "com.google.android.apps.maps" to AppCategory.TOOLS,
        "com.android.chrome" to AppCategory.TOOLS,
        "com.sec.android.app.sbrowser" to AppCategory.TOOLS,
        "com.sec.android.app.myfiles" to AppCategory.TOOLS,
        "com.google.android.apps.nbu.files" to AppCategory.TOOLS,
        "com.android.vending" to AppCategory.TOOLS,
        "com.google.android.apps.translate" to AppCategory.TOOLS,
    )

    // Checked in order against the lower-cased package name; first hit wins.
    private val keywords: List<Pair<String, AppCategory>> = listOf(
        "game" to AppCategory.GAMES,
        "calculator" to AppCategory.TOOLS,
        "clock" to AppCategory.TOOLS,
        "settings" to AppCategory.TOOLS,
        "browser" to AppCategory.TOOLS,
        "files" to AppCategory.TOOLS,
        "camera" to AppCategory.MEDIA,
        "gallery" to AppCategory.MEDIA,
        "photo" to AppCategory.MEDIA,
        "music" to AppCategory.MEDIA,
        "video" to AppCategory.MEDIA,
        "player" to AppCategory.MEDIA,
        "radio" to AppCategory.MEDIA,
        "podcast" to AppCategory.MEDIA,
        "messag" to AppCategory.SOCIAL,
        "chat" to AppCategory.SOCIAL,
        "social" to AppCategory.SOCIAL,
        "mail" to AppCategory.WORK,
        "calendar" to AppCategory.WORK,
        "office" to AppCategory.WORK,
        "docs" to AppCategory.WORK,
        "notes" to AppCategory.WORK,
    )

    /** The category of [app], or null when nothing matches. Blocking (one PackageManager lookup). */
    fun categoryOf(packageManager: PackageManager, app: AppInfo): AppCategory? {
        packageMap[app.packageName]?.let { return it }
        val declared = declaredCategory(packageManager, app.packageName)
        if (declared != null) return declared
        val lower = app.packageName.lowercase()
        return keywords.firstOrNull { (word, _) -> lower.contains(word) }?.second
    }

    /** Categories for every app, keyed by `AppInfo.key`. Blocking; call it off the main thread. */
    fun classify(packageManager: PackageManager, apps: List<AppInfo>): Map<String, AppCategory> {
        val result = HashMap<String, AppCategory>(apps.size)
        val perPackage = HashMap<String, AppCategory?>()
        for (app in apps) {
            val category = if (perPackage.containsKey(app.packageName)) {
                perPackage[app.packageName]
            } else {
                categoryOf(packageManager, app).also { perPackage[app.packageName] = it }
            }
            if (category != null) result[app.key] = category
        }
        return result
    }

    private fun declaredCategory(packageManager: PackageManager, packageName: String): AppCategory? {
        val info = try {
            packageManager.getApplicationInfo(packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            return null
        } catch (e: RuntimeException) {
            return null
        }
        return when (info.category) {
            ApplicationInfo.CATEGORY_GAME -> AppCategory.GAMES
            ApplicationInfo.CATEGORY_AUDIO,
            ApplicationInfo.CATEGORY_VIDEO,
            ApplicationInfo.CATEGORY_IMAGE,
            ApplicationInfo.CATEGORY_NEWS,
            -> AppCategory.MEDIA
            ApplicationInfo.CATEGORY_SOCIAL -> AppCategory.SOCIAL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> AppCategory.WORK
            ApplicationInfo.CATEGORY_MAPS,
            ApplicationInfo.CATEGORY_ACCESSIBILITY,
            -> AppCategory.TOOLS
            else -> null
        }
    }
}
