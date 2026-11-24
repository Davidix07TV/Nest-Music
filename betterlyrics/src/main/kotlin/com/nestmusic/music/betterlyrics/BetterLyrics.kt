package com.nestmusic.music.betterlyrics

import com.nestmusic.music.betterlyrics.models.TTMLResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import timber.log.Timber

object BetterLyrics {
    private const val TAG = "BetterLyrics"
    private val client by lazy {
        HttpClient(CIO) {
            install(ContentNegotiation) {
                json(
                    Json {
                        isLenient = true
                        ignoreUnknownKeys = true
                    },
                )
            }

            install(HttpTimeout) {
                requestTimeoutMillis = 15000
                connectTimeoutMillis = 10000
                socketTimeoutMillis = 15000
            }

            defaultRequest {
                url("https://lyrics-api.boidu.dev")
                headers {
                    append("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    append("Accept", "application/json")
                }
            }

            expectSuccess = false
        }
    }

    private data class QueryCandidate(
        val artist: String,
        val title: String,
        val duration: Int,
        val album: String?,
    )

    private val parentheticalNoiseRegex =
        Regex("""\s*[\(\[](?:official\s*(?:music\s*)?video|visualizer|lyric\s*video|audio|hd|4k|remaster(?:ed)?(?:\s*\d{4})?)[\)\]]""", RegexOption.IGNORE_CASE)
    private val artistSplitRegex =
        Regex("""\s*(?:,|&|\bfeat\.?|\bft\.?|\bx\b)\s*""", RegexOption.IGNORE_CASE)

    private suspend fun fetchTTML(
        artist: String,
        title: String,
        duration: Int = -1,
        album: String? = null,
    ): String? = runCatching {
        Timber.tag(TAG).d("Fetching TTML for: $title by $artist (dur=$duration, album=$album)")
        val response = client.get("/getLyrics") {
            parameter("s", title)
            parameter("a", artist)
            if (duration > 0) {
                parameter("d", duration)
            }
            if (!album.isNullOrBlank()) {
                parameter("al", album)
            }
        }
        if (response.status == HttpStatusCode.OK) {
            val ttml = response.body<TTMLResponse>().ttml?.trim()?.takeIf { it.isNotEmpty() }
            ttml
        } else {
            Timber.tag(TAG).w("API returned status: ${response.status}")
            null
        }
    }.getOrElse { e ->
        Timber.tag(TAG).e(e, "Exception during fetchTTML")
        null
    }

    private suspend fun fetchTTMLWithFallbacks(
        artist: String,
        title: String,
        duration: Int,
        album: String?,
    ): String? {
        val cleanTitle = title.replace(parentheticalNoiseRegex, "").trim().ifEmpty { title.trim() }
        val primaryArtist = artist.split(artistSplitRegex).firstOrNull()?.trim().orEmpty().ifEmpty { artist.trim() }
        val cleanAlbum = album?.trim()?.takeIf { it.isNotEmpty() && !it.equals(title, ignoreCase = true) }

        val candidates = LinkedHashSet<QueryCandidate>().apply {
            if (!cleanAlbum.isNullOrEmpty()) {
                add(QueryCandidate(artist.trim(), title.trim(), duration, cleanAlbum))
            }
            add(QueryCandidate(artist.trim(), title.trim(), duration, null))
            if (primaryArtist != artist.trim() || cleanTitle != title.trim()) {
                add(QueryCandidate(primaryArtist, cleanTitle, duration, null))
            }
            if (duration > 0) {
                add(QueryCandidate(primaryArtist, cleanTitle, -1, null))
            }
        }

        for (candidate in candidates) {
            val ttml = fetchTTML(
                artist = candidate.artist,
                title = candidate.title,
                duration = candidate.duration,
                album = candidate.album,
            )
            if (!ttml.isNullOrEmpty()) return ttml
        }
        return null
    }

    suspend fun getLyrics(
        title: String,
        artist: String,
        duration: Int,
        album: String? = null,
    ) = runCatching {
        val ttml =
            fetchTTMLWithFallbacks(artist, title, duration, album)
                ?: throw IllegalStateException("Lyrics unavailable")

        val parsedLines = TTMLParser.parseTTML(ttml)
        if (parsedLines.isEmpty()) {
            throw IllegalStateException("Failed to parse lyrics")
        }
        
        TTMLParser.toLRC(parsedLines)
    }
}
