/*
 * Adapted verbatim from Nest Music (Nest Music) ThinSlider.kt under GPL-3.0.
 */

package com.nestmusic.music.ui.player.nest

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.horizontalDrag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val REST_TRACK_HEIGHT = 7.dp
private val DRAGGING_TRACK_HEIGHT = 12.dp
private val TOUCH_HEIGHT = 28.dp
private const val MORPH_MS = 450
private const val SHEEN_BAND_FRACTION = 0.34f

/**
 * Nest Music Apple Music hairline capsule slider:
 * - Rests at 7dp (or [restTrackHeight]), springs to 12dp (or [draggingTrackHeight]) on grab
 * - Relative horizontal drag (scrubs smoothly from wherever the bar is without jumping on touch-down)
 * - Optional ping-pong [MixSheen] indeterminate wave while buffering/switching tracks.
 */
@Composable
internal fun NestThinSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    onValueChangeFinished: (() -> Unit)? = null,
    activeColor: Color = Color.White.copy(alpha = 0.81f),
    inactiveColor: Color = Color.White.copy(alpha = 0.24f),
    restTrackHeight: Dp = REST_TRACK_HEIGHT,
    draggingTrackHeight: Dp = DRAGGING_TRACK_HEIGHT,
    touchHeight: Dp = TOUCH_HEIGHT,
    relativeDrag: Boolean = true,
    onDraggingChange: ((Boolean) -> Unit)? = null,
) {
    var dragging by remember { mutableStateOf(false) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val latestValue by rememberUpdatedState(value.coerceIn(0f, 1f))
    val latestOnChange by rememberUpdatedState(onValueChange)
    val latestOnFinished by rememberUpdatedState(onValueChangeFinished)
    val latestOnDragging by rememberUpdatedState(onDraggingChange)
    val haptics = LocalHapticFeedback.current

    val barHeightDp by animateDpAsState(
        targetValue = if (dragging) draggingTrackHeight else restTrackHeight,
        animationSpec = spring(
            dampingRatio = 0.7f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "nestThinSliderHeight",
    )

    val sheenVisible = isLoading && !dragging
    val fillFactor by animateFloatAsState(
        targetValue = if (sheenVisible) 0f else 1f,
        animationSpec = tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing),
        label = "nestThinSliderFillFactor",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(touchHeight)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(enabled, relativeDrag) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    dragging = true
                    latestOnDragging?.invoke(true)
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                    var scrub = if (relativeDrag) {
                        latestValue
                    } else {
                        (down.position.x / widthPx).coerceIn(0f, 1f)
                    }
                    if (!relativeDrag) {
                        latestOnChange(scrub)
                    }

                    val finished = horizontalDrag(down.id) { change ->
                        val dx = change.positionChange().x
                        change.consume()
                        val prev = scrub
                        scrub = if (relativeDrag) {
                            (scrub + dx / widthPx).coerceIn(0f, 1f)
                        } else {
                            (change.position.x / widthPx).coerceIn(0f, 1f)
                        }
                        if ((prev > 0f && scrub == 0f) || (prev < 1f && scrub == 1f)) {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        latestOnChange(scrub)
                    }
                    dragging = false
                    latestOnDragging?.invoke(false)
                    if (finished) {
                        latestOnFinished?.invoke()
                    } else {
                        latestOnFinished?.invoke()
                    }
                }
            },
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(touchHeight),
        ) {
            val barHeight = barHeightDp.toPx()
            val barTop = (size.height - barHeight) / 2f
            val radius = CornerRadius(barHeight / 2f, barHeight / 2f)

            drawRoundRect(
                color = inactiveColor,
                topLeft = Offset(0f, barTop),
                size = Size(size.width, barHeight),
                cornerRadius = radius,
            )

            val fraction = value.coerceIn(0f, 1f)
            val filled = size.width * fraction * fillFactor
            if (filled > 0f) {
                drawRoundRect(
                    color = activeColor,
                    topLeft = Offset(0f, barTop),
                    size = Size(
                        filled.coerceAtLeast(barHeight * fillFactor).coerceAtMost(size.width),
                        barHeight,
                    ),
                    cornerRadius = radius,
                )
            }
        }

        AnimatedVisibility(
            visible = sheenVisible,
            enter = fadeIn(tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing)) +
                scaleIn(
                    animationSpec = tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing),
                    initialScale = 0f,
                    transformOrigin = TransformOrigin(0f, 0.5f),
                ),
            exit = fadeOut(tween(durationMillis = MORPH_MS, easing = FastOutSlowInEasing)),
        ) {
            MixSheen(height = barHeightDp, color = activeColor)
        }
    }
}

@Composable
private fun MixSheen(height: Dp, color: Color) {
    val transition = rememberInfiniteTransition(label = "nestMixSheen")
    val phase by transition.animateFloat(
        initialValue = -SHEEN_BAND_FRACTION,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "nestMixSheenPhase",
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val band = size.width * SHEEN_BAND_FRACTION
        val x = phase * size.width
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to color,
                    1f to Color.Transparent,
                ),
                startX = x,
                endX = x + band,
            ),
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(size.height / 2f),
        )
    }
}
