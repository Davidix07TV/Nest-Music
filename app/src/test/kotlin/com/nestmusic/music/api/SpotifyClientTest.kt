/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.api

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SpotifyClientTest {
    private fun parse(raw: String): JsonObject =
        Json.parseToJsonElement(raw) as JsonObject

    private fun nextDataOf(json: String): JsonObject =
        SpotifyClient.extractNextData("""<script id="__NEXT_DATA__" type="application/json">$json</script>""")

    private val embedHtml = """
        <html><head></head><body>
        <script id="__NEXT_DATA__" type="application/json">{"props":{"pageProps":{"state":{
          "settings":{"session":{"accessToken":"tok-123","accessTokenExpirationTimestampMs":1790000000000}},
          "data":{"entity":{"name":"My Mix","subtitle":"Someone","uri":"spotify:playlist:abc",
            "trackList":[
              {"entityType":"track","uri":"spotify:track:1111111111111111111111","title":"Song A","subtitle":"Artist A, Artist B","duration":200000},
              {"entityType":"playlist","uri":"spotify:playlist:2222222222222222222222","title":"Not a track"},
              {"entityType":"track","uri":"spotify:track:3333333333333333333333","title":"Song B","subtitle":"Artist C","duration":180000}
            ]}
        }}}}}
        </script></body></html>
    """.trimIndent()

    private val playlistPage = """
        {"data":{"playlistV2":{
          "uri":"spotify:playlist:abc","name":"My Mix","description":"",
          "content":{"totalCount":3,"items":[
            {"itemV2":{"data":{"__typename":"Track","uri":"spotify:track:1111111111111111111111",
              "name":"Song A","duration":{"totalMilliseconds":200000},
              "artists":{"items":[{"profile":{"name":"Artist A"}},{"profile":{"name":"Artist B"}}]}}}},
            {"itemV2":{"data":{"__typename":"Track","uri":"spotify:track:3333333333333333333333",
              "name":"Song B","duration":{"totalMilliseconds":180000},
              "artists":{"items":[{"profile":{"name":"Artist C"}}]}}}},
            {"itemV2":{"data":{"__typename":"Song","uri":"spotify:local:x","name":"Local file"}}}
          ]}
        }}}
    """.trimIndent()

    // __NEXT_DATA__ extraction
    @Test
    fun `extractNextData pulls the json payload`() {
        assertNotNull(SpotifyClient.extractNextData(embedHtml))
    }

    @Test(expected = SpotifyException::class)
    fun `extractNextData rejects pages without payload`() {
        SpotifyClient.extractNextData("<html></html>")
    }

    // Input parsing
    @Test
    fun `playlist id extracted from urls uris and bare ids`() {
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyClient.playlistIdFromInput("https://open.spotify.com/playlist/37i9dQZF1DXcBWIGoYBM5M?si=abc"),
        )
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyClient.playlistIdFromInput("https://open.spotify.com/intl-it/playlist/37i9dQZF1DXcBWIGoYBM5M"),
        )
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyClient.playlistIdFromInput("spotify:playlist:37i9dQZF1DXcBWIGoYBM5M"),
        )
        assertEquals(
            "37i9dQZF1DXcBWIGoYBM5M",
            SpotifyClient.playlistIdFromInput("37i9dQZF1DXcBWIGoYBM5M"),
        )
        assertNull(SpotifyClient.playlistIdFromInput("https://example.com/playlist/short"))
    }

    // Session
    @Test
    fun `embedSession returns token and expiry`() {
        val session = SpotifyClient.embedSession(SpotifyClient.extractNextData(embedHtml))
        assertNotNull(session)
        assertEquals("tok-123", session!!.first)
        assertEquals(1_790_000_000_000L, session.second)
    }

    @Test
    fun `embedSession is null without session block`() {
        assertNull(SpotifyClient.embedSession(nextDataOf("""{"props":{"pageProps":{"state":{}}}}""")))
    }

    // Pathfinder playlist page
    @Test
    fun `parsePlaylistPage reads tracks and skips non-track rows`() {
        val body = parse(playlistPage)
        val parsed = SpotifyClient.parsePlaylistPage(body)
        assertNotNull(parsed)
        assertEquals("My Mix", parsed!!.first)
        assertEquals(2, parsed.second.size)
        val songA = parsed.second[0]
        assertEquals("Song A", songA.title)
        assertEquals(listOf("Artist A", "Artist B"), songA.artists)
        assertEquals(200_000L, songA.durationMs)
        assertEquals(3, SpotifyClient.playlistTotalCount(body))
    }

    // Embed fallback entity
    @Test
    fun `parseEmbedPlaylist reads track list and skips foreign rows`() {
        val playlist = SpotifyClient.parseEmbedPlaylist(SpotifyClient.extractNextData(embedHtml))
        assertNotNull(playlist)
        assertEquals("My Mix", playlist!!.name)
        assertEquals(2, playlist.tracks.size)
        assertEquals("Song A", playlist.tracks[0].title)
        assertEquals(listOf("Artist A", "Artist B"), playlist.tracks[0].artists)
        assertEquals(200_000L, playlist.tracks[0].durationMs)
    }

    @Test
    fun `parseEmbedPlaylist reports unavailable pages as null`() {
        val nextData = nextDataOf("""{"props":{"pageProps":{"status":404}}}""")
        assertNull(SpotifyClient.parseEmbedPlaylist(nextData))
    }
}
