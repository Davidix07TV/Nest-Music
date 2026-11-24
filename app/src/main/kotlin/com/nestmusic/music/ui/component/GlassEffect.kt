/*
 * Nest Music Project (C) 2026
 * Licensed under GPL-3.0 | Adapted from Nest Music (Nest Music).
 */

package com.nestmusic.music.ui.component

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.nestmusic.music.ui.component.backdrop.Backdrop
import com.nestmusic.music.ui.component.backdrop.BackdropEffectScope
import com.nestmusic.music.ui.component.backdrop.backdrops.EmptyBackdrop
import com.nestmusic.music.ui.component.backdrop.drawBackdrop
import com.nestmusic.music.ui.component.backdrop.effects.blur
import com.nestmusic.music.ui.component.backdrop.effects.colorControls
import com.nestmusic.music.ui.component.backdrop.effects.lens
import com.nestmusic.music.ui.component.backdrop.highlight.Highlight
import com.nestmusic.music.ui.component.backdrop.highlight.HighlightElement
import com.nestmusic.music.ui.component.backdrop.highlight.HighlightStyle
import com.nestmusic.music.ui.component.backdrop.internal.ShapeProvider
import com.nestmusic.music.ui.component.backdrop.isRenderEffectSupported
import com.nestmusic.music.ui.component.backdrop.shadow.Shadow
import com.nestmusic.music.ui.theme.GlassTintTokens
import com.nestmusic.music.ui.theme.outerDarkRim

enum class GlassStyle {
    LIQUID,
    FROSTED,
    BLUR,
    TRANSPARENT,
}

@Immutable
data class GlassEffectConfig(
    val style: GlassStyle = GlassStyle.LIQUID,
    val blurRadius: Float = 3f,
    val lensHeightFraction: Float = 0.60f,
    val lensAmountFraction: Float = 0.69f,
    val depthEffect: Boolean = true,
    val chromaticAberration: Boolean = true,
    val saturation: Float = 1.15f,
    val surfaceColor: Color = Color.Unspecified,
    val surfaceOpacity: Float = 0.25f,
    val tintColor: Color = Color.Unspecified,
    val highlightColor: Color = Color.Unspecified,
    val highlightOpacity: Float = EdgeHighlightAlpha,
    val resolutionScale: Float = 1f,
)

internal const val EdgeHighlightAlpha = 0.55f
internal val EdgeHighlightWidth = 0.75.dp
internal const val HighlightAngleFrozen = 45f

val LocalGlassEffectConfig = staticCompositionLocalOf { GlassEffectConfig() }
val LocalAppBackdrop = compositionLocalOf<Backdrop> { EmptyBackdrop }
val LocalAppBackdropFrozen = staticCompositionLocalOf<() -> Boolean> { { false } }
val LocalAmbientBackgroundColor = compositionLocalOf<State<Color>> { mutableStateOf(Color.Unspecified) }

@Composable
fun glassUsesDarkAppearance(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

internal fun shouldUseTranslucentGlassFallback(
    style: GlassStyle,
    renderEffectSupported: Boolean,
): Boolean = style == GlassStyle.TRANSPARENT || !renderEffectSupported

private fun Color.isHueless(): Boolean = GlassTintTokens.isHueless(this)

/**
 * Authoritative Nest Music/Nest Music Liquid Glass surface:
 * - Captures [backdrop] (or [LocalAppBackdrop]) through a saturation boost,
 *   frosted blur, and AGSL rounded-rect convex lens refraction with dispersion
 * - Draws the specular edge pluck [Highlight] + opposing [outerDarkRim]
 * - Freezes automatically via [frozen] during sheet drags and scrolls so
 *   frame times stay under 2ms on 120Hz displays.
 */
@Composable
fun Modifier.liquidGlassEffect(
    config: GlassEffectConfig = LocalGlassEffectConfig.current,
    shape: CornerBasedShape,
    backdrop: Backdrop = LocalAppBackdrop.current,
    blurRadiusDp: Float = config.blurRadius,
    surfaceTint: Color = Color.Unspecified,
    rimTintColor: Color = Color.Unspecified,
    highlightAlpha: Float = EdgeHighlightAlpha,
    applyEdgeEffects: Boolean = true,
    outerShadow: Boolean = false,
    resolutionScale: Float = config.resolutionScale,
    frozen: () -> Boolean = LocalAppBackdropFrozen.current,
): Modifier {
    val isDark = glassUsesDarkAppearance()
    val density = LocalDensity.current

    val blurPx = with(density) { blurRadiusDp.dp.toPx() }
    // Reference bar height is ~56dp; lens height & refraction scale cleanly off Apple's 0.60 / 0.69 ratios.
    val lensBasePx = with(density) { 28.dp.toPx() }
    val lensHeightPx = lensBasePx * config.lensHeightFraction
    val lensAmountPx = lensBasePx * config.lensAmountFraction
    val saturation = config.saturation

    val ambientColor = LocalAmbientBackgroundColor.current.value
    val isHeroContext = ambientColor.isSpecified && !ambientColor.isHueless()

    val baseSurfaceColor = when {
        config.surfaceColor.isSpecified -> config.surfaceColor
        isDark -> Color(0xFF141418)
        else -> Color(0xFFF6F6FA)
    }
    val baseSurfaceOpacity = config.surfaceOpacity.coerceIn(0f, 1f)

    val tintColor = when {
        surfaceTint.isSpecified -> surfaceTint
        config.tintColor.isSpecified -> config.tintColor
        isHeroContext -> GlassTintTokens.heroSurfaceTint(ambientColor, isDark)
        else -> GlassTintTokens.clearGlassTint(isDark)
    }
    val tintOpacity = when {
        surfaceTint.isSpecified -> 1f
        config.tintColor.isSpecified -> 1f
        isHeroContext -> GlassTintTokens.heroTintOpacity(isDark)
        else -> 1f
    }
    val effectiveTintAlpha = (tintColor.alpha * tintOpacity).coerceIn(0f, 1f)

    val glassFade = tween<Color>(GlassThemeFadeMillis)
    val animBaseColor = animateColorAsState(baseSurfaceColor.copy(alpha = baseSurfaceOpacity), glassFade, label = "glassBase")
    val animTintColor = animateColorAsState(tintColor.copy(alpha = effectiveTintAlpha), glassFade, label = "glassTint")

    if (shouldUseTranslucentGlassFallback(config.style, isRenderEffectSupported())) {
        return this
            .clip(shape)
            .background(baseSurfaceColor.copy(alpha = baseSurfaceOpacity))
            .background(tintColor.copy(alpha = effectiveTintAlpha))
    }

    val animatedHighlightAngle = HighlightAngleFrozen
    val shapeBlock: () -> Shape = remember(shape) { { shape } }
    val plainBlur = config.style == GlassStyle.BLUR
    val skipLens = plainBlur || config.style == GlassStyle.FROSTED

    val effectsBlock: BackdropEffectScope.() -> Unit = remember(
        saturation,
        blurPx,
        applyEdgeEffects,
        skipLens,
        lensHeightPx,
        lensAmountPx,
        config.depthEffect,
        config.chromaticAberration,
    ) {
        {
            if (saturation != 1f) {
                colorControls(saturation = saturation)
            }
            if (blurPx > 0f) {
                blur(blurPx)
            }
            if (!skipLens &&
                applyEdgeEffects &&
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                (lensHeightPx > 0f || lensAmountPx > 0f)
            ) {
                lens(
                    refractionHeight = lensHeightPx,
                    refractionAmount = lensAmountPx,
                    depthEffect = config.depthEffect,
                    chromaticAberration = config.chromaticAberration,
                )
            }
        }
    }

    val isHeroContextForPluck = (ambientColor.isSpecified && !ambientColor.isHueless()) || rimTintColor.isSpecified
    val rimColor = when {
        config.highlightColor.isSpecified -> config.highlightColor
        rimTintColor.isSpecified -> rimTintColor
        else -> GlassTintTokens.pluckColor(isDark, ambientColor)
    }
    val baseHighlightAlpha = when {
        highlightAlpha != EdgeHighlightAlpha -> highlightAlpha
        else -> GlassTintTokens.pluckOpacity(isDark, isHeroContextForPluck)
    }
    val rimAlpha = (baseHighlightAlpha * (config.highlightOpacity / EdgeHighlightAlpha)).coerceIn(0f, 1f)

    val animRim = animateColorAsState(rimColor.copy(alpha = rimAlpha), glassFade, label = "glassRim")
    val highlightBlock: (() -> Highlight?)? = remember(applyEdgeEffects, plainBlur, rimTintColor, animatedHighlightAngle) {
        if (applyEdgeEffects && !plainBlur) {
            {
                Highlight(
                    width = EdgeHighlightWidth,
                    style = HighlightStyle.Default(
                        color = animRim.value,
                        angle = animatedHighlightAngle,
                    ),
                )
            }
        } else {
            null
        }
    }

    val shadowBlock: (() -> Shadow?)? = remember(applyEdgeEffects, outerShadow) {
        if (applyEdgeEffects && outerShadow) ({ Shadow.Default }) else null
    }

    val darkRimAlpha = (DarkRimAlpha * (config.highlightOpacity / EdgeHighlightAlpha)).coerceIn(0f, 1f)

    val surfaceBlock: DrawScope.() -> Unit = remember {
        {
            val base = animBaseColor.value
            if (base.alpha > 0f) drawRect(color = base, size = size)
            val tint = animTintColor.value
            if (tint.alpha > 0f) drawRect(color = tint, size = size)
        }
    }

    return drawBackdrop(
        backdrop = backdrop,
        shape = shapeBlock,
        effects = effectsBlock,
        highlight = highlightBlock,
        shadow = shadowBlock,
        onDrawSurface = surfaceBlock,
        backdropScale = resolutionScale,
        frozen = frozen,
    ).then(
        if (applyEdgeEffects && !plainBlur && darkRimAlpha > 0f) {
            Modifier.outerDarkRim(
                shape = shape,
                width = DarkRimWidth,
                alpha = darkRimAlpha,
                angle = animatedHighlightAngle,
            )
        } else {
            Modifier
        }
    )
}

@Composable
internal fun Modifier.glassRim(shape: Shape): Modifier {
    val config = LocalGlassEffectConfig.current
    if (!LocalLiquidGlassEnabled.current || config.style == GlassStyle.BLUR ||
        shouldUseTranslucentGlassFallback(config.style, isRenderEffectSupported())
    ) {
        return this
    }
    val isDark = glassUsesDarkAppearance()
    val ambientColor = LocalAmbientBackgroundColor.current.value
    val heroContext = ambientColor.isSpecified && !ambientColor.isHueless()
    val rimColor = if (config.highlightColor.isSpecified) {
        config.highlightColor
    } else {
        GlassTintTokens.pluckColor(isDark, ambientColor)
    }
    val rimAlpha = (
        GlassTintTokens.pluckOpacity(isDark, heroContext) *
            (config.highlightOpacity / EdgeHighlightAlpha)
        ).coerceIn(0f, 1f)
    val rim = rimColor.copy(alpha = rimAlpha)
    val shapeProvider = remember(shape) { ShapeProvider { shape } }
    val highlight = remember(rim) {
        Highlight(
            width = EdgeHighlightWidth,
            style = HighlightStyle.Default(color = rim, angle = HighlightAngleFrozen),
        )
    }
    return then(HighlightElement(shapeProvider = shapeProvider, highlight = { highlight }))
}

private const val GlassThemeFadeMillis = 280
private val DarkRimWidth = 1.6.dp
private const val DarkRimAlpha = 0.32f
