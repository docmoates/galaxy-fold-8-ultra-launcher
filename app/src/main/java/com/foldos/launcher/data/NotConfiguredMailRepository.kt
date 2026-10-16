package com.foldos.launcher.data

import android.app.Activity
import com.foldos.launcher.core.repo.MailRepository
import com.foldos.launcher.core.repo.MailState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Placeholder [MailRepository]: always [MailState.NotConfigured]. Replaced by the Hub-mail-AI agent (`mail/`). */
class NotConfiguredMailRepository : MailRepository {
    override val state: Flow<MailState> = flowOf(MailState.NotConfigured)

    override suspend fun connect(activity: Activity) = Unit

    override suspend fun disconnect() = Unit

    override suspend fun refresh() = Unit
}
