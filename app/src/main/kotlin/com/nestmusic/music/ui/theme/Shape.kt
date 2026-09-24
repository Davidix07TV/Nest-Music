/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.nestmusic.music.constants.ThumbnailCornerRadius

/** Squircle scale, closer to the app icon. Classic keeps stock M3. */
val NestShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(30.dp),
    extraLarge = RoundedCornerShape(40.dp),
)

/** Cover art silhouette. Classic stays almost square; the logo look is a soft tile. */
@Composable
fun coverShape(): Shape =
    if (useNestUi()) RoundedCornerShape(20.dp) else RoundedCornerShape(ThumbnailCornerRadius)
