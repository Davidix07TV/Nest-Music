package com.nestmusic.music.ui.player.nest.canvas

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/**
 * Fallback animated cover resolver querying public Tidal metadata mirrors for 1280x1280 MP4
 * `videoCover` loops.
 */
internal object NestTidalCanvas {
    private val INSTANCES = listOf(
        "https://api.monochrome.tf",
        "https://triton.squid.wtf",
        "https://wolf.qqdl.site",
    )

    suspend fun resolve(
        title: String,
        artist: String,
    ): NestCanvasArtwork? {
        val cleanArtist = artist.split(',', '&', ';').firstOrNull().orEmpty().trim()
        val query = URLEncoder.encode("$cleanArtist $title".trim(), "UTF-8")
        val expectedArtist = cleanArtist.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }

        for (base in INSTANCES) {
            val raw = NestCanvasHttp.getText("$base/search/?s=$query") ?: continue
            val videoId = parseVideoCoverUuid(raw, expectedArtist) ?: continue
            val path = videoId.replace('-', '/')
            return NestCanvasArtwork(
                url = "https://resources.tidal.com/videos/$path/1280x1280.mp4",
                isHls = false,
                isTall = false,
                source = NestCanvasSource.TIDAL,
            )
        }
        return null
    }

    private fun parseVideoCoverUuid(
        jsonText: String,
        expectedArtist: String,
    ): String? =
        runCatching {
            val root = JSONObject(jsonText)
            val data = root.optJSONObject("data") ?: root
            val items: JSONArray =
                data.optJSONArray("items")
                    ?: data.optJSONObject("tracks")?.optJSONArray("items")
                    ?: return@runCatching null

            var fallbackUuid: String? = null
            for (i in 0 until items.length()) {
                val track = items.optJSONObject(i) ?: continue
                val album = track.optJSONObject("album")
                val videoCover =
                    album?.optString("videoCover")?.takeIf { it.isNotBlank() && it != "null" }
                        ?: track.optString("videoCover").takeIf { it.isNotBlank() && it != "null" }
                        ?: continue
                if (fallbackUuid == null) fallbackUuid = videoCover
                val trackArtist =
                    track.optJSONObject("artist")?.optString("name").orEmpty()
                        .lowercase(Locale.ROOT)
                        .filter { it.isLetterOrDigit() }
                if (expectedArtist.isNotEmpty() &&
                    (trackArtist.contains(expectedArtist) || expectedArtist.contains(trackArtist))
                ) {
                    return@runCatching videoCover
                }
            }
            fallbackUuid
        }.getOrNull()
}
