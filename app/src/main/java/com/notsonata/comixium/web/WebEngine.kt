package com.notsonata.comixium.web

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.edit
import com.notsonata.comixium.offline.ChapterDownloads
import com.notsonata.comixium.offline.ChapterLink
import com.notsonata.comixium.offline.Connectivity
import com.notsonata.comixium.offline.DownloadPrefs
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

    /** Whether the phone is online, for downloads and for answering pages from them. */
    val connectivity = Connectivity(app).also {
        // Pages hide the site's "Http Error" messages while offline.
        it.onChange = { pushPagePrefs() }
    }

    val downloadPrefs = DownloadPrefs(prefs)

    /** Chapters saved for offline reading. */
    val downloads = ChapterDownloads(app, adBlocker, downloadPrefs, connectivity).also {
        it.onChange = { pushDownloadStates() }
        it.domain = { domain }
    }

    /** A downloaded (or downloading) chapter whose button was tapped: the UI offers what to do with it. */
    var downloadPrompt by mutableStateOf<ChapterLink?>(null)

    /** A series page's Download button was tapped: the UI offers what to download. */
    var seriesDownload by mutableStateOf<SeriesDownloadOffer?>(null)

    /** What a series page knows when its Download button is tapped. */
    data class SeriesDownloadOffer(
        val slug: String,
        val title: String,
        val cover: String,
        /** Scanlation groups, as the page's group filter lists them. */
        val groups: List<String>,
        /** Where the reader left off ("185"), from the page's Continue button; "" if not started. */
        val current: String,
    )

    fun onSeriesDownloadTap(o: SeriesDownloadOffer) {
        seriesDownload = o
    }

    /** Downloaded chapters read to the end, deleted once no page shows them (setting). */
    private val readToDelete = mutableSetOf<ChapterLink>()

    /** A reader page reached the end of its chapter. */
    fun onReadEnd(link: ChapterLink) {
        if (!downloads.isDownloaded(link)) return
        downloads.markRead(link)
        if (downloadPrefs.deleteAfterRead) readToDelete += link
    }

    /** A tab moved to another page: delete read chapters nothing shows any more. */
    fun onTabUrl() {
        if (readToDelete.isEmpty()) return
        val shown = tabs.values.filter { it.isCreated }.mapNotNull { ChapterLink.parse(Site.pathOnly(it.url)) }.toSet()
        readToDelete.filter { it !in shown }.forEach { link ->
            readToDelete -= link
            downloads.delete(link)
        }
    }

    /** A page's download button: start the chapter, or ask about one already saved or on its way. */
    fun onDownloadTap(link: ChapterLink, series: String, label: String, group: String, cover: String) {
        if (downloads.isDownloaded(link) || downloads.itemFor(link) != null) {
            downloadPrompt = link
            return
        }
        downloads.enqueue(ChapterDownloads.Request(link, domain, series, label, group, cover))
    }

    private val main = android.os.Handler(android.os.Looper.getMainLooper())
    private var downloadPushQueued = false

    /** Sends download states to pages with download buttons; changes are batched (progress is frequent). */
    fun pushDownloadStates() {
        if (downloadPushQueued) return
        downloadPushQueued = true
        main.postDelayed({
            downloadPushQueued = false
            tabs.values.filter { it.isCreated }.forEach { it.pushDownloadStates() }
        }, 150)
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
        "window.__comixium && (window.__comixium.setPrefs({${reader.pageScriptFields()},${appPrefs.pageScriptFields()}})," +
            " window.__comixium.setOffline && window.__comixium.setOffline(${!connectivity.online}))"

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

    private val tabs = linkedMapOf<String, WebTab>()

    /** Tabs live as long as the process, so switching tabs or recreating the activity keeps pages. */
    fun tab(key: String, startPath: String?, preferOffline: Boolean = false): WebTab =
        tabs.getOrPut(key) { WebTab(key, startPath, this, preferOffline) }

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
