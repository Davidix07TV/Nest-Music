package com.nestmusic.music.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.nestmusic.music.R

val bbhBartle = FontFamily(
    Font(R.font.bbh_bartle_regular, FontWeight.Normal)
)

private fun outfit(weight: FontWeight) = Font(
    R.font.outfit_wght,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Geometric grotesque used by the logo look. */
val OutfitFamily = FontFamily(
    outfit(FontWeight.Normal),
    outfit(FontWeight.Medium),
    outfit(FontWeight.SemiBold),
    outfit(FontWeight.Bold),
    outfit(FontWeight.ExtraBold),
)
