/*
 * Nest Music Project (C) 2026
 * Licensed under GPL-3.0 | Adapted from Nest Music (Nest Music).
 */

package com.nestmusic.music.ui.theme

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

fun Modifier.outerDarkRim(
    shape: Shape,
    width: Dp = 1.6.dp,
    alpha: Float = 0.32f,
    angle: Float = 45f,
    insideOnly: Boolean = false,
): Modifier = drawWithCache {
    val inset = if (insideOnly) width.toPx() / 4f else 0f
    val outline = shape.createOutline(
        Size(size.width - inset * 2f, size.height - inset * 2f),
        layoutDirection,
        this,
    )
    val stroke = Stroke(width = if (insideOnly) width.toPx() / 2f else width.toPx())
    val center = Offset(size.width / 2f, size.height / 2f)
    fun flankBrush(axisDeg: Float): Brush {
        val a = axisDeg * (PI / 180f).toFloat()
        val dir = Offset(cos(a), sin(a))
        val reach = dir * (abs(dir.x) * size.width + abs(dir.y) * size.height) / 2f
        return Brush.linearGradient(
            0f to Color.Black.copy(alpha = alpha),
            0.5f to Color.Transparent,
            1f to Color.Black.copy(alpha = alpha),
            start = center - reach,
            end = center + reach,
        )
    }
    val firstBrush = flankBrush(angle)
    val secondBrush = flankBrush(angle + 90f)
    onDrawWithContent {
        drawContent()
        translate(inset, inset) {
            drawOutline(outline = outline, brush = firstBrush, style = stroke)
            drawOutline(outline = outline, brush = secondBrush, style = stroke)
        }
    }
}
