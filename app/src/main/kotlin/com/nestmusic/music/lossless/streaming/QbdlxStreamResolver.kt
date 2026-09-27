package com.nestmusic.music.lossless.streaming

import com.nestmusic.music.lossless.FlacLogger
import com.nestmusic.music.lossless.model.FlacStreamUrl
import com.nestmusic.music.lossless.model.TrackQuery
import com.nestmusic.music.lossless.qbdlx.QbdlxQobuzSource

class QbdlxStreamResolver(
    private val source: QbdlxQobuzSource,
) {
    private val logger = FlacLogger(TAG)

    suspend fun resolve(query: TrackQuery, requestedQuality: Int): FlacStreamUrl? {
        logger.d("resolve attempt title='${query.title}'")
        if (!source.isEnabledForStreaming()) {
            logger.d("disabled (toggle off or pool dead)")
            return null
        }
        val result = source.resolveImmediate(query, requestedQuality) ?: run {
            logger.d("no_result")
            return null
        }
        val etspMs = parseEtspMs(result.url) ?: run {
            logger.w("no_etsp in url")
            return null
        }
        logger.d("resolved origin=$ORIGIN expiresInSec=${(etspMs - System.currentTimeMillis()) / 1000}")
        return FlacStreamUrl(
            url = result.url,
            expiresAtMs = etspMs,
            codec = result.codec.takeIf { it.isNotBlank() },
            bitsPerSample = result.bitDepth.takeIf { it > 0 },
            sampleRateHz = result.sampleRateHz.takeIf { it > 0 },
            bitrateKbps = null,
            coverArtUrl = result.coverArtUrl?.takeIf { it.isNotBlank() },
            origin = ORIGIN,
        )
    }

    private fun parseEtspMs(url: String): Long? {
        val match = ETSP_REGEX.find(url) ?: return null
        val secs = match.groupValues[1].toLongOrNull() ?: return null
        return secs * 1000L
    }

    companion object {
        const val TAG = "QbdlxStreamResolver"
        const val ORIGIN = "qbdlx"
        val ETSP_REGEX = Regex("""[?&]etsp=(\d+)""")
    }
}
