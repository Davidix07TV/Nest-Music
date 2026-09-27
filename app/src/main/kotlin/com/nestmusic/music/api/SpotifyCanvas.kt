/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.api

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.put
import timber.log.Timber
import java.net.URLEncoder
import java.nio.ByteBuffer
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * Resolves looping Canvas videos for the artwork carousel.
 *
 * Flow: match the playing song against Spotify's search, ask for the
 * track's Canvas, cache the answer. Needs the user's `sp_dc` cookie for
 * the token exchange; every failure is swallowed — Canvas is decorative,
 * so playback must never notice a missing video.
 */
object SpotifyCanvas {
    private const val TAG = "SpotifyCanvas"

    private const val SERVER_TIME_URL = "https://open.spotify.com/api/server-time"
    private const val TOKEN_URL = "https://open.spotify.com/api/token"
    private const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v1/query"
    private const val SEARCH_OPERATION = "searchDesktop"
    private const val SEARCH_HASH =
        "eff59fa0a3d026b88b56fddbcf4bdfa16a186b8175a5c1a358c072e053c2e5b0"
    private const val CANVAS_OPERATION = "canvas"
    private const val CANVAS_HASH =
        "575138ab27cd5c1b3e54da54d0a7cc8d85485402de26340c2145f0f6bb5e7a9f"
    private const val TOKEN_SKEW_MS = 60_000L

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"

    /**
     * (version, secret) pairs tried newest-first; Spotify keeps a few grace
     * versions alive at once and retires the rest on rotation.
     */
    internal val TOTP_SECRETS = listOf(
        61 to ",7/*F(\"rLJ2oxaKL^f+E1xvP@N",
        60 to "OmE{ZA.J^\":0FG\\Uz?[@WW",
        59 to "{iOFn;4}<1PFYKPV?5{%u14]M>/V0hDH",
    )

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 12_000
                connectTimeoutMillis = 6_000
                socketTimeoutMillis = 12_000
            }
            expectSuccess = false
        }
    }

    @Volatile
    private var bearerToken: String? = null
    @Volatile
    private var tokenExpiresAtMs: Long = 0L
    @Volatile
    private var tokenSpDc: String? = null

    private val canvasCache: MutableMap<String, String?> =
        java.util.Collections.synchronizedMap(LinkedHashMap())

    // ------------------------------------------------------------------
    // TOTP (RFC 6238 with Spotify's key obfuscation)
    // ------------------------------------------------------------------

    /** HMAC key: each secret char XORed with `index % 33 + 9`, then decimals. */
    internal fun totpKey(secret: String): ByteArray =
        secret.mapIndexed { index, c ->
            (c.code xor (index % 33 + 9)).toString()
        }.joinToString(separator = "").toByteArray(Charsets.UTF_8)

    /** Six-digit code for [timestampSeconds] (30-second window). */
    internal fun generateTotp(secret: String, timestampSeconds: Long): String {
        val counter = timestampSeconds / 30
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(totpKey(secret), "HmacSHA1"))
        val digest = mac.doFinal(ByteBuffer.allocate(8).putLong(counter).array())
        val offset = digest[digest.size - 1].toInt() and 0x0F
        val code = ByteBuffer.wrap(digest, offset, 4).int and 0x7FFFFFFF
        return (code % 1_000_000).toString().padStart(6, '0')
    }

    // ------------------------------------------------------------------
    // Response parsing (unit-tested)
    // ------------------------------------------------------------------

    /** `data.trackUnion.canvas.url` of a canvas operation, or null. */
    internal fun parseCanvasUrl(body: JsonObject): String? {
        val union = body.path("data", "trackUnion") as? JsonObject ?: return null
        val canvas = union["canvas"] as? JsonObject ?: return null
        val url = (canvas["url"] as? JsonPrimitive)?.contentOrNull
        return url?.takeIf { it.isNotEmpty() }
    }

    /** Candidate `spotify:track:` uris with durations from a search body. */
    internal fun parseSearchTrackCandidates(body: JsonObject): List<Pair<String, Long>> {
        val searchV2 = body.path("data", "searchV2") as? JsonObject ?: return emptyList()
        val tracksV2 = searchV2["tracksV2"] as? JsonObject ?: return emptyList()
        val items = tracksV2["items"] as? JsonArray ?: return emptyList()
        return items.mapNotNull { element ->
            val wrapper = element as? JsonObject ?: return@mapNotNull null
            val data = wrapper.path("item", "data") as? JsonObject ?: return@mapNotNull null
            val uri = (data["uri"] as? JsonPrimitive)?.contentOrNull
                ?: return@mapNotNull null
            if (!uri.startsWith("spotify:track:")) return@mapNotNull null
            val duration = data.path("duration", "totalMilliseconds")
            val durationMs = (duration as? JsonPrimitive)?.contentOrNull?.toLongOrNull() ?: 0L
            uri to durationMs
        }
    }

    private fun JsonElement.path(vararg keys: String): JsonElement? {
        var current: JsonElement = this
        for (key in keys) {
            current = (current as? JsonObject)?.get(key) ?: return null
        }
        return current
    }

    // ------------------------------------------------------------------
    // sp_dc → web-player token
    // ------------------------------------------------------------------

    /** True while a cached token for [spDc] is still comfortably valid. */
    private fun cachedTokenFor(spDc: String): String? {
        val token = bearerToken ?: return null
        if (tokenSpDc != spDc) return null
        if (System.currentTimeMillis() >= tokenExpiresAtMs - TOKEN_SKEW_MS) return null
        return token
    }

    /**
     * Exchanges the cookie for a short-lived token, walking the embedded
     * secret versions newest-first until one is accepted.
     */
    internal suspend fun exchangeToken(spDc: String): String {
        cachedTokenFor(spDc)?.let { return it }

        val serverTimeSeconds = runCatching {
            val response = client.get(SERVER_TIME_URL) {
                header("Cookie", "sp_dc=$spDc")
                header("User-Agent", USER_AGENT)
                header("Origin", "https://open.spotify.com/")
            }
            json.parseToJsonElement(response.bodyAsText())
                .let { (it as? JsonObject)?.get("serverTime") }
                .let { (it as? JsonPrimitive)?.contentOrNull?.toLongOrNull() }
                ?: throw IllegalStateException("unexpected server-time payload")
        }.getOrElse {
            Timber.tag(TAG).d(it, "server-time unavailable; falling back to local clock")
            System.currentTimeMillis() / 1000
        }
        val nowSeconds = System.currentTimeMillis() / 1000

        for ((version, secret) in TOTP_SECRETS) {
            val url = TOKEN_URL +
                "?reason=init" +
                "&productType=web-player" +
                "&totp=${generateTotp(secret, nowSeconds)}" +
                "&totpServer=${generateTotp(secret, serverTimeSeconds)}" +
                "&totpVer=$version"
            val result = runCatching {
                val response = client.get(url) {
                    header("Cookie", "sp_dc=$spDc")
                    header("User-Agent", USER_AGENT)
                    header("Origin", "https://open.spotify.com/")
                    header("Referer", "https://open.spotify.com/")
                }
                val body = json.parseToJsonElement(response.bodyAsText()) as? JsonObject
                    ?: return@runCatching null
                val token = (body["accessToken"] as? JsonPrimitive)?.contentOrNull
                val expires =
                    (body["accessTokenExpirationTimestampMs"] as? JsonPrimitive)
                        ?.contentOrNull?.toLongOrNull()
                if (token.isNullOrEmpty() || expires == null) {
                    Timber.tag(TAG).d("token exchange rejected version %d", version)
                    null
                } else {
                    token to expires
                }
            }.getOrNull() ?: continue

            bearerToken = result.first
            tokenExpiresAtMs = result.second
            tokenSpDc = spDc
            return result.first
        }
        throw SpotifyException("Spotify session cookie was rejected")
    }

    private fun invalidateToken() {
        bearerToken = null
        tokenExpiresAtMs = 0L
        tokenSpDc = null
    }

    // ------------------------------------------------------------------
    // Pathfinder queries
    // ------------------------------------------------------------------

    private suspend fun pathfinder(operation: String, hash: String, variables: JsonObject): JsonObject? {
        val token = bearerToken ?: return null
        val extensionsJson =
            """{"persistedQuery":{"version":1,"sha256Hash":"$hash"}}"""
        val url = PATHFINDER_URL +
            "?operationName=$operation" +
            "&variables=${URLEncoder.encode(variables.toString(), "UTF-8")}" +
            "&extensions=${URLEncoder.encode(extensionsJson, "UTF-8")}"
        val response = client.get(url) {
            header("Authorization", "Bearer $token")
            header("app-platform", "WebPlayer")
            header("User-Agent", USER_AGENT)
            header("Accept", "application/json")
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            invalidateToken()
            return null
        }
        if (response.status != HttpStatusCode.OK) return null
        return runCatching {
            json.parseToJsonElement(response.bodyAsText()) as? JsonObject
        }.getOrNull()
    }

    /** Spotify track best matching title/artist/duration, or null. */
    private suspend fun searchTrackUri(
        title: String,
        artist: String?,
        durationMs: Long,
    ): String? {
        val query = if (artist.isNullOrBlank()) title else "$title $artist"
        val variables = buildJsonObject {
            put("searchTerm", query)
            put("offset", 0)
            put("limit", 10)
            put("numberOfTopResults", 5)
            put("includeAudiobooks", true)
            put("includePreReleases", true)
            put("includeAlbumPreReleases", false)
            put("includeAuthors", false)
            put("includeEpisodeContentRatingsV2", false)
        }
        val body = pathfinder(SEARCH_OPERATION, SEARCH_HASH, variables) ?: return null
        val candidates = parseSearchTrackCandidates(body)
        if (candidates.isEmpty()) return null
        if (durationMs <= 0) return candidates.first().first
        return candidates.minByOrNull { (_, candidateDuration) ->
            if (candidateDuration <= 0) Long.MAX_VALUE
            else kotlin.math.abs(candidateDuration - durationMs)
        }?.first
    }

    private suspend fun canvasForTrack(trackUri: String): String? {
        val variables = buildJsonObject {
            put("trackUri", trackUri)
        }
        val body = pathfinder(CANVAS_OPERATION, CANVAS_HASH, variables) ?: return null
        return parseCanvasUrl(body)
    }

    // ------------------------------------------------------------------
    // Public entry point
    // ------------------------------------------------------------------

    /**
     * Canvas video url for a song, or null when there is none (or any part
     * of the pipeline fails). Results — including misses — are cached per
     * title/artist for the process lifetime.
     */
    suspend fun canvasUrlFor(
        spDc: String,
        title: String,
        artists: List<String>,
        durationMs: Long,
    ): String? {
        val cookie = spDc.trim()
        if (cookie.isEmpty() || title.isBlank()) return null
        val cacheKey = "${title.lowercase()}|${artists.firstOrNull()?.lowercase().orEmpty()}"
        canvasCache[cacheKey]?.let { return it }

        val url = resolve(cookie, title, artists, durationMs)
        if (canvasCache.size >= 200) canvasCache.clear()
        canvasCache[cacheKey] = url
        return url
    }

    private suspend fun resolve(
        spDc: String,
        title: String,
        artists: List<String>,
        durationMs: Long,
    ): String? = runCatching {
        try {
            exchangeToken(spDc)
            val trackUri = searchTrackUri(title, artists.firstOrNull(), durationMs)
                ?: return@runCatching null
            canvasForTrack(trackUri)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (ignored: Exception) {
            // One retry with a fresh token (expired mid-flight, clock skew…)
            invalidateToken()
            exchangeToken(spDc)
            val trackUri = searchTrackUri(title, artists.firstOrNull(), durationMs)
                ?: return@runCatching null
            canvasForTrack(trackUri)
        }
    }.getOrElse { error ->
        if (error is kotlinx.coroutines.CancellationException) throw error
        Timber.tag(TAG).d(error, "canvas lookup failed for '%s'", title.take(50))
        null
    }
}
