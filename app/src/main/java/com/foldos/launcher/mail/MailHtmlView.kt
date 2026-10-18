package com.foldos.launcher.mail

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.nestedscroll.rememberNestedScrollInteropConnection
import androidx.compose.ui.viewinterop.AndroidView
import java.io.ByteArrayInputStream
import java.util.Locale

/**
 * Renders the server-sanitized HTML body of a message in a locked-down [WebView].
 *
 * - JavaScript, DOM storage, file / content access, geolocation, zoom and multiple windows are off; mixed content is
 *   never allowed.
 * - Every http(s) subresource is answered with an empty response until [allowImages] is true; then only https images
 *   load (everything else stays blocked). Nothing is remembered between messages.
 * - Link taps never navigate the WebView: http(s) opens in the default browser, `mailto:` calls [onMailto] (a FoldOS
 *   compose draft), `tel:` opens the dialer (no call is placed), anything else is ignored.
 * - The view sizes itself to its content so it can live inside the reader's scrolling column; the nested-scroll
 *   interop hands vertical drags to that column, wide content scrolls sideways or is scaled to fit.
 * - The body is never logged. The WebView is destroyed when this leaves the composition.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
internal fun MailHtmlView(
    html: String,
    allowImages: Boolean,
    dark: Boolean,
    fontScale: Float,
    onMailto: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val document = wrapMailHtml(html, dark, fontScale)
    val key = "${document.hashCode()}|${document.length}|$allowImages"
    AndroidView(
        modifier = modifier.nestedScroll(rememberNestedScrollInteropConnection()),
        factory = { context ->
            WebView(context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setBackgroundColor(AndroidColor.TRANSPARENT)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = true
                overScrollMode = WebView.OVER_SCROLL_NEVER
                isNestedScrollingEnabled = true
                with(settings) {
                    javaScriptEnabled = false
                    domStorageEnabled = false
                    allowFileAccess = false
                    allowContentAccess = false
                    setGeolocationEnabled(false)
                    mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    javaScriptCanOpenWindowsAutomatically = false
                    setSupportMultipleWindows(false)
                    setSupportZoom(false)
                    builtInZoomControls = false
                    displayZoomControls = false
                    cacheMode = WebSettings.LOAD_NO_CACHE
                    // The CSS already carries the font size (system and launcher text size); do not scale twice.
                    textZoom = 100
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                webViewClient = MailWebViewClient(onMailto)
            }
        },
        update = { view ->
            val client = view.webViewClient as? MailWebViewClient
            client?.onMailto = onMailto
            client?.allowImages = allowImages
            view.settings.blockNetworkImage = !allowImages
            if (view.tag != key) {
                view.tag = key
                view.loadDataWithBaseURL(null, document, "text/html", "utf-8", null)
            }
        },
        onRelease = { view ->
            view.stopLoading()
            view.webViewClient = WebViewClient()
            (view.parent as? ViewGroup)?.removeView(view)
            view.removeAllViews()
            view.destroy()
        },
    )
}

private class MailWebViewClient(var onMailto: (String) -> Unit) : WebViewClient() {
    @Volatile
    var allowImages: Boolean = false

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        val scheme = request.url.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        val accept = request.requestHeaders?.entries
            ?.firstOrNull { it.key.equals("Accept", ignoreCase = true) }?.value.orEmpty()
        val isImage = accept.contains("image/", ignoreCase = true)
        if (allowImages && scheme == "https" && isImage && !request.isForMainFrame) return null
        return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
    }

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        openLink(view.context, request.url.toString(), onMailto)
        // Never navigate inside the WebView.
        return true
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean = true
}

private fun openLink(context: Context, url: String, onMailto: (String) -> Unit) {
    val scheme = Uri.parse(url).scheme?.lowercase(Locale.ROOT) ?: return
    when (scheme) {
        "http", "https" -> start(context, Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
        "mailto" -> onMailto(url)
        // ACTION_DIAL only opens the dialer with the number; it never places the call.
        "tel" -> start(context, Intent(Intent.ACTION_DIAL, Uri.parse(url)))
        else -> Unit
    }
}

private fun start(context: Context, intent: Intent) {
    runCatching {
        if (context.findActivity() == null) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

/** A prefilled FoldOS compose draft for a `mailto:` link (to, cc, subject and body are honored). */
internal fun mailtoDraft(url: String): MailComposeState {
    val rest = url.substringAfter(':', "")
    val to = Uri.decode(rest.substringBefore('?')).trim()
    val params = rest.substringAfter('?', "").split('&').mapNotNull { pair ->
        val name = pair.substringBefore('=', "").lowercase(Locale.ROOT)
        if (name.isEmpty()) null else name to Uri.decode(pair.substringAfter('=', "").replace("+", " "))
    }.toMap()
    val extraTo = params["to"].orEmpty()
    return MailComposeState(
        title = "New message",
        seedTo = listOf(to, extraTo).filter { it.isNotBlank() }.joinToString(", "),
        seedCc = params["cc"].orEmpty(),
        seedSubject = params["subject"].orEmpty(),
        seedBody = params["body"].orEmpty(),
    )
}

/**
 * The sanitized [html] inside a small document: viewport meta, images / tables / pre capped to the width, long words
 * wrap, system font at the app's text size, line height 1.5. Light appearance: dark text on a transparent
 * background. Dark appearance: the whole body is inverted (`invert(1) hue-rotate(180deg)`, which flips lightness and
 * keeps hues), images are inverted back, so dark-on-white newsletters become light-on-dark and dark-on-dark or
 * light-on-light never happens; the background stays transparent over the pane.
 */
internal fun wrapMailHtml(html: String, dark: Boolean, fontScale: Float): String {
    val px = String.format(Locale.US, "%.1f", 15f * fontScale.coerceIn(0.5f, 3f))
    val invert = "invert(1) hue-rotate(180deg)"
    val darkCss = if (dark) "#w{filter:$invert}#w img,#w video{filter:$invert}" else ""
    return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">" +
        "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">" +
        "<style>" +
        "html,body{margin:0;padding:0;background:transparent}" +
        "body{font-family:system-ui,-apple-system,Roboto,sans-serif;font-size:${px}px;line-height:1.5;color:#1a1a1a;" +
        "word-wrap:break-word;overflow-wrap:anywhere}" +
        "#w{padding:2px 0 8px}" +
        "img{max-width:100%;height:auto}" +
        "table{max-width:100%}" +
        "pre{max-width:100%;white-space:pre-wrap;overflow:auto}" +
        "blockquote{margin:.6em 0;padding-left:.8em;border-left:3px solid rgba(128,128,128,.5)}" +
        "a{color:#0b57d0}" +
        darkCss +
        "</style></head><body><div id=\"w\">" + html + "</div></body></html>"
}
