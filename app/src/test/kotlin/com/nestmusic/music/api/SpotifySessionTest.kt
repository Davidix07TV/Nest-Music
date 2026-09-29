/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifySessionTest {

    @Test
    fun `reads sp_dc from a single jar`() {
        val jars = listOf("sp_tdid=abc; sp_dc=AbCd1234; ttae=1")
        assertEquals("AbCd1234", SpotifySession.extractSpDc(jars))
    }

    /**
     * The regression. The accounts.spotify.com jar is non-empty from the moment
     * the login page loads (sp_tdid, __cf_bm, login_ticket…), so a search that
     * settles for the first non-empty jar never reaches the open.spotify.com one
     * and reports no session even though sp_dc is right there.
     */
    @Test
    fun `keeps searching when the first jar has cookies but no sp_dc`() {
        val jars = listOf(
            "__cf_bm=xyz; sp_tdid=abc; login_ticket=zzz",
            "sp_dc=realSessionToken; sp_tdid=abc",
        )
        assertEquals("realSessionToken", SpotifySession.extractSpDc(jars))
    }

    @Test
    fun `prefers the earlier jar when several carry sp_dc`() {
        val jars = listOf("sp_dc=first", "sp_dc=second")
        assertEquals("first", SpotifySession.extractSpDc(jars))
    }

    @Test
    fun `trims the value`() {
        assertEquals("AbCd1234", SpotifySession.extractSpDc(listOf("sp_dc= AbCd1234 ; ttae=1")))
    }

    @Test
    fun `ignores a blank sp_dc and keeps looking`() {
        val jars = listOf("sp_dc=   ; ttae=1", "sp_dc=realSessionToken")
        assertEquals("realSessionToken", SpotifySession.extractSpDc(jars))
    }

    @Test
    fun `returns null when no jar carries sp_dc`() {
        val jars = listOf("sp_tdid=abc; ttae=1", "__cf_bm=xyz")
        assertNull(SpotifySession.extractSpDc(jars))
    }

    /** CookieManager returns null for a host it has never seen. */
    @Test
    fun `tolerates null jars`() {
        val jars = listOf(null, "sp_dc=realSessionToken")
        assertEquals("realSessionToken", SpotifySession.extractSpDc(jars))
        assertNull(SpotifySession.extractSpDc(listOf(null, null)))
    }

    @Test
    fun `does not match a cookie whose name merely ends in sp_dc`() {
        assertNull(SpotifySession.extractSpDc(listOf("xsp_dc=nope; notsp_dc=alsoNo")))
    }
}
