/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.playback

import kotlin.math.cos
import kotlin.math.sin

/**
 * Presets exposed by the transition editor.
 *
 * AUTO deliberately keeps the old crossfade behaviour. It is not a fourth
 * curve that needs to be guessed by the editor: when selected, the pair falls
 * back to the same global crossfade rules used before per-pair transitions
 * existed.
 */
enum class MixStyle {
    AUTO,
    FADE,
    RISE,
    BLEND,

    /** Kept so transition files written by early beta builds remain readable. */
    @Deprecated("Use BLEND")
    SMOOTH;

    /** Volume factor for the incoming track at progress [p]. */
    fun fadeIn(
        p: Float,
        volumeCurve: MixVolumeCurve = MixVolumeCurve.LINEAR,
    ): Float {
        val progress = p.coerceIn(0f, 1f)
        return when (this) {
            AUTO -> if (volumeCurve == MixVolumeCurve.EQUAL_POWER) {
                sin(progress * PI / 2f)
            } else {
                autoFadeIn(progress)
            }
            FADE -> when (volumeCurve) {
                MixVolumeCurve.LINEAR -> progress
                MixVolumeCurve.EQUAL_POWER -> sin(progress * PI / 2f)
            }
            RISE -> when (volumeCurve) {
                MixVolumeCurve.LINEAR -> progress * progress
                MixVolumeCurve.EQUAL_POWER -> sin(progress * PI / 2f)
            }
            BLEND -> when (volumeCurve) {
                MixVolumeCurve.LINEAR -> progress
                MixVolumeCurve.EQUAL_POWER -> sin(progress * PI / 2f)
            }
            SMOOTH -> sin(progress * PI / 2f)
        }
    }

    /** Volume factor for the outgoing track at progress [p]. */
    fun fadeOut(
        p: Float,
        volumeCurve: MixVolumeCurve = MixVolumeCurve.LINEAR,
    ): Float {
        val progress = p.coerceIn(0f, 1f)
        return when (this) {
            AUTO -> if (volumeCurve == MixVolumeCurve.EQUAL_POWER) {
                cos(progress * PI / 2f)
            } else {
                autoFadeOut(progress)
            }
            FADE -> when (volumeCurve) {
                MixVolumeCurve.LINEAR -> 1f - progress
                MixVolumeCurve.EQUAL_POWER -> cos(progress * PI / 2f)
            }
            RISE -> {
                if (progress < RISE_HOLD_POINT) {
                    1f
                } else {
                    val remaining = ((1f - progress) / (1f - RISE_HOLD_POINT)).coerceIn(0f, 1f)
                    when (volumeCurve) {
                        MixVolumeCurve.LINEAR -> remaining * remaining
                        MixVolumeCurve.EQUAL_POWER -> cos((1f - remaining) * PI / 2f)
                    }
                }
            }
            BLEND -> when (volumeCurve) {
                MixVolumeCurve.LINEAR -> 1f - progress
                MixVolumeCurve.EQUAL_POWER -> cos(progress * PI / 2f)
            }
            SMOOTH -> cos(progress * PI / 2f)
        }
    }

    private fun autoFadeIn(p: Float): Float {
        // This is the quadratic curve used by the original Android crossfade
        // implementation. Keep it stable for existing users who select Auto.
        val remaining = 1f - p
        return 1f - remaining * remaining
    }

    private fun autoFadeOut(p: Float): Float {
        val remaining = 1f - p
        return remaining * remaining
    }

    private companion object {
        const val PI = 3.141592653589793f
        const val RISE_HOLD_POINT = 0.4f
    }
}

enum class MixVolumeCurve {
    LINEAR,
    EQUAL_POWER,
}

enum class MixEqMode {
    NONE,
    LOW_HIGH_SWAP,
}

enum class MixEffect {
    NONE,
    HIGH_PASS,
    LOW_PASS,
}

/**
 * A single place for the editor's preset values. Keeping the values here also
 * lets the service and non-Compose callers use the same defaults.
 */
data class MixPreset(
    val style: MixStyle,
    val durationMs: Long,
    val volumeCurve: MixVolumeCurve,
    val eqMode: MixEqMode,
    val effect: MixEffect,
) {
    companion object {
        const val AUTO_DURATION_MS = 5_000L
        const val FADE_DURATION_MS = 3_000L
        const val RISE_DURATION_MS = 5_000L
        const val BLEND_DURATION_MS = 8_000L

        fun forStyle(style: MixStyle, autoDurationMs: Long = AUTO_DURATION_MS): MixPreset =
            when (style) {
                MixStyle.AUTO -> MixPreset(
                    style = MixStyle.AUTO,
                    durationMs = autoDurationMs,
                    volumeCurve = MixVolumeCurve.LINEAR,
                    eqMode = MixEqMode.NONE,
                    effect = MixEffect.NONE,
                )
                MixStyle.FADE -> MixPreset(
                    style = MixStyle.FADE,
                    durationMs = FADE_DURATION_MS,
                    volumeCurve = MixVolumeCurve.LINEAR,
                    eqMode = MixEqMode.NONE,
                    effect = MixEffect.NONE,
                )
                MixStyle.RISE -> MixPreset(
                    style = MixStyle.RISE,
                    durationMs = RISE_DURATION_MS,
                    volumeCurve = MixVolumeCurve.LINEAR,
                    eqMode = MixEqMode.NONE,
                    effect = MixEffect.NONE,
                )
                MixStyle.BLEND, MixStyle.SMOOTH -> MixPreset(
                    style = MixStyle.BLEND,
                    durationMs = BLEND_DURATION_MS,
                    volumeCurve = MixVolumeCurve.EQUAL_POWER,
                    eqMode = MixEqMode.NONE,
                    effect = MixEffect.NONE,
                )
            }
    }
}

fun MixStyle.displayStyle(): MixStyle = if (this == MixStyle.SMOOTH) MixStyle.BLEND else this
