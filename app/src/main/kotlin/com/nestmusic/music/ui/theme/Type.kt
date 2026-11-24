package com.nestmusic.music.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Apple Music / iOS 26 SF Pro Display typography scale with tight optical tracking.
 */
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = SFProDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 48.sp,
        lineHeight = 52.sp,
        letterSpacing = (-1.1).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = SFProDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 38.sp,
        lineHeight = 42.sp,
        letterSpacing = (-0.85).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = SFProDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 34.sp,
        lineHeight = 38.sp,
        letterSpacing = (-0.75).sp,
    ),
    headlineLarge = TextStyle(
        fontFamily = SFProDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 31.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.65).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = SFProDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 26.sp,
        lineHeight = 31.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = SFProDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        lineHeight = 27.sp,
        letterSpacing = (-0.38).sp,
    ),
    titleLarge = TextStyle(
        fontFamily = SFProDisplayFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.32).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = SFProDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 21.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleSmall = TextStyle(
        fontFamily = SFProDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = (-0.12).sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = SFProDisplayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        letterSpacing = (-0.18).sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = SFProDisplayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 19.sp,
        letterSpacing = (-0.1).sp,
    ),
    bodySmall = TextStyle(
        fontFamily = SFProDisplayFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = SFProDisplayFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = (-0.08).sp,
    ),
    labelMedium = TextStyle(
        fontFamily = SFProDisplayFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.05.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = SFProDisplayFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.08.sp,
    ),
)
