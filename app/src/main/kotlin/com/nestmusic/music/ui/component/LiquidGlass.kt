/**
 * Nest Music — Nest Music / Nest Music / Kyant0 Liquid Glass integration
 * Licensed under GPL-3.0
 */

package com.nestmusic.music.ui.component

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.scale
import com.nestmusic.music.ui.component.backdrop.Backdrop
import com.nestmusic.music.ui.component.backdrop.GlassShaderWarmup
import com.nestmusic.music.ui.component.backdrop.backdrops.EmptyBackdrop
import com.nestmusic.music.ui.component.backdrop.backdrops.LayerBackdrop
import com.nestmusic.music.ui.component.backdrop.backdrops.layerBackdrop
import com.nestmusic.music.ui.component.backdrop.backdrops.rememberLayerBackdrop
import com.nestmusic.music.ui.component.backdrop.isRenderEffectSupported
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

val LocalLiquidGlassEnabled = compositionLocalOf { true }
val LocalGlassBackdrop = compositionLocalOf<GlassBackdrop?> { null }

/** Android 12+ supports RenderEffect blur; Android 13+ adds full AGSL lens refraction & dispersion. */
val isLiquidGlassSupported: Boolean
    get() = isRenderEffectSupported()

/**
 * Wraps Nest Music's [LayerBackdrop] while keeping source compatibility with
 * existing Nest Music callers (`rememberGlassBackdrop`, `Modifier.glassBackdrop`,
 * `rememberGlassLuminance`, and `LocalGlassBackdrop`).
 */
@Stable
class GlassBackdrop internal constructor(
    internal val layerBackdrop: LayerBackdrop,
) : Backdrop {
    override val isCoordinatesDependent: Boolean
        get() = layerBackdrop.isCoordinatesDependent

    override fun DrawScope.drawBackdrop(
        density: Density,
        coordinates: LayoutCoordinates?,
        layerBlock: (GraphicsLayerScope.() -> Unit)?,
    ) {
        with(layerBackdrop) {
            drawBackdrop(density, coordinates, layerBlock)
        }
    }

    internal suspend fun averageLuminance(): Float? {
        val graphicsLayer = layerBackdrop.graphicsLayer
        val size = graphicsLayer.size
        if (size.width <= 0 || size.height <= 0) return null
        val bitmap = try {
            graphicsLayer.toImageBitmap()
        } catch (_: Throwable) {
            return null
        }
        return withContext(Dispatchers.Default) {
            val tiny = try {
                val androidBitmap = bitmap.asAndroidBitmap()
                val software = if (androidBitmap.config == Bitmap.Config.HARDWARE) {
                    androidBitmap.copy(Bitmap.Config.ARGB_8888, false)
                } else {
                    androidBitmap
                }
                software?.scale(8, 8, true)
            } catch (_: Throwable) {
                null
            } ?: return@withContext null

            val pixels = IntArray(64)
            tiny.getPixels(pixels, 0, 8, 0, 0, 8, 8)
            if (tiny !== bitmap.asAndroidBitmap()) tiny.recycle()

            var sum = 0f
            for (argb in pixels) {
                sum += Color(argb).luminance()
            }
            sum / pixels.size
        }
    }
}

@Composable
fun rememberGlassBackdrop(): GlassBackdrop {
    LaunchedEffect(Unit) {
        GlassShaderWarmup.warmUp()
    }
    val layer = rememberLayerBackdrop()
    return remember(layer) { GlassBackdrop(layer) }
}

fun Modifier.glassBackdrop(backdrop: GlassBackdrop?): Modifier {
    if (backdrop == null) return this
    return this.layerBackdrop(backdrop.layerBackdrop)
}

@Composable
fun rememberGlassLuminance(
    backdrop: GlassBackdrop?,
    defaultLuminance: Float = 0f,
    sampleEveryMs: Long = 280L,
): State<Float> {
    val enabled = LocalLiquidGlassEnabled.current
    val state = remember { mutableFloatStateOf(defaultLuminance) }
    val active = enabled && backdrop != null && isLiquidGlassSupported
    LaunchedEffect(backdrop, active, defaultLuminance) {
        if (!active || backdrop == null) {
            state.floatValue = defaultLuminance
            return@LaunchedEffect
        }
        while (isActive) {
            withFrameNanos { }
            val measured = backdrop.averageLuminance()
            if (measured != null) {
                state.floatValue = state.floatValue * 0.65f + measured * 0.35f
            }
            delay(sampleEveryMs)
        }
    }
    return state
}

private val DefaultGlassShape = RoundedCornerShape(28.dp)

/**
 * Nest Music-style one-argument Liquid Glass modifier using Apple's 3dp frosted blur +
 * 0.60/0.69 AGSL convex lens refraction + chromatic aberration + specular pluck rim.
 */
@Composable
fun Modifier.nestLiquidGlass(
    shape: CornerBasedShape = CircleShape,
    backdrop: Backdrop? = null,
    blurRadiusDp: Float? = null,
    edgeEffects: Boolean = true,
    outerShadow: Boolean = false,
): Modifier {
    val resolvedBackdrop = backdrop
        ?: LocalGlassBackdrop.current
        ?: LocalAppBackdrop.current
    val appFrozen = LocalAppBackdropFrozen.current
    val config = LocalGlassEffectConfig.current
    return liquidGlassEffect(
        config = config,
        shape = shape,
        backdrop = resolvedBackdrop,
        blurRadiusDp = blurRadiusDp ?: config.blurRadius,
        applyEdgeEffects = edgeEffects,
        outerShadow = outerShadow,
        frozen = {
            GlassMotion.sheet.frozen() ||
                GlassMotion.nav.frozen() ||
                GlassMotion.scroll.frozen() ||
                appFrozen()
        },
    )
}

/**
 * Liquid Glass modifier compatible with all Nest Music surfaces, powered by
 * the vendored Nest Music / Nest Music backdrop + AGSL refraction engine.
 */
@Composable
fun Modifier.liquidGlass(
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    shape: Shape = DefaultGlassShape,
    enabled: Boolean = LocalLiquidGlassEnabled.current,
    interactive: Boolean = true,
    blurRadius: Dp = 3.dp,
    tint: Color? = null,
    tintColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    borderWidth: Dp = EdgeHighlightWidth,
    refraction: Float = 1f,
    fallbackColor: Color = Color.Black.copy(alpha = 0.28f),
): Modifier {
    val hasExplicitTint = tint != null && tint.isSpecified
    val resolvedBackdrop: Backdrop = backdrop ?: if (hasExplicitTint) EmptyBackdrop else LocalAppBackdrop.current
    val resolvedFallback = if (hasExplicitTint) tint!! else fallbackColor

    if (!enabled || !isLiquidGlassSupported || resolvedBackdrop === EmptyBackdrop) {
        val base = this
            .clip(shape)
            .background(resolvedFallback, shape)
        return if (borderWidth > 0.dp && borderColor.isSpecified && borderColor.alpha > 0f && !hasExplicitTint) {
            base.border(borderWidth, borderColor, shape)
        } else {
            base
        }
    }

    val cornerShape: CornerBasedShape = (shape as? CornerBasedShape) ?: DefaultGlassShape
    val appFrozen = LocalAppBackdropFrozen.current
    val baseConfig = LocalGlassEffectConfig.current
    val config = remember(baseConfig, refraction) {
        if (refraction <= 0f) {
            baseConfig.copy(style = GlassStyle.FROSTED, lensHeightFraction = 0f, lensAmountFraction = 0f)
        } else {
            baseConfig.copy(
                lensHeightFraction = baseConfig.lensHeightFraction * refraction.coerceIn(0f, 1.5f),
                lensAmountFraction = baseConfig.lensAmountFraction * refraction.coerceIn(0f, 1.5f),
            )
        }
    }

    return liquidGlassEffect(
        config = config,
        shape = cornerShape,
        backdrop = resolvedBackdrop,
        blurRadiusDp = blurRadius.value.coerceAtLeast(3f),
        surfaceTint = tintColor,
        rimTintColor = borderColor,
        applyEdgeEffects = borderWidth > 0.dp || refraction > 0f,
        frozen = {
            GlassMotion.sheet.frozen() ||
                GlassMotion.nav.frozen() ||
                GlassMotion.scroll.frozen() ||
                appFrozen()
        },
    )
}

@Composable
fun LiquidGlassContainer(
    modifier: Modifier = Modifier,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    shape: Shape = DefaultGlassShape,
    blurRadius: Dp = 3.dp,
    tintColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    fallbackColor: Color = Color.Black.copy(alpha = 0.28f),
    contentAlignment: Alignment = Alignment.Center,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.liquidGlass(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            tintColor = tintColor,
            borderColor = borderColor,
            fallbackColor = fallbackColor,
        ),
        contentAlignment = contentAlignment,
        content = content,
    )
}

@Composable
fun LiquidGlassIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    backdrop: GlassBackdrop? = LocalGlassBackdrop.current,
    shape: Shape = CircleShape,
    blurRadius: Dp = 3.dp,
    tintColor: Color = Color.Unspecified,
    borderColor: Color = Color.Unspecified,
    fallbackColor: Color = Color.White.copy(alpha = 0.14f),
    contentPadding: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier
            .liquidGlass(
                backdrop = backdrop,
                shape = shape,
                blurRadius = blurRadius,
                tintColor = tintColor,
                borderColor = borderColor,
                fallbackColor = fallbackColor,
            )
            .clickable(
                enabled = enabled,
                role = Role.Button,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(contentPadding),
        contentAlignment = Alignment.Center,
        content = content,
    )
}
