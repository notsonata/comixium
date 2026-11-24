package com.notsonata.comixium.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteTest {
    @Test fun hosts() {
        assertTrue(Site.isSiteUrl("https://comix.ws/"))
        assertTrue(Site.isSiteUrl("https://comix.to/title/x"))
        assertTrue(Site.isSiteUrl("https://www.comix.ws/browse?q=a"))
        assertFalse(Site.isSiteUrl("https://notcomix.ws/"))
        assertFalse(Site.isSiteUrl("https://comix.ws.evil.com/"))
        assertFalse(Site.isSiteUrl("https://evil.com/?u=https://comix.ws"))
        assertFalse(Site.isSiteUrl(null))
        assertNull(Site.hostOf("mailto:a@b.c"))
        assertEquals("mailto", Site.schemeOf("mailto:a@b.c"))
        assertEquals("comix.ws", Site.hostOf("https://user@comix.ws:443/x"))
    }

    @Test fun readerUrls() {
        assertTrue(Site.isReaderUrl("https://comix.ws/title/9nej6-return-of-the-blossoming-blade/11578620-chapter-185"))
        assertTrue(Site.isReaderUrl("https://comix.to/title/abc-x/123-chapter-188.2?p=3#top"))
        assertFalse(Site.isReaderUrl("https://comix.ws/title/9nej6-return-of-the-blossoming-blade"))
        assertFalse(Site.isReaderUrl("https://comix.ws/browse"))
        assertFalse(Site.isReaderUrl("https://evil.com/title/a/1-chapter-1"))
    }

    @Test fun paths() {
        assertEquals("/browse?q=solo#x", Site.pathOf("https://comix.ws/browse?q=solo#x"))
        assertEquals("/", Site.pathOf("https://comix.ws"))
        assertEquals("/", Site.pathOf("https://example.com/browse"))
        assertEquals("https://comix.to/title/a?b=1", Site.swapDomain("https://comix.ws/title/a?b=1", "comix.to"))
        assertEquals("https://comix.ws/user?tab=history", Site.url("comix.ws", "user?tab=history"))
    }

    @Test fun titleUrls() {
        assertTrue(Site.isTitleUrl("https://comix.ws/title/9nej6-return-of-the-blossoming-blade"))
        assertTrue(Site.isTitleUrl("https://comix.to/title/abc-x/?tab=chapters"))
        assertFalse(Site.isTitleUrl("https://comix.ws/title/abc-x/123-chapter-1"))
        assertFalse(Site.isTitleUrl("https://comix.ws/browse"))
    }
}
