/*
 * Adapted from Nest Music (Nest Music) for Nest Music under GPL-3.0.
 */

package com.nestmusic.music.ui.component

import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

private val FADE_HEIGHT = 180.dp
private val FADE_HEIGHT_WITH_MINI_PLAYER = 254.dp
private const val STOPS = 16

/**
 * Soft cubic bottom floor wash under the floating Liquid Glass capsules,
 * preventing hard seam lines while keeping scroll performance free of extra full-screen blurs.
 */
@Composable
fun BottomFadeScrim(
    modifier: Modifier = Modifier,
    withMiniPlayer: Boolean = false,
    bottomInset: Dp = 0.dp,
    pageColor: Color = MaterialTheme.colorScheme.background,
) {
    val height by animateDpAsState(
        targetValue = bottomInset + if (withMiniPlayer) FADE_HEIGHT_WITH_MINI_PLAYER else FADE_HEIGHT,
        animationSpec = tween(220),
        label = "bottomScrimHeight",
    )

    val brush = remember(pageColor) {
        Brush.verticalGradient(
            colorStops = Array(STOPS) { i ->
                val t = i / (STOPS - 1f)
                t to pageColor.copy(alpha = EaseInCubic.transform(t))
            },
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .background(brush),
    )
}
