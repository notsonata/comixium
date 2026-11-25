package com.notsonata.comixium.web

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.notsonata.comixium.ui.theme.ComixColors
import com.notsonata.comixium.ui.theme.withSiteTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * App-wide web state: which domain we're on, the site's current theme and login state, the ad
 * blocker, and the injected page script. Lives as long as the process so tabs (and their pages)
 * survive activity recreation.
 */
class WebEngine(private val app: Application) {

    private val prefs = app.getSharedPreferences("comixium", Context.MODE_PRIVATE)

    val adBlocker = AdBlocker { runCatching { app.assets.open(HOSTS_ASSET) }.getOrNull() }.apply {
        enabled = prefs.getBoolean(KEY_ADBLOCK, true)
        loadAsync()
    }

    /** Domain new loads go to. Switches to the mirror automatically if the current one is unreachable. */
    var domain by mutableStateOf(prefs.getString(KEY_DOMAIN, null)?.takeIf { it in Site.DOMAINS } ?: Site.DOMAINS[0])
        private set

    var colors by mutableStateOf(loadCachedTheme())
        private set

    var auth by mutableStateOf(AuthState.UNKNOWN)
        private set

    /** Requests cancelled by the ad blocker plus popunders removed, for the settings screen. */
    var blockedCount by mutableIntStateOf(0)
        private set

    val recentSearches = RecentSearches(prefs)
    val reader = ReaderPrefs(prefs)
    val appPrefs = AppPrefs(prefs)

    /** All options the page script applies, as one setPrefs call. */
    fun pagePrefsCall(): String =
        "window.__comixium && window.__comixium.setPrefs({${reader.pageScriptFields()},${appPrefs.pageScriptFields()}})"

    /** The tab currently on screen (null on native-only screens); used for hardware keys. */
    var activeTab: WebTab? = null

    /** Set by the activity while it's alive. */
    var services: ActivityServices? = null

    /** Re-sends page options to every open page after they change. */
    fun pushPagePrefs() {
        val call = pagePrefsCall()
        tabs.values.filter { it.isCreated }.forEach { it.js(call) }
    }

    /** Mobile optimizations rewrite some of the site's styles, so turning them off needs a reload. */
    fun setMobileOptimizations(on: Boolean) {
        appPrefs.updateMobileOptimizations(on)
        pushPagePrefs()
        tabs.values.filter { it.isCreated }.forEach { it.reload() }
    }

    /** A setting changed from inside a page (the reader's settings sheet, the ✕ on the home share bar). */
    internal fun onPagePref(key: String, value: Any?) {
        val handled = when (key) {
            "share" -> (value as? Boolean)?.let { appPrefs.updateHomeShareBar(it) } != null
            "announce" -> (value as? Boolean)?.let { appPrefs.updateHomeAnnouncements(it) } != null
            else -> reader.apply(key, value)
        }
        if (handled) pushPagePrefs()
    }

    /** A comix.ws / comix.to link opened from another app, waiting for the UI to show it. */
    var deepLinkPath by mutableStateOf<String?>(null)

    // --- Google sign-in (see GoogleSignIn) ---------------------------------------------------------

    /** Google's return link, handed over by the browser, waiting for the UI to finish it in a tab. */
    var googleCallbackUrl by mutableStateOf<String?>(null)

    /** When Google's page was sent to the browser (elapsed ms), until its return link comes back. */
    private var googleOpenedAt = 0L

    /** Shown when the user comes back from the browser without the return link (see the dialog). */
    var googleReturnHint by mutableStateOf(false)
        private set

    fun onGoogleCallback(url: String) {
        googleOpenedAt = 0L
        googleReturnHint = false
        googleCallbackUrl = url
    }

    fun dismissGoogleReturnHint() {
        googleOpenedAt = 0L
        googleReturnHint = false
    }

    /** The browser picker's choice. */
    fun openGoogleIn(url: String, packageName: String) {
        browserChoice = null
        googleOpenedAt = SystemClock.elapsedRealtime()
        services?.openInBrowserApp(url, packageName)
    }

    /** Set while Google sign-in waits for the user to let the app open [this] domain's links. */
    var googleLinkHost by mutableStateOf<String?>(null)
        private set
    /** What Android reported for [googleLinkHost], shown in the dialog so the user knows what's missing. */
    var googleLinkAccess by mutableStateOf(LinkAccess.ALLOWED)
        private set
    private var pendingGoogleUrl: String? = null

    /**
     * Sends Google's sign-in page to the browser, once the app can receive the return link.
     * [force] skips the check (the user says it's set; some phones report it differently).
     */
    fun startGoogleSignIn(authorizeUrl: String, returnHost: String, force: Boolean = false) {
        val s = services ?: return
        val access = if (force) LinkAccess.ALLOWED else s.linkAccess(returnHost)
        if (access == LinkAccess.ALLOWED) {
            cancelGoogleSignIn()
            googleOpenedAt = SystemClock.elapsedRealtime()
            s.openInBrowser(authorizeUrl)
        } else {
            pendingGoogleUrl = authorizeUrl
            googleLinkAccess = access
            googleLinkHost = returnHost
        }
    }

    /** Browsers to pick from when the phone has no default browser (never other apps). */
    var browserChoice by mutableStateOf<BrowserChoice?>(null)

    /** The dialog's "Continue anyway". */
    fun continueGoogleSignIn() {
        val host = googleLinkHost ?: return
        val url = pendingGoogleUrl ?: return
        startGoogleSignIn(url, host, force = true)
    }

    fun cancelGoogleSignIn() {
        pendingGoogleUrl = null
        googleLinkHost = null
    }

    /** Back in the app (e.g. from the link settings): carry on if links are now allowed, else say what's still missing. */
    fun onActivityResumed() {
        val host = googleLinkHost
        val url = pendingGoogleUrl
        if (host != null && url != null) {
            startGoogleSignIn(url, host)
            return
        }
        // Back from the browser without Google's return link (a link coming in arrives before this).
        if (googleOpenedAt > 0 && googleCallbackUrl == null && SystemClock.elapsedRealtime() - googleOpenedAt > 4000) {
            googleReturnHint = true
        }
    }

    private val tabs = linkedMapOf<String, WebTab>()

    /** Tabs live as long as the process, so switching tabs or recreating the activity keeps pages. */
    fun tab(key: String, startPath: String?): WebTab = tabs.getOrPut(key) { WebTab(key, startPath, this) }

    val createdTabs: Collection<WebTab> get() = tabs.values

    /** Moves every open tab to the same page on [domain] (after the user picks a different domain). */
    fun switchDomain(d: String) {
        selectDomain(d)
        failedDomains.clear()
        tabs.values.filter { it.isCreated }.forEach { t ->
            val u = t.url
            if (u != null && Site.isSiteUrl(u)) t.loadUrl(Site.swapDomain(u, d)) else t.startPath?.let(t::loadPath)
        }
    }

    /** Bytes in the app's cache folder (the WebView's HTTP cache plus shared-image copies). */
    suspend fun cacheBytes(): Long = withContext(Dispatchers.IO) { dirSize(app.cacheDir) }

    /**
     * Clears the WebView's disk cache and the app's own cached files; returns the bytes freed.
     * The WebView deletes its files in the background, so this waits for the size to settle.
     */
    suspend fun clearCache(): Long {
        val before = cacheBytes()
        tabs.values.firstOrNull { it.isCreated }?.webView?.clearCache(true)
        withContext(Dispatchers.IO) { File(app.cacheDir, Downloads.SHARED_DIR).deleteRecursively() }
        var last = cacheBytes()
        if (before < 1024) return (before - last).coerceAtLeast(0)
        repeat(15) {
            delay(200)
            val now = cacheBytes()
            if (now == last && now < before) return (before - now).coerceAtLeast(0)
            last = now
        }
        return (before - last).coerceAtLeast(0)
    }

    /** Domains we already fell back from during this process, so a total outage can't ping-pong. */
    private val failedDomains = mutableSetOf<String>()

    val pageScript: String by lazy {
        val css = app.assets.open("inject/comixium.css").bufferedReader().use { it.readText() }
        val js = app.assets.open("inject/comixium.js").bufferedReader().use { it.readText() }
        js.replace("__COMIXIUM_CSS__", JSONObject.quote(css))
    }

    var adBlockEnabled by mutableStateOf(adBlocker.enabled)
        private set

    fun updateAdBlock(on: Boolean) {
        adBlocker.enabled = on
        adBlockEnabled = on
        prefs.edit { putBoolean(KEY_ADBLOCK, on) }
    }

    fun selectDomain(d: String) {
        if (d !in Site.DOMAINS) return
        domain = d
        prefs.edit { putString(KEY_DOMAIN, d) }
    }

    /**
     * Called when [failed] could not be reached. Returns the domain to retry on, or null if none is
     * left or the device itself is offline. The switch lasts for this session only; the next launch
     * tries the preferred domain again (logins are per domain, so we don't want to stick to a mirror).
     */
    fun fallbackFrom(failed: String): String? {
        if (!isOnline()) return null
        failedDomains += failed
        val next = Site.DOMAINS.firstOrNull { it != failed && it !in failedDomains } ?: return null
        domain = next
        return next
    }

    fun isOnline(): Boolean {
        val cm = app.getSystemService(ConnectivityManager::class.java) ?: return true
        val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    fun onLoadSucceeded(host: String?) {
        Site.DOMAINS.firstOrNull { host == it || host?.endsWith(".$it") == true }?.let { failedDomains -= it }
    }

    internal fun noteBlocked() {
        blockedCount++
    }

    fun onPageState(state: PageState) {
        if (state.auth != AuthState.UNKNOWN) auth = state.auth
        if (state.theme.isNotEmpty()) {
            val next = ComixColors().withSiteTheme(state.theme, state.isLightTone)
            if (next != colors) {
                colors = next
                prefs.edit {
                    putString(KEY_THEME, JSONObject(state.theme).toString())
                    putBoolean(KEY_THEME_LIGHT, state.isLightTone)
                }
            }
        }
    }

    private fun loadCachedTheme(): ComixColors {
        val raw = prefs.getString(KEY_THEME, null) ?: return ComixColors()
        return runCatching {
            val o = JSONObject(raw)
            val map = o.keys().asSequence().associateWith { o.getString(it) }
            ComixColors().withSiteTheme(map, prefs.getBoolean(KEY_THEME_LIGHT, false))
        }.getOrDefault(ComixColors())
    }

    companion object {
        /** Total size of the files under [dir]; symlinks are not followed. */
        internal fun dirSize(dir: File): Long =
            dir.walkTopDown().onEnter { !java.nio.file.Files.isSymbolicLink(it.toPath()) }
                .filter { it.isFile }.sumOf { it.length() }

        const val HOSTS_ASSET = "adblock/hosts.txt"
        private const val KEY_DOMAIN = "domain"
        private const val KEY_THEME = "theme_vars"
        private const val KEY_THEME_LIGHT = "theme_light"
        private const val KEY_ADBLOCK = "adblock"
    }
}
