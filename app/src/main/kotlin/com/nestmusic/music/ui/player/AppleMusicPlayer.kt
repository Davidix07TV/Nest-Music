/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * The Apple Music-style Now Playing screen, ported from SimpMusic's NowPlayingContentAppleMusic.
 *
 * SimpMusic's version is built on its own KMP player handler, view model and mesh backdrop; this
 * one is rebuilt on Nest's player — the same connection, the same bottom sheet, the same menu —
 * so choosing the style swaps the *look* and nothing else. What carries over from the original is
 * the layout and the details that make it read as Apple:
 *
 *  - artwork on a tall stage, tapped to cross-fade into the lyrics and back,
 *  - the title row: title, artist, and the badge beside them,
 *  - a thin scrubber with the elapsed time under it and the remaining time at the far end,
 *  - plain transport controls, with the play/pause on a liquid-glass disc,
 *  - a glass pill of secondary actions at the bottom.
 */

package com.nestmusic.music.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nestmusic.music.LocalPlayerConnection
import com.nestmusic.music.R
import com.nestmusic.music.models.MediaMetadata
import com.nestmusic.music.ui.component.BottomSheet
import com.nestmusic.music.ui.component.BottomSheetState
import com.nestmusic.music.ui.component.GlassBackdrop
import com.nestmusic.music.ui.component.LiquidGlassContainer
import com.nestmusic.music.ui.component.LiquidGlassIconButton
import com.nestmusic.music.ui.component.Lyrics
import com.nestmusic.music.ui.component.glassBackdrop
import com.nestmusic.music.ui.component.liquidGlass
import com.nestmusic.music.ui.component.rememberGlassBackdrop
import com.nestmusic.music.ui.component.rememberGlassLuminance
import com.nestmusic.music.utils.makeTimeString
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/** The artwork's corner radius, matched to Apple's. */
private val AppleArtworkCorner = 12.dp

@Composable
fun AppleMusicPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val isMuted by playerConnection.isMuted.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()

    // State objects rather than plain values, so the mini player in the collapsed sheet can share
    // the same clock instead of running a second one.
    val positionState = remember {
        mutableLongStateOf(runCatching { playerConnection.player.currentPosition }.getOrDefault(0L))
    }
    val durationState = remember { mutableLongStateOf(0L) }
    var position by positionState
    var duration by durationState
    var sliderPosition by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(mediaMetadata?.id) {
        while (isActive) {
            if (sliderPosition == null) {
                position = runCatching { playerConnection.player.currentPosition }.getOrDefault(0L)
                duration =
                    (mediaMetadata?.duration?.takeIf { it > 0 }?.toLong()?.times(1000L))
                        ?: runCatching { playerConnection.player.duration }.getOrDefault(0L).coerceAtLeast(0L)
            }
            // A playing track needs the fast tick for a smooth scrubber; a paused one does not
            // move at all, and waking the main thread five times a second behind a static
            // slider is pure battery.
            delay(if (isPlaying) 200L else 1000L)
        }
    }

    var showLyrics by rememberSaveable { mutableStateOf(false) }

    // The glass samples the artwork behind the sheet. The source is the BACKGROUND and not the
    // sheet content on purpose: a glass surface must never be inside the layer it samples.
    val backdrop = rememberGlassBackdrop()
    rememberGlassLuminance(backdrop)

    BottomSheet(
        state = state,
        modifier = modifier,
        background = {
            AppleMusicBackdrop(
                metadata = mediaMetadata,
                backdrop = backdrop,
            )
        },
        collapsedContent = {
            MiniPlayer(
                positionState = positionState,
                durationState = durationState,
                onClick = { state.expandSoft() },
            )
        },
    ) {
        val metadata = mediaMetadata

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
        ) {
            AppleMusicTopRow(
                onCollapse = { state.collapseSoft() },
                metadata = metadata,
                navController = navController,
                state = state,
                backdrop = backdrop,
            )

            Spacer(modifier = Modifier.height(8.dp))

            // The artwork's stage. Tapping it is how you get to the lyrics and back, which is the
            // one interaction everyone already knows from Apple Music.
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Crossfade(
                    targetState = showLyrics,
                    animationSpec = tween(400),
                    label = "appleMusicArtworkOrLyrics",
                ) { lyricsVisible ->
                    if (lyricsVisible) {
                        Lyrics(
                            sliderPositionProvider = { sliderPosition },
                            modifier = Modifier.fillMaxSize(),
                            showLyrics = true,
                        )
                    } else {
                        AppleMusicArtwork(
                            metadata = metadata,
                            onClick = { showLyrics = true },
                        )
                    }
                }
            }

            if (metadata != null) {
                Spacer(modifier = Modifier.height(20.dp))
                AppleMusicTitleRow(metadata = metadata)

                Spacer(modifier = Modifier.height(4.dp))

                AppleMusicScrubber(
                    position = sliderPosition ?: position,
                    duration = duration,
                    onSeek = { sliderPosition = it },
                    onSeekFinished = {
                        sliderPosition?.let { target ->
                            runCatching { playerConnection.player.seekTo(target) }
                            position = target
                        }
                        sliderPosition = null
                    },
                )

                Spacer(modifier = Modifier.height(8.dp))

                AppleMusicTransport(
                    isPlaying = isPlaying,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    backdrop = backdrop,
                    onPrevious = { playerConnection.seekToPrevious() },
                    onNext = { playerConnection.seekToNext() },
                    onPlayPause = { playerConnection.togglePlayPause() },
                )

                Spacer(modifier = Modifier.height(12.dp))

                AppleMusicActionRow(
                    metadata = metadata,
                    backdrop = backdrop,
                    repeatMode = repeatMode,
                    isMuted = isMuted,
                    showLyrics = showLyrics,
                    onToggleLyrics = { showLyrics = !showLyrics },
                    onToggleLike = { runCatching { playerConnection.toggleLike() } },
                    onCycleRepeat = {
                        val next =
                            when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                        runCatching { playerConnection.player.setRepeatMode(next) }
                    },
                    onToggleMute = { playerConnection.toggleMute() },
                )

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

/**
 * Apple's background is a mesh: two or three wide radial pools of colour drawn from the artwork,
 * over a blurred copy of it. The blur is what keeps the glass above it legible, and the pools are
 * what stop a dark cover from turning the whole screen into a grey slab.
 */
@Composable
private fun AppleMusicBackdrop(
    metadata: MediaMetadata?,
    backdrop: GlassBackdrop,
) {
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
                .glassBackdrop(backdrop),
    ) {
        AnimatedContent(
            targetState = metadata?.thumbnailUrl,
            transitionSpec = { fadeIn(tween(800)) togetherWith fadeOut(tween(800)) },
            label = "appleMusicBackdropArtwork",
        ) { thumbnailUrl ->
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .blur(80.dp),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(primary.copy(alpha = 0.55f), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(0f, 0f),
                            radius = 1200f,
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(secondary.copy(alpha = 0.42f), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(900f, 2100f),
                            radius = 1400f,
                        ),
                    ),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(tertiary.copy(alpha = 0.3f), Color.Transparent),
                            center = androidx.compose.ui.geometry.Offset(300f, 1400f),
                            radius = 1000f,
                        ),
                    ),
        )
        // The wash that makes white text legible over any cover.
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.35f)),
        )
    }
}

@Composable
private fun AppleMusicTopRow(
    onCollapse: () -> Unit,
    metadata: MediaMetadata?,
    navController: NavController,
    state: BottomSheetState,
    backdrop: GlassBackdrop,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LiquidGlassIconButton(
            backdrop = backdrop,
            painter = painterResource(R.drawable.arrow_downward),
            contentDescription = stringResource(R.string.close),
            onClick = onCollapse,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (metadata != null) {
            MoreActionsButton(
                mediaMetadata = metadata,
                navController = navController,
                state = state,
                textButtonColor = Color.White.copy(alpha = 0.16f),
                iconButtonColor = Color.White,
                // Same liquid-glass disc as the collapse button beside it.
                backdrop = backdrop,
            )
        }
    }
}

@Composable
private fun AppleMusicArtwork(
    metadata: MediaMetadata?,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .shadow(24.dp, RoundedCornerShape(AppleArtworkCorner))
                .clip(RoundedCornerShape(AppleArtworkCorner))
                .background(Color.White.copy(alpha = 0.06f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    // The tap target is the whole cover, so the action has to be spelled out:
                    // the artwork alone announces the song, not what tapping it does.
                    onClickLabel = stringResource(R.string.lyrics),
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        if (metadata?.thumbnailUrl != null) {
            AsyncImage(
                model = metadata.thumbnailUrl,
                contentDescription = metadata.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                painter = painterResource(R.drawable.small_icon),
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

/**
 * Title, artist and the badge, on the Apple grid: the title on its own line, the artist under it,
 * both left aligned, with the explicit marker riding beside the title.
 */
@Composable
private fun AppleMusicTitleRow(metadata: MediaMetadata) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = metadata.title,
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = metadata.artists.joinToString { it.name },
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 18.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (metadata.explicit) {
            Spacer(modifier = Modifier.width(8.dp))
            ExplicitBadge()
        }
    }
}

/** The small grey "E" Apple prints next to explicit tracks. */
@Composable
private fun ExplicitBadge() {
    Box(
        modifier =
            Modifier
                .size(18.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.White.copy(alpha = 0.22f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "E",
            color = Color.White,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/**
 * The thin scrubber, with the times underneath — elapsed at the left, remaining at the right, which
 * is the one place Apple differs from the rest of the world: it counts the song down.
 */
@Composable
private fun AppleMusicScrubber(
    position: Long,
    duration: Long,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = position.coerceIn(0L, duration.coerceAtLeast(0L)).toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..(if (duration > 0L) duration.toFloat() else 1f),
            colors =
                SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.28f),
                ),
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 28.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = makeTimeString(position),
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
            )
            Text(
                text = if (duration > 0L) "-${makeTimeString(duration - position)}" else "",
                color = Color.White.copy(alpha = 0.72f),
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Plain transport controls in Apple's proportions: the two skips small and far apart, the
 * play/pause on a glass disc between them.
 */
@Composable
private fun AppleMusicTransport(
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    backdrop: GlassBackdrop,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPlayPause: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Previous / next sit in the same glass discs as play-pause so the whole transport row
        // reads as one set of controls. A disabled skip keeps its glass and only dims the icon.
        LiquidGlassContainer(
            backdrop = backdrop,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
        ) {
            IconButton(onClick = onPrevious, enabled = canSkipPrevious) {
                Icon(
                    painter = painterResource(R.drawable.skip_previous),
                    contentDescription = stringResource(R.string.previous),
                    tint = Color.White.copy(alpha = if (canSkipPrevious) 1f else 0.4f),
                    modifier = Modifier.size(30.dp),
                )
            }
        }

        LiquidGlassContainer(
            backdrop = backdrop,
            modifier = Modifier.size(72.dp),
            shape = CircleShape,
        ) {
            IconButton(onClick = onPlayPause) {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }
        }

        LiquidGlassContainer(
            backdrop = backdrop,
            modifier = Modifier.size(56.dp),
            shape = CircleShape,
        ) {
            IconButton(onClick = onNext, enabled = canSkipNext) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = stringResource(R.string.next),
                    tint = Color.White.copy(alpha = if (canSkipNext) 1f else 0.4f),
                    modifier = Modifier.size(30.dp),
                )
            }
        }
    }
}

@Composable
private fun AppleMusicActionRow(
    metadata: MediaMetadata,
    backdrop: GlassBackdrop,
    repeatMode: Int,
    isMuted: Boolean,
    showLyrics: Boolean,
    onToggleLyrics: () -> Unit,
    onToggleLike: () -> Unit,
    onCycleRepeat: () -> Unit,
    onToggleMute: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center,
    ) {
        // One glass pill for the whole row, exactly like the nav bar capsule: the actions sit on a
        // single pane instead of four separate discs.
        Row(
            modifier =
                Modifier
                    .liquidGlass(
                        backdrop = backdrop,
                        shape = RoundedCornerShape(28.dp),
                        blurRadius = 22.dp,
                    )
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onToggleLike) {
                Icon(
                    painter = painterResource(if (metadata.liked) R.drawable.favorite else R.drawable.favorite_border),
                    contentDescription =
                        stringResource(if (metadata.liked) R.string.action_remove_like else R.string.like),
                    tint = if (metadata.liked) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = onToggleLyrics) {
                Icon(
                    painter = painterResource(R.drawable.lyrics),
                    contentDescription = stringResource(R.string.lyrics),
                    tint = if (showLyrics) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    painter =
                        painterResource(
                            when (repeatMode) {
                                Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                else -> R.drawable.repeat
                            },
                        ),
                    contentDescription = stringResource(R.string.repeat),
                    tint = if (repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
            IconButton(onClick = onToggleMute) {
                Icon(
                    painter = painterResource(if (isMuted) R.drawable.volume_off else R.drawable.volume_up),
                    contentDescription = stringResource(if (isMuted) R.string.unmute else R.string.mute),
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
            }
        }
    }
}
