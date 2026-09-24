/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.theme

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamiccolor.ColorSpec
import com.materialkolor.rememberDynamicColorScheme
import com.materialkolor.score.Score

// Nest brand seed: the tangerine stop of the logo sunset
val DefaultThemeColor = Color(0xFFF97316)

@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    nestUi: Boolean = true,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // When the classic UI is active, the "default" color is the old coral seed
    val referenceColor = if (nestUi) DefaultThemeColor else LegacyThemeColor
    val effectiveThemeColor =
        if (!nestUi && themeColor == DefaultThemeColor) LegacyThemeColor else themeColor
    // The logo look ignores wallpaper dynamic color. Classic keeps Material You.
    // The classic path lives in its own composable so toggling the look
    // does not reshuffle remember calls.
    val colorScheme = if (nestUi) {
        nestColorScheme(
            darkTheme = darkTheme,
            accent = effectiveThemeColor,
            pureBlack = pureBlack,
        )
    } else {
        classicColorScheme(
            context = context,
            darkTheme = darkTheme,
            pureBlack = pureBlack,
            themeColor = effectiveThemeColor,
            referenceColor = referenceColor,
        )
    }

    // Use standard MaterialTheme instead of MaterialExpressiveTheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = if (nestUi) AppTypography else LegacyTypography,
        shapes = if (nestUi) NestShapes else MaterialTheme.shapes,
        content = content
    )
}

@Composable
private fun classicColorScheme(
    context: Context,
    darkTheme: Boolean,
    pureBlack: Boolean,
    themeColor: Color,
    referenceColor: Color,
): ColorScheme {
    val useSystemDynamicColor = themeColor == referenceColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val baseColorScheme = if (useSystemDynamicColor) {
        if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        rememberDynamicColorScheme(
            seedColor = themeColor,
            isDark = darkTheme,
            specVersion = ColorSpec.SpecVersion.SPEC_2025,
            style = PaletteStyle.TonalSpot,
        )
    }
    return remember(baseColorScheme, pureBlack, darkTheme) {
        if (darkTheme && pureBlack) baseColorScheme.pureBlack(true) else baseColorScheme
    }
}

fun Bitmap.extractThemeColor(): Color {
    val colorsToPopulation = Palette.from(this)
        .maximumColorCount(8)
        .generate()
        .swatches
        .associate { it.rgb to it.population }
    val rankedColors = Score.score(colorsToPopulation)
    return Color(rankedColors.first())
}

fun Bitmap.extractGradientColors(): List<Color> {
    val extractedColors = Palette.from(this)
        .maximumColorCount(64)
        .generate()
        .swatches
        .associate { it.rgb to it.population }

    val orderedColors = Score.score(extractedColors, 2, 0xff4285f4.toInt(), true)
        .sortedByDescending { Color(it).luminance() }

    return if (orderedColors.size >= 2)
        listOf(Color(orderedColors[0]), Color(orderedColors[1]))
    else
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) copy(
        surface = Color.Black,
        background = Color.Black
    ) else this

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}
