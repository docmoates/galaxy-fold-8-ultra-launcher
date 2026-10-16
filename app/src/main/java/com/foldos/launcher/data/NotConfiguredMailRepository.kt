package com.foldos.launcher.data

import android.app.Activity
import com.foldos.launcher.core.repo.MailAccount
import com.foldos.launcher.core.repo.MailRepository
import com.foldos.launcher.core.repo.MailResult
import com.foldos.launcher.core.repo.MailState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/** Placeholder [MailRepository]: always [MailState.NotConfigured]. The real one is `mail.GmailMailRepository`. */
class NotConfiguredMailRepository : MailRepository {
    override val state: Flow<MailState> = flowOf(MailState.NotConfigured)

    override val account: Flow<MailAccount> = flowOf(MailAccount())

    override suspend fun signIn(email: String, password: String): MailResult =
        MailResult(false, "Mail is not available.")

    override suspend fun signOut() = Unit

    override suspend fun startLink(activity: Activity): MailResult = MailResult(false, "Mail is not available.")

    override suspend fun unlink(): MailResult = MailResult(false, "Mail is not available.")

    override suspend fun refresh() = Unit
}
