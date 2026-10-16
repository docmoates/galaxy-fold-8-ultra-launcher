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

/** Connection state of the Hub's Mail tab (served by the Crosspoint server). */
sealed class MailState {
    /** Not signed in to a Crosspoint account yet; [MailRepository.signIn] fixes it. */
    data object SignedOut : MailState()

    /** Signed in, but Gmail is not linked yet; [MailRepository.startLink] fixes it. */
    data object NotLinked : MailState()

    /** The server says mail is not set up (its administrator has not configured it). */
    data object NotConfigured : MailState()

    /** Loading (starting up, signing in or fetching the inbox). */
    data object Connecting : MailState()

    /** Gmail linked; [messages] is the inbox, newest first, [unread] the unread count. */
    data class Connected(val messages: List<MailMessage>, val unread: Int) : MailState()

    /** A problem to show as friendly text (also No-network mode). */
    data class Error(val message: String) : MailState()
}

/** Who is signed in and what the server knows about the Gmail link. */
data class MailAccount(
    /** Crosspoint account email, null when signed out. */
    val signedInAs: String? = null,
    val name: String? = null,
    /** The linked Gmail address, null when not linked. */
    val gmail: String? = null,
    val linked: Boolean = false,
    /** False when the server reports mail is not set up. */
    val available: Boolean = true,
    /** True while the app is waiting for the browser Gmail approval to finish. */
    val waitingForLink: Boolean = false,
)

/** Outcome of a user action; [message] is friendly text to show when [ok] is false. */
data class MailResult(val ok: Boolean, val message: String? = null)

/**
 * The Hub's Mail tab. People sign in with their Crosspoint account, link Gmail once in the browser, and the
 * inbox comes from the server. Implemented by `mail.GmailMailRepository`; `data.NotConfiguredMailRepository`
 * is the placeholder.
 */
interface MailRepository {
    val state: Flow<MailState>

    /** Account and link status for the Mail tab and Settings. */
    val account: Flow<MailAccount>

    /** Signs in with a Crosspoint account. */
    suspend fun signIn(email: String, password: String): MailResult

    /** Signs out of the Crosspoint account on this phone (and tells the server). */
    suspend fun signOut()

    /** Asks the server for the Gmail link page and opens it in the browser from [activity]; then waits for it to finish. */
    suspend fun startLink(activity: Activity): MailResult

    /** Disconnects Gmail on the server; stays signed in. */
    suspend fun unlink(): MailResult

    /** Reloads the account status and, when linked, the inbox; no-op while signed out. */
    suspend fun refresh()
}
