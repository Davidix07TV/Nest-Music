/*
 * Adapted verbatim from Nest Music (Nest Music) NowPlayingChrome.kt,
 * NowPlayingCredits.kt, NowPlayingArtwork.kt, and PlayerControls.kt under GPL-3.0.
 */

package com.nestmusic.music.ui.player.nest

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.nestmusic.music.R
import com.nestmusic.music.models.MediaMetadata
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal val NEST_THUMB_SIZE = 66.dp
internal val NEST_HEADER_HEIGHT = 72.dp
internal val NEST_ART_CORNER = 14.dp
internal val NEST_THUMB_CORNER = 8.dp
internal val NEST_GUTTER = 24.dp

private val CREDITS_TITLE_SIZE = 22.sp
private val CREDITS_ARTIST_SIZE = 17.sp
private const val CREDITS_ARTIST_ALPHA = 0.73f

private const val MARQUEE_DP_PER_SEC = 26f
private val MARQUEE_GAP = 48.dp
private const val MARQUEE_REST_MS = 5_000L
private const val MARQUEE_ARTIST_STAGGER_MS = 3_000L

private val PLAYER_SKIP_ICON_SIZE = 51.dp
private const val PLAYER_SKIP_ICON_SCALE_Y = 0.80f
private val TRANSPORT_SPACING = 15.dp
private const val VOLUME_BAR_OPACITY = 0.49f

private val BOTTOM_ACTION_SIZE = 44.dp
private val BOTTOM_ICON_SIZE = 29.dp
private val BOTTOM_ACTIVE_TILE_SIZE = 35.dp
private val BOTTOM_ACTIVE_TILE_CORNER = 7.dp
private const val BOTTOM_ACTIVE_TILE_ALPHA = 0.62f

/**
 * Nest Music `PlayerTopStrip`:
 * - Multi-stop top gradient scrim for legible header chrome
 * - Left collapse chevron (`PlayerChevronDown`) + two-line stacked uppercase `PLAYING FROM` + source title.
 */
@Composable
internal fun NestPlayerTopStrip(
    queueTitle: String?,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LocalPlayerInk.current.value
    val alphas = LocalPlayerAlphas.current

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.28f),
                    0.6f to Color.Black.copy(alpha = 0.10f),
                    1f to Color.Transparent,
                ),
            )
            .padding(horizontal = 20.dp),
    ) {
        // Apple Music drag-pill handle centered at top
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 8.dp)
                .width(38.dp)
                .height(5.dp)
                .clip(CircleShape)
                .background(ink.copy(alpha = 0.36f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onCollapse,
                ),
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(top = 8.dp)
                .fillMaxWidth(),
        ) {
            NestTactileGlyph(
                onClick = onCollapse,
                touchSize = 40.dp,
            ) {
                Icon(
                    imageVector = NestPlayerIcons.PlayerChevronDown,
                    contentDescription = null,
                    tint = ink.copy(alpha = alphas.secondary),
                    modifier = Modifier.size(24.dp),
                )
            }

            if (!queueTitle.isNullOrBlank()) {
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.playing_from).uppercase(),
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.8.sp,
                        ),
                        color = ink.copy(alpha = 0.56f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = queueTitle,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        color = ink.copy(alpha = alphas.primary),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * Nest Music `MarqueeText` with calibrated constant-dp/sec scroll, 5s rest pause,
 * and optional initial delay (`MARQUEE_ARTIST_STAGGER_MS = 3_000L` for artist row).
 */
@Composable
internal fun NestMarqueeText(
    text: String,
    style: TextStyle,
    color: Color,
    initialDelayMs: Long = 0L,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val gapPx = with(density) { MARQUEE_GAP.roundToPx() }
    val pxPerSec = with(density) { MARQUEE_DP_PER_SEC.dp.toPx() }
    val scroll = remember(text) { Animatable(0f) }
    var overflowPx by remember(text) { mutableIntStateOf(0) }
    var textWidthPx by remember(text) { mutableIntStateOf(0) }

    LaunchedEffect(text, overflowPx, textWidthPx) {
        scroll.snapTo(0f)
        if (overflowPx <= 0 || textWidthPx <= 0) return@LaunchedEffect
        delay(MARQUEE_REST_MS + initialDelayMs)
        val distance = (textWidthPx + gapPx).toFloat()
        val durationMs = ((distance / pxPerSec) * 1000f).roundToInt().coerceAtLeast(1500)
        while (true) {
            scroll.animateTo(
                targetValue = -distance,
                animationSpec = tween(durationMillis = durationMs, easing = LinearEasing),
            )
            scroll.snapTo(0f)
            delay(MARQUEE_REST_MS)
        }
    }

    val clickMod = if (onClick != null) {
        Modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClick = onClick,
        )
    } else {
        Modifier
    }

    SubcomposeLayout(
        modifier = modifier
            .clipToBounds()
            .then(clickMod),
    ) { constraints ->
        val unbounded = constraints.copy(maxWidth = Constraints.Infinity)
        val first = subcompose("first") {
            Text(text = text, style = style, color = color, maxLines = 1, softWrap = false)
        }.first().measure(unbounded)

        val containerWidth = minOf(first.width, constraints.maxWidth)
        val spills = first.width > constraints.maxWidth
        overflowPx = (first.width - constraints.maxWidth).coerceAtLeast(0)
        textWidthPx = first.width

        val second = if (spills) {
            subcompose("second") {
                Text(text = text, style = style, color = color, maxLines = 1, softWrap = false)
            }.first().measure(unbounded)
        } else {
            null
        }

        layout(containerWidth, first.height) {
            val dx = if (spills) scroll.value.roundToInt() else 0
            first.placeRelative(dx, 0)
            second?.placeRelative(dx + first.width + gapPx, 0)
        }
    }
}

@Composable
internal fun NestExplicitBadge(color: Color, modifier: Modifier = Modifier) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(16.dp)
            .border(1.dp, color.copy(alpha = 0.65f), RoundedCornerShape(3.dp)),
    ) {
        Text(
            text = "E",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            lineHeight = 10.sp,
            color = color.copy(alpha = 0.85f),
        )
    }
}

/**
 * Nest Music `PortraitCredits`:
 * - Left: Bold 22sp Title + `ExplicitBadge`, and 17sp Artist (`0.73f` alpha, staggered marquee)
 * - Right: Bare `PlayerStar` / `PlayerStarFilled` favorite toggle + `34.dp` frosted `CircleGlyph` `PlayerEllipsis` menu
 * - Shifts smoothly right by `THUMB_SIZE + 14.dp` (`80.dp`) when `collapseProgress` animates `0 -> 1`
 *   so the collapsed 66dp sleeve thumbnail lands cleanly in the top-left header slot!
 */
@Composable
internal fun NestPortraitCredits(
    mediaMetadata: MediaMetadata,
    liked: Boolean,
    collapseProgress: Float,
    onToggleLike: () -> Unit,
    onTitleClick: () -> Unit,
    onArtistClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LocalPlayerInk.current.value
    val glow = LocalPlayerInkGlow.current.value
    val alphas = LocalPlayerAlphas.current
    val artistText = remember(mediaMetadata.artists) {
        mediaMetadata.artists.joinToString(", ") { it.name }
    }

    val startInset = lerp(0.dp, NEST_THUMB_SIZE + 14.dp, collapseProgress.coerceIn(0f, 1f))
    val titleScale = lerp(1f, 0.86f, collapseProgress.coerceIn(0f, 1f))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = startInset),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NestMarqueeText(
                    text = mediaMetadata.title,
                    style = TextStyle(
                        fontSize = (CREDITS_TITLE_SIZE.value * titleScale).sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                        shadow = Shadow(glow, blurRadius = 8f),
                    ),
                    color = ink.copy(alpha = alphas.primary),
                    onClick = onTitleClick,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (mediaMetadata.explicit) {
                    Spacer(Modifier.width(6.dp))
                    NestExplicitBadge(color = ink)
                }
            }
            if (artistText.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                NestMarqueeText(
                    text = artistText,
                    style = TextStyle(
                        fontSize = (CREDITS_ARTIST_SIZE.value * lerp(1f, 0.90f, collapseProgress)).sp,
                        fontWeight = FontWeight.Medium,
                        shadow = Shadow(glow, blurRadius = 8f),
                    ),
                    color = ink.copy(alpha = CREDITS_ARTIST_ALPHA),
                    initialDelayMs = MARQUEE_ARTIST_STAGGER_MS,
                    onClick = onArtistClick,
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        // Favorite star (bare glyph, matching Nest Music Apple Music Now Playing)
        NestTactileGlyph(
            onClick = onToggleLike,
            touchSize = 38.dp,
        ) {
            Icon(
                imageVector = if (liked) NestPlayerIcons.PlayerStarFilled else NestPlayerIcons.PlayerStar,
                contentDescription = null,
                tint = if (liked) ink else ink.copy(alpha = alphas.secondary),
                modifier = Modifier.size(21.dp),
            )
        }

        Spacer(Modifier.width(6.dp))

        // 34dp frosted circle ellipsis menu button
        NestCircleGlyph(
            onClick = onMoreClick,
            discSize = 34.dp,
            touchSize = 40.dp,
        ) {
            Icon(
                imageVector = NestPlayerIcons.PlayerEllipsis,
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/**
 * Nest Music morphing Album Sleeve:
 * - At `collapseProgress = 0f`: Centered square card (`RoundedCornerShape(14.dp)`),
 *   paused spring scale (`0.84f` paused -> `1.0f` playing), deep ambient shadow,
 *   horizontal drag-to-skip with elastic snapback.
 * - At `collapseProgress = 1f`: Smoothly shrinks & translates into the `66.dp` `8.dp`-rounded
 *   collapsed header thumbnail at `(x = 0, y = headerTop)`, tapping it closes Lyrics/Queue!
 */
@Composable
internal fun NestMorphingArtwork(
    thumbnailUrl: String?,
    isPlaying: Boolean,
    collapseProgress: Float,
    expandedSize: Dp,
    expandedTop: Dp,
    expandedLeft: Dp,
    collapsedTop: Dp,
    collapsedLeft: Dp,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onCollapsedTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val p = collapseProgress.coerceIn(0f, 1f)
    val playScale by animateFloatAsState(
        targetValue = if (isPlaying || p > 0.05f) 1f else 0.845f,
        animationSpec = spring(
            dampingRatio = 0.68f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "nestSleevePlayScale",
    )

    val currentSize = lerp(expandedSize, NEST_THUMB_SIZE, p)
    val currentLeft = lerp(expandedLeft, collapsedLeft, p)
    val currentTop = lerp(expandedTop, collapsedTop, p)
    val cornerRadius = lerp(NEST_ART_CORNER, NEST_THUMB_CORNER, p)
    val shadowElev = lerp(if (isPlaying) 28.dp else 12.dp, 6.dp, p)

    val scope = rememberCoroutineScope()
    val dragX = remember { Animatable(0f) }
    val haptics = LocalHapticFeedback.current

    val shape = RoundedCornerShape(cornerRadius)

    Box(
        modifier = modifier
            .offset(x = currentLeft, y = currentTop)
            .size(currentSize)
            .graphicsLayer {
                val s = lerp(playScale, 1f, p)
                scaleX = s
                scaleY = s
                translationX = dragX.value * (1f - p)
            }
            .shadow(
                elevation = shadowElev,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.50f),
                spotColor = Color.Black.copy(alpha = 0.65f),
            )
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .then(
                if (p > 0.6f) {
                    Modifier.clickable(onClick = onCollapsedTap)
                } else {
                    Modifier.pointerInput(canSkipPrevious, canSkipNext) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                val dx = dragX.value
                                val threshold = size.width * 0.24f
                                when {
                                    dx > threshold && canSkipPrevious -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSkipPrevious()
                                    }
                                    dx < -threshold && canSkipNext -> {
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        onSkipNext()
                                    }
                                }
                                scope.launch {
                                    dragX.animateTo(
                                        0f,
                                        spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMediumLow,
                                        ),
                                    )
                                }
                            },
                            onDragCancel = {
                                scope.launch { dragX.animateTo(0f, spring()) }
                            },
                            onHorizontalDrag = { change, dragAmount ->
                                change.consume()
                                scope.launch {
                                    dragX.snapTo((dragX.value + dragAmount * 0.65f).coerceIn(-180f, 180f))
                                }
                            },
                        )
                    }
                },
            ),
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

/**
 * Nest Music `QualityBadgePill` between timestamps (Lossless / Hi-Res / Dolby / High Quality).
 */
@Composable
internal fun NestAudioQualityBadge(
    label: String?,
    isLossless: Boolean = false,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    if (label.isNullOrBlank()) return
    val ink = LocalPlayerInk.current.value
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
            .clip(RoundedCornerShape(5.dp))
            .background(ink.copy(alpha = 0.14f))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 7.dp, vertical = 2.5.dp),
    ) {
        if (isLossless) {
            Icon(
                painter = painterResource(R.drawable.nest_ic_lossless),
                contentDescription = null,
                tint = ink.copy(alpha = 0.82f),
                modifier = Modifier.height(8.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.3.sp,
            ),
            color = ink.copy(alpha = 0.82f),
            maxLines = 1,
        )
    }
}

/**
 * Nest Music `TransportRow`:
 * - Previous (`53.dp` touch, `51.dp` x `0.80f` `TransportShape.PREVIOUS`)
 * - Play / Pause (`92.dp` touch, `48.dp` morphing `TransportShape.PLAY` <-> `PAUSE`)
 * - Next (`53.dp` touch, `51.dp` x `0.80f` `TransportShape.NEXT`)
 * spaced at `TRANSPORT_SPACING = 15.dp`.
 */
@Composable
internal fun NestTransportRow(
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val ink = LocalPlayerInk.current.value
    val pauseFraction by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "nestPlayPauseMorph",
    )

    val centerSize = if (compact) 42.dp else 48.dp
    val skipSize = if (compact) 44.dp else PLAYER_SKIP_ICON_SIZE

    Row(
        horizontalArrangement = Arrangement.spacedBy(TRANSPORT_SPACING, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth(),
    ) {
        NestTactileGlyph(
            onClick = onSkipPrevious,
            enabled = canSkipPrevious,
            touchSize = 56.dp,
        ) {
            TransportShapeGlyph(
                shape = TransportShape.PREVIOUS,
                tint = ink.copy(alpha = if (canSkipPrevious) 0.94f else 0.36f),
                contentDescription = null,
                modifier = Modifier
                    .size(skipSize)
                    .graphicsLayer { scaleY = PLAYER_SKIP_ICON_SCALE_Y },
            )
        }

        NestTactileGlyph(
            onClick = onPlayPause,
            touchSize = if (compact) 76.dp else 92.dp,
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(centerSize)
                    .graphicsLayer {
                        scaleX = 0.90f + 0.10f * pauseFraction
                        scaleY = 0.90f + 0.10f * pauseFraction
                    },
            ) {
                TransportShapeGlyph(
                    shape = if (isPlaying) TransportShape.PAUSE else TransportShape.PLAY,
                    tint = ink.copy(alpha = 0.96f),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        NestTactileGlyph(
            onClick = onSkipNext,
            enabled = canSkipNext,
            touchSize = 56.dp,
        ) {
            TransportShapeGlyph(
                shape = TransportShape.NEXT,
                tint = ink.copy(alpha = if (canSkipNext) 0.94f else 0.36f),
                contentDescription = null,
                modifier = Modifier
                    .size(skipSize)
                    .graphicsLayer { scaleY = PLAYER_SKIP_ICON_SCALE_Y },
            )
        }
    }
}

/**
 * Nest Music `VolumeRow` wired directly to Android `AudioManager.STREAM_MUSIC`
 * with live hardware volume-button broadcast listening.
 */
@Composable
internal fun NestVolumeRow(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val ink = LocalPlayerInk.current.value
    val alphas = LocalPlayerAlphas.current
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val maxVol = remember(audioManager) {
        audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }
    var volumeFraction by remember {
        mutableFloatStateOf(
            audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVol,
        )
    }

    DisposableEffect(context, audioManager, maxVol) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context?, intent: Intent?) {
                val cur = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                volumeFraction = (cur.toFloat() / maxVol).coerceIn(0f, 1f)
            }
        }
        try {
            context.registerReceiver(receiver, IntentFilter("android.media.VOLUME_CHANGED_ACTION"))
        } catch (_: Throwable) {
        }
        onDispose {
            try {
                context.unregisterReceiver(receiver)
            } catch (_: Throwable) {
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = NestPlayerIcons.PlayerVolumeLow,
            contentDescription = null,
            tint = ink.copy(alpha = alphas.tertiary),
            modifier = Modifier.size(16.dp),
        )
        NestThinSlider(
            value = volumeFraction,
            onValueChange = { next ->
                volumeFraction = next
                val targetStep = (next * maxVol).roundToInt().coerceIn(0, maxVol)
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStep, 0)
            },
            activeColor = ink.copy(alpha = VOLUME_BAR_OPACITY + 0.26f),
            inactiveColor = ink.copy(alpha = 0.20f),
            restTrackHeight = 5.dp,
            draggingTrackHeight = 9.dp,
            touchHeight = 24.dp,
            relativeDrag = true,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = NestPlayerIcons.PlayerVolumeHigh,
            contentDescription = null,
            tint = ink.copy(alpha = alphas.tertiary),
            modifier = Modifier.size(18.dp),
        )
    }
}

/**
 * Nest Music `PlayerActionRow`:
 * - Left: Lyrics `BottomGlyph` (with `35.dp` `7.dp`-rounded `BlendMode.SrcOut` cut-out active tile!)
 * - Center: 3-segment frosted `Pill` capsule joining `Shuffle` | `Repeat` (with `1` badge on Repeat-One) | `Autoplay`
 *   separated by `1.dp` `PillDivider`s (or AirPlay / Audio Output button when Queue is open)
 * - Right: Mode-badged `Queue` `BottomGlyph` (`PlayerQueue` / `PlayerQueueShuffle` / `PlayerQueueRepeat` / `PlayerQueueAutoplay`
 *   with `BlendMode.SrcOut` cut-out active tile when Queue is open!).
 */
@Composable
internal fun NestPlayerActionRow(
    lyricsOpen: Boolean,
    queueOpen: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: Int,
    autoplayEnabled: Boolean,
    onToggleLyrics: () -> Unit,
    onToggleQueue: () -> Unit,
    onToggleShuffle: () -> Unit,
    onToggleRepeat: () -> Unit,
    onToggleAutoplay: () -> Unit,
    onAirPlayClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LocalPlayerInk.current.value

    val queueIcon = when {
        repeatMode != Player.REPEAT_MODE_OFF -> NestPlayerIcons.PlayerQueueRepeat
        shuffleEnabled -> NestPlayerIcons.PlayerQueueShuffle
        autoplayEnabled -> NestPlayerIcons.PlayerQueueAutoplay
        else -> NestPlayerIcons.PlayerQueue
    }

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        NestBottomGlyph(
            icon = NestPlayerIcons.PlayerLyrics,
            active = lyricsOpen,
            contentDescription = stringResource(R.string.open_lyrics),
            onClick = onToggleLyrics,
        )

        // Center 3-segment mode pill (Shuffle | Repeat | Autoplay) matching Nest Music PlayerControls.kt
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .height(36.dp)
                .clip(CircleShape)
                .background(ink.copy(alpha = 0.14f)),
        ) {
            NestPillSegment(
                active = shuffleEnabled,
                onClick = onToggleShuffle,
            ) { tint ->
                Icon(
                    imageVector = NestPlayerIcons.PlayerShuffle,
                    contentDescription = stringResource(R.string.shuffle),
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(ink.copy(alpha = 0.14f)),
            )
            NestPillSegment(
                active = repeatMode != Player.REPEAT_MODE_OFF,
                onClick = onToggleRepeat,
            ) { tint ->
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = NestPlayerIcons.PlayerRepeat,
                        contentDescription = stringResource(R.string.repeat),
                        tint = tint,
                        modifier = Modifier.size(18.dp),
                    )
                    if (repeatMode == Player.REPEAT_MODE_ONE) {
                        Text(
                            text = "1",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = tint,
                        )
                    }
                }
            }
            Box(
                Modifier
                    .width(1.dp)
                    .height(18.dp)
                    .background(ink.copy(alpha = 0.14f)),
            )
            NestPillSegment(
                active = autoplayEnabled,
                onClick = onToggleAutoplay,
            ) { tint ->
                Icon(
                    imageVector = NestPlayerIcons.PlayerAutoplay,
                    contentDescription = stringResource(R.string.autoplay),
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            NestBottomGlyph(
                icon = NestPlayerIcons.PlayerAirPlay,
                active = false,
                contentDescription = stringResource(R.string.airplay),
                onClick = onAirPlayClick,
            )

            NestBottomGlyph(
                icon = queueIcon,
                active = queueOpen,
                contentDescription = stringResource(R.string.queue),
                onClick = onToggleQueue,
            )
        }
    }
}

@Composable
private fun NestPillSegment(
    active: Boolean,
    onClick: () -> Unit,
    content: @Composable (Color) -> Unit,
) {
    val ink = LocalPlayerInk.current.value
    val haptics = LocalHapticFeedback.current
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .width(48.dp)
            .height(36.dp)
            .background(if (active) ink.copy(alpha = 0.86f) else Color.Transparent)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            },
    ) {
        content(if (active) Color(0xFF141418) else ink.copy(alpha = 0.78f))
    }
}

/**
 * Nest Music `BottomGlyph`:
 * - When `active == true`, draws a `35.dp` `7.dp`-rounded luminous tile (`0.62f` alpha)
 *   and punches the icon out via `BlendMode.SrcOut` inside `CompositingStrategy.Offscreen`
 *   so the live blurred artwork backdrop shines directly through the glyph!
 */
@Composable
private fun NestBottomGlyph(
    icon: ImageVector,
    active: Boolean,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = LocalPlayerInk.current.value
    val alphas = LocalPlayerAlphas.current

    NestTactileGlyph(
        onClick = onClick,
        touchSize = BOTTOM_ACTION_SIZE,
        modifier = modifier,
    ) {
        if (active) {
            val tileCorner = BOTTOM_ACTIVE_TILE_CORNER
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(BOTTOM_ACTIVE_TILE_SIZE)
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                    .drawWithContent {
                        drawContent()
                        drawRoundRect(
                            color = ink.copy(alpha = BOTTOM_ACTIVE_TILE_ALPHA),
                            cornerRadius = CornerRadius(tileCorner.toPx(), tileCorner.toPx()),
                            blendMode = BlendMode.SrcOut,
                        )
                    },
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = contentDescription,
                    tint = Color.Black,
                    modifier = Modifier.size(BOTTOM_ICON_SIZE - 4.dp),
                )
            }
        } else {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = ink.copy(alpha = alphas.tertiary),
                modifier = Modifier.size(BOTTOM_ICON_SIZE - 3.dp),
            )
        }
    }
}

@Composable
internal fun NestCircleGlyph(
    onClick: () -> Unit,
    discSize: Dp = 34.dp,
    touchSize: Dp = 40.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val ink = LocalPlayerInk.current.value
    val alphas = LocalPlayerAlphas.current
    NestTactileGlyph(
        onClick = onClick,
        touchSize = touchSize,
        modifier = modifier,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(discSize)
                .clip(CircleShape)
                .background(ink.copy(alpha = alphas.buttonFill)),
        ) {
            content()
        }
    }
}

@Composable
internal fun NestTactileGlyph(
    onClick: () -> Unit,
    enabled: Boolean = true,
    touchSize: Dp = 44.dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.86f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "nestGlyphPressScale",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(touchSize)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = enabled,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    onClick()
                },
            ),
    ) {
        content()
    }
}
