package com.notsonata.comixium.offline

import android.app.Application
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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

/**
 * The download queue. Each chapter is first prepared by [PageCollector] (needs the app on screen):
 * its page addresses, plus the page, files and answers the site's reader needs offline. Its images
 * then download in the background ([PageFetcher], three at a time across chapters) while the next
 * chapter is prepared. Series downloads ([SeriesWalk]) add their chapters one at a time.
 * Unfinished work stays on disk and is picked up again on the next launch.
 * Main thread, except the file and network work.
 */
class ChapterDownloads(
    private val app: Application,
    adBlocker: AdBlocker,
    val prefs: DownloadPrefs,
    private val net: Connectivity,
) {

    /** What to download, with the labels the page could see. */
    data class Request(
        val link: ChapterLink,
        val domain: String,
        val seriesTitle: String = "",
        val label: String = "",
        val group: String = "",
        val coverUrl: String = "",
        /** The series download that added it, if any. */
        val walkId: String? = null,
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

    /** Serves downloaded chapters to the app's WebViews. */
    val server = OfflineServer()

    val collector = PageCollector(app, adBlocker, app.assets.open("inject/collect.js").bufferedReader().use { it.readText() })

    /** Chapters waiting, being prepared, downloading or failed (finished ones are in [store]). */
    val queue = mutableStateListOf<Item>()

    /** Series downloads still going (or stopped by a problem). */
    val walks = mutableStateListOf<SeriesWalk>()

    /** Bumped whenever the store changes, so screens re-read it. */
    var version by mutableIntStateOf(0)
        private set

    /** Why nothing is moving right now ("Waiting for Wi-Fi"), or null. */
    var waitingFor by mutableStateOf<String?>(null)
        private set

    /** The domain the app uses now, for chapters picked up again after a restart. */
    var domain: () -> String = { Site.DOMAINS[0] }

    /** Called after any change, so pages showing download buttons can be updated. */
    var onChange: (() -> Unit)? = null

    /** Finished chapters, kept in memory so page buttons never wait on the disk. */
    private val complete: MutableSet<ChapterLink> = java.util.concurrent.ConcurrentHashMap.newKeySet()
    /** Shared files already saved, so a page doesn't send them again. */
    private val knownAssets: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private val foreground = MutableStateFlow(false)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Wakes the worker: something was added, retried or allowed again. */
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private var current: Item? = null
    private var currentJob: Job? = null
    private val fetches = HashMap<ChapterLink, Job>()
    private val pageGate = Semaphore(PARALLEL)
    private val userAgent by lazy { runCatching { WebSettings.getDefaultUserAgent(app) }.getOrDefault("Mozilla/5.0 (Linux; Android)") }

    init {
        scope.launch {
            val (all, saved) = withContext(Dispatchers.IO) {
                runCatching { store.allChapters() }.getOrDefault(emptyList()) to runCatching { store.walks() }.getOrDefault(emptyList())
            }
            complete += all.filter { it.complete }.map { it.link }
            withContext(Dispatchers.IO) {
                runCatching { knownAssets += store.assets().map { it.url } }
                runCatching { server.rebuild(store) }
            }
            // Unfinished chapters go back in the queue, and so do chapters saved before the page was
            // (the first 1.9.0 build): preparing them again adds what the site's reader needs, keeping the images.
            all.filter { !it.complete || !it.recorded }.forEach { ch ->
                if (queue.none { it.request.link == ch.link }) {
                    queue += Item(Request(ch.link, domain(), store.seriesTitle(ch.link.seriesSlug).orEmpty(), ch.label, ch.group))
                }
            }
            saved.mapNotNull(SeriesWalk::fromMap).filter { !it.isDone }.forEach { w ->
                if (walks.any { it.id == w.id }) return@forEach
                walks += w
                // The chapter it was waiting for goes back in line as part of it (or is queued again
                // if the app closed before it was saved at all).
                val link = (w.step as? SeriesWalk.Step.Wait)?.link ?: return@forEach
                if (link in complete && all.firstOrNull { it.link == link }?.recorded == true) return@forEach
                val i = queue.indexOfFirst { it.request.link == link }
                val r = Request(link, domain(), w.seriesTitle, "Ch.${link.number}", w.lastGroup, w.coverUrl, walkId = w.id)
                if (i >= 0) queue[i] = Item(queue[i].request.copy(walkId = w.id)) else queue += Item(r)
            }
            changed()
            scope.launch { work() }
        }
        scope.launch {
            net.net.collect { wake.trySend(Unit) }
        }
    }

    /** The app is on screen (true) or left it (false): chapters can only be prepared while it's on. */
    fun setForeground(on: Boolean) {
        foreground.value = on
        if (!on) collector.interrupt() else wake.trySend(Unit)
    }

    fun enqueue(r: Request) {
        itemFor(r.link)?.let { if (it.state is State.Failed) retry(it); return }
        if (r.link in complete && store.chapter(r.link)?.recorded != false) return
        queue += Item(r)
        changed()
    }

    fun retry(item: Item) {
        item.state = State.Queued
        changed()
    }

    /** Stops and forgets a queued or running chapter, and removes what it saved (unless it was already complete). */
    fun cancel(item: Item) {
        queue.remove(item)
        val link = item.request.link
        if (current === item) {
            currentJob?.cancel()
            collector.interrupt()
        }
        fetches.remove(link)?.cancel()
        item.request.walkId?.let { id -> updateWalk(id) { w -> if (w.step == SeriesWalk.Step.Wait(link)) w.copy(step = SeriesWalk.Step.Peek(link.path)) else w } }
        scope.launch {
            if (link !in complete) withContext(Dispatchers.IO) { runCatching { store.deleteChapter(link) } }
            changed()
        }
    }

    fun delete(link: ChapterLink) {
        itemFor(link)?.let(::cancel)
        complete -= link
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { store.deleteChapter(link); store.pruneAssets() } }
            storeChanged()
        }
    }

    fun deleteSeries(slug: String) {
        queue.filter { it.request.link.seriesSlug == slug }.forEach { cancel(it) }
        walks.filter { it.seriesSlug == slug }.forEach { cancelWalk(it) }
        complete.removeAll { it.seriesSlug == slug }
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { store.deleteSeries(slug); store.pruneAssets() } }
            storeChanged()
        }
    }

    /** Everything downloaded is gone. */
    fun deleteAll() {
        queue.toList().forEach { cancel(it) }
        walks.toList().forEach { cancelWalk(it) }
        complete.clear()
        knownAssets.clear()
        scope.launch {
            withContext(Dispatchers.IO) { runCatching { store.deleteAll() } }
            storeChanged()
        }
    }

    /** The chapter was read to the end. */
    fun markRead(link: ChapterLink) {
        if (link !in complete) return
        scope.launch(Dispatchers.IO) {
            runCatching { store.chapter(link)?.takeIf { !it.read }?.let { store.writeChapter(it.copy(read = true)) } }
            withContext(Dispatchers.Main) { changed() }
        }
    }

    // --- Series downloads ---------------------------------------------------------------------

    /**
     * Downloads chapters of a series one number at a time: from [startNumber] (or the first
     * chapter), [limit] chapters (0 = up to the latest), each from the first of [groups] that has it.
     */
    fun startWalk(slug: String, title: String, coverUrl: String, groups: List<String>, startNumber: String?, limit: Int) {
        val w = SeriesWalk(
            id = "w" + System.currentTimeMillis(),
            seriesSlug = slug, seriesTitle = title, coverUrl = coverUrl,
            groups = groups, limit = limit.coerceAtLeast(0),
            step = startNumber?.takeIf { it.isNotBlank() }?.let { SeriesWalk.Step.Find(it) } ?: SeriesWalk.Step.First,
        )
        walks += w
        saveWalk(w)
        changed()
    }

    fun retryWalk(w: SeriesWalk) = updateWalk(w.id) { it.copy(problem = "") }

    /** Stops a series download; chapters it already queued carry on. */
    fun cancelWalk(w: SeriesWalk) {
        walks.removeAll { it.id == w.id }
        scope.launch(Dispatchers.IO) { runCatching { store.deleteWalk(w.id) } }
        changed()
    }

    private fun updateWalk(id: String, f: (SeriesWalk) -> SeriesWalk) {
        val i = walks.indexOfFirst { it.id == id }
        if (i < 0) return
        val w = f(walks[i])
        if (w.isDone && w.problem.isEmpty()) {
            walks.removeAt(i)
            scope.launch(Dispatchers.IO) { runCatching { store.deleteWalk(id) } }
        } else {
            walks[i] = w
            saveWalk(w)
        }
        changed()
    }

    private fun saveWalk(w: SeriesWalk) {
        val m = w.toMap()
        scope.launch(Dispatchers.IO) { runCatching { store.writeWalk(w.id, m) } }
    }

    // --- For pages and screens ----------------------------------------------------------------

    /** Download states of a series' chapters for the page script: id -> done/queued/preparing/active:NN/failed. */
    fun pageStates(seriesSlug: String): Map<String, String> {
        val out = linkedMapOf<String, String>()
        complete.filter { it.seriesSlug == seriesSlug }.forEach { out[it.chapterId] = "done" }
        queue.filter { it.request.link.seriesSlug == seriesSlug }.forEach { item ->
            if (item.request.link in complete) return@forEach
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

    private fun changed() {
        version++
        onChange?.invoke()
        wake.trySend(Unit)
    }

    /** Files on disk changed: re-read them for the WebViews, then tell the screens. */
    private suspend fun storeChanged() {
        withContext(Dispatchers.IO) { runCatching { server.rebuild(store) } }
        changed()
    }

    // --- The worker -----------------------------------------------------------------------------

    private suspend fun work() {
        while (true) {
            val next: Any? = queue.firstOrNull { it.state == State.Queued }
                ?: walks.firstOrNull { it.problem.isEmpty() && it.step !is SeriesWalk.Step.Wait && !it.isDone }
            if (next == null) {
                waitingFor = null
                // Nothing to prepare: free the hidden WebView after a while, unless work turns up.
                if (withTimeoutOrNull(IDLE_RELEASE_MS) { wake.receive() } == null) {
                    collector.release()
                    wake.receive()
                }
                continue
            }
            if (!ready()) {
                wake.receive()
                continue
            }
            waitingFor = null
            val job = scope.launch {
                when (next) {
                    is Item -> { current = next; prepare(next) }
                    is SeriesWalk -> step(next)
                }
            }
            currentJob = job
            job.join()
            current = null
            currentJob = null
        }
    }

    /** Online, allowed to use this connection, and on screen; otherwise says what it's waiting for. */
    private fun ready(): Boolean {
        val n = net.net.value
        waitingFor = when {
            !n.online -> "Waiting for a connection"
            prefs.wifiOnly && !n.unmetered -> "Waiting for Wi-Fi"
            !foreground.value -> "Paused while Comixium is in the background"
            else -> null
        }
        return waitingFor == null
    }

    private suspend fun prepare(item: Item) {
        val r = item.request
        item.state = State.Preparing(0, 0)
        changed()
        val url = Site.url(r.domain, r.link.path)
        val job = PageCollector.Job("chapter", comments = prefs.comments, have = knownAssets.toList())
        val result = collector.collect(url, job) { found, total -> item.state = State.Preparing(found, total); onChange?.invoke() }
        when (result) {
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
            is PageCollector.Result.Pages -> {
                val saved = runCatching { saveRecording(item, result) }.onFailure { if (it is CancellationException) throw it }.isSuccess
                if (!saved) {
                    item.state = State.Failed("Couldn't save the chapter. Check the free space, then retry")
                    changed()
                    return
                }
                r.walkId?.let { id ->
                    updateWalk(id) { w ->
                        if (w.step != SeriesWalk.Step.Wait(r.link)) w
                        else w.copy(seen = w.seen + result.number.ifBlank { r.link.number }, lastGroup = result.group.ifBlank { w.lastGroup }).advance(result.next)
                    }
                }
                item.state = State.Fetching(0, result.urls.size)
                changed()
                fetches[r.link] = scope.launch { fetchImages(item, result) }
            }
            else -> {
                item.state = State.Failed(describe("unknown"))
                changed()
            }
        }
    }

    private suspend fun saveRecording(item: Item, pages: PageCollector.Result.Pages) {
        val r = item.request
        val link = r.link
        val (titleSeries, titleLabel) = splitTitle(pages.title)
        val seriesTitle = pages.seriesTitle.ifBlank { titleSeries }.ifBlank { r.seriesTitle }.ifBlank { link.seriesSlug }
        val label = titleLabel.ifBlank { r.label }.ifBlank { "Ch.${link.number}" }
        val coverUrl = pages.coverUrl.ifBlank { r.coverUrl }
        // The cover is on comix itself, behind its Cloudflare check: send the app's cookies.
        val coverCookies = coverUrl.takeIf { Site.isSiteUrl(it) }?.let { runCatching { CookieManager.getInstance().getCookie(it) }.getOrNull() }
        val rec = pages.recording
        withContext(Dispatchers.IO) {
            if (store.seriesTitle(link.seriesSlug).isNullOrBlank()) store.writeSeries(link.seriesSlug, seriesTitle)
            if (store.coverFile(link.seriesSlug) == null && coverUrl.isNotBlank()) {
                fetchCover(coverUrl, coverCookies)?.let { store.writeCover(link.seriesSlug, it) }
            }
            rec.newAssets.forEach { (url, typed) -> if (!store.hasAsset(url)) store.writeAsset(url, typed.first, typed.second) }
            val images = pages.urls.map { Site.pathOnly(it) }
            // A chapter saved before with a different page count (e.g. another image quality): start its images over.
            val before = store.chapter(link)
            if (before != null && before.pages != pages.urls.size) store.pageFiles(link).forEach { it.delete() }
            store.writeRecording(link, rec.html, rec.answers, images, rec.assets)
            store.writeChapter(
                DownloadStore.Chapter(
                    link, label, pages.group.ifBlank { r.group }, pages = pages.urls.size, complete = false,
                    savedAt = System.currentTimeMillis(), recorded = true, next = pages.next, read = before?.read ?: false,
                ),
            )
        }
        knownAssets += rec.newAssets.keys
    }

    private suspend fun fetchImages(item: Item, pages: PageCollector.Result.Pages) {
        val link = item.request.link
        val total = pages.urls.size
        try {
            // Wi-Fi only: images wait for an unmetered connection too.
            net.net.first { it.online && (!prefs.wifiOnly || it.unmetered) }
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    val fetcher = PageFetcher(userAgent)
                    val done = AtomicInteger(0)
                    coroutineScope {
                        pages.urls.mapIndexed { i, pageUrl ->
                            async {
                                pageGate.withPermit {
                                    if (store.pageFile(link, i + 1) == null) fetcher.fetch(store, link, i + 1, pageUrl)
                                    val n = done.incrementAndGet()
                                    withContext(Dispatchers.Main) { item.state = State.Fetching(n, total); onChange?.invoke() }
                                }
                            }
                        }.awaitAll()
                    }
                    val files = store.pageFiles(link)
                    check(files.size >= total) { "pages missing" }
                    files.drop(total).forEach { it.delete() }
                    val ch = store.chapter(link) ?: error("chapter gone")
                    store.writeChapter(ch.copy(pages = total, complete = true, bytes = files.take(total).sumOf { it.length() }, savedAt = System.currentTimeMillis()))
                    server.rebuild(store)
                }.onFailure { if (it is CancellationException) throw it }.isSuccess
            }
            if (ok) {
                complete += link
                queue.remove(item)
            } else {
                item.state = State.Failed("Some pages didn't download. Retry")
            }
        } finally {
            fetches.remove(link)
        }
        changed()
    }

    private suspend fun step(w: SeriesWalk) {
        val seriesUrl = Site.url(domain(), "/title/${w.seriesSlug}")
        when (val s = w.step) {
            SeriesWalk.Step.First -> when (val r = collector.collect(seriesUrl, PageCollector.Job("first"))) {
                is PageCollector.Result.Rows -> {
                    val n = SeriesWalk.firstNumber(r.rows)
                    if (n == null) updateWalk(w.id) { it.copy(step = SeriesWalk.Step.Done, problem = "No chapters found") }
                    else choose(w, r.rows, n)
                }
                else -> stepFailed(w, r)
            }
            is SeriesWalk.Step.Find -> when (val r = collector.collect(seriesUrl, PageCollector.Job("find", number = s.number))) {
                is PageCollector.Result.Rows -> choose(w, r.rows, s.number)
                else -> stepFailed(w, r)
            }
            is SeriesWalk.Step.Peek -> when (val r = collector.collect(Site.url(domain(), s.path), PageCollector.Job("peek"))) {
                is PageCollector.Result.Info -> updateWalk(w.id) { it.copy(seen = it.seen + r.number).advance(r.next) }
                else -> stepFailed(w, r)
            }
            is SeriesWalk.Step.Wait, SeriesWalk.Step.Done -> Unit
        }
    }

    private suspend fun stepFailed(w: SeriesWalk, r: PageCollector.Result) {
        // Left the app mid-way: try again once it's back (wait a moment so a view that isn't up yet doesn't spin).
        if (r == PageCollector.Result.NotNow) { delay(1000); return }
        val why = (r as? PageCollector.Result.Failed)?.why ?: "unknown"
        updateWalk(w.id) { it.copy(problem = describe(why)) }
    }

    private fun choose(w: SeriesWalk, rows: List<SeriesWalk.Row>, number: String) {
        when (val c = w.choose(rows, number)) {
            SeriesWalk.Choice.Missing -> updateWalk(w.id) { it.copy(step = SeriesWalk.Step.Done, problem = if (it.saved == 0) "Ch.$number isn't listed" else "") }
            is SeriesWalk.Choice.Skip -> updateWalk(w.id) { it.copy(step = SeriesWalk.Step.Peek(c.row.path)) }
            is SeriesWalk.Choice.Save -> {
                val link = ChapterLink.parse(c.row.path) ?: return updateWalk(w.id) { it.copy(problem = describe("unknown")) }
                // Already downloaded (or on its way) in some version: don't fetch the number twice.
                val have = runCatching { store.chapters(w.seriesSlug) }.getOrDefault(emptyList())
                    .firstOrNull { it.recorded && SeriesWalk.sameNumber(it.link.number, number) }
                val queued = queue.firstOrNull { it.request.link.seriesSlug == w.seriesSlug && SeriesWalk.sameNumber(it.request.link.number, number) }
                when {
                    have != null && have.next.isNotBlank() -> updateWalk(w.id) { it.copy(seen = it.seen + number).advance(have.next) }
                    have != null || queued != null -> updateWalk(w.id) { it.copy(step = SeriesWalk.Step.Peek((have?.link ?: queued!!.request.link).path)) }
                    else -> {
                        queue += Item(Request(link, domain(), w.seriesTitle, "Ch.$number", c.row.group, w.coverUrl, walkId = w.id))
                        updateWalk(w.id) { it.copy(saved = it.saved + 1, lastGroup = c.row.group, step = SeriesWalk.Step.Wait(link)) }
                    }
                }
            }
        }
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
            "noreader" -> "The page didn't open. Check the connection, then retry"
            "missing" -> "Some pages couldn't be found. Retry"
            "nopage", "nodata" -> "The chapter page couldn't be saved. Retry"
            "nosearch" -> "The series page changed. Retry later"
            "timeout" -> "Took too long. Retry"
            "crashed" -> "The page stopped. Retry"
            else -> "Didn't work ($why). Retry"
        }
    }
}
