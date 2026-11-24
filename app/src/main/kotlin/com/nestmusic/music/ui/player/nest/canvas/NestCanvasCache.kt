package com.nestmusic.music.ui.player.nest.canvas

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
object NestCanvasCache {
    private const val MAX_BYTES = 100L * 1024L * 1024L // 100 MB LRU disk cache
    private const val DIR_NAME = "nest_canvas_cache"

    @Volatile
    private var simpleCache: SimpleCache? = null

    private fun ensureCache(context: Context): SimpleCache {
        simpleCache?.let { return it }
        return synchronized(this) {
            simpleCache ?: run {
                val appContext = context.applicationContext
                val dir = File(appContext.cacheDir, DIR_NAME).apply { mkdirs() }
                SimpleCache(
                    dir,
                    LeastRecentlyUsedCacheEvictor(MAX_BYTES),
                    StandaloneDatabaseProvider(appContext),
                ).also { simpleCache = it }
            }
        }
    }

    fun dataSourceFactory(context: Context): DataSource.Factory {
        val upstream =
            DefaultHttpDataSource
                .Factory()
                .setUserAgent(
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
                        "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15",
                ).setConnectTimeoutMs(8_000)
                .setReadTimeoutMs(10_000)
                .setAllowCrossProtocolRedirects(true)

        return runCatching {
            val cache = ensureCache(context)
            CacheDataSource
                .Factory()
                .setCache(cache)
                .setUpstreamDataSourceFactory(upstream)
                .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
        }.getOrElse { upstream }
    }
}
