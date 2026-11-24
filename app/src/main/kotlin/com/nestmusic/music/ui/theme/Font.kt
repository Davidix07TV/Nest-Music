package com.nestmusic.music.ui.theme

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.nestmusic.music.R

val bbhBartle = FontFamily(
    Font(R.font.bbh_bartle_regular, FontWeight.Normal),
)

/**
 * SF Pro Display family used across the Apple Music / Liquid Glass UI.
 */
val SFProDisplayFamily = FontFamily(
    Font(R.font.sf_pro_display_regular, FontWeight.Normal),
    Font(R.font.sf_pro_display_medium, FontWeight.Medium),
    Font(R.font.sf_pro_display_semibold, FontWeight.SemiBold),
    Font(R.font.sf_pro_display_bold, FontWeight.Bold),
    Font(R.font.sf_pro_display_heavy, FontWeight.ExtraBold),
    Font(R.font.sf_pro_display_heavy, FontWeight.Black),
)

private fun outfit(weight: FontWeight) = Font(
    R.font.outfit_wght,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

/** Kept as an alias to SF Pro Display so every screen shares uniform Apple Music typography. */
val OutfitFamily = SFProDisplayFamily
