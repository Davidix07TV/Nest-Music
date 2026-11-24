package com.nestmusic.music.playback.audio

import android.content.Context
import android.media.AudioManager
import android.media.MediaCodecList
import android.media.Spatializer
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import timber.log.Timber
import java.util.Locale

object NestDeviceCodecs {
    private const val TAG = "NestDeviceCodecs"

    private val DOLBY_MIMES = listOf(
        "audio/eac3-joc",
        "audio/eac3",
        "audio/ac4",
    )

    @Volatile
    private var probed: Boolean? = null

    /**
     * Whether this device ships a hardware/vendor Dolby Atmos (E-AC-3 JOC / E-AC-3 / AC-4) decoder
     * or Android 12L+ platform [Spatializer].
     */
    fun playsDolbyAtmos(context: Context? = null): Boolean {
        val hwCodec = probed ?: probe().also { probed = it }
        if (hwCodec) return true
        return context != null && hasPlatformSpatializer(context)
    }

    fun isDolbyAtmosMime(mimeType: String?): Boolean {
        if (mimeType.isNullOrBlank()) return false
        val lower = mimeType.lowercase(Locale.ROOT)
        return lower == "audio/eac3-joc" || lower == "audio/eac3" || lower == "audio/ac4" ||
            lower.contains("atmos") || lower.contains("eac3")
    }

    fun isSpatializerActive(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) return false
        return runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            val spatializer = am.spatializer
            spatializer.immersiveAudioLevel != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE &&
                spatializer.isAvailable &&
                spatializer.isEnabled
        }.getOrDefault(false)
    }

    fun hasPlatformSpatializer(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S_V2) return false
        return runCatching {
            val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return false
            am.spatializer.immersiveAudioLevel != Spatializer.SPATIALIZER_IMMERSIVE_LEVEL_NONE
        }.getOrDefault(false)
    }

    private fun probe(): Boolean {
        val viaMedia3 = media3Decoders()
        if (viaMedia3 != null) {
            Timber.tag(TAG).d("Dolby Atmos decoders (Media3): %s", viaMedia3.ifEmpty { listOf("none") })
            return viaMedia3.isNotEmpty()
        }
        return runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos.filter { info ->
                !info.isEncoder && info.supportedTypes.any {
                    it.lowercase(Locale.ROOT) in DOLBY_MIMES
                }
            }.map { it.name }
        }.map { it.isNotEmpty() }.getOrElse { false }
    }

    @OptIn(UnstableApi::class)
    private fun media3Decoders(): List<String>? = runCatching {
        DOLBY_MIMES.flatMap { MediaCodecUtil.getDecoderInfos(it, false, false) }
            .map { it.name }
            .distinct()
    }.getOrNull()
}
