/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.utils

import kotlinx.serialization.json.Json
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ListenBrainzPayloadTest {
    private val json = Json

    private fun encode(listenType: String, listenedAt: Long?): String {
        val submission = ListenBrainz.ListenSubmission(
            listen_type = listenType,
            payload = listOf(
                ListenBrainz.Listen(
                    listened_at = listenedAt,
                    track_metadata = ListenBrainz.TrackMetadata(
                        artist_name = "Artist",
                        track_name = "Track",
                        release_name = "Album",
                        additional_info = ListenBrainz.AdditionalInfo(
                            duration_ms = 240_000,
                            media_player = "Nest Music",
                            submission_client = "Nest Music",
                        ),
                    ),
                ),
            ),
        )
        return json.encodeToString(ListenBrainz.ListenSubmission.serializer(), submission)
    }

    @Test
    fun `playing now payload omits listened_at`() {
        val body = encode("playing_now", listenedAt = null)
        assertTrue(body.contains("\"listen_type\":\"playing_now\""))
        assertFalse("playing_now must not carry a timestamp", body.contains("listened_at"))
        assertTrue(body.contains("\"artist_name\":\"Artist\""))
        assertTrue(body.contains("\"track_name\":\"Track\""))
    }

    @Test
    fun `single listen carries timestamp and metadata`() {
        val body = encode("single", listenedAt = 1_700_000_000L)
        assertTrue(body.contains("\"listen_type\":\"single\""))
        assertTrue(body.contains("\"listened_at\":1700000000"))
        assertTrue(body.contains("\"release_name\":\"Album\""))
        assertTrue(body.contains("\"duration_ms\":240000"))
        assertTrue(body.contains("\"submission_client\":\"Nest Music\""))
    }

    @Test
    fun `null optional fields are omitted`() {
        val submission = ListenBrainz.ListenSubmission(
            listen_type = "single",
            payload = listOf(
                ListenBrainz.Listen(
                    listened_at = 1L,
                    track_metadata = ListenBrainz.TrackMetadata(
                        artist_name = "A",
                        track_name = "T",
                    ),
                ),
            ),
        )
        val body = json.encodeToString(ListenBrainz.ListenSubmission.serializer(), submission)
        assertFalse(body.contains("release_name"))
        assertFalse(body.contains("additional_info"))
    }
}
