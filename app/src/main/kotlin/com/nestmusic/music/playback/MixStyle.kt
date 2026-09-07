/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.playback

import kotlin.math.cos
import kotlin.math.sin

/**
 * Volume curves used by the DJ-style transition engine. Each style defines how the
 * outgoing track fades out and how the incoming track fades in over the overlap
 * window, as a function of progress [p] in the range 0f..1f (0 = mix starts,
 * 1 = outgoing track fully gone).
 */
enum class MixStyle {
    /**
     * Classic crossfade: both tracks move quadratically, the outgoing one drops
     * off gradually while the incoming one builds up.
     */
    FADE,

    /**
     * Dramatic transition: the outgoing track holds full volume for most of the
     * overlap and then drops, while the incoming track swells in from silence
     * and takes over at the very end.
     */
    RISE,

    /**
     * Equal-power crossfade: sine/cosine curves so that the combined output
     * power (sin^2 + cos^2) stays constant for a level, even-sounding blend.
     */
    SMOOTH;

    /** Volume factor for the incoming track at progress [p]. */
    fun fadeIn(p: Float): Float = when (this) {
        FADE -> {
            val q = 1f - p
            1f - q * q
        }

        RISE -> p * p
        SMOOTH -> sin(p * PI / 2f)
    }

    /** Volume factor for the outgoing track at progress [p]. */
    fun fadeOut(p: Float): Float = when (this) {
        FADE -> {
            val q = 1f - p
            q * q
        }

        RISE -> {
            if (p < RISE_HOLD_POINT) {
                1f
            } else {
                val q = (1f - p) / (1f - RISE_HOLD_POINT)
                q * q
            }
        }

        SMOOTH -> cos(p * PI / 2f)
    }

    private companion object {
        const val PI = 3.141592653589793f
        // Point of the overlap after which the outgoing track is allowed to drop.
        const val RISE_HOLD_POINT = 0.4f
    }
}
