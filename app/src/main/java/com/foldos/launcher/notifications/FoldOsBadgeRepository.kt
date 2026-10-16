package com.foldos.launcher.notifications

import android.content.Context
import android.service.notification.StatusBarNotification
import com.foldos.launcher.core.model.LauncherSettings
import com.foldos.launcher.core.repo.BadgeRepository
import com.foldos.launcher.core.repo.NotificationSummary
import com.foldos.launcher.core.repo.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [BadgeRepository] fed by `builtins.FoldOsNotificationListener` through [ActiveNotifications].
 *
 * - [counts] is empty while the Badges feature (`FEATURE_BADGES`) is off or `badgeStyle` is
 *   `BADGE_OFF`, and otherwise maps each package to its number of active, badge-worthy
 *   notifications (group summaries, ongoing and media notifications never count).
 * - Packages routed to the quiet drawer ([QuietDrawerStore]) are excluded from both [counts] and
 *   [notificationsFor]; their notifications appear only in [QuietDrawerSheet].
 * - Both flows emit empty values until the user grants notification access.
 *
 * Replaces `data.EmptyBadgeRepository` in `AppGraphImpl`:
 * `override val badges: BadgeRepository by lazy { FoldOsBadgeRepository(appContext, settings) }`.
 */
class FoldOsBadgeRepository(
    context: Context,
    private val settings: SettingsRepository,
) : BadgeRepository {

    /** The quiet drawer configuration this repository honours; shared with the settings rows. */
    val quietDrawer: QuietDrawerStore = QuietDrawerStore(context)

    /** Active notifications minus the quiet drawer's packages. */
    private val visible: Flow<List<StatusBarNotification>> =
        combine(ActiveNotifications.notifications, quietDrawer.config) { list, quiet ->
            if (quiet.enabled && quiet.packages.isNotEmpty()) list.filterNot { it.packageName in quiet.packages } else list
        }

    override val counts: Flow<Map<String, Int>> =
        combine(visible, settings.settings) { list, prefs ->
            if (!badgesEnabled(prefs)) emptyMap<String, Int>() else list.groupingBy { it.packageName }.eachCount()
        }.distinctUntilChanged()

    override fun notificationsFor(packageName: String): Flow<List<NotificationSummary>> =
        visible
            .map { list ->
                list.filter { it.packageName == packageName }
                    .sortedByDescending { it.postTime }
                    .map { it.toSummary() }
            }
            .distinctUntilChanged()

    /** Notifications routed to the quiet drawer, newest first. */
    val quietNotifications: Flow<List<NotificationSummary>> = quietNotificationsFlow(quietDrawer)

    private fun badgesEnabled(prefs: LauncherSettings): Boolean =
        prefs.feature(LauncherSettings.FEATURE_BADGES) && prefs.badgeStyle != LauncherSettings.BADGE_OFF
}
