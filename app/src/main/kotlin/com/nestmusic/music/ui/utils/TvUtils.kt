/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.utils

import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import androidx.compose.foundation.Indication
import androidx.compose.foundation.IndicationInstance
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * True when the app runs on an Android TV / Google TV device.
 * Provided at the root of the composition by MainActivity.
 */
val LocalIsTv = staticCompositionLocalOf { false }

fun Context.isTvDevice(): Boolean {
    val uiModeManager = getSystemService(Context.UI_MODE_SERVICE) as? UiModeManager
    if (uiModeManager?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION) return true
    return packageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
}

/** Horizontal overscan-safe margin (per side) applied to TV layouts. */
val TvOverscanHorizontal = 24.dp

/** Vertical overscan-safe margin (per side) applied to TV layouts. */
val TvOverscanVertical = 16.dp

/**
 * Applies overscan-safe padding on TV devices, returns the receiver unchanged otherwise.
 */
@Composable
fun Modifier.tvOverscanPadding(
    horizontal: Dp = TvOverscanHorizontal,
    vertical: Dp = TvOverscanVertical,
): Modifier =
    if (LocalIsTv.current) {
        padding(horizontal = horizontal, vertical = vertical)
    } else {
        this
    }

/**
 * Draws a focus ring around this component when it is focused on a TV device.
 * Intended for Material3 components (which ignore [LocalIndication]) such as IconButton.
 * No-op on non-TV devices.
 */
@Composable
fun Modifier.tvFocusRing(shape: Shape = CircleShape): Modifier {
    if (!LocalIsTv.current) return this
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    return this
        .onFocusChanged { focused = it.isFocused }
        .then(if (focused) Modifier.border(2.dp, color, shape) else Modifier)
}

/**
 * Indication for TV devices: draws a highlight wash plus a focus ring around
 * foundation clickables (list rows, cards, menu entries, ...) when they gain
 * D-pad focus, and a wash while pressed. Provided via LocalIndication on TV.
 */
class TvFocusIndication(
    private val highlight: Color,
    private val cornerRadius: Dp = 12.dp,
) : Indication {
    @Composable
    override fun rememberUpdatedInstance(interactionSource: InteractionSource): IndicationInstance {
        val focused = interactionSource.collectIsFocusedAsState()
        val pressed = interactionSource.collectIsPressedAsState()
        return remember(interactionSource, highlight, cornerRadius) {
            TvFocusIndicationInstance(focused, pressed, highlight, cornerRadius)
        }
    }
}

private class TvFocusIndicationInstance(
    private val focused: State<Boolean>,
    private val pressed: State<Boolean>,
    private val highlight: Color,
    private val cornerRadius: Dp,
) : IndicationInstance {
    override fun ContentDrawScope.drawIndication() {
        drawContent()
        val isFocused = focused.value
        val isPressed = pressed.value
        if (!isFocused && !isPressed) return
        val radius = CornerRadius(cornerRadius.toPx())
        if (isPressed) {
            drawRoundRect(
                color = highlight,
                alpha = 0.24f,
                cornerRadius = radius,
            )
        } else {
            drawRoundRect(
                color = highlight,
                alpha = 0.16f,
                cornerRadius = radius,
            )
            val strokeWidth = 2.dp.toPx()
            val halfStroke = strokeWidth / 2f
            drawRoundRect(
                color = highlight,
                topLeft = Offset(halfStroke, halfStroke),
                size = Size(size.width - strokeWidth, size.height - strokeWidth),
                cornerRadius = radius,
                style = Stroke(width = strokeWidth),
            )
        }
    }
}
