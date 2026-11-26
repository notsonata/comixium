package com.notsonata.comixium.offline

/**
 * Downloading many chapters of a series, one chapter number at a time, the way a reader goes
 * through it. Each number usually has several versions (one per scanlation group); the walk saves
 * one of them, preferring [groups] in order, and moves on to the number the reader's Next button
 * names. It never pages through the site's chapter list: it looks numbers up with the series
 * page's chapter search, which the site offers for exactly that.
 *
 * Pure Kotlin (state, choices and persistence as plain maps), so it's unit-tested.
 */
data class SeriesWalk(
    val id: String,
    val seriesSlug: String,
    val seriesTitle: String,
    val coverUrl: String = "",
    /** Scanlation groups to download from, most preferred first. Empty = any group. */
    val groups: List<String> = emptyList(),
    /** Chapters to save; 0 = every chapter up to the latest. */
    val limit: Int = 0,
    /** Chapters this walk has queued so far. */
    val saved: Int = 0,
    /** What happens next. */
    val step: Step = Step.First,
    /** Group of the last chapter queued, kept when no group is preferred so versions don't jump around. */
    val lastGroup: String = "",
    /** Numbers already looked at, so a site quirk can't send the walk round in circles. */
    val seen: List<String> = emptyList(),
    /** Why it stopped early, if it did. */
    val problem: String = "",
) {
    sealed interface Step {
        /** Find the series' first chapter (the series page's list, oldest first). */
        data object First : Step
        /** Look up the versions of chapter [number] (the series page's chapter search). */
        data class Find(val number: String) : Step
        /** Open a chapter without saving it, just to read which number comes next. */
        data class Peek(val path: String) : Step
        /** Waiting for [link] (queued by this walk) to be prepared; its Next button gives the next number. */
        data class Wait(val link: ChapterLink) : Step
        data object Done : Step
    }

    val isDone: Boolean get() = step == Step.Done
    val isFull: Boolean get() = limit in 1..saved

    /** One row of the series page's chapter list. */
    data class Row(val path: String, val number: String, val group: String)

    sealed interface Choice {
        /** Download this version. */
        data class Save(val row: Row) : Choice
        /** None of the wanted groups has this number: step over it (via this version). */
        data class Skip(val row: Row) : Choice
        /** The number isn't listed at all. */
        data object Missing : Choice
    }

    /** Which version of chapter [number] to take from [rows]. */
    fun choose(rows: List<Row>, number: String): Choice {
        val versions = rows.filter { sameNumber(it.number, number) }
        if (versions.isEmpty()) return Choice.Missing
        if (groups.isNotEmpty()) {
            for (g in groups) versions.firstOrNull { it.group.equals(g, ignoreCase = true) }?.let { return Choice.Save(it) }
            return Choice.Skip(versions.first())
        }
        return Choice.Save(versions.firstOrNull { lastGroup.isNotEmpty() && it.group.equals(lastGroup, ignoreCase = true) } ?: versions.first())
    }

    /** Moves on to [next] (the number after the one just handled), or finishes. */
    fun advance(next: String): SeriesWalk = when {
        isFull -> copy(step = Step.Done)
        next.isBlank() -> copy(step = Step.Done)
        seen.any { sameNumber(it, next) } -> copy(step = Step.Done, problem = "Stopped: Ch.$next came up twice")
        else -> copy(step = Step.Find(next))
    }

    fun toMap(): Map<String, String> = buildMap {
        put("id", id)
        put("series", seriesSlug)
        put("title", seriesTitle)
        put("cover", coverUrl)
        put("groups", groups.joinToString("\n"))
        put("limit", limit.toString())
        put("saved", saved.toString())
        put("lastGroup", lastGroup)
        put("seen", seen.joinToString(","))
        put("problem", problem)
        when (val s = step) {
            Step.First -> put("step", "first")
            is Step.Find -> { put("step", "find"); put("arg", s.number) }
            is Step.Peek -> { put("step", "peek"); put("arg", s.path) }
            is Step.Wait -> { put("step", "wait"); put("arg", s.link.path) }
            Step.Done -> put("step", "done")
        }
    }

    companion object {
        /** "185" and "185.0" are the same chapter; anything that isn't a number compares as text. */
        fun sameNumber(a: String, b: String): Boolean {
            val x = a.trim().toDoubleOrNull()
            val y = b.trim().toDoubleOrNull()
            return if (x != null && y != null) x == y else a.trim().equals(b.trim(), ignoreCase = true)
        }

        /** The lowest chapter number among [rows] (the start of the series). */
        fun firstNumber(rows: List<Row>): String? =
            rows.minByOrNull { it.number.toDoubleOrNull() ?: Double.MAX_VALUE }?.number

        fun fromMap(m: Map<String, String>): SeriesWalk? {
            val id = m["id"] ?: return null
            val slug = m["series"] ?: return null
            val arg = m["arg"].orEmpty()
            val step = when (m["step"]) {
                "first" -> Step.First
                "find" -> Step.Find(arg)
                "peek" -> Step.Peek(arg)
                "wait" -> ChapterLink.parse(arg)?.let { Step.Wait(it) } ?: Step.Done
                else -> Step.Done
            }
            return SeriesWalk(
                id = id,
                seriesSlug = slug,
                seriesTitle = m["title"].orEmpty(),
                coverUrl = m["cover"].orEmpty(),
                groups = m["groups"].orEmpty().split('\n').filter { it.isNotBlank() },
                limit = m["limit"]?.toIntOrNull() ?: 0,
                saved = m["saved"]?.toIntOrNull() ?: 0,
                step = step,
                lastGroup = m["lastGroup"].orEmpty(),
                seen = m["seen"].orEmpty().split(',').filter { it.isNotBlank() },
                problem = m["problem"].orEmpty(),
            )
        }
    }
}
