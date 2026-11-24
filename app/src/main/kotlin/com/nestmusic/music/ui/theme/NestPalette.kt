package com.nestmusic.music.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb

/**
 * Apple Music / iOS 26 OLED dark & crisp light color schemes.
 * Pure neutral charcoal/black surfaces let album covers and Liquid Glass take center stage,
 * paired with #FA2D48 Apple Music Red interactive accents.
 */
private val OledBlack = Color(0xFF000000)
private val SurfaceLowestDark = Color(0xFF070708)
private val SurfaceLowDark = Color(0xFF0E0E10)
private val SurfaceDark = Color(0xFF161618)
private val SurfaceHighDark = Color(0xFF1C1C1E)
private val SurfaceHighestDark = Color(0xFF2C2C2E)
private val InkLight = Color(0xFFFFFFFF)
private val InkMutedDark = Color(0xFF98989F)

private val PaperLight = Color(0xFFFFFFFF)
private val GroupedLight = Color(0xFFF2F2F7)
private val InkDark = Color(0xFF111114)
private val InkMutedLight = Color(0xFF6E6E73)

private val NestDarkScheme = darkColorScheme(
    primary = NestAppleRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF3A1018),
    onPrimaryContainer = Color(0xFFFFD9DE),
    inversePrimary = NestAppleRedLight,
    secondary = NestAppleRed,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF2C2C2E),
    onSecondaryContainer = Color.White,
    tertiary = Color(0xFF0A84FF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF102A45),
    onTertiaryContainer = Color(0xFFD6E9FF),
    background = OledBlack,
    onBackground = InkLight,
    surface = OledBlack,
    onSurface = InkLight,
    surfaceVariant = SurfaceHighDark,
    onSurfaceVariant = InkMutedDark,
    surfaceTint = Color.Transparent,
    inverseSurface = InkLight,
    inverseOnSurface = InkDark,
    error = Color(0xFFFF453A),
    onError = Color.White,
    errorContainer = Color(0xFF4F1210),
    onErrorContainer = Color(0xFFFFDAD6),
    outline = Color(0xFF48484A),
    outlineVariant = Color(0xFF2C2C2E),
    scrim = Color.Black,
    surfaceBright = Color(0xFF343438),
    surfaceDim = OledBlack,
    surfaceContainerLowest = SurfaceLowestDark,
    surfaceContainerLow = SurfaceLowDark,
    surfaceContainer = SurfaceDark,
    surfaceContainerHigh = SurfaceHighDark,
    surfaceContainerHighest = SurfaceHighestDark,
)

private val NestLightScheme = lightColorScheme(
    primary = NestAppleRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFE2E6),
    onPrimaryContainer = Color(0xFF3D000B),
    inversePrimary = NestAppleRedLight,
    secondary = NestAppleRed,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEBEBF0),
    onSecondaryContainer = InkDark,
    tertiary = Color(0xFF007AFF),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFDCEBFF),
    onTertiaryContainer = Color(0xFF00264D),
    background = PaperLight,
    onBackground = InkDark,
    surface = PaperLight,
    onSurface = InkDark,
    surfaceVariant = GroupedLight,
    onSurfaceVariant = InkMutedLight,
    surfaceTint = Color.Transparent,
    inverseSurface = InkDark,
    inverseOnSurface = InkLight,
    error = Color(0xFFFF3B30),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    outline = Color(0xFFC7C7CC),
    outlineVariant = Color(0xFFE5E5EA),
    scrim = Color.Black,
    surfaceBright = Color.White,
    surfaceDim = GroupedLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF7F7FA),
    surfaceContainer = GroupedLight,
    surfaceContainerHigh = Color(0xFFEAEAEF),
    surfaceContainerHighest = Color(0xFFE0E0E6),
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
        val onAccent = if (accent.luminance() > 0.62f) InkDark else Color.White
        base.copy(
            primary = accent,
            onPrimary = onAccent,
            primaryContainer = blend(accent, base.surfaceContainerHigh, 0.76f),
            onPrimaryContainer = if (darkTheme) InkLight else InkDark,
            inversePrimary = accent,
            surfaceTint = Color.Transparent,
        )
    }
    return if (darkTheme && pureBlack) {
        tinted.copy(
            surface = Color.Black,
            background = Color.Black,
            surfaceDim = Color.Black,
            surfaceContainerLowest = Color.Black,
            surfaceContainerLow = Color(0xFF0A0A0C),
            surfaceContainer = Color(0xFF121214),
            surfaceContainerHigh = Color(0xFF1A1A1D),
            surfaceContainerHighest = Color(0xFF242428),
            surfaceBright = Color(0xFF2C2C30),
        )
    } else {
        tinted
    }
}

private fun isDesignedAccent(color: Color): Boolean {
    val argb = color.toArgb()
    return argb == DefaultThemeColor.toArgb() ||
        argb == LegacyThemeColor.toArgb() ||
        argb == Color(0xFFF97316).toArgb() ||
        argb == Color(0xFFFB8016).toArgb() ||
        argb == NestAppleRed.toArgb() ||
        argb == NestAppleRedLight.toArgb()
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
