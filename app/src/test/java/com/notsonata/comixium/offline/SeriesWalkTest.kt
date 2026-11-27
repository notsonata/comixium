package com.notsonata.comixium.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SeriesWalkTest {
    private fun row(group: String, number: String = "50", id: String = group.length.toString()) =
        SeriesWalk.Row("/title/s/$id-chapter-$number", number, group)

    private val versions = listOf(row("WebToon"), row("Asura Scans"), row("QI Scans"), row("KaynScan"))

    @Test fun takesTheFirstPreferredGroupThatHasTheChapter() {
        val w = SeriesWalk("w", "s", "S", groups = listOf("Lagoon Scans", "qi scans", "Asura Scans"))
        assertEquals(SeriesWalk.Choice.Save(row("QI Scans")), w.choose(versions, "50"))
    }

    @Test fun skipsNumbersNoPreferredGroupHas() {
        val w = SeriesWalk("w", "s", "S", groups = listOf("Lagoon Scans"))
        assertEquals(SeriesWalk.Choice.Skip(row("WebToon")), w.choose(versions, "50"))
    }

    @Test fun anyGroupKeepsToTheLastOneUsed() {
        assertEquals(SeriesWalk.Choice.Save(row("WebToon")), SeriesWalk("w", "s", "S").choose(versions, "50"))
        assertEquals(SeriesWalk.Choice.Save(row("KaynScan")), SeriesWalk("w", "s", "S", lastGroup = "KaynScan").choose(versions, "50"))
    }

    @Test fun onlyMatchesTheSameNumber() {
        val w = SeriesWalk("w", "s", "S")
        assertEquals(SeriesWalk.Choice.Missing, w.choose(versions, "51"))
        assertEquals(SeriesWalk.Choice.Save(row("WebToon")), w.choose(versions, "50.0"))
        assertTrue(SeriesWalk.sameNumber("185", "185.0"))
        assertTrue(!SeriesWalk.sameNumber("185", "185.5"))
        assertEquals("1", SeriesWalk.firstNumber(listOf(row("A", "2"), row("B", "1"), row("C", "1.5"))))
    }

    @Test fun advancesUntilTheLimitOrTheEnd() {
        val w = SeriesWalk("w", "s", "S", limit = 2, saved = 1, step = SeriesWalk.Step.Peek("/title/s/1-chapter-50"))
        assertEquals(SeriesWalk.Step.Find("51"), w.advance("51").step)
        assertTrue(w.copy(saved = 2).advance("51").isDone)
        assertTrue(w.advance("").isDone)
        // Unlimited keeps going.
        assertEquals(SeriesWalk.Step.Find("51"), w.copy(limit = 0, saved = 40).advance("51").step)
        // A number seen before ends it (no loops).
        val looped = w.copy(seen = listOf("50", "51")).advance("51.0")
        assertTrue(looped.isDone)
        assertTrue(looped.problem.isNotEmpty())
    }

    @Test fun roundTripsEveryStep() {
        listOf(
            SeriesWalk.Step.First, SeriesWalk.Step.Find("12.5"), SeriesWalk.Step.Peek("/title/s/1-chapter-3"), SeriesWalk.Step.Done,
        ).forEach { step ->
            val w = SeriesWalk("w1", "s", "Title", "https://x/c.jpg", listOf("A", "B C"), 10, 3, step, "A", listOf("1", "2"), "")
            assertEquals(w, SeriesWalk.fromMap(w.toMap()))
        }
    }

    @Test fun queuingAChapterLooksAheadUnlessThatWasTheLast() {
        val w = SeriesWalk("w", "s", "S", limit = 2, step = SeriesWalk.Step.Find("50"))
        val one = w.queued(row("Asura Scans"))
        assertEquals(1, one.saved)
        assertEquals("Asura Scans", one.lastGroup)
        assertEquals(SeriesWalk.Step.Peek(row("Asura Scans").path), one.step)
        assertTrue(one.queued(row("Asura Scans", "51")).isDone)
        // Up to the latest never fills up.
        assertEquals(SeriesWalk.Step.Peek(row("A", "51").path), w.copy(limit = 0, saved = 99).queued(row("A", "51")).step)
    }

    @Test fun aWalkSavedWhileWaitingOnAChapterLooksItsNumberUpAgain() {
        val old = SeriesWalk("w1", "s", "T").toMap() + mapOf("step" to "wait", "arg" to "/title/s/77-chapter-12.5")
        assertEquals(SeriesWalk.Step.Find("12.5"), SeriesWalk.fromMap(old)?.step)
    }
}
