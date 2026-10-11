/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Liquid glass surfaces, ported from SimpMusic's LiquidGlassContainer / GlassLuminance.
 *
 * SimpMusic draws its glass with Kyant's `backdrop` library; this is the same effect assembled out
 * of what Compose and the platform already ship, so the fork stays dependency-free:
 *
 *  - the backdrop is a [GraphicsLayer] the screen records itself into ([glassBackdrop]),
 *  - a glass surface samples that layer through a `saveLayer` carrying a RenderEffect blur and a
 *    saturation colour matrix ([liquidGlass]),
 *  - the scrim adapts to what sits behind it ([rememberGlassLuminance]),
 *  - the press response is an observe-only gesture, so wrapped buttons keep their own clicks.
 *
 * Everything here needs Android 12 (API 31) for the blur; below that the surfaces fall back to the
 * flat translucent pill the app used before, because a fake glass reads worse than no glass.
 */

package com.nestmusic.music.ui.component

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastFirstOrNull
import androidx.compose.ui.util.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sign

/**
 * Whether liquid glass surfaces should draw at all. Provided once at the app root from the user's
 * setting, so the call sites never have to read the preference themselves: with it off every glass
 * surface falls back to the flat translucent pill the app used before.
 */
val LocalLiquidGlassEnabled = compositionLocalOf { true }

/**
 * The backdrop the surfaces of one region sample, handed down the tree so the mini player, the
 * navigation bar and the player itself can draw glass without every screen threading it through
 * its own parameters. Null means "no glass here": the surfaces then fall back to the flat pill.
 */
val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/** True while the platform can actually blur, i.e. RenderEffect exists (API 31+). */
val isLiquidGlassSupported: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * The backdrop glass surfaces sample from.
 *
 * One instance belongs to one region of the screen (in practice: the content behind the player and
 * the navigation bar). A glass surface MUST be a sibling of the region it samples — nesting it
 * inside the source would make the layer record itself.
 */
class GlassBackdrop internal constructor(
    internal val layer: GraphicsLayer,
) {
    internal var positionInRoot by mutableStateOf(Offset.Zero)

    /**
     * 0f dark .. 1f bright. The glass reads it in the draw phase (so changing it does not
     * recompose) and [rememberGlassLuminance] keeps it following the artwork behind the surface.
     */
    val luminance = Animatable(0.5f)
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    val layer = rememberGraphicsLayer()
    return remember(layer) { GlassBackdrop(layer) }
}

/**
 * Marks this composable as the source that sibling glass surfaces refract.
 *
 * The content is drawn normally and, in the same pass, recorded into [backdrop]'s layer — recording
 * costs nothing extra to display, it only keeps a GPU-side copy for the glass to sample.
 */
fun Modifier.glassBackdrop(backdrop: GlassBackdrop?): Modifier {
    if (backdrop == null) return this
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    return this
        .onGloballyPositioned { coordinates -> backdrop.positionInRoot = coordinates.positionInRoot() }
        .drawWithContent {
            drawContent()
            backdrop.layer.record(
                density = density,
                layoutDirection = layoutDirection,
                size = IntSize(size.width.roundToInt(), size.height.roundToInt()),
            ) {
                this@drawWithContent.drawContent()
            }
        }
}

/**
 * Press/hold state for a single glass surface: a spring-driven progress plus the pointer's own
 * position, which the glass turns into a radial glow that follows the finger.
 *
 * The gesture is observe-only, so a button wrapped in a glass surface still receives its clicks.
 */
class GlassInteraction(
    private val animationScope: CoroutineScope,
) {
    private val pressSpec = spring(dampingRatio = 0.5f, stiffness = 300f, visibilityThreshold = 0.001f)
    private val pressAnimation = Animatable(0f, 0.001f)

    /** 0f at rest, animating to 1f while pressed. Read in draw/effect/layer blocks. */
    val pressProgress: Float get() = pressAnimation.value

    /** Local-space touch point used as the centre of the press glow. */
    var touchPosition by mutableStateOf(Offset.Zero)
        private set

    suspend fun detectPress(pointer: PointerInputScope) =
        with(pointer) {
            inspectDragGestures(
                onDragStart = { down ->
                    touchPosition = down.position
                    animationScope.launch { pressAnimation.animateTo(1f, pressSpec) }
                },
                onDragEnd = { animationScope.launch { pressAnimation.animateTo(0f, pressSpec) } },
                onDragCancel = { animationScope.launch { pressAnimation.animateTo(0f, pressSpec) } },
            ) { change, _ ->
                touchPosition = change.position
            }
        }
}

@Composable
fun rememberGlassInteraction(): GlassInteraction {
    val scope = rememberCoroutineScope()
    return remember(scope) { GlassInteraction(scope) }
}

/**
 * The liquid-glass modifier: samples [backdrop] through a blur + saturation stack, clips it to
 * [shape], lays an adaptive scrim and a rim light over it, and answers a press with a slight
 * scale-up and a radial glow.
 *
 * The glass is drawn *behind* whatever the node itself renders, so a caller can put an icon, a
 * label or a whole row of controls on top of it.
 *
 * @param interactive set false for a static glass surface.
 * @param tint colour of the fallback pill when the effect is off or unavailable.
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: GlassBackdrop?,
    shape: Shape = CircleShape,
    enabled: Boolean = LocalLiquidGlassEnabled.current,
    interactive: Boolean = true,
    blurRadius: Dp = 18.dp,
    saturation: Float = 1.6f,
    minScrim: Float = 0.12f,
    maxScrim: Float = 0.5f,
    pressedScale: Float = 1.06f,
    tint: Color? = null,
): Modifier {
    if (backdrop == null || !enabled || !isLiquidGlassSupported) {
        // Without the platform blur the glass would be a flat pane with a rim, which reads worse
        // than the plain pill the app already had — so the fallback is that pill, not a fake glass.
        return this
            .clip(shape)
            .background(tint ?: MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.8f))
    }

    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val interaction = if (interactive) rememberGlassInteraction() else null
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val blurPx = with(density) { blurRadius.toPx() }
    val rimWidth = with(density) { 1.dp.toPx() }
    var surfacePosition by remember { mutableStateOf(Offset.Zero) }

    return this
        .clip(shape)
        .onGloballyPositioned { coordinates -> surfacePosition = coordinates.positionInRoot() }
        .then(
            if (interaction != null) {
                Modifier
                    // graphicsLayer, not scale(): the press progress is read in the draw phase, so
                    // holding a glass button animates it without recomposing anything.
                    .graphicsLayer {
                        val pressed = lerp(1f, pressedScale, interaction.pressProgress)
                        scaleX = pressed
                        scaleY = pressed
                    }
                    .pointerInput(interaction) { interaction.detectPress(this) }
            } else {
                Modifier
            },
        )
        .drawWithContent {
            val luminance = backdrop.luminance.value
            val luminanceSigned = (luminance * 2f - 1f).let { sign(it) * it * it }
            val press = interaction?.pressProgress ?: 0f
            // Blur tracks the background the same way SimpMusic's does: brighter scenes get a softer,
            // wider pane, dark ones keep more of the artwork readable behind it.
            val radius =
                if (luminanceSigned > 0f) {
                    lerp(blurPx * 0.6f, blurPx, luminanceSigned)
                } else {
                    lerp(blurPx * 0.6f, blurPx * 0.25f, -luminanceSigned)
                } + blurPx * 0.12f * press

            val outline = shape.createOutline(size, layoutDirection, this)
            run {
                val backdropOffset = backdrop.positionInRoot - surfacePosition
                drawIntoCanvas { canvas ->
                    val paint = Paint()
                    paint.colorFilter =
                        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        paint.asFrameworkPaint().applyGlassBlur(radius)
                    }
                    canvas.saveLayer(Rect(Offset.Zero, Size(size.width, size.height)), paint)
                    // The recorded layer covers the whole source region, so it is translated by the
                    // glass surface's own offset inside it: what lands under the glass is exactly
                    // the content that is behind it on screen.
                    translate(left = backdropOffset.x, top = backdropOffset.y) {
                        drawLayer(backdrop.layer)
                    }
                    canvas.restore()
                }

                // The rim: a directional sweep, which reads well on an elongated pill and stays
                // visible on a small circle.
                val rimBrush =
                    Brush.linearGradient(
                        colors =
                            listOf(
                                Color.White.copy(alpha = 0.55f),
                                Color.White.copy(alpha = 0.08f),
                                Color.White.copy(alpha = 0.22f),
                            ),
                        start = Offset(0f, 0f),
                        end = Offset(size.width, size.height),
                    )
                drawOutline(outline, brush = rimBrush, style = Stroke(width = rimWidth))

                // The scrim darkens more as the background brightens, so the glass never washes out
                // to white on a bright cover.
                val darken = lerp(minScrim, maxScrim, ((luminance - 0.3f) / 0.5f).coerceIn(0f, 1f))
                drawRect((if (isDark) Color.Black else Color.White).copy(alpha = darken))
                if (press > 0f) {
                    drawRect(
                        brush =
                            Brush.radialGradient(
                                colors =
                                    listOf(
                                        Color.White.copy(alpha = 0.18f * press),
                                        Color.Transparent,
                                    ),
                                center = interaction?.touchPosition ?: Offset(size.width / 2f, size.height / 2f),
                                radius = size.minDimension * 1.2f,
                            ),
                        blendMode = BlendMode.Plus,
                    )
                }
            }
            drawContent()
        }
}

@RequiresApi(Build.VERSION_CODES.S)
private fun android.graphics.Paint.applyGlassBlur(radius: Float) {
    setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP))
}

/**
 * A liquid-glass surface wrapping arbitrary [content] — a pill of icon buttons, a single circle,
 * a whole player control row.
 */
@Composable
fun LiquidGlassContainer(
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier,
    shape: Shape = CircleShape,
    enabled: Boolean = LocalLiquidGlassEnabled.current,
    interactive: Boolean = true,
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.liquidGlass(backdrop = backdrop, shape = shape, enabled = enabled, interactive = interactive),
        contentAlignment = contentAlignment,
        content = content,
    )
}

/**
 * Convenience wrapper for the common single-icon case: the circular glass buttons the player and
 * the detail screens use for back, close, share and the overflow menu.
 */
@Composable
fun LiquidGlassIconButton(
    backdrop: GlassBackdrop?,
    modifier: Modifier = Modifier.size(48.dp),
    shape: Shape = CircleShape,
    enabled: Boolean = LocalLiquidGlassEnabled.current,
    tint: Color = Color.White,
    imageVector: ImageVector? = null,
    painter: Painter? = null,
    contentDescription: String? = null,
    onClick: () -> Unit,
) {
    LiquidGlassContainer(
        backdrop = backdrop,
        modifier = modifier,
        shape = shape,
        enabled = enabled,
        interactive = true,
    ) {
        IconButton(onClick = onClick) {
            when {
                imageVector != null -> Icon(imageVector = imageVector, contentDescription = contentDescription, tint = tint)
                painter != null -> Icon(painter = painter, contentDescription = contentDescription, tint = tint)
                else -> Unit
            }
        }
    }
}

/**
 * Observe-only drag/press recogniser. It never consumes events, so a glass surface can react to a
 * press while the buttons it wraps still handle their own taps.
 */
internal suspend fun PointerInputScope.inspectDragGestures(
    onDragStart: (down: PointerInputChange) -> Unit = {},
    onDragEnd: (change: PointerInputChange) -> Unit = {},
    onDragCancel: () -> Unit = {},
    onDrag: (change: PointerInputChange, dragAmount: Offset) -> Unit,
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)

        val down = awaitFirstDown(requireUnconsumed = false)

        onDragStart(down)
        onDrag(down, Offset.Zero)
        val upEvent =
            drag(
                pointerId = down.id,
                onDrag = { onDrag(it, it.positionChange()) },
            )
        if (upEvent == null) {
            onDragCancel()
        } else {
            onDragEnd(upEvent)
        }
    }
}

private suspend inline fun AwaitPointerEventScope.drag(
    pointerId: PointerId,
    onDrag: (PointerInputChange) -> Unit,
): PointerInputChange? {
    val isPointerUp = currentEvent.changes.fastFirstOrNull { it.id == pointerId }?.pressed != true
    if (isPointerUp) {
        return null
    }
    var pointer = pointerId
    while (true) {
        val change = awaitDragOrUp(pointer) ?: return null
        if (change.isConsumed) {
            return null
        }
        if (change.changedToUpIgnoreConsumed()) {
            return change
        }
        onDrag(change)
        pointer = change.id
    }
}

private suspend inline fun AwaitPointerEventScope.awaitDragOrUp(pointerId: PointerId): PointerInputChange? {
    var pointer = pointerId
    while (true) {
        val event = awaitPointerEvent()
        val dragEvent = event.changes.fastFirstOrNull { it.id == pointer } ?: return null
        if (dragEvent.changedToUpIgnoreConsumed()) {
            val otherDown = event.changes.fastFirstOrNull { it.pressed }
            if (otherDown == null) {
                return dragEvent
            } else {
                pointer = otherDown.id
            }
        } else {
            val hasDragged = dragEvent.previousPosition != dragEvent.position
            if (hasDragged) {
                return dragEvent
            }
        }
    }
}

private const val GLASS_SAMPLE_SIZE = 5
private const val GLASS_SAMPLE_INTERVAL_MS = 2_500L
private const val GLASS_LUMINANCE_THRESHOLD = 0.02f

/**
 * Samples a tiny GPU-rendered copy of the backdrop and animates towards its average luminance, so
 * the glass can darken over bright artwork and lift over dark artwork.
 *
 * Failure is not fatal by design: pixel readback is the one step that can be unavailable, and the
 * glass simply keeps its neutral tint when it is.
 */
@Composable
fun rememberGlassLuminance(
    backdrop: GlassBackdrop?,
    enabled: Boolean = true,
): State<Float> {
    if (backdrop == null || !enabled) {
        return remember { mutableStateOf(0.5f) }
    }
    val sample = rememberGraphicsLayer()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current

    LaunchedEffect(backdrop, sample, lifecycle, density, layoutDirection) {
        val source = backdrop.layer
        lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (isActive) {
                val sourceSize = source.size
                // A layer has no display list until its first draw.
                if (sourceSize.width > 0 && sourceSize.height > 0) {
                    try {
                        sample.record(
                            density = density,
                            layoutDirection = layoutDirection,
                            size = IntSize(GLASS_SAMPLE_SIZE, GLASS_SAMPLE_SIZE),
                        ) {
                            scale(
                                scaleX = GLASS_SAMPLE_SIZE.toFloat() / sourceSize.width,
                                scaleY = GLASS_SAMPLE_SIZE.toFloat() / sourceSize.height,
                                pivot = Offset.Zero,
                            ) {
                                drawLayer(source)
                            }
                        }
                        val pixelMap = sample.toImageBitmap().toPixelMap()
                        var sum = 0f
                        for (y in 0 until pixelMap.height) {
                            for (x in 0 until pixelMap.width) {
                                val color = pixelMap[x, y]
                                sum += color.luminance()
                            }
                        }
                        val average =
                            (sum / (pixelMap.width * pixelMap.height).coerceAtLeast(1)).coerceIn(0.3f, 0.8f)
                        if (abs(average - backdrop.luminance.targetValue) >= GLASS_LUMINANCE_THRESHOLD) {
                            backdrop.luminance.animateTo(average, tween(500))
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (error: Exception) {
                        // Keep the last tint and stop sampling: the glass still looks right, it just
                        // stops adapting.
                        return@repeatOnLifecycle
                    }
                }
                delay(GLASS_SAMPLE_INTERVAL_MS)
            }
        }
    }
    return backdrop.luminance.asState()
}

/** The padding a glass pill keeps around the controls it wraps. */
internal val GlassContentPadding: Dp = 12.dp
