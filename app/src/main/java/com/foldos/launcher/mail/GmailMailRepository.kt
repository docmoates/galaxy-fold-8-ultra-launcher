package com.foldos.launcher.mail

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.foldos.launcher.core.repo.MailAccount
import com.foldos.launcher.core.repo.MailMessage
import com.foldos.launcher.core.repo.MailRepository
import com.foldos.launcher.core.repo.MailResult
import com.foldos.launcher.core.repo.MailState
import com.foldos.launcher.core.repo.SettingsRepository
import com.foldos.launcher.server.ServerAuthStore
import com.foldos.launcher.server.ServerClient
import com.foldos.launcher.server.ServerException
import com.foldos.launcher.server.ServerMailInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/** Gmail label ids the server passes through on each message. */
object MailLabels {
    const val STARRED: String = "STARRED"
    const val CATEGORY_PERSONAL: String = "CATEGORY_PERSONAL"
    const val CATEGORY_UPDATES: String = "CATEGORY_UPDATES"
}

/**
 * [MailRepository] on the Crosspoint server: the person signs in with their Crosspoint account, links Gmail
 * once through a browser page on the server, and the inbox is pulled from the server ([ServerClient]). The
 * server token lives in [ServerAuthStore] (encrypted). No Google sign-in happens on the phone. In No-network
 * mode nothing is requested and the state is an [MailState.Error] with the usual message. A 401 from any call
 * clears the token and returns to [MailState.SignedOut].
 *
 * Without a sign-in the Mail tab still shows the Gmail-app counts from [GmailProviderClient], which needs no account.
 */
class GmailMailRepository(
    context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : MailRepository {

    private val appContext: Context = context.applicationContext
    private val client = ServerClient { settings.settings.first().mailSetupUrl }

    /** Gmail-app label counts; shared with the Mail tab so both read the same picked account. */
    val provider: GmailProviderClient = GmailProviderClient(appContext)

    private val refreshLock = Mutex()
    private val threadIds = ConcurrentHashMap<String, String>()
    private val labelIds = ConcurrentHashMap<String, Set<String>>()
    private var linkJob: Job? = null

    /** Built on [scope] (IO): `EncryptedSharedPreferences` can take a second on first use. Null until ready. */
    private val storeFlow = MutableStateFlow<ServerAuthStore?>(null)

    private suspend fun authStore(): ServerAuthStore = storeFlow.value ?: storeFlow.filterNotNull().first()

    private val internalState = MutableStateFlow<MailState>(MailState.Connecting)
    private val _account = MutableStateFlow(MailAccount())

    override val account: Flow<MailAccount> = _account.asStateFlow()

    private val _tokensEncrypted = MutableStateFlow<Boolean?>(null)

    /** True when the token is stored encrypted, false on the plain-prefs fallback, null while loading. */
    val tokensEncrypted: StateFlow<Boolean?> = _tokensEncrypted.asStateFlow()

    override val state: Flow<MailState> = combine(
        internalState,
        settings.settings.map { it.noNetwork }.distinctUntilChanged(),
    ) { current, offline ->
        // Settings > Features > No-network mode: nothing is requested, nothing leaves the phone.
        if (offline) MailState.Error(NO_NETWORK_MESSAGE) else current
    }

    init {
        scope.launch {
            val store = ServerAuthStore(appContext)
            _tokensEncrypted.value = store.encrypted
            storeFlow.value = store
            if (store.signedIn) {
                _account.value = MailAccount(
                    signedInAs = store.email,
                    name = store.name,
                    gmail = store.gmailAccount,
                    linked = store.gmailAccount != null,
                )
                refreshInternal(verifyAccount = true)
            } else {
                internalState.value = MailState.SignedOut
            }
        }
        // Leaving No-network mode: pick the inbox up again.
        scope.launch {
            settings.settings.map { it.noNetwork }.distinctUntilChanged().drop(1).collect { offline ->
                if (!offline && authStore().signedIn) refreshInternal(verifyAccount = true)
            }
        }
    }

    /** Gmail thread id of a listed message (for [GmailIntents.openThread]). */
    fun threadIdFor(messageId: String): String? = threadIds[messageId]

    /** Gmail label ids of a listed message (`STARRED`, `CATEGORY_UPDATES`, ...). */
    fun labelsFor(messageId: String): Set<String> = labelIds[messageId] ?: emptySet()

    private suspend fun offline(): Boolean = settings.settings.first().noNetwork

    /* ------------------------------------------------------------------------------------ */
    /* Actions                                                                              */
    /* ------------------------------------------------------------------------------------ */

    override suspend fun signIn(email: String, password: String): MailResult {
        if (offline()) return MailResult(false, NO_NETWORK_MESSAGE)
        val cleanEmail = email.trim()
        if (cleanEmail.isEmpty() || password.isEmpty()) {
            return MailResult(false, "Enter your Crosspoint email and password.")
        }
        // Finish on the repository scope so a cancelled caller (the tab left the composition) cannot drop the token.
        return scope.async {
            try {
                val session = client.login(cleanEmail, password)
                val token = session.token ?: return@async MailResult(false, "The server did not return a sign-in. Please try again.")
                val store = authStore()
                store.token = token
                store.email = session.email.ifBlank { cleanEmail }
                store.name = session.name
                applyMailInfo(session.mail)
                _account.value = _account.value.copy(signedInAs = store.email, name = store.name)
                internalState.value = MailState.Connecting
                refreshInternal(verifyAccount = false)
                MailResult(true)
            } catch (e: ServerException) {
                MailResult(false, signInMessage(e))
            } catch (e: IOException) {
                MailResult(false, UNREACHABLE_MESSAGE)
            } catch (e: Exception) {
                Log.w(TAG, "Sign-in failed: ${e.javaClass.simpleName}")
                MailResult(false, "Could not sign in. Please try again.")
            }
        }.await()
    }

    override suspend fun signOut() {
        linkJob?.cancel()
        val store = authStore()
        val token = store.token
        // Tell the server (best effort), then forget everything on the phone.
        if (token != null && !offline()) {
            runCatching { scope.async { client.logout(token) }.await() }
        }
        clearSession()
    }

    override suspend fun startLink(activity: Activity): MailResult {
        if (offline()) return MailResult(false, NO_NETWORK_MESSAGE)
        val token = authStore().token ?: return MailResult(false, "Sign in with your Crosspoint account first.")
        return try {
            val url = scope.async { client.mailLink(token) }.await()
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            if (activity.isFinishing) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
            waitForLink()
            MailResult(true)
        } catch (e: ServerException) {
            if (e.unauthorized) {
                clearSession()
                MailResult(false, SESSION_ENDED_MESSAGE)
            } else if (e.status == 503) {
                _account.value = _account.value.copy(available = false)
                internalState.value = MailState.NotConfigured
                MailResult(false, NOT_SET_UP_MESSAGE)
            } else {
                MailResult(false, e.message ?: "Could not start the Gmail link.")
            }
        } catch (e: IOException) {
            MailResult(false, UNREACHABLE_MESSAGE)
        } catch (e: Exception) {
            MailResult(false, "Could not open the browser to link Gmail.")
        }
    }

    /** Polls /me every ~2 s for up to ~2 minutes until the server reports Gmail linked. */
    private fun waitForLink() {
        linkJob?.cancel()
        _account.value = _account.value.copy(waitingForLink = true)
        linkJob = scope.launch {
            try {
                repeat(LINK_POLL_COUNT) {
                    delay(LINK_POLL_MS)
                    val token = authStore().token ?: return@launch
                    if (offline()) return@launch
                    val linked = try {
                        applyMailInfo(client.me(token).mail)
                    } catch (e: ServerException) {
                        if (e.unauthorized) clearSession()
                        return@launch
                    } catch (e: IOException) {
                        false
                    }
                    if (linked) {
                        refreshInternal(verifyAccount = false)
                        return@launch
                    }
                }
            } finally {
                _account.value = _account.value.copy(waitingForLink = false)
            }
        }
    }

    override suspend fun unlink(): MailResult {
        if (offline()) return MailResult(false, NO_NETWORK_MESSAGE)
        val token = authStore().token ?: return MailResult(false, "You are not signed in.")
        linkJob?.cancel()
        return try {
            val info = scope.async { client.mailUnlink(token) }.await()
            threadIds.clear()
            labelIds.clear()
            applyMailInfo(info)
            internalState.value = if (info.available) MailState.NotLinked else MailState.NotConfigured
            MailResult(true)
        } catch (e: ServerException) {
            if (e.unauthorized) {
                clearSession()
                MailResult(false, SESSION_ENDED_MESSAGE)
            } else {
                MailResult(false, e.message ?: "Could not disconnect Gmail.")
            }
        } catch (e: IOException) {
            MailResult(false, UNREACHABLE_MESSAGE)
        }
    }

    override suspend fun refresh() {
        if (!authStore().signedIn) return
        scope.launch { refreshInternal(verifyAccount = true) }.join()
    }

    /* ------------------------------------------------------------------------------------ */
    /* Internals                                                                            */
    /* ------------------------------------------------------------------------------------ */

    private fun signInMessage(e: ServerException): String = when {
        e.status == 401 -> "That email and password did not match a Crosspoint account."
        e.status == 429 -> e.message?.takeIf { it.isNotBlank() }
            ?: "Too many tries. Please wait 15 minutes and try again."
        e.status >= 500 -> "The Crosspoint server had a problem. Please try again in a moment."
        else -> e.message ?: "Could not sign in."
    }

    /** Stores the server's mail block in the account flow and the store; returns whether Gmail is linked. */
    private suspend fun applyMailInfo(info: ServerMailInfo): Boolean {
        val store = authStore()
        store.gmailAccount = if (info.linked) info.account else null
        _account.value = _account.value.copy(
            gmail = if (info.linked) info.account else null,
            linked = info.linked,
            available = info.available,
        )
        return info.linked
    }

    private suspend fun clearSession() {
        authStore().clear()
        threadIds.clear()
        labelIds.clear()
        _account.value = MailAccount()
        internalState.value = MailState.SignedOut
    }

    private suspend fun refreshInternal(verifyAccount: Boolean) {
        refreshLock.withLock { load(verifyAccount) }
    }

    private suspend fun load(verifyAccount: Boolean) {
        if (offline()) return
        val store = authStore()
        val token = store.token
        if (token == null) {
            internalState.value = MailState.SignedOut
            return
        }
        if (internalState.value !is MailState.Connected) internalState.value = MailState.Connecting
        try {
            // Account status first: after a restart, or while Gmail is not linked yet (the person may have just approved it).
            if (verifyAccount || !_account.value.linked) {
                applyMailInfo(client.me(token).mail)
            }
            val info = _account.value
            when {
                !info.available -> internalState.value = MailState.NotConfigured
                !info.linked -> internalState.value = MailState.NotLinked
                else -> fetchInbox(token)
            }
        } catch (e: ServerException) {
            handleServerError(e)
        } catch (e: IOException) {
            internalState.value = MailState.Error(UNREACHABLE_MESSAGE)
        } catch (e: Exception) {
            Log.w(TAG, "Mail refresh failed: ${e.javaClass.simpleName}")
            internalState.value = MailState.Error("Could not load your mail. Please try again.")
        }
    }

    private suspend fun fetchInbox(token: String) {
        val inbox = client.inbox(token, limit = INBOX_PAGE_SIZE, fresh = true)
        threadIds.clear()
        labelIds.clear()
        val messages = inbox.messages.map { m ->
            threadIds[m.id] = m.threadId
            labelIds[m.id] = if (m.starred) m.labels + MailLabels.STARRED else m.labels
            MailMessage(
                id = m.id,
                from = m.fromName,
                subject = m.subject,
                snippet = m.snippet,
                date = m.receivedAt,
                unread = m.unread,
            )
        }
        if (inbox.account != null && inbox.account != _account.value.gmail) {
            authStore().gmailAccount = inbox.account
            _account.value = _account.value.copy(gmail = inbox.account)
        }
        internalState.value = MailState.Connected(messages, unread = inbox.unread)
    }

    private suspend fun handleServerError(e: ServerException) {
        when {
            e.unauthorized -> clearSession()
            e.status == 409 || e.code == "not_linked" -> {
                authStore().gmailAccount = null
                _account.value = _account.value.copy(linked = false, gmail = null)
                internalState.value = MailState.NotLinked
            }
            e.status == 503 -> {
                _account.value = _account.value.copy(available = false)
                internalState.value = MailState.NotConfigured
            }
            e.status == 502 -> internalState.value = MailState.Error(e.message ?: "Gmail could not be reached. Please try again.")
            else -> internalState.value = MailState.Error(e.message ?: "Could not load your mail. Please try again.")
        }
    }

    private companion object {
        const val TAG = "GmailMailRepository"
        const val INBOX_PAGE_SIZE = 25
        const val LINK_POLL_MS = 2_000L
        const val LINK_POLL_COUNT = 60
        const val NO_NETWORK_MESSAGE = "No-network mode is on. Turn it off in Settings > Features to use Gmail."
        const val UNREACHABLE_MESSAGE = "Can't reach the Crosspoint server. Check your connection and try again."
        const val SESSION_ENDED_MESSAGE = "Your Crosspoint sign-in ended. Please sign in again."
        const val NOT_SET_UP_MESSAGE = "Your administrator has not set up mail yet."
    }
}

/** Short name used in the task brief for [GmailMailRepository]. */
typealias GmailRepository = GmailMailRepository
