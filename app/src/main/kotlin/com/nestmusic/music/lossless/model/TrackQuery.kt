package com.nestmusic.music.lossless.model

/**
 * Identification fields for a track lookup.
 * ISRC is the most precise identifier — when present, sources should match by ISRC first.
 */
data class TrackQuery(
    val artist: String,
    val title: String,
    val album: String? = null,
    val isrc: String? = null,
    val durationMs: Long? = null,
    val explicit: Boolean? = null,
) {
    fun searchTerms(): List<String> = buildList {
        isrc?.takeIf { it.isNotBlank() }?.let(::add)
        val full = "$artist $title".trim()
        if (full.isNotBlank()) add(full)
        val primary = artist.substringBefore(",").trim()
        if (primary.isNotEmpty() && !primary.equals(artist.trim(), ignoreCase = true)) {
            add("$primary $title".trim())
        }
    }.distinct()
}

data class AudioFormatInfo(
    val id: String,
    val codec: String,
    val bitrateKbps: Int? = null,
    val bitsPerSample: Int? = null,
    val sampleRateHz: Int? = null,
) {
    val isLossless: Boolean
        get() = codec.lowercase() in setOf("flac", "alac", "wav", "ape", "tta", "wv", "aiff")
}

data class RateLimitState(
    val tokensAvailable: Double,
    val msUntilNextToken: Long,
    val isCircuitBroken: Boolean,
    val msUntilUnblock: Long,
    val recentFailures: Int,
)
