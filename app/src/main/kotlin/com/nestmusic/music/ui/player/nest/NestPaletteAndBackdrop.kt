/*
 * Adapted from Nest Music (Nest Music) MeshGradient.kt, ArtworkMeshBackdrop.kt,
 * NowPlayingBackdrop.kt, PlayerInk.kt, and PlayerInkContrast.kt under GPL-3.0.
 */

package com.nestmusic.music.ui.player.nest

import android.graphics.Bitmap
import android.util.LruCache
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.scale
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

@Immutable
internal data class NowPlayingAlphas(
    val primary: Float = 0.92f,
    val secondary: Float = 0.72f,
    val tertiary: Float = 0.65f,
    val track: Float = 0.24f,
    val buttonFill: Float = 0.22f,
)

internal val LocalPlayerInk = staticCompositionLocalOf<State<Color>> {
    mutableStateOf(Color.White)
}

internal val LocalPlayerInkGlow = staticCompositionLocalOf<State<Color>> {
    mutableStateOf(Color.Black.copy(alpha = 0.35f))
}

internal val LocalPlayerAlphas = staticCompositionLocalOf { NowPlayingAlphas() }

private val FallbackMeshColors = listOf(
    Color(0xFF28203E),
    Color(0xFF1E324A),
    Color(0xFF3E2238),
    Color(0xFF192434),
)

@Immutable
data class MeshPalette(
    val colors: List<Color> = FallbackMeshColors,
    val ink: Color = Color.White,
    val inkGlow: Color = Color.Black.copy(alpha = 0.35f),
)

private val meshPaletteCache = LruCache<String, MeshPalette>(48)
private val fullBlurCache = LruCache<String, ImageBitmap>(24)

@Composable
internal fun rememberNestPalette(
    imageUrl: String?,
    darkTheme: Boolean = true,
): MeshPalette {
    val context = LocalContext.current
    val cacheKey = imageUrl?.let { "$it#$darkTheme" }
    var palette by remember {
        mutableStateOf(cacheKey?.let(meshPaletteCache::get) ?: MeshPalette())
    }

    LaunchedEffect(imageUrl, darkTheme) {
        if (imageUrl.isNullOrBlank() || cacheKey == null) {
            palette = MeshPalette()
            return@LaunchedEffect
        }
        meshPaletteCache.get(cacheKey)?.let {
            palette = it
            return@LaunchedEffect
        }
        val computed = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(160)
                    .allowHardware(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                val result = context.imageLoader.execute(request)
                val raw = (result as? SuccessResult)?.image?.toBitmap() ?: return@withContext null
                val sw = if (raw.config == Bitmap.Config.HARDWARE) {
                    raw.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    raw
                } ?: return@withContext null
                extractNestPalette(sw, darkTheme)
            } catch (_: Throwable) {
                null
            }
        }
        if (computed != null) {
            meshPaletteCache.put(cacheKey, computed)
            palette = computed
        }
    }
    return palette
}

private fun extractNestPalette(bitmap: Bitmap, darkTheme: Boolean): MeshPalette {
    val p = Palette.from(bitmap).maximumColorCount(24).generate()
    var swatches = p.swatches.sortedByDescending { it.population }.map { Color(it.rgb) }
    if (swatches.isEmpty()) {
        val unfiltered = Palette.from(bitmap).maximumColorCount(24).clearFilters().generate()
        swatches = unfiltered.swatches.sortedByDescending { it.population }.map { Color(it.rgb) }
    }
    val distinct = swatches.distinctEnough()
    val four = when {
        distinct.isEmpty() -> FallbackMeshColors
        distinct.size >= 4 -> distinct.take(4)
        else -> distinct.expandedToFour()
    }

    // Derive an album-tinted light ink (Apple Music style: warm/cool white lightly tinted by the sleeve's hue)
    val dominant = four.first()
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(dominant.toArgb(), hsl)
    val ink = if (hsl[1] < 0.08f) {
        Color.White
    } else {
        Color(
            ColorUtils.HSLToColor(
                floatArrayOf(
                    hsl[0],
                    (hsl[1] * 0.38f).coerceAtMost(0.32f),
                    0.94f,
                ),
            ),
        )
    }
    val glow = Color.Black.copy(alpha = if (darkTheme) 0.34f else 0.28f)
    return MeshPalette(colors = four, ink = ink, inkGlow = glow)
}

private fun List<Color>.distinctEnough(): List<Color> {
    val kept = mutableListOf<Color>()
    forEach { color -> if (kept.none { it.isCloseTo(color) }) kept += color }
    return kept
}

private fun Color.isCloseTo(other: Color): Boolean {
    val a = hsl()
    val b = other.hsl()
    val hueGap = abs(a[0] - b[0]).let { min(it, 360f - it) }
    return hueGap < 15f && abs(a[2] - b[2]) < 0.12f
}

private fun List<Color>.expandedToFour(): List<Color> {
    val out = toMutableList()
    var step = 1
    while (out.size < 4) {
        out += this[(out.size - size) % size].shifted(24f * step, 0.12f * step)
        step++
    }
    return out
}

private fun Color.shifted(hue: Float, lightness: Float): Color {
    val hsl = hsl()
    hsl[0] = (hsl[0] + hue) % 360f
    hsl[2] = (hsl[2] + lightness).coerceIn(0.2f, 0.7f)
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.hsl(): FloatArray =
    FloatArray(3).also { ColorUtils.colorToHSL(toArgb(), it) }

private fun Color.tuned(darkTheme: Boolean): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[1] = (hsl[1] * 1.35f).coerceAtMost(1f)
    hsl[2] = if (darkTheme) {
        hsl[2].coerceIn(0.20f, 0.46f)
    } else {
        hsl[2].coerceIn(0.28f, 0.56f)
    }
    return Color(ColorUtils.HSLToColor(hsl))
}

private fun Color.dimmed(): Color {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(toArgb(), hsl)
    hsl[2] = 0.11f
    return Color(ColorUtils.HSLToColor(hsl))
}

private const val DRIFT_RADIANS = (PI * 0.45f).toFloat()

/**
 * Nest Music 4-blob radial mesh gradient backdrop:
 * - Smooth 1400ms color crossfade between tracks
 * - 8000ms orbital settle (`DRIFT_RADIANS`) triggered on track skip
 * - Legibility scrim ensuring WCAG AA contrast for over-artwork controls.
 */
@Composable
internal fun NestMeshGradientBackground(
    palette: MeshPalette,
    trackKey: Any?,
    darkTheme: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val targets = remember(palette, darkTheme) {
        (palette.colors.ifEmpty { FallbackMeshColors } + FallbackMeshColors)
            .take(4)
            .map { it.tuned(darkTheme) }
    }
    val baseTarget = remember(targets) { targets.first().dimmed() }

    val spec = tween<Color>(durationMillis = 1400, easing = FastOutSlowInEasing)
    val c0 by animateColorAsState(targets[0], spec, label = "mesh0")
    val c1 by animateColorAsState(targets[1], spec, label = "mesh1")
    val c2 by animateColorAsState(targets[2], spec, label = "mesh2")
    val c3 by animateColorAsState(targets[3], spec, label = "mesh3")
    val base by animateColorAsState(baseTarget, spec, label = "meshBase")

    val orbit = remember { Animatable(0f) }
    var targetAngle by remember { mutableStateOf(0f) }
    LaunchedEffect(trackKey) {
        targetAngle += DRIFT_RADIANS
        orbit.animateTo(
            targetValue = targetAngle,
            animationSpec = tween(durationMillis = 8000, easing = LinearOutSlowInEasing),
        )
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = base)
        val w = size.width
        val h = size.height
        val radius = maxOf(w, h) * 0.88f
        val phase = orbit.value

        val blobs = arrayOf(
            Triple(c0, 0.24f + 0.16f * cos(phase), 0.22f + 0.12f * sin(phase)),
            Triple(c1, 0.78f + 0.15f * cos(phase + 1.7f), 0.28f + 0.14f * sin(phase + 1.7f)),
            Triple(c2, 0.26f + 0.16f * cos(phase + 3.3f), 0.74f + 0.14f * sin(phase + 3.3f)),
            Triple(c3, 0.76f + 0.15f * cos(phase + 4.9f), 0.78f + 0.13f * sin(phase + 4.9f)),
        )

        for ((color, nx, ny) in blobs) {
            val center = Offset(nx * w, ny * h)
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.85f), Color.Transparent),
                    center = center,
                    radius = radius,
                ),
            )
        }

        // Nest Music vertical legibility scrim (lighter under artwork, deeper behind controls)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.12f),
                0.55f to Color.Black.copy(alpha = 0.24f),
                1f to Color.Black.copy(alpha = 0.42f),
            ),
        )
    }
}

/**
 * Pre-blurred 128px full-cover bitmap for Lyrics & Queue surfaces (Nest Music `ArtworkMeshBackdrop.kt`):
 * computed once per song on `Dispatchers.Default` and cached by URL so scrolling 60fps word-synced
 * lyrics never pays a live full-screen RenderEffect blur per frame.
 */
@Composable
internal fun rememberFullArtworkBlurImage(
    imageUrl: String?,
    darkTheme: Boolean = true,
): ImageBitmap? {
    val context = LocalContext.current
    val cacheKey = imageUrl?.let { if (darkTheme) "$it#dark" else it }
    var image by remember { mutableStateOf(cacheKey?.let(fullBlurCache::get)) }

    LaunchedEffect(imageUrl, darkTheme) {
        if (imageUrl.isNullOrBlank() || cacheKey == null) {
            image = null
            return@LaunchedEffect
        }
        fullBlurCache.get(cacheKey)?.let {
            image = it
            return@LaunchedEffect
        }
        val blurred = withContext(Dispatchers.IO) {
            try {
                val req = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .size(128)
                    .allowHardware(false)
                    .memoryCachePolicy(CachePolicy.ENABLED)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .build()
                val res = context.imageLoader.execute(req)
                val raw = (res as? SuccessResult)?.image?.toBitmap() ?: return@withContext null
                val sw = if (raw.config == Bitmap.Config.HARDWARE) {
                    raw.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    raw
                } ?: return@withContext null
                val scaled = sw.scale(96, 96, true)
                withContext(Dispatchers.Default) {
                    boxBlurBitmap(scaled, radius = 14, passes = 3, darkTone = darkTheme).asImageBitmap()
                }
            } catch (_: Throwable) {
                null
            }
        }
        if (blurred != null) {
            fullBlurCache.put(cacheKey, blurred)
            image = blurred
        }
    }
    return image
}

private fun boxBlurBitmap(src: Bitmap, radius: Int, passes: Int, darkTone: Boolean): Bitmap {
    val w = src.width
    val h = src.height
    val pixels = IntArray(w * h)
    val temp = IntArray(w * h)
    src.getPixels(pixels, 0, w, 0, 0, w, h)
    val div = 2 * radius + 1

    repeat(passes) {
        // Horizontal pass
        for (y in 0 until h) {
            var rSum = 0; var gSum = 0; var bSum = 0
            val rowOffset = y * w
            for (i in -radius..radius) {
                val c = pixels[rowOffset + i.coerceIn(0, w - 1)]
                rSum += (c shr 16) and 0xFF
                gSum += (c shr 8) and 0xFF
                bSum += c and 0xFF
            }
            for (x in 0 until w) {
                temp[rowOffset + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val outC = pixels[rowOffset + (x - radius).coerceIn(0, w - 1)]
                val inC = pixels[rowOffset + (x + radius + 1).coerceIn(0, w - 1)]
                rSum += ((inC shr 16) and 0xFF) - ((outC shr 16) and 0xFF)
                gSum += ((inC shr 8) and 0xFF) - ((outC shr 8) and 0xFF)
                bSum += (inC and 0xFF) - (outC and 0xFF)
            }
        }
        // Vertical pass
        for (x in 0 until w) {
            var rSum = 0; var gSum = 0; var bSum = 0
            for (i in -radius..radius) {
                val c = temp[i.coerceIn(0, h - 1) * w + x]
                rSum += (c shr 16) and 0xFF
                gSum += (c shr 8) and 0xFF
                bSum += c and 0xFF
            }
            for (y in 0 until h) {
                pixels[y * w + x] = (0xFF shl 24) or ((rSum / div) shl 16) or ((gSum / div) shl 8) or (bSum / div)
                val outC = temp[(y - radius).coerceIn(0, h - 1) * w + x]
                val inC = temp[(y + radius + 1).coerceIn(0, h - 1) * w + x]
                rSum += ((inC shr 16) and 0xFF) - ((outC shr 16) and 0xFF)
                gSum += ((inC shr 8) and 0xFF) - ((outC shr 8) and 0xFF)
                bSum += (inC and 0xFF) - (outC and 0xFF)
            }
        }
    }

    if (darkTone) {
        val hsl = FloatArray(3)
        for (i in pixels.indices) {
            ColorUtils.colorToHSL(pixels[i], hsl)
            hsl[1] = (hsl[1] * 1.18f).coerceAtMost(0.90f)
            hsl[2] = (hsl[2] * 0.56f).coerceIn(0.08f, 0.36f)
            pixels[i] = ColorUtils.HSLToColor(hsl)
        }
    }

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    out.setPixels(pixels, 0, w, 0, 0, w, h)
    return out
}

/**
 * Nest Music `PortraitBackdrop`:
 * - Main Now Playing view stands on the animated 4-blob `NestMeshGradientBackground`
 * - Opening Lyrics or Queue smoothly crossfades (360ms) into the full-artwork blurred
 *   cover backdrop (`FullArtworkBlurBackdrop`), matching Nest Music's `NowPlayingBackdrop.kt`.
 */
@Composable
internal fun NestPortraitBackdrop(
    imageUrl: String?,
    palette: MeshPalette,
    trackKey: Any?,
    panelOpen: Boolean,
    darkTheme: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val fullBlur = rememberFullArtworkBlurImage(imageUrl = imageUrl, darkTheme = darkTheme)
    val panelCover by animateFloatAsState(
        targetValue = if (panelOpen && fullBlur != null) 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "nestPanelBackdropCover",
    )

    // Crossfade between previous & current blurred cover on skips
    var previousBlur by remember { mutableStateOf<ImageBitmap?>(null) }
    var activeBlur by remember { mutableStateOf(fullBlur) }
    val blurTransition = remember { Animatable(1f) }
    LaunchedEffect(fullBlur) {
        if (fullBlur != null && fullBlur !== activeBlur) {
            previousBlur = activeBlur
            activeBlur = fullBlur
            blurTransition.snapTo(0f)
            blurTransition.animateTo(1f, tween(durationMillis = 480, easing = FastOutSlowInEasing))
            previousBlur = null
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color(0xFF0E0E12))) {
        if (panelCover < 1f) {
            NestMeshGradientBackground(
                palette = palette,
                trackKey = trackKey,
                darkTheme = darkTheme,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (panelCover > 0f && activeBlur != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = panelCover },
            ) {
                previousBlur?.let { prev ->
                    Image(
                        painter = remember(prev) { BitmapPainter(prev, filterQuality = FilterQuality.Medium) },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                activeBlur?.let { curr ->
                    Image(
                        painter = remember(curr) { BitmapPainter(curr, filterQuality = FilterQuality.Medium) },
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = blurTransition.value },
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.24f),
                                0.5f to Color.Black.copy(alpha = 0.30f),
                                1f to Color.Black.copy(alpha = 0.48f),
                            ),
                        ),
                )
            }
        }
    }
}
