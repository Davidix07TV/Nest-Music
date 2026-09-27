/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.screens.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsSearchTest {
    private val entries = listOf(
        ResolvedSettingsEntry(
            entry = SettingsSearchEntry(
                title = 1, icon = 2, route = "settings/player",
                parent = null, keywords = "fade gapless mix",
            ),
            title = "Crossfade",
            text = "crossfade fade gapless mix",
        ),
        ResolvedSettingsEntry(
            entry = SettingsSearchEntry(
                title = 3, icon = 4, route = "settings/integrations/lastfm",
                parent = 5, keywords = "scrobble lastfm",
            ),
            title = "Last.fm",
            text = "last.fm integrations scrobble lastfm",
        ),
    )

    @Test
    fun `empty query matches nothing`() {
        assertTrue(matchSettingsEntries(entries, "   ").isEmpty())
    }

    @Test
    fun `single token matches case-insensitively`() {
        val results = matchSettingsEntries(entries, "CROSS")
        assertEquals(1, results.size)
        assertEquals("Crossfade", results[0].title)
    }

    @Test
    fun `all tokens must match`() {
        assertEquals(1, matchSettingsEntries(entries, "scrobble lastfm").size)
        assertTrue(matchSettingsEntries(entries, "scrobble piano").isEmpty())
    }

    @Test
    fun `keywords widen matching beyond visible labels`() {
        val results = matchSettingsEntries(entries, "gapless")
        assertEquals("Crossfade", results.single().title)
    }
}
