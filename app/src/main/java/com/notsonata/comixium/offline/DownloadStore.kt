package com.notsonata.comixium.offline

import java.io.File
import java.util.Properties

/** A chapter link on comix: `/title/<series slug>/<chapter id>-chapter-<number>`. */
data class ChapterLink(val seriesSlug: String, val chapterId: String, val number: String) {
    val path: String get() = "/title/$seriesSlug/$chapterId-chapter-$number"
    val seriesPath: String get() = "/title/$seriesSlug"

    /** For ordering: "189.2" sorts after "189"; anything unparsable goes last. */
    val sortKey: Double get() = number.toDoubleOrNull() ?: Double.NEGATIVE_INFINITY

    companion object {
        private val PATH = Regex("^/title/([A-Za-z0-9][A-Za-z0-9._-]*)/(\\d+)-chapter-([A-Za-z0-9][A-Za-z0-9._-]*)/?$")

        private val SERIES = Regex("^/title/([A-Za-z0-9][A-Za-z0-9._-]*)(/|$)")

        /** The series slug of a series or chapter path. */
        fun seriesSlugOf(path: String?): String? = SERIES.find(path ?: return null)?.groupValues?.get(1)

        fun parse(path: String?): ChapterLink? {
            val m = PATH.matchEntire(path?.substringBefore('?')?.substringBefore('#') ?: return null) ?: return null
            return ChapterLink(m.groupValues[1], m.groupValues[2], m.groupValues[3])
        }
    }
}

/**
 * Downloaded chapters on disk, one folder per series and per chapter:
 *
 *     <root>/<series slug>/series.properties   title
 *     <root>/<series slug>/cover               cover image, if it could be fetched
 *     <root>/<series slug>/<chapter id>/chapter.properties
 *     <root>/<series slug>/<chapter id>/001.webp, 002.webp, ...
 *     <root>/<series slug>/<chapter id>/page.html        the chapter page as the site sent it
 *     <root>/<series slug>/<chapter id>/images.txt       each page's image address (path), in order
 *     <root>/<series slug>/<chapter id>/responses.properties + response-N   the site's API answers
 *     <root>/<series slug>/<chapter id>/assets.txt       the scripts, styles and fonts the page used
 *     <root>/_assets/<sha1 of address> + .meta           those files, shared by every chapter
 *     <root>/_walks/<id>.properties                      series downloads still in progress
 *     <root>/_queue.properties                           chapters waiting to be downloaded, in order
 *
 * With the page, its files and its answers on disk, the site's own reader runs offline (see
 * [OfflineServer]). Plain files and [Properties], so it works (and is tested) without Android.
 * Not thread-safe: the download queue writes from one place at a time.
 */
class DownloadStore(val root: File) {

    data class Series(val slug: String, val title: String)

    data class Chapter(
        val link: ChapterLink,
        /** As the site labels it, e.g. "Ch.185". */
        val label: String,
        /** Scanlation group, if known. */
        val group: String = "",
        val pages: Int = 0,
        /** Every page is on disk. */
        val complete: Boolean = false,
        val bytes: Long = 0,
        val savedAt: Long = 0,
        /** The page, its files and its answers were saved too, so the site's reader can open it offline. */
        val recorded: Boolean = false,
        /** Number of the chapter after this one, as the reader's Next button says ("" at the end or unknown). */
        val next: String = "",
        /** Read to the end at least once since it was downloaded. */
        val read: Boolean = false,
    )

    /** One of the site's API answers, saved with what the page needs to read it. */
    data class Response(val url: String, val type: String, val enc: String, val body: ByteArray) {
        override fun equals(other: Any?): Boolean =
            other is Response && url == other.url && type == other.type && enc == other.enc && body.contentEquals(other.body)
        override fun hashCode(): Int = url.hashCode()
    }

    /** A saved answer on disk. */
    data class SavedResponse(val url: String, val type: String, val enc: String, val file: File)

    /** A script, style or font a chapter page uses, shared by all chapters. */
    data class Asset(val url: String, val type: String, val file: File)

    fun seriesDir(slug: String): File = File(root, safe(slug))
    fun chapterDir(link: ChapterLink): File = File(seriesDir(link.seriesSlug), safe(link.chapterId))

    fun series(): List<Series> =
        (root.listFiles() ?: emptyArray()).filter { it.isDirectory && chapterDirs(it).isNotEmpty() }
            .map { Series(it.name, readProps(File(it, SERIES_FILE)).getProperty("title").orEmpty().ifBlank { it.name }) }
            .sortedBy { it.title.lowercase() }

    fun seriesTitle(slug: String): String? = readProps(File(seriesDir(slug), SERIES_FILE)).getProperty("title")

    fun writeSeries(slug: String, title: String) {
        val dir = seriesDir(slug).apply { mkdirs() }
        writeProps(File(dir, SERIES_FILE), Properties().apply { setProperty("title", title) })
    }

    fun coverFile(slug: String): File? = File(seriesDir(slug), COVER_FILE).takeIf { it.isFile && it.length() > 0 }

    fun writeCover(slug: String, bytes: ByteArray) {
        val dir = seriesDir(slug).apply { mkdirs() }
        writeAtomically(File(dir, COVER_FILE)) { it.writeBytes(bytes) }
    }

    /** A series' chapters, newest number first. */
    fun chapters(slug: String): List<Chapter> =
        chapterDirs(seriesDir(slug)).mapNotNull { readChapter(File(it, CHAPTER_FILE)) }
            .sortedWith(compareByDescending<Chapter> { it.link.sortKey }.thenByDescending { it.link.chapterId.toLongOrNull() ?: 0 })

    fun chapter(link: ChapterLink): Chapter? = readChapter(File(chapterDir(link), CHAPTER_FILE))

    fun allChapters(): List<Chapter> = series().flatMap { chapters(it.slug) }

    fun writeChapter(ch: Chapter) {
        val dir = chapterDir(ch.link).apply { mkdirs() }
        val p = Properties().apply {
            setProperty("series", ch.link.seriesSlug)
            setProperty("id", ch.link.chapterId)
            setProperty("number", ch.link.number)
            setProperty("label", ch.label)
            setProperty("group", ch.group)
            setProperty("pages", ch.pages.toString())
            setProperty("complete", ch.complete.toString())
            setProperty("bytes", ch.bytes.toString())
            setProperty("savedAt", ch.savedAt.toString())
            setProperty("recorded", ch.recorded.toString())
            setProperty("next", ch.next)
            setProperty("read", ch.read.toString())
        }
        writeProps(File(dir, CHAPTER_FILE), p)
    }

    // --- The chapter page, for the site's reader offline ------------------------------------

    /**
     * Saves what the site's reader needs besides the images: the page, the API answers (the
     * chapter, and its comments if wanted) and which page image each address is.
     */
    fun writeRecording(link: ChapterLink, html: String, responses: List<Response>, images: List<String>, assets: List<String>) {
        val dir = chapterDir(link).apply { mkdirs() }
        (dir.listFiles() ?: emptyArray()).filter { it.name.startsWith(RESPONSE_PREFIX) }.forEach { it.delete() }
        val p = Properties()
        responses.forEachIndexed { i, r ->
            writeAtomically(File(dir, "$RESPONSE_PREFIX$i")) { it.writeBytes(r.body) }
            p.setProperty("$i.url", r.url)
            p.setProperty("$i.type", r.type)
            p.setProperty("$i.enc", r.enc)
        }
        p.setProperty("count", responses.size.toString())
        writeProps(File(dir, RESPONSES_FILE), p)
        writeAtomically(File(dir, IMAGES_FILE)) { it.writeText(images.joinToString("\n"), Charsets.UTF_8) }
        writeAtomically(File(dir, ASSETS_FILE)) { it.writeText(assets.joinToString("\n"), Charsets.UTF_8) }
        writeAtomically(File(dir, PAGE_HTML)) { it.writeText(html, Charsets.UTF_8) }
    }

    fun pageHtml(link: ChapterLink): File? = File(chapterDir(link), PAGE_HTML).takeIf { it.isFile }

    fun responses(link: ChapterLink): List<SavedResponse> {
        val dir = chapterDir(link)
        val p = readProps(File(dir, RESPONSES_FILE))
        val n = p.getProperty("count")?.toIntOrNull() ?: 0
        return (0 until n).mapNotNull { i ->
            val f = File(dir, "$RESPONSE_PREFIX$i").takeIf { it.isFile } ?: return@mapNotNull null
            SavedResponse(p.getProperty("$i.url") ?: return@mapNotNull null, p.getProperty("$i.type").orEmpty(), p.getProperty("$i.enc").orEmpty(), f)
        }
    }

    /** The image address (path) of each page, in page order. */
    fun images(link: ChapterLink): List<String> = lines(File(chapterDir(link), IMAGES_FILE))

    /** The shared files (scripts, styles, fonts) this chapter's page uses. */
    fun chapterAssets(link: ChapterLink): List<String> = lines(File(chapterDir(link), ASSETS_FILE))

    private val assetsDir: File get() = File(root, ASSETS_DIR)

    fun hasAsset(url: String): Boolean = File(assetsDir, sha1(url)).isFile

    fun writeAsset(url: String, type: String, bytes: ByteArray) {
        val dir = assetsDir.apply { mkdirs() }
        val name = sha1(url)
        writeAtomically(File(dir, name)) { it.writeBytes(bytes) }
        writeProps(File(dir, "$name.meta"), Properties().apply { setProperty("url", url); setProperty("type", type) })
    }

    fun assets(): List<Asset> =
        (assetsDir.listFiles() ?: emptyArray()).filter { it.name.endsWith(".meta") }.mapNotNull { meta ->
            val body = File(meta.parentFile, meta.name.removeSuffix(".meta")).takeIf { it.isFile } ?: return@mapNotNull null
            val p = readProps(meta)
            Asset(p.getProperty("url") ?: return@mapNotNull null, p.getProperty("type").orEmpty(), body)
        }

    /** Deletes shared files no downloaded chapter uses any more. */
    fun pruneAssets() {
        val used = allChapters().flatMap { chapterAssets(it.link) }.map(::sha1).toSet()
        (assetsDir.listFiles() ?: emptyArray()).forEach { f ->
            if (f.name.removeSuffix(".meta").removeSuffix(".part") !in used) f.delete()
        }
    }

    // --- Series downloads in progress (see SeriesWalk) ---------------------------------------

    private val walksDir: File get() = File(root, WALKS_DIR)

    fun writeWalk(id: String, values: Map<String, String>) {
        val dir = walksDir.apply { mkdirs() }
        writeProps(File(dir, safe(id) + ".properties"), Properties().apply { values.forEach { (k, v) -> setProperty(k, v) } })
    }

    fun walks(): List<Map<String, String>> =
        (walksDir.listFiles() ?: emptyArray()).filter { it.name.endsWith(".properties") }.sortedBy { it.name }.map { f ->
            val p = readProps(f)
            p.stringPropertyNames().associateWith { p.getProperty(it) }
        }

    fun deleteWalk(id: String) {
        File(walksDir, safe(id) + ".properties").delete()
    }

    // --- Chapters waiting to be downloaded -----------------------------------------------------

    /** Replaces the saved queue with [entries] (one map per chapter, in order). */
    fun writeQueue(entries: List<Map<String, String>>) {
        root.mkdirs()
        val p = Properties()
        p.setProperty("count", entries.size.toString())
        entries.forEachIndexed { i, e -> e.forEach { (k, v) -> p.setProperty("$i.$k", v) } }
        writeProps(File(root, QUEUE_FILE), p)
    }

    fun queue(): List<Map<String, String>> {
        val p = readProps(File(root, QUEUE_FILE))
        val n = p.getProperty("count")?.toIntOrNull() ?: return emptyList()
        return (0 until n).map { i ->
            p.stringPropertyNames().filter { it.startsWith("$i.") }.associate { it.removePrefix("$i.") to p.getProperty(it) }
        }.filter { it.isNotEmpty() }
    }

    /** The page files present, in page order (complete files only, not ones still downloading). */
    fun pageFiles(link: ChapterLink): List<File> =
        (chapterDir(link).listFiles() ?: emptyArray()).filter { it.isFile && PAGE_FILE.matches(it.name) }.sortedBy { it.name }

    /** The file for page [index] (1-based) if it's on disk. */
    fun pageFile(link: ChapterLink, index: Int): File? {
        val prefix = pageName(index, "")
        return (chapterDir(link).listFiles() ?: emptyArray()).firstOrNull { it.isFile && it.name.startsWith(prefix) && PAGE_FILE.matches(it.name) }
    }

    /** Where page [index] (1-based) with extension [ext] goes. */
    fun newPageFile(link: ChapterLink, index: Int, ext: String): File = File(chapterDir(link), pageName(index, ext))

    fun deleteChapter(link: ChapterLink) {
        chapterDir(link).deleteRecursively()
        val series = seriesDir(link.seriesSlug)
        if (chapterDirs(series).isEmpty()) series.deleteRecursively()
    }

    fun deleteSeries(slug: String) {
        seriesDir(slug).deleteRecursively()
    }

    fun deleteAll() {
        root.listFiles()?.forEach { it.deleteRecursively() }
    }

    fun bytes(slug: String? = null): Long = (if (slug == null) root else seriesDir(slug)).walkTopDown().filter { it.isFile }.sumOf { it.length() }

    private fun chapterDirs(series: File): List<File> =
        (series.listFiles() ?: emptyArray()).filter { it.isDirectory && File(it, CHAPTER_FILE).isFile }

    private fun readChapter(file: File): Chapter? {
        if (!file.isFile) return null
        val p = readProps(file)
        val link = ChapterLink(p.getProperty("series") ?: return null, p.getProperty("id") ?: return null, p.getProperty("number") ?: return null)
        return Chapter(
            link = link,
            label = p.getProperty("label").orEmpty().ifBlank { "Ch.${link.number}" },
            group = p.getProperty("group").orEmpty(),
            pages = p.getProperty("pages")?.toIntOrNull() ?: 0,
            complete = p.getProperty("complete") == "true",
            bytes = p.getProperty("bytes")?.toLongOrNull() ?: 0,
            savedAt = p.getProperty("savedAt")?.toLongOrNull() ?: 0,
            recorded = p.getProperty("recorded") == "true" && File(file.parentFile, PAGE_HTML).isFile,
            next = p.getProperty("next").orEmpty(),
            read = p.getProperty("read") == "true",
        )
    }

    private fun lines(file: File): List<String> =
        if (!file.isFile) emptyList() else file.readText(Charsets.UTF_8).split('\n').map { it.trim() }.filter { it.isNotEmpty() }

    companion object {
        private const val SERIES_FILE = "series.properties"
        private const val CHAPTER_FILE = "chapter.properties"
        private const val COVER_FILE = "cover"
        private const val PAGE_HTML = "page.html"
        private const val IMAGES_FILE = "images.txt"
        private const val ASSETS_FILE = "assets.txt"
        private const val RESPONSES_FILE = "responses.properties"
        private const val RESPONSE_PREFIX = "response-"
        /** Not a series: series folders are named by slugs, which start with a letter or digit. */
        private const val ASSETS_DIR = "_assets"
        private const val WALKS_DIR = "_walks"
        private const val QUEUE_FILE = "_queue.properties"
        private val SAFE = Regex("^[A-Za-z0-9._-]{1,200}$")
        private val PAGE_FILE = Regex("^\\d{3,}\\.[a-z0-9]{1,5}$")

        /** Folder names come from the site: refuse anything that could leave the downloads folder. */
        private fun safe(name: String): String {
            require(SAFE.matches(name) && name != "." && name != "..") { "unsafe name: $name" }
            return name
        }

        fun pageName(index: Int, ext: String): String = "%03d".format(index) + if (ext.isEmpty()) "." else ".$ext"

        /** File extension for an image content type ("image/webp" -> "webp"). */
        fun extensionFor(contentType: String?): String =
            when (contentType?.substringBefore(';')?.trim()?.lowercase()) {
                "image/webp" -> "webp"
                "image/jpeg", "image/jpg" -> "jpg"
                "image/png" -> "png"
                "image/gif" -> "gif"
                "image/avif" -> "avif"
                else -> "img"
            }

        fun sha1(text: String): String =
            java.security.MessageDigest.getInstance("SHA-1").digest(text.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }

        private fun readProps(file: File): Properties =
            Properties().apply { if (file.isFile) runCatching { file.reader(Charsets.UTF_8).use { load(it) } } }

        private fun writeProps(file: File, p: Properties) =
            writeAtomically(file) { f -> f.writer(Charsets.UTF_8).use { p.store(it, null) } }

        /** Writes to a temporary file first, so a crash never leaves half a file behind. */
        fun writeAtomically(file: File, write: (File) -> Unit) {
            val tmp = File(file.parentFile, file.name + ".part")
            write(tmp)
            if (!tmp.renameTo(file)) {
                file.delete()
                check(tmp.renameTo(file)) { "couldn't save ${file.name}" }
            }
        }
    }
}
