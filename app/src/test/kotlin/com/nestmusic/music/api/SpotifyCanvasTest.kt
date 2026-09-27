/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpotifyCanvasTest {
    private fun parse(raw: String): JsonObject =
        Json.parseToJsonElement(raw) as JsonObject

    // TOTP: vectors computed with an independent RFC 6238 implementation
    // using Spotify's key obfuscation (char XOR index%33+9, decimals).
    @Test
    fun `totp key derivation matches the obfuscation scheme`() {
        // "1" (0x31) at index 0 → 49 ^ 9 = 56 → "56"
        assertArrayEquals(
            "56".toByteArray(),
            SpotifyCanvas.totpKey("1"),
        )
        // Known key prefix for the newest embedded secret
        val key = String(SpotifyCanvas.totpKey(",7/*F(\"rLJ2oxaKL^f+E1xvP@N"))
        assertEquals("37613638753845989388331231091199284711244889441021", key.take(50))
    }

    @Test
    fun `totp vectors for all embedded secret versions`() {
        val vectors = mapOf(
            61 to listOf(
                0L to "204513",
                30L to "332823",
                1_700_000_000L to "371599",
                1_760_000_009L to "934995",
            ),
            60 to listOf(
                0L to "360407",
                30L to "390335",
                1_700_000_000L to "834657",
                1_760_000_009L to "249121",
            ),
            59 to listOf(
                0L to "609816",
                30L to "921688",
                1_700_000_000L to "419531",
                1_760_000_009L to "652987",
            ),
        )
        for ((version, secret) in SpotifyCanvas.TOTP_SECRETS) {
            for ((timestamp, expected) in vectors.getValue(version)) {
                assertEquals(
                    "version=$version ts=$timestamp",
                    expected,
                    SpotifyCanvas.generateTotp(secret, timestamp),
                )
            }
        }
    }

    @Test
    fun `totp codes are always six digits`() {
        val code = SpotifyCanvas.generateTotp(SpotifyCanvas.TOTP_SECRETS.first().second, 12345L)
        assertEquals(6, code.length)
        assertTrue(code.all { it in '0'..'9' })
    }

    // Canvas response parsing
    @Test
    fun `parseCanvasUrl reads the video url`() {
        val body = parse(
            """{"data":{"trackUnion":{"canvas":{
                "url":"https://canvaz.scdn.co/upload/x/video/abc.cnvs.mp4",
                "uri":"spotify:canvas:1"}}}}""",
        )
        assertEquals(
            "https://canvaz.scdn.co/upload/x/video/abc.cnvs.mp4",
            SpotifyCanvas.parseCanvasUrl(body),
        )
    }

    @Test
    fun `parseCanvasUrl returns null when the track has no canvas`() {
        assertNull(SpotifyCanvas.parseCanvasUrl(parse("""{"data":{"trackUnion":{}}}""")))
        assertNull(SpotifyCanvas.parseCanvasUrl(parse("""{"data":{}}""")))
    }

    // Search response parsing
    @Test
    fun `parseSearchTrackCandidates extracts uris and durations`() {
        val body = parse(
            """{"data":{"searchV2":{"tracksV2":{"items":[
                {"item":{"data":{"uri":"spotify:track:aaaaaaaaaaaaaaaaaaaaaa",
                  "name":"X","duration":{"totalMilliseconds":200000}}}},
                {"item":{"data":{"uri":"spotify:playlist:bbbbbbbbbbbbbbbbbbbbbb",
                  "name":"Not a track"}}},
                {"item":{"data":{"uri":"spotify:track:cccccccccccccccccccccc",
                  "name":"Y","duration":{"totalMilliseconds":185000}}}}
            ]}}}}""",
        )
        val candidates = SpotifyCanvas.parseSearchTrackCandidates(body)
        assertEquals(2, candidates.size)
        assertEquals("spotify:track:aaaaaaaaaaaaaaaaaaaaaa", candidates[0].first)
        assertEquals(200_000L, candidates[0].second)
        assertEquals(185_000L, candidates[1].second)
    }

    @Test
    fun `parseSearchTrackCandidates tolerates missing sections`() {
        assertEquals(
            emptyList<Pair<String, Long>>(),
            SpotifyCanvas.parseSearchTrackCandidates(parse("""{"data":{"searchV2":{}}}""")),
        )
        assertEquals(
            emptyList<Pair<String, Long>>(),
            SpotifyCanvas.parseSearchTrackCandidates(parse("""{"errors":[]}""")),
        )
    }
}
