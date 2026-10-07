/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseVersionTest {
    @Test
    fun `version comes from the tag, not from the release title`() {
        // Regression: the release *name* used to be read as the version, so a release titled
        // "🎵 Nest Music v1.0.9 - Sunset, Night and Aurora" was compared as 0.0.0 and nobody ever
        // saw the update prompt.
        val sunset = "🎵 Nest Music v1.0.9 - Sunset, Night and Aurora"

        assertEquals("1.0.10", ReleaseVersion.fromRelease("v1.0.10", sunset))
        assertEquals("1.0.9", ReleaseVersion.fromRelease("v1.0.9", sunset))
    }

    @Test
    fun `a newer tag is detected as an update`() {
        val latest = ReleaseVersion.fromRelease("v1.0.10", "1.0.10")
        val current = "1.0.9"

        assertTrue(ReleaseVersion.compare(latest, current) > 0)
        assertFalse(ReleaseVersion.compare(current, latest) > 0)
    }

    @Test
    fun `titles are only a fallback when the tag carries no digits`() {
        assertEquals("1.0.5", ReleaseVersion.fromRelease("nightly", "🎵 Nest Music v1.0.5 - Sunset Interface"))
        assertEquals("nightly", ReleaseVersion.fromRelease("nightly", "Nest Music"))
    }

    @Test
    fun `suffixes on tags do not break comparisons`() {
        assertEquals(0, ReleaseVersion.compare("v1.0.6-lossless", "1.0.6"))
        assertEquals(1, ReleaseVersion.compare("1.0.6-lossless", "1.0.5"))
    }

    @Test
    fun `components are ordered numerically and missing ones count as zero`() {
        assertEquals(1, ReleaseVersion.compare("1.0.10", "1.0.9"))
        assertEquals(-1, ReleaseVersion.compare("1.0.9", "1.0.10"))
        assertEquals(0, ReleaseVersion.compare("1.0", "1.0.0"))
        assertEquals(1, ReleaseVersion.compare("v2.0.0", "1.9.9"))
    }
}
