package com.notsonata.comixium.offline

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Downloads one page image. The reader's image hosts serve pages without cookies or a referrer
 * (the site's own <img> tags use referrerpolicy=no-referrer), so a plain request does.
 */
class PageFetcher(private val userAgent: String) {

    class Fetched(val file: File, val bytes: Long)

    /** Saves [url] as page [index] of [link]; retries a few times. Throws IOException if it never works. */
    fun fetch(store: DownloadStore, link: ChapterLink, index: Int, url: String): Fetched {
        var last: IOException? = null
        repeat(ATTEMPTS) { attempt ->
            if (attempt > 0) Thread.sleep(BACKOFF_MS * attempt)
            try {
                return once(store, link, index, url)
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: IOException("couldn't download page $index")
    }

    private fun once(store: DownloadStore, link: ChapterLink, index: Int, url: String): Fetched {
        require(url.startsWith("https://") || url.startsWith("http://")) { "not a web address" }
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP $code")
            val type = conn.contentType
            if (type != null && !type.startsWith("image/")) throw IOException("not an image ($type)")
            val file = store.newPageFile(link, index, DownloadStore.extensionFor(type))
            var size = 0L
            DownloadStore.writeAtomically(file) { tmp ->
                conn.inputStream.use { input -> tmp.outputStream().use { size = input.copyTo(it) } }
            }
            if (size == 0L) {
                file.delete()
                throw IOException("empty image")
            }
            return Fetched(file, size)
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val ATTEMPTS = 3
        const val BACKOFF_MS = 1500L
    }
}
