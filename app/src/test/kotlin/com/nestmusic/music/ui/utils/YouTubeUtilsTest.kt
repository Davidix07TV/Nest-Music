package com.nestmusic.music.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Characterisation tests for [resize]: they pin the URL shapes the app actually emits today — the
 * upstream InnerTune/Metrolist ones, which are what every installed build has been serving. They
 * deliberately do not encode an idealised thumbnail format: if one of these starts failing, the
 * production regex changed and that has to be a decision, not a way to make a test green.
 */
class YouTubeUtilsTest {

    @Test
    fun `lh3 googleusercontent is rewritten to the requested size`() {
        val url = "https://lh3.googleusercontent.com/abc=w120-h120-l90-rj"
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w544-h544-p-l90-rj",
            url.resize(544, 544),
        )
    }

    @Test
    fun `yt3 googleusercontent is rewritten too (YouTube migrated host - fixes the blurry player)`() {
        // YT moved album art to yt3.googleusercontent.com and serves a ~60px thumbnail; without
        // matching this host, resize() would no-op and the player upscales the 60px image (blur).
        val url = "https://yt3.googleusercontent.com/_zDuDZFnSKmuQwDX=w60-h60-l90-rj"
        assertEquals(
            "https://yt3.googleusercontent.com/_zDuDZFnSKmuQwDX=w544-h544-p-l90-rj",
            url.resize(544, 544),
        )
    }

    @Test
    fun `the p suffix and the l90-rj tail are kept as upstream emits them`() {
        val url = "https://lh3.googleusercontent.com/abc=w680-h383-l90-rj"
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w544-h544-p-l90-rj",
            url.resize(544, 544),
        )
    }

    @Test
    fun `a single dimension scales through integer division of the source ratio`() {
        // Lossy by construction: (544 / 120) * 120 is 480, not 544. Pinned so a future rewrite of
        // the aspect math is visible as a behaviour change instead of a silent fix.
        val url = "https://lh3.googleusercontent.com/abc=w120-h120-l90-rj"
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w544-h480-p-l90-rj",
            url.resize(width = 544),
        )
    }

    @Test
    fun `yt3 ggpht avatars keep the =s size syntax`() {
        val url = "https://yt3.ggpht.com/abc=s88"
        assertEquals("https://yt3.ggpht.com/abc=s88-s544", url.resize(544, 544))
    }

    @Test
    fun `i_ytimg urls are left alone - no maxres upgrade is implemented`() {
        val url = "https://i.ytimg.com/vi/abc/hqdefault.jpg?sqp=-oaymwE"
        assertEquals(url, url.resize(544, 544))
    }

    @Test
    fun `null dimensions return the url unchanged`() {
        val url = "https://yt3.googleusercontent.com/abc=w60-h60-l90-rj"
        assertEquals(url, url.resize())
    }
}
