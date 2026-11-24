package com.nestmusic.music.ui.player.nest.canvas

import androidx.compose.runtime.Immutable

enum class NestCanvasSource {
    APPLE_MUSIC,
    TIDAL,
    COMMUNITY,
}

@Immutable
data class NestCanvasArtwork(
    val url: String,
    val isHls: Boolean = url.substringBefore('?').endsWith(".m3u8", ignoreCase = true),
    val isTall: Boolean = false,
    val source: NestCanvasSource = NestCanvasSource.APPLE_MUSIC,
)
