/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.utils

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * Minimal ListenBrainz submit client: one endpoint, token auth, JSON body.
 * Kept independent from Last.fm so scrobbling works with either service.
 */
object ListenBrainz {
    private const val TAG = "ListenBrainz"
    private const val ENDPOINT = "https://listenbrainz.org/1/submit-listens"
    private const val CLIENT_NAME = "Nest Music"

    /** User token from settings; submissions are skipped while it is blank. */
    var token: String? = null

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 10_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 10_000
            }
            expectSuccess = false
        }
    }

    @Serializable
    data class AdditionalInfo(
        val duration_ms: Long? = null,
        val media_player: String? = null,
        val submission_client: String? = null,
    )

    @Serializable
    data class TrackMetadata(
        val artist_name: String,
        val track_name: String,
        val release_name: String? = null,
        val additional_info: AdditionalInfo? = null,
    )

    @Serializable
    data class Listen(
        val listened_at: Long? = null, // omitted for playing_now
        val track_metadata: TrackMetadata,
    )

    @Serializable
    data class ListenSubmission(
        val listen_type: String,
        val payload: List<Listen>,
    )

    /** A completed play, anchored to [timestamp] (unix seconds). */
    suspend fun scrobble(
        artist: String,
        track: String,
        timestamp: Long,
        album: String? = null,
        durationSeconds: Int? = null,
    ) = submit(
        listenType = "single",
        listenedAt = timestamp,
        artist = artist,
        track = track,
        album = album,
        durationSeconds = durationSeconds,
    )

    /** The track that is playing right now (no timestamp allowed). */
    suspend fun submitNowPlaying(
        artist: String,
        track: String,
        album: String? = null,
        durationSeconds: Int? = null,
    ) = submit(
        listenType = "playing_now",
        listenedAt = null,
        artist = artist,
        track = track,
        album = album,
        durationSeconds = durationSeconds,
    )

    private suspend fun submit(
        listenType: String,
        listenedAt: Long?,
        artist: String,
        track: String,
        album: String?,
        durationSeconds: Int?,
    ) = runCatching {
        val authToken = token?.trim().orEmpty()
        if (authToken.isEmpty()) return@runCatching
        val submission = ListenSubmission(
            listen_type = listenType,
            payload = listOf(
                Listen(
                    listened_at = listenedAt,
                    track_metadata = TrackMetadata(
                        artist_name = artist,
                        track_name = track,
                        release_name = album,
                        additional_info = AdditionalInfo(
                            duration_ms = durationSeconds?.times(1000L),
                            media_player = CLIENT_NAME,
                            submission_client = CLIENT_NAME,
                        ),
                    ),
                ),
            ),
        )
        val response = client.post(ENDPOINT) {
            header("Authorization", "Token $authToken")
            header("Content-Type", "application/json")
            header("User-Agent", CLIENT_NAME)
            setBody(json.encodeToString(submission))
        }
        if (response.status != HttpStatusCode.OK) {
            Timber.tag(TAG).w(
                "submission failed ($listenType): HTTP ${response.status} ${response.bodyAsText().take(200)}",
            )
        }
    }
}
