package com.notsonata.comixium.web

import java.io.BufferedInputStream
import java.io.InputStream
import java.util.zip.GZIPInputStream
import kotlin.concurrent.thread

/**
 * Network-level blocker for third-party ad and tracker hosts.
 *
 * The domain list is StevenBlack's unified hosts file (MIT), bundled as `assets/adblock/hosts.txt`
 * (one domain per line; the APK compresses it). Gzipped input is accepted too.
 * comix's own ads are first-party (a popunder link and promo banners built by the site's bundle),
 * so those are handled by the injected CSS/JS instead; this class covers the third-party side.
 *
 * Image requests are never blocked: chapter pages come from rotating, random-looking CDN domains,
 * and a false positive there would silently break reading.
 */
class AdBlocker(private val source: () -> InputStream?) {

    @Volatile private var domains: Set<String> = EXTRA_BLOCKED
    @Volatile var enabled: Boolean = true
    @Volatile var loaded: Boolean = false
        private set

    fun loadAsync() {
        thread(name = "adblock-load", isDaemon = true) { load() }
    }

    fun load() {
        val set = HashSet<String>(100_000)
        set += EXTRA_BLOCKED
        runCatching {
            source()?.let { raw ->
                val input = BufferedInputStream(raw)
                input.mark(2)
                val gzipped = input.read() == 0x1f && input.read() == 0x8b
                input.reset()
                (if (gzipped) GZIPInputStream(input) else input).bufferedReader().useLines { lines ->
                    for (line in lines) {
                        val d = line.trim()
                        if (d.isNotEmpty() && !d.startsWith("#")) set += d.lowercase()
                    }
                }
            }
        }
        domains = set
        loaded = true
    }

    val size: Int get() = domains.size

    fun isBlockedHost(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        var h = host.lowercase().trimEnd('.')
        val set = domains
        while (true) {
            if (h in set) return true
            val dot = h.indexOf('.')
            if (dot < 0 || dot == h.length - 1) return false
            h = h.substring(dot + 1)
        }
    }

    /** Decide whether a sub-resource request from the site should be cancelled. */
    fun shouldBlock(url: String, accept: String?, isMainFrame: Boolean): Boolean {
        if (!enabled) return false
        val scheme = Site.schemeOf(url)
        if (scheme != "http" && scheme != "https") return false
        val host = Site.hostOf(url) ?: return false
        if (Site.isSiteHost(host)) return false
        if (ALWAYS_ALLOWED.any { host == it || host.endsWith(".$it") }) return false
        if (!isMainFrame && accept != null && accept.startsWith("image/")) return false
        return isBlockedHost(host)
    }

    companion object {
        /** Hosts seen serving comix's popunder or tracking that the public list misses. */
        val EXTRA_BLOCKED: Set<String> = setOf(
            "araplhn.org",
            "whos.amung.us",
            "sharethis.com",
            "static.cloudflareinsights.com",
            "gotaku.to",
        )

        /** Needed for the site to work (login captcha, fonts). Never block these. */
        val ALWAYS_ALLOWED: Set<String> = setOf(
            "challenges.cloudflare.com",
            "fonts.googleapis.com",
            "fonts.gstatic.com",
        )
    }
}
