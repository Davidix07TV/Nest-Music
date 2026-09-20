/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.playback

import android.media.AudioManager
import android.media.ToneGenerator
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Pure A-B loop state, free of player and Android types so it can be unit tested.
 *
 * A loop is active as soon as both points are set; [loopTarget] then tells the caller where
 * playback has to jump back to.
 */
class PracticeLoopState(
    private val minLengthMs: Long = MIN_LOOP_LENGTH_MS,
    private val overshootToleranceMs: Long = OVERSHOOT_TOLERANCE_MS,
    private val seekSettleMs: Long = SEEK_SETTLE_MS,
) {
    var startMs: Long? = null
        private set

    var endMs: Long? = null
        private set

    private var settleUntilMs = Long.MIN_VALUE

    val isActive: Boolean
        get() = startMs != null && endMs != null

    val lengthMs: Long?
        get() = if (isActive) endMs!! - startMs!! else null

    /**
     * Marks the start point at [positionMs]. Returns false when the position cannot be used.
     * A start point too close to (or after) the current end point drops the end point instead
     * of leaving an inverted loop behind.
     */
    fun setStart(positionMs: Long): Boolean {
        if (positionMs < 0L) return false
        startMs = positionMs
        val end = endMs
        if (end != null && end - positionMs < minLengthMs) endMs = null
        return true
    }

    /**
     * Marks the end point at [positionMs]. Returns false when no start point is set or the
     * section would be shorter than [minLengthMs].
     */
    fun setEnd(positionMs: Long): Boolean {
        val start = startMs ?: return false
        if (positionMs - start < minLengthMs) return false
        endMs = positionMs
        return true
    }

    fun clear() {
        startMs = null
        endMs = null
        settleUntilMs = Long.MIN_VALUE
    }

    /**
     * Records that the player was just seeked back to the start of the loop. Position reads
     * need a moment to catch up with a seek, and acting on a stale one would rewind playback
     * over and over.
     */
    fun onLoopSeekIssued(nowMs: Long) {
        settleUntilMs = nowMs + seekSettleMs
    }

    /**
     * Returns the position playback should jump to when it moved past the end point, or null
     * when nothing has to be done. The phase inside the loop is preserved so that a late tick
     * does not shift the loop backwards.
     */
    fun loopTarget(positionMs: Long, nowMs: Long): Long? {
        if (nowMs < settleUntilMs) return null
        val start = startMs ?: return null
        val end = endMs ?: return null
        if (positionMs < end) return null
        // Further than the tolerance the position comes from a seek: do not fight the user.
        if (positionMs > end + overshootToleranceMs) return null
        val length = end - start
        return start + (positionMs - end) % length
    }

    /** How far playback is into the loop, 0f..1f, or null when no loop is active. */
    fun progress(positionMs: Long): Float? {
        val start = startMs ?: return null
        val end = endMs ?: return null
        return ((positionMs - start).toFloat() / (end - start)).coerceIn(0f, 1f)
    }

    companion object {
        /** Shorter sections are not loopable: the seek alone would eat them. */
        const val MIN_LOOP_LENGTH_MS = 500L

        /** Past this much overshoot the position is assumed to come from a manual seek. */
        const val OVERSHOOT_TOLERANCE_MS = 3_000L

        /** How long position reads are ignored after the loop seeked back. */
        const val SEEK_SETTLE_MS = 120L
    }
}

/**
 * Drives [PracticeLoopState] against the service player: repeats the section between the two
 * points, optionally with a count-in, and exposes the state the player UI renders.
 *
 * Owned by [MusicService]; the player instance is swapped whenever the service recreates it.
 */
class PracticeLoop(
    private val scope: CoroutineScope,
    private val elapsedRealtime: () -> Long = SystemClock::elapsedRealtime,
) : Player.Listener {
    private companion object {
        private const val TICK_MS = 60L
        private const val COUNT_IN_BEEPS = 3
        private const val COUNT_IN_BEEP_INTERVAL_MS = 600L
        private const val COUNT_IN_BEEP_DURATION_MS = 120
        private const val COUNT_IN_VOLUME = 70
    }

    private val state = PracticeLoopState()
    private var tickerJob: Job? = null
    private var mediaId: String? = null

    /** Player the loop drives; [MusicService] replaces it when it recreates its player. */
    var player: Player? = null

    /** True once the user started a practice session, which is what the player UI shows. */
    var enabled by mutableStateOf(false)
        private set

    var startMs by mutableStateOf<Long?>(null)
        private set

    var endMs by mutableStateOf<Long?>(null)
        private set

    /** Repeats completed in the current session. */
    var repetitions by mutableIntStateOf(0)
        private set

    /** True while the count-in beeps play before a repeat. */
    var isCountingIn by mutableStateOf(false)
        private set

    /** Whether a count-in is played before every repeat; kept in sync with a preference. */
    var countInEnabled: Boolean = false

    val isActive: Boolean
        get() = state.isActive

    val lengthMs: Long?
        get() = state.lengthMs

    fun startSession() {
        enabled = true
    }

    fun endSession() {
        enabled = false
        clear()
    }

    fun setStartHere() {
        val position = currentPosition() ?: return
        if (!state.setStart(position)) return
        mediaId = player?.currentMediaItem?.mediaId
        syncPoints()
        repetitions = 0
        ensureTicker()
    }

    fun setEndHere() {
        val position = currentPosition() ?: return
        if (!state.setEnd(position)) return
        mediaId = player?.currentMediaItem?.mediaId
        syncPoints()
        ensureTicker()
    }

    fun clear() {
        state.clear()
        tickerJob?.cancel()
        tickerJob = null
        mediaId = null
        syncPoints()
        repetitions = 0
        isCountingIn = false
    }

    fun progress(positionMs: Long): Float? = state.progress(positionMs)

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        val loopMediaId = mediaId ?: return
        // A different track means the points no longer describe anything playable.
        if (mediaItem?.mediaId != loopMediaId) clear()
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        // The ticker stops itself while playback is paused, so resume it here.
        if (isPlaying && state.isActive) ensureTicker()
    }

    private fun syncPoints() {
        startMs = state.startMs
        endMs = state.endMs
    }

    private fun currentPosition(): Long? {
        val player = player ?: return null
        return runCatching { player.currentPosition }.getOrNull()?.takeIf { it >= 0L }
    }

    private fun ensureTicker() {
        if (tickerJob?.isActive == true) return
        tickerJob =
            scope.launch {
                while (state.isActive && isActive) {
                    if (!tick()) break
                    delay(TICK_MS)
                }
            }
    }

    /** Returns false when the loop has nothing to watch anymore. */
    private suspend fun tick(): Boolean {
        val player = player ?: return false
        if (!player.isPlaying) return false
        if (isCountingIn) return true
        val target = state.loopTarget(player.currentPosition, elapsedRealtime()) ?: return true

        if (countInEnabled) {
            countInAndRepeat(player, target)
        } else {
            restartSection(player, target)
        }
        return true
    }

    private fun restartSection(player: Player, targetMs: Long) {
        player.seekTo(targetMs)
        state.onLoopSeekIssued(elapsedRealtime())
        repetitions++
    }

    /**
     * Pauses playback, plays three beeps and then restarts the section, so the repeat does not
     * come as a surprise while practising.
     */
    private suspend fun countInAndRepeat(player: Player, targetMs: Long) {
        isCountingIn = true
        val tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, COUNT_IN_VOLUME) }.getOrNull()
        try {
            player.pause()
            repeat(COUNT_IN_BEEPS) {
                if (!state.isActive) return
                runCatching { tone?.startTone(ToneGenerator.TONE_PROP_BEEP, COUNT_IN_BEEP_DURATION_MS) }
                delay(COUNT_IN_BEEP_INTERVAL_MS)
            }
            if (!state.isActive) return
            restartSection(player, targetMs)
            // If playback was resumed during the count-in, the user is in charge of it.
            if (!player.playWhenReady) player.play()
        } finally {
            runCatching { tone?.release() }
            isCountingIn = false
        }
    }
}
