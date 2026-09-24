/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Nest brand "sunset" palette, inspired by the app logo:
 * indigo sky fading into raspberry and a tangerine horizon.
 */
val NestViolet = Color(0xFF341B87)
val NestRaspberry = Color(0xFFD6246E)
val NestTangerine = Color(0xFFF97316)

/** Stops of the brand sunset, light-to-dark top to bottom. */
val NestSunsetColors = listOf(NestViolet, NestRaspberry, NestTangerine)

/** Vertical sunset brush, the default brand direction. */
val NestSunsetBrush = Brush.verticalGradient(NestSunsetColors)

/** Horizontal sunset brush, for wide surfaces. */
val NestSunsetBrushHorizontal = Brush.horizontalGradient(NestSunsetColors)
