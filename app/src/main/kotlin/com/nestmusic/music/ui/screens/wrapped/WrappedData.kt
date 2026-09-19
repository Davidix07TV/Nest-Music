/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.screens.wrapped

import com.nestmusic.music.R

/**
 * A tease/reveal pair for the minutes page, held as string resources so the
 * messages can be translated. The reveal carries the minutes placeholder.
 */
data class MessagePair(val range: LongRange, val teaseRes: Int, val revealRes: Int)

object WrappedRepository {
    private val messages = listOf(
        MessagePair(0L..999L, R.string.wrapped_message_tease_1, R.string.wrapped_message_reveal_1),
        MessagePair(0L..999L, R.string.wrapped_message_tease_2, R.string.wrapped_message_reveal_2),
        MessagePair(0L..999L, R.string.wrapped_message_tease_3, R.string.wrapped_message_reveal_3),
        MessagePair(0L..999L, R.string.wrapped_message_tease_4, R.string.wrapped_message_reveal_4),

        MessagePair(1000L..4999L, R.string.wrapped_message_tease_5, R.string.wrapped_message_reveal_5),
        MessagePair(1000L..4999L, R.string.wrapped_message_tease_6, R.string.wrapped_message_reveal_6),
        MessagePair(1000L..4999L, R.string.wrapped_message_tease_7, R.string.wrapped_message_reveal_7),
        MessagePair(1000L..4999L, R.string.wrapped_message_tease_8, R.string.wrapped_message_reveal_8),

        MessagePair(5000L..14999L, R.string.wrapped_message_tease_9, R.string.wrapped_message_reveal_9),
        MessagePair(5000L..14999L, R.string.wrapped_message_tease_10, R.string.wrapped_message_reveal_10),
        MessagePair(5000L..14999L, R.string.wrapped_message_tease_11, R.string.wrapped_message_reveal_11),
        MessagePair(5000L..14999L, R.string.wrapped_message_tease_12, R.string.wrapped_message_reveal_12),

        MessagePair(15000L..39999L, R.string.wrapped_message_tease_13, R.string.wrapped_message_reveal_13),
        MessagePair(15000L..39999L, R.string.wrapped_message_tease_14, R.string.wrapped_message_reveal_14),
        MessagePair(15000L..39999L, R.string.wrapped_message_tease_15, R.string.wrapped_message_reveal_15),
        MessagePair(15000L..39999L, R.string.wrapped_message_tease_16, R.string.wrapped_message_reveal_16),

        MessagePair(40000L..Long.MAX_VALUE, R.string.wrapped_message_tease_17, R.string.wrapped_message_reveal_17),
        MessagePair(40000L..Long.MAX_VALUE, R.string.wrapped_message_tease_18, R.string.wrapped_message_reveal_18),
        MessagePair(40000L..Long.MAX_VALUE, R.string.wrapped_message_tease_19, R.string.wrapped_message_reveal_19),
        MessagePair(40000L..Long.MAX_VALUE, R.string.wrapped_message_tease_20, R.string.wrapped_message_reveal_20),
    )

    private val fallback =
        MessagePair(
            0L..Long.MAX_VALUE,
            R.string.wrapped_message_tease_fallback,
            R.string.wrapped_message_reveal_fallback,
        )

    fun getMessage(minutes: Long): MessagePair {
        val possibleMessages = messages.filter { minutes in it.range }
        // Fallback for safety
        return possibleMessages.randomOrNull() ?: fallback
    }
}
