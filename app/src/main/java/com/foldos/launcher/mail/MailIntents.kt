package com.foldos.launcher.mail

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri

/** Deep links into the Gmail app (with browser / chooser fallbacks). Every call swallows failures. */
object GmailIntents {

    /** Opens the Gmail app, or its Play Store page when it is not installed. */
    fun openGmail(context: Context) {
        val launch = context.packageManager.getLaunchIntentForPackage(GmailProviderClient.GMAIL_PACKAGE)
        if (launch != null) {
            start(context, launch)
        } else {
            start(context, Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${GmailProviderClient.GMAIL_PACKAGE}")))
        }
    }

    /** New message in Gmail (falls back to any mail app). */
    fun compose(context: Context, to: String? = null, subject: String? = null, body: String? = null) {
        val mailto = Uri.parse("mailto:${to.orEmpty()}")
        val intent = Intent(Intent.ACTION_SENDTO, mailto).apply {
            if (!subject.isNullOrBlank()) putExtra(Intent.EXTRA_SUBJECT, subject)
            if (!body.isNullOrBlank()) putExtra(Intent.EXTRA_TEXT, body)
        }
        val inGmail = Intent(intent).setPackage(GmailProviderClient.GMAIL_PACKAGE)
        if (!start(context, inGmail)) start(context, intent)
    }

    /**
     * Opens a conversation by Gmail thread id, in the Gmail app when it handles
     * `mail.google.com` links, else in the browser. Replying happens there ("Reply in Gmail").
     */
    fun openThread(context: Context, threadId: String, account: String? = null) {
        val builder = Uri.parse("https://mail.google.com/mail/").buildUpon()
        if (!account.isNullOrBlank()) builder.appendQueryParameter("authuser", account)
        val uri = Uri.parse(builder.build().toString() + "#all/" + Uri.encode(threadId))
        val view = Intent(Intent.ACTION_VIEW, uri)
        val inGmail = Intent(view).setPackage(GmailProviderClient.GMAIL_PACKAGE)
        if (!start(context, inGmail)) start(context, view)
    }

    /** Gmail's search results for [query] (web fallback). */
    fun search(context: Context, query: String, account: String? = null) {
        val builder = Uri.parse("https://mail.google.com/mail/").buildUpon()
        if (!account.isNullOrBlank()) builder.appendQueryParameter("authuser", account)
        val uri = Uri.parse(builder.build().toString() + "#search/" + Uri.encode(query))
        val view = Intent(Intent.ACTION_VIEW, uri)
        val inGmail = Intent(view).setPackage(GmailProviderClient.GMAIL_PACKAGE)
        if (!start(context, inGmail)) start(context, view)
    }

    private fun start(context: Context, intent: Intent): Boolean = runCatching {
        if (context !is Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }.isSuccess
}

/** The [Activity] hosting a Compose [Context] (unwraps `ContextWrapper`s), or null. */
internal fun Context.findActivity(): Activity? {
    var current: Context? = this
    while (current != null) {
        if (current is Activity) return current
        current = (current as? ContextWrapper)?.baseContext
    }
    return null
}
