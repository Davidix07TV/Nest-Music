package com.nestmusic.music.lossless

import com.nestmusic.music.constants.FlacQuality
import com.nestmusic.music.db.entities.Song
import com.nestmusic.music.lossless.model.FlacStreamUrl

interface LosslessStreamResolver {
    suspend fun resolve(song: Song, quality: FlacQuality): FlacStreamUrl?
}
