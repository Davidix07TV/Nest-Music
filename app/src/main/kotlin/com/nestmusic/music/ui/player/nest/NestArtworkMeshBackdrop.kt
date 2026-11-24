package com.nestmusic.music.ui.player.nest

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.VertexMode
import androidx.compose.ui.graphics.Vertices
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Apple Music / iOS 26 multi-stop smoothstep `Vertices(VertexMode.Triangles)` artwork mesh backdrop
 * with gentle organic drift, paired with a full-bleed blurred artwork layer on Android 12+ so the
 * Now Playing & Lyrics surfaces glow organically with the album cover (and live motion frames).
 */
@Composable
fun NestArtworkMeshBackdrop(
    artworkModel: Any?,
    liveFrameBitmap: Bitmap?,
    fallbackPalette: List<Color>,
    isPlaying: Boolean,
    dimScrimAlpha: Float = 0.28f,
    modifier: Modifier = Modifier,
) {
    var sampledGrid by remember { mutableStateOf<List<Color>?>(null) }

    LaunchedEffect(liveFrameBitmap) {
        val bmp = liveFrameBitmap ?: return@LaunchedEffect
        val extracted = withContext(Dispatchers.Default) { extract4x4ColorStops(bmp) }
        if (extracted.isNotEmpty()) {
            sampledGrid = extracted
        }
    }

    val baseStops = remember(sampledGrid, fallbackPalette) {
        val src = sampledGrid ?: expandPaletteTo16(fallbackPalette)
        src.map { deepenForBackdrop(it) }
    }

    val animatedStops = List(16) { idx ->
        val target = baseStops.getOrElse(idx) { Color(0xFF0A0A0E) }
        val c by animateColorAsState(
            targetValue = target,
            animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            label = "meshStop$idx",
        )
        c
    }

    val infinite = rememberInfiniteTransition(label = "nestMeshDrift")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "meshPhase",
    )

    val indices = remember {
        val cols = 12
        val rows = 12
        val out = ArrayList<Int>(cols * rows * 6)
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val i0 = r * (cols + 1) + c
                val i1 = i0 + 1
                val i2 = i0 + (cols + 1)
                val i3 = i2 + 1
                out.add(i0)
                out.add(i1)
                out.add(i2)
                out.add(i1)
                out.add(i3)
                out.add(i2)
            }
        }
        out.toList()
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(Color.Black),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val cols = 12
            val rows = 12
            val count = (cols + 1) * (rows + 1)
            val positions = ArrayList<Offset>(count)
            val colors = ArrayList<Color>(count)
            val driftAmp = if (isPlaying) 0.065f else 0.018f

            for (r in 0..rows) {
                val v = r.toFloat() / rows.toFloat()
                for (c in 0..cols) {
                    val u = c.toFloat() / cols.toFloat()
                    val du =
                        if (c in 1 until cols) {
                            sin(phase + v * 3.1f + u * 2.3f) * driftAmp
                        } else {
                            0f
                        }
                    val dv =
                        if (r in 1 until rows) {
                            cos(phase * 0.83f + u * 2.9f - v * 2.1f) * driftAmp
                        } else {
                            0f
                        }
                    positions.add(Offset((u + du).coerceIn(0f, 1f) * w, (v + dv).coerceIn(0f, 1f) * h))
                    colors.add(sampleSmooth4x4(animatedStops, u, v))
                }
            }

            val meshPaint = Paint()
            drawIntoCanvas { canvas ->
                canvas.drawVertices(
                    vertices =
                        Vertices(
                            vertexMode = VertexMode.Triangles,
                            positions = positions,
                            textureCoordinates = positions,
                            colors = colors,
                            indices = indices,
                        ),
                    blendMode = BlendMode.SrcOver,
                    paint = meshPaint,
                )
            }
        }

        // Subtle optical artwork bloom on Android 12+ for rich spatial depth
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && artworkModel != null) {
            AsyncImage(
                model = artworkModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.38f
                            scaleY = 1.38f
                            alpha = 0.34f
                        }.blur(84.dp),
            )
        }

        // Dark tonal legibility veil so white SF Pro typography and Liquid Glass controls pop crisply
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = dimScrimAlpha * 0.55f),
                            0.48f to Color.Black.copy(alpha = dimScrimAlpha * 0.85f),
                            0.78f to Color.Black.copy(alpha = (dimScrimAlpha + 0.18f).coerceAtMost(0.72f)),
                            1.0f to Color.Black.copy(alpha = (dimScrimAlpha + 0.30f).coerceAtMost(0.82f)),
                        ),
                    ),
        )
    }
}

private fun sampleSmooth4x4(
    stops16: List<Color>,
    u: Float,
    v: Float,
): Color {
    val gx = (u.coerceIn(0f, 1f) * 3f)
    val gy = (v.coerceIn(0f, 1f) * 3f)
    val ix = gx.toInt().coerceIn(0, 2)
    val iy = gy.toInt().coerceIn(0, 2)
    val fx = smoothstep(gx - ix)
    val fy = smoothstep(gy - iy)

    val c00 = stops16[iy * 4 + ix]
    val c10 = stops16[iy * 4 + ix + 1]
    val c01 = stops16[(iy + 1) * 4 + ix]
    val c11 = stops16[(iy + 1) * 4 + ix + 1]

    val top = lerpColor(c00, c10, fx)
    val bot = lerpColor(c01, c11, fx)
    return lerpColor(top, bot, fy)
}

private fun smoothstep(t: Float): Float {
    val x = t.coerceIn(0f, 1f)
    return x * x * (3f - 2f * x)
}

private fun lerpColor(
    a: Color,
    b: Color,
    t: Float,
): Color =
    Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f,
    )

private fun expandPaletteTo16(palette: List<Color>): List<Color> {
    if (palette.isEmpty()) {
        return List(16) { Color(0xFF121216) }
    }
    return List(16) { idx ->
        val base = palette[idx % palette.size]
        val shift = ((idx * 7) % 5 - 2) * 0.04f
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(base.toArgb(), hsl)
        hsl[1] = (hsl[1] * 1.12f).coerceIn(0.12f, 0.88f)
        hsl[2] = (hsl[2] + shift).coerceIn(0.10f, 0.46f)
        Color(ColorUtils.HSLToColor(hsl))
    }
}

private fun deepenForBackdrop(c: Color): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(c.toArgb(), hsl)
    hsl[1] = (hsl[1] * 1.18f).coerceIn(0.08f, 0.88f)
    hsl[2] = (hsl[2] * 0.68f).coerceIn(0.07f, 0.42f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun extract4x4ColorStops(bitmap: Bitmap): List<Color> =
    runCatching {
        val w = bitmap.width
        val h = bitmap.height
        if (w < 4 || h < 4) return@runCatching emptyList()
        val stops = ArrayList<Color>(16)
        for (gy in 0 until 4) {
            for (gx in 0 until 4) {
                val x0 = (gx * w) / 4
                val x1 = ((gx + 1) * w) / 4
                val y0 = (gy * h) / 4
                val y1 = ((gy + 1) * h) / 4
                var rSum = 0L
                var gSum = 0L
                var bSum = 0L
                var count = 0L
                val stepX = ((x1 - x0) / 4).coerceAtLeast(1)
                val stepY = ((y1 - y0) / 4).coerceAtLeast(1)
                var y = y0
                while (y < y1) {
                    var x = x0
                    while (x < x1) {
                        val px = bitmap.getPixel(x, y)
                        rSum += (px shr 16) and 0xFF
                        gSum += (px shr 8) and 0xFF
                        bSum += px and 0xFF
                        count++
                        x += stepX
                    }
                    y += stepY
                }
                if (count > 0) {
                    stops.add(
                        Color(
                            red = (rSum / count).toInt() / 255f,
                            green = (gSum / count).toInt() / 255f,
                            blue = (bSum / count).toInt() / 255f,
                            alpha = 1f,
                        ),
                    )
                } else {
                    stops.add(Color(0xFF141418))
                }
            }
        }
        stops
    }.getOrDefault(emptyList())
