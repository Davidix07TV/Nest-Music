package com.nestmusic.music.lossless.qbdlx

import java.security.MessageDigest

/**
 * Signs Qobuz API requests. Qobuz validates request_sig = md5(object+method+params+ts+secret)
 * The param order and literal concatenation per endpoint were reverse-engineered.
 */
class QbdlxSigner(
    private val clock: () -> Long = { System.currentTimeMillis() / 1000L },
) {
    fun requestTs(): Long = clock()

    fun signGetFileUrl(ts: Long, trackId: Long, formatId: Int, appSecret: String): String =
        md5("trackgetFileUrl" + "format_id$formatId" + "intentstream" + "track_id$trackId" + ts + appSecret)

    fun signLyricsUrl(ts: Long, trackId: Long, appSecret: String): String =
        md5("tracklyricsUrl" + "track_id$trackId" + ts + appSecret)

    private fun md5(s: String): String =
        MessageDigest.getInstance("MD5").digest(s.toByteArray())
            .joinToString("") { "%02x".format(it) }
}
