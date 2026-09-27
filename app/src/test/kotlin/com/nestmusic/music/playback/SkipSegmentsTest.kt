/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSegmentsTest {
    private val payload = """
        [
            {"category":"sponsor","segment":[10.5,20.0],"UUID":"abc","videoDuration":100.0,"locked":1,"votes":3},
            {"category":"intro","segment":[0.0,5.25],"UUID":"def"},
            {"category":"broken","segment":[30.0]},
            {"category":"inverted","segment":[50.0,40.0]}
        ]
    """.trimIndent()

    // Parsing
    @Test
    fun `parse converts seconds to milliseconds and keeps known fields`() {
        val segments = SkipSegmentsRepository.parse(payload)
        assertEquals("malformed entries dropped", 2, segments.size)
        val sponsor = segments.first { it.category == "sponsor" }
        assertEquals(10_500L, sponsor.startMs)
        assertEquals(20_000L, sponsor.endMs)
        val intro = segments.first { it.category == "intro" }
        assertEquals(0L, intro.startMs)
        assertEquals(5_250L, intro.endMs)
    }

    @Test
    fun `parse tolerates garbage and empty bodies`() {
        assertTrue("not json", SkipSegmentsRepository.parse("oops").isEmpty())
        assertTrue("empty body", SkipSegmentsRepository.parse("").isEmpty())
        assertTrue("empty array", SkipSegmentsRepository.parse("[]").isEmpty())
    }

    @Test
    fun `parse drops segments with zero or negative length`() {
        val segments = SkipSegmentsRepository.parse(
            """[{"category":"x","segment":[5.0,5.0]},{"category":"y","segment":[9.0,1.0]}]""",
        )
        assertTrue(segments.isEmpty())
    }

    // Position lookup
    @Test
    fun `findActive matches inside the range only`() {
        val segments = SkipSegmentsRepository.parse(payload)
        val sponsor = segments.first { it.category == "sponsor" }

        assertNull("before range", SkipSegmentsRepository.findActive(10_499L, segments))
        assertEquals(
            "start is inclusive",
            sponsor,
            SkipSegmentsRepository.findActive(10_500L, segments),
        )
        assertEquals(
            "last ms inside range",
            sponsor,
            SkipSegmentsRepository.findActive(19_999L, segments),
        )
        assertNull("end is exclusive", SkipSegmentsRepository.findActive(20_000L, segments))
        assertNull("no active segment", SkipSegmentsRepository.findActive(60_000L, segments))
    }

    @Test
    fun `findActive prefers the first matching segment on overlap`() {
        val overlapping = listOf(
            SkipSegment("sponsor", 0L, 10_000L),
            SkipSegment("intro", 5_000L, 15_000L),
        )
        assertEquals("sponsor", SkipSegmentsRepository.findActive(7_000L, overlapping)?.category)
    }

    // Range helpers
    @Test
    fun `contains is start-inclusive and end-exclusive`() {
        val segment = SkipSegment("outro", 1_000L, 2_000L)
        assertTrue(segment.contains(1_000L))
        assertTrue(segment.contains(1_999L))
        assertTrue(!segment.contains(999L))
        assertTrue(!segment.contains(2_000L))
    }

    // Defaults
    @Test
    fun `default categories are a subset of the supported ones`() {
        assertTrue(DEFAULT_SKIP_CATEGORIES.all { it in SKIP_CATEGORIES })
        assertTrue(DEFAULT_SKIP_CATEGORIES.isNotEmpty())
    }
}
