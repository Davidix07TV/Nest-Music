/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.playback

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.ConnectivityManager
import com.nestmusic.innertube.YouTube
import com.nestmusic.innertube.strategy.ContentHints
import com.nestmusic.music.constants.AudioQuality
import com.nestmusic.music.utils.YTPlayerUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.OkHttpClient
import okhttp3.Request
import timber.log.Timber
import java.io.File
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.sin
import kotlin.random.Random

/**
 * Computes real waveform peaks for a track so the transition editor can show
 * the actual audio shape around the mix point (like Spotify's transition
 * editor).
 *
 * Pipeline: resolve the stream URL for the video id (lowest quality to keep
 * downloads small) -> download the audio file -> decode to PCM with
 * MediaCodec -> compute min/max peaks per bucket. Results are cached on disk
 * in [Context.cacheDir]/waveforms.
 *
 * If analysis fails (no network, decode error, timeout...) a deterministic
 * placeholder waveform seeded by the track id is returned instead, flagged
 * with [Waveform.isApproximate], so the UI always has something to draw.
 */
object WaveformHelper {
    /** Number of peak buckets stored per track. */
    const val BUCKET_COUNT = 600

    private const val TAG = "WaveformHelper"
    private const val CACHE_DIR = "waveforms"
    private const val ANALYSIS_TIMEOUT_MS = 60_000L
    private const val DOWNLOAD_CHUNK_SIZE = 512 * 1024
    // Internal resolution used while decoding; resampled to BUCKET_COUNT at the end.
    private const val WINDOW_SAMPLES = 512

    data class Waveform(
        val peaks: ShortArray,
        val durationMs: Long,
        val isApproximate: Boolean,
    )

    private val inFlight: MutableMap<String, Deferred<Waveform>> =
        Collections.synchronizedMap(HashMap())
    private val memoryCache = ConcurrentHashMap<String, Waveform>()

    private fun cacheDir(context: Context): File =
        File(context.cacheDir, CACHE_DIR).apply { mkdirs() }

    private fun cacheFile(context: Context, mediaId: String): File =
        File(cacheDir(context), "$mediaId.pwk")

    /**
     * Returns the waveform for [mediaId], computing (and caching) it on first
     * use. Never throws: falls back to a deterministic placeholder.
     */
    suspend fun getWaveform(context: Context, mediaId: String): Waveform {
        memoryCache[mediaId]?.let { return it }
        val appContext = context.applicationContext
        try {
            val fromDisk = readCache(appContext, mediaId)
            if (fromDisk != null) {
                memoryCache[mediaId] = fromDisk
                return fromDisk
            }
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Cache read failed for $mediaId")
        }

        inFlight[mediaId]?.let { return it.await() }
        val deferred =
            CoroutineScope(Dispatchers.IO).async {
                val waveform =
                    try {
                        withTimeout(ANALYSIS_TIMEOUT_MS) { analyze(appContext, mediaId) }
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Waveform analysis failed for $mediaId, using placeholder")
                        placeholder(mediaId)
                    }
                runCatching { writeCache(appContext, mediaId, waveform) }
                memoryCache[mediaId] = waveform
                inFlight.remove(mediaId)
                waveform
            }
        inFlight[mediaId] = deferred
        return deferred.await()
    }

    private suspend fun analyze(context: Context, mediaId: String): Waveform =
        withContext(Dispatchers.IO) {
            val tempFile = File(context.cacheDir, "waveform_${mediaId}.tmp")
            try {
                val playbackData =
                    YTPlayerUtils
                        .playerResponseForPlayback(
                            videoId = mediaId,
                            audioQuality = AudioQuality.LOW,
                            connectivityManager =
                                context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager,
                            contentHints = ContentHints(),
                        ).getOrThrow()

                downloadStream(playbackData.streamUrl, playbackData.streamHeaders, tempFile)
                val (peaks, durationMs) = decodePeaks(tempFile)
                Waveform(peaks, durationMs, isApproximate = false)
            } finally {
                tempFile.delete()
            }
        }

    private fun downloadStream(url: String, headers: Map<String, String>, target: File) {
        val client =
            OkHttpClient
                .Builder()
                .proxy(YouTube.proxy)
                .build()
        val request =
            Request
                .Builder()
                .url(url)
                .apply { headers.forEach { (k, v) -> header(k, v) } }
                .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("Stream download failed: HTTP ${response.code}")
            }
            val body = response.body ?: throw java.io.IOException("Empty stream body")
            target.outputStream().use { out ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(DOWNLOAD_CHUNK_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        out.write(buffer, 0, read)
                        total += read
                    }
                    if (total == 0L) throw java.io.IOException("Downloaded 0 bytes")
                    Timber.tag(TAG).d("Downloaded ${total / 1024} KB for waveform analysis")
                }
            }
        }
    }

    /**
     * Decodes the audio file and returns per-bucket peak amplitudes (0..32767)
     * plus the total duration in ms.
     */
    private fun decodePeaks(file: File): Pair<ShortArray, Long> {
        val extractor = MediaExtractor()
        val codec = MediaCodec()
        try {
            extractor.setDataSource(file.absolutePath)
            var trackIndex = -1
            for (i in 0 until extractor.trackCount) {
                val trackMime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?: continue
                if (trackMime.startsWith("audio/")) {
                    trackIndex = i
                    break
                }
            }
            if (trackIndex == -1) throw IllegalArgumentException("No audio track found")

            extractor.selectTrack(trackIndex)
            val format = extractor.getTrackFormat(trackIndex)
            val sampleFormat = format.getInteger(MediaFormat.KEY_SAMPLE_FORMAT)
            if (sampleFormat != AudioFormat.ENCODING_PCM_16BIT) {
                throw IllegalArgumentException("Unsupported sample format: $sampleFormat")
            }
            val sampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channelCount = format.getInteger(MediaFormat.KEY_CHANNEL_COUNT)

            codec.configure(format, null, null, 0)
            codec.start()

            // Running min/max over fixed-size mono windows; resampled later.
            val windowPeaks = ArrayList<Short>()
            var windowMin = Short.MAX_VALUE
            var windowMax = Short.MIN_VALUE
            var windowCount = 0
            var totalFrames = 0L

            var inputEos = false
            var outputEos = false
            val outInfo = MediaCodec.BufferInfo()

            while (!outputEos) {
                if (!inputEos) {
                    val inIndex = codec.dequeueInputBuffer(10_000)
                    if (inIndex >= 0) {
                        val inBuffer = codec.getInputBuffer(inIndex)!!
                        inBuffer.clear()
                        val sampleSize = extractor.readSampleData(inBuffer, 0)
                        if (sampleSize >= 0) {
                            extractor.advance()
                            codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                        } else {
                            codec.queueInputBuffer(inIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEos = true
                        }
                    }
                }
                val outIndex = codec.dequeueOutputBuffer(outInfo, 10_000)
                if (outIndex >= 0) {
                    if (outInfo.size > 0) {
                        val outBuffer = codec.getOutputBuffer(outIndex)!!
                        val frameCount = outInfo.size / (2 * channelCount)
                        totalFrames += frameCount
                        // Take the first channel for a mono representative amplitude.
                        for (frame in 0 until frameCount) {
                            val sample = outBuffer.getShort(frame * channelCount * 2).toInt().toShort()
                            if (sample < windowMin) windowMin = sample
                            if (sample > windowMax) windowMax = sample
                            windowCount++
                            if (windowCount == WINDOW_SAMPLES) {
                                windowPeaks.add(((windowMax - windowMin) / 2).coerceIn(0, 32767).toShort())
                                windowMin = Short.MAX_VALUE
                                windowMax = Short.MIN_VALUE
                                windowCount = 0
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIndex, false)
                    if (outInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputEos = true
                    }
                }
            }
            if (windowCount > 0) {
                windowPeaks.add(((windowMax - windowMin) / 2).coerceIn(0, 32767).toShort())
            }
            if (windowPeaks.isEmpty()) throw IllegalStateException("No audio samples decoded")

            val durationMs = totalFrames * 1000L / sampleRate
            return resamplePeaks(windowPeaks, durationMs)
        } finally {
            runCatching { codec.stop() }
            codec.release()
            extractor.release()
        }
    }

    /** Resamples the internal window peaks down to exactly [BUCKET_COUNT] buckets. */
    private fun resamplePeaks(
        windowPeaks: List<Short>,
        durationMs: Long,
    ): Pair<ShortArray, Long> {
        val peaks = ShortArray(BUCKET_COUNT)
        val windowCount = windowPeaks.size
        for (b in 0 until BUCKET_COUNT) {
            val from = (b.toLong() * windowCount / BUCKET_COUNT).toInt()
            val to =
                (((b + 1).toLong() * windowCount / BUCKET_COUNT).toInt())
                    .coerceAtLeast(from + 1)
            var peak = 0
            for (i in from until to.coerceAtMost(windowCount)) {
                val v = windowPeaks[i].toInt()
                if (v > peak) peak = v
            }
            peaks[b] = peak.coerceAtLeast(64).toShort()
        }
        return peaks to durationMs
    }

    /**
     * Deterministic organic-looking waveform seeded by [mediaId]. Used when
     * real analysis is not possible; still stable across calls.
     */
    private fun placeholder(mediaId: String): Waveform {
        val random = Random(abs(mediaId.hashCode()).toLong() * 0x9E3779B97F4A7C15L)
        val peaks = ShortArray(BUCKET_COUNT)
        var level = 0.5f
        for (i in peaks.indices) {
            level = (level + (random.nextFloat() - 0.5f) * 0.3f).coerceIn(0.18f, 1f)
            val envelope = 0.55f + 0.45f * sin(i / 14f)
            peaks[i] = (level * envelope * 28000f).toInt().coerceIn(120, 32767).toShort()
        }
        return Waveform(peaks, 0L, isApproximate = true)
    }

    private fun readCache(context: Context, mediaId: String): Waveform? {
        val f = cacheFile(context, mediaId)
        if (!f.exists() || f.length() < 12L) return null
        return f.inputStream().use { input ->
            val data = input.buffered()
            val count = data.readInt()
            val durationMs = data.readLong()
            if (count != BUCKET_COUNT) return null
            val peaks = ShortArray(BUCKET_COUNT)
            for (i in 0 until BUCKET_COUNT) {
                peaks[i] = data.readShort()
            }
            Waveform(peaks, durationMs, isApproximate = false)
        }
    }

    private fun writeCache(context: Context, mediaId: String, waveform: Waveform) {
        if (waveform.isApproximate) return
        val f = cacheFile(context, mediaId)
        f.outputStream().use { output ->
            val data = output.buffered()
            data.writeInt(BUCKET_COUNT)
            data.writeLong(waveform.durationMs)
            waveform.peaks.forEach { data.writeShort(it.toInt()) }
        }
    }
}
