/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
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

val DefaultThemeColor = Color(0xFFED5564)
val SunsetThemeColor = Color(0xFFFF5E36)

// Sunset palette accent tones inspired by the Nest Music logo
val SunsetPurple = Color(0xFF8B5CF6)
val SunsetOrange = Color(0xFFFF6E40)
val SunsetDeepBlue = Color(0xFF1E1B4B)
val SunsetElectricCyan = Color(0xFF38BDF8)

private fun getSunsetDarkColorScheme(pureBlack: Boolean): ColorScheme {
    return darkColorScheme(
        primary = Color(0xFFB388FF),
        onPrimary = Color(0xFF1E0045),
        primaryContainer = Color(0xFF5B21B6),
        onPrimaryContainer = Color(0xFFEDE9FE),
        secondary = Color(0xFFFF7A45),
        onSecondary = Color(0xFF431407),
        secondaryContainer = Color(0xFF9A3412),
        onSecondaryContainer = Color(0xFFFFEDD5),
        tertiary = Color(0xFF38BDF8),
        onTertiary = Color(0xFF082F49),
        tertiaryContainer = Color(0xFF0369A1),
        onTertiaryContainer = Color(0xFFE0F2FE),
        background = if (pureBlack) Color.Black else Color(0xFF0D0B18),
        onBackground = Color(0xFFF1F5F9),
        surface = if (pureBlack) Color.Black else Color(0xFF141124),
        onSurface = Color(0xFFF1F5F9),
        surfaceVariant = if (pureBlack) Color(0xFF131120) else Color(0xFF221C38),
        onSurfaceVariant = Color(0xFFCBD5E1),
        surfaceContainer = if (pureBlack) Color(0xFF0A0912) else Color(0xFF1A1630),
        surfaceContainerHigh = if (pureBlack) Color(0xFF12101E) else Color(0xFF231E3D),
        surfaceContainerHighest = if (pureBlack) Color(0xFF1A172A) else Color(0xFF2E274D),
        outline = Color(0xFF64748B),
        outlineVariant = Color(0xFF334155),
        inversePrimary = Color(0xFF7C3AED),
        inverseSurface = Color(0xFFF1F5F9),
        inverseOnSurface = Color(0xFF0D0B18),
    )
}

private fun getSunsetLightColorScheme(): ColorScheme {
    return lightColorScheme(
        primary = Color(0xFF7C3AED),
        onPrimary = Color.White,
        primaryContainer = Color(0xFFEDE9FE),
        onPrimaryContainer = Color(0xFF3B0764),
        secondary = Color(0xFFEA580C),
        onSecondary = Color.White,
        secondaryContainer = Color(0xFFFFEDD5),
        onSecondaryContainer = Color(0xFF7C2D12),
        tertiary = Color(0xFF0284C7),
        onTertiary = Color.White,
        tertiaryContainer = Color(0xFFE0F2FE),
        onTertiaryContainer = Color(0xFF075985),
        background = Color(0xFFFAF7FF),
        onBackground = Color(0xFF18112C),
        surface = Color(0xFFFFFFFF),
        onSurface = Color(0xFF18112C),
        surfaceVariant = Color(0xFFF1EEF8),
        onSurfaceVariant = Color(0xFF474354),
        surfaceContainer = Color(0xFFF6F2FC),
        surfaceContainerHigh = Color(0xFFEFE9F7),
        surfaceContainerHighest = Color(0xFFE7DFF2),
        outline = Color(0xFF79747E),
        outlineVariant = Color(0xFFCAC4D0),
        inversePrimary = Color(0xFFB388FF),
    )
}

@Composable
fun MetrolistTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val isSunset = themeColor == SunsetThemeColor
    val useSystemDynamicColor = (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

    val baseColorScheme = when {
        isSunset -> {
            if (darkTheme) getSunsetDarkColorScheme(pureBlack) else getSunsetLightColorScheme()
        }
        useSystemDynamicColor -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            rememberDynamicColorScheme(
                seedColor = themeColor,
                isDark = darkTheme,
                specVersion = ColorSpec.SpecVersion.SPEC_2025,
                style = PaletteStyle.TonalSpot
            )
        }
    }

    val colorScheme = remember(baseColorScheme, pureBlack, darkTheme, isSunset) {
        if (darkTheme && pureBlack && !isSunset) {
            baseColorScheme.pureBlack(true)
        } else {
            baseColorScheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
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
