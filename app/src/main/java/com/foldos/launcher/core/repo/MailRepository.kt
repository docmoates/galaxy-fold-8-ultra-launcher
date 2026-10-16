package com.foldos.launcher.core.repo

import android.app.Activity
import kotlinx.coroutines.flow.Flow

/** One inbox message; [from] is the display name or address, [date] epoch millis. */
data class MailMessage(
    val id: String,
    val from: String,
    val subject: String,
    val snippet: String,
    val date: Long,
    val unread: Boolean,
)

/** Connection state of the Hub's Mail tab. */
sealed class MailState {
    /** No OAuth client id (`LauncherSettings.mailClientId`) or no account chosen yet. */
    data object NotConfigured : MailState()

    /** Configured but signed out; [MailRepository.connect] signs in. */
    data object Disconnected : MailState()

    data object Connecting : MailState()

    /** Signed in; [messages] is the inbox, newest first, [unread] the unread count. */
    data class Connected(val messages: List<MailMessage>, val unread: Int) : MailState()

    data class Error(val message: String) : MailState()
}

/**
 * The Hub's Mail tab (Gmail API through Google sign-in, IMAP fallback). Implemented by the
 * Hub-mail-AI agent; `data.NotConfiguredMailRepository` is the placeholder.
 */
interface MailRepository {
    val state: Flow<MailState>

    /** Starts the sign-in flow from [activity] and, on success, loads the inbox. */
    suspend fun connect(activity: Activity)

    /** Signs out and forgets the cached inbox; state becomes [MailState.Disconnected]. */
    suspend fun disconnect()

    /** Reloads the inbox when connected; no-op otherwise. */
    suspend fun refresh()
}
