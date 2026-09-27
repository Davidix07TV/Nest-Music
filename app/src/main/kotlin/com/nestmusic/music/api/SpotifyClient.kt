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
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import timber.log.Timber
import java.net.URLEncoder

/** One track of an imported Spotify playlist, reduced to what matching needs. */
data class SpotifyTrack(
    val title: String,
    val artists: List<String>,
    val durationMs: Long,
)

/** A Spotify playlist reduced to a name and its tracks. */
data class SpotifyPlaylist(
    val name: String,
    val tracks: List<SpotifyTrack>,
)

/** Raised when a playlist cannot be fetched or parsed. */
class SpotifyException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Read-only Spotify access used for playlist import: an anonymous bearer
 * token is bootstrapped from the public embed page, then the same GraphQL
 * endpoint the web player uses is queried for the playlist. When that fails,
 * the embed page itself still carries the first page of tracks.
 *
 * Everything here is best-effort by design: no credentials, no guarantees
 * from Spotify, and every failure path ends in a typed [SpotifyException].
 */
object SpotifyClient {
    private const val TAG = "SpotifyClient"

    private const val EMBED_URL = "https://open.spotify.com/embed/playlist/%s"
    private const val PATHFINDER_URL = "https://api-partner.spotify.com/pathfinder/v1/query"
    private const val PLAYLIST_OPERATION = "fetchPlaylist"
    private const val PLAYLIST_HASH =
        "a65e12194ed5fc443a1cdebed5fabe33ca5b07b987185d63c72483867ad13cb4"
    private const val PAGE_SIZE = 100
    private const val MAX_TRACKS = 1000
    private const val TOKEN_SKEW_MS = 60_000L

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/132.0.0.0 Safari/537.36"

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val client by lazy {
        HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = 15_000
                connectTimeoutMillis = 8_000
                socketTimeoutMillis = 15_000
            }
            expectSuccess = false
        }
    }

    @Volatile
    private var bearerToken: String? = null
    @Volatile
    private var tokenExpiresAtMs: Long = 0L

    // ------------------------------------------------------------------
    // Pure helpers (unit-tested without any network)
    // ------------------------------------------------------------------

    /** Pulls the `__NEXT_DATA__` JSON blob out of an embed page. */
    internal fun extractNextData(html: String): JsonObject {
        val marker = """<script id="__NEXT_DATA__" type="application/json">"""
        val start = html.indexOf(marker)
        if (start < 0) throw SpotifyException("Embed page carries no __NEXT_DATA__ payload")
        val bodyStart = start + marker.length
        val end = html.indexOf("</script>", bodyStart)
        if (end < 0) throw SpotifyException("Embed page __NEXT_DATA__ is not terminated")
        return runCatching {
            json.parseToJsonElement(html.substring(bodyStart, end)).jsonObject
        }.getOrElse { throw SpotifyException("Embed page __NEXT_DATA__ is not valid JSON", it) }
    }

    /** Accepts pasted URLs, `spotify:playlist:` URIs and bare 22-char ids. */
    internal fun playlistIdFromInput(input: String): String? {
        val trimmed = input.trim()
        Regex("""spotify:playlist:([A-Za-z0-9]{22})""").find(trimmed)?.let {
            return it.groupValues[1]
        }
        Regex("""/playlist/([A-Za-z0-9]{22})""").find(trimmed)?.let {
            return it.groupValues[1]
        }
        if (trimmed.matches(Regex("""[A-Za-z0-9]{22}"""))) return trimmed
        return null
    }

    /** Anonymous session (token + absolute expiry) from the embed payload. */
    internal fun embedSession(nextData: JsonObject): Pair<String, Long>? {
        val session = nextData.path("props", "pageProps", "state", "settings", "session")
            as? JsonObject ?: return null
        val token = session.optString("accessToken")
        val expires = session["accessTokenExpirationTimestampMs"].optLong()
        if (token.isNullOrEmpty() || expires == null) return null
        return token to expires
    }

    /**
     * Playlist name + tracks from a `fetchPlaylist` pathfinder body.
     * Non-track rows (local files, episodes) are skipped.
     */
    internal fun parsePlaylistPage(body: JsonObject): Pair<String, List<SpotifyTrack>>? {
        val union = body.path("data", "playlistV2") as? JsonObject ?: return null
        val name = union.optString("name") ?: return null
        val tracks = parsePlaylistItems(union.path("content") as? JsonObject)
        return name to tracks
    }

    /** Tracks of one `content` page (also used page-by-page while paging). */
    internal fun parsePlaylistItems(content: JsonObject?): List<SpotifyTrack> {
        val items = content?.get("items") as? JsonArray ?: return emptyList()
        return items.mapNotNull { element ->
            val item = element as? JsonObject ?: return@mapNotNull null
            val data = item.path("itemV2", "data") as? JsonObject ?: return@mapNotNull null
            if (data.optString("__typename") != "Track") return@mapNotNull null
            trackFromGql(data)
        }
    }

    /** Total track count reported by the playlist page (for paging/caps). */
    internal fun playlistTotalCount(body: JsonObject): Int? {
        val union = body.path("data", "playlistV2") as? JsonObject ?: return null
        val content = union.path("content") as? JsonObject ?: return null
        return content["totalCount"].optLong()?.toInt()
    }

    /** Name + ~50 tracks straight from the embed entity (fallback tier). */
    internal fun parseEmbedPlaylist(nextData: JsonObject): SpotifyPlaylist? {
        val pageProps = nextData.path("props", "pageProps") as? JsonObject ?: return null
        if ("status" in pageProps || "forbiddenReason" in pageProps) return null
        val entity = pageProps.path("state", "data", "entity") as? JsonObject ?: return null
        val name = entity.optString("name")
            ?: entity.optString("title")
            ?: return null
        val rows = entity["trackList"] as? JsonArray ?: return SpotifyPlaylist(name, emptyList())
        val tracks = rows.mapNotNull { element ->
            val row = element as? JsonObject ?: return@mapNotNull null
            val entityType = row.optString("entityType")
            if (entityType != null && entityType != "track") return@mapNotNull null
            val title = row.optString("title")
                ?: row.optString("name")
                ?: return@mapNotNull null
            val uri = row.optString("uri") ?: return@mapNotNull null
            if (!uri.contains("track")) return@mapNotNull null
            val subtitle = row.optString("subtitle")
            val artists = subtitle
                ?.split(',')
                ?.mapNotNull { part -> part.trim().takeIf { it.isNotEmpty() } }
                ?: emptyList()
            SpotifyTrack(
                title = title,
                artists = artists,
                durationMs = row["duration"].optLong() ?: 0L,
            )
        }
        return SpotifyPlaylist(name, tracks)
    }

    private fun trackFromGql(data: JsonObject): SpotifyTrack? {
        val title = data.optString("name") ?: return null
        val artists = data.path("artists", "items")
            ?.let { it as? JsonArray }
            ?.mapNotNull { element ->
                val profile = (element as? JsonObject)?.path("profile") as? JsonObject
                profile?.optString("name")
            }
            ?: emptyList()
        val durationMs = data.path("duration", "totalMilliseconds").optLong() ?: 0L
        return SpotifyTrack(title, artists, durationMs)
    }


    private fun JsonObject.optString(key: String): String? =
        (this[key] as? kotlinx.serialization.json.JsonPrimitive)?.contentOrNull

    private fun JsonElement?.optLong(): Long? =
        (this as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull

    private fun JsonElement.path(vararg keys: String): JsonElement? {
        var current: JsonElement = this
        for (key in keys) {
            current = (current as? JsonObject)?.get(key) ?: return null
        }
        return current
    }

    // ------------------------------------------------------------------
    // Network
    // ------------------------------------------------------------------

    /**
     * Fetches a playlist from a pasted URL/URI/id. Never throws; failures
     * come back as a failed [Result] with a user-readable message.
     */
    suspend fun fetchPlaylist(input: String): Result<SpotifyPlaylist> = runCatching {
        val id = playlistIdFromInput(input)
            ?: throw SpotifyException("Not a Spotify playlist link")
        val embedHtml = getBody(EMBED_URL.format(id))
            ?: throw SpotifyException("Could not reach the playlist page")
        val nextData = extractNextData(embedHtml)
        embedSession(nextData)?.let { (token, expires) ->
            bearerToken = token
            tokenExpiresAtMs = expires
        }
        val fallback = parseEmbedPlaylist(nextData)

        val pages = mutableListOf<SpotifyTrack>()
        var name: String? = null
        var total: Int? = null
        try {
            var offset = 0
            while (pages.size < MAX_TRACKS) {
                val body = pathfinderPlaylist(id, offset) ?: break
                if (name == null) name = parsePlaylistPage(body)?.first
                parsePlaylistPage(body)?.second?.let { page -> pages.addAll(page) }
                total = playlistTotalCount(body) ?: total
                val fetchedTotal = total ?: break
                offset += PAGE_SIZE
                if (offset >= fetchedTotal || pages.isEmpty()) break
                kotlinx.coroutines.delay(150)
            }
        } catch (e: SpotifyException) {
            Timber.tag(TAG).w(e, "pathfinder playlist query failed; using embed fallback")
        }

        val tracks = pages
        val resolvedName = name ?: fallback?.name
        val resolvedTracks = tracks.ifEmpty { fallback?.tracks.orEmpty() }
        if (resolvedName == null || (resolvedTracks.isEmpty() && fallback == null)) {
            throw SpotifyException("Playlist could not be parsed")
        }
        SpotifyPlaylist(name = resolvedName, tracks = resolvedTracks.take(MAX_TRACKS))
    }.onFailure {
        Timber.tag(TAG).w(it, "playlist import failed for input '%s'", input.take(80))
    }

    private suspend fun pathfinderPlaylist(id: String, offset: Int): JsonObject? {
        val token = bearerToken?.takeIf {
            System.currentTimeMillis() < tokenExpiresAtMs - TOKEN_SKEW_MS
        } ?: throw SpotifyException("Missing Spotify session token")
        val variables = buildJsonObject {
            put("uri", "spotify:playlist:$id")
            put("offset", offset)
            put("limit", PAGE_SIZE)
            put("enableWatchFeedEntrypoint", false)
        }
        val extensions = buildJsonObject {
            put("persistedQuery", buildJsonObject {
                put("version", 1)
                put("sha256Hash", PLAYLIST_HASH)
            })
        }
        val url = PATHFINDER_URL +
            "?operationName=$PLAYLIST_OPERATION" +
            "&variables=${URLEncoder.encode(variables.toString(), "UTF-8")}" +
            "&extensions=${URLEncoder.encode(extensions.toString(), "UTF-8")}"
        val response = client.get(url) {
            header("Authorization", "Bearer $token")
            header("app-platform", "WebPlayer")
            header("User-Agent", USER_AGENT)
            header("Accept", "application/json")
        }
        if (response.status == HttpStatusCode.Unauthorized) {
            bearerToken = null
            throw SpotifyException("Spotify session expired (HTTP 401)")
        }
        val bodyText = response.bodyAsText()
        if (response.status != HttpStatusCode.OK) {
            throw SpotifyException("Spotify responded with HTTP ${response.status}")
        }
        val body = runCatching { json.parseToJsonElement(bodyText).jsonObject }.getOrElse {
            throw SpotifyException("Spotify returned a non-JSON body", it)
        }
        val errors = body["errors"] as? JsonArray
        if (errors != null && errors.any {
                (it as? JsonObject)?.optString("message") == "PersistedQueryNotFound"
            }
        ) {
            throw SpotifyException("Spotify rotated its query hash")
        }
        return body
    }

    private suspend fun getBody(url: String): String? = runCatching {
        val response = client.get(url) { header("User-Agent", USER_AGENT) }
        if (response.status == HttpStatusCode.OK) response.bodyAsText() else null
    }.getOrNull()

    /** Clears the cached bearer token (used by tests / forced refresh). */
    internal fun resetSession() {
        bearerToken = null
        tokenExpiresAtMs = 0L
    }
}
