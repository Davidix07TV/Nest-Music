package com.nestmusic.music.lossless.qbdlx

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class QbdlxSearchResponse(val tracks: QbdlxTrackList = QbdlxTrackList())

@Serializable
data class QbdlxTrackList(val items: List<QbdlxTrack> = emptyList())

@Serializable
data class QbdlxTrack(
    val id: Long = 0,
    val title: String = "",
    val isrc: String? = null,
    val duration: Int = 0,
    val streamable: Boolean = true,
    val performer: QbdlxPerformer? = null,
    @SerialName("maximum_bit_depth") val maximumBitDepth: Int = 0,
    @SerialName("maximum_sampling_rate") val maximumSamplingRate: Float = 0f,
    val album: QbdlxAlbum? = null,
)

@Serializable data class QbdlxPerformer(val name: String = "")
@Serializable data class QbdlxAlbum(val image: QbdlxImage? = null)
@Serializable data class QbdlxImage(val large: String? = null, val small: String? = null, val thumbnail: String? = null)

@Serializable
data class QbdlxFileUrl(
    val url: String? = null,
    @SerialName("format_id") val formatId: Int = 0,
    @SerialName("bit_depth") val bitDepth: Int = 0,
    @SerialName("sampling_rate") val samplingRate: Float = 0f,
    val sample: Boolean = false,
    val restrictions: List<QbdlxRestriction> = emptyList(),
)

@Serializable data class QbdlxRestriction(val code: String = "")

data class QbdlxSigning(val appId: String, val appSecret: String)

fun interface QbdlxSigningResolver {
    suspend fun signingFor(token: String): QbdlxSigning
}

fun interface QbdlxPoolProvider {
    suspend fun rawPool(): String
}

sealed interface QbdlxResolveResult {
    data class Ok(val url: String, val codec: String, val bitDepth: Int, val sampleRateHz: Int) : QbdlxResolveResult
    object TokenDead : QbdlxResolveResult
    object RegionLocked : QbdlxResolveResult
}

class QbdlxAuthException(val status: Int, message: String? = null) : RuntimeException(message)
class QbdlxApiException(val status: Int, message: String? = null) : RuntimeException(message)
