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
 * Runs download jobs in a WebView the user never sees (see collect.js): a chapter's page images
 * (the site's own reader finds them) plus what that reader needs offline, or a quick look-up for a
 * series download. The WebView sits behind the app's screens: the reader only fills in pages while
 * its page is being drawn, so it has to be attached and visible, just covered.
 * One job at a time, on the main thread.
 */
class PageCollector(private val app: Context, private val adBlocker: AdBlocker, private val script: String) {

    /** What to do on the page; sent to collect.js when it says hello. */
    data class Job(val mode: String, val number: String = "", val comments: Boolean = false, val have: Collection<String> = emptyList()) {
        fun toJson(): String = JSONObject()
            .put("mode", mode).put("number", number).put("comments", comments)
            .put("have", org.json.JSONArray(have.toList())).toString()
    }

    /** The page, its files and the API answers the reader used, for reading offline. */
    class Recording(
        val html: String,
        val answers: List<DownloadStore.Response>,
        /** Files fetched this time (url -> type, bytes); ones the app already had aren't sent again. */
        val newAssets: Map<String, Pair<String, ByteArray>>,
        /** Every file the page uses. */
        val assets: List<String>,
    )

    sealed interface Result {
        data class Pages(
            val urls: List<String>, val title: String, val seriesTitle: String, val coverUrl: String,
            val number: String, val group: String, val next: String, val recording: Recording,
        ) : Result
        /** A chapter looked at without saving it. */
        data class Info(val number: String, val group: String, val next: String) : Result
        /** Rows of a series page's chapter list. */
        data class Rows(val rows: List<SeriesWalk.Row>) : Result
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
    private var job: Job? = null
    private var html = ""
    private val answers = mutableListOf<DownloadStore.Response>()
    private val newAssets = linkedMapOf<String, Pair<String, ByteArray>>()

    fun retarget(activity: Context?) {
        context.baseContext = activity ?: app
    }

    /** Runs [job] on [url]; [progress] gets (found, total) as the reader fills pages in. */
    suspend fun collect(url: String, job: Job, progress: (found: Int, total: Int) -> Unit = { _, _ -> }): Result {
        if (!host.isAttachedToWindow || host.windowVisibility != View.VISIBLE) return Result.NotNow
        val wv = webView ?: create().also { webView = it }
        val done = CompletableDeferred<Result>()
        pending = done
        onProgress = progress
        this.job = job
        html = ""
        answers.clear()
        newAssets.clear()
        wv.loadUrl(url)
        return try {
            withTimeoutOrNull(if (job.mode == "chapter") TIMEOUT_MS else LOOKUP_TIMEOUT_MS) { done.await() } ?: Result.Failed("timeout")
        } finally {
            // Also when cancelled: stop the page, so nothing from it reaches the next job.
            pending = null
            onProgress = null
            this.job = null
            html = ""
            answers.clear()
            newAssets.clear()
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
            WebViewCompat.addWebMessageListener(wv, "ComixiumCollector", Site.ORIGIN_RULES) { _, message, _, isMainFrame, reply ->
                if (!isMainFrame) return@addWebMessageListener
                val data = message.data ?: return@addWebMessageListener
                if (data.startsWith("{\"type\":\"hello\"")) {
                    job?.let { runCatching { reply.postMessage(it.toJson()) } }
                } else {
                    onMessage(data)
                }
            }
        }
        wv.webViewClient = Client(injectManually = !WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT))
        host.addView(wv)
        return wv
    }

    private fun onMessage(json: String) {
        if (pending == null) return
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        when (o.optString("type")) {
            "progress" -> onProgress?.invoke(o.optInt("found"), o.optInt("total"))
            "asset" -> {
                val url = o.optString("url")
                val bytes = runCatching { android.util.Base64.decode(o.optString("data"), android.util.Base64.DEFAULT) }.getOrNull()
                if (isWebAddress(url) && bytes != null && bytes.size <= MAX_ASSET_BYTES) newAssets[url] = o.optString("ct") to bytes
            }
            "answer" -> {
                val url = o.optString("url")
                if (Site.isSiteUrl(url)) answers += DownloadStore.Response(url, o.optString("ct"), o.optString("enc"), o.optString("body").toByteArray())
            }
            "document" -> html = o.optString("html")
            "pages" -> {
                val urls = strings(o.optJSONArray("urls"))
                if (urls.isEmpty() || urls.any { !isWebAddress(it) } || html.isEmpty()) {
                    pending?.complete(Result.Failed(if (html.isEmpty()) "nopage" else "missing"))
                } else {
                    val recording = Recording(html, answers.toList(), LinkedHashMap(newAssets), strings(o.optJSONArray("assets")).filter(::isWebAddress))
                    pending?.complete(
                        Result.Pages(
                            urls, o.optString("title"), o.optString("series"), o.optString("cover"),
                            o.optString("number"), o.optString("group"), o.optString("next"), recording,
                        ),
                    )
                }
            }
            "info" -> pending?.complete(Result.Info(o.optString("number"), o.optString("group"), o.optString("next")))
            "rows" -> {
                val arr = o.optJSONArray("rows")
                val rows = (0 until (arr?.length() ?: 0)).mapNotNull { i ->
                    val r = arr?.optJSONObject(i) ?: return@mapNotNull null
                    val path = r.optString("path")
                    if (ChapterLink.parse(path) == null) null else SeriesWalk.Row(path, r.optString("number"), r.optString("group"))
                }
                pending?.complete(Result.Rows(rows))
            }
            "failed" -> pending?.complete(Result.Failed(o.optString("why").ifBlank { "unknown" }))
        }
    }

    private fun strings(arr: org.json.JSONArray?): List<String> = (0 until (arr?.length() ?: 0)).map { arr!!.optString(it) }

    private fun isWebAddress(u: String) = u.startsWith("https://") || u.startsWith("http://")

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
        const val TIMEOUT_MS = 240_000L
        const val LOOKUP_TIMEOUT_MS = 60_000L
        const val MAX_ASSET_BYTES = 8 * 1024 * 1024
        /** A 1x1 transparent PNG. */
        val PIXEL: ByteArray = android.util.Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNkYAAAAAYAAjCB0C8AAAAASUVORK5CYII=", android.util.Base64.DEFAULT,
        )
    }
}
