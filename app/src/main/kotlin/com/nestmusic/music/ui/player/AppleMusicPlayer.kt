package com.nestmusic.music.ui.player

import android.app.Activity
import android.graphics.Bitmap
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
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
import com.nestmusic.music.constants.AnimatedCanvasKey
import com.nestmusic.music.constants.CanvasOverCellularKey
import com.nestmusic.music.constants.DolbyAtmosKey
import com.nestmusic.music.constants.FullBleedArtworkKey
import com.nestmusic.music.constants.KeepScreenOn
import com.nestmusic.music.constants.SimilarContent
import com.nestmusic.music.constants.SpatialAudioKey
import com.nestmusic.music.playback.audio.NestAudioOutputKind
import com.nestmusic.music.playback.audio.NestAudioOutputStatus
import com.nestmusic.music.playback.audio.rememberNestAudioOutputs
import com.nestmusic.music.ui.component.BottomSheet
import com.nestmusic.music.ui.component.BottomSheetState
import com.nestmusic.music.ui.component.EnsureCurrentLyricsLoaded
import com.nestmusic.music.ui.component.LocalBottomSheetPageState
import com.nestmusic.music.ui.component.LocalMenuState
import com.nestmusic.music.ui.component.glassBackdrop
import com.nestmusic.music.ui.component.rememberGlassBackdrop
import com.nestmusic.music.ui.menu.PlayerMenu
import com.nestmusic.music.ui.player.nest.LocalPlayerAlphas
import com.nestmusic.music.ui.player.nest.LocalPlayerInk
import com.nestmusic.music.ui.player.nest.LocalPlayerInkGlow
import com.nestmusic.music.ui.player.nest.NEST_GUTTER
import com.nestmusic.music.ui.player.nest.NEST_HEADER_HEIGHT
import com.nestmusic.music.ui.player.nest.NestArtworkMeshBackdrop
import com.nestmusic.music.ui.player.nest.NestAudioOutputSheet
import com.nestmusic.music.ui.player.nest.NestAudioPipelineDialog
import com.nestmusic.music.ui.player.nest.NestAudioQualityBadge
import com.nestmusic.music.ui.player.nest.NestCurrentLyricStrip
import com.nestmusic.music.ui.player.nest.NestLyricsPanel
import com.nestmusic.music.ui.player.nest.NestMorphingArtwork
import com.nestmusic.music.ui.player.nest.NestPlayerActionRow
import com.nestmusic.music.ui.player.nest.NestPlayerTopStrip
import com.nestmusic.music.ui.player.nest.NestPortraitCredits
import com.nestmusic.music.ui.player.nest.NestQueuePanel
import com.nestmusic.music.ui.player.nest.NestThinSlider
import com.nestmusic.music.ui.player.nest.NestTransportRow
import com.nestmusic.music.ui.player.nest.NestVolumeRow
import com.nestmusic.music.ui.player.nest.NowPlayingAlphas
import com.nestmusic.music.ui.player.nest.canvas.NestCanvasArtwork
import com.nestmusic.music.ui.player.nest.canvas.NestCanvasRepository
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
    val animatedCanvasEnabled by rememberPreference(AnimatedCanvasKey, defaultValue = true)
    val fullBleedEnabled by rememberPreference(FullBleedArtworkKey, defaultValue = true)
    val canvasOverCellular by rememberPreference(CanvasOverCellularKey, defaultValue = true)
    val dolbyAtmosPref by rememberPreference(DolbyAtmosKey, defaultValue = true)
    val spatialAudioPref by rememberPreference(SpatialAudioKey, defaultValue = false)

    val audioPipeline by NestAudioOutputStatus.pipeline.collectAsState()
    val audioOutputs = rememberNestAudioOutputs()
    val activeExternalRoute = remember(audioOutputs) {
        audioOutputs.firstOrNull { it.isSelected && it.kind != NestAudioOutputKind.SPEAKER }?.name
    }

    var showPipelineDialog by remember { mutableStateOf(false) }
    var showAudioOutputSheet by remember { mutableStateOf(false) }

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

    // Animated album cover (Apple Music HLS tall/square editorial motion + Tidal loop fallback)
    var canvasArtwork by remember(mediaMetadata?.id) { mutableStateOf<NestCanvasArtwork?>(null) }
    var liveCanvasBitmap by remember(mediaMetadata?.id) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(
        mediaMetadata?.id,
        animatedCanvasEnabled,
        fullBleedEnabled,
        canvasOverCellular,
        state.isExpanded,
    ) {
        val meta = mediaMetadata
        if (!animatedCanvasEnabled || !state.isExpanded || meta == null) {
            canvasArtwork = null
            liveCanvasBitmap = null
            return@LaunchedEffect
        }
        val artistName = meta.artists.firstOrNull()?.name.orEmpty()
        canvasArtwork =
            NestCanvasRepository.resolve(
                context = context,
                mediaId = meta.id,
                title = meta.title,
                artist = artistName,
                album = meta.album?.title,
                preferTall = fullBleedEnabled,
                allowCellular = canvasOverCellular,
            )
    }

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
    // Always crisp luminous white ink on dark full-bleed / mesh Now Playing
    val inkState = rememberUpdatedState(Color.White)
    val glowState = rememberUpdatedState(palette.inkGlow)
    val alphas = remember { NowPlayingAlphas() }
    val sheetBackdrop = rememberGlassBackdrop()

    val collapseProgress by animateFloatAsState(
        targetValue = if (panelOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = IOS_PANEL_EASING),
        label = "nestHeaderCollapseProgress",
    )

    // Badge label: Dolby Atmos / Hi-Res Lossless / Lossless / Spatial Audio
    data class BadgeSpec(
        val label: String,
        val isDolbyAtmos: Boolean,
        val isLossless: Boolean,
    )

    val badgeSpec = remember(currentFormat, audioPipeline, dolbyAtmosPref, spatialAudioPref) {
        val fmt = currentFormat
        val mime = (audioPipeline.mimeType ?: fmt?.mimeType).orEmpty().lowercase()
        val isLosslessStream = "flac" in mime || "alac" in mime || "wav" in mime
        val sampleRate = audioPipeline.sampleRateHz.takeIf { it > 0 } ?: (fmt?.sampleRate ?: 0)
        when {
            dolbyAtmosPref || audioPipeline.isDolbyAtmos -> BadgeSpec(
                label = if (isLosslessStream) "Dolby Atmos · Lossless" else "Dolby Atmos",
                isDolbyAtmos = true,
                isLossless = false,
            )
            isLosslessStream -> BadgeSpec(
                label = if (sampleRate > 48_000) "Hi-Res Lossless" else "Lossless",
                isDolbyAtmos = false,
                isLossless = true,
            )
            spatialAudioPref || audioPipeline.isSpatialAudio -> BadgeSpec(
                label = "Spatial Audio",
                isDolbyAtmos = true,
                isLossless = false,
            )
            else -> BadgeSpec(
                label = audioPipeline.badgeLabel,
                isDolbyAtmos = false,
                isLossless = false,
            )
        }
    }

    val playingFromTitle = remember(queueTitle, mediaMetadata) {
        queueTitle?.takeIf { it.isNotBlank() }
            ?: mediaMetadata?.album?.title?.takeIf { it.isNotBlank() }
            ?: mediaMetadata?.artists?.firstOrNull()?.name
    }

    val paletteStops = remember(palette) {
        listOf(
            palette.primary,
            palette.secondary,
            palette.tertiary,
            palette.background,
        )
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
                NestArtworkMeshBackdrop(
                    artworkModel = mediaMetadata?.thumbnailUrl,
                    liveFrameBitmap = liveCanvasBitmap,
                    fallbackPalette = paletteStops,
                    isPlaying = isPlaying && state.isExpanded,
                    dimScrimAlpha = if (panelOpen) 0.42f else 0.26f,
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
            val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

            Box(modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .navigationBarsPadding(),
                ) {
                    if (metadata == null) {
                        Spacer(Modifier.weight(1f))
                        return@Column
                    }

                    // Upper morphing stage: Edge-to-Edge Full-Screen Cover (from y = 0 behind status bar!)
                    // <-> 66dp Collapsed Header + Credits + Lyrics/Queue panel
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    ) {
                        val stageWidth = maxWidth
                        val stageHeight = maxHeight
                        val creditsBlockHeight = 62.dp

                        val expandedWidth =
                            if (fullBleedEnabled) {
                                stageWidth
                            } else {
                                min(
                                    (stageWidth - NEST_GUTTER * 2).coerceAtLeast(120.dp),
                                    (stageHeight - statusBarTop - 36.dp - creditsBlockHeight - 20.dp).coerceAtLeast(120.dp),
                                )
                            }
                        val expandedHeight =
                            if (fullBleedEnabled) {
                                // Full-screen cover extends from top edge (0.dp) right down through the credits zone
                                // so its 16-stop smooth bottom fade melts directly behind the song title!
                                (stageHeight - 10.dp).coerceAtLeast(220.dp)
                            } else {
                                expandedWidth
                            }
                        val expandedLeft = if (fullBleedEnabled) 0.dp else (stageWidth - expandedWidth) / 2f
                        val expandedTop =
                            if (fullBleedEnabled) {
                                0.dp
                            } else {
                                (statusBarTop + 34.dp + ((stageHeight - statusBarTop - 34.dp - creditsBlockHeight - expandedHeight) / 2f))
                                    .coerceAtLeast(statusBarTop + 28.dp)
                            }

                        val collapsedArtLeft = NEST_GUTTER
                        val collapsedArtTop = statusBarTop + 30.dp

                        val expandedCreditsTop = (stageHeight - creditsBlockHeight - 2.dp).coerceAtLeast(statusBarTop + 40.dp)
                        val collapsedCreditsTop = statusBarTop + 34.dp
                        val currentCreditsTop = lerp(expandedCreditsTop, collapsedCreditsTop, collapseProgress)

                        val currentError = playbackError
                        if (currentError != null && !panelOpen) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(expandedHeight)
                                    .offset(y = expandedTop)
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
                                canvasArtwork = canvasArtwork,
                                isPlaying = isPlaying,
                                fullBleed = fullBleedEnabled,
                                collapseProgress = collapseProgress,
                                expandedWidth = expandedWidth,
                                expandedHeight = expandedHeight,
                                expandedTop = expandedTop,
                                expandedLeft = expandedLeft,
                                collapsedTop = collapsedArtTop,
                                collapsedLeft = collapsedArtLeft,
                                canSkipPrevious = canSkipPrevious,
                                canSkipNext = canSkipNext,
                                onSkipPrevious = { playerConnection.seekToPrevious() },
                                onSkipNext = { playerConnection.seekToNext() },
                                onCollapsedTap = { activePanel = NestPlayerPanel.NONE },
                                onCanvasFrameSampled = { bmp -> liveCanvasBitmap = bmp },
                                modifier = Modifier.zIndex(if (panelOpen) 2f else 1f),
                            )
                        }

                        // Credits row: floats cleanly over the bottom fade of the full-screen cover when expanded,
                        // and glides up beside the 66dp thumbnail when Lyrics or Queue opens!
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
                                .zIndex(3f),
                        )

                        // Inline Lyrics / Queue body occupying the stage below the collapsed header
                        if (collapseProgress > 0.02f) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(top = statusBarTop + 30.dp + NEST_HEADER_HEIGHT)
                                    .graphicsLayer {
                                        alpha = ((collapseProgress - 0.15f) / 0.85f).coerceIn(0f, 1f)
                                        translationY = (1f - collapseProgress) * 32.dp.toPx()
                                    }.zIndex(2f),
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
                                            shuffleEnabled = shuffleModeEnabled,
                                            repeatMode = repeatMode,
                                            autoplayEnabled = autoplayEnabled,
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
                                            bottomPadding = 12.dp,
                                            modifier = Modifier.fillMaxSize(),
                                        )
                                    }
                                    NestPlayerPanel.NONE -> Unit
                                }
                            }
                        }
                    }

                    // Bottom Now Playing Deck (Lyric Strip + Scrubber + Dolby Atmos Badge + Transport + Volume + 3-Icon Dock)
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
                            activeColor = ink.copy(alpha = 0.88f),
                            inactiveColor = ink.copy(alpha = 0.24f),
                            relativeDrag = true,
                        )

                        // Timestamps + centered tappable Dolby Atmos / Lossless / Spatial Audio badge
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
                                color = ink.copy(alpha = 0.56f),
                            )

                            NestAudioQualityBadge(
                                label = badgeSpec.label,
                                isDolbyAtmos = badgeSpec.isDolbyAtmos,
                                isLossless = badgeSpec.isLossless,
                                onClick = { showPipelineDialog = true },
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
                                color = ink.copy(alpha = 0.56f),
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
                            activeRouteName = activeExternalRoute,
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
                            onAirPlayClick = { showAudioOutputSheet = true },
                        )

                        Spacer(Modifier.height(10.dp))
                    }
                }

                // Floating Apple Music grabber capsule overlaid cleanly over the top of the full-bleed cover
                NestPlayerTopStrip(
                    queueTitle = playingFromTitle,
                    showSourceLabel = !fullBleedEnabled && !panelOpen,
                    onCollapse = { state.collapseSoft() },
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding()
                        .zIndex(5f),
                )
            }

            if (showPipelineDialog) {
                NestAudioPipelineDialog(
                    onDismiss = { showPipelineDialog = false },
                )
            }

            if (showAudioOutputSheet) {
                NestAudioOutputSheet(
                    onDismiss = { showAudioOutputSheet = false },
                    onOpenPipelineDetails = { showPipelineDialog = true },
                )
            }
        }
    }
}
