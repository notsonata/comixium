package com.notsonata.comixium.web

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PageTitleTest {
    private val generic = "Comix - Read Comics online for free"

    @Test fun titles() {
        assertEquals("", PageTitle.of("https://comix.ws/", generic))
        assertEquals("", PageTitle.of("https://comix.ws/home", generic))
        assertEquals("Browse", PageTitle.of("https://comix.ws/browse", generic))
        assertEquals("“solo leveling”", PageTitle.of("https://comix.ws/browse?q=solo+leveling", generic))
        assertEquals("Library", PageTitle.of("https://comix.ws/user?tab=titles", "whatever"))
        assertEquals("Notifications", PageTitle.of("https://comix.ws/user?tab=notifs", generic))
        assertEquals("Profile", PageTitle.of("https://comix.ws/user", generic))
        assertEquals("Return of the Blossoming Blade", PageTitle.of("https://comix.ws/title/x", "Return of the Blossoming Blade"))
        assertEquals("Popular Genres", PageTitle.of("https://comix.ws/genres", "Popular Genres - Comix"))
    }

    @Test fun samePage() {
        assertTrue(Site.samePage("https://comix.ws/", "/"))
        assertTrue(Site.samePage("https://comix.to/home", "/"))
        assertTrue(Site.samePage("https://comix.ws/browse/", "/browse"))
        assertTrue(Site.samePage("https://comix.ws/user?tab=titles#x", "/user?tab=titles"))
        assertFalse(Site.samePage("https://comix.ws/browse?q=a", "/browse"))
        assertFalse(Site.samePage("https://comix.ws/user?tab=history", "/user?tab=titles"))
        assertFalse(Site.samePage(null, "/browse"))
    }
}
