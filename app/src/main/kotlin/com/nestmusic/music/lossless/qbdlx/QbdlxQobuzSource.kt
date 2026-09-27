package com.nestmusic.music.lossless.qbdlx

import com.nestmusic.music.lossless.FlacConfig
import com.nestmusic.music.lossless.FlacLogger
import com.nestmusic.music.lossless.model.TrackQuery
import com.nestmusic.music.lossless.qobuz.QobuzCandidateMatcher
import com.nestmusic.music.lossless.ratelimit.AggregatorRateLimiter
import kotlinx.coroutines.CancellationException

data class QbdlxResolvedStream(
    val url: String,
    val codec: String,
    val bitDepth: Int,
    val sampleRateHz: Int,
    val confidence: Float,
    val coverArtUrl: String?,
    val sourceTrackId: String,
)

class QbdlxQobuzSource(
    private val apiClient: QbdlxApiClient,
    private val credentialStore: QbdlxCredentialStore,
    private val rateLimiter: AggregatorRateLimiter,
    private val config: FlacConfig,
) {
    private val logger = FlacLogger(TAG)
    val id: String = SOURCE_ID

    suspend fun isEnabledForStreaming(): Boolean =
        config.qbdlxEnabled() && !credentialStore.allDead()

    suspend fun resolveImmediate(
        query: TrackQuery,
        requestedQuality: Int? = null,
    ): QbdlxResolvedStream? {
        if (!isEnabledForStreaming()) return null
        return resolveInternal(query, bypassRateLimit = true, requestedQuality = requestedQuality)
    }

    private suspend fun resolveInternal(
        query: TrackQuery,
        bypassRateLimit: Boolean,
        requestedQuality: Int?,
    ): QbdlxResolvedStream? {
        val (track, conf, token) = search(query, bypassRateLimit) ?: return null
        val formatId = requestedQuality ?: 27
        return resolveFile(track, conf, token, formatId, bypassRateLimit)
    }

    private suspend fun search(
        query: TrackQuery,
        bypassRateLimit: Boolean,
    ): Triple<QbdlxTrack, Float, String>? {
        var token = credentialStore.activeToken() ?: return null
        val tried = mutableSetOf<String>()
        var guard = 0
        while (guard++ < MAX_TOKEN_ATTEMPTS) {
            tried += token
            try {
                for (term in query.searchTerms()) {
                    val candidates = callLimited(bypassRateLimit) {
                        apiClient.search(term, token)
                    } ?: continue
                    val match = candidates
                        .map { it to confidence(query, it) }
                        .filter { it.second >= QobuzCandidateMatcher.MIN_CONFIDENCE }
                        .maxByOrNull { it.second }
                    if (match != null) return Triple(match.first, match.second, token)
                }
                return null
            } catch (e: QbdlxAuthException) {
                logger.w("search auth-failed ${e.status} marking dead + rotating")
                credentialStore.markDead(token)
                token = credentialStore.activeToken()?.takeUnless { it in tried } ?: return null
            }
        }
        return null
    }

    private suspend fun resolveFile(
        track: QbdlxTrack,
        conf: Float,
        startToken: String,
        formatId: Int,
        bypassRateLimit: Boolean,
    ): QbdlxResolvedStream? {
        val tried = mutableSetOf<String>()
        var token: String? = startToken
        var guard = 0
        while (token != null && guard++ < MAX_TOKEN_ATTEMPTS) {
            if (!tried.add(token)) {
                token = credentialStore.activeToken()?.takeUnless { it in tried }
                continue
            }
            val outcome = resolveOnce(track, token, formatId, bypassRateLimit)
            when (outcome) {
                is Outcome.Resolved -> {
                    credentialStore.recordAlive(token)
                    return build(track, conf, outcome.ok)
                }
                Outcome.Dead -> {
                    credentialStore.markDead(token)
                    token = credentialStore.activeToken()?.takeUnless { it in tried }
                }
                Outcome.Region -> return resolveRegion(track, conf, tried, formatId, bypassRateLimit)
                Outcome.Abort -> return null
            }
        }
        return null
    }

    private suspend fun resolveRegion(
        track: QbdlxTrack,
        conf: Float,
        tried: MutableSet<String>,
        formatId: Int,
        bypassRateLimit: Boolean,
    ): QbdlxResolvedStream? {
        for (rt in credentialStore.tokensForRegion(null)) {
            if (!tried.add(rt)) continue
            when (val outcome = resolveOnce(track, rt, formatId, bypassRateLimit)) {
                is Outcome.Resolved -> {
                    credentialStore.recordAlive(rt)
                    return build(track, conf, outcome.ok)
                }
                Outcome.Dead -> credentialStore.markDead(rt)
                Outcome.Region -> Unit
                Outcome.Abort -> return null
            }
        }
        return null
    }

    private suspend fun resolveOnce(
        track: QbdlxTrack,
        token: String,
        formatId: Int,
        bypassRateLimit: Boolean,
    ): Outcome {
        val result = try {
            callLimited(bypassRateLimit) { apiClient.getFileUrl(track.id, formatId, token) }
        } catch (e: QbdlxAuthException) {
            return Outcome.Dead
        } ?: return Outcome.Abort
        return when (result) {
            is QbdlxResolveResult.Ok -> Outcome.Resolved(result)
            QbdlxResolveResult.TokenDead -> Outcome.Dead
            QbdlxResolveResult.RegionLocked -> Outcome.Region
        }
    }

    private sealed interface Outcome {
        data class Resolved(val ok: QbdlxResolveResult.Ok) : Outcome
        object Dead : Outcome
        object Region : Outcome
        object Abort : Outcome
    }

    private fun build(track: QbdlxTrack, conf: Float, ok: QbdlxResolveResult.Ok): QbdlxResolvedStream {
        val img = track.album?.image
        val art = img?.large ?: img?.thumbnail ?: img?.small
        return QbdlxResolvedStream(
            url = ok.url,
            codec = ok.codec,
            bitDepth = ok.bitDepth,
            sampleRateHz = ok.sampleRateHz,
            confidence = conf,
            coverArtUrl = art,
            sourceTrackId = track.id.toString(),
        )
    }

    private fun confidence(query: TrackQuery, candidate: QbdlxTrack): Float =
        QobuzCandidateMatcher.confidence(
            query = query,
            candTitle = candidate.title,
            candArtist = candidate.performer?.name.orEmpty(),
            candIsrc = candidate.isrc,
            candDurationSec = candidate.duration,
            candStreamable = candidate.streamable,
        )

    private suspend fun <T> callLimited(
        bypassRateLimit: Boolean,
        block: suspend () -> T,
    ): T? {
        if (!bypassRateLimit && !rateLimiter.acquire(id)) return null
        return try {
            block().also { rateLimiter.reportSuccess(id) }
        } catch (e: QbdlxAuthException) {
            throw e
        } catch (e: CancellationException) {
            throw e
        } catch (e: QbdlxApiException) {
            if (e.status == 429) rateLimiter.reportRateLimited(id) else rateLimiter.reportFailure(id)
            logger.w("qbdlx api failed status=${e.status}")
            null
        } catch (e: Exception) {
            rateLimiter.reportFailure(id)
            logger.w("qbdlx call threw ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    companion object {
        const val SOURCE_ID = "qbdlx_qobuz"
        private const val TAG = "QbdlxSource"
        private const val MAX_TOKEN_ATTEMPTS = 6
    }
}
