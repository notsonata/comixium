package com.notsonata.comixium.offline

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class DownloadStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private val link = ChapterLink("9nej6-return-of-the-blossoming-blade", "11578620", "185")

    @Test fun parsesChapterLinks() {
        assertEquals(link, ChapterLink.parse("/title/9nej6-return-of-the-blossoming-blade/11578620-chapter-185"))
        assertEquals("189.2", ChapterLink.parse("/title/9nej6-x/11836211-chapter-189.2/?x=1")?.number)
        assertEquals(link.path, ChapterLink.parse(link.path)?.path)
        assertNull(ChapterLink.parse("/title/9nej6-x"))
        assertNull(ChapterLink.parse("/title/../11578620-chapter-1"))
        assertNull(ChapterLink.parse("/title/a/b-chapter-1"))
        assertTrue(ChapterLink("s", "2", "189.2").sortKey > ChapterLink("s", "1", "189").sortKey)
    }

    @Test fun roundTripsChaptersAndSeries() {
        val store = DownloadStore(tmp.root)
        store.writeSeries(link.seriesSlug, "Return of the Blossoming Blade")
        val ch = DownloadStore.Chapter(link, "Ch.185", "Lagoon Scans", pages = 2, complete = true, bytes = 10, savedAt = 5, next = "186", read = true)
        store.writeChapter(ch)
        // "recorded" needs the saved page too.
        assertEquals(ch, store.chapter(link))
        assertEquals(listOf(DownloadStore.Series(link.seriesSlug, "Return of the Blossoming Blade")), store.series())
        // Newest number first.
        store.writeChapter(ch.copy(link = link.copy(chapterId = "11836211", number = "189.2")))
        store.writeChapter(ch.copy(link = link.copy(chapterId = "1", number = "9")))
        assertEquals(listOf("189.2", "185", "9"), store.chapters(link.seriesSlug).map { it.link.number })
    }

    @Test fun pagesAreListedInOrderAndDeletingTheLastChapterRemovesTheSeries() {
        val store = DownloadStore(tmp.root)
        store.writeSeries(link.seriesSlug, "Title")
        store.writeChapter(DownloadStore.Chapter(link, "Ch.185"))
        store.newPageFile(link, 2, "webp").writeBytes(byteArrayOf(2))
        store.newPageFile(link, 1, "jpg").writeBytes(byteArrayOf(1))
        store.newPageFile(link, 10, "webp").writeBytes(byteArrayOf(10))
        java.io.File(store.chapterDir(link), "003.webp.part").writeBytes(byteArrayOf(3)) // still downloading
        assertEquals(listOf("001.jpg", "002.webp", "010.webp"), store.pageFiles(link).map { it.name })
        assertEquals("002.webp", store.pageFile(link, 2)?.name)
        assertNull(store.pageFile(link, 3))
        assertTrue(store.bytes() > 0)
        store.deleteChapter(link)
        assertFalse(store.seriesDir(link.seriesSlug).exists())
        assertEquals(emptyList<DownloadStore.Series>(), store.series())
    }

    @Test fun savesTheChapterPageAnswersAndSharedFiles() {
        val store = DownloadStore(tmp.root)
        store.writeChapter(DownloadStore.Chapter(link, "Ch.185", complete = true, recorded = true))
        val answer = DownloadStore.Response("https://comix.to/api/v1/chapters/11578620?v=0&w=0&_=x", "application/json", "2", "{\"e\":\"abc\"}".toByteArray())
        val js = "https://comix.to/assets/build/1/dist/main-a.js"
        store.writeRecording(link, "<html>page</html>", listOf(answer), listOf("/hi/a", "/hi/b"), listOf(js))
        store.writeAsset(js, "application/javascript", "code".toByteArray())

        assertTrue(store.chapter(link)!!.recorded)
        assertEquals("<html>page</html>", store.pageHtml(link)!!.readText())
        val saved = store.responses(link).single()
        assertEquals(answer.url, saved.url)
        assertEquals("2", saved.enc)
        assertArrayEquals(answer.body, saved.file.readBytes())
        assertEquals(listOf("/hi/a", "/hi/b"), store.images(link))
        assertEquals(listOf(js), store.chapterAssets(link))
        assertTrue(store.hasAsset(js))
        assertEquals("code", store.assets().single { it.url == js }.file.readText())
        // Shared files and series downloads aren't series.
        store.writeWalk("w1", mapOf("id" to "w1", "series" to link.seriesSlug))
        assertEquals(listOf(link.seriesSlug), store.series().map { it.slug })

        // A file no chapter uses any more is deleted.
        store.writeAsset("https://comix.to/assets/old.js", "application/javascript", "old".toByteArray())
        store.pruneAssets()
        assertTrue(store.hasAsset(js))
        assertFalse(store.hasAsset("https://comix.to/assets/old.js"))
        store.deleteChapter(link)
        store.pruneAssets()
        assertFalse(store.hasAsset(js))
    }

    @Test fun keepsSeriesDownloads() {
        val store = DownloadStore(tmp.root)
        val w = SeriesWalk("w9", "slug", "Title", groups = listOf("Asura Scans"), limit = 5, saved = 2, step = SeriesWalk.Step.Find("12"))
        store.writeWalk(w.id, w.toMap())
        assertEquals(w, store.walks().mapNotNull(SeriesWalk::fromMap).single())
        store.deleteWalk(w.id)
        assertTrue(store.walks().isEmpty())
    }

    @Test fun keepsTheQueueInOrder() {
        val store = DownloadStore(tmp.root)
        assertTrue(store.queue().isEmpty())
        val q = listOf(
            mapOf("path" to "/title/s/2-chapter-1", "group" to "Asura Scans", "title" to "A = B: C"),
            mapOf("path" to "/title/s/3-chapter-2", "group" to ""),
        )
        store.writeQueue(q)
        assertEquals(q, store.queue())
        // The queue file isn't a series.
        assertTrue(store.series().isEmpty())
        store.writeQueue(emptyList())
        assertTrue(store.queue().isEmpty())
    }

    @Test(expected = IllegalArgumentException::class) fun refusesUnsafeFolderNames() {
        DownloadStore(tmp.root).seriesDir("../outside")
    }

    @Test fun mapsContentTypes() {
        assertEquals("webp", DownloadStore.extensionFor("image/webp"))
        assertEquals("jpg", DownloadStore.extensionFor("image/jpeg; charset=binary"))
        assertEquals("img", DownloadStore.extensionFor(null))
    }
}
