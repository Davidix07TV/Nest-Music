/*
 * Adapted verbatim from Nest Music (Nest Music) LyricLine.kt under GPL-3.0,
 * with a converter from Nest Music's LyricsEntry / WordTimestamp model.
 */

package com.nestmusic.music.ui.player.nest

import com.nestmusic.music.lyrics.LyricsEntry
import com.nestmusic.music.lyrics.WordTimestamp

enum class LyricAlignment { Start, End }

data class LyricSyllable(
    val startMs: Long,
    val endMs: Long,
    val startChar: Int,
    val endChar: Int,
) {
    companion object {
        fun normalized(parts: List<LyricSyllable>, length: Int): List<LyricSyllable> {
            val usable = parts
                .filter { it.endChar > it.startChar && it.startChar < length }
                .sortedBy { it.startChar }
            if (usable.size < 2) return emptyList()
            return usable.mapIndexed { index, part ->
                LyricSyllable(
                    startMs = part.startMs,
                    endMs = maxOf(part.startMs, part.endMs),
                    startChar = if (index == 0) 0 else part.startChar,
                    endChar = usable.getOrNull(index + 1)?.startChar ?: length,
                )
            }
        }
    }
}

data class LyricWord(
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val syllables: List<LyricSyllable> = emptyList(),
) {
    fun charsSungAt(positionMs: Long): Float {
        if (positionMs <= startMs) return 0f
        if (positionMs >= endMs) return text.length.toFloat()
        if (syllables.isEmpty()) {
            val span = (endMs - startMs).coerceAtLeast(1L)
            return (positionMs - startMs).toFloat() / span * text.length
        }
        for (syllable in syllables) {
            if (positionMs < syllable.startMs) return syllable.startChar.toFloat()
            if (positionMs < syllable.endMs) {
                val span = (syllable.endMs - syllable.startMs).coerceAtLeast(1L)
                val through = (positionMs - syllable.startMs).toFloat() / span
                return syllable.startChar + through * (syllable.endChar - syllable.startChar)
            }
        }
        return text.length.toFloat()
    }

    fun withoutChars(drop: (Char) -> Boolean): LyricWord? {
        if (text.none(drop)) return this
        val moved = IntArray(text.length + 1)
        val kept = StringBuilder()
        text.forEachIndexed { index, char ->
            moved[index] = kept.length
            if (!drop(char)) kept.append(char)
        }
        moved[text.length] = kept.length
        val lead = kept.indexOfFirst { !it.isWhitespace() }.takeIf { it >= 0 } ?: return null
        val trimmed = kept.toString().trim()
        val parts = syllables.map {
            LyricSyllable(
                startMs = it.startMs,
                endMs = it.endMs,
                startChar = (moved[it.startChar] - lead).coerceAtLeast(0),
                endChar = (moved[it.endChar] - lead).coerceAtMost(trimmed.length),
            )
        }
        return copy(text = trimmed, syllables = LyricSyllable.normalized(parts, trimmed.length))
    }
}

data class NestLyricLine(
    val timeMs: Long,
    val text: String,
    val words: List<LyricWord> = emptyList(),
    val sungUntilMs: Long? = null,
    val background: NestLyricLine? = null,
    val alignment: LyricAlignment = LyricAlignment.Start,
    val timingSource: NestLyricLine? = null,
    val sourceEntryIndex: Int = -1,
) {
    val isGap: Boolean get() = text.isEmpty()
    val isWordSynced: Boolean get() = words.isNotEmpty() || timingSource?.isWordSynced == true

    val wordSpans: List<IntRange> by lazy(LazyThreadSafetyMode.NONE) {
        var offset = 0
        words.map { word ->
            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            offset = end
            start until end
        }
    }

    val sweepSpans: List<IntRange> by lazy(LazyThreadSafetyMode.NONE) {
        words.flatMapIndexed { index, word ->
            val span = wordSpans[index]
            if (word.syllables.isEmpty()) {
                listOf(span)
            } else {
                word.syllables.map { (span.first + it.startChar) until (span.first + it.endChar) }
            }
        }
    }

    fun isLifted(positionMs: Long): Boolean {
        val first = words.firstOrNull() ?: return false
        if (positionMs <= first.startMs) return false
        return positionMs < words.last().endMs + RISE_MS || isGrowing(positionMs)
    }

    val animatesFromMs: Long by lazy(LazyThreadSafetyMode.NONE) {
        val src = timingSource
        if (src != null) src.animatesFromMs else minOf(timeMs, words.firstOrNull()?.startMs ?: timeMs)
    }

    val animatesUntilMs: Long by lazy(LazyThreadSafetyMode.NONE) {
        val src = timingSource
        if (src != null) {
            src.animatesUntilMs
        } else {
            val settled = words.lastOrNull()?.let { it.endMs + RISE_MS.toLong() } ?: endMs
            val grown = growingWords.maxOfOrNull { it.restsAtMs } ?: Long.MIN_VALUE
            maxOf(endMs, settled, grown)
        }
    }

    fun wordLift(index: Int, positionMs: Long): Float {
        val word = words.getOrNull(index) ?: return 0f
        val riseMs = (word.endMs - word.startMs).toFloat().coerceIn(RISE_MIN_MS, RISE_MS)
        val rising = ((positionMs - word.startMs) / riseMs).coerceIn(0f, 1f)
        val falling = (1f - (positionMs - word.endMs) / RISE_MS).coerceIn(0f, 1f)
        return liftHeight(word) * smooth(minOf(rising, falling))
    }

    val hasKnownEnd: Boolean get() = words.isNotEmpty() || sungUntilMs != null

    val endMs: Long
        get() {
            val src = timingSource
            if (src != null) return src.endMs
            val lead = words.lastOrNull()?.endMs ?: sungUntilMs ?: timeMs
            return maxOf(lead, background?.endMs ?: lead)
        }

    fun revealedChars(positionMs: Long): Float {
        timingSource?.let { source ->
            if (source.text.isEmpty()) return 0f
            return (source.revealedChars(positionMs) / source.text.length)
                .coerceIn(0f, 1f) * text.length
        }
        if (words.isEmpty()) return if (positionMs >= timeMs) text.length.toFloat() else 0f
        var offset = 0
        words.forEachIndexed { index, word ->
            val start = text.indexOf(word.text, offset).takeIf { it >= 0 } ?: offset
            val end = start + word.text.length
            if (positionMs < word.startMs) return start.toFloat()
            if (positionMs < word.endMs) return start + word.charsSungAt(positionMs)
            val next = words.getOrNull(index + 1)
            if (next != null && positionMs < next.startMs) {
                val gapStart = text.indexOf(next.text, end).takeIf { it >= 0 } ?: end
                val pause = (next.startMs - word.endMs).coerceAtLeast(1L)
                val through = (positionMs - word.endMs).toFloat() / pause
                return end + through * (gapStart - end)
            }
            offset = end
        }
        return text.length.toFloat()
    }

    fun wordFall(index: Int, positionMs: Long): Float {
        val word = words.getOrNull(index) ?: return 0f
        val falling = (1f - (positionMs - word.endMs) / RISE_MS).coerceIn(0f, 1f)
        return liftHeight(word) * smooth(falling)
    }

    val growingWords: List<GrowingWord> by lazy(LazyThreadSafetyMode.NONE) {
        words.mapIndexedNotNull { index, word ->
            if (word.canGrow()) GrowingWord(index, word) else null
        }
    }

    fun growingAt(index: Int): GrowingWord? =
        growingWords.firstOrNull { it.index == index }

    fun isGrowing(positionMs: Long): Boolean = growingWords.any {
        positionMs >= it.startMs && positionMs <= it.restsAtMs
    }

    fun withoutBracketPunctuation(): NestLyricLine = copy(
        text = text.replace("(", "").replace(")", "").trim(),
        words = words.mapNotNull { word -> word.withoutChars { it == '(' || it == ')' } },
    )
}

class GrowingWord internal constructor(
    val index: Int,
    word: LyricWord,
) {
    val startMs: Long = word.startMs
    val endMs: Long = word.endMs

    private val chars: Int = word.text.length
    private val scalePeak = FloatArray(chars)
    private val shiftPeak = FloatArray(chars)
    private val risePeak = FloatArray(chars)
    private val bloomPeak = FloatArray(chars)
    val restsAtMs: Long

    init {
        val held = (endMs - startMs).coerceAtLeast(1L).toFloat()
        val earned = ((held - GROW_RAMP_MIN_MS) / (GROW_RAMP_MAX_MS - GROW_RAMP_MIN_MS))
            .coerceIn(0f, 1f)
            .let { it * it * it }
        val decay = decayRate(chars, held)
        val bloomPace = minOf(GROW_BLOOM_PACE_MAX, held / GROW_BLOOM_PACE_MS)
        val bloomSpread = when {
            chars <= 3 -> GROW_BLOOM_SHORT
            chars >= 6 -> GROW_BLOOM_LONG
            else -> 1f
        }
        val base = if (chars <= 3) GROW_BASE_SHORT else GROW_BASE_LONG
        val liftPace = (held / GROW_LIFT_PACE_MS).coerceIn(GROW_LIFT_FLOOR, 1f)

        for (i in 0 until chars) {
            val place = if (chars > 1) i.toFloat() / (chars - 1) else 0f
            val reach = earned * (1f - place * decay)
            val scale = 1f + base + reach * GROW_SCALE_RANGE
            scalePeak[i] = scale * GROW_SCALE_TRIM
            bloomPeak[i] = (GROW_BLOOM_FLOOR + reach * GROW_BLOOM_RANGE) * bloomPace * bloomSpread
            risePeak[i] = ((scale - 1f) / GROW_SCALE_CEILING) * liftPace
            val centre = (i + 0.5f) / chars
            shiftPeak[i] = (centre - 0.5f) * 2f * (scale - 1f) * GROW_SHIFT_EM * GROW_SCALE_TRIM
        }

        val last = (chars - 1).coerceAtLeast(0) * GROW_STAGGER + GROW_SPAN
        restsAtMs = startMs + (held * last).toLong()
    }

    fun sampleInto(charIndex: Int, positionMs: Long, into: CharGrowth) {
        if (charIndex !in 0 until chars) {
            into.scale = 1f; into.shift = 0f; into.rise = 0f; into.bloom = 0f
            return
        }
        val span = (endMs - startMs).coerceAtLeast(1L).toFloat()
        val elapsed = positionMs - startMs - charIndex * span * GROW_STAGGER
        val phase = (elapsed / (span * GROW_SPAN)).coerceIn(0f, 1f)
        val peakScale = scalePeak[charIndex]
        when {
            phase < GROW_IN -> {
                val t = smooth(phase / GROW_IN)
                into.scale = 1f + (peakScale - 1f) * t
                into.shift = shiftPeak[charIndex] * t
                into.rise = risePeak[charIndex] * t
                into.bloom = bloomPeak[charIndex] * t
            }
            phase < GROW_HOLD -> {
                into.scale = peakScale
                into.shift = shiftPeak[charIndex]
                into.rise = risePeak[charIndex]
                into.bloom = bloomPeak[charIndex]
            }
            phase < GROW_OUT -> {
                val t = smooth((phase - GROW_HOLD) / (GROW_OUT - GROW_HOLD))
                into.scale = peakScale + (1f - peakScale) * t
                into.shift = shiftPeak[charIndex] * (1f - t)
                into.rise = risePeak[charIndex] + (GROW_REST - risePeak[charIndex]) * t
                into.bloom = bloomPeak[charIndex] * (1f - t)
            }
            else -> {
                into.scale = 1f
                into.shift = 0f
                into.rise = GROW_REST
                into.bloom = 0f
            }
        }
    }
}

class CharGrowth {
    var scale: Float = 1f
    var shift: Float = 0f
    var rise: Float = 0f
    var bloom: Float = 0f
}

private const val RISE_MS = 700f
private const val RISE_MIN_MS = 250f
private const val LIFT_QUICK_MS = 150f
private const val LIFT_HELD_MS = 900f
private const val LIFT_FLOOR = 0.25f

private fun liftHeight(word: LyricWord): Float {
    val held = (word.endMs - word.startMs).toFloat()
    val through = ((held - LIFT_QUICK_MS) / (LIFT_HELD_MS - LIFT_QUICK_MS)).coerceIn(0f, 1f)
    return LIFT_FLOOR + (1f - LIFT_FLOOR) * smooth(through)
}

private fun smooth(fraction: Float) = fraction * fraction * (3f - 2f * fraction)

private fun LyricWord.canGrow(): Boolean {
    val length = text.length
    if (length == 0 || length > GROW_MAX_CHARS) return false
    if ('-' in text || text.any { it.isBlockScript() || it.isJoinedScript() }) return false
    val held = endMs - startMs
    return when {
        length == 1 -> held >= GROW_MIN_SOLO_MS
        length <= 3 -> held >= GROW_MIN_SHORT_MS + (length - 2) * GROW_SHORT_STEP_MS
        length == 4 -> held >= GROW_MIN_FOUR_MS
        else -> held >= GROW_MIN_LONG_MS && held >= length * GROW_MS_PER_CHAR
    }
}

private fun decayRate(length: Int, heldMs: Float): Float {
    val long = length > GROW_DECAY_LONG_CHARS
    val quick = heldMs < GROW_DECAY_QUICK_MS
    if (!long && !quick) return 0f
    var strength = 0f
    if (long) {
        strength += minOf((length - GROW_DECAY_LONG_CHARS) / 5f, 1f) * GROW_DECAY_LONG
    }
    if (quick) {
        val short = maxOf(0f, 1f - (heldMs - GROW_DECAY_QUICK_FLOOR_MS) / 400f)
        strength += short * if (length > 3) GROW_DECAY_QUICK else GROW_DECAY_QUICK_TINY
    }
    return minOf(strength, GROW_DECAY_MAX)
}

private fun Char.isBlockScript(): Boolean =
    this in '\u4E00'..'\u9FFF' || this in '\u3040'..'\u309F' ||
        this in '\u30A0'..'\u30FF' || this in '\uAC00'..'\uD7AF'

private fun Char.isJoinedScript(): Boolean =
    this in '\u0590'..'\u08FF'

private const val GROW_MAX_CHARS = 7
private const val GROW_MIN_SOLO_MS = 1_100L
private const val GROW_MIN_SHORT_MS = 1_360L
private const val GROW_SHORT_STEP_MS = 140L
private const val GROW_MIN_FOUR_MS = 1_050L
private const val GROW_MIN_LONG_MS = 900L
private const val GROW_MS_PER_CHAR = 200L
private const val GROW_DECAY_LONG_CHARS = 5
private const val GROW_DECAY_QUICK_MS = 1_200f
private const val GROW_DECAY_QUICK_FLOOR_MS = 800f
private const val GROW_DECAY_LONG = 0.4f
private const val GROW_DECAY_QUICK = 0.3f
private const val GROW_DECAY_QUICK_TINY = 0.1f
private const val GROW_DECAY_MAX = 0.7f
private const val GROW_STAGGER = 0.09f
private const val GROW_SPAN = 1.5f
private const val GROW_IN = 0.25f
private const val GROW_HOLD = 0.30f
private const val GROW_OUT = 0.75f
private const val GROW_REST = 1f
private const val GROW_RAMP_MIN_MS = 400f
private const val GROW_RAMP_MAX_MS = 3_000f
private const val GROW_BASE_SHORT = 0.05f
private const val GROW_BASE_LONG = 0.04f
private const val GROW_SCALE_RANGE = 0.08f
private const val GROW_SCALE_CEILING = 0.1f
private const val GROW_SCALE_TRIM = 0.98f
private const val GROW_SHIFT_EM = 25f / 34f
private const val GROW_BLOOM_FLOOR = 0.35f
private const val GROW_BLOOM_RANGE = 0.45f
private const val GROW_BLOOM_PACE_MS = 1_500f
private const val GROW_BLOOM_PACE_MAX = 1.1f
private const val GROW_BLOOM_SHORT = 0.85f
private const val GROW_BLOOM_LONG = 1.1f
private const val GROW_LIFT_PACE_MS = 2_000f
private const val GROW_LIFT_FLOOR = 0.3f

private fun List<WordTimestamp>.toNestWords(): List<LyricWord> {
    if (isEmpty()) return emptyList()
    // Merge hyphenated syllable pieces (where !hasTrailingSpace) into a single LyricWord with LyricSyllables,
    // matching Nest Music's TTML syllable grouping so words lift whole while sweeping syllable-by-syllable.
    val result = ArrayList<LyricWord>(size)
    var i = 0
    while (i < size) {
        val first = this[i]
        if (first.hasTrailingSpace || i == lastIndex) {
            val trimmed = first.text.trim()
            if (trimmed.isNotEmpty()) {
                val s = (first.startTime * 1000.0).toLong()
                val e = maxOf(s + 1L, (first.endTime * 1000.0).toLong())
                result += LyricWord(startMs = s, endMs = e, text = trimmed)
            }
            i++
        } else {
            val wordBuilder = StringBuilder()
            val syllables = ArrayList<LyricSyllable>(4)
            var groupStartMs = (first.startTime * 1000.0).toLong()
            var groupEndMs = (first.endTime * 1000.0).toLong()
            var j = i
            while (j < size) {
                val piece = this[j]
                val clean = piece.text.trim()
                if (clean.isNotEmpty()) {
                    val startChar = wordBuilder.length
                    wordBuilder.append(clean)
                    val endChar = wordBuilder.length
                    val ps = (piece.startTime * 1000.0).toLong()
                    val pe = maxOf(ps + 1L, (piece.endTime * 1000.0).toLong())
                    groupStartMs = minOf(groupStartMs, ps)
                    groupEndMs = maxOf(groupEndMs, pe)
                    syllables += LyricSyllable(ps, pe, startChar, endChar)
                }
                val done = piece.hasTrailingSpace
                j++
                if (done) break
            }
            val fullWord = wordBuilder.toString()
            if (fullWord.isNotEmpty()) {
                result += LyricWord(
                    startMs = groupStartMs,
                    endMs = maxOf(groupStartMs + 1L, groupEndMs),
                    text = fullWord,
                    syllables = LyricSyllable.normalized(syllables, fullWord.length),
                )
            }
            i = j
        }
    }
    return result
}

/**
 * Converts Nest Music's [LyricsEntry] list into Nest Music's [NestLyricLine] list:
 * - Folds consecutive `isBackground` lines onto their preceding lead line as `background`
 * - Maps TTML duet `agent == "v2"` to [LyricAlignment.End]
 * - Inserts instrumental gap rows (`text = ""`) when >4.2s silence exists between timed lines
 */
internal fun List<LyricsEntry>.toNestLyricLines(
    isSynced: Boolean,
    showIntervalIndicators: Boolean = true,
    respectAgentPositioning: Boolean = true,
): List<NestLyricLine> {
    if (isEmpty()) return emptyList()
    val raw = ArrayList<NestLyricLine>(size)
    var index = 0
    while (index < size) {
        val entry = this[index]
        if (entry === LyricsEntry.HEAD_LYRICS_ENTRY) {
            // Check if there is an intro gap before the first real line
            val firstSung = this.getOrNull(index + 1)
            if (isSynced && showIntervalIndicators && firstSung != null && firstSung.time >= 4_200L) {
                raw += NestLyricLine(
                    timeMs = 0L,
                    text = "",
                    sungUntilMs = firstSung.time,
                    sourceEntryIndex = index,
                )
            }
            index++
            continue
        }
        if (entry.text.isBlank()) {
            if (isSynced && showIntervalIndicators) {
                val nextTime = this.getOrNull(index + 1)?.time ?: (entry.time + 4_000L)
                if (nextTime - entry.time >= 2_500L) {
                    raw += NestLyricLine(
                        timeMs = entry.time,
                        text = "",
                        sungUntilMs = nextTime,
                        sourceEntryIndex = index,
                    )
                }
            }
            index++
            continue
        }

        val alignment = if (respectAgentPositioning && entry.agent == "v2") {
            LyricAlignment.End
        } else {
            LyricAlignment.Start
        }

        val words = entry.words?.toNestWords().orEmpty()

        // Fold an immediately following background vocal entry onto this lead line (Apple Music style)
        var bgLine: NestLyricLine? = null
        var consumedNext = false
        val next = this.getOrNull(index + 1)
        if (!entry.isBackground && next != null && next.isBackground && next.text.isNotBlank()) {
            val bgWords = next.words?.toNestWords().orEmpty()
            bgLine = NestLyricLine(
                timeMs = next.time,
                text = next.text.trim(),
                words = bgWords,
                alignment = alignment,
                sourceEntryIndex = index + 1,
            )
            consumedNext = true
        }

        val leadLine = NestLyricLine(
            timeMs = if (isSynced) entry.time else 0L,
            text = entry.text.trim(),
            words = if (isSynced) words else emptyList(),
            background = bgLine,
            alignment = alignment,
            sourceEntryIndex = index,
        )
        raw += leadLine

        val stepAfter = if (consumedNext) index + 2 else index + 1
        val following = this.getOrNull(stepAfter)
        if (isSynced && showIntervalIndicators && following != null && leadLine.hasKnownEnd) {
            val leadEnd = leadLine.endMs
            if (following.time - leadEnd >= 4_200L) {
                raw += NestLyricLine(
                    timeMs = leadEnd,
                    text = "",
                    sungUntilMs = following.time,
                    sourceEntryIndex = -1,
                )
            }
        }
        index = stepAfter
    }
    return raw
}
