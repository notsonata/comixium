package com.notsonata.comixium.web

/** Everything the app knows about the site's domains and URL structure. Pure Kotlin, unit-tested. */
object Site {
    /** First entry is the default. The site itself points users at comix.ws when comix.to is down. */
    val DOMAINS = listOf("comix.ws", "comix.to")

    val ORIGIN_RULES: Set<String> = DOMAINS.flatMap { listOf("https://$it", "https://*.$it") }.toSet()

    private val URL_PARTS = Regex("^([a-zA-Z][a-zA-Z0-9+.-]*):(?://(?:[^/?#@]*@)?([^/?#:]*)(?::\\d+)?)?([^?#]*)(\\?[^#]*)?(#.*)?$")

    fun schemeOf(url: String?): String? = url?.let { URL_PARTS.find(it.trim())?.groupValues?.get(1)?.lowercase() }

    fun hostOf(url: String?): String? =
        url?.let { URL_PARTS.find(it.trim())?.groupValues?.get(2)?.lowercase()?.ifEmpty { null } }

    fun isSiteHost(host: String?): Boolean {
        if (host.isNullOrEmpty()) return false
        val h = host.lowercase()
        return DOMAINS.any { h == it || h.endsWith(".$it") }
    }

    fun isSiteUrl(url: String?): Boolean = isSiteHost(hostOf(url))

    private val READER_PATH = Regex("^/title/[^/]+/\\d+-chapter-[^/?#]+/?$")

    fun isReaderPath(path: String?): Boolean = path != null && READER_PATH.matches(path)

    fun isReaderUrl(url: String?): Boolean = isSiteUrl(url) && isReaderPath(pathOnly(url))

    fun url(domain: String, path: String): String = "https://$domain" + (if (path.startsWith("/")) path else "/$path")

    /** Same path on another domain (for falling back from comix.ws to comix.to and back). */
    fun swapDomain(url: String, domain: String): String = url(domain, pathOf(url))

    /** Path only, without query or fragment. */
    fun pathOnly(url: String?): String {
        val m = url?.let { URL_PARTS.find(it.trim()) } ?: return "/"
        return m.groupValues[3].ifEmpty { "/" }
    }

    /** The path+query+fragment part of a site URL, or "/" for anything else. */
    fun pathOf(url: String?): String {
        if (!isSiteUrl(url)) return "/"
        val m = URL_PARTS.find(url!!.trim()) ?: return "/"
        return m.groupValues[3].ifEmpty { "/" } + m.groupValues[4] + m.groupValues[5]
    }
}
