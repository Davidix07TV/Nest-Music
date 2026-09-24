/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Sampled from the Nest logo: violet sky, raspberry, tangerine horizon,
 * then the deep blue of the water.
 */
val NestViolet = Color(0xFF40108A)
val NestMagenta = Color(0xFF9A146E)
val NestRaspberry = Color(0xFFE02049)
val NestTangerine = Color(0xFFFB8016)
val NestWater = Color(0xFF12144A)
val NestNight = Color(0xFF07081C)

val NestInk = NestNight
val NestEmber = NestTangerine
val NestEmberDeep = NestRaspberry
val NestSand = Color(0xFFE7D4FF)
val NestCream = Color(0xFFFFF7F8)

val NestSunsetColors = listOf(NestViolet, NestMagenta, NestRaspberry, NestTangerine)

val NestSunsetBrush = Brush.verticalGradient(NestSunsetColors)

val NestSunsetBrushHorizontal = Brush.horizontalGradient(
    listOf(NestViolet, NestRaspberry, NestTangerine),
)
