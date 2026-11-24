package com.notsonata.comixium.web

sealed interface LinkAction {
    /** Let the WebView load it (site pages, iframes, captcha flows). */
    data object Internal : LinkAction
    /** Open in a Custom Tab (external sites the user tapped on). */
    data class External(val url: String) : LinkAction
    /** Hand to Android (mailto:, intent:, market: ...). */
    data class System(val url: String) : LinkAction
    /** Swallow (ads, popunders, redirects nobody tapped). */
    data object Block : LinkAction
}

object LinkPolicy {
    private val PASS_THROUGH_SCHEMES = setOf("about", "data", "blob", "javascript")

    fun decide(url: String, hasGesture: Boolean, isMainFrame: Boolean, adBlocker: AdBlocker): LinkAction {
        if (!isMainFrame) return LinkAction.Internal
        val scheme = Site.schemeOf(url) ?: return LinkAction.Block
        if (scheme in PASS_THROUGH_SCHEMES) return LinkAction.Internal
        if (scheme != "http" && scheme != "https") {
            return if (hasGesture) LinkAction.System(url) else LinkAction.Block
        }
        val host = Site.hostOf(url) ?: return LinkAction.Block
        if (Site.isSiteHost(host)) return LinkAction.Internal
        if (host == "cloudflare.com" || host.endsWith(".cloudflare.com")) return LinkAction.Internal
        if (adBlocker.enabled && adBlocker.isBlockedHost(host)) return LinkAction.Block
        if (!hasGesture) return LinkAction.Block
        return LinkAction.External(url)
    }
}
