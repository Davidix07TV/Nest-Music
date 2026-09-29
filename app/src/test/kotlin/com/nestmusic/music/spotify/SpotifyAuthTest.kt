/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.spotify

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyAuthTest {

    // ── authorize URL ──────────────────────────────────────────────────

    @Test
    fun `authorize url carries every pkce parameter`() {
        val url = SpotifyAuth.authorizeUrl("client-123", "state-abc", "challenge-xyz")
        assertTrue(url.startsWith("https://accounts.spotify.com/authorize?"))
        assertTrue(url.contains("response_type=code"))
        assertTrue(url.contains("client_id=client-123"))
        assertTrue(url.contains("code_challenge_method=S256"))
        assertTrue(url.contains("code_challenge=challenge-xyz"))
        assertTrue(url.contains("state=state-abc"))
    }

    /**
     * The redirect URI contains `://` and a host. If it is not escaped, the
     * `&` and `://` split the query and Spotify never sees the URI it was
     * registered with, and the redirect silently comes back to the wrong
     * place. Decoding it back out of the query is the check that matters.
     */
    @Test
    fun `redirect uri survives the query round trip`() {
        val url = SpotifyAuth.authorizeUrl("id", "s", "c")
        val encoded = Regex("redirect_uri=([^&]+)").find(url)!!.groupValues[1]
        val decoded = java.net.URLDecoder.decode(encoded, "UTF-8")
        assertEquals(SpotifyAuth.REDIRECT_URI, decoded)
    }

    @Test
    fun `scopes are escaped rather than sent as separate parameters`() {
        val url = SpotifyAuth.authorizeUrl("id", "s", "c")
        val encoded = Regex("scope=([^&]+)").find(url)!!.groupValues[1]
        // three scopes, three spaces — if they were raw the query would break
        assertEquals(3, java.net.URLDecoder.decode(encoded, "UTF-8").split(" ").size)
    }

    // ── callback parsing ───────────────────────────────────────────────

    @Test
    fun `parses a successful callback`() {
        val cb = SpotifyAuth.parseCallback("nestmusic://spotify/callback?code=abc123&state=xyz")
        assertEquals("abc123", cb.code)
        assertEquals("xyz", cb.state)
        assertNull(cb.error)
    }

    @Test
    fun `parses a denied callback`() {
        val cb = SpotifyAuth.parseCallback(
            "nestmusic://spotify/callback?error=access_denied&state=xyz"
        )
        assertNull(cb.code)
        assertEquals("access_denied", cb.error)
    }

    @Test
    fun `decodes an escaped state`() {
        val cb = SpotifyAuth.parseCallback(
            "nestmusic://spotify/callback?code=a&state=a%2Bb%2Fc%3D"
        )
        assertEquals("a+b/c=", cb.state)
    }

    @Test
    fun `a callback with no query yields nothing rather than throwing`() {
        val cb = SpotifyAuth.parseCallback("nestmusic://spotify/callback")
        assertNull(cb.code)
        assertNull(cb.state)
        assertNull(cb.error)
    }

    // ── token response ─────────────────────────────────────────────────

    @Test
    fun `parses a token response`() {
        val token = SpotifyAuth.parseTokenResponse(
            """{"access_token":"tok","token_type":"Bearer","scope":"user-read-email",
               "expires_in":3600,"refresh_token":"ref"}"""
        )
        assertNotNull(token)
        assertEquals("tok", token!!.accessToken)
        assertEquals("ref", token.refreshToken)
        assertEquals(3600L, token.expiresInSeconds)
    }

    @Test
    fun `tolerates a response with no refresh token`() {
        val token = SpotifyAuth.parseTokenResponse("""{"access_token":"tok","expires_in":60}""")
        assertEquals("tok", token?.accessToken)
        assertNull(token?.refreshToken)
    }

    @Test
    fun `returns null for an error body or for garbage`() {
        assertNull(SpotifyAuth.parseTokenResponse("""{"error":"invalid_grant"}"""))
        assertNull(SpotifyAuth.parseTokenResponse("not json at all"))
        assertNull(SpotifyAuth.parseTokenResponse(""))
    }

    // ── PKCE ───────────────────────────────────────────────────────────

    @Test
    fun `verifier and challenge are distinct and fresh each time`() {
        val a = SpotifyAuth.generatePkcePair()
        val b = SpotifyAuth.generatePkcePair()
        assertNotEquals(a.verifier, a.challenge)
        assertNotEquals(a.verifier, b.verifier)
        assertTrue(a.verifier.length >= 43)
        assertTrue(a.challenge.length >= 43)
    }

    @Test
    fun `challenge is url safe and unpadded`() {
        val pkce = SpotifyAuth.generatePkcePair()
        for (value in listOf(pkce.verifier, pkce.challenge, SpotifyAuth.generateState())) {
            assertTrue(
                "unsafe character in $value",
                value.none { it == '+' || it == '/' || it == '=' }
            )
        }
    }
}
