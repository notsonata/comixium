package com.notsonata.comixium.web

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkPolicyTest {
    private val blocker = AdBlocker { null }.apply { load() }
    private fun d(url: String, gesture: Boolean = true, main: Boolean = true) = LinkPolicy.decide(url, gesture, main, blocker)

    @Test fun sitePagesStayInApp() {
        assertEquals(LinkAction.Internal, d("https://comix.ws/title/abc"))
        assertEquals(LinkAction.Internal, d("https://comix.to/browse", gesture = false))
    }

    @Test fun tappedExternalLinksOpenInCustomTab() {
        assertEquals(LinkAction.External("https://anilist.co/manga/1"), d("https://anilist.co/manga/1"))
    }

    @Test fun untappedOrAdNavigationsAreBlocked() {
        assertEquals(LinkAction.Block, d("https://anilist.co/manga/1", gesture = false))
        assertEquals(LinkAction.Block, d("https://araplhn.org/4/abc"))
        assertEquals(LinkAction.Block, d("https://gotaku.to/?utm_source=comix_home"))
    }

    @Test fun otherSchemes() {
        assertEquals(LinkAction.System("mailto:x@y.z"), d("mailto:x@y.z"))
        assertEquals(LinkAction.Block, d("intent://x#Intent;end", gesture = false))
        assertEquals(LinkAction.Internal, d("about:blank", gesture = false))
        assertEquals(LinkAction.Internal, d("https://evil.com/frame", gesture = false, main = false))
    }
}
