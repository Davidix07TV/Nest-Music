/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Logo palette. Surfaces stay the night-blue of the water; the accent is the
 * tangerine horizon. A custom theme color only retints the accent.
 */
private val Night = NestNight
private val NightRaised = Color(0xFF14163A)
private val NightHigh = Color(0xFF1C1E4E)
private val NightHighest = Color(0xFF282A62)
private val Mist = Color(0xFFFFF7F8)
private val MistMuted = Color(0xFFCDB8E6)
private val Horizon = NestTangerine
private val InkText = Color(0xFF1A1030)

private val NestDarkScheme = darkColorScheme(
    primary = Horizon,
    onPrimary = Color(0xFF2A1004),
    primaryContainer = Color(0xFF5A220C),
    onPrimaryContainer = Color(0xFFFFD8C2),
    inversePrimary = NestRaspberry,
    secondary = NestRaspberry,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF5C1238),
    onSecondaryContainer = Color(0xFFFFD8E6),
    tertiary = Color(0xFF8B96FF),
    onTertiary = Color(0xFF10144A),
    tertiaryContainer = Color(0xFF2A2C78),
    onTertiaryContainer = Color(0xFFDEE2FF),
    background = Night,
    onBackground = Mist,
    surface = Night,
    onSurface = Mist,
    surfaceVariant = Color(0xFF32285A),
    onSurfaceVariant = MistMuted,
    surfaceTint = Horizon,
    inverseSurface = Mist,
    inverseOnSurface = Color(0xFF2A1840),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF8E7CB0),
    outlineVariant = Color(0xFF3E3460),
    scrim = Color.Black,
    surfaceBright = Color(0xFF34366E),
    surfaceDim = Night,
    surfaceContainerLowest = Color(0xFF050614),
    surfaceContainerLow = Color(0xFF0E102C),
    surfaceContainer = NightRaised,
    surfaceContainerHigh = NightHigh,
    surfaceContainerHighest = NightHighest,
)

private val NestLightScheme = lightColorScheme(
    primary = Color(0xFFE24A12),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFD8C4),
    onPrimaryContainer = Color(0xFF3B1400),
    inversePrimary = Horizon,
    secondary = Color(0xFFC41858),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFD8E4),
    onSecondaryContainer = Color(0xFF3E0018),
    tertiary = Color(0xFF3A2FA8),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFE4E0FF),
    onTertiaryContainer = Color(0xFF140E46),
    background = Color(0xFFFFF6F8),
    onBackground = InkText,
    surface = Color(0xFFFFF8F7),
    onSurface = InkText,
    surfaceVariant = Color(0xFFF3E4EE),
    onSurfaceVariant = Color(0xFF5C4560),
    surfaceTint = Color(0xFFE24A12),
    inverseSurface = Color(0xFF2A1840),
    inverseOnSurface = Mist,
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFF8A7084),
    outlineVariant = Color(0xFFE4D0DC),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = Color(0xFFF0E0E6),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1F4),
    surfaceContainer = Color(0xFFFFE8EE),
    surfaceContainerHigh = Color(0xFFF8DCE6),
    surfaceContainerHighest = Color(0xFFF0D0DC),
)

fun nestColorScheme(
    darkTheme: Boolean,
    accent: Color,
    pureBlack: Boolean,
): ColorScheme {
    val base = if (darkTheme) NestDarkScheme else NestLightScheme
    val tinted = if (isDesignedAccent(accent)) {
        base
    } else {
        val onAccent = if (accent.luminance() > 0.62f) InkText else Color.White
        base.copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = blend(accent, base.surfaceContainerHigh, 0.72f),
            onPrimaryContainer = if (darkTheme) Mist else InkText,
            inversePrimary = accent,
            surfaceTint = accent,
        )
    }
    return if (darkTheme && pureBlack) {
        tinted.copy(
            surface = Color.Black,
            background = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color.Black,
            surfaceContainer = Color(0xFF0E0E0E),
            surfaceContainerHigh = Color(0xFF161616),
            surfaceContainerHighest = Color(0xFF1E1E1E),
            surfaceBright = Color(0xFF242424),
        )
    } else {
        tinted
    }
}

private fun isDesignedAccent(color: Color): Boolean {
    val argb = color.toArgb()
    return argb == DefaultThemeColor.toArgb() ||
        argb == LegacyThemeColor.toArgb() ||
        argb == NestTangerine.toArgb() ||
        argb == NestRaspberry.toArgb() ||
        argb == NestViolet.toArgb()
}

private fun blend(from: Color, to: Color, amount: Float): Color {
    val t = amount.coerceIn(0f, 1f)
    return Color(
        red = from.red * (1f - t) + to.red * t,
        green = from.green * (1f - t) + to.green * t,
        blue = from.blue * (1f - t) + to.blue * t,
        alpha = 1f,
    )
}
