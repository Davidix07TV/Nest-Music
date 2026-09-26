package com.nestmusic.music.lossless

import com.nestmusic.music.constants.FlacQuality
import com.nestmusic.music.db.entities.Song
import com.nestmusic.music.lossless.model.FlacStreamUrl
import com.nestmusic.music.lossless.model.TrackQuery
import com.nestmusic.music.lossless.streaming.FlacStreamRegistry
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FlacCoreLosslessStreamResolver @Inject constructor(
    private val registry: FlacStreamRegistry
) : LosslessStreamResolver {
    override suspend fun resolve(song: Song, quality: FlacQuality): FlacStreamUrl? {
        val artistName = song.artists.mapNotNull { it.name.takeIf { it.isNotBlank() } }.joinToString(", ")
        val query = TrackQuery(
            artist = artistName.ifBlank { "Unknown Artist" },
            title = song.title,
            album = song.album?.title,
            durationMs = song.song.duration.takeIf { it > 0 }?.let { it * 1000L },
            explicit = song.song.explicit
        )
        return registry.resolve(query, quality.streamQuality)
    }
}
