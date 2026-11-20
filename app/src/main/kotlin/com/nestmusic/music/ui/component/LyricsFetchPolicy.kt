/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.component

/**
 * When missing lyrics should be fetched. Extracted as pure logic so the regression that broke
 * the Apple Music player (its lyrics pane rendered [Lyrics] without ever triggering a fetch)
 * stays covered by a JVM unit test.
 */
object LyricsFetchPolicy {
    fun shouldFetch(
        enabled: Boolean,
        mediaId: String?,
        hasCurrentLyrics: Boolean,
    ): Boolean = enabled && !mediaId.isNullOrBlank() && !hasCurrentLyrics
}
