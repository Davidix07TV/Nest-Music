/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.screens.wrapped

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
     * The Wrapped card is only offered during the year-end period:
     * December of the Wrapped year through January of the following year.
     */
    val IS_IN_YEAR_END_WINDOW: Boolean
        get() {
            val now = LocalDate.now()
            return now.monthValue == 12 || (now.monthValue == 1 && now.year == YEAR + 1)
        }
}
