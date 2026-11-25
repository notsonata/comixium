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
 *
 * Plain files and [Properties], so it works (and is tested) without Android. Not thread-safe:
 * the download queue writes from one place at a time.
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
        /** Page sizes in pixels (width x height), so the offline reader can lay out before images load. */
        val sizes: List<Pair<Int, Int>> = emptyList(),
        /** Last page read offline (1-based), 0 if never opened. */
        val lastPage: Int = 0,
    )

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
            setProperty("sizes", ch.sizes.joinToString(",") { (w, h) -> "${w}x$h" })
            setProperty("lastPage", ch.lastPage.toString())
        }
        writeProps(File(dir, CHAPTER_FILE), p)
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
            sizes = parseSizes(p.getProperty("sizes").orEmpty()),
            lastPage = p.getProperty("lastPage")?.toIntOrNull() ?: 0,
        )
    }

    companion object {
        private const val SERIES_FILE = "series.properties"
        private const val CHAPTER_FILE = "chapter.properties"
        private const val COVER_FILE = "cover"
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

        fun parseSizes(text: String): List<Pair<Int, Int>> =
            if (text.isBlank()) emptyList() else text.split(',').map { s ->
                val w = s.substringBefore('x').toIntOrNull() ?: 0
                val h = s.substringAfter('x', "").toIntOrNull() ?: 0
                w to h
            }

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
