/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.player

import com.nestmusic.music.models.MediaMetadata

internal fun matchesQueueSearch(
    metadata: MediaMetadata,
    query: String,
): Boolean {
    val normalizedQuery = query.trim()
    if (normalizedQuery.isEmpty()) return true

    return metadata.title.contains(normalizedQuery, ignoreCase = true) ||
        metadata.artists.any { it.name.contains(normalizedQuery, ignoreCase = true) }
}

internal fun buildQueueShareText(
    queueTitle: String?,
    tracks: List<MediaMetadata>,
): String {
    val trackList = tracks.mapIndexed { index, track ->
        buildString {
            append(index + 1).append(". ").append(track.title)

            val artists = track.artists
                .asSequence()
                .map { it.name.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .joinToString(", ")
            if (artists.isNotEmpty()) append(" — ").append(artists)

            if (track.id.isNotBlank()) {
                append("\nhttps://music.youtube.com/watch?v=").append(track.id)
            }
        }
    }

    val body = trackList.joinToString("\n\n")
    val title = queueTitle?.trim()?.takeIf { it.isNotEmpty() }
    return when {
        title == null -> body
        body.isEmpty() -> title
        else -> "$title\n\n$body"
    }
}
