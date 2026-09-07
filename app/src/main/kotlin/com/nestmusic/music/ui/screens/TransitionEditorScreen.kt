/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nestmusic.music.LocalDatabase
import com.nestmusic.music.LocalPlayerAwareWindowInsets
import com.nestmusic.music.LocalPlayerConnection
import com.nestmusic.music.R
import com.nestmusic.music.db.entities.Song
import com.nestmusic.music.playback.MixStyle
import com.nestmusic.music.playback.TransitionStore
import com.nestmusic.music.playback.WaveformHelper
import com.nestmusic.music.ui.component.ListDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Spotify-style transition editor: shows the outgoing and the incoming track
 * with real audio waveforms around the mix point, and lets the user customize
 * the DJ-style transition (length + style) that MusicService will perform
 * between the two.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitionEditorScreen(
    navController: NavController,
    savedStateHandle: SavedStateHandle,
) {
    val prevId = savedStateHandle.get<String>("prevId") ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()

    val prevSong by database.song(prevId).collectAsStateWithLifecycle(initialValue = null)

    // Next track: resolved from the current player queue first, falling back
    // to related songs (user can always change it via the picker).
    var nextId by remember { mutableStateOf<String?>(null) }
    var showNextPicker by rememberSaveable { mutableStateOf(false) }
    val relatedSongs by produceState<List<Song>>(initialValue = emptyList(), prevId) {
        value = withContext(Dispatchers.IO) { database.relatedSongs(prevId) }
    }

    LaunchedEffect(Unit) {
        // The player may not be ready yet right after service start; degrade
        // gracefully to the "no next track" state in that case.
        runCatching { resolveNextTrackId(playerConnection.player, prevId) }
            .getOrNull()?.let { nextId = it }
    }

    val nextSong by produceState<Song?>(initialValue = null, nextId) {
        nextId?.let { id -> value = withContext(Dispatchers.IO) { database.song(id).first() } }
    }

    // Editor state
    var durationSec by rememberSaveable { mutableFloatStateOf(5f) }
    var style by rememberSaveable { mutableStateOf(MixStyle.FADE) }
    var hasExisting by remember { mutableStateOf(false) }

    LaunchedEffect(nextId) {
        nextId?.let { nid ->
            val existing = TransitionStore.get(prevId, nid)
            hasExisting = existing != null
            if (existing != null) {
                durationSec = existing.durationMs / 1000f
                style = existing.style
            }
        }
    }

    // Waveforms (real peaks when analysis succeeds, placeholder otherwise)
    var prevWaveform by remember { mutableStateOf<WaveformHelper.Waveform?>(null) }
    var nextWaveform by remember { mutableStateOf<WaveformHelper.Waveform?>(null) }
    LaunchedEffect(prevId) {
        prevWaveform = WaveformHelper.getWaveform(context, prevId)
    }
    LaunchedEffect(nextId) {
        nextId?.let { id -> nextWaveform = WaveformHelper.getWaveform(context, id) }
    }

    val currentMediaId by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPreviewable = currentMediaId?.id == prevId
    val overlapMs = (durationSec * 1000f).toLong()

    val saveTransition =
        saveBlock@{
            val nid = nextId ?: return@saveBlock
            TransitionStore.save(
                TransitionStore.Transition(prevId, nid, overlapMs, style),
            )
            playerConnection.service.rearmCrossfade()
            hasExisting = true
            Toast.makeText(context, R.string.transition_saved, Toast.LENGTH_SHORT).show()
            navController.navigateUp()
        }

    val removeTransition =
        removeBlock@{
            val nid = nextId ?: return@removeBlock
            TransitionStore.remove(prevId, nid)
            playerConnection.service.rearmCrossfade()
            hasExisting = false
            durationSec = 5f
            style = MixStyle.FADE
            Toast.makeText(context, R.string.transition_removed, Toast.LENGTH_SHORT).show()
        }

    val saveAndPreview =
        previewBlock@{
            val nid = nextId ?: return@previewBlock
            // Persist first so the engine mixes with the fresh settings.
            TransitionStore.save(
                TransitionStore.Transition(prevId, nid, overlapMs, style),
            )
            playerConnection.service.rearmCrossfade()
            coroutineScope.launch(Dispatchers.Main.immediate) {
                runCatching {
                    val player = playerConnection.player
                    if (player.currentMediaItem?.mediaId == prevId) {
                        // Land just before the mix point so the armed transition fires.
                        val trigger = (player.duration - overlapMs).coerceAtLeast(0L)
                        player.seekTo((trigger - 1500L).coerceAtLeast(0L))
                        if (!player.playWhenReady) {
                            player.playWhenReady = true
                        }
                    }
                }
            }
        }

    val nextCandidates = remember(relatedSongs, prevId) {
        relatedSongs.filter { it.song.id != prevId }.take(30)
    }

    if (showNextPicker) {
        ListDialog(onDismiss = { showNextPicker = false }) {
            item {
                Text(
                    text = stringResource(R.string.transition_choose_next),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                )
            }
            if (nextCandidates.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.transition_no_next),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
                    )
                }
            }
            items(nextCandidates, key = { it.song.id }) { song ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                nextId = song.song.id
                                showNextPicker = false
                            }
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                ) {
                    AsyncImage(
                        model = song.song.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(10.dp)),
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song.song.title,
                            style = MaterialTheme.typography.bodyLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = song.artists.joinToString(", ") { it.name },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (song.song.id == nextId) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top),
                ),
    ) {
        TopAppBar(
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.edit_transition))
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFF1ED760).copy(alpha = 0.18f),
                    ) {
                        Text(
                            text = stringResource(R.string.transition_beta),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF1ED760),
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        )
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = { navController.navigateUp() }) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.cancel),
                    )
                }
            },
            actions = {
                TextButton(
                    onClick = saveTransition,
                    enabled = nextId != null,
                ) {
                    Text(
                        stringResource(R.string.save),
                        color =
                            if (nextId != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                    )
                }
            },
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TransitionTrackCard(
                song = prevSong,
                label = stringResource(R.string.transition_outgoing),
                showChangeButton = false,
            )

            // Waveforms around the mix point: outgoing track ends on the right,
            // incoming track starts on the left, overlap zone highlighted.
            Card(
                shape = RoundedCornerShape(20.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val prevDurationMs =
                        (prevSong?.song?.duration ?: 0).takeIf { it > 0 }?.let { it * 1000L }
                            ?: prevWaveform?.durationMs
                            ?: 0L
                    val nextDurationMs =
                        (nextSong?.song?.duration ?: 0).takeIf { it > 0 }?.let { it * 1000L }
                            ?: nextWaveform?.durationMs
                            ?: 0L

                    TransitionWaveform(
                        waveform = prevWaveform,
                        trackDurationMs = prevDurationMs,
                        overlapMs = overlapMs,
                        window = WaveWindow.TAIL,
                        barColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                    )
                    TransitionWaveform(
                        waveform = nextWaveform,
                        trackDurationMs = nextDurationMs,
                        overlapMs = overlapMs,
                        window = WaveWindow.HEAD,
                        barColor = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
                    )
                    if (prevWaveform?.isApproximate == true || nextWaveform?.isApproximate == true) {
                        Text(
                            text = stringResource(R.string.transition_approach_note),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (nextId != null) {
                TransitionTrackCard(
                    song = nextSong,
                    label = stringResource(R.string.transition_incoming),
                    showChangeButton = true,
                    onChange = { showNextPicker = true },
                )
            } else {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        ),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.transition_no_next),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        if (nextCandidates.isNotEmpty()) {
                            FilledTonalButton(onClick = { showNextPicker = true }) {
                                Text(stringResource(R.string.transition_change_next))
                            }
                        }
                    }
                }
            }

            // Transition length
            Card(
                shape = RoundedCornerShape(16.dp),
                colors =
                    CardDefaults.cardColors(
                        containerColor =
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    ),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.timer),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.transition_length),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                        ) {
                            Text(
                                text =
                                    stringResource(
                                        R.string.transition_length_value,
                                        durationSec.toInt(),
                                    ),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Slider(
                        value = durationSec,
                        onValueChange = { durationSec = it },
                        valueRange =
                            (TransitionStore.MIN_DURATION_MS / 1000f)..(TransitionStore.MAX_DURATION_MS / 1000f),
                        steps = 13,
                    )
                }
            }

            // Transition style
            Text(
                text = stringResource(R.string.transition_style),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                styleOption(
                    style = MixStyle.FADE,
                    iconRes = R.drawable.linear_scale,
                    label = stringResource(R.string.transition_style_fade),
                    selected = style == MixStyle.FADE,
                    onSelected = { style = MixStyle.FADE },
                )
                styleOption(
                    style = MixStyle.RISE,
                    iconRes = R.drawable.trending_up,
                    label = stringResource(R.string.transition_style_rise),
                    selected = style == MixStyle.RISE,
                    onSelected = { style = MixStyle.RISE },
                )
                styleOption(
                    style = MixStyle.SMOOTH,
                    iconRes = R.drawable.gradient,
                    label = stringResource(R.string.transition_style_smooth),
                    selected = style == MixStyle.SMOOTH,
                    onSelected = { style = MixStyle.SMOOTH },
                )
            }
            Text(
                text =
                    when (style) {
                        MixStyle.FADE -> stringResource(R.string.transition_style_fade_desc)
                        MixStyle.RISE -> stringResource(R.string.transition_style_rise_desc)
                        MixStyle.SMOOTH -> stringResource(R.string.transition_style_smooth_desc)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
            )

            // Preview: only meaningful while this exact track is playing.
            if (isPreviewable) {
                FilledTonalButton(
                    onClick = saveAndPreview,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.transition_preview))
                }
                Text(
                    text = stringResource(R.string.transition_preview_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (hasExisting && nextId != null) {
                TextButton(
                    onClick = removeTransition,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        stringResource(R.string.transition_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

/** Resolves the track that follows [prevId] in the current playback order. */
private fun resolveNextTrackId(player: Player, prevId: String): String? {
    val timeline = player.currentTimeline
    if (timeline.isEmpty) return null
    val count = timeline.windowCount
    var found = C.INDEX_UNSET
    for (i in 0 until count) {
        if (timeline.getWindow(i, Timeline.Window()).mediaItem.mediaId == prevId) {
            found = i
            break
        }
    }
    if (found == C.INDEX_UNSET) return null
    val nextIndex =
        timeline.getNextWindowIndex(found, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
    if (nextIndex == C.INDEX_UNSET) return null
    return timeline.getWindow(nextIndex, Timeline.Window()).mediaItem.mediaId
}

private enum class WaveWindow { HEAD, TAIL }

/**
 * Draws the portion of a track's waveform around the mix point.
 *
 * [WaveWindow.TAIL] renders the end of the outgoing track (mix point on the
 * right edge); [WaveWindow.HEAD] renders the start of the incoming track (mix
 * point on the left edge). The overlap zone is highlighted and the mix point
 * is marked with a green line, mirroring Spotify's transition editor.
 */
@Composable
private fun TransitionWaveform(
    waveform: WaveformHelper.Waveform?,
    trackDurationMs: Long,
    overlapMs: Long,
    window: WaveWindow,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    val minWindowMs = 20_000L
    val windowMs = overlapMs.coerceAtLeast(minWindowMs)
    val totalMs = trackDurationMs.takeIf { it >= windowMs } ?: windowMs
    val startMs = if (window == WaveWindow.TAIL) totalMs - windowMs else 0L
    val endMs = if (window == WaveWindow.TAIL) totalMs else windowMs
    val overlapRatio =
        (overlapMs.toFloat() / (endMs - startMs).coerceAtLeast(1L)).coerceIn(0f, 1f)
    val mixPointX =
        if (window == WaveWindow.TAIL) 1f - overlapRatio else overlapRatio

    Canvas(modifier = modifier.fillMaxWidth().height(64.dp)) {
        val mid = size.height / 2f
        val peaks = waveform?.peaks
        if (peaks != null) {
            val n = peaks.size
            val b0 = ((startMs.toFloat() / totalMs) * n).toInt().coerceIn(0, n)
            val b1 = ((endMs.toFloat() / totalMs) * n).toInt().coerceIn(b0, n)
            val count = (b1 - b0).coerceAtLeast(1)
            val barW = size.width / count
            for (i in 0 until count) {
                val v = peaks[b0 + i].toInt().coerceIn(0, 32767)
                val h = (v / 32767f) * size.height * 0.92f
                drawRect(
                    topLeft = Offset(i * barW, mid - h / 2f),
                    size = Size(barW * 0.62f, h),
                    color = barColor,
                )
            }
        } else {
            // Loading placeholder: a flat dim bar.
            drawRect(
                topLeft = Offset(0f, mid - 8f),
                size = Size(size.width, 16f),
                color = barColor.copy(alpha = 0.25f),
            )
        }
        // Overlap zone + mix point marker
        if (overlapRatio > 0f && overlapRatio < 1f) {
            val zoneStartX = if (window == WaveWindow.TAIL) size.width * (1f - overlapRatio) else 0f
            drawRect(
                topLeft = Offset(zoneStartX, 0f),
                size = Size(size.width * overlapRatio, size.height),
                color = barColor.copy(alpha = 0.14f),
            )
            drawLine(
                color = Color(0xFF34C759),
                start = Offset(size.width * mixPointX, 0f),
                end = Offset(size.width * mixPointX, size.height),
                strokeWidth = 2.dp.toPx(),
            )
        }
    }
}

/** Track card with label, artwork, title/artist and duration chip. */
@Composable
private fun TransitionTrackCard(
    song: Song?,
    label: String,
    showChangeButton: Boolean,
    onChange: (() -> Unit)? = null,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(56.dp)) {
                val thumbnailUrl = song?.song?.thumbnailUrl
                if (!thumbnailUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp)),
                    )
                } else {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(12.dp),
                                ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.music_note),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = song?.song?.title ?: "…",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val artistName = song?.artists?.joinToString(", ") { it.name }.orEmpty()
                if (artistName.isNotEmpty()) {
                    Text(
                        text = artistName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            val durationSec = song?.song?.duration ?: 0
            if (durationSec > 0) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Text(
                        text =
                            "%d:%02d".format(
                                durationSec / 60,
                                durationSec % 60,
                            ),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
            if (showChangeButton) {
                IconButton(onClick = { onChange?.invoke() }) {
                    Icon(
                        painter = painterResource(R.drawable.sync),
                        contentDescription = stringResource(R.string.transition_change_next),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}

/** Selectable style tile in the transition style row. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RowScope.styleOption(
    style: MixStyle,
    iconRes: Int,
    label: String,
    selected: Boolean,
    onSelected: () -> Unit,
) {
    Card(
        onClick = onSelected,
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(16.dp),
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (selected) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    },
            ),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                modifier = Modifier.size(22.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color =
                    if (selected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
