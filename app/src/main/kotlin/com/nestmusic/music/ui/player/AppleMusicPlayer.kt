/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 *
 * Nest Music Apple Music Now Playing screen (ported from Nest Music under GPL-3.0),
 * integrated natively with Nest Music's PlayerConnection, BottomSheet, LyricsViewModel, and Queue.
 */

package com.nestmusic.music.ui.player

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.navigation.NavController
import com.nestmusic.music.LocalPlayerConnection
import com.nestmusic.music.R
import com.nestmusic.music.constants.KeepScreenOn
import com.nestmusic.music.constants.SimilarContent
import com.nestmusic.music.ui.component.BottomSheet
import com.nestmusic.music.ui.component.BottomSheetState
import com.nestmusic.music.ui.component.EnsureCurrentLyricsLoaded
import com.nestmusic.music.ui.component.LocalBottomSheetPageState
import com.nestmusic.music.ui.component.LocalMenuState
import com.nestmusic.music.ui.component.glassBackdrop
import com.nestmusic.music.ui.component.rememberGlassBackdrop
import com.nestmusic.music.ui.menu.PlayerMenu
import com.nestmusic.music.ui.player.nest.NEST_GUTTER
import com.nestmusic.music.ui.player.nest.NEST_HEADER_HEIGHT
import com.nestmusic.music.ui.player.nest.NestAudioQualityBadge
import com.nestmusic.music.ui.player.nest.NestCurrentLyricStrip
import com.nestmusic.music.ui.player.nest.NestLyricsPanel
import com.nestmusic.music.ui.player.nest.NestMorphingArtwork
import com.nestmusic.music.ui.player.nest.NestPlayerActionRow
import com.nestmusic.music.ui.player.nest.NestPlayerTopStrip
import com.nestmusic.music.ui.player.nest.NestPortraitBackdrop
import com.nestmusic.music.ui.player.nest.NestPortraitCredits
import com.nestmusic.music.ui.player.nest.NestQueuePanel
import com.nestmusic.music.ui.player.nest.NestThinSlider
import com.nestmusic.music.ui.player.nest.NestTransportRow
import com.nestmusic.music.ui.player.nest.NestVolumeRow
import com.nestmusic.music.ui.player.nest.LocalPlayerAlphas
import com.nestmusic.music.ui.player.nest.LocalPlayerInk
import com.nestmusic.music.ui.player.nest.LocalPlayerInkGlow
import com.nestmusic.music.ui.player.nest.NowPlayingAlphas
import com.nestmusic.music.ui.player.nest.rememberNestLyricsSession
import com.nestmusic.music.ui.player.nest.rememberNestPalette
import com.nestmusic.music.ui.utils.ShowMediaInfo
import com.nestmusic.music.utils.makeTimeString
import com.nestmusic.music.utils.rememberPreference
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.roundToLong

private enum class NestPlayerPanel {
    NONE,
    LYRICS,
    QUEUE,
}

private val IOS_PANEL_EASING = CubicBezierEasing(0.32f, 0.72f, 0f, 1f)

@Composable
fun AppleMusicPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val currentFormat by playerConnection.currentFormat.collectAsStateWithLifecycle(initialValue = null)
    val queueTitle by playerConnection.queueTitle.collectAsStateWithLifecycle()
    val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext by playerConnection.canSkipNext.collectAsStateWithLifecycle()
    val playbackError by playerConnection.error.collectAsStateWithLifecycle()

    val (autoplayEnabled, onAutoplayChange) = rememberPreference(SimilarContent, defaultValue = true)
    val isKeepScreenOn by rememberPreference(KeepScreenOn, defaultValue = false)

    val positionState = remember {
        mutableLongStateOf(runCatching { playerConnection.player.currentPosition }.getOrDefault(0L))
    }
    val durationState = remember { mutableLongStateOf(0L) }
    var position by positionState
    var duration by durationState
    var sliderPosition by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(mediaMetadata?.id, isPlaying) {
        while (isActive) {
            if (sliderPosition == null) {
                position = runCatching { playerConnection.player.currentPosition }.getOrDefault(0L)
                duration =
                    (mediaMetadata?.duration?.takeIf { it > 0 }?.toLong()?.times(1000L))
                        ?: runCatching { playerConnection.player.duration }.getOrDefault(0L).coerceAtLeast(0L)
            }
            delay(if (isPlaying) 180L else 900L)
        }
    }

    var activePanel by rememberSaveable { mutableStateOf(NestPlayerPanel.NONE) }
    val panelOpen = activePanel != NestPlayerPanel.NONE

    // Ensure uncached lyrics are fetched automatically for both the live strip above the scrubber
    // and the full Nest Music lyrics panel (preserving Nest Music's LyricsFetchPolicy).
    EnsureCurrentLyricsLoaded(enabled = state.isExpanded || activePanel == NestPlayerPanel.LYRICS)

    val lyricsSession = rememberNestLyricsSession(
        sliderPositionProvider = { sliderPosition },
        activeForTranslation = state.isExpanded && activePanel == NestPlayerPanel.LYRICS,
    )

    // White status-bar icons while Apple Music Now Playing is expanded
    val keepScreenOn = isPlaying && isKeepScreenOn
    DisposableEffect(state.isExpanded, keepScreenOn) {
        val window = (context as? Activity)?.window
        if (window != null && state.isExpanded) {
            WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
            if (keepScreenOn) {
                window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            } else {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    BackHandler(enabled = state.isExpanded) {
        if (activePanel != NestPlayerPanel.NONE) {
            activePanel = NestPlayerPanel.NONE
        } else {
            state.collapseSoft()
        }
    }

    val palette = rememberNestPalette(
        imageUrl = mediaMetadata?.thumbnailUrl,
        darkTheme = true,
    )
    val inkState = rememberUpdatedState(palette.ink)
    val glowState = rememberUpdatedState(palette.inkGlow)
    val alphas = remember { NowPlayingAlphas() }
    val sheetBackdrop = rememberGlassBackdrop()

    val collapseProgress by animateFloatAsState(
        targetValue = if (panelOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = IOS_PANEL_EASING),
        label = "nestHeaderCollapseProgress",
    )

    val qualityInfo = remember(currentFormat) {
        val fmt = currentFormat ?: return@remember null
        val mime = fmt.mimeType.lowercase()
        val kbps = fmt.bitrate / 1000
        when {
            "flac" in mime || "alac" in mime || "wav" in mime -> {
                if ((fmt.sampleRate ?: 0) > 48_000) "Hi-Res Lossless" to true else "Lossless" to true
            }
            kbps >= 256 -> "High Quality" to false
            kbps > 0 -> "${kbps} kbps" to false
            else -> null
        }
    }

    val playingFromTitle = remember(queueTitle, mediaMetadata) {
        queueTitle?.takeIf { it.isNotBlank() }
            ?: mediaMetadata?.album?.title?.takeIf { it.isNotBlank() }
            ?: mediaMetadata?.artists?.firstOrNull()?.name
    }

    CompositionLocalProvider(
        LocalPlayerInk provides inkState,
        LocalPlayerInkGlow provides glowState,
        LocalPlayerAlphas provides alphas,
    ) {
        BottomSheet(
            state = state,
            modifier = modifier,
            background = {
                NestPortraitBackdrop(
                    imageUrl = mediaMetadata?.thumbnailUrl,
                    palette = palette,
                    trackKey = mediaMetadata?.id,
                    panelOpen = panelOpen,
                    darkTheme = true,
                    modifier = Modifier
                        .fillMaxSize()
                        .glassBackdrop(sheetBackdrop),
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
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding(),
            ) {
                NestPlayerTopStrip(
                    queueTitle = playingFromTitle,
                    onCollapse = { state.collapseSoft() },
                )

                if (metadata == null) {
                    Spacer(Modifier.weight(1f))
                    return@Column
                }

                // Upper morphing stage: houses the Sleeve <-> Collapsed Header + Credits + Lyrics/Queue body
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    val stageWidth = maxWidth
                    val stageHeight = maxHeight

                    // Reserve ~64dp at the bottom of the upper stage for PortraitCredits when expanded
                    val creditsBlockHeight = 62.dp
                    val maxArtFromWidth = (stageWidth - NEST_GUTTER * 2).coerceAtLeast(120.dp)
                    val maxArtFromHeight = (stageHeight - creditsBlockHeight - 24.dp).coerceAtLeast(120.dp)
                    val expandedArtSize = min(maxArtFromWidth, maxArtFromHeight)
                    val expandedArtLeft = (stageWidth - expandedArtSize) / 2f
                    val expandedArtTop = ((stageHeight - creditsBlockHeight - expandedArtSize) / 2f)
                        .coerceAtLeast(4.dp)

                    val collapsedArtLeft = NEST_GUTTER
                    val collapsedArtTop = 4.dp

                    val expandedCreditsTop = (expandedArtTop + expandedArtSize + 14.dp)
                        .coerceAtMost((stageHeight - creditsBlockHeight).coerceAtLeast(0.dp))
                    val collapsedCreditsTop = 8.dp
                    val currentCreditsTop = lerp(expandedCreditsTop, collapsedCreditsTop, collapseProgress)

                    val currentError = playbackError
                    if (currentError != null && !panelOpen) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(expandedArtSize)
                                .offset(y = expandedArtTop)
                                .padding(horizontal = NEST_GUTTER),
                        ) {
                            PlaybackError(
                                error = currentError,
                                retry = { runCatching { playerConnection.player.prepare() } },
                            )
                        }
                    } else {
                        NestMorphingArtwork(
                            thumbnailUrl = metadata.thumbnailUrl,
                            isPlaying = isPlaying,
                            collapseProgress = collapseProgress,
                            expandedSize = expandedArtSize,
                            expandedTop = expandedArtTop,
                            expandedLeft = expandedArtLeft,
                            collapsedTop = collapsedArtTop,
                            collapsedLeft = collapsedArtLeft,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            onSkipPrevious = { playerConnection.seekToPrevious() },
                            onSkipNext = { playerConnection.seekToNext() },
                            onCollapsedTap = { activePanel = NestPlayerPanel.NONE },
                            modifier = Modifier.zIndex(2f),
                        )
                    }

                    // Credits row: sits under the full artwork when expanded, and glides up next to the
                    // 66dp thumbnail in the collapsed header when Lyrics or Queue opens!
                    val isLiked = currentSong?.song?.liked ?: metadata.liked
                    NestPortraitCredits(
                        mediaMetadata = metadata,
                        liked = isLiked,
                        collapseProgress = collapseProgress,
                        onToggleLike = { runCatching { playerConnection.toggleLike() } },
                        onTitleClick = {
                            val albumId = metadata.album?.id
                            if (!albumId.isNullOrBlank()) {
                                state.collapseSoft()
                                navController.navigate("album/$albumId")
                            }
                        },
                        onArtistClick = {
                            val artistId = metadata.artists.firstOrNull()?.id
                            if (!artistId.isNullOrBlank()) {
                                state.collapseSoft()
                                navController.navigate("artist/$artistId")
                            }
                        },
                        onMoreClick = {
                            menuState.show {
                                PlayerMenu(
                                    mediaMetadata = metadata,
                                    playerBottomSheetState = state,
                                    onShowDetailsDialog = {
                                        bottomSheetPageState.show {
                                            ShowMediaInfo(metadata.id)
                                        }
                                    },
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        modifier = Modifier
                            .offset(y = currentCreditsTop)
                            .padding(horizontal = NEST_GUTTER)
                            .zIndex(2f),
                    )

                    // Inline Lyrics / Queue body occupying the stage below the 72dp collapsed header
                    if (collapseProgress > 0.02f) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = NEST_HEADER_HEIGHT + 6.dp)
                                .graphicsLayer {
                                    alpha = ((collapseProgress - 0.15f) / 0.85f).coerceIn(0f, 1f)
                                    translationY = (1f - collapseProgress) * 32.dp.toPx()
                                }
                                .zIndex(1f),
                        ) {
                            when (activePanel) {
                                NestPlayerPanel.LYRICS -> {
                                    if (lyricsSession != null) {
                                        NestLyricsPanel(
                                            lines = lyricsSession.lines,
                                            rawEntries = lyricsSession.rawEntries,
                                            clock = lyricsSession.clock,
                                            isSynced = lyricsSession.isSynced,
                                            isLoading = lyricsSession.isLoading,
                                            lyricsUnavailable = lyricsSession.lyricsUnavailable,
                                            showRomanized = lyricsSession.showRomanized,
                                            showTranslated = lyricsSession.showTranslated,
                                            onToggleRomanized = lyricsSession.onToggleRomanized,
                                            onToggleTranslated = lyricsSession.onToggleTranslated,
                                            onSeekToMs = lyricsSession.onSeekToMs,
                                            onLineLongPress = { lyricsSession.onOpenLyricsMenu() },
                                            bottomPadding = 12.dp,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                }
                                NestPlayerPanel.QUEUE -> {
                                    NestQueuePanel(
                                        playerBottomSheetState = state,
                                        bottomPadding = 12.dp,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                                NestPlayerPanel.NONE -> Unit
                            }
                        }
                    }
                }

                // Bottom Now Playing Deck (Lyric Strip + ThinSlider + Timestamps/Badge + Transport + Volume + Action Row)
                val ink = LocalPlayerInk.current.value
                val effectivePosition = sliderPosition ?: position
                val safeDuration = duration.coerceAtLeast(1L)
                val sliderFraction = if (duration > 0L) {
                    (effectivePosition.toFloat() / safeDuration).coerceIn(0f, 1f)
                } else {
                    0f
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = NEST_GUTTER),
                ) {
                    // Single-line word-swept lyric preview directly above the scrubber (when main cover is shown)
                    AnimatedVisibility(
                        visible = !panelOpen && lyricsSession != null,
                        enter = fadeIn(tween(240)) + expandVertically(tween(260)),
                        exit = fadeOut(tween(180)) + shrinkVertically(tween(220)),
                    ) {
                        if (lyricsSession != null) {
                            NestCurrentLyricStrip(
                                lines = lyricsSession.lines,
                                clock = lyricsSession.clock,
                                isSynced = lyricsSession.isSynced,
                                isLoading = lyricsSession.isLoading,
                                lyricsUnavailable = lyricsSession.lyricsUnavailable,
                                onClick = { activePanel = NestPlayerPanel.LYRICS },
                            )
                        }
                    }

                    // Hairline Apple Music capsule scrubber (7dp -> 12dp spring + MixSheen while buffering)
                    NestThinSlider(
                        value = sliderFraction,
                        onValueChange = { fraction ->
                            if (duration > 0L) {
                                sliderPosition = (fraction * duration).roundToLong()
                            }
                        },
                        onValueChangeFinished = {
                            sliderPosition?.let { target ->
                                runCatching { playerConnection.player.seekTo(target) }
                                position = target
                            }
                            sliderPosition = null
                        },
                        isLoading = playbackState == Player.STATE_BUFFERING,
                        activeColor = ink.copy(alpha = 0.84f),
                        inactiveColor = ink.copy(alpha = 0.24f),
                        relativeDrag = true,
                    )

                    // Timestamps + centered Lossless / Quality badge
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset(y = (-4).dp),
                    ) {
                        Text(
                            text = makeTimeString(effectivePosition),
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = ink.copy(alpha = 0.52f),
                        )

                        NestAudioQualityBadge(
                            label = qualityInfo?.first,
                            isLossless = qualityInfo?.second == true,
                            onClick = {
                                bottomSheetPageState.show {
                                    ShowMediaInfo(metadata.id)
                                }
                            },
                        )

                        Text(
                            text = if (duration > 0L) {
                                "-${makeTimeString((duration - effectivePosition).coerceAtLeast(0L))}"
                            } else {
                                "-0:00"
                            },
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = ink.copy(alpha = 0.52f),
                        )
                    }

                    Spacer(Modifier.height(if (panelOpen) 2.dp else 8.dp))

                    NestTransportRow(
                        isPlaying = isPlaying,
                        canSkipPrevious = canSkipPrevious,
                        canSkipNext = canSkipNext,
                        onPlayPause = { playerConnection.togglePlayPause() },
                        onSkipPrevious = { playerConnection.seekToPrevious() },
                        onSkipNext = { playerConnection.seekToNext() },
                        compact = panelOpen,
                    )

                    // Volume row hides smoothly when Lyrics/Queue opens, giving full vertical room to lyrics
                    AnimatedVisibility(
                        visible = !panelOpen,
                        enter = fadeIn(tween(240)) + expandVertically(tween(260)),
                        exit = fadeOut(tween(180)) + shrinkVertically(tween(220)),
                    ) {
                        Column {
                            Spacer(Modifier.height(8.dp))
                            NestVolumeRow()
                        }
                    }

                    Spacer(Modifier.height(if (panelOpen) 4.dp else 8.dp))

                    NestPlayerActionRow(
                        lyricsOpen = activePanel == NestPlayerPanel.LYRICS,
                        queueOpen = activePanel == NestPlayerPanel.QUEUE,
                        shuffleEnabled = shuffleModeEnabled,
                        repeatMode = repeatMode,
                        autoplayEnabled = autoplayEnabled,
                        onToggleLyrics = {
                            activePanel = if (activePanel == NestPlayerPanel.LYRICS) {
                                NestPlayerPanel.NONE
                            } else {
                                NestPlayerPanel.LYRICS
                            }
                        },
                        onToggleQueue = {
                            activePanel = if (activePanel == NestPlayerPanel.QUEUE) {
                                NestPlayerPanel.NONE
                            } else {
                                NestPlayerPanel.QUEUE
                            }
                        },
                        onToggleShuffle = {
                            val next = !shuffleModeEnabled
                            runCatching { playerConnection.player.shuffleModeEnabled = next }
                        },
                        onToggleRepeat = {
                            val next = when (repeatMode) {
                                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                                else -> Player.REPEAT_MODE_OFF
                            }
                            runCatching { playerConnection.player.repeatMode = next }
                        },
                        onToggleAutoplay = {
                            onAutoplayChange(!autoplayEnabled)
                        },
                        onAirPlayClick = {
                            runCatching {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                    val panelIntent = Intent("com.android.settings.panel.action.MEDIA_OUTPUT").apply {
                                        putExtra("com.android.settings.panel.extra.PACKAGE_NAME", context.packageName)
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                    context.startActivity(panelIntent)
                                } else {
                                    context.startActivity(
                                        Intent(Settings.ACTION_BLUETOOTH_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                    )
                                }
                            }
                        },
                    )

                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}
