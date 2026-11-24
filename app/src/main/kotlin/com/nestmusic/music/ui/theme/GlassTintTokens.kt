/*
 * Copyright (C) 2026 Nest Music contributors
 * Adapted from Nest Music (Nest Music) for Nest Music under GPL-3.0.
 */

package com.nestmusic.music.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Apple HIG Liquid Glass tint & rim tokens.
 *
 * Clear Glass (chrome floating over content):
 *  - Light: #F5F5F7 @ 0.06
 *  - Dark:  #FFFFFF @ 0.05
 *
 * Tinted Glass (album / artist hero with extracted color):
 *  - Light: H fixed, S in [0.15, 0.35], L in [0.85, 0.92] @ 0.14
 *  - Dark:  H fixed, S in [0.25, 0.45], L in [0.20, 0.30] @ 0.18
 *  - Low-saturation source (S < 0.08): falls back to Clear Glass
 *
 * Pluck Rim (edge specular stroke):
 *  - Light: #FFFFFF @ 0.55 (Clear) / 0.40 (Tinted)
 *  - Dark:  #FFFFFF @ 0.22 (Clear) / 0.28 (Tinted, shifted +/-10% toward source hue)
 */
object GlassTintTokens {

    val ClearLightColor = Color(0xFFF5F5F7)
    const val ClearLightOpacity = 0.06f

    val ClearDarkColor = Color(0xFFFFFFFF)
    const val ClearDarkOpacity = 0.05f

    const val HeroLightOpacity = 0.14f
    const val HeroDarkOpacity = 0.18f

    const val PluckLightClearOpacity = 0.55f
    const val PluckLightTintedOpacity = 0.40f
    const val PluckDarkClearOpacity = 0.22f
    const val PluckDarkTintedOpacity = 0.28f

    private const val HuelessSaturationThreshold = 0.08f
    private const val DarkPluckHueBlend = 0.10f

    fun clearGlassTint(isDark: Boolean): Color =
        if (isDark) {
            ClearDarkColor.copy(alpha = ClearDarkOpacity)
        } else {
            ClearLightColor.copy(alpha = ClearLightOpacity)
        }

    fun heroSurfaceTint(source: Color, isDark: Boolean): Color {
        if (!source.isSpecified) return clearGlassTint(isDark)
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(source.toArgb(), hsl)
        if (hsl[1] < HuelessSaturationThreshold) {
            return clearGlassTint(isDark)
        }
        if (isDark) {
            hsl[1] = hsl[1].coerceIn(0.25f, 0.45f)
            hsl[2] = hsl[2].coerceIn(0.20f, 0.30f)
        } else {
            hsl[1] = hsl[1].coerceIn(0.15f, 0.35f)
            hsl[2] = hsl[2].coerceIn(0.85f, 0.92f)
        }
        return Color(ColorUtils.HSLToColor(hsl))
    }

    fun heroTintOpacity(isDark: Boolean): Float =
        if (isDark) HeroDarkOpacity else HeroLightOpacity

    fun pluckColor(isDark: Boolean, source: Color = Color.Unspecified): Color {
        if (!isDark || !source.isSpecified) return Color.White
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(source.toArgb(), hsl)
        if (hsl[1] < HuelessSaturationThreshold) return Color.White
        hsl[1] = (hsl[1] * 0.5f).coerceAtMost(0.40f)
        hsl[2] = 0.85f
        val huePeer = Color(ColorUtils.HSLToColor(hsl))
        return lerpRgb(Color.White, huePeer, DarkPluckHueBlend)
    }

    fun pluckOpacity(isDark: Boolean, isTinted: Boolean): Float = when {
        isDark && isTinted -> PluckDarkTintedOpacity
        isDark -> PluckDarkClearOpacity
        isTinted -> PluckLightTintedOpacity
        else -> PluckLightClearOpacity
    }

    fun isHueless(color: Color): Boolean {
        if (!color.isSpecified) return true
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        return hsl[1] < HuelessSaturationThreshold
    }

    fun foregroundOn(background: Color, isDarkFallback: Boolean): Color {
        val lum = if (background.isSpecified) background.luminance() else if (isDarkFallback) 0.05f else 0.92f
        return if (lum > 0.5f) Color(0xFF0B0B0F) else Color.White
    }

    private fun lerpRgb(a: Color, b: Color, t: Float): Color = Color(
        red = a.red + (b.red - a.red) * t,
        green = a.green + (b.green - a.green) * t,
        blue = a.blue + (b.blue - a.blue) * t,
        alpha = 1f,
    )
}
