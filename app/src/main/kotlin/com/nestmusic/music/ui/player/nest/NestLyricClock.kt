/*
 * Adapted verbatim from Nest Music (Nest Music) LyricClock.kt under GPL-3.0.
 */

package com.nestmusic.music.ui.player.nest

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import kotlin.math.abs

internal class NestLyricClock(startMs: Long) {
    var displayedMs: Double = startMs.toDouble()
        private set

    private var lastNowMs = Double.NaN
    private var lastReportMs = Long.MIN_VALUE
    private var lastSampledAtMs = Double.NaN
    private var heldAtMs = Double.NEGATIVE_INFINITY
    private var lastDiscontinuity: Long? = null
    private var jumpPending = false

    private var anchorAtMs = Double.NaN
    private var anchorMs = 0.0
    private var rate = 1.0

    private val historyAt = DoubleArray(HISTORY)
    private val historyMs = DoubleArray(HISTORY)
    private var historySize = 0
    private var rateSettled = false

    private var suspectAtMs = Double.NaN
    private var suspectMs = 0L
    private var rejections = 0

    private var glideStartMs = Double.NaN
    private var glideOffsetMs = 0.0

    fun restart() {
        lastNowMs = Double.NaN
        jumpPending = false
        glideStartMs = Double.NaN
        clearSuspect()
    }

    fun hold(positionMs: Long, nowMs: Double) {
        restart()
        displayedMs = positionMs.toDouble()
        heldAtMs = nowMs
    }

    fun frame(nowMs: Double, reportedMs: Long, sampledAtMs: Double, discontinuity: Long): Long {
        val firstOfRun = lastNowMs.isNaN()
        if (discontinuity != lastDiscontinuity) {
            if (lastDiscontinuity != null && !firstOfRun) jumpPending = true
            lastDiscontinuity = discontinuity
        }
        val isNew = reportedMs != lastReportMs ||
            (!sampledAtMs.isNaN() && sampledAtMs != lastSampledAtMs)
        lastReportMs = reportedMs
        lastSampledAtMs = sampledAtMs

        if (firstOfRun) {
            lastNowMs = nowMs
            val takenAt = when {
                sampledAtMs.isNaN() || sampledAtMs <= heldAtMs -> nowMs
                else -> minOf(sampledAtMs, nowMs)
            }
            anchor(takenAt, reportedMs)
            displayedMs = targetAt(nowMs)
            return displayedMs.toLong()
        }

        val dt = (nowMs - lastNowMs).coerceAtLeast(0.0)
        lastNowMs = nowMs
        advance(nowMs, dt)

        if (isNew) {
            val takenAt = if (sampledAtMs.isNaN()) nowMs else minOf(sampledAtMs, nowMs)
            if (takenAt >= anchorAtMs || jumpPending) take(nowMs, takenAt, reportedMs)
        }
        return displayedMs.toLong()
    }

    private fun take(nowMs: Double, takenAt: Double, reportedMs: Long) {
        if (jumpPending) {
            jumpPending = false
            clearSuspect()
            anchor(takenAt, reportedMs)
            jumpTo(nowMs)
            return
        }
        val elapsed = takenAt - anchorAtMs
        if (abs(reportedMs - (anchorMs + elapsed * rate)) <= tolerance(elapsed)) {
            clearSuspect()
            anchor(takenAt, reportedMs, continues = true)
            return
        }
        val sinceSuspect = takenAt - suspectAtMs
        val agrees = !suspectAtMs.isNaN() && sinceSuspect >= 0 &&
            abs(reportedMs - (suspectMs + sinceSuspect * rate)) <= tolerance(sinceSuspect)
        rejections++
        if (agrees) {
            val firstAt = suspectAtMs
            val first = suspectMs
            clearSuspect()
            anchor(firstAt, first)
            anchor(takenAt, reportedMs, continues = true)
        } else if (rejections > MAX_REJECTIONS) {
            clearSuspect()
            anchor(takenAt, reportedMs)
        } else {
            suspectAtMs = takenAt
            suspectMs = reportedMs
        }
    }

    private fun tolerance(elapsedMs: Double): Double =
        OUTLIER_MS + abs(elapsedMs) * if (rateSettled) RATE_SLACK else UNSETTLED_RATE_SLACK

    private fun anchor(atMs: Double, positionMs: Long, continues: Boolean = false) {
        anchorAtMs = atMs
        anchorMs = positionMs.toDouble()
        if (!continues) historySize = 0
        if (historySize == HISTORY) {
            historyAt.copyInto(historyAt, 0, 1, HISTORY)
            historyMs.copyInto(historyMs, 0, 1, HISTORY)
            historySize--
        }
        historyAt[historySize] = atMs
        historyMs[historySize] = anchorMs
        historySize++
        learnRate()
    }

    private fun learnRate() {
        val newest = historySize - 1
        if (newest < 1) return
        var oldest = 0
        while (oldest < newest && historyAt[newest] - historyAt[oldest] > RATE_WINDOW_MS) oldest++
        val span = historyAt[newest] - historyAt[oldest]
        if (span >= MIN_RATE_SPAN_MS) {
            rate = ((historyMs[newest] - historyMs[oldest]) / span).coerceIn(MIN_RATE, MAX_RATE)
            rateSettled = true
            return
        }
        val pairSpan = historyAt[newest] - historyAt[newest - 1]
        if (!rateSettled && pairSpan >= MIN_PAIR_SPAN_MS) {
            val measured = ((historyMs[newest] - historyMs[newest - 1]) / pairSpan)
                .coerceIn(MIN_RATE, MAX_RATE)
            rate += (measured - rate) * PAIR_LEARNING
        }
    }

    private fun clearSuspect() {
        suspectAtMs = Double.NaN
        rejections = 0
    }

    private fun advance(nowMs: Double, dt: Double) {
        val target = targetAt(nowMs)
        val step = dt * rate
        val error = target - (displayedMs + step)
        when {
            !glideStartMs.isNaN() -> {
                val progress = (nowMs - glideStartMs) / GLIDE_MS
                if (progress >= 1.0) {
                    displayedMs = target
                    glideStartMs = Double.NaN
                } else {
                    displayedMs = target + glideOffsetMs * (1.0 - smoothstep(progress))
                }
            }
            error <= -LOST_MS || error >= LOST_MS -> displayedMs = target
            error >= GLIDE_FROM_MS -> {
                startGlide(nowMs - dt, displayedMs - (target - step))
                displayedMs = target + glideOffsetMs * (1.0 - smoothstep(dt / GLIDE_MS))
            }
            else -> {
                val closing = error * (dt / (dt + CORRECTION_MS))
                displayedMs += step + closing.coerceIn(-MAX_SLOW * step, MAX_SLEW * step)
            }
        }
    }

    private fun jumpTo(nowMs: Double) {
        val offset = displayedMs - targetAt(nowMs)
        if (abs(offset) > GLIDE_MAX_MS) {
            displayedMs = targetAt(nowMs)
            glideStartMs = Double.NaN
        } else {
            startGlide(nowMs, offset)
        }
    }

    private fun startGlide(startMs: Double, offsetMs: Double) {
        glideStartMs = startMs
        glideOffsetMs = offsetMs
    }

    private fun targetAt(atMs: Double): Double = anchorMs + (atMs - anchorAtMs) * rate
}

private fun smoothstep(fraction: Double): Double {
    val t = fraction.coerceIn(0.0, 1.0)
    return t * t * (3.0 - 2.0 * t)
}

private const val OUTLIER_MS = 250.0
private const val RATE_SLACK = 0.2
private const val UNSETTLED_RATE_SLACK = 0.75
private const val MAX_REJECTIONS = 2
private const val MIN_RATE_SPAN_MS = 900.0
private const val RATE_WINDOW_MS = 4_000.0
private const val HISTORY = 16
private const val MIN_PAIR_SPAN_MS = 300.0
private const val PAIR_LEARNING = 0.5
private const val MIN_RATE = 0.25
private const val MAX_RATE = 4.0
private const val CORRECTION_MS = 600.0
private const val MAX_SLEW = 0.25
private const val MAX_SLOW = 0.5
private const val GLIDE_FROM_MS = 300.0
private const val GLIDE_MS = 260.0
private const val GLIDE_MAX_MS = 2_000.0
private const val LOST_MS = 3_000.0
private const val SAME_CLOCK_NANOS = 250_000_000L

private fun nanosToMs(nanos: Long): Double = nanos / 1_000_000.0

/**
 * Frame-steered lyric playhead reading [player] on vsync frames while playing
 * and holding exact position while paused or scrubbing, invalidating only draw
 * lambdas that read [MutableLongState.longValue].
 */
@Composable
internal fun rememberNestLyricClock(
    trackKey: Any,
    player: Player,
    isPlaying: Boolean,
    lyricsOffsetMs: Long = 0L,
    sliderOverrideProvider: () -> Long? = { null },
): MutableLongState {
    val currentOffset by rememberUpdatedState(lyricsOffsetMs)
    val currentOverride by rememberUpdatedState(sliderOverrideProvider)

    val clock = remember(trackKey) {
        mutableLongStateOf((player.currentPosition + lyricsOffsetMs).coerceAtLeast(0L))
    }
    val engine = remember(trackKey) { NestLyricClock(clock.longValue) }
    val discontinuity = remember(trackKey) { mutableLongStateOf(0L) }

    DisposableEffect(player, trackKey) {
        val listener = object : Player.Listener {
            override fun onPositionDiscontinuity(
                oldPosition: Player.PositionInfo,
                newPosition: Player.PositionInfo,
                reason: Int,
            ) {
                discontinuity.longValue++
                if (!player.isPlaying) {
                    val pos = (newPosition.positionMs + currentOffset).coerceAtLeast(0L)
                    engine.hold(pos, nanosToMs(System.nanoTime()))
                    clock.longValue = pos
                }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()
    val foreground = lifecycleState.isAtLeast(Lifecycle.State.STARTED)

    LaunchedEffect(engine, player, isPlaying, foreground, trackKey) {
        if (!foreground) return@LaunchedEffect
        if (!isPlaying) {
            val pos = ((currentOverride() ?: player.currentPosition) + currentOffset).coerceAtLeast(0L)
            engine.hold(pos, nanosToMs(System.nanoTime()))
            clock.longValue = pos
            return@LaunchedEffect
        }
        engine.restart()
        while (true) {
            withFrameNanos { frameNanos ->
                val overrideMs = currentOverride()
                if (overrideMs != null) {
                    val scrubbed = (overrideMs + currentOffset).coerceAtLeast(0L)
                    engine.hold(scrubbed, nanosToMs(System.nanoTime()))
                    clock.longValue = scrubbed
                } else {
                    val system = System.nanoTime()
                    val now = if (abs(system - frameNanos) < SAME_CLOCK_NANOS) frameNanos else system
                    val reported = (player.currentPosition + currentOffset).coerceAtLeast(0L)
                    clock.longValue = engine.frame(
                        nowMs = nanosToMs(now),
                        reportedMs = reported,
                        sampledAtMs = nanosToMs(system),
                        discontinuity = discontinuity.longValue,
                    )
                }
            }
        }
    }
    return clock
}
