package com.nestmusic.music.ui.player.nest.canvas

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Resolves animated motion covers (Apple Music HLS tall/square editorial motion + Tidal 1280p loop
 * fallback) with an in-memory LRU cache so revisited tracks resolve instantaneously.
 */
object NestCanvasRepository {
    private sealed interface CacheEntry {
        data class Hit(val artwork: NestCanvasArtwork) : CacheEntry
        data object Miss : CacheEntry
    }

    private val memoryCache = LruCache<String, CacheEntry>(128)

    suspend fun resolve(
        context: Context,
        mediaId: String,
        title: String,
        artist: String,
        album: String?,
        preferTall: Boolean = true,
        allowCellular: Boolean = true,
    ): NestCanvasArtwork? {
        if (title.isBlank() && artist.isBlank()) return null

        val cacheKey = "$mediaId|${if (preferTall) "tall" else "sq"}"
        synchronized(memoryCache) { memoryCache.get(cacheKey) }?.let { cached ->
            return when (cached) {
                is CacheEntry.Hit -> cached.artwork
                CacheEntry.Miss -> null
            }
        }

        if (!isNetworkAllowed(context, allowCellular)) return null

        val resolved =
            withContext(Dispatchers.IO) {
                NestAppleMusicCanvas.resolve(
                    title = title,
                    artist = artist,
                    album = album,
                    preferTall = preferTall,
                ) ?: NestTidalCanvas.resolve(
                    title = title,
                    artist = artist,
                )
            }

        synchronized(memoryCache) {
            memoryCache.put(
                cacheKey,
                if (resolved != null) CacheEntry.Hit(resolved) else CacheEntry.Miss,
            )
        }
        return resolved
    }

    private fun isNetworkAllowed(
        context: Context,
        allowCellular: Boolean,
    ): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return true
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) return false
        if (allowCellular) return true
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) ||
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
    }
}
