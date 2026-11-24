package com.nestmusic.music.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Apple Music / iOS 26 neutral OLED + #FA2D48 crimson accent palette for Nest Music.
 * Retains legacy symbol names so all existing screens adopt the clean monochrome + crimson look.
 */
val NestAppleRed = Color(0xFFFA2D48)
val NestAppleRedLight = Color(0xFFFF375F)
val NestAppleRedDeep = Color(0xFFD7193A)

val NestViolet = Color(0xFF1C1C1E)
val NestMagenta = Color(0xFFD7193A)
val NestRaspberry = NestAppleRed
val NestTangerine = NestAppleRed
val NestWater = Color(0xFF141416)
val NestNight = Color(0xFF000000)

val NestInk = NestNight
val NestEmber = NestAppleRed
val NestEmberDeep = NestAppleRedDeep
val NestSand = Color(0xFFEBEBF5)
val NestCream = Color(0xFFFFFFFF)

val NestSunsetColors = listOf(NestAppleRedLight, NestAppleRed)

val NestSunsetBrush = Brush.verticalGradient(NestSunsetColors)

val NestSunsetBrushHorizontal = Brush.horizontalGradient(NestSunsetColors)
