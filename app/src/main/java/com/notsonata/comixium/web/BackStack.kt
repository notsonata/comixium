package com.notsonata.comixium.web

/**
 * Native-style Back over a WebView's history.
 *
 * A browser steps back one entry at a time, which feels wrong in an app: after
 * Home → series → chapter → (tap the series title) → series, Back should return to Home, not into
 * the chapter again. So Back works on pages rather than entries:
 *  - all chapters of one series count as a single "reader" page (Back leaves the reader instead
 *    of walking through every chapter read);
 *  - if the current page already appears earlier in the history (the user went "up" to it), Back
 *    continues from its first appearance, as if the later visit had replaced the stack above it.
 */
object BackStack {

    /** Identity of the page a URL shows, for comparing history entries. */
    fun pageKey(url: String?): String {
        if (url.isNullOrEmpty()) return ""
        if (!Site.isSiteUrl(url)) return url
        val path = Site.pathOnly(url)
        if (Site.isReaderPath(path)) return "reader:" + path.split('/').getOrElse(2) { "" }
        val p = Site.pathOf(url).substringBefore('#')
        val pathPart = p.substringBefore('?').trimEnd('/').ifEmpty { "/" }.let { if (it == "/home") "/" else it }
        val query = p.substringAfter('?', "")
        return if (query.isEmpty()) pathPart else "$pathPart?$query"
    }

    /**
     * Index of the history entry Back should go to, or -1 if Back should leave this tab.
     * [urls] is the WebView's back/forward list, [current] the current index in it.
     */
    fun target(urls: List<String?>, current: Int): Int {
        if (current !in urls.indices) return -1
        val keys = urls.map(::pageKey)
        val key = keys[current]
        val first = (0..current).first { keys[it] == key }
        var t = first - 1
        // Never land on a blank or duplicate entry (e.g. a redirect that left two identical entries).
        while (t >= 0 && (keys[t].isEmpty() || keys[t] == key)) t--
        return t
    }
}
