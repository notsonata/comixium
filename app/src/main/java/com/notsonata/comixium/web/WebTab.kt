package com.notsonata.comixium.web

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.MutableContextWrapper
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.toArgb
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import androidx.webkit.WebResourceErrorCompat
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewClientCompat
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.notsonata.comixium.BuildConfig
import java.io.ByteArrayInputStream

data class WebError(val url: String, val code: Int, val description: String, val isHttp: Boolean = false)

/**
 * One tab: a WebView with its own back stack, wrapped in a pull-to-refresh layout, plus the
 * observable state the native UI renders (title, progress, errors, reader/dialog flags).
 */
class WebTab(
    val key: String,
    /** Page this tab starts on and returns to when its bottom-bar item is tapped again; null = blank until told. */
    val startPath: String?,
    private val engine: WebEngine,
) {
    var url by mutableStateOf<String?>(null)
        private set
    var title by mutableStateOf("")
        private set
    var progress by mutableIntStateOf(0)
        private set
    var loading by mutableStateOf(false)
        private set
    var canGoBack by mutableStateOf(false)
        private set
    var isReader by mutableStateOf(false)
        private set
    var dialogOpen by mutableStateOf(false)
        private set
    var error by mutableStateOf<WebError?>(null)
        private set
    /** Bumped whenever the WebView instance is replaced (after a renderer crash). */
    var generation by mutableIntStateOf(0)
        private set

    val isAtRoot: Boolean get() = startPath != null && Site.samePage(url, startPath)
    val displayTitle: String get() = PageTitle.of(url, title)
    val isCreated: Boolean get() = created

    private val main = Handler(Looper.getMainLooper())
    private val contextWrapper = MutableContextWrapper(null)
    private var created = false
    private var clearHistoryOnLoad = false
    /** Path requested before the views existed (e.g. a More-screen link); loaded on attach. */
    private var pendingPath: String? = null
    /** A site helper (openLogin, openSettings...) to run once a page with the site header has loaded. */
    private var pendingAction: String? = null
    lateinit var webView: WebView
        private set
    lateinit var container: SwipeRefreshLayout
        private set

    /** Creates the views on first use, or re-targets them at a new activity. */
    fun attach(activity: Context) {
        contextWrapper.baseContext = activity
        if (created) return
        created = true
        container = SwipeRefreshLayout(contextWrapper).apply {
            setOnRefreshListener { reload() }
            setOnChildScrollUpCallback { _, _ -> !canPullToRefresh() }
        }
        installWebView()
        (pendingPath ?: startPath)?.let(::loadPath)
        pendingPath = null
    }

    /** Load [path] now, or as soon as the tab is first shown. Replaces the tab's history. */
    fun open(path: String) {
        if (!created) {
            pendingPath = path
            return
        }
        clearHistoryOnLoad = true
        loadPath(path)
    }

    /** Point the views at the application context so a destroyed activity isn't leaked. */
    fun detach(appContext: Context) {
        contextWrapper.baseContext = appContext
    }

    fun loadPath(path: String) {
        error = null
        webView.loadUrl(Site.url(engine.domain, path))
    }

    fun loadUrl(url: String) {
        error = null
        webView.loadUrl(url)
    }

    /** Navigate to [path] on top of the current history (deep links), now or once shown. */
    fun push(path: String) {
        if (!created) pendingPath = path else loadPath(path)
    }

    /** Bottom-bar reselect: scroll to top if already on the tab's start page, else go there fresh. */
    fun goRoot() {
        val root = startPath ?: return
        if (isAtRoot && error == null) {
            js("window.scrollTo({top:0,behavior:'smooth'})")
        } else {
            clearHistoryOnLoad = true
            loadPath(root)
        }
    }

    /**
     * Runs one of the page script's helpers (see comixium.js). Helpers that need the site header
     * (login, settings) can't work on reader pages, so the tab hops to the home page and runs it there.
     */
    fun siteAction(name: String) {
        js("window.__comixium ? window.__comixium.$name() : false") { result ->
            if (result != "true") {
                pendingAction = name
                loadPath("/")
            }
        }
    }

    fun reload() {
        val failed = error
        error = null
        if (failed != null) webView.loadUrl(failed.url) else webView.reload()
    }

    /** Back inside the page: closes a site dialog first, then walks the history. */
    fun handleBack(): Boolean {
        if (dialogOpen) {
            js("window.__comixium && window.__comixium.closeDialog()")
            dialogOpen = false
            return true
        }
        if (webView.canGoBack()) {
            webView.goBack()
            return true
        }
        return false
    }

    fun js(script: String, callback: ((String?) -> Unit)? = null) {
        if (!created) return
        webView.evaluateJavascript(script) { callback?.invoke(it) }
    }

    fun setBackgroundColor(argb: Int) {
        if (created) webView.setBackgroundColor(argb)
    }

    fun onPause() { if (created) webView.onPause() }
    fun onResume() { if (created) webView.onResume() }

    private fun canPullToRefresh(): Boolean =
        error == null && !isReader && !dialogOpen && webView.scrollY <= 0

    @SuppressLint("SetJavaScriptEnabled")
    private fun installWebView() {
        val wv = WebView(contextWrapper)
        wv.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        wv.setBackgroundColor(engine.colors.bg.toArgb())
        wv.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = true
            setSupportMultipleWindows(false)
            javaScriptCanOpenWindowsAutomatically = false
            useWideViewPort = true
            loadWithOverviewMode = false
            textZoom = 100
            // Pinch zoom is allowed by the WebView, but the site's viewport tag still disables it on
            // every page except chapters, where the page script relaxes it (see applyZoom).
            setSupportZoom(true)
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(wv.settings, false)
        }
        wv.setDownloadListener { url, userAgent, contentDisposition, mimetype, _ ->
            // blob:/data: downloads are handled in the page script (they can't be fetched from here).
            if (Site.schemeOf(url) == "http" || Site.schemeOf(url) == "https") {
                contextWrapper.baseContext?.let { Downloads.enqueue(it, url, userAgent, contentDisposition, mimetype) }
            }
        }
        wv.setOnLongClickListener {
            val hit = wv.hitTestResult
            val src = hit.extra
            val isImage = hit.type == WebView.HitTestResult.IMAGE_TYPE || hit.type == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE
            if (isImage && src != null && (Site.schemeOf(src) == "https" || Site.schemeOf(src) == "http")) {
                engine.services?.onImageLongPress(src, displayTitle.ifBlank { "comixium" })
                true
            } else {
                false
            }
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(wv, true)
        }
        WebView.setWebContentsDebuggingEnabled(BuildConfig.DEBUG)

        val documentStart = WebViewFeature.isFeatureSupported(WebViewFeature.DOCUMENT_START_SCRIPT)
        if (documentStart) {
            WebViewCompat.addDocumentStartJavaScript(wv, engine.pageScript, Site.ORIGIN_RULES)
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            WebViewCompat.addWebMessageListener(wv, "ComixiumNative", Site.ORIGIN_RULES) { _, message, _, isMainFrame, _ ->
                if (isMainFrame) message.data?.let(::onMessage)
            }
        }

        wv.webViewClient = Client(injectManually = !documentStart)
        wv.webChromeClient = Chrome()
        webView = wv
        container.addView(wv)
    }

    private fun onMessage(json: String) {
        if (PageState.type(json) == "readerHelp") {
            engine.readerHelpOpen = true
            return
        }
        PageDownload.parse(json)?.let { d ->
            contextWrapper.baseContext?.let { Downloads.saveBytes(it, d.name, d.mime, d.bytes) }
            return
        }
        val state = PageState.parse(json) ?: return
        if (state.title.isNotBlank()) title = state.title
        isReader = state.isReader || Site.isReaderUrl(state.url)
        dialogOpen = state.dialogOpen
        engine.onPageState(state)
        if (state.isReader) js(engine.reader.pageScriptCall())
        val action = pendingAction
        if (action != null && state.auth != AuthState.UNKNOWN) {
            pendingAction = null
            js("window.__comixium && window.__comixium.$action()")
        }
    }

    /** Renderer crashed or was killed for memory: swap in a fresh WebView at the same URL. */
    private fun recover() {
        val last = url
        container.removeView(webView)
        webView.destroy()
        installWebView()
        generation++
        if (last != null) webView.loadUrl(last) else startPath?.let(::loadPath)
    }

    private fun openExternal(url: String) {
        val ctx = contextWrapper.baseContext ?: return
        val scheme = CustomTabColorSchemeParams.Builder()
            .setToolbarColor(engine.colors.bg.toArgb())
            .setNavigationBarColor(engine.colors.bg.toArgb())
            .build()
        val intent = CustomTabsIntent.Builder()
            .setDefaultColorSchemeParams(scheme)
            .setShowTitle(true)
            .build()
        if (ctx !is android.app.Activity) intent.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { intent.launchUrl(ctx, Uri.parse(url)) }
    }

    private fun openSystem(url: String) {
        val ctx = contextWrapper.baseContext ?: return
        val intent = runCatching {
            if (url.startsWith("intent:", ignoreCase = true)) {
                Intent.parseUri(url, Intent.URI_INTENT_SCHEME).apply {
                    // Never let a page target a specific (possibly non-exported) component.
                    component = null
                    selector = null
                    addCategory(Intent.CATEGORY_BROWSABLE)
                }
            } else {
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE)
            }
        }.getOrNull() ?: return
        if (ctx !is android.app.Activity) intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            ctx.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            intent.getStringExtra("browser_fallback_url")?.let { fallback ->
                if (Site.schemeOf(fallback) == "https") openExternal(fallback)
            }
        }
    }

    private inner class Client(private val injectManually: Boolean) : WebViewClientCompat() {

        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
            val target = request.url.toString()
            return when (val action = LinkPolicy.decide(target, request.hasGesture(), request.isForMainFrame, engine.adBlocker)) {
                LinkAction.Internal -> false
                is LinkAction.External -> { openExternal(action.url); true }
                is LinkAction.System -> { openSystem(action.url); true }
                LinkAction.Block -> { main.post { engine.noteBlocked() }; true }
            }
        }

        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
            val target = request.url.toString()
            val accept = request.requestHeaders["Accept"] ?: request.requestHeaders["accept"]
            if (!engine.adBlocker.shouldBlock(target, accept, request.isForMainFrame)) return null
            main.post { engine.noteBlocked() }
            return WebResourceResponse("text/plain", "utf-8", 204, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }

        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
            loading = true
            if (injectManually && Site.isSiteUrl(url)) view.evaluateJavascript(engine.pageScript, null)
        }

        override fun onPageFinished(view: WebView, url: String) {
            loading = false
            container.isRefreshing = false
            if (clearHistoryOnLoad) {
                clearHistoryOnLoad = false
                view.clearHistory()
            }
            canGoBack = view.canGoBack()
            if (injectManually && Site.isSiteUrl(url)) view.evaluateJavascript(engine.pageScript, null)
            if (error == null && Site.isSiteUrl(url)) engine.onLoadSucceeded(Site.hostOf(url))
        }

        override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
            this@WebTab.url = url
            canGoBack = view.canGoBack()
            isReader = Site.isReaderUrl(url)
        }

        override fun onReceivedError(view: WebView, request: WebResourceRequest, err: WebResourceErrorCompat) {
            if (!request.isForMainFrame) return
            val failedUrl = request.url.toString()
            val code = if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_CODE)) err.errorCode else ERROR_UNKNOWN
            val desc = if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_RESOURCE_ERROR_GET_DESCRIPTION)) err.description.toString() else ""
            if (code in UNREACHABLE && tryFallback(view, failedUrl)) return
            error = WebError(failedUrl, code, desc)
            loading = false
            container.isRefreshing = false
        }

        override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
            if (!request.isForMainFrame) return
            val status = response.statusCode
            // 403/503 are usually Cloudflare challenges that resolve themselves; 52x means the origin is down.
            if (status in 520..526 || status == 502 || status == 504) {
                val failedUrl = request.url.toString()
                if (tryFallback(view, failedUrl)) return
                error = WebError(failedUrl, status, response.reasonPhrase ?: "HTTP $status", isHttp = true)
            }
        }

        private fun tryFallback(view: WebView, failedUrl: String): Boolean {
            val host = Site.hostOf(failedUrl) ?: return false
            val failedDomain = Site.DOMAINS.firstOrNull { host == it || host.endsWith(".$it") } ?: return false
            val next = engine.fallbackFrom(failedDomain) ?: return false
            view.loadUrl(Site.swapDomain(failedUrl, next))
            return true
        }

        override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
            main.post { recover() }
            return true
        }
    }

    private inner class Chrome : WebChromeClient() {
        override fun onProgressChanged(view: WebView, newProgress: Int) {
            progress = newProgress
        }

        override fun onShowFileChooser(
            view: WebView,
            callback: android.webkit.ValueCallback<Array<Uri>>,
            params: FileChooserParams,
        ): Boolean = engine.services?.chooseFiles(callback, params) ?: false

        override fun onReceivedTitle(view: WebView, t: String?) {
            if (!t.isNullOrBlank() && !t.startsWith("http")) title = t
        }
    }

    companion object {
        private const val ERROR_UNKNOWN = -1
        val UNREACHABLE = setOf(
            WebViewClient.ERROR_HOST_LOOKUP,
            WebViewClient.ERROR_CONNECT,
            WebViewClient.ERROR_TIMEOUT,
            WebViewClient.ERROR_FAILED_SSL_HANDSHAKE,
        )
    }
}
