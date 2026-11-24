/*
 * Nest Music — Dolby Atmos / Spatial Audio Binaural Processor
 * Licensed under GPL-3.0
 */

package com.nestmusic.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.roundToInt

/**
 * Real-time mid/side + Haas head-shadow binaural spatial audio processor running inside
 * Media3's [DefaultAudioSink] pipeline:
 * - Expands stereo side energy (`widthGain = 2.5f`) while keeping center vocals anchored
 * - Mixes a 15ms delayed, one-pole low-passed cross-feed (`lowpassCoeff = 0.3f`, `crossfeedGain = 0.2f`)
 *   modeling natural interaural head shadow so headphones & speakers gain real depth without phase hollowness
 * - Supports both 16-bit PCM and 32-bit Float PCM, bowing out (`NOT_SET`) on multichannel (5.1/7.1.4 E-AC-3 JOC)
 *   streams so native Dolby Atmos multichannel mixes pass straight through to the hardware spatializer.
 */
@UnstableApi
class SpatialAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var isEnabled: Boolean = false

    private val widthGain = 2.5f
    private val outputGain = 0.82f
    private val crossfeedGain = 0.2f
    private val lowpassCoeff = 0.3f

    private var sampleRate: Int = 0
    private var channelCount: Int = 0
    private var encoding: Int = C.ENCODING_INVALID

    private var delayLeft = FloatArray(0)
    private var delayRight = FloatArray(0)
    private var delayIndex = 0
    private var lowpassLeft = 0f
    private var lowpassRight = 0f

    private fun configureDelayLine(sampleRate: Int, channelCount: Int, encoding: Int) {
        this.sampleRate = sampleRate
        this.channelCount = channelCount
        this.encoding = encoding
        if (channelCount != 2 || sampleRate <= 0) {
            delayLeft = FloatArray(0)
            delayRight = FloatArray(0)
            delayIndex = 0
            lowpassLeft = 0f
            lowpassRight = 0f
            return
        }
        val delaySamples = (sampleRate * DELAY_MS / 1000f)
            .roundToInt()
            .coerceAtLeast(1)
        if (delayLeft.size != delaySamples) {
            delayLeft = FloatArray(delaySamples)
            delayRight = FloatArray(delaySamples)
        }
        onFlush()
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        val supportedEncoding = inputAudioFormat.encoding == C.ENCODING_PCM_16BIT ||
            inputAudioFormat.encoding == C.ENCODING_PCM_FLOAT
        if (!supportedEncoding || inputAudioFormat.channelCount != 2) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        configureDelayLine(
            sampleRate = inputAudioFormat.sampleRate,
            channelCount = inputAudioFormat.channelCount,
            encoding = inputAudioFormat.encoding,
        )
        return inputAudioFormat
    }

    override fun onFlush() {
        delayLeft.fill(0f)
        delayRight.fill(0f)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
    }

    override fun onReset() {
        delayLeft = FloatArray(0)
        delayRight = FloatArray(0)
        delayIndex = 0
        lowpassLeft = 0f
        lowpassRight = 0f
        channelCount = 0
        sampleRate = 0
        encoding = C.ENCODING_INVALID
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val remaining = inputBuffer.remaining()
        if (remaining == 0) return

        val isFloat = encoding == C.ENCODING_PCM_FLOAT
        val bytesPerFrame = if (isFloat) BYTES_PER_FLOAT_FRAME else BYTES_PER_16BIT_FRAME
        val frameCount = remaining / bytesPerFrame
        if (frameCount == 0) return

        val outputBuffer = replaceOutputBuffer(frameCount * bytesPerFrame)
        val delaySize = delayLeft.size

        if (!isEnabled || delaySize == 0) {
            outputBuffer.put(inputBuffer)
            outputBuffer.flip()
            return
        }

        inputBuffer.order(ByteOrder.nativeOrder())
        outputBuffer.order(ByteOrder.nativeOrder())

        var dIdx = delayIndex
        var lpL = lowpassLeft
        var lpR = lowpassRight

        if (isFloat) {
            repeat(frameCount) {
                val left = inputBuffer.float
                val right = inputBuffer.float

                val mid = (left + right) * 0.5f
                val side = (left - right) * 0.5f * widthGain
                var widenedLeft = mid + side
                var widenedRight = mid - side

                val delayedRight = delayRight[dIdx]
                val delayedLeft = delayLeft[dIdx]
                lpL += lowpassCoeff * (delayedRight - lpL)
                lpR += lowpassCoeff * (delayedLeft - lpR)
                widenedLeft += lpL * crossfeedGain
                widenedRight += lpR * crossfeedGain

                delayLeft[dIdx] = left
                delayRight[dIdx] = right
                dIdx = (dIdx + 1) % delaySize

                outputBuffer.putFloat((widenedLeft * outputGain).coerceIn(-1f, 1f))
                outputBuffer.putFloat((widenedRight * outputGain).coerceIn(-1f, 1f))
            }
        } else {
            val invScale = 1.0f / 32768.0f
            repeat(frameCount) {
                val left = inputBuffer.short.toFloat() * invScale
                val right = inputBuffer.short.toFloat() * invScale

                val mid = (left + right) * 0.5f
                val side = (left - right) * 0.5f * widthGain
                var widenedLeft = mid + side
                var widenedRight = mid - side

                val delayedRight = delayRight[dIdx]
                val delayedLeft = delayLeft[dIdx]
                lpL += lowpassCoeff * (delayedRight - lpL)
                lpR += lowpassCoeff * (delayedLeft - lpR)
                widenedLeft += lpL * crossfeedGain
                widenedRight += lpR * crossfeedGain

                delayLeft[dIdx] = left
                delayRight[dIdx] = right
                dIdx = (dIdx + 1) % delaySize

                outputBuffer.putShort(clamp16FromFloat(widenedLeft * outputGain))
                outputBuffer.putShort(clamp16FromFloat(widenedRight * outputGain))
            }
        }

        delayIndex = dIdx
        lowpassLeft = lpL
        lowpassRight = lpR
        outputBuffer.flip()
    }

    private fun clamp16FromFloat(sample: Float): Short {
        val scaled = (sample * 32767.0f).roundToInt()
        return scaled.coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt()).toShort()
    }

    private companion object {
        const val BYTES_PER_16BIT_FRAME = 4
        const val BYTES_PER_FLOAT_FRAME = 8
        const val DELAY_MS = 15
    }
}
