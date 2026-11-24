/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Apple Music lyrics styling upgraded to Nest Music's exact depth-of-field & typography metrics.
 */

package com.nestmusic.music.ui.component

import android.os.Build
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs

private val NEST_LYRIC_EASING = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)
private val NEST_FALLOFF_ALPHA = floatArrayOf(1f, 0.80f, 0.70f, 0.58f, 0.46f)
private val NEST_FALLOFF_BLUR = arrayOf(0.dp, 1.dp, 1.dp, 1.7.dp, 2.4.dp)
private const val PRE_ROLL_LINE_ALPHA = 0.58f

internal val AppleMusicPendingWordColor = Color.White.copy(alpha = 0.34f)
internal val AppleMusicInactiveLineColor = Color.White.copy(alpha = 0.72f)
internal val AppleMusicSungWordColor = Color.White
internal val AppleMusicTranslatedColor = Color.White.copy(alpha = 0.72f)
internal val AppleMusicRomanizedColor = Color.White.copy(alpha = 0.72f)

internal val AppleMusicLyricFontSize = 34.sp
internal val AppleMusicLyricLineHeight = 41.sp
internal val AppleMusicSubLineFontSize = 20.sp
internal val AppleMusicSubLineHeight = 25.sp
internal val AppleMusicMainToSubGap = 6.dp
internal val AppleMusicLyricPaddingX = 24.dp
internal val AppleMusicLyricCornerRadius = 14.dp

internal val isAppleMusicBlurSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

@Composable
internal fun Modifier.appleMusicLyricFocus(
    distanceFromCurrent: Int,
    blurEnabled: Boolean = isAppleMusicBlurSupported,
    hasActiveLine: Boolean = true,
    allLinesCurrent: Boolean = false,
): Modifier {
    val absDist = abs(distanceFromCurrent)
    val clamped = absDist.coerceAtMost(NEST_FALLOFF_ALPHA.lastIndex)

    val targetBlur: Dp =
        if (!blurEnabled || allLinesCurrent || !hasActiveLine || distanceFromCurrent == 0) {
            0.dp
        } else {
            NEST_FALLOFF_BLUR[clamped]
        }

    val targetAlpha =
        when {
            allLinesCurrent -> 0.94f
            !hasActiveLine -> PRE_ROLL_LINE_ALPHA
            else -> NEST_FALLOFF_ALPHA[clamped]
        }

    val blurRadius by animateDpAsState(
        targetValue = targetBlur,
        animationSpec = tween(durationMillis = 360, easing = NEST_LYRIC_EASING),
        label = "appleMusicLyricBlur",
    )
    val lineAlpha by animateFloatAsState(
        targetValue = targetAlpha,
        animationSpec = tween(durationMillis = 360, easing = NEST_LYRIC_EASING),
        label = "appleMusicLyricAlpha",
    )

    return this
        .alpha(lineAlpha)
        .then(
            if (blurRadius > 0.dp) {
                Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded)
            } else {
                Modifier
            },
        )
}
