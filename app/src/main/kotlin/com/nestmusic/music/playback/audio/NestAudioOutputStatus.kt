package com.nestmusic.music.playback.audio

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.annotation.OptIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NestAudioOutputKind {
    SPEAKER,
    WIRED_HEADPHONES,
    USB_DAC,
    BLUETOOTH,
    HDMI_CAST,
}

@Immutable
data class NestAudioOutputDevice(
    val id: Int,
    val name: String,
    val kind: NestAudioOutputKind,
    val isSelected: Boolean,
    val deviceInfo: AudioDeviceInfo?,
)

@Immutable
data class NestAudioPipelineSnapshot(
    val mimeType: String? = null,
    val codecs: String? = null,
    val sampleRateHz: Int = 48_000,
    val bitDepth: Int = 16,
    val channelCount: Int = 2,
    val bitrateKbps: Int = 0,
    val isDolbyAtmos: Boolean = true,
    val isSpatialAudio: Boolean = false,
    val hardwareSpatializerActive: Boolean = false,
    val selectedDeviceId: Int? = null,
) {
    val codecLabel: String
        get() {
            val m = mimeType.orEmpty().lowercase()
            return when {
                "eac3-joc" in m -> "Dolby Digital Plus JOC"
                "eac3" in m -> "Dolby Digital Plus"
                "ac4" in m -> "Dolby AC-4"
                "flac" in m -> "FLAC Lossless"
                "alac" in m -> "Apple Lossless"
                "wav" in m -> "Linear PCM"
                "opus" in m -> "Opus"
                "mp4a" in m || "aac" in m -> "AAC"
                "mpeg" in m || "mp3" in m -> "MP3"
                !codecs.isNullOrBlank() -> codecs.uppercase()
                else -> "AAC / Opus"
            }
        }

    val badgeLabel: String
        get() {
            val m = mimeType.orEmpty().lowercase()
            val lossless = "flac" in m || "alac" in m || "wav" in m
            return when {
                isDolbyAtmos && lossless -> "Dolby Atmos · Lossless"
                isDolbyAtmos -> "Dolby Atmos"
                lossless && sampleRateHz > 48_000 -> "Hi-Res Lossless"
                lossless -> "Lossless"
                isSpatialAudio -> "Spatial Audio"
                bitrateKbps >= 256 -> "High Quality"
                else -> "Dolby Audio"
            }
        }

    val detailSubtitle: String
        get() = buildString {
            append(codecLabel)
            if (sampleRateHz > 0) {
                val khz = sampleRateHz / 1000f
                val khzText = if (sampleRateHz % 1000 == 0) "${sampleRateHz / 1000}" else String.format("%.1f", khz)
                append(" · $bitDepth-bit/$khzText kHz")
            }
            if (channelCount > 2) {
                append(" · ${channelCount}ch")
            }
        }
}

object NestAudioOutputStatus {
    private val _pipeline = MutableStateFlow(NestAudioPipelineSnapshot())
    val pipeline: StateFlow<NestAudioPipelineSnapshot> = _pipeline.asStateFlow()

    @Volatile
    private var activePlayerRef: ExoPlayer? = null

    fun attachPlayer(player: ExoPlayer?) {
        activePlayerRef = player
    }

    fun detachPlayer(player: ExoPlayer?) {
        if (activePlayerRef === player) {
            activePlayerRef = null
        }
    }

    fun updateFromFormat(
        mimeType: String?,
        codecs: String?,
        sampleRateHz: Int,
        bitDepth: Int,
        channelCount: Int,
        bitrateKbps: Int,
        dolbyAtmosEnabled: Boolean,
        spatialAudioActive: Boolean,
        hardwareSpatializerActive: Boolean,
    ) {
        _pipeline.value = _pipeline.value.copy(
            mimeType = mimeType,
            codecs = codecs,
            sampleRateHz = sampleRateHz,
            bitDepth = bitDepth,
            channelCount = channelCount,
            bitrateKbps = bitrateKbps,
            isDolbyAtmos = dolbyAtmosEnabled,
            isSpatialAudio = spatialAudioActive,
            hardwareSpatializerActive = hardwareSpatializerActive,
        )
    }

    @OptIn(UnstableApi::class)
    fun selectOutputDevice(deviceInfo: AudioDeviceInfo?) {
        runCatching {
            activePlayerRef?.setPreferredAudioDevice(deviceInfo)
        }
        _pipeline.value = _pipeline.value.copy(selectedDeviceId = deviceInfo?.id)
    }
}

@Composable
fun rememberNestAudioOutputs(): List<NestAudioOutputDevice> {
    val context = LocalContext.current
    val snapshot by NestAudioOutputStatus.pipeline.collectAsState()
    val selectedDeviceId = snapshot.selectedDeviceId
    var devices by remember { mutableStateOf<List<NestAudioOutputDevice>>(emptyList()) }

    DisposableEffect(context, selectedDeviceId) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        fun refresh() {
            if (am == null) return
            val raw = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { it.isSink }
            val mapped = mutableListOf<NestAudioOutputDevice>()
            var phoneAdded = false
            for (info in raw) {
                val kind = when (info.type) {
                    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                    AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                    AudioDeviceInfo.TYPE_BLE_HEADSET,
                    AudioDeviceInfo.TYPE_BLE_SPEAKER,
                    AudioDeviceInfo.TYPE_BLE_BROADCAST,
                    -> NestAudioOutputKind.BLUETOOTH

                    AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                    AudioDeviceInfo.TYPE_WIRED_HEADSET,
                    AudioDeviceInfo.TYPE_LINE_ANALOG,
                    AudioDeviceInfo.TYPE_AUX_LINE,
                    -> NestAudioOutputKind.WIRED_HEADPHONES

                    AudioDeviceInfo.TYPE_USB_HEADSET,
                    AudioDeviceInfo.TYPE_USB_DEVICE,
                    AudioDeviceInfo.TYPE_USB_ACCESSORY,
                    -> NestAudioOutputKind.USB_DAC

                    AudioDeviceInfo.TYPE_HDMI,
                    AudioDeviceInfo.TYPE_HDMI_ARC,
                    AudioDeviceInfo.TYPE_HDMI_EARC,
                    -> NestAudioOutputKind.HDMI_CAST

                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> {
                        if (phoneAdded) continue
                        phoneAdded = true
                        NestAudioOutputKind.SPEAKER
                    }

                    else -> continue
                }
                val label = when (kind) {
                    NestAudioOutputKind.SPEAKER -> Build.MODEL.takeIf { it.isNotBlank() } ?: "Phone Speaker"
                    NestAudioOutputKind.WIRED_HEADPHONES ->
                        info.productName?.toString()?.takeIf { it.isNotBlank() && it != Build.MODEL }
                            ?: "Wired Headphones"
                    NestAudioOutputKind.USB_DAC ->
                        info.productName?.toString()?.takeIf { it.isNotBlank() } ?: "USB Audio DAC"
                    else -> info.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Audio Output"
                }
                mapped += NestAudioOutputDevice(
                    id = info.id,
                    name = label,
                    kind = kind,
                    isSelected = false,
                    deviceInfo = if (kind == NestAudioOutputKind.SPEAKER) null else info,
                )
            }
            val activeId =
                selectedDeviceId?.takeIf { id -> mapped.any { it.id == id } }
                    ?: mapped.firstOrNull { it.kind == NestAudioOutputKind.BLUETOOTH }?.id
                    ?: mapped.firstOrNull { it.kind == NestAudioOutputKind.USB_DAC }?.id
                    ?: mapped.firstOrNull { it.kind == NestAudioOutputKind.WIRED_HEADPHONES }?.id
                    ?: mapped.firstOrNull { it.kind == NestAudioOutputKind.HDMI_CAST }?.id
                    ?: mapped.firstOrNull()?.id
            devices = mapped.map { it.copy(isSelected = it.id == activeId) }
        }
        refresh()
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
        }
        am?.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        onDispose { am?.unregisterAudioDeviceCallback(callback) }
    }

    return devices
}
