/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.player

import com.nestmusic.music.models.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueToolsTest {
    @Test
    fun `queue search matches title and artist without case sensitivity`() {
        val track = track(title = "Midnight City", artist = "M83")

        assertTrue(matchesQueueSearch(track, "midnight"))
        assertTrue(matchesQueueSearch(track, "m83"))
        assertFalse(matchesQueueSearch(track, "daft punk"))
    }

    @Test
    fun `blank queue search matches every track`() {
        assertTrue(matchesQueueSearch(track("Song"), "  "))
    }

    @Test
    fun `queue share includes title artist and direct links`() {
        val tracks = listOf(
            track("Midnight City", "M83", id = "abc123"),
            track("Outro", "M83", id = "def456"),
        )

        assertEquals(
            "Road trip\n\n" +
                "1. Midnight City — M83\nhttps://music.youtube.com/watch?v=abc123\n\n" +
                "2. Outro — M83\nhttps://music.youtube.com/watch?v=def456",
            buildQueueShareText(" Road trip ", tracks),
        )
    }

    @Test
    fun `queue share omits empty artists and title`() {
        assertEquals(
            "1. Instrumental",
            buildQueueShareText(null, listOf(track("Instrumental", id = ""))),
        )
    }

    private fun track(
        title: String,
        artist: String? = null,
        id: String = title.lowercase().replace(' ', '_'),
    ) = MediaMetadata(
        id = id,
        title = title,
        artists = listOfNotNull(artist?.let { MediaMetadata.Artist(id = null, name = it) }),
        duration = 180,
    )
}
