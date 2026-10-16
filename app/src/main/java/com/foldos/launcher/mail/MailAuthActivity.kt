package com.foldos.launcher.mail

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CompletableDeferred
import net.openid.appauth.AuthState
import net.openid.appauth.AuthorizationException
import net.openid.appauth.AuthorizationRequest
import net.openid.appauth.AuthorizationResponse
import net.openid.appauth.AuthorizationService
import net.openid.appauth.AuthorizationServiceConfiguration
import net.openid.appauth.ResponseTypeValues

/** Outcome of one [MailAuthActivity] run, delivered through [MailAuthBridge]. */
sealed class MailAuthOutcome {
    /** Signed in; [authStateJson] is the serialized AppAuth `AuthState` with fresh tokens. */
    data class Success(val authStateJson: String) : MailAuthOutcome()

    data class Failure(val message: String, val cancelled: Boolean = false) : MailAuthOutcome()
}

/**
 * Hands the result of the sign-in activity back to whoever started it (the repository's
 * `connect`). One flow at a time: starting a new one abandons the previous waiter.
 */
internal object MailAuthBridge {
    private var pending: CompletableDeferred<MailAuthOutcome>? = null

    /** Registers a waiter for the next outcome; call right before starting [MailAuthActivity]. */
    fun begin(): CompletableDeferred<MailAuthOutcome> {
        pending?.complete(MailAuthOutcome.Failure("Replaced by a newer sign-in", cancelled = true))
        return CompletableDeferred<MailAuthOutcome>().also { pending = it }
    }

    fun deliver(outcome: MailAuthOutcome) {
        pending?.complete(outcome)
        pending = null
    }
}

/**
 * Runs the Google OAuth authorization-code + PKCE flow with AppAuth for the Gmail API
 * (`gmail.readonly`), exchanges the code for tokens and finishes. Started by
 * [GmailMailRepository.connect]; the redirect `com.foldos.launcher:/oauth2redirect` is caught by
 * AppAuth's `RedirectUriReceiverActivity` through the `appAuthRedirectScheme` manifest placeholder.
 *
 * Manifest (Integrate): `<activity android:name=".mail.MailAuthActivity" android:exported="false"
 * android:launchMode="singleTop" android:theme="@style/Theme.FoldOS" />`.
 */
class MailAuthActivity : ComponentActivity() {

    private var authService: AuthorizationService? = null
    private var delivered = false

    private val authorization = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        handleAuthorizationResult(result.data)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(32.dp),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Signing in to Gmail…",
                            modifier = Modifier.padding(top = 20.dp),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    }
                }
            }
        }
        val clientId = intent?.getStringExtra(EXTRA_CLIENT_ID)?.trim().orEmpty()
        if (clientId.isEmpty()) {
            finishWith(MailAuthOutcome.Failure("No OAuth client id. Paste one in Settings › Mail."))
            return
        }
        authService = AuthorizationService(this)
        if (savedInstanceState == null) {
            val request = AuthorizationRequest.Builder(
                SERVICE_CONFIGURATION,
                clientId,
                ResponseTypeValues.CODE,
                Uri.parse(REDIRECT_URI),
            )
                .setScopes(SCOPE_GMAIL_READONLY)
                .setPrompt(PROMPT_SELECT_ACCOUNT)
                .build()
            val launchIntent = runCatching { authService?.getAuthorizationRequestIntent(request) }.getOrNull()
            if (launchIntent == null) {
                finishWith(MailAuthOutcome.Failure("No browser available for Google sign-in."))
            } else {
                authorization.launch(launchIntent)
            }
        }
    }

    private fun handleAuthorizationResult(data: Intent?) {
        if (data == null) {
            finishWith(MailAuthOutcome.Failure("Sign-in cancelled", cancelled = true))
            return
        }
        val response = AuthorizationResponse.fromIntent(data)
        val exception = AuthorizationException.fromIntent(data)
        if (response == null) {
            val cancelled = exception?.code == AuthorizationException.GeneralErrors.USER_CANCELED_AUTH_FLOW.code
            finishWith(
                MailAuthOutcome.Failure(
                    exception?.errorDescription ?: exception?.error ?: "Sign-in failed",
                    cancelled = cancelled,
                ),
            )
            return
        }
        val state = AuthState(response, exception)
        val service = authService
        if (service == null) {
            finishWith(MailAuthOutcome.Failure("Sign-in service is gone"))
            return
        }
        service.performTokenRequest(response.createTokenExchangeRequest()) { tokenResponse, tokenException ->
            state.update(tokenResponse, tokenException)
            if (tokenResponse != null && state.isAuthorized) {
                finishWith(MailAuthOutcome.Success(state.jsonSerializeString()))
            } else {
                finishWith(
                    MailAuthOutcome.Failure(
                        tokenException?.errorDescription ?: tokenException?.error ?: "Token exchange failed",
                    ),
                )
            }
        }
    }

    private fun finishWith(outcome: MailAuthOutcome) {
        if (!delivered) {
            delivered = true
            MailAuthBridge.deliver(outcome)
        }
        if (!isFinishing) finish()
    }

    override fun onDestroy() {
        // A recreate (fold / unfold, rotation, dark-mode switch) must not resolve the waiter as
        // cancelled: the recreated instance still receives the real AppAuth result.
        if (!delivered && isFinishing && !isChangingConfigurations) {
            delivered = true
            MailAuthBridge.deliver(MailAuthOutcome.Failure("Sign-in cancelled", cancelled = true))
        }
        authService?.dispose()
        authService = null
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_CLIENT_ID = "com.foldos.launcher.mail.CLIENT_ID"

        /** Must match the `appAuthRedirectScheme` manifest placeholder in `app/build.gradle.kts`. */
        const val REDIRECT_SCHEME: String = "com.foldos.launcher"
        const val REDIRECT_URI: String = "$REDIRECT_SCHEME:/oauth2redirect"
        const val SCOPE_GMAIL_READONLY: String = "https://www.googleapis.com/auth/gmail.readonly"
        private const val PROMPT_SELECT_ACCOUNT = "select_account"

        val SERVICE_CONFIGURATION: AuthorizationServiceConfiguration = AuthorizationServiceConfiguration(
            Uri.parse("https://accounts.google.com/o/oauth2/v2/auth"),
            Uri.parse("https://oauth2.googleapis.com/token"),
        )

        fun intent(context: Context, clientId: String): Intent =
            Intent(context, MailAuthActivity::class.java).putExtra(EXTRA_CLIENT_ID, clientId)
    }
}
