package com.nestmusic.music.lossless.qobuz

import com.nestmusic.music.lossless.model.TrackQuery
import kotlin.math.abs

/**
 * Scores how well a catalog candidate matches a TrackQuery.
 * Implements ISRC fast-path, Jaccard similarity on normalized tokens,
 * artist subset-coverage handling for stylized names, and duration penalty.
 */
object QobuzCandidateMatcher {

    const val MIN_CONFIDENCE = 0.5f

    fun confidence(
        query: TrackQuery,
        candTitle: String,
        candArtist: String,
        candIsrc: String?,
        candDurationSec: Int,
        candStreamable: Boolean,
    ): Float {
        if (!candStreamable) return 0f

        val qIsrc = query.isrc?.takeIf { it.isNotBlank() }
        val cIsrc = candIsrc?.takeIf { it.isNotBlank() }
        if (qIsrc != null && cIsrc != null && qIsrc.equals(cIsrc, ignoreCase = true)) {
            return 0.95f
        }

        val titleSim = jaccard(normalize(query.title), normalize(candTitle))
        val artistSim = artistSimilarity(normalize(query.artist), normalize(candArtist))

        val durationFactor: Float = run {
            val qMs = query.durationMs ?: return@run 1.0f
            if (qMs <= 0 || candDurationSec <= 0) return@run 1.0f
            val cMs = candDurationSec * 1000L
            val drift = abs(qMs - cMs).toDouble() / qMs.toDouble()
            when {
                drift < 0.05 -> 1.0f
                drift < 0.10 -> 0.85f
                drift < 0.20 -> 0.6f
                else -> 0.3f
            }
        }

        return titleSim * artistSim * durationFactor
    }

    fun normalize(s: String): String =
        s.lowercase()
            .replace(Regex("\\([^)]*\\)"), " ")
            .replace(Regex("\\[[^]]*\\]"), " ")
            .replace(Regex("(?i)\\b(feat\\.?|ft\\.?|featuring)\\b.*"), " ")
            .replace(Regex("[''`]"), "")
            .replace(Regex("[^\\p{L}\\p{N}\\p{S}\\s]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()

    fun jaccard(a: String, b: String): Float {
        val setA = a.split(" ").filter { it.isNotEmpty() }.toSet()
        val setB = b.split(" ").filter { it.isNotEmpty() }.toSet()
        if (setA.isEmpty() || setB.isEmpty()) return 0f
        val inter = setA.intersect(setB).size.toFloat()
        val union = setA.union(setB).size.toFloat()
        return inter / union
    }

    fun artistSimilarity(a: String, b: String): Float {
        val setA = a.split(" ").filter { it.isNotEmpty() }.toSet()
        val setB = b.split(" ").filter { it.isNotEmpty() }.toSet()
        if (setA.isEmpty() || setB.isEmpty()) return 0f

        val inter = setA.intersect(setB)
        val union = setA.union(setB)
        val jaccardScore = inter.size.toFloat() / union.size.toFloat()

        val smallerSize = minOf(setA.size, setB.size)
        val fullyCovered = inter.size == smallerSize
        val hasDistinctive = inter.any { token ->
            token.length > 3 || token.any { ch -> !ch.isLetterOrDigit() }
        }
        val coverageScore = if (fullyCovered && hasDistinctive) 1.0f else 0f

        return maxOf(jaccardScore, coverageScore)
    }
}
