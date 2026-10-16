package com.foldos.launcher.mail

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.util.Log
import com.foldos.launcher.core.repo.MailMessage
import com.foldos.launcher.core.repo.MailRepository
import com.foldos.launcher.core.repo.MailState
import com.foldos.launcher.core.repo.SettingsRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
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
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationService
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * [MailRepository] on the Gmail API: Google sign-in through AppAuth ([MailAuthActivity]), tokens
 * in [GmailAuthStore], inbox from [GmailApiClient]. Needs `LauncherSettings.mailClientId` (a
 * Google Cloud OAuth client id of type Android for this package); without it the state is
 * [MailState.NotConfigured] and the Mail tab shows the Gmail-app counts from
 * [GmailProviderClient] instead, which needs no sign-in.
 *
 * Wire in `AppGraphImpl`: `override val mail: MailRepository by lazy { GmailMailRepository(appContext, settings) }`.
 */
class GmailMailRepository(
    context: Context,
    private val settings: SettingsRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
) : MailRepository {

    private val appContext: Context = context.applicationContext
    private val api = GmailApiClient()

    /** Gmail-app label counts; shared with the Mail tab so both read the same picked account. */
    val provider: GmailProviderClient = GmailProviderClient(appContext)

    private val refreshLock = Mutex()
    private val threadIds = ConcurrentHashMap<String, String>()
    private val labelIds = ConcurrentHashMap<String, Set<String>>()

    /**
     * The token store, built on [scope] (IO) because `EncryptedSharedPreferences` + the Keystore
     * master key take anywhere from 100 ms to over a second on first use, and `graph.mail` is
     * first touched from composition on the main thread. Null until ready.
     */
    private val storeFlow = MutableStateFlow<GmailAuthStore?>(null)

    private suspend fun authStore(): GmailAuthStore = storeFlow.value ?: storeFlow.filterNotNull().first()

    /** Connecting until the store has loaded; the init coroutine then settles it. */
    private val internalState = MutableStateFlow<MailState>(MailState.Connecting)

    private val _accountEmail = MutableStateFlow<String?>(null)

    /** The signed-in Gmail address, once known. */
    val accountEmail: StateFlow<String?> = _accountEmail.asStateFlow()

    private val _tokensEncrypted = MutableStateFlow<Boolean?>(null)

    /**
     * True when tokens are stored encrypted (see [GmailAuthStore.encrypted]), false on the
     * plain-prefs fallback, null while the store is still loading.
     */
    val tokensEncrypted: StateFlow<Boolean?> = _tokensEncrypted.asStateFlow()

    private val serverPrefs = appContext.getSharedPreferences(SERVER_PREFS, Context.MODE_PRIVATE)
    private val _serverClientId = MutableStateFlow(serverPrefs.getString(KEY_SERVER_CLIENT_ID, "").orEmpty())

    /** The shared client id downloaded from the setup server (empty when the server has none). */
    val serverClientId: StateFlow<String> = _serverClientId.asStateFlow()

    /**
     * Downloads the shared Google sign-in id from `<setup server>/api/v1/public/foldos-mail`. Only an
     * explicit answer from the server changes the cached id; no network or a bad reply keeps the old one.
     * Never runs in No-network mode.
     */
    suspend fun refreshServerConfig() {
        val current = settings.settings.first()
        if (current.noNetwork) return
        val base = current.mailSetupUrl?.trim()?.trimEnd('/').takeUnless { it.isNullOrEmpty() }
            ?: com.foldos.launcher.core.model.LauncherSettings.DEFAULT_MAIL_SETUP_URL
        val reply = withContext(Dispatchers.IO) {
            runCatching {
                val conn = java.net.URL("$base/api/v1/public/foldos-mail").openConnection() as java.net.HttpURLConnection
                try {
                    conn.connectTimeout = 8_000
                    conn.readTimeout = 8_000
                    conn.requestMethod = "GET"
                    if (conn.responseCode != 200) return@runCatching null
                    org.json.JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                } finally {
                    conn.disconnect()
                }
            }.onFailure { Log.i(TAG, "Mail setup server not reachable: ${it.message}") }.getOrNull()
        } ?: return
        val id = if (reply.optBoolean("configured", false)) reply.optString("client_id").trim() else ""
        if (id != _serverClientId.value) {
            serverPrefs.edit().putString(KEY_SERVER_CLIENT_ID, id).apply()
            _serverClientId.value = id
        }
    }

    override val state: Flow<MailState> = combine(
        combine(
            settings.settings.map { it.mailClientId?.trim().orEmpty() }.distinctUntilChanged(),
            _serverClientId,
        ) { own, shared -> own.ifEmpty { shared } }.distinctUntilChanged(),
        internalState,
        storeFlow,
        settings.settings.map { it.noNetwork }.distinctUntilChanged(),
    ) { clientId, current, store, offline ->
        when {
            // Settings > Features > No-network mode: no sign-in, no inbox request, nothing leaves the phone.
            offline -> MailState.Error(NO_NETWORK_MESSAGE)
            clientId.isEmpty() -> MailState.NotConfigured
            store == null -> MailState.Connecting
            // Tokens issued for another client id are useless once the id changes.
            store.signedIn && store.clientId != null && store.clientId != clientId -> MailState.Disconnected
            else -> current
        }
    }

    init {
        scope.launch {
            val store = GmailAuthStore(appContext)
            _accountEmail.value = store.accountEmail
            _tokensEncrypted.value = store.encrypted
            storeFlow.value = store
            if (store.signedIn) refreshInternal() else internalState.value = MailState.Disconnected
        }
        // Ask the setup server for the shared sign-in id at start, and again when its address or No-network mode changes.
        scope.launch {
            settings.settings.map { it.mailSetupUrl?.trim().orEmpty() to it.noNetwork }.distinctUntilChanged().collect { refreshServerConfig() }
        }
        // Leaving No-network mode: pick the inbox up again (the early return in loadInbox left the state untouched).
        scope.launch {
            settings.settings.map { it.noNetwork }.distinctUntilChanged().drop(1).collect { offline ->
                if (!offline && authStore().signedIn) refreshInternal()
            }
        }
    }

    /** Gmail thread id of a listed message (for [GmailIntents.openThread]). */
    fun threadIdFor(messageId: String): String? = threadIds[messageId]

    /** Gmail label ids of a listed message (`STARRED`, `CATEGORY_UPDATES`, ...). */
    fun labelsFor(messageId: String): Set<String> = labelIds[messageId] ?: emptySet()

    /** False while `LauncherSettings.noNetwork` is on: every Gmail / AppAuth request checks this first. */
    private suspend fun networkAllowed(): Boolean = !settings.settings.first().noNetwork

    override suspend fun connect(activity: Activity) {
        val current = settings.settings.first()
        if (current.noNetwork) return
        // Make sure the shared id is as fresh as possible before signing in.
        if (current.mailClientId.isNullOrBlank() && _serverClientId.value.isEmpty()) refreshServerConfig()
        val clientId = current.mailClientId?.trim().orEmpty().ifEmpty { _serverClientId.value }
        if (clientId.isEmpty()) {
            internalState.value = MailState.NotConfigured
            return
        }
        val store = authStore()
        if (store.signedIn && store.clientId != null && store.clientId != clientId) {
            store.clear()
            threadIds.clear()
            labelIds.clear()
        }
        internalState.value = MailState.Connecting
        val waiter = MailAuthBridge.begin()
        val started = runCatching {
            val intent = MailAuthActivity.intent(activity, clientId)
            if (activity.isFinishing) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            activity.startActivity(intent)
        }.isSuccess
        if (!started) {
            MailAuthBridge.deliver(MailAuthOutcome.Failure("Could not open the sign-in screen"))
        }
        // Finish the flow on the repository scope so a cancelled caller (the tab left the
        // composition) does not drop the tokens the browser is about to hand back.
        scope.launch { finishSignIn(waiter, clientId) }.join()
    }

    private suspend fun finishSignIn(waiter: CompletableDeferred<MailAuthOutcome>, clientId: String) {
        val outcome: MailAuthOutcome = withTimeoutOrNull(SIGN_IN_TIMEOUT_MS) { waiter.await() }
            ?: MailAuthOutcome.Failure("Sign-in timed out", cancelled = true)
        when (outcome) {
            is MailAuthOutcome.Success -> {
                val store = authStore()
                store.authStateJson = outcome.authStateJson
                store.clientId = clientId
                refreshInternal()
            }
            is MailAuthOutcome.Failure -> {
                internalState.value = if (outcome.cancelled) MailState.Disconnected else MailState.Error(outcome.message)
            }
        }
    }

    override suspend fun disconnect() {
        authStore().clear()
        threadIds.clear()
        labelIds.clear()
        _accountEmail.value = null
        internalState.value = MailState.Disconnected
    }

    override suspend fun refresh() {
        if (!authStore().signedIn) return
        scope.launch { refreshInternal() }.join()
    }

    private suspend fun refreshInternal() {
        refreshLock.withLock { loadInbox() }
    }

    private suspend fun loadInbox() {
        if (!networkAllowed()) return
        val store = authStore()
        val json = store.authStateJson
        if (json == null) {
            internalState.value = MailState.Disconnected
            return
        }
        val authState = runCatching { AuthState.jsonDeserialize(json) }.getOrNull()
        if (authState == null) {
            Log.w(TAG, "Stored auth state is unreadable; signing out")
            store.clear()
            internalState.value = MailState.Disconnected
            return
        }
        val previous = internalState.value
        if (previous !is MailState.Connected) internalState.value = MailState.Connecting
        try {
            val token = freshAccessToken(authState)
            // AuthState refreshed the token in place; keep the new one.
            store.authStateJson = authState.jsonSerializeString()
            if (store.accountEmail == null) {
                val email = runCatching { api.profileEmail(token) }.getOrNull()
                if (email != null) {
                    store.accountEmail = email
                    _accountEmail.value = email
                }
            }
            val fetched = api.listInbox(token, maxResults = INBOX_PAGE_SIZE)
            threadIds.clear()
            labelIds.clear()
            fetched.forEach { entry ->
                threadIds[entry.message.id] = entry.threadId
                labelIds[entry.message.id] = entry.labelIds
            }
            val messages: List<MailMessage> = fetched.map { it.message }
            internalState.value = MailState.Connected(messages, unread = messages.count { it.unread })
        } catch (e: GmailApiException) {
            Log.w(TAG, "Inbox load failed: ${e.statusCode} ${e.message}")
            if (e.unauthorized) {
                store.clear()
                internalState.value = MailState.Error("Google signed you out. Connect again in Settings › Mail.")
            } else {
                internalState.value = MailState.Error(e.message ?: "Gmail is unreachable")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Inbox load failed", e)
            internalState.value = MailState.Error(e.message ?: "Could not load the inbox")
        }
    }

    /** A valid access token, refreshing through AppAuth when the stored one has expired. */
    private suspend fun freshAccessToken(authState: AuthState): String {
        val service = withContext(Dispatchers.Main.immediate) { AuthorizationService(appContext) }
        try {
            return suspendCancellableCoroutine<String> { continuation ->
                authState.performActionWithFreshTokens(service) { accessToken, _, exception ->
                    if (accessToken != null) {
                        continuation.resume(accessToken)
                    } else {
                        continuation.resumeWithException(
                            GmailApiException(
                                exception?.errorDescription ?: exception?.error ?: "Could not refresh the sign-in",
                                statusCode = 401,
                            ),
                        )
                    }
                }
            }
        } finally {
            // NonCancellable: a cancelled caller must still release the bound CustomTabs connection.
            withContext(NonCancellable + Dispatchers.Main.immediate) { service.dispose() }
        }
    }

    private companion object {
        const val TAG = "GmailMailRepository"
        const val SERVER_PREFS = "foldos_mail_server"
        const val KEY_SERVER_CLIENT_ID = "client_id"
        const val INBOX_PAGE_SIZE = 25
        const val SIGN_IN_TIMEOUT_MS = 10L * 60L * 1000L
        const val NO_NETWORK_MESSAGE = "No-network mode is on. Turn it off in Settings > Features to use Gmail."
    }
}

/** Short name used in the task brief for [GmailMailRepository]. */
typealias GmailRepository = GmailMailRepository
