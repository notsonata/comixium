package com.notsonata.comixium.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflinePageTest {
    private val link = ChapterLink("9nej6-return-of-the-blossoming-blade", "11578620", "185")

    @Test fun servesOnlyChapterFilesUnderItsPrefix() {
        assertEquals("https://appassets.androidplatform.net/offline/9nej6-return-of-the-blossoming-blade/11578620/index.html", OfflinePage.url(link))
        assertEquals(Triple("9nej6-x", "11578620", "001.webp"), OfflinePage.parsePath("9nej6-x/11578620/001.webp"))
        assertNull(OfflinePage.parsePath("../x/1/001.webp"))
        assertNull(OfflinePage.parsePath("s/1/../../secret"))
        assertNull(OfflinePage.parsePath("s/abc/001.webp"))
    }

    @Test fun listsPagesWithSizesAndEscapesText() {
        val html = OfflinePage.html(listOf("001.webp", "002.jpg"), listOf(1532 to 1024), "#22282A", startPage = 2,
            endText = "End of <Ch.185>", nextLabel = "Ch.186 & more")
        assertTrue(html.contains("<img src=\"001.webp\" width=\"1532\" height=\"1024\""))
        assertTrue(html.contains("<img src=\"002.jpg\" loading=\"lazy\"")) // size unknown: no dimensions
        assertTrue(html.contains("End of &lt;Ch.185&gt;"))
        assertTrue(html.contains("Next: Ch.186 &amp; more"))
        assertTrue(html.contains("var start = 2;"))
        assertFalse(OfflinePage.html(listOf("001.webp"), emptyList(), "#000", 0, "End", null).contains("Next:"))
    }

    @Test fun splitsTheReaderTitle() {
        assertEquals("Return of the Blossoming Blade" to "Ch.185", ChapterDownloads.splitTitle("Return of the Blossoming Blade · Ch.185"))
        assertEquals("A · B" to "Ch.1", ChapterDownloads.splitTitle("A · B · Ch.1"))
        assertEquals("Just a title" to "", ChapterDownloads.splitTitle("Just a title"))
    }
}
