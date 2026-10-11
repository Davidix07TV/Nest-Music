/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Apple Music lyrics styling, ported from SimpMusic's AppleMusicLyricsLines.
 */

package com.nestmusic.music.ui.component

import android.os.Build
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestmusic.music.lyrics.WordTimestamp
import kotlin.math.abs

/**
 * Apple Music renders EVERY lyric line at the same size — what separates the line being sung from
 * the rest is focus, not scale: the active line is opaque and sharp, its neighbours dim and
 * progressively blurred with distance, as if the page had a shallow depth of field.
 *
 * That is the whole trick, and it is why the style is gated on blur support: without the blur the
 * lines merely fade, which the Classic renderer already does better by changing size too.
 *
 * The numbers come from SimpMusic's port of AMLL (the Apple Music-like lyrics player), which in
 * turn measured them off Apple's own screenshots.
 */
private const val BLUR_PER_LINE_EM = 0.095f
private const val BLUR_MAX_EM = 0.45f

// Blur and fade work together rather than either doing the job alone: blur alone leaves far lines
// as bright smears, fade alone leaves them sharp and readable when they should not be.
private const val ACTIVE_LINE_ALPHA = 1f
private const val ALPHA_FALLOFF_PER_LINE = 0.25f
private const val MIN_LINE_ALPHA = 0.25f

// Before the FIRST line is due — an intro, a long instrumental opening — there is no sung line for
// anything to be near, so distance is meaningless and every line is equally "not yet". They are
// dimmed uniformly and NOT blurred: blur means "far from where we are in the song", and during an
// intro nowhere is where we are.
private const val PRE_ROLL_LINE_ALPHA = 0.6f

/** The unsung REMAINDER of the line currently being sung — "ce" in "dan|ce". */
internal val AppleMusicPendingWordColor = Color(0xFF8E8E8E)

/** The line NOT being sung. White at reduced opacity is still white on a dark page. */
internal val AppleMusicInactiveLineColor = Color(0xFF9B9B9B)

/** The ink the sung part of a line is drawn in. White, like Apple — not the theme accent. */
internal val AppleMusicSungWordColor = Color.White

internal val AppleMusicTranslatedColor = Color.White.copy(alpha = 0.78f)
internal val AppleMusicRomanizedColor = Color.White.copy(alpha = 0.62f)

/**
 * Apple sets lyrics far larger than body copy — a line fills most of the width and wraps after a
 * few words, which is what makes the page readable at arm's length while the phone sits on a desk.
 */
internal val AppleMusicLyricFontSize = 28.sp
internal val AppleMusicLyricLineHeight = 34.sp
internal val AppleMusicLyricGap = 20.dp
internal val AppleMusicSubLineFontSize = 14.sp
internal val AppleMusicSubLineHeight = 21.sp
internal val AppleMusicMainToSubGap = 12.dp
internal val AppleMusicLyricPaddingX = 20.dp
internal val AppleMusicLyricCornerRadius = 8.dp
internal val AppleMusicLyricPressedBackground = Color.White.copy(alpha = 0.067f)

/**
 * Rich-sync emphasis. AMLL derives both from the word's own duration and position inside the wipe;
 * the constants below are what SimpMusic resolved that maths to, and they are what makes a held
 * note flare while a thrown-away syllable barely lights up.
 */
private const val EMP_AMOUNT_REF_MS = 400f
private const val EMP_BLUR_REF_MS = 250f

/** The soft white halo a word carries while it is being sung. */
internal fun appleMusicGlow(alpha: Float, blurRadiusPx: Float): Shadow =
    Shadow(
        color = Color.White.copy(alpha = alpha.coerceIn(0f, 1f)),
        offset = Offset.Zero,
        blurRadius = blurRadiusPx,
    )

/**
 * Modifier.blur is backed by RenderEffect, added in API 31 (Android 12, VERSION_CODES.S). Below
 * that the modifier is a documented no-op, so the style has to be gated rather than degraded.
 */
internal val isAppleMusicBlurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * Depth-of-field treatment for one lyric line. [distanceFromCurrent] is signed line distance from
 * the line being sung; its magnitude drives both blur and dimming.
 *
 * Both values are animated rather than applied outright, so the focus glides down the page with the
 * song instead of snapping.
 */
@Composable
internal fun Modifier.appleMusicLyricFocus(
    distanceFromCurrent: Int,
    blurEnabled: Boolean = isAppleMusicBlurSupported,
    hasActiveLine: Boolean = true,
    // An unsynced sheet has no sung line and never will have one, so every line IS the sung line:
    // full opacity, no blur. Deliberately NOT folded into [hasActiveLine], which is false in two
    // situations that want opposite treatments — this one, and the pre-roll of a SYNCED sheet,
    // where a sung line is coming and PRE_ROLL_LINE_ALPHA is dimmer on purpose.
    allLinesCurrent: Boolean = false,
): Modifier {
    // Signed: negative means this line has already been sung. AMLL adds one to that side so the
    // page behind the singer recedes faster than the page ahead of it.
    val distance =
        when {
            distanceFromCurrent < 0 -> abs(distanceFromCurrent) + 1
            else -> distanceFromCurrent
        }
    val fontSizeDp = with(LocalDensity.current) { AppleMusicLyricFontSize.toDp() }
    val targetBlur: Dp =
        if (!blurEnabled || allLinesCurrent || !hasActiveLine || distanceFromCurrent == 0) {
            0.dp
        } else {
            fontSizeDp * (distance * BLUR_PER_LINE_EM).coerceAtMost(BLUR_MAX_EM)
        }
    val targetAlpha =
        when {
            allLinesCurrent -> ACTIVE_LINE_ALPHA
            !hasActiveLine -> PRE_ROLL_LINE_ALPHA
            distanceFromCurrent == 0 -> ACTIVE_LINE_ALPHA
            else -> (1f - distance * ALPHA_FALLOFF_PER_LINE).coerceAtLeast(MIN_LINE_ALPHA)
        }

    val blurRadius by animateDpAsState(targetValue = targetBlur, animationSpec = tween(400), label = "appleMusicLyricBlur")
    val lineAlpha by animateFloatAsState(targetValue = targetAlpha, animationSpec = tween(400), label = "appleMusicLyricAlpha")

    // alpha BEFORE blur: blurring an already-faded line keeps the two effects independent, whereas
    // fading a blurred layer washes the blur out into a flat smear.
    return this
        .alpha(lineAlpha)
        .then(
            if (blurRadius > 0.dp) {
                // Unbounded, NOT the default. blur(radius) alone uses BlurredEdgeTreatment.Rectangle,
                // which clips the blur to the line's own bounds — so the softened glyphs get sliced
                // off square at the edges and the line reads as a smudged block rather than an
                // out-of-focus word. Unbounded lets the blur bleed past the bounds.
                Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded)
            } else {
                Modifier
            },
        )
}

/**
 * Alpha one lyric line should carry on the Apple page, exposed so callers that paint text
 * themselves (the experimental canvas renderer) can match the Compose path exactly.
 */
internal fun appleMusicFocusAlpha(
    distanceFromCurrent: Int,
    hasActiveLine: Boolean = true,
): Float {
    val distance =
        when {
            distanceFromCurrent < 0 -> abs(distanceFromCurrent) + 1
            else -> distanceFromCurrent
        }
    return when {
        !hasActiveLine -> PRE_ROLL_LINE_ALPHA
        distanceFromCurrent == 0 -> ACTIVE_LINE_ALPHA
        else -> (1f - distance * ALPHA_FALLOFF_PER_LINE).coerceAtLeast(MIN_LINE_ALPHA)
    }
}

/** Blur radius, in dp, the Apple page gives a line [distanceFromCurrent] lines away from the song. */
internal fun appleMusicFocusBlur(
    distanceFromCurrent: Int,
    fontSizeDp: Float,
): Float {
    val distance =
        when {
            distanceFromCurrent < 0 -> abs(distanceFromCurrent) + 1
            else -> distanceFromCurrent
        }
    if (distanceFromCurrent == 0) return 0f
    return fontSizeDp * (distance * BLUR_PER_LINE_EM).coerceAtMost(BLUR_MAX_EM)
}

/** Scale and halo a sung word carries, derived from how long it is held. Not used for line sync. */
internal fun appleMusicWordEmphasis(wordDurationMs: Long): Pair<Float, Float> {
    val duration = wordDurationMs.coerceAtLeast(40L).toFloat()
    val amount = (duration / EMP_AMOUNT_REF_MS).coerceIn(0f, 1.4f)
    val blur = (duration / EMP_BLUR_REF_MS).coerceIn(0f, 1.6f)
    return amount to blur
}

/**
 * One lyric line, Apple Music style, for renderers that paint through Compose rather than Canvas:
 * same size for every line (the Classic renderer swaps sizes instead), white while sung, grey while
 * not, with the romanisation and the translation underneath.
 *
 * Focus (blur + dimming) is applied by the caller through [appleMusicLyricFocus] so the depth of
 * field wraps the whole line — translation included — instead of only the lyric.
 */
@Composable
internal fun AppleMusicLyricsLineItem(
    mainText: String,
    subText: String?,
    translatedText: String?,
    isCurrent: Boolean,
    textAlign: TextAlign,
    isBackground: Boolean,
    fontSize: Float,
    lineSpacing: Float,
    modifier: Modifier = Modifier,
) {
    val bodyColor = if (isCurrent) AppleMusicSungWordColor else AppleMusicInactiveLineColor
    val scale = if (isBackground) 0.8f else 1f
    Column(modifier = modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(AppleMusicLyricGap))
        Text(
            text = mainText,
            modifier = Modifier.fillMaxWidth(),
            color = bodyColor,
            textAlign = textAlign,
            fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Medium,
            fontSize = (fontSize * scale).sp,
            lineHeight = (fontSize * lineSpacing * scale).sp,
        )
        subText?.let { text ->
            Spacer(modifier = Modifier.height(AppleMusicMainToSubGap))
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = AppleMusicSubLineFontSize,
                        lineHeight = AppleMusicSubLineHeight,
                    ),
                // Follows the line's own state: readable white-ish on the sung line, the same grey
                // as the lyric everywhere else.
                color = if (isCurrent) AppleMusicRomanizedColor else AppleMusicInactiveLineColor,
                textAlign = textAlign,
            )
        }
        translatedText?.let { text ->
            Spacer(modifier = Modifier.height(AppleMusicMainToSubGap))
            Text(
                text = text,
                modifier = Modifier.fillMaxWidth(),
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = AppleMusicSubLineFontSize,
                        lineHeight = AppleMusicSubLineHeight,
                    ),
                color = if (isCurrent) AppleMusicTranslatedColor else AppleMusicInactiveLineColor,
                textAlign = textAlign,
            )
        }
        Spacer(modifier = Modifier.height(AppleMusicLyricGap))
    }
}

/**
 * Fraction of a word that has been sung at [positionMs]. Kept out of the drawing code so every
 * renderer measures a wipe the same way.
 */
internal fun appleMusicWordProgress(
    word: WordTimestamp,
    positionMs: Long,
    isWordSung: Boolean,
    isWordActive: Boolean,
): Float =
    when {
        isWordSung -> 1f
        !isWordActive -> 0f
        else -> {
            val startMs = (word.startTime * 1000).toLong()
            val endMs = (word.endTime * 1000).toLong()
            ((positionMs - startMs).toFloat() / (endMs - startMs).coerceAtLeast(1L).toFloat()).coerceIn(0f, 1f)
        }
    }
