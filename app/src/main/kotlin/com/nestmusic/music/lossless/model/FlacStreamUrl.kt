package com.nestmusic.music.lossless.model

data class FlacStreamUrl(
    val url: String,
    val expiresAtMs: Long,
    val codec: String? = null,
    val bitsPerSample: Int? = null,
    val sampleRateHz: Int? = null,
    val bitrateKbps: Int? = null,
    val coverArtUrl: String? = null,
    val origin: String,
)
