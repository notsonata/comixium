package com.notsonata.comixium.offline

import android.app.Application
import android.graphics.BitmapFactory
import android.webkit.CookieManager
import android.webkit.WebSettings
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.notsonata.comixium.web.AdBlocker
import com.notsonata.comixium.web.Site
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * The download queue: one chapter at a time, first its page addresses ([PageCollector], needs the
 * app on screen), then its images ([PageFetcher], three at a time). Chapters left unfinished (the
 * app closed, a page failed) stay on disk as incomplete and are picked up again on the next launch.
 * Main thread, except the file and network work.
 */
class ChapterDownloads(private val app: Application, adBlocker: AdBlocker) {

    /** What the page asked to download, with the labels it could see. */
    data class Request(
        val link: ChapterLink,
        val domain: String,
        val seriesTitle: String = "",
        val label: String = "",
        val group: String = "",
        val coverUrl: String = "",
    )

    sealed interface State {
        data object Queued : State
        data class Preparing(val found: Int, val total: Int) : State
        data class Fetching(val done: Int, val total: Int) : State
        data class Failed(val why: String) : State
    }

    class Item(val request: Request) {
        var state by mutableStateOf<State>(State.Queued)
            internal set
    }

    val store = DownloadStore(File(app.filesDir, "downloads"))

    val collector = PageCollector(app, adBlocker, app.assets.open("inject/collect.js").bufferedReader().use { it.readText() })

    /** Chapters waiting, downloading or failed (finished ones are in [store]). */
    val queue = mutableStateListOf<Item>()

    /** Bumped whenever the store changes, so screens re-read it. */
    var version by mutableIntStateOf(0)
        private set

    /** The domain the app uses now, for chapters picked up again after a restart. */
    var domain: () -> String = { Site.DOMAINS[0] }

    /** Called after any change, so pages showing download buttons can be updated. */
    var onChange: (() -> Unit)? = null

    /** Finished chapters, kept in memory so page buttons never wait on the disk. */
    private val complete: MutableSet<ChapterLink> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private val foreground = MutableStateFlow(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var worker: Job? = null
    private var current: Item? = null
    private val userAgent by lazy { runCatching { WebSettings.getDefaultUserAgent(app) }.getOrDefault("Mozilla/5.0 (Linux; Android)") }

    init {
        // Unfinished chapters from last time go back in the queue.
        scope.launch {
            val all = withContext(Dispatchers.IO) { runCatching { store.allChapters() }.getOrDefault(emptyList()) }
            complete += all.filter { it.complete }.map { it.link }
            val unfinished = all.filter { !it.complete }
            unfinished.forEach { ch ->
                if (queue.none { it.request.link == ch.link }) {
                    queue += Item(Request(ch.link, domain(), store.seriesTitle(ch.link.seriesSlug).orEmpty(), ch.label, ch.group))
                }
            }
            changed()
            if (unfinished.isNotEmpty()) start()
        }
    }

    /** The app is on screen (true) or left it (false): page addresses can only be found while it's on. */
    fun setForeground(on: Boolean) {
        foreground.value = on
        if (!on) collector.interrupt()
    }

    fun enqueue(r: Request) {
        if (queue.any { it.request.link == r.link }) {
            queue.first { it.request.link == r.link }.takeIf { it.state is State.Failed }?.let { retry(it) }
            return
        }
        if (r.link in complete) return
        queue += Item(r)
        changed()
        start()
    }

    fun retry(item: Item) {
        item.state = State.Queued
        changed()
        start()
    }

    /** Stops and forgets a queued or running chapter, and removes what it saved. */
    fun cancel(item: Item) {
        queue.remove(item)
        if (current === item) {
            worker?.cancel()
            worker = null
            current = null
            collector.interrupt()
        }
        scope.launch {
            if (item.request.link !in complete) withContext(Dispatchers.IO) { runCatching { store.deleteChapter(item.request.link) } }
            changed()
            start()
        }
    }

    fun delete(link: ChapterLink) {
        itemFor(link)?.let { if (current === it) cancel(it) else queue.remove(it) }
        complete -= link
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { store.deleteChapter(link) } }
            changed()
        }
    }

    fun deleteSeries(slug: String) {
        queue.filter { it.request.link.seriesSlug == slug }.forEach { if (current === it) cancel(it) else queue.remove(it) }
        complete.removeAll { it.seriesSlug == slug }
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { store.deleteSeries(slug) } }
            changed()
        }
    }

    fun saveLastPage(link: ChapterLink, page: Int) {
        scope.launch(Dispatchers.IO) {
            runCatching { store.chapter(link)?.takeIf { it.lastPage != page }?.let { store.writeChapter(it.copy(lastPage = page)) } }
        }
    }

    /** Download states of a series' chapters for the page script: id -> done/queued/preparing/active:NN/failed. */
    fun pageStates(seriesSlug: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        complete.filter { it.seriesSlug == seriesSlug }.forEach { out[it.chapterId] = "done" }
        queue.filter { it.request.link.seriesSlug == seriesSlug }.forEach { item ->
            out[item.request.link.chapterId] = when (val s = item.state) {
                State.Queued -> "queued"
                is State.Preparing -> "preparing"
                is State.Fetching -> "active:" + (if (s.total > 0) 100 * s.done / s.total else 0)
                is State.Failed -> "failed"
            }
        }
        return out
    }

    fun itemFor(link: ChapterLink): Item? = queue.firstOrNull { it.request.link == link }

    fun isDownloaded(link: ChapterLink): Boolean = link in complete

    /** Everything downloaded is gone (from settings). */
    fun deleteAll() {
        queue.toList().forEach { if (current === it) cancel(it) else queue.remove(it) }
        complete.clear()
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { store.deleteAll() } }
            changed()
        }
    }

    private fun changed() {
        version++
        onChange?.invoke()
    }

    private fun start() {
        if (worker?.isActive == true) return
        worker = scope.launch {
            try {
                while (true) {
                    val item = queue.firstOrNull { it.state == State.Queued } ?: break
                    foreground.first { it }
                    current = item
                    process(item)
                    current = null
                }
            } finally {
                current = null
            }
            // Nothing left to prepare: free the hidden WebView after a while.
            delay(IDLE_RELEASE_MS)
            if (queue.none { it.state == State.Queued || it.state is State.Preparing }) collector.release()
        }
    }

    private suspend fun process(item: Item) {
        val r = item.request
        item.state = State.Preparing(0, 0)
        changed()
        val url = Site.url(r.domain, r.link.path)
        when (val result = collector.collect(url) { found, total -> item.state = State.Preparing(found, total); onChange?.invoke() }) {
            PageCollector.Result.NotNow -> {
                // Left the app mid-way: back in line, and wait a moment so a view that isn't up yet doesn't spin.
                item.state = State.Queued
                changed()
                delay(1000)
            }
            is PageCollector.Result.Failed -> {
                item.state = State.Failed(describe(result.why))
                changed()
            }
            is PageCollector.Result.Pages -> fetchAll(item, result)
        }
    }

    private suspend fun fetchAll(item: Item, pages: PageCollector.Result.Pages) {
        val r = item.request
        val link = r.link
        val (titleSeries, titleLabel) = splitTitle(pages.title)
        val seriesTitle = pages.seriesTitle.ifBlank { titleSeries }.ifBlank { r.seriesTitle }.ifBlank { link.seriesSlug }
        val label = titleLabel.ifBlank { r.label }.ifBlank { "Ch.${link.number}" }
        val coverUrl = pages.coverUrl.ifBlank { r.coverUrl }
        // The cover is on comix itself, behind its Cloudflare check: send the app's cookies.
        val coverCookies = coverUrl.takeIf { Site.isSiteUrl(it) }?.let { runCatching { CookieManager.getInstance().getCookie(it) }.getOrNull() }
        val total = pages.urls.size
        item.state = State.Fetching(0, total)
        changed()
        val ok = withContext(Dispatchers.IO) {
            runCatching {
                if (store.seriesTitle(link.seriesSlug).isNullOrBlank()) store.writeSeries(link.seriesSlug, seriesTitle)
                if (store.coverFile(link.seriesSlug) == null && coverUrl.isNotBlank()) {
                    fetchCover(coverUrl, coverCookies)?.let { store.writeCover(link.seriesSlug, it) }
                }
                val started = store.chapter(link)
                store.writeChapter(DownloadStore.Chapter(link, label, r.group, pages = total, complete = false, savedAt = System.currentTimeMillis()))
                val fetcher = PageFetcher(userAgent)
                val gate = Semaphore(PARALLEL)
                val done = AtomicInteger(0)
                coroutineScope {
                    pages.urls.mapIndexed { i, pageUrl ->
                        async {
                            gate.withPermit {
                                if (store.pageFile(link, i + 1) == null) fetcher.fetch(store, link, i + 1, pageUrl)
                                val n = done.incrementAndGet()
                                withContext(Dispatchers.Main) { item.state = State.Fetching(n, total); onChange?.invoke() }
                            }
                        }
                    }.awaitAll()
                }
                val files = store.pageFiles(link)
                check(files.size >= total) { "pages missing" }
                val sizes = files.take(total).map { f ->
                    val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(f.path, o)
                    o.outWidth.coerceAtLeast(0) to o.outHeight.coerceAtLeast(0)
                }
                store.writeChapter(
                    DownloadStore.Chapter(
                        link, label, r.group, pages = total, complete = true, bytes = files.sumOf { it.length() },
                        savedAt = System.currentTimeMillis(), sizes = sizes, lastPage = started?.lastPage ?: 0,
                    ),
                )
            }.onFailure { if (it is CancellationException) throw it }.isSuccess
        }
        if (ok) {
            complete += link
            queue.remove(item)
        } else {
            item.state = State.Failed("Some pages didn't download")
        }
        changed()
    }

    private fun fetchCover(url: String, cookies: String?): ByteArray? = runCatching {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.connectTimeout = 15_000
            conn.readTimeout = 20_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "image/*")
            if (cookies != null) conn.setRequestProperty("Cookie", cookies)
            if (conn.responseCode != 200 || conn.contentType?.startsWith("image/") != true) return null
            conn.inputStream.use { it.readBytes() }.takeIf { it.isNotEmpty() }
        } finally {
            conn.disconnect()
        }
    }.getOrNull()

    companion object {
        private const val PARALLEL = 3
        private const val IDLE_RELEASE_MS = 30_000L

        /** "Return of the Blossoming Blade · Ch.185" -> ("Return of the Blossoming Blade", "Ch.185"). */
        fun splitTitle(title: String): Pair<String, String> {
            val i = title.lastIndexOf(" · ")
            return if (i <= 0) title.trim() to "" else title.substring(0, i).trim() to title.substring(i + 3).trim()
        }

        fun describe(why: String): String = when (why) {
            "check" -> "comix asked for a check. Open the chapter in the app once, then retry"
            "noreader" -> "The chapter didn't open. Check the connection, then retry"
            "missing" -> "Some pages couldn't be found. Retry"
            "timeout" -> "Took too long. Retry"
            "crashed" -> "The page stopped. Retry"
            else -> "Didn't work ($why). Retry"
        }
    }
}
