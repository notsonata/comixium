package com.notsonata.comixium.offline

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.view.View
import android.view.ViewGroup
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.FrameLayout
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.notsonata.comixium.web.AdBlocker
import com.notsonata.comixium.web.Site
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import java.io.ByteArrayInputStream

/**
 * Finds a chapter's page images by letting the site's own reader do it, in a WebView the user
 * never sees (see collect.js). The WebView sits behind the app's screens: the reader only fills
 * in pages while its page is being drawn, so it has to be attached and visible, just covered.
 * One chapter at a time, on the main thread.
 */
class PageCollector(private val app: Context, private val adBlocker: AdBlocker, private val script: String) {

    sealed interface Result {
        data class Pages(val urls: List<String>, val title: String, val seriesTitle: String, val coverUrl: String) : Result
        data class Failed(val why: String) : Result
        /** The app went to the background (or the view isn't on screen): try again when it's back. */
        data object NotNow : Result
    }

    private val context = MutableContextWrapper(app)

    /** Placed behind the app's content by the UI (see [com.notsonata.comixium.ui.ComixiumApp]). */
    val host: FrameLayout = FrameLayout(context).apply {
        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        isFocusable = false
        isClickable = false
    }

    private var webView: WebView? = null
    private var pending: CompletableDeferred<Result>? = null
    private var onProgress: ((Int, Int) -> Unit)? = null

    fun retarget(activity: Context?) {
        context.baseContext = activity ?: app
    }

    /** Collects [url]'s page images; [progress] gets (found, total) as the reader fills them in. */
    suspend fun collect(url: String, progress: (found: Int, total: Int) -> Unit): Result {
        if (!host.isAttachedToWindow || host.windowVisibility != View.VISIBLE) return Result.NotNow
        val wv = webView ?: create().also { webView = it }
        val done = CompletableDeferred<Result>()
        pending = done
        onProgress = progress
        wv.loadUrl(url)
        return try {
            withTimeoutOrNull(TIMEOUT_MS) { done.await() } ?: Result.Failed("timeout")
        } finally {
            // Also when cancelled: stop the page, so nothing from it reaches the next chapter.
            pending = null
            onProgress = null
            webView?.let {
                it.stopLoading()
                it.loadUrl("about:blank")
            }
        }
    }

    /** The app left the screen: the reader stops filling in pages, so give up for now. */
    fun interrupt() {
        pending?.complete(Result.NotNow)
    }

    /** Frees the WebView when the queue is empty. */
    fun release() {
        interrupt()
        webView?.let {
            host.removeView(it)
            it.destroy()
        }
        webView = null
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun create(): WebView {
        val wv = WebView(context)
        wv.layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wv.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        wv.isFocusable = false
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)) {
            WebViewCompat.addDocumentStartJavaScript(wv, script, Site.ORIGIN_RULES)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(wv, "ComixiumCollector", Site.ORIGIN_RULES) { _, message, _, isMainFrame, _ ->
                if (isMainFrame) message.data?.let(::onMessage)
            }
        }
        wv.webViewClient = Client(injectManually = !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        host.addView(wv)
        return wv
    }

    private fun onMessage(json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        when (o.optString("type")) {
            "progress" -> onProgress?.invoke(o.optInt("found"), o.optInt("total"))
            "pages" -> {
                val arr = o.optJSONArray("urls") ?: return
                val urls = (0 until arr.length()).map { arr.optString(it) }
                if (urls.isEmpty() || urls.any { !it.startsWith("https://") && !it.startsWith("http://") }) {
                    pending?.complete(Result.Failed("missing"))
                } else {
                    pending?.complete(Result.Pages(urls, o.optString("title"), o.optString("series"), o.optString("cover")))
                }
            }
            "failed" -> pending?.complete(Result.Failed(o.optString("why").ifBlank { "unknown" }))
        }
    }

    private inner class Client(private val injectManually: Boolean) : WebViewClientCompat() {
        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
            !(request.isForMainFrame && Site.isSiteUrl(request.url.toString())) // stay on the chapter; no ads, no popups

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val url = request.url.toString()
            val accept = request.requestHeaders["Accept"] ?: request.requestHeaders["accept"]
            if (adBlocker.shouldBlock(url, accept, request.isForMainFrame)) return empty(204)
            // Nothing this visit does may count as reading: the progress upload is a POST to the account API.
            if (Site.isSiteUrl(url) && Site.pathOnly(url).startsWith("/api/v1/user/") && !request.method.equals("GET", ignoreCase = true)) {
                return WebResourceResponse("application/json", "utf-8", 200, "OK", emptyMap(), ByteArrayInputStream("{\"status\":\"ok\",\"result\":null}".toByteArray()))
            }
            // Only the image addresses matter: answer every image with a 1x1 placeholder.
            if (!request.isForMainFrame && accept != null && accept.startsWith("image/")) {
                return WebResourceResponse("image/png", null, 200, "OK", emptyMap(), ByteArrayInputStream(PIXEL))
            }
            return null
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            if (injectManually && Site.isSiteUrl(url)) view.evaluateJavascript(script, null)
        }

        override fun onPageFinished(view: WebView, url: String) {
            if (injectManually && Site.isSiteUrl(url)) view.evaluateJavascript(script, null)
        }

        override fun onRenderProcessGone(view: WebView, detail: android.webkit.RenderProcessGoneDetail): Boolean {
            // Never take the app down with it: drop this WebView and fail the chapter (it can be retried).
            if (view === webView) {
                host.removeView(view)
                view.destroy()
                webView = null
            }
            pending?.complete(Result.Failed("crashed"))
            return true
        }

        private fun empty(code: Int) = WebResourceResponse("text/plain", "utf-8", code, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }

    private companion object {
        const val TIMEOUT_MS = 180_000L
        /** A 1x1 transparent PNG. */
        val PIXEL: ByteArray = android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=", android.util.Base64.DEFAULT,
        )
    }
}
