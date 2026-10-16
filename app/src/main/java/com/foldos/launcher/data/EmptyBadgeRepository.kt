package com.foldos.launcher.data

import com.foldos.launcher.core.repo.BadgeRepository
import com.foldos.launcher.core.repo.NotificationSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Placeholder [BadgeRepository]: no badges. Replaced by the Badges agent (`notifications/`). */
class EmptyBadgeRepository : BadgeRepository {
    override val counts: Flow<Map<String, Int>> = flowOf(emptyMap())

    override fun notificationsFor(packageName: String): Flow<List<NotificationSummary>> = flowOf(emptyList())
}
