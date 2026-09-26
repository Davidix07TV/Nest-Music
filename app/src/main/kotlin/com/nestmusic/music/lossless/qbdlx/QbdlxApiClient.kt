package com.nestmusic.music.lossless.qbdlx

import com.nestmusic.music.lossless.FlacConfig
import com.nestmusic.music.lossless.FlacLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

class QbdlxApiClient(
    private val config: FlacConfig,
    sharedClient: OkHttpClient,
    private val signer: QbdlxSigner,
    private val signingResolver: QbdlxSigningResolver,
) {
    internal var appId: String = ""
    internal var httpClient: OkHttpClient = sharedClient
    internal var baseUrl: String = ORIGIN
    internal var json: Json = Json { ignoreUnknownKeys = true; isLenient = true; coerceInputValues = true }

    private val logger = FlacLogger(TAG)

    suspend fun search(query: String, token: String, limit: Int = 10): List<QbdlxTrack> =
        withContext(Dispatchers.IO) {
            if (appId.isEmpty()) appId = config.qbdlxAppId()
            val url = "$baseUrl/api.json/0.2/catalog/search".toHttpUrl().newBuilder()
                .addQueryParameter("query", query)
                .addQueryParameter("type", "tracks")
                .addQueryParameter("limit", limit.toString())
                .addQueryParameter("app_id", appId)
                .build()
            val body = get(url.toString(), token)
            runCatching { json.decodeFromString<QbdlxSearchResponse>(body).tracks.items }.getOrDefault(emptyList())
        }

    suspend fun getFileUrl(trackId: Long, formatId: Int, token: String): QbdlxResolveResult =
        withContext(Dispatchers.IO) {
            if (appId.isEmpty()) appId = config.qbdlxAppId()
            val signing = signingResolver.signingFor(token)
            val ts = signer.requestTs()
            val sig = signer.signGetFileUrl(ts = ts, trackId = trackId, formatId = formatId, appSecret = signing.appSecret)
            val url = "$baseUrl/api.json/0.2/track/getFileUrl".toHttpUrl().newBuilder()
                .addQueryParameter("track_id", trackId.toString())
                .addQueryParameter("format_id", formatId.toString())
                .addQueryParameter("app_id", signing.appId)
                .addQueryParameter("request_ts", ts.toString())
                .addQueryParameter("request_sig", sig)
                .addQueryParameter("intent", "stream")
                .build()
            val raw = get(url.toString(), token, appIdHeader = signing.appId)
            val result = classify(json.decodeFromString<QbdlxFileUrl>(raw))
            if (result is QbdlxResolveResult.TokenDead) {
                logger.w("getFileUrl TokenDead track=$trackId fmt=$formatId raw=${raw.take(300)}")
            }
            result
        }

    private fun classify(f: QbdlxFileUrl): QbdlxResolveResult {
        val dead = f.sample || f.formatId == 5 ||
            f.restrictions.any { it.code.equals("UserUnauthenticated", ignoreCase = true) }
        if (dead) return QbdlxResolveResult.TokenDead
        if (f.url.isNullOrBlank() || f.formatId < 6) return QbdlxResolveResult.RegionLocked
        return QbdlxResolveResult.Ok(f.url, "flac", f.bitDepth, (f.samplingRate * 1000f).toInt())
    }

    private suspend fun get(url: String, token: String, appIdHeader: String? = null): String {
        val tokenAppId = appIdHeader ?: signingResolver.signingFor(token).appId
        val req = Request.Builder().url(
            url.toHttpUrl().newBuilder().setQueryParameter("app_id", tokenAppId).build(),
        )
            .header("X-App-Id", tokenAppId)
            .header("X-User-Auth-Token", token)
            .header("Accept", "application/json")
            .header("User-Agent", UA)
            .get().build()
        httpClient.newCall(req).execute().use { resp ->
            val body = resp.body?.string().orEmpty()
            if (resp.code == 401) {
                logger.w("auth 401 ${url.substringBefore('?').substringAfterLast('/')}: ${body.take(160)}")
                throw QbdlxAuthException(401, body.take(120))
            }
            if (resp.code == 403 && body.contains("USER_BLOCKED", ignoreCase = true)) {
                logger.w("token blocked 403 USER_BLOCKED — marking dead")
                throw QbdlxAuthException(403, body.take(120))
            }
            if (!resp.isSuccessful) {
                logger.w("HTTP ${resp.code} ${url.substringBefore('?').substringAfterLast('/')}: ${body.take(160)}")
                throw QbdlxApiException(resp.code, body.take(120))
            }
            return body
        }
    }

    companion object {
        const val TAG = "QbdlxApiClient"
        const val ORIGIN = "https://www.qobuz.com"
        const val UA = "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Mobile Safari/537.36"
    }
}
