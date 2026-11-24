package com.nestmusic.music.ui.player.nest.canvas

import org.json.JSONObject
import java.net.URI
import java.net.URLEncoder
import java.util.Locale

/**
 * Resolves official Apple Music editorial motion album covers (tall 3:4 full-screen and 1:1 square
 * HLS streams) using iTunes Search API + Apple Music storefront metadata.
 */
internal object NestAppleMusicCanvas {
    private val M3U8_REGEX =
        Regex("""https://mvod\.itunes\.apple\.com/itunes-assets/[^\s"'<>\\]+\.m3u8[^\s"'<>\\]*""")

    private val TALL_BLOCK_REGEX =
        Regex(""""motionDetailTall"\s*:\s*\{[^{}]*"video"\s*:\s*"([^"]+\.m3u8[^"]*)"""")

    private val SQUARE_BLOCK_REGEX =
        Regex(""""(?:motionDetailSquare|motionSquareVideo1x1)"\s*:\s*\{[^{}]*"video"\s*:\s*"([^"]+\.m3u8[^"]*)"""")

    suspend fun resolve(
        title: String,
        artist: String,
        album: String?,
        preferTall: Boolean = true,
    ): NestCanvasArtwork? {
        val cleanArtist = sanitizeArtist(artist)
        val cleanAlbum = album?.let(::sanitizeTitle)?.takeIf { it.isNotBlank() }
        val cleanTitle = sanitizeTitle(title)

        val candidates = buildList {
            if (cleanAlbum != null) {
                add("$cleanArtist $cleanAlbum" to "album")
            }
            if (cleanTitle.isNotBlank()) {
                add("$cleanArtist $cleanTitle" to "song")
            }
        }

        for ((term, entity) in candidates) {
            val collectionUrl = lookupAppleAlbumUrl(term = term, entity = entity, expectedArtist = cleanArtist)
                ?: continue
            val artwork = extractEditorialMotionFromAlbumPage(collectionUrl, preferTall = preferTall)
            if (artwork != null) return artwork
        }
        return null
    }

    private suspend fun lookupAppleAlbumUrl(
        term: String,
        entity: String,
        expectedArtist: String,
    ): String? {
        val encoded = URLEncoder.encode(term.trim(), "UTF-8")
        val searchUrl =
            "https://itunes.apple.com/search?term=$encoded&media=music&entity=$entity&limit=6&country=US"
        val raw = NestCanvasHttp.getText(searchUrl) ?: return null
        return runCatching {
            val results = JSONObject(raw).optJSONArray("results") ?: return@runCatching null
            val expectedNorm = normalizeCompare(expectedArtist)
            var fallbackUrl: String? = null
            for (i in 0 until results.length()) {
                val item = results.optJSONObject(i) ?: continue
                val artistName = item.optString("artistName")
                val rawUrl =
                    item.optString("collectionViewUrl").ifBlank {
                        item.optString("trackViewUrl")
                    }
                if (rawUrl.isBlank()) continue
                val pageUrl = rawUrl.substringBefore('?').replace("itunes.apple.com", "music.apple.com")
                if (fallbackUrl == null) fallbackUrl = pageUrl
                val normArtist = normalizeCompare(artistName)
                if (expectedNorm.isNotEmpty() &&
                    (normArtist.contains(expectedNorm) || expectedNorm.contains(normArtist))
                ) {
                    return@runCatching pageUrl
                }
            }
            fallbackUrl
        }.getOrNull()
    }

    private suspend fun extractEditorialMotionFromAlbumPage(
        albumUrl: String,
        preferTall: Boolean,
    ): NestCanvasArtwork? {
        val html = NestCanvasHttp.getText(albumUrl) ?: return null
        val unescaped = html.replace("\\/", "/").replace("\\u0026", "&")

        val tallMaster = TALL_BLOCK_REGEX.find(unescaped)?.groupValues?.getOrNull(1)
        val squareMaster = SQUARE_BLOCK_REGEX.find(unescaped)?.groupValues?.getOrNull(1)
        val genericMaster = M3U8_REGEX.find(unescaped)?.value

        val chosenMaster: Pair<String, Boolean> = when {
            preferTall && !tallMaster.isNullOrBlank() -> tallMaster to true
            !squareMaster.isNullOrBlank() -> squareMaster to false
            !tallMaster.isNullOrBlank() -> tallMaster to true
            !genericMaster.isNullOrBlank() -> genericMaster to false
            else -> return null
        }

        val bestStream = pickBestVariantUrl(chosenMaster.first) ?: chosenMaster.first
        return NestCanvasArtwork(
            url = bestStream,
            isHls = bestStream.substringBefore('?').endsWith(".m3u8", ignoreCase = true),
            isTall = chosenMaster.second,
            source = NestCanvasSource.APPLE_MUSIC,
        )
    }

    /**
     * Parses the HLS master playlist so ExoPlayer locks immediately onto a crisp high-res AVC/HEVC
     * stream (up to 1080p/2160p) instead of starting blurry on the lowest bitrate rung.
     */
    private suspend fun pickBestVariantUrl(masterUrl: String): String? {
        val playlist = NestCanvasHttp.getText(masterUrl) ?: return null
        if (!playlist.contains("#EXT-X-STREAM-INF")) return masterUrl

        data class Variant(
            val url: String,
            val bandwidth: Long,
            val width: Int,
            val isAvc: Boolean,
        )

        val lines = playlist.lines()
        val variants = mutableListOf<Variant>()
        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                val bw = Regex("""BANDWIDTH=(\d+)""").find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                val resMatch = Regex("""RESOLUTION=(\d+)x(\d+)""").find(line)
                val width = resMatch?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 0
                val codecs = Regex("""CODECS="([^"]+)"""").find(line)?.groupValues?.getOrNull(1).orEmpty()
                val isAvc = codecs.contains("avc1", ignoreCase = true)
                var j = i + 1
                while (j < lines.size && (lines[j].isBlank() || lines[j].trim().startsWith("#"))) {
                    j++
                }
                if (j < lines.size) {
                    val resolved = resolveRelativeUrl(masterUrl, lines[j].trim())
                    variants += Variant(
                        url = resolved,
                        bandwidth = bw,
                        width = width,
                        isAvc = isAvc,
                    )
                }
                i = j
            }
            i++
        }

        if (variants.isEmpty()) return masterUrl

        // Prefer AVC (universally hardware-decoded without SDR/PQ tone-map glitches) up to 1440px,
        // otherwise fall back to highest resolution <= 2160px.
        val avcPool = variants.filter { it.isAvc && it.width in 720..1440 }
        val pool =
            when {
                avcPool.isNotEmpty() -> avcPool
                variants.any { it.isAvc } -> variants.filter { it.isAvc }
                else -> variants.filter { it.width <= 2160 }.ifEmpty { variants }
            }
        return pool.maxWithOrNull(compareBy<Variant> { it.width }.thenBy { it.bandwidth })?.url ?: masterUrl
    }

    private fun resolveRelativeUrl(baseUrl: String, ref: String): String =
        if (ref.startsWith("http://") || ref.startsWith("https://")) {
            ref
        } else {
            runCatching { URI(baseUrl).resolve(ref).toString() }.getOrDefault(ref)
        }

    private fun sanitizeTitle(raw: String): String =
        raw
            .replace(Regex("""\s*[\(\[][^)\]]*(?:official|video|audio|lyrics|visualizer|remaster|deluxe|edition|feat\.|ft\.)[^)\]]*[\)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+(?:Single|EP)$""", RegexOption.IGNORE_CASE), "")
            .trim()

    private fun sanitizeArtist(raw: String): String =
        raw
            .split(',', '&', ';', '/')
            .firstOrNull()
            .orEmpty()
            .replace(Regex("""\s+(?:feat\.|ft\.).*$""", RegexOption.IGNORE_CASE), "")
            .trim()

    private fun normalizeCompare(s: String): String =
        s.lowercase(Locale.ROOT).filter { it.isLetterOrDigit() }
}
