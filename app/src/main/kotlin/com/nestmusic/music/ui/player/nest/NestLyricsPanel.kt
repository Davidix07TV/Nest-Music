/*
 * Adapted verbatim from Nest Music (Nest Music) PlayerLyrics.kt under GPL-3.0,
 * integrated with Nest Music's LyricsEntry / Romanization / AI Translation flows.
 */

package com.nestmusic.music.ui.player.nest

import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestmusic.music.R
import com.nestmusic.music.lyrics.LyricsEntry
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

internal val LYRIC_EASING = CubicBezierEasing(0.41f, 0f, 0.12f, 0.99f)
private const val LYRIC_SETTLE_MS = 500
private const val SCROLL_LEAD_MAX_MS = 500L
private const val SCROLL_LEAD_MIN_MS = 350L
private const val SCROLL_LEAD_SHARE = 0.45f
private const val ACTIVE_ANCHOR = 0.25f

private val LINE_FALLOFF_ALPHA = floatArrayOf(1f, 0.80f, 0.70f, 0.58f, 0.46f)
private val LINE_FALLOFF_BLUR = arrayOf(0.dp, 1.dp, 1.dp, 1.7.dp, 2.4.dp)
private const val BROWSING_ALPHA = 0.85f
private const val STAGGER_STEPS = 3
private const val STAGGER_FRACTION = 0.06f

private const val INACTIVE_SCALE = 0.97f
private const val PRESSED_SCALE = 0.94f
private const val GLOW_ALPHA = 0.62f
private val GLOW_ROOM = 10.dp
private val GLOW_RADIUS = 6.dp

private val SYNCED_FONT_SIZE = 34.sp
private val SYNCED_LINE_HEIGHT = 41.sp
private val UNSYNCED_FONT_SIZE = 30.sp
private val UNSYNCED_LINE_HEIGHT = 38.sp
private val BACKING_FONT_SIZE = 23.sp
private val BACKING_LINE_HEIGHT = 28.sp
private const val BACKING_ALPHA = 0.78f
private val SUB_LYRIC_FONT_SIZE = 20.sp
private val SUB_LYRIC_LINE_HEIGHT = 25.sp
private val SUB_BACKING_FONT_SIZE = 17.sp
private val SUB_BACKING_LINE_HEIGHT = 22.sp
private val SUB_LYRIC_TUCK = 4.dp
private const val SUB_LYRIC_ALPHA = 0.72f
private const val SUB_LYRIC_OPEN_MS = 320
private const val SUB_LYRIC_CLOSE_MS = 220

private const val BACKING_OPEN_MS = 400
private const val BACKING_CLOSE_MS = 450
private const val BACKING_LEAD_MS = 350L
private const val BACKING_REST_SCALE = 0.94f
private const val BACKING_FADE_LEAD = 1.4f
private val BACKING_RISE = 5.dp

private const val UNSUNG_ALPHA = 0.34f
private const val UNSUNG_ALPHA_STRIP = 0.52f
private val WIPE_FEATHER = 30.dp
private val WORD_RISE = 2.dp
private val DUET_LANE = 44.dp

private const val GAP_DOTS = 3
private val GAP_DOT_SIZE = 10.dp
private val GAP_DOT_GAP = 8.dp
private val GAP_ROW_HEIGHT = 26.dp
private const val GAP_DOT_REST = 0.28f

private const val PHASE_BEFORE = 0
private const val PHASE_MOVING = 1
private const val PHASE_AFTER = 2

private const val SKELETON_PERIOD_MS = 1_500
private val SKELETON_BAR = 24.dp
private val SKELETON_LEADING = 10.dp
private val SKELETON_BLOCK_GAP = 28.dp
private val SKELETON_BLOCKS = listOf(
    listOf(0.82f, 0.54f),
    listOf(0.92f),
    listOf(0.76f, 0.64f),
    listOf(0.88f, 0.44f),
    listOf(0.70f),
)

private fun TextStyle.forText(text: String): TextStyle {
    for (char in text) {
        when (Character.getDirectionality(char)) {
            Character.DIRECTIONALITY_LEFT_TO_RIGHT -> return this
            Character.DIRECTIONALITY_RIGHT_TO_LEFT,
            Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC ->
                return copy(textDirection = TextDirection.Rtl)
        }
    }
    return this
}

private fun TextLayoutResult.xOn(offset: Int, visualLine: Int, fallback: Float): Float =
    if (offset >= layoutInput.text.length || getLineForOffset(offset) != visualLine) {
        fallback
    } else {
        getHorizontalPosition(offset, usePrimaryDirection = true)
    }

private fun horizontalAt(
    layout: TextLayoutResult,
    chars: Float,
    visualLine: Int,
    spans: List<IntRange>,
): Float {
    val lineStart = layout.getLineStart(visualLine)
    val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)
    val index = chars.toInt()
    val span = spans.firstOrNull { index in it }
    if (span != null) {
        val from = maxOf(span.first, lineStart)
        val to = minOf(span.last + 1, lineEnd)
        val width = to - from
        if (width > 0) {
            val left = layout.xOn(from, visualLine, layout.getLineLeft(visualLine))
            val right = layout.xOn(to, visualLine, layout.getLineRight(visualLine))
            return left + (right - left) * ((chars - from) / width).coerceIn(0f, 1f)
        }
    }
    val here = layout.xOn(index, visualLine, 0f)
    val next = layout.xOn((index + 1).coerceAtMost(lineEnd), visualLine, 0f)
    return here + (next - here) * (chars - index)
}

private fun ContentDrawScope.sweepTo(
    layout: TextLayoutResult,
    revealedChars: Float,
    spans: List<IntRange>,
    feather: Boolean,
) {
    if (revealedChars <= 0f) return
    if (revealedChars >= layout.layoutInput.text.length) {
        drawContent()
        return
    }
    for (visualLine in 0 until layout.lineCount) {
        val start = layout.getLineStart(visualLine)
        if (revealedChars <= start) return
        val end = layout.getLineEnd(visualLine, visibleEnd = true)
        val lineLeft = layout.getLineLeft(visualLine)
        val lineRight = layout.getLineRight(visualLine)
        val cut = revealedChars < end
        val right = if (cut) {
            horizontalAt(layout, revealedChars, visualLine, spans).coerceIn(lineLeft, lineRight)
        } else {
            lineRight
        }
        val top = layout.getLineTop(visualLine)
        val bottom = layout.getLineBottom(visualLine)
        if (right <= lineLeft) continue
        clipRect(
            left = lineLeft,
            top = top,
            right = right,
            bottom = bottom,
        ) {
            this@sweepTo.drawContent()
            if (feather && cut) {
                val featherPx = WIPE_FEATHER.toPx()
                val fStart = maxOf(lineLeft, right - featherPx)
                if (right > fStart + 0.5f) {
                    drawRect(
                        brush = Brush.horizontalGradient(
                            colors = listOf(Color.Black, Color.Transparent),
                            startX = fStart,
                            endX = right,
                        ),
                        topLeft = Offset(fStart, top),
                        size = Size(right - fStart, bottom - top),
                        blendMode = BlendMode.DstIn,
                    )
                }
            }
        }
    }
}

private fun DrawScope.clipShiftedDown(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    risePx: Float,
    block: DrawScope.() -> Unit,
) {
    translate(top = -risePx) {
        clipRect(left = left, top = top, right = right, bottom = bottom) {
            block()
        }
    }
}

private fun ContentDrawScope.growEach(
    layout: TextLayoutResult,
    span: IntRange,
    visualLine: Int,
    growing: GrowingWord,
    positionMs: Long,
    risePx: Float,
    into: CharGrowth,
    draw: ContentDrawScope.() -> Unit,
) {
    val lineStart = layout.getLineStart(visualLine)
    val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)
    val lineLeft = layout.getLineLeft(visualLine)
    val lineRight = layout.getLineRight(visualLine)
    val top = layout.getLineTop(visualLine)
    val bottom = layout.getLineBottom(visualLine)
    val from = maxOf(span.first, lineStart)
    val to = minOf(span.last, lineEnd - 1)
    for (offset in from..to) {
        growing.sampleInto(offset - span.first, positionMs, into)
        val x0 = layout.xOn(offset, visualLine, lineLeft)
        val x1 = layout.xOn(offset + 1, visualLine, lineRight)
        val cLeft = minOf(x0, x1)
        val cRight = maxOf(x0, x1)
        if (cRight <= cLeft) continue
        val width = cRight - cLeft
        val pivot = Offset((cLeft + cRight) * 0.5f, (top + bottom) * 0.5f)
        translate(left = into.shift * width, top = -risePx * into.rise) {
            scale(scaleX = into.scale, scaleY = into.scale, pivot = pivot) {
                clipRect(left = cLeft, top = top, right = cRight, bottom = bottom) {
                    this@growEach.draw()
                }
            }
        }
    }
}

private fun ContentDrawScope.riseWith(
    layout: TextLayoutResult,
    line: NestLyricLine,
    positionMs: Long,
    risePx: Float,
    into: CharGrowth,
    growDraw: (ContentDrawScope.() -> Unit)? = null,
    draw: ContentDrawScope.() -> Unit,
) {
    if (!line.isLifted(positionMs)) {
        draw()
        return
    }
    val effectiveGrowDraw = growDraw ?: draw
    val spans = line.wordSpans
    val settled = positionMs >= line.words.last().endMs
    for (visualLine in 0 until layout.lineCount) {
        val lineStart = layout.getLineStart(visualLine)
        val lineEnd = layout.getLineEnd(visualLine, visibleEnd = true)
        val lineLeft = layout.getLineLeft(visualLine)
        val lineRight = layout.getLineRight(visualLine)
        val top = layout.getLineTop(visualLine)
        val bottom = layout.getLineBottom(visualLine)
        var cursor = lineLeft
        for (index in spans.indices) {
            val span = spans[index]
            if (span.last < lineStart || span.first >= lineEnd) continue
            val growing = line.growingAt(index)?.takeIf {
                positionMs >= it.startMs && positionMs <= it.restsAtMs
            }
            val lift = if (growing != null) {
                0f
            } else if (settled) {
                line.wordFall(index, positionMs)
            } else {
                line.wordLift(index, positionMs)
            }
            if (growing == null && lift <= 0f) continue
            val wordLeft = layout.xOn(maxOf(span.first, lineStart), visualLine, lineLeft)
            val wordRight = layout.xOn(minOf(span.last + 1, lineEnd), visualLine, lineRight)
            val lo = minOf(wordLeft, wordRight)
            val hi = maxOf(wordLeft, wordRight)
            if (lo > cursor) {
                clipRect(left = cursor, top = top, right = lo, bottom = bottom) {
                    this@riseWith.draw()
                }
            }
            if (growing != null) {
                growEach(layout, span, visualLine, growing, positionMs, risePx, into, effectiveGrowDraw)
            } else {
                clipShiftedDown(lo, top, hi, bottom, risePx * lift) { this@riseWith.draw() }
            }
            cursor = maxOf(cursor, hi)
        }
        if (cursor < lineRight) {
            clipRect(left = cursor, top = top, right = lineRight, bottom = bottom) {
                this@riseWith.draw()
            }
        }
    }
}

private fun ContentDrawScope.glowGrown(
    layout: TextLayoutResult,
    line: NestLyricLine,
    positionMs: Long,
    risePx: Float,
    padPx: Float,
    into: CharGrowth,
) {
    if (!line.isGrowing(positionMs)) return
    drawContent()
    val spans = line.wordSpans
    var cursor = 0f
    for (grown in line.growingWords) {
        if (positionMs < grown.startMs || positionMs > grown.restsAtMs) continue
        val span = spans[grown.index]
        for (offset in span) {
            grown.sampleInto(offset - span.first, positionMs, into)
            val box = layout.getBoundingBox(offset)
            val left = padPx + box.left + into.shift * box.width - box.width * (into.scale - 1f)
            val right = padPx + box.right + into.shift * box.width + box.width * (into.scale - 1f)
            if (left > cursor) {
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset(cursor, 0f),
                    size = Size(left - cursor, size.height),
                    blendMode = BlendMode.Clear,
                )
            }
            if (into.bloom < 1f && right > left) {
                drawRect(
                    color = Color.Black.copy(alpha = into.bloom),
                    topLeft = Offset(left, 0f),
                    size = Size(right - left, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
            cursor = maxOf(cursor, right)
        }
    }
    if (cursor < size.width) {
        drawRect(
            color = Color.Transparent,
            topLeft = Offset(cursor, 0f),
            size = Size(size.width - cursor, size.height),
            blendMode = BlendMode.Clear,
        )
    }
    val _unused = risePx
}

/**
 * Nest Music word-by-word swept lyric line:
 * - Unsung base layer (`unsungAlpha`)
 * - Blurred bloom halo layer for held notes (`glowAlpha` + `GrowingWord`)
 * - Lit sweep layer (`30.dp` `WIPE_FEATHER`, `2.dp` `WORD_RISE`, per-letter `GrowingWord` swell).
 */
@Composable
internal fun SweptLyricLine(
    line: NestLyricLine,
    clock: State<Long>,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Color.White,
    unsungAlpha: Float = UNSUNG_ALPHA,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
    rise: Boolean = true,
    glowAlpha: Float = 0f,
    room: Dp = 0.dp,
) {
    val resolved = style.forText(line.text)
    var layout by remember(line.text) { mutableStateOf<TextLayoutResult?>(null) }
    val growth = remember { CharGrowth() }

    val phase by remember(line, clock) {
        derivedStateOf(structuralEqualityPolicy()) {
            val now = clock.value
            when {
                now < line.animatesFromMs -> PHASE_BEFORE
                now < line.animatesUntilMs -> PHASE_MOVING
                else -> PHASE_AFTER
            }
        }
    }
    val moving = phase == PHASE_MOVING
    val active = remember(line, clock) {
        derivedStateOf {
            val now = clock.value
            now >= line.timeMs && now < line.endMs
        }
    }

    Box(modifier) {
        val pad = Modifier.padding(room)
        Text(
            text = line.text,
            style = resolved,
            color = color.copy(alpha = unsungAlpha),
            maxLines = maxLines,
            overflow = overflow,
            onTextLayout = { layout = it },
            modifier = pad.drawWithContent {
                val measured = layout ?: return@drawWithContent
                if (!moving) {
                    drawContent()
                } else {
                    val now = clock.value
                    val risePx = if (rise) WORD_RISE.toPx() else 0f
                    if (risePx > 0f && line.isLifted(now)) {
                        riseWith(measured, line, now, risePx, growth) {
                            drawContent()
                        }
                    } else {
                        drawContent()
                    }
                }
            },
        )

        if (moving && glowAlpha > 0f && line.growingWords.isNotEmpty() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Text(
                text = line.text,
                style = resolved,
                color = color,
                maxLines = maxLines,
                overflow = overflow,
                modifier = Modifier
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                        alpha = glowAlpha
                    }
                    .drawWithContent {
                        val measured = layout ?: return@drawWithContent
                        glowGrown(measured, line, clock.value, WORD_RISE.toPx(), room.toPx(), growth)
                    }
                    .blur(GLOW_RADIUS, BlurredEdgeTreatment.Unbounded)
                    .then(pad)
                    .drawWithContent {
                        val measured = layout ?: return@drawWithContent
                        val now = clock.value
                        if (!line.isGrowing(now)) return@drawWithContent
                        riseWith(
                            layout = measured,
                            line = line,
                            positionMs = now,
                            risePx = WORD_RISE.toPx(),
                            into = growth,
                            growDraw = { drawContent() },
                        ) {}
                    },
            )
        }

        val offscreen = if (moving && active.value) {
            Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        } else {
            Modifier
        }
        Text(
            text = line.text,
            style = resolved,
            color = color,
            maxLines = maxLines,
            overflow = overflow,
            modifier = offscreen.then(pad).drawWithContent {
                val measured = layout ?: return@drawWithContent
                when (phase) {
                    PHASE_BEFORE -> Unit
                    PHASE_AFTER -> drawContent()
                    else -> {
                        val now = clock.value
                        val revealed = line.revealedChars(now)
                        if (revealed <= 0f) return@drawWithContent
                        val feather = active.value
                        val risePx = if (rise) WORD_RISE.toPx() else 0f
                        if (risePx > 0f) {
                            riseWith(measured, line, now, risePx, growth) {
                                sweepTo(measured, revealed, line.sweepSpans, feather)
                            }
                        } else {
                            sweepTo(measured, revealed, line.sweepSpans, feather)
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun PanelVoice(
    line: NestLyricLine,
    clock: State<Long>,
    style: TextStyle,
    isActive: Boolean,
    sung: Boolean,
    synced: Boolean,
    browsing: Boolean,
    glowAlpha: Float,
    room: Dp,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
    rise: Boolean = true,
) {
    val ink = LocalPlayerInk.current.value
    val voiced = if (alignEnd) style.copy(textAlign = TextAlign.End) else style
    if (line.isWordSynced) {
        SweptLyricLine(
            line = line,
            clock = clock,
            style = voiced,
            color = ink,
            unsungAlpha = UNSUNG_ALPHA,
            rise = rise,
            glowAlpha = glowAlpha,
            room = room,
            modifier = modifier,
        )
    } else {
        val lit by animateFloatAsState(
            targetValue = if (!synced || browsing || sung || isActive) 1f else UNSUNG_ALPHA,
            animationSpec = tween(220),
            label = "nestLyricLit",
        )
        Text(
            text = line.text,
            style = voiced.forText(line.text),
            color = ink.copy(alpha = lit),
            modifier = modifier.padding(room),
        )
    }
}

private class ScrollRun {
    var fromIndex by mutableIntStateOf(0)
    var startedAtMs by mutableLongStateOf(0L)
    var durationMs by mutableIntStateOf(LYRIC_SETTLE_MS)
}

private fun Modifier.revealBelow(progress: State<Float>): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val open = progress.value
        val height = (placeable.height * open).roundToInt()
        layout(placeable.width, height) {
            placeable.placeWithLayer(0, 0) {
                translationY = -placeable.height * (1f - open) * 0.6f
                alpha = open * open
            }
        }
    }

private fun Modifier.revealBacking(progress: State<Float>, alignEnd: Boolean): Modifier = this
    .layout { measurable, constraints ->
        val placeable = measurable.measure(constraints)
        val open = progress.value
        val rise = BACKING_RISE.toPx()
        layout(placeable.width, (placeable.height * open).roundToInt()) {
            placeable.placeWithLayer(0, 0) {
                alpha = (open * BACKING_FADE_LEAD).coerceAtMost(1f)
                translationY = rise * (1f - open)
                val grow = BACKING_REST_SCALE + (1f - BACKING_REST_SCALE) * open
                scaleX = grow
                scaleY = grow
                transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0f)
            }
        }
    }

private fun Modifier.fadingVerticalEdges(topFade: Dp = 28.dp, bottomFade: Dp = 44.dp): Modifier =
    this
        .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        .drawWithCache {
            val topFraction = (topFade.toPx() / size.height.coerceAtLeast(1f)).coerceIn(0f, 0.25f)
            val botFraction = (1f - bottomFade.toPx() / size.height.coerceAtLeast(1f)).coerceIn(0.70f, 1f)
            val mask = Brush.verticalGradient(
                0f to Color.Transparent,
                topFraction to Color.Black,
                botFraction to Color.Black,
                1f to Color.Transparent,
            )
            onDrawWithContent {
                drawContent()
                drawRect(brush = mask, blendMode = BlendMode.DstIn)
            }
        }

/**
 * Animated 3-dot instrumental interlude row (Nest Music `PlayerLyrics.kt` gap indicator).
 */
@Composable
private fun InstrumentalDotsRow(
    line: NestLyricLine,
    clock: State<Long>,
    alignEnd: Boolean,
    modifier: Modifier = Modifier,
) {
    val ink = LocalPlayerInk.current.value
    val active by remember(line, clock) {
        derivedStateOf {
            val now = clock.value
            now >= line.timeMs && now < line.endMs
        }
    }
    val open by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(durationMillis = 360, easing = LYRIC_EASING),
        label = "nestGapDotsOpen",
    )
    if (open <= 0.01f) return

    Box(
        contentAlignment = if (alignEnd) Alignment.CenterEnd else Alignment.CenterStart,
        modifier = modifier
            .fillMaxWidth()
            .layout { measurable, constraints ->
                val p = measurable.measure(constraints)
                layout(p.width, (p.height * open).roundToInt()) {
                    p.placeWithLayer(0, 0) {
                        alpha = open
                        scaleX = 0.85f + 0.15f * open
                        scaleY = 0.85f + 0.15f * open
                        transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0.5f)
                    }
                }
            }
            .padding(horizontal = GLOW_ROOM, vertical = 6.dp),
    ) {
        val totalWidth = GAP_DOT_SIZE * GAP_DOTS + GAP_DOT_GAP * (GAP_DOTS - 1)
        Box(
            modifier = Modifier
                .width(totalWidth)
                .height(GAP_ROW_HEIGHT)
                .drawBehind {
                    val now = clock.value
                    val span = (line.endMs - line.timeMs).coerceAtLeast(1L).toFloat()
                    val progress = ((now - line.timeMs) / span).coerceIn(0f, 1f)
                    val r = GAP_DOT_SIZE.toPx() / 2f
                    val step = GAP_DOT_SIZE.toPx() + GAP_DOT_GAP.toPx()
                    val cy = size.height / 2f
                    for (i in 0 until GAP_DOTS) {
                        val dotStart = i.toFloat() / GAP_DOTS
                        val dotFill = ((progress - dotStart) * GAP_DOTS).coerceIn(0f, 1f)
                        val alpha = GAP_DOT_REST + (1f - GAP_DOT_REST) * dotFill
                        val scale = 0.88f + 0.18f * sinOfPulse(dotFill)
                        drawCircle(
                            color = ink.copy(alpha = alpha),
                            radius = r * scale,
                            center = Offset(r + i * step, cy),
                        )
                    }
                },
        )
    }
}

private fun sinOfPulse(t: Float): Float = 4f * t * (1f - t)

@Composable
internal fun NestLyricsSkeleton(
    animated: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val sweep: State<Float> = if (!animated) {
        remember { mutableStateOf(0.5f) }
    } else {
        rememberInfiniteTransition(label = "nestLyricsSkeleton").animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(SKELETON_PERIOD_MS, easing = LinearEasing)),
            label = "nestLyricsSkeletonSweep",
        )
    }
    BoxWithConstraints(modifier = modifier.padding(top = 32.dp, start = 24.dp, end = 24.dp)) {
        val column = maxWidth
        Column(verticalArrangement = Arrangement.spacedBy(SKELETON_BLOCK_GAP)) {
            SKELETON_BLOCKS.forEach { rows ->
                Column(verticalArrangement = Arrangement.spacedBy(SKELETON_LEADING)) {
                    rows.forEach { fraction ->
                        Box(
                            Modifier
                                .fillMaxWidth(fraction)
                                .height(SKELETON_BAR)
                                .clip(RoundedCornerShape(4.dp))
                                .drawWithCache {
                                    val full = column.toPx()
                                    val band = full * 0.45f
                                    val startX = -band + sweep.value * (full + band * 2)
                                    val brush = Brush.horizontalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = 0.10f),
                                            Color.White.copy(alpha = 0.24f),
                                            Color.White.copy(alpha = 0.10f),
                                        ),
                                        startX = startX,
                                        endX = startX + band,
                                    )
                                    onDrawBehind { drawRect(brush) }
                                },
                        )
                    }
                }
            }
        }
    }
}

/**
 * Single-line lyric preview directly above the scrubber in Nest Music Now Playing
 * (`CurrentLyricStrip` in Nest Music `PlayerLyrics.kt`):
 * - Word-synced wipe via [SweptLyricLine] (`UNSUNG_ALPHA_STRIP = 0.52f`, `rise = false`)
 * - Smooth 340ms vertical slide + fade between lines
 * - Tapping opens the full inline [NestLyricsPanel].
 */
@Composable
internal fun NestCurrentLyricStrip(
    lines: List<NestLyricLine>,
    clock: State<Long>,
    isSynced: Boolean,
    isLoading: Boolean,
    lyricsUnavailable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LocalPlayerInk.current.value
    val glow = LocalPlayerInkGlow.current.value
    val lineStyle = MaterialTheme.typography.titleMedium.copy(
        fontWeight = FontWeight.SemiBold,
        shadow = Shadow(glow, blurRadius = 9f),
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .offset(y = 4.dp),
    ) {
        when {
            lines.isEmpty() && isLoading -> {
                Text(
                    text = stringResource(R.string.loading_lyrics),
                    style = lineStyle,
                    color = ink.copy(alpha = 0.50f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(vertical = 4.dp),
                )
            }
            lines.isEmpty() || lyricsUnavailable -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClick)
                        .padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = NestPlayerIcons.MusicNote,
                        contentDescription = null,
                        tint = ink.copy(alpha = 0.55f),
                        modifier = Modifier.size(15.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.lyrics_not_available),
                        style = lineStyle,
                        color = ink.copy(alpha = 0.55f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            !isSynced -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClick)
                        .padding(vertical = 4.dp),
                ) {
                    Icon(
                        imageVector = NestPlayerIcons.MusicNote,
                        contentDescription = null,
                        tint = ink,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.open_lyrics),
                        style = lineStyle,
                        color = ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = NestPlayerIcons.ChevronRight,
                        contentDescription = null,
                        tint = ink.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            else -> {
                val activeIdx by remember(lines, clock) {
                    derivedStateOf { lines.indexOfLast { it.timeMs <= clock.value } }
                }
                val current = lines.getOrNull(activeIdx)
                val instrumental = current == null || current.isGap

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onClick)
                        .padding(vertical = 4.dp),
                ) {
                    if (instrumental) {
                        Icon(
                            imageVector = NestPlayerIcons.MusicNote,
                            contentDescription = null,
                            tint = ink.copy(alpha = 0.72f),
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    AnimatedContent(
                        targetState = activeIdx,
                        transitionSpec = {
                            val forward = targetState >= initialState
                            val slideSpec = tween<androidx.compose.ui.unit.IntOffset>(
                                durationMillis = 340,
                                easing = FastOutSlowInEasing,
                            )
                            (slideInVertically(slideSpec) { if (forward) it else -it } + fadeIn(tween(240))) togetherWith
                                (slideOutVertically(slideSpec) { if (forward) -it else it } + fadeOut(tween(180)))
                        },
                        label = "nestStripLine",
                        modifier = Modifier.weight(1f, fill = false),
                    ) { idx ->
                        val line = lines.getOrNull(idx)
                        if (line == null || line.isGap) {
                            Text(
                                text = stringResource(R.string.instrumental),
                                style = lineStyle,
                                color = ink.copy(alpha = 0.65f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else if (line.isWordSynced) {
                            SweptLyricLine(
                                line = line,
                                clock = clock,
                                style = lineStyle,
                                color = ink,
                                unsungAlpha = UNSUNG_ALPHA_STRIP,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                rise = false,
                            )
                        } else {
                            Text(
                                text = line.text,
                                style = lineStyle,
                                color = ink,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = NestPlayerIcons.ChevronRight,
                        contentDescription = null,
                        tint = ink.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

private fun String.differsFrom(original: String): Boolean =
    trim().lowercase(Locale.ROOT) != original.trim().lowercase(Locale.ROOT)

/**
 * Full Nest Music Apple Music Lyrics Panel:
 * - Synced (`34.sp`/`41.sp` ExtraBold) & unsynced (`30.sp`/`38.sp` ExtraBold)
 * - Word-by-word feathered sweep (`SweptLyricLine`) + `2.dp` word lift + `GrowingWord` held-note swell & bloom
 * - Distance-indexed symmetric blur (`0, 1, 1, 1.7, 2.4dp`) & alpha (`1, 0.80, 0.70, 0.58, 0.46`) depth-of-field
 * - `LYRIC_EASING` staggered spring-like scroll with `ACTIVE_ANCHOR = 0.25f` and adaptive scroll lead
 * - Duet right-aligned lane (`agent == "v2"`), fold-open backing vocals, live Romanization & AI Translation sub-lines
 * - Animated 3-dot instrumental interludes, manual-browsing auto-scroll pause, tap-to-seek, and long-press line menu.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun NestLyricsPanel(
    lines: List<NestLyricLine>,
    rawEntries: List<LyricsEntry>,
    clock: State<Long>,
    isSynced: Boolean,
    isLoading: Boolean,
    lyricsUnavailable: Boolean,
    showRomanized: Boolean,
    showTranslated: Boolean,
    onToggleRomanized: (() -> Unit)? = null,
    onToggleTranslated: (() -> Unit)? = null,
    onSeekToMs: (Long) -> Unit,
    onLineLongPress: ((LyricsEntry) -> Unit)? = null,
    bottomPadding: Dp = 32.dp,
    modifier: Modifier = Modifier,
) {
    val ink = LocalPlayerInk.current.value
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    if (lines.isEmpty()) {
        Box(modifier = modifier.fillMaxSize()) {
            if (isLoading && !lyricsUnavailable) {
                NestLyricsSkeleton(modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                ) {
                    Text(
                        text = stringResource(R.string.lyrics_not_available),
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        color = ink.copy(alpha = 0.56f),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        return
    }

    val listState = rememberLazyListState()
    val isDuet = remember(lines) { lines.any { it.alignment == LyricAlignment.End } }

    // Manual scroll / browse detection: freezes auto-scroll for 4.5s after user drag
    var manualBrowseUntilMs by remember { mutableLongStateOf(0L) }
    var programmaticScrolling by remember { mutableStateOf(false) }
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !programmaticScrolling) {
            manualBrowseUntilMs = System.currentTimeMillis() + 4_500L
        }
    }
    var browsing by remember { mutableStateOf(false) }
    LaunchedEffect(manualBrowseUntilMs) {
        val remaining = manualBrowseUntilMs - System.currentTimeMillis()
        if (remaining > 0L) {
            browsing = true
            delay(remaining)
            browsing = false
        } else {
            browsing = false
        }
    }

    val activeIndex by remember(lines, clock, isSynced) {
        derivedStateOf {
            if (!isSynced) -1 else lines.indexOfLast { it.timeMs <= clock.value }
        }
    }

    // Scroll lead index: start scrolling ~350-500ms ahead of the next line so it arrives right on the downbeat
    val focusIndex by remember(lines, clock, isSynced) {
        derivedStateOf {
            if (!isSynced) return@derivedStateOf 0
            val now = clock.value
            val curr = lines.indexOfLast { it.timeMs <= now }.coerceAtLeast(0)
            val next = lines.getOrNull(curr + 1)
            if (next != null) {
                val gap = (next.timeMs - lines[curr].timeMs).coerceAtLeast(1L)
                val lead = (gap * SCROLL_LEAD_SHARE).toLong().coerceIn(SCROLL_LEAD_MIN_MS, SCROLL_LEAD_MAX_MS)
                if (now >= next.timeMs - lead) curr + 1 else curr
            } else {
                curr
            }
        }
    }

    val run = remember { ScrollRun() }
    var since by remember { mutableLongStateOf(0L) }

    LaunchedEffect(focusIndex, isSynced, browsing) {
        if (!isSynced || browsing || focusIndex < 0 || focusIndex >= lines.size) return@LaunchedEffect
        val layoutInfo = listState.layoutInfo
        val viewportHeight = layoutInfo.viewportEndOffset - layoutInfo.viewportStartOffset
        if (viewportHeight <= 0) return@LaunchedEffect
        val anchorPx = (viewportHeight * ACTIVE_ANCHOR).roundToInt()

        run.fromIndex = focusIndex
        run.startedAtMs = System.nanoTime() / 1_000_000L
        run.durationMs = LYRIC_SETTLE_MS

        programmaticScrolling = true
        try {
            val visible = layoutInfo.visibleItemsInfo.firstOrNull { it.index == focusIndex }
            if (visible != null) {
                val delta = (visible.offset - anchorPx).toFloat()
                if (abs(delta) > 2f) {
                    listState.animateScrollBy(
                        value = delta,
                        animationSpec = tween(durationMillis = LYRIC_SETTLE_MS, easing = LYRIC_EASING),
                    )
                }
            } else {
                listState.animateScrollToItem(index = focusIndex, scrollOffset = -anchorPx)
            }
        } finally {
            programmaticScrolling = false
        }
    }

    // Stagger clock during scroll runs
    LaunchedEffect(run.startedAtMs) {
        if (run.startedAtMs == 0L) return@LaunchedEffect
        while (true) {
            val nowMs = withFrameMillis { it }
            val elapsed = nowMs - run.startedAtMs
            since = elapsed
            if (elapsed > run.durationMs + 240L) break
        }
    }

    val subRevealProgress = remember { Animatable(if (showRomanized || showTranslated) 1f else 0f) }
    LaunchedEffect(showRomanized, showTranslated) {
        val target = if (showRomanized || showTranslated) 1f else 0f
        subRevealProgress.animateTo(
            targetValue = target,
            animationSpec = tween(
                durationMillis = if (target > 0.5f) SUB_LYRIC_OPEN_MS else SUB_LYRIC_CLOSE_MS,
                easing = if (target > 0.5f) LYRIC_EASING else FastOutSlowInEasing,
            ),
        )
    }

    val mainStyle = TextStyle(
        fontSize = if (isSynced) SYNCED_FONT_SIZE else UNSYNCED_FONT_SIZE,
        lineHeight = if (isSynced) SYNCED_LINE_HEIGHT else UNSYNCED_LINE_HEIGHT,
        fontWeight = FontWeight.ExtraBold,
        letterSpacing = (-0.4).sp,
    )
    val subStyle = TextStyle(
        fontSize = SUB_LYRIC_FONT_SIZE,
        lineHeight = SUB_LYRIC_LINE_HEIGHT,
        fontWeight = FontWeight.SemiBold,
    )

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                start = (24.dp - GLOW_ROOM).coerceAtLeast(0.dp),
                end = (24.dp - GLOW_ROOM).coerceAtLeast(0.dp),
                top = 28.dp,
                bottom = bottomPadding + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(if (isSynced) 16.dp else 14.dp),
            modifier = Modifier
                .fillMaxSize()
                .fadingVerticalEdges(topFade = 26.dp, bottomFade = 42.dp),
        ) {
            itemsIndexed(
                items = lines,
                key = { idx, line -> "${idx}_${line.timeMs}" },
            ) { index, line ->
                val alignEnd = line.alignment == LyricAlignment.End
                if (line.isGap) {
                    InstrumentalDotsRow(
                        line = line,
                        clock = clock,
                        alignEnd = alignEnd,
                    )
                    return@itemsIndexed
                }

                val isActive = isSynced && index == activeIndex
                val sung = isSynced && index < activeIndex
                val distance = if (isSynced && activeIndex >= 0) {
                    abs(index - activeIndex)
                } else {
                    0
                }
                val targetAlpha = when {
                    !isSynced -> 0.94f
                    browsing -> BROWSING_ALPHA
                    else -> LINE_FALLOFF_ALPHA[distance.coerceAtMost(LINE_FALLOFF_ALPHA.lastIndex)]
                }
                val rowAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = tween(durationMillis = 340, easing = LYRIC_EASING),
                    label = "nestRowAlpha",
                )

                val interactionSource = remember { MutableInteractionSource() }
                val isPressed by interactionSource.collectIsPressedAsState()
                val targetScale = when {
                    isPressed -> PRESSED_SCALE
                    !isSynced || isActive -> 1f
                    else -> INACTIVE_SCALE
                }
                val rowScale by animateFloatAsState(
                    targetValue = targetScale,
                    animationSpec = tween(durationMillis = 320, easing = LYRIC_EASING),
                    label = "nestRowScale",
                )
                val glow by animateFloatAsState(
                    targetValue = if (isActive) GLOW_ALPHA else 0f,
                    animationSpec = tween(durationMillis = 320),
                    label = "nestRowGlow",
                )

                val blurRadius = if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                    isSynced &&
                    !browsing &&
                    !isActive
                ) {
                    LINE_FALLOFF_BLUR[distance.coerceAtMost(LINE_FALLOFF_BLUR.lastIndex)]
                } else {
                    0.dp
                }

                // Live romanization / translation sub-line from the underlying LyricsEntry
                val rawEntry = rawEntries.getOrNull(line.sourceEntryIndex)
                val romanized by (rawEntry?.romanizedTextFlow)?.collectAsState() ?: remember { mutableStateOf(null) }
                val translated by (rawEntry?.translatedTextFlow)?.collectAsState() ?: remember { mutableStateOf(null) }
                val subText = when {
                    showTranslated && !translated.isNullOrBlank() -> translated
                    showRomanized && !romanized.isNullOrBlank() -> romanized
                    else -> null
                }

                // Backing vocal fold-open animation
                val backingOpen = line.background?.let { backing ->
                    val due by remember(backing, clock) {
                        derivedStateOf {
                            val now = clock.value
                            now >= backing.timeMs - BACKING_LEAD_MS && now < backing.endMs
                        }
                    }
                    val wanted = !isSynced || browsing || if (backing.isWordSynced) {
                        due
                    } else {
                        isActive || index == focusIndex
                    }
                    animateFloatAsState(
                        targetValue = if (wanted) 1f else 0f,
                        animationSpec = if (wanted) {
                            tween(BACKING_OPEN_MS, easing = LYRIC_EASING)
                        } else {
                            tween(BACKING_CLOSE_MS, easing = FastOutSlowInEasing)
                        },
                        label = "nestBackingOpen",
                    )
                }

                val lanePadding = when {
                    !isDuet -> Modifier
                    alignEnd -> Modifier.padding(start = DUET_LANE)
                    else -> Modifier.padding(end = DUET_LANE)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(lanePadding)
                        .graphicsLayer {
                            alpha = rowAlpha
                            scaleX = rowScale
                            scaleY = rowScale
                            transformOrigin = TransformOrigin(if (alignEnd) 1f else 0f, 0.5f)
                            // Stagger trailing rows slightly during focus scroll runs (Nest Music ScrollRun)
                            val trail = (index - run.fromIndex).coerceIn(0, STAGGER_STEPS)
                            if (trail > 0 && since in 1L..(run.durationMs + 160L)) {
                                val t = (since.toFloat() / run.durationMs).coerceIn(0f, 1f)
                                val wave = 4f * t * (1f - t)
                                translationY = trail * STAGGER_FRACTION * wave * with(density) { 42.dp.toPx() }
                            }
                        }
                        .then(
                            if (blurRadius > 0.dp) {
                                Modifier.blur(blurRadius, BlurredEdgeTreatment.Unbounded)
                            } else {
                                Modifier
                            },
                        )
                        .clip(RoundedCornerShape(14.dp))
                        .combinedClickable(
                            interactionSource = interactionSource,
                            indication = null,
                            onClick = {
                                if (isSynced) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    manualBrowseUntilMs = 0L
                                    browsing = false
                                    onSeekToMs(line.timeMs)
                                }
                            },
                            onLongClick = if (rawEntry != null && onLineLongPress != null) {
                                {
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onLineLongPress(rawEntry)
                                }
                            } else {
                                null
                            },
                        ),
                ) {
                    PanelVoice(
                        line = line,
                        clock = clock,
                        style = mainStyle,
                        isActive = isActive,
                        sung = sung,
                        synced = isSynced,
                        browsing = browsing,
                        glowAlpha = glow,
                        room = GLOW_ROOM,
                        alignEnd = alignEnd,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (subText != null && subText.differsFrom(line.text)) {
                        val subLine = remember(subText, line) {
                            NestLyricLine(
                                timeMs = line.timeMs,
                                text = subText,
                                timingSource = if (line.isWordSynced) line else null,
                                alignment = line.alignment,
                            )
                        }
                        PanelVoice(
                            line = subLine,
                            clock = clock,
                            style = subStyle,
                            isActive = isActive,
                            sung = sung,
                            synced = isSynced,
                            browsing = browsing,
                            glowAlpha = 0f,
                            room = 0.dp,
                            alignEnd = alignEnd,
                            rise = false,
                            modifier = Modifier
                                .fillMaxWidth()
                                .revealBelow(subRevealProgress.asState())
                                .padding(start = GLOW_ROOM, end = GLOW_ROOM, bottom = GLOW_ROOM)
                                .offset(y = -SUB_LYRIC_TUCK)
                                .graphicsLayer { alpha = SUB_LYRIC_ALPHA },
                        )
                    }

                    line.background?.let { backing ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .then(backingOpen?.let { Modifier.revealBacking(it, alignEnd) } ?: Modifier),
                        ) {
                            PanelVoice(
                                line = backing.withoutBracketPunctuation(),
                                clock = clock,
                                style = mainStyle.copy(
                                    fontSize = BACKING_FONT_SIZE,
                                    lineHeight = BACKING_LINE_HEIGHT,
                                ),
                                isActive = isActive,
                                sung = sung,
                                synced = isSynced,
                                browsing = browsing,
                                glowAlpha = 0f,
                                room = 0.dp,
                                alignEnd = alignEnd,
                                rise = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = GLOW_ROOM, end = GLOW_ROOM, bottom = GLOW_ROOM)
                                    .graphicsLayer { alpha = BACKING_ALPHA },
                            )
                        }
                    }
                }
            }
        }

        // Bottom-start Romanize / Translate pill controls (matching Nest Music's lyrics script toggles)
        if (onToggleTranslated != null || onToggleRomanized != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = bottomPadding + 10.dp),
            ) {
                if (onToggleTranslated != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (showTranslated) {
                                    ink.copy(alpha = 0.85f)
                                } else {
                                    ink.copy(alpha = 0.18f)
                                },
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleTranslated()
                            },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.nest_translate),
                            contentDescription = stringResource(R.string.ai_lyrics_translation),
                            tint = if (showTranslated) Color.Black else ink,
                            modifier = Modifier.size(19.dp),
                        )
                    }
                }
                if (onToggleRomanized != null) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .height(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (showRomanized) {
                                    ink.copy(alpha = 0.85f)
                                } else {
                                    ink.copy(alpha = 0.18f)
                                },
                            )
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                onToggleRomanized()
                            }
                            .padding(horizontal = 12.dp),
                    ) {
                        Text(
                            text = "Aa",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = if (showRomanized) Color.Black else ink,
                        )
                    }
                }
            }
        }
    }
}
