package com.nestmusic.music.lossless.download

import com.nestmusic.music.db.MusicDatabase
import com.nestmusic.music.lossless.FlacCoreLosslessStreamResolver
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface FlacDownloaderEntryPoint {
    fun database(): MusicDatabase
    fun losslessResolver(): FlacCoreLosslessStreamResolver
}
