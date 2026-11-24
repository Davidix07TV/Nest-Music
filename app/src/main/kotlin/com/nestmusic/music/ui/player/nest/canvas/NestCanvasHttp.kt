package com.nestmusic.music.ui.player.nest.canvas

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

internal object NestCanvasHttp {
    private const val BROWSER_UA =
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) " +
            "AppleWebKit/605.1.15 (KHTML, like Gecko) Version/18.1 Safari/605.1.15"

    val client: OkHttpClient by lazy {
        OkHttpClient
            .Builder()
            .connectTimeout(7, TimeUnit.SECONDS)
            .readTimeout(9, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }

    suspend fun getText(
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): String? =
        withContext(Dispatchers.IO) {
            runCatching {
                val reqBuilder =
                    Request
                        .Builder()
                        .url(url)
                        .header("User-Agent", BROWSER_UA)
                        .header("Accept-Language", "en-US,en;q=0.9")
                headers.forEach { (k, v) -> reqBuilder.header(k, v) }
                client.newCall(reqBuilder.build()).execute().use { resp ->
                    if (!resp.isSuccessful) return@use null
                    resp.body?.string()
                }
            }.getOrNull()
        }
}
