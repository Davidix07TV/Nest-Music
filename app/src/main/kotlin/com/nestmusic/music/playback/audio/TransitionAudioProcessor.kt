/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.playback.audio

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import com.nestmusic.music.playback.MixEffect
import com.nestmusic.music.playback.MixEqMode
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI

/**
 * Small, transition-only PCM processor.
 *
 * It is installed on every ExoPlayer as a cheap pass-through processor and is
 * switched on only for the two players participating in a mix. Keeping the
 * processor in the renderer chain means EQ/filter work is performed before the
 * AudioTrack and is also available to the secondary player used by
 * [com.nestmusic.music.playback.MusicService].
 */
@UnstableApi
@Suppress("DEPRECATION")
class TransitionAudioProcessor : AudioProcessor {
    private data class State(
        val eqMode: MixEqMode,
        val effect: MixEffect,
        val progress: Float,
        val outgoing: Boolean,
    )

    private var sampleRate = 0
    private var channelCount = 0
    private var encoding = C.ENCODING_INVALID
    private var outputBuffer: ByteBuffer = EMPTY_BUFFER
    private var inputEnded = false

    @Volatile
    private var state = State(MixEqMode.NONE, MixEffect.NONE, 0f, outgoing = false)

    // One-pole low-pass state, one value per channel. The complementary
    // high-pass signal is input - low-pass, which is enough for a short DJ
    // transition and avoids allocating a filter for every progress tick.
    private var lowPassLeft = 0.0
    private var lowPassRight = 0.0

    /** Called by MusicService on the main thread for each crossfade tick. */
    fun setTransition(
        eqMode: MixEqMode,
        effect: MixEffect,
        progress: Float,
        outgoing: Boolean,
    ) {
        state = State(eqMode, effect, progress.coerceIn(0f, 1f), outgoing)
    }

    fun clearTransition() {
        state = State(MixEqMode.NONE, MixEffect.NONE, 0f, outgoing = false)
    }

    override fun configure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount
        encoding = inputAudioFormat.encoding
        if (channelCount !in 1..2 || encoding !in setOf(
                C.ENCODING_PCM_16BIT,
                C.ENCODING_PCM_24BIT,
                C.ENCODING_PCM_32BIT,
                C.ENCODING_PCM_FLOAT,
            )
        ) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        return inputAudioFormat
    }

    // This processor must stay in the chain even when its current state is a
    // pass-through so it can be enabled without rebuilding the ExoPlayer.
    override fun isActive(): Boolean = true

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) {
            outputBuffer = EMPTY_BUFFER
            return
        }

        val inputSize = inputBuffer.remaining()
        val bytesPerSample = when (encoding) {
            C.ENCODING_PCM_16BIT -> 2
            C.ENCODING_PCM_24BIT -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> 4
            else -> throw AudioProcessor.UnhandledAudioFormatException(
                AudioProcessor.AudioFormat(sampleRate, channelCount, encoding),
            )
        }
        val out = replaceOutputBuffer(inputSize)
        inputBuffer.order(ByteOrder.LITTLE_ENDIAN)
        out.order(ByteOrder.LITTLE_ENDIAN)
        val current = state

        if (current.eqMode == MixEqMode.NONE && current.effect == MixEffect.NONE) {
            out.put(inputBuffer)
            out.flip()
            return
        }

        val frameSize = bytesPerSample * channelCount
        val frameCount = inputSize / frameSize
        repeat(frameCount) {
            when (encoding) {
                C.ENCODING_PCM_16BIT -> {
                    val left = inputBuffer.getShort().toDouble() / 32768.0
                    out.putShort(toPcm16(process(left, 0, current)))
                    if (channelCount == 2) {
                        val right = inputBuffer.getShort().toDouble() / 32768.0
                        out.putShort(toPcm16(process(right, 1, current)))
                    }
                }
                C.ENCODING_PCM_24BIT -> {
                    val left = inputBuffer.get24Bit().toDouble() / 8_388_608.0
                    out.put24Bit(toPcm24(process(left, 0, current)))
                    if (channelCount == 2) {
                        val right = inputBuffer.get24Bit().toDouble() / 8_388_608.0
                        out.put24Bit(toPcm24(process(right, 1, current)))
                    }
                }
                C.ENCODING_PCM_32BIT -> {
                    val left = inputBuffer.getInt().toDouble() / 2_147_483_648.0
                    out.putInt(toPcm32(process(left, 0, current)))
                    if (channelCount == 2) {
                        val right = inputBuffer.getInt().toDouble() / 2_147_483_648.0
                        out.putInt(toPcm32(process(right, 1, current)))
                    }
                }
                C.ENCODING_PCM_FLOAT -> {
                    val left = inputBuffer.getFloat().toDouble()
                    out.putFloat(process(left, 0, current).toFloat())
                    if (channelCount == 2) {
                        val right = inputBuffer.getFloat().toDouble()
                        out.putFloat(process(right, 1, current).toFloat())
                    }
                }
            }
        }
        // Preserve a partial frame, if a codec ever supplies one, rather than
        // silently dropping it. Normal PCM buffers are always frame-aligned.
        while (inputBuffer.hasRemaining()) out.put(inputBuffer.get())
        out.flip()
    }

    private fun process(input: Double, channel: Int, current: State): Double {
        var value = input
        val progress = current.progress

        if (current.eqMode == MixEqMode.LOW_HIGH_SWAP) {
            val low = lowPass(input, channel)
            val high = input - low
            val lowGain = if (current.outgoing) 1f - progress else progress
            val highGain = if (current.outgoing) progress else 1f - progress
            value = low * lowGain + high * highGain
        }

        val filterAmount = when (current.effect) {
            // A high-pass rise is naturally useful on the outgoing track.
            MixEffect.HIGH_PASS -> if (current.outgoing) progress else 0f
            // A low-pass rise is naturally useful on the incoming track.
            MixEffect.LOW_PASS -> if (current.outgoing) 0f else progress
            MixEffect.NONE -> 0f
        }
        if (filterAmount > 0f) {
            val low = lowPass(value, channel)
            val filtered = when (current.effect) {
                MixEffect.HIGH_PASS -> value - low
                MixEffect.LOW_PASS -> low
                MixEffect.NONE -> value
            }
            value = value * (1f - filterAmount) + filtered * filterAmount
        }
        return value.coerceIn(-1.0, 1.0)
    }

    private fun lowPass(input: Double, channel: Int): Double {
        // A 1.1 kHz cutoff gives an audible but gentle transition filter.
        val alpha = (2.0 * PI * 1_100.0 / sampleRate.coerceAtLeast(1))
            .coerceIn(0.01, 0.35)
        return if (channel == 0) {
            lowPassLeft += alpha * (input - lowPassLeft)
            lowPassLeft
        } else {
            lowPassRight += alpha * (input - lowPassRight)
            lowPassRight
        }
    }

    private fun toPcm16(value: Double): Short =
        (value * 32768.0).coerceIn(-32768.0, 32767.0).toInt().toShort()

    private fun toPcm24(value: Double): Int =
        (value * 8_388_608.0).coerceIn(-8_388_608.0, 8_388_607.0).toInt()

    private fun toPcm32(value: Double): Int =
        (value * 2_147_483_648.0)
            .coerceIn(-2_147_483_648.0, 2_147_483_647.0)
            .toLong()
            .toInt()

    private fun ByteBuffer.get24Bit(): Int {
        val b0 = get().toInt() and 0xFF
        val b1 = get().toInt() and 0xFF
        val b2 = get().toInt()
        return (b2 shl 16) or (b1 shl 8) or b0
    }

    private fun ByteBuffer.put24Bit(value: Int) {
        put((value and 0xFF).toByte())
        put(((value shr 8) and 0xFF).toByte())
        put(((value shr 16) and 0xFF).toByte())
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val output = outputBuffer
        outputBuffer = EMPTY_BUFFER
        return output
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === EMPTY_BUFFER

    @Deprecated("Deprecated in AudioProcessor")
    override fun flush() {
        outputBuffer = EMPTY_BUFFER
        inputEnded = false
        lowPassLeft = 0.0
        lowPassRight = 0.0
    }

    @Deprecated("Deprecated in AudioProcessor")
    override fun reset() {
        flush()
        sampleRate = 0
        channelCount = 0
        encoding = C.ENCODING_INVALID
    }

    private fun replaceOutputBuffer(size: Int): ByteBuffer {
        if (outputBuffer.capacity() < size) {
            outputBuffer = ByteBuffer.allocateDirect(size).order(ByteOrder.nativeOrder())
        } else {
            outputBuffer.clear()
        }
        return outputBuffer
    }

    companion object {
        private val EMPTY_BUFFER: ByteBuffer = ByteBuffer.allocateDirect(0).order(ByteOrder.nativeOrder())
    }
}
