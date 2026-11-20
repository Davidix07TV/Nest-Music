package com.nestmusic.music.ui.component

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsFetchPolicyTest {
    @Test
    fun `fetches when enabled, track present and lyrics missing`() {
        // The Apple Music player calls EnsureCurrentLyricsLoaded(enabled = showLyrics):
        // opening its lyrics pane on an uncached track must trigger a fetch.
        assertTrue(LyricsFetchPolicy.shouldFetch(enabled = true, mediaId = "abc", hasCurrentLyrics = false))
    }

    @Test
    fun `does not fetch when pane hidden, no track, or lyrics already cached`() {
        assertFalse(LyricsFetchPolicy.shouldFetch(enabled = false, mediaId = "abc", hasCurrentLyrics = false))
        assertFalse(LyricsFetchPolicy.shouldFetch(enabled = true, mediaId = null, hasCurrentLyrics = false))
        assertFalse(LyricsFetchPolicy.shouldFetch(enabled = true, mediaId = "  ", hasCurrentLyrics = false))
        assertFalse(LyricsFetchPolicy.shouldFetch(enabled = true, mediaId = "abc", hasCurrentLyrics = true))
    }
}
