package com.notsonata.comixium.web

import org.junit.Assert.assertEquals
import org.junit.Test

class BackStackTest {
    private val home = "https://comix.ws/"
    private val title = "https://comix.ws/title/9nej6-return-of-the-blossoming-blade"
    private val ch185 = "https://comix.ws/title/9nej6-return-of-the-blossoming-blade/11578620-chapter-185"
    private val ch186 = "https://comix.ws/title/9nej6-return-of-the-blossoming-blade/11590001-chapter-186"
    private val other = "https://comix.ws/title/lgkg7-fog-land"
    private val otherCh = "https://comix.ws/title/lgkg7-fog-land/1234-chapter-1"
    private val browse = "https://comix.ws/browse?q=solo"

    private fun back(vararg urls: String) = BackStack.target(urls.toList(), urls.lastIndex)

    @Test fun plainBackStepsOnePage() {
        assertEquals(1, back(home, title, ch185))
        assertEquals(0, back(home, title))
        assertEquals(-1, back(home))
    }

    @Test fun goingUpFromChapterToSeriesThenBackReturnsToWhereTheSeriesWasOpenedFrom() {
        // The reported case: Home → series → chapter → tap series title → series → Back = Home.
        assertEquals(0, back(home, title, ch185, title))
        assertEquals(1, back(home, browse, title, ch185, title))
    }

    @Test fun chaptersOfOneSeriesAreOneReaderPage() {
        assertEquals(1, back(home, title, ch185, ch186))
        assertEquals(1, back(home, title, ch185, title, ch186))
        assertEquals(3, back(home, title, ch185, other, otherCh))
    }

    @Test fun skipsDuplicateEntries() {
        assertEquals(0, back(home, title, title))
        assertEquals(0, back("https://comix.ws/home", title, "https://comix.to/title/9nej6-return-of-the-blossoming-blade/"))
        assertEquals(-1, back(home, "https://comix.ws/home"))
    }

    @Test fun differentSearchesAreDifferentPages() {
        assertEquals(1, back(home, "https://comix.ws/browse?q=a", "https://comix.ws/browse?q=b"))
    }
}
