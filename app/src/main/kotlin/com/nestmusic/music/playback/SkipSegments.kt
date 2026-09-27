/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.playback

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import timber.log.Timber

/**
 * A skip-able time range inside a video, in milliseconds.
 */
data class SkipSegment(
    val category: String,
    val startMs: Long,
    val endMs: Long,
) {
    fun contains(positionMs: Long): Boolean = positionMs in startMs until endMs
}

/**
 * Categories that can be skipped, in the order they are shown in settings.
 * Ids come from the segment API; labels are resolved in the settings screen.
 */
val SKIP_CATEGORIES = listOf(
    "sponsor",
    "intro",
    "outro",
    "selfpromo",
    "interaction",
    "music_offtopic",
    "filler",
    "preview",
)

/**
 * Categories skipped out of the box: the ones that cover non-music parts of a video.
 */
val DEFAULT_SKIP_CATEGORIES = setOf(
    "sponsor",
    "intro",
    "outro",
    "selfpromo",
    "interaction",
    "music_offtopic",
)

private const val TAG = "SkipSegments"

@Serializable
private data class RawSegment(
    val category: String = "",
    val segment: List<Double> = emptyList(),
)

/**
 * Fetches community-maintained skip ranges for a video id and converts them
 * into [SkipSegment]s. Responses are cached in memory for the lifetime of the
 * process (positive and negative results alike) so a song that comes back
 * around never triggers the same request twice.
 */
object SkipSegmentsRepository {
    private const val ENDPOINT = "https://sponsor.ajay.app/api/skipSegments"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 8_000
                connectTimeoutMillis = 5_000
                socketTimeoutMillis = 8_000
            }
            expectSuccess = false
        }
    }

    private val cache: MutableMap<String, List<SkipSegment>> =
        java.util.Collections.synchronizedMap(LinkedHashMap<String, List<SkipSegment>>())

    /**
     * Returns the skip segments for [videoId], filtered to [categories].
     * Empty result (including API 404 = "nothing submitted for this video")
     * is cached as well. Never throws; network/parse failures yield an empty
     * list so playback is never affected.
     */
    suspend fun fetch(videoId: String, categories: Set<String>): List<SkipSegment> {
        if (videoId.isEmpty() || categories.isEmpty()) return emptyList()
        cache[videoId]?.let { return it.filter { segment -> segment.category in categories } }
        val segments = fetchUncached(videoId)
        if (cache.size >= 200) cache.clear() // crude bound; entries are tiny
        cache[videoId] = segments
        return segments.filter { it.category in categories }
    }

    private suspend fun fetchUncached(videoId: String): List<SkipSegment> = try {
        val response = client.get(ENDPOINT) {
            parameter("videoID", videoId)
            parameter("categories", categoriesParam(DEFAULT_SKIP_CATEGORIES + SKIP_CATEGORIES))
        }
        if (response.status != HttpStatusCode.OK) {
            Timber.tag(TAG).d("no segments for $videoId (HTTP ${response.status})")
            emptyList()
        } else {
            parse(response.bodyAsText())
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        // Never swallow cancellation: the caller's scope is going away.
        throw e
    } catch (e: Exception) {
        Timber.tag(TAG).d(e, "failed to fetch segments for $videoId")
        emptyList()
    }

    /**
     * Turns a raw API payload into milliseconds-based [SkipSegment]s,
     * dropping malformed ranges.
     */
    fun parse(body: String): List<SkipSegment> = try {
        json.decodeFromString<List<RawSegment>>(body).mapNotNull { raw ->
            val start = raw.segment.getOrNull(0) ?: return@mapNotNull null
            val end = raw.segment.getOrNull(1) ?: return@mapNotNull null
            if (end <= start || start < 0) return@mapNotNull null
            SkipSegment(
                category = raw.category,
                startMs = (start * 1000).toLong(),
                endMs = (end * 1000).toLong(),
            )
        }
    } catch (e: Exception) {
        Timber.tag(TAG).d(e, "failed to parse segment payload")
        emptyList()
    }

    /**
     * The segment covering [positionMs], or null when playback is outside
     * every skip range.
     */
    fun findActive(positionMs: Long, segments: List<SkipSegment>): SkipSegment? =
        segments.firstOrNull { it.contains(positionMs) }

    private fun categoriesParam(categories: Set<String>): String =
        categories.joinToString(separator = ",", prefix = "[", postfix = "]") { "\"$it\"" }
}
