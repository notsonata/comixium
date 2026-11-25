package com.notsonata.comixium.offline

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
        val ch = DownloadStore.Chapter(link, "Ch.185", "Lagoon Scans", pages = 2, complete = true, bytes = 10, savedAt = 5,
            sizes = listOf(1532 to 1024, 800 to 12000), lastPage = 2)
        store.writeChapter(ch)
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

    @Test(expected = IllegalArgumentException::class) fun refusesUnsafeFolderNames() {
        DownloadStore(tmp.root).seriesDir("../outside")
    }

    @Test fun mapsContentTypes() {
        assertEquals("webp", DownloadStore.extensionFor("image/webp"))
        assertEquals("jpg", DownloadStore.extensionFor("image/jpeg; charset=binary"))
        assertEquals("img", DownloadStore.extensionFor(null))
        assertEquals(listOf(1 to 2, 0 to 0), DownloadStore.parseSizes("1x2,bad"))
    }
}
