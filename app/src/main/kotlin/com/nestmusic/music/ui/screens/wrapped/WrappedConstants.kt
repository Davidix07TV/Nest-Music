/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.screens.wrapped

import com.nestmusic.music.R
import java.time.LocalDate

object WrappedConstants {
    /**
     * The Wrapped always covers the current calendar year.
     * In January it refers to the year that just ended (the classic
     * year-end Wrapped window).
     */
    val YEAR: Int
        get() {
            val now = LocalDate.now()
            return if (now.monthValue == 1) now.year - 1 else now.year
        }

    val PLAYLIST_NAME: String
        get() = "Nest Music $YEAR"

    /**
     * The two playlist covers available for [year] (white and black variants).
     * Every year has its own artwork so the created playlist always shows the
     * year it belongs to: add a new entry when a new year approaches.
     * Returns null once we run out of prepared artworks (no cover at all
     * rather than a cover showing the wrong year).
     */
    fun playlistCovers(year: Int): Pair<Int, Int>? = when (year) {
        2026 -> R.drawable.wrapped_playlist_2026_v1 to R.drawable.wrapped_playlist_2026_v2
        2027 -> R.drawable.wrapped_playlist_2027_v1 to R.drawable.wrapped_playlist_2027_v2
        2028 -> R.drawable.wrapped_playlist_2028_v1 to R.drawable.wrapped_playlist_2028_v2
        2029 -> R.drawable.wrapped_playlist_2029_v1 to R.drawable.wrapped_playlist_2029_v2
        2030 -> R.drawable.wrapped_playlist_2030_v1 to R.drawable.wrapped_playlist_2030_v2
        else -> null
    }

    /**
     * The Wrapped card is only offered during the year-end period:
     * December of the Wrapped year through January of the following year.
     */
    val IS_IN_YEAR_END_WINDOW: Boolean
        get() {
            val now = LocalDate.now()
            return now.monthValue == 12 || (now.monthValue == 1 && now.year == YEAR + 1)
        }
}
