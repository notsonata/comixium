package com.notsonata.comixium.offline

import com.notsonata.comixium.web.Site
import java.io.File

/**
 * Answers the app's WebViews from downloaded chapters, so the site's own reader (with the app's
 * controls and the saved comments) opens them without a connection:
 *  - page images the reader asks for: always from disk (their addresses don't change)
 *  - scripts, styles and fonts a saved page used: always from disk (their names change when the site does)
 *  - a downloaded chapter's page: from disk when offline, or always in the Downloaded screen's reader
 *  - its chapter data: from disk on a page that came from disk (it has to match that page's
 *    scripts), or when offline
 *  - its comments: from disk when offline (online, the live ones are newer)
 *  - the account's state for it (liked, where you left off): when offline, a blank one. Signed in,
 *    the site's reader waits for that answer before it tracks the page you're on, and the reading
 *    progress it keeps (queued in the page, sent once online) depends on that
 *
 * The lookup is plain Kotlin and unit-tested; [com.notsonata.comixium.web.WebTab] turns a [Served]
 * into the WebView's response. Lookups run on WebView threads, so the index is an immutable snapshot.
 */
class OfflineServer {

    /** What to send back: a file (or [bytes]), its type, extra headers, and whether it's a chapter page. */
    data class Served(
        val file: File?,
        val type: String,
        val headers: Map<String, String> = emptyMap(),
        val isPage: Boolean = false,
        val bytes: ByteArray? = null,
    ) {
        fun open(): java.io.InputStream? =
            bytes?.let { java.io.ByteArrayInputStream(it) } ?: file?.let { runCatching { java.io.FileInputStream(it) }.getOrNull() }
    }

    /** How the asking WebView is doing: [preferOffline] for the Downloaded reader, [replaying] if its page came from disk. */
    data class Mode(val online: Boolean, val preferOffline: Boolean = false, val replaying: Boolean = false)

    class Index(
        /** Chapter path -> saved page. */
        val pages: Map<String, File> = emptyMap(),
        /** Chapter id -> saved chapter data. */
        val chapters: Map<String, DownloadStore.SavedResponse> = emptyMap(),
        /** Other saved answers (comments), by [answerKey]. */
        val answers: Map<String, DownloadStore.SavedResponse> = emptyMap(),
        /** Image path ("/hi/...") -> page file. */
        val images: Map<String, File> = emptyMap(),
        /** Shared files by [assetKey]. */
        val assets: Map<String, DownloadStore.Asset> = emptyMap(),
    )

    @Volatile var index: Index = Index()
        private set

    /** Re-reads what's on disk (IO thread). */
    fun rebuild(store: DownloadStore) {
        val pages = HashMap<String, File>()
        val chapters = HashMap<String, DownloadStore.SavedResponse>()
        val answers = HashMap<String, DownloadStore.SavedResponse>()
        val images = HashMap<String, File>()
        for (ch in store.allChapters()) {
            if (!ch.complete) continue
            val files = store.pageFiles(ch.link)
            store.images(ch.link).forEachIndexed { i, path -> files.getOrNull(i)?.let { images[path] = it } }
            if (!ch.recorded) continue
            store.pageHtml(ch.link)?.let { pages[ch.link.path] = it }
            for (r in store.responses(ch.link)) {
                val id = chapterIdOfApi(r.url)
                if (id != null) chapters[id] = r else answerKey(r.url)?.let { answers[it] = r }
            }
        }
        val assets = store.assets().associateBy { assetKey(it.url) }
        index = Index(pages, chapters, answers, images, assets)
    }

    fun lookup(url: String, method: String, isMainFrame: Boolean, mode: Mode): Served? {
        if (!method.equals("GET", ignoreCase = true)) return null
        val ix = index
        val path = Site.pathOnly(url)
        if (path.startsWith("/hi/")) {
            return ix.images[path]?.let { Served(it, imageType(it.name), CORS) }
        }
        val site = Site.isSiteUrl(url)
        if (isMainFrame) {
            if (!site || !(mode.preferOffline || !mode.online)) return null
            val page = ChapterLink.parse(path)?.let { ix.pages[it.path] } ?: return null
            return Served(page, "text/html; charset=utf-8", mapOf("Cache-Control" to "no-store"), isPage = true)
        }
        ix.assets[assetKey(url)]?.let { return Served(it.file, it.type.ifBlank { "application/octet-stream" }, CORS) }
        if (!site) return null
        chapterIdOfApi(url)?.let { id ->
            if (!(mode.replaying || !mode.online)) return null
            val r = ix.chapters[id] ?: return null
            return Served(r.file, r.type.ifBlank { "application/json" }, CORS + listOfNotNull(r.enc.takeIf { it.isNotEmpty() }?.let { "x-enc" to it }))
        }
        if (!mode.online) {
            answerKey(url)?.let { key -> ix.answers[key]?.let { return Served(it.file, it.type.ifBlank { "application/json" }, CORS) } }
            chapterIdOfState(url)?.takeIf { it in ix.chapters }?.let { return Served(null, "application/json", CORS, bytes = BLANK_STATE) }
        }
        return null
    }

    /** Whether chapter [id]'s data is saved (it can be read offline). */
    fun hasChapter(id: String): Boolean = id in index.chapters

    companion object {
        private val CORS = mapOf("Access-Control-Allow-Origin" to "*")
        private val CHAPTER_API = Regex("^/api/v1/chapters/(\\d+)/?$")
        private val CHAPTER_STATE = Regex("^/api/v1/user/chapters/(\\d+)/state/?$")
        /** The account's state for a chapter, as the site sends it, with nothing set. */
        private val BLANK_STATE = "{\"status\":\"ok\",\"result\":{\"liked\":false}}".toByteArray()
        /** Query parameters that change on every request (anti-cache tokens), left out of [answerKey]. */
        private val VOLATILE = setOf("_", "v", "w")

        fun chapterIdOfApi(url: String): String? =
            if (Site.isSiteUrl(url)) CHAPTER_API.matchEntire(Site.pathOnly(url))?.groupValues?.get(1) else null

        /** The chapter whose account state (liked, last page) [url] asks for. */
        fun chapterIdOfState(url: String): String? =
            if (Site.isSiteUrl(url)) CHAPTER_STATE.matchEntire(Site.pathOnly(url))?.groupValues?.get(1) else null

        /**
         * Saved answers other than chapter data: the comment threads. Key = path + sorted stable query,
         * with a site address in the query (the chapter's page_url) reduced to its path, so comments
         * saved on one domain are found when the chapter is read on the other.
         */
        fun answerKey(url: String): String? {
            if (!Site.isSiteUrl(url)) return null
            val path = Site.pathOnly(url)
            if (!path.startsWith("/api/v1/threads/")) return null
            val query = url.substringAfter('?', "").substringBefore('#')
            val params = query.split('&').filter { it.isNotEmpty() && it.substringBefore('=') !in VOLATILE }.map { p ->
                val value = runCatching { java.net.URLDecoder.decode(p.substringAfter('=', ""), "UTF-8") }.getOrNull()
                if (value != null && Site.isSiteUrl(value)) p.substringBefore('=') + "=" + Site.pathOf(value) else p
            }.sorted()
            return if (params.isEmpty()) path else path + "?" + params.joinToString("&")
        }

        /** Site files are the same on every domain: key them by path. Others (fonts) by the full address. */
        fun assetKey(url: String): String =
            if (Site.isSiteUrl(url)) Site.pathOf(url).substringBefore('#') else url.substringBefore('#')

        fun imageType(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
            "webp" -> "image/webp"
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "avif" -> "image/avif"
            else -> "application/octet-stream"
        }
    }
}
