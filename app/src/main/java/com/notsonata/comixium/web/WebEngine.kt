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
import com.notsonata.comixium.ui.theme.ComixColors
import com.notsonata.comixium.ui.theme.withSiteTheme
import org.json.JSONObject

/**
 * App-wide web state: which domain we're on, the site's current theme and login state, the ad
 * blocker, and the injected page script. Lives as long as the process so tabs (and their pages)
 * survive activity recreation.
 */
class WebEngine(private val app: Application) {

    private val prefs = app.getSharedPreferences("comixium", Context.MODE_PRIVATE)

    val adBlocker = AdBlocker { runCatching { app.assets.open("adblock/hosts.gz") }.getOrNull() }.apply {
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

    fun clearCache() {
        tabs.values.firstOrNull { it.isCreated }?.webView?.clearCache(true)
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
        private const val KEY_DOMAIN = "domain"
        private const val KEY_THEME = "theme_vars"
        private const val KEY_THEME_LIGHT = "theme_light"
        private const val KEY_ADBLOCK = "adblock"
    }
}
