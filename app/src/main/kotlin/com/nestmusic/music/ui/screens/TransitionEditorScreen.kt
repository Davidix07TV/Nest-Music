/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.screens

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import coil3.compose.AsyncImage
import com.nestmusic.music.LocalDatabase
import com.nestmusic.music.LocalPlayerAwareWindowInsets
import com.nestmusic.music.LocalPlayerConnection
import com.nestmusic.music.R
import com.nestmusic.music.constants.CrossfadeDurationKey
import com.nestmusic.music.db.entities.Song
import com.nestmusic.music.extensions.metadata
import com.nestmusic.music.models.MediaMetadata
import com.nestmusic.music.playback.MixEffect
import com.nestmusic.music.playback.MixEqMode
import com.nestmusic.music.playback.MixPreset
import com.nestmusic.music.playback.MixStyle
import com.nestmusic.music.playback.MixVolumeCurve
import com.nestmusic.music.playback.TransitionStore
import com.nestmusic.music.playback.WaveformHelper
import com.nestmusic.music.playback.displayStyle
import com.nestmusic.music.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.roundToLong

private val MAX_EDITOR_WINDOW_MS = TransitionStore.MAX_DURATION_MS.toFloat()

/** Spotify-style editor for one concrete outgoing -> incoming pair. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitionEditorScreen(
    navController: androidx.navigation.NavController,
    savedStateHandle: SavedStateHandle,
) {
    val prevId = savedStateHandle.get<String>("prevId") ?: return
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val database = LocalDatabase.current
    val globalCrossfadeDuration by rememberPreference(CrossfadeDurationKey, defaultValue = 5f)
    val autoDurationMs = (globalCrossfadeDuration * 1000f).roundToLong()
        .coerceIn(TransitionStore.MIN_DURATION_MS, TransitionStore.MAX_DURATION_MS)

    // New queue/playlist pills always provide nextId. The fallback is only for
    // old deep links from the first beta of the editor.
    var nextId by remember {
        mutableStateOf(savedStateHandle.get<String>("nextId") ?: resolveNextTrackId(playerConnection.player, prevId))
    }
    val prevSong by database.song(prevId).collectAsStateWithLifecycle(initialValue = null)
    val nextSong by produceState<Song?>(initialValue = null, nextId) {
        value = nextId?.let { id ->
            withContext(Dispatchers.IO) { database.song(id).first() }
        }
    }

    var durationMs by rememberSaveable { mutableLongStateOf(autoDurationMs) }
    var mixPointOffsetMs by rememberSaveable { mutableLongStateOf(0L) }
    var style by rememberSaveable { mutableStateOf(MixStyle.AUTO) }
    var volumeCurve by rememberSaveable { mutableStateOf(MixVolumeCurve.LINEAR) }
    var eqMode by rememberSaveable { mutableStateOf(MixEqMode.NONE) }
    var effect by rememberSaveable { mutableStateOf(MixEffect.NONE) }
    var hasExisting by remember { mutableStateOf(false) }

    LaunchedEffect(prevId, nextId) {
        val existing = nextId?.let { TransitionStore.get(prevId, it) }
        hasExisting = existing != null
        if (existing != null) {
            durationMs = existing.durationMs
            mixPointOffsetMs = existing.mixPointOffsetMs
            style = existing.style.displayStyle()
            volumeCurve = existing.volumeCurve
            eqMode = existing.eqMode
            effect = existing.effect
        } else {
            durationMs = autoDurationMs
            mixPointOffsetMs = 0L
            style = MixStyle.AUTO
            volumeCurve = MixVolumeCurve.LINEAR
            eqMode = MixEqMode.NONE
            effect = MixEffect.NONE
        }
    }

    var outgoingWaveform by remember { mutableStateOf<WaveformHelper.Waveform?>(null) }
    var incomingWaveform by remember { mutableStateOf<WaveformHelper.Waveform?>(null) }
    LaunchedEffect(prevId) {
        outgoingWaveform = WaveformHelper.getWaveform(context, prevId)
    }
    LaunchedEffect(nextId) {
        incomingWaveform = nextId?.let { WaveformHelper.getWaveform(context, it) }
    }

    val outgoingMetadata = remember(playerConnection.player, prevId) {
        mediaMetadataFor(playerConnection.player, prevId)
    }
    val incomingMetadata = remember(playerConnection.player, nextId) {
        nextId?.let { mediaMetadataFor(playerConnection.player, it) }
    }

    fun selectPreset(presetStyle: MixStyle) {
        val preset = MixPreset.forStyle(presetStyle, autoDurationMs)
        style = preset.style
        durationMs = preset.durationMs.coerceIn(TransitionStore.MIN_DURATION_MS, TransitionStore.MAX_DURATION_MS)
        volumeCurve = preset.volumeCurve
        eqMode = preset.eqMode
        effect = preset.effect
        mixPointOffsetMs = 0L
    }

    fun persist() {
        val incomingId = nextId ?: return
        val isUntouchedAuto =
            style.displayStyle() == MixStyle.AUTO &&
                durationMs == autoDurationMs &&
                mixPointOffsetMs == 0L &&
                volumeCurve == MixVolumeCurve.LINEAR &&
                eqMode == MixEqMode.NONE &&
                effect == MixEffect.NONE
        if (isUntouchedAuto) {
            TransitionStore.remove(prevId, incomingId)
        } else {
            TransitionStore.save(
                TransitionStore.Transition(
                    prevId = prevId,
                    nextId = incomingId,
                    durationMs = durationMs,
                    style = style.displayStyle(),
                    volumeCurve = volumeCurve,
                    eqMode = eqMode,
                    effect = effect,
                    mixPointOffsetMs = mixPointOffsetMs,
                ),
            )
        }
        playerConnection.service.rearmCrossfade()
        hasExisting = !isUntouchedAuto
        Toast.makeText(context, R.string.transition_saved, Toast.LENGTH_SHORT).show()
        navController.navigateUp()
    }

    fun remove() {
        nextId?.let { TransitionStore.remove(prevId, it) }
        playerConnection.service.rearmCrossfade()
        hasExisting = false
        selectPreset(MixStyle.AUTO)
        Toast.makeText(context, R.string.transition_removed, Toast.LENGTH_SHORT).show()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Top)),
    ) {
        TopAppBar(
            title = {
                Column {
                    Text(stringResource(R.string.edit_transition))
                    Text(
                        text = stringResource(R.string.transition_length_value, durationMs / 1000f),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
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
                    onClick = { persist() },
                    enabled = nextId != null,
                ) {
                    Text(stringResource(R.string.save))
                }
            },
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TrackRow(
                song = prevSong,
                fallback = outgoingMetadata,
                label = stringResource(R.string.transition_outgoing),
            )

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.transition_mix_point),
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.weight(1f),
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Text(
                                text = stringResource(R.string.transition_length_value, durationMs / 1000f),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            )
                        }
                    }
                    TransitionWaveformEditor(
                        outgoing = outgoingWaveform,
                        incoming = incomingWaveform,
                        durationMs = durationMs,
                        mixPointOffsetMs = mixPointOffsetMs,
                        style = style,
                        volumeCurve = volumeCurve,
                        onDurationChanged = { durationMs = it },
                        onMixPointChanged = { mixPointOffsetMs = it },
                    )
                    Text(
                        text = stringResource(R.string.transition_drag_handles_hint),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            TrackRow(
                song = nextSong,
                fallback = incomingMetadata,
                label = stringResource(R.string.transition_incoming),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TransitionControlMenu(
                    title = stringResource(R.string.transition_control_volume),
                    current = when (volumeCurve) {
                        MixVolumeCurve.LINEAR -> stringResource(R.string.transition_volume_linear)
                        MixVolumeCurve.EQUAL_POWER -> stringResource(R.string.transition_volume_equal_power)
                    },
                    options = listOf(
                        MixVolumeCurve.LINEAR to stringResource(R.string.transition_volume_linear),
                        MixVolumeCurve.EQUAL_POWER to stringResource(R.string.transition_volume_equal_power),
                    ),
                    selected = volumeCurve,
                    onSelected = { volumeCurve = it },
                    icon = R.drawable.volume_up,
                )
                TransitionControlMenu(
                    title = stringResource(R.string.transition_control_eq),
                    current = when (eqMode) {
                        MixEqMode.NONE -> stringResource(R.string.transition_eq_none)
                        MixEqMode.LOW_HIGH_SWAP -> stringResource(R.string.transition_eq_low_high_swap)
                    },
                    options = listOf(
                        MixEqMode.NONE to stringResource(R.string.transition_eq_none),
                        MixEqMode.LOW_HIGH_SWAP to stringResource(R.string.transition_eq_low_high_swap),
                    ),
                    selected = eqMode,
                    onSelected = { eqMode = it },
                    icon = R.drawable.equalizer,
                )
                TransitionControlMenu(
                    title = stringResource(R.string.transition_control_effects),
                    current = when (effect) {
                        MixEffect.NONE -> stringResource(R.string.transition_effects_none)
                        MixEffect.HIGH_PASS -> stringResource(R.string.transition_effect_high_pass)
                        MixEffect.LOW_PASS -> stringResource(R.string.transition_effect_low_pass)
                    },
                    options = listOf(
                        MixEffect.NONE to stringResource(R.string.transition_effects_none),
                        MixEffect.HIGH_PASS to stringResource(R.string.transition_effect_high_pass),
                        MixEffect.LOW_PASS to stringResource(R.string.transition_effect_low_pass),
                    ),
                    selected = effect,
                    onSelected = { effect = it },
                    icon = R.drawable.tune,
                )
            }

            Text(
                text = stringResource(R.string.transition_style),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(start = 4.dp),
            )
            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(MixStyle.AUTO, MixStyle.FADE, MixStyle.RISE, MixStyle.BLEND).forEach { presetStyle ->
                    PresetChip(
                        style = presetStyle,
                        durationMs = MixPreset.forStyle(presetStyle, autoDurationMs).durationMs,
                        selected = style.displayStyle() == presetStyle,
                        onClick = { selectPreset(presetStyle) },
                    )
                }
            }
            Text(
                text = when (style.displayStyle()) {
                    MixStyle.AUTO -> stringResource(R.string.transition_style_auto_desc)
                    MixStyle.FADE -> stringResource(R.string.transition_style_fade_desc)
                    MixStyle.RISE -> stringResource(R.string.transition_style_rise_desc)
                    MixStyle.BLEND, MixStyle.SMOOTH -> stringResource(R.string.transition_style_blend_desc)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (hasExisting && nextId != null) {
                TextButton(
                    onClick = { remove() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.transition_remove),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    song: Song?,
    fallback: MediaMetadata?,
    label: String,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val thumbnail = song?.song?.thumbnailUrl ?: fallback?.thumbnailUrl
            if (!thumbnail.isNullOrBlank()) {
                AsyncImage(
                    model = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)),
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.music_note),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Column(
                modifier = Modifier.padding(start = 12.dp).weight(1f),
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = song?.song?.title ?: fallback?.title ?: "…",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val artists = song?.artists?.joinToString(", ") { it.name }
                    ?: fallback?.artists?.joinToString(", ") { it.name }
                if (!artists.isNullOrBlank()) {
                    Text(
                        text = artists,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val bpm = fallback?.bpm?.takeIf { it > 0 }
                if (bpm != null) {
                    Text(
                        text = stringResource(R.string.bpm_format, bpm),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val duration = song?.song?.duration ?: fallback?.duration ?: 0
            if (duration > 0) {
                Text(
                    text = "%d:%02d".format(duration / 60, duration % 60),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private enum class DragHandle { START, END, CENTRE }

/**
 * Draws both tracks in the same coordinate space. The left/right handles resize
 * the overlap and the centre handle moves its position; every resize callback
 * is clamped to the 0.5–12 second product limits.
 */
@Composable
private fun TransitionWaveformEditor(
    outgoing: WaveformHelper.Waveform?,
    incoming: WaveformHelper.Waveform?,
    durationMs: Long,
    mixPointOffsetMs: Long,
    style: MixStyle,
    volumeCurve: MixVolumeCurve,
    onDurationChanged: (Long) -> Unit,
    onMixPointChanged: (Long) -> Unit,
) {
    val minRatio = TransitionStore.MIN_DURATION_MS / MAX_EDITOR_WINDOW_MS
    val maxRatio = 1f
    val overlapRatio = (durationMs / MAX_EDITOR_WINDOW_MS).coerceIn(minRatio, maxRatio)
    val centre = (0.5f + mixPointOffsetMs / MAX_EDITOR_WINDOW_MS).coerceIn(
        overlapRatio / 2f,
        1f - overlapRatio / 2f,
    )
    val start = centre - overlapRatio / 2f
    val end = centre + overlapRatio / 2f
    val primary = MaterialTheme.colorScheme.primary
    val tertiary = MaterialTheme.colorScheme.tertiary
    val curveStyle = style.displayStyle()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(168.dp)
            .pointerInput(durationMs, mixPointOffsetMs) {
                var activeHandle: DragHandle? = null
                detectDragGestures(
                    onDragStart = { point ->
                        val x = point.x / size.width.coerceAtLeast(1)
                        val distances = listOf(
                            DragHandle.START to abs(x - start),
                            DragHandle.END to abs(x - end),
                            DragHandle.CENTRE to abs(x - centre),
                        )
                        activeHandle = distances.minBy { it.second }.first
                    },
                    onDragEnd = { activeHandle = null },
                    onDragCancel = { activeHandle = null },
                ) { change, dragAmount ->
                    change.consume()
                    val dx = dragAmount.x / size.width.coerceAtLeast(1)
                    when (activeHandle) {
                        DragHandle.START -> {
                            val newStart = (start + dx).coerceIn(0f, end - minRatio)
                            onDurationChanged(
                                ((end - newStart) * MAX_EDITOR_WINDOW_MS)
                                    .roundToLong()
                                    .coerceIn(TransitionStore.MIN_DURATION_MS, TransitionStore.MAX_DURATION_MS),
                            )
                        }
                        DragHandle.END -> {
                            val newEnd = (end + dx).coerceIn(start + minRatio, 1f)
                            onDurationChanged(
                                ((newEnd - start) * MAX_EDITOR_WINDOW_MS)
                                    .roundToLong()
                                    .coerceIn(TransitionStore.MIN_DURATION_MS, TransitionStore.MAX_DURATION_MS),
                            )
                        }
                        DragHandle.CENTRE -> {
                            val newCentre = (centre + dx).coerceIn(overlapRatio / 2f, 1f - overlapRatio / 2f)
                            onMixPointChanged(
                                ((newCentre - 0.5f) * MAX_EDITOR_WINDOW_MS)
                                    .roundToLong()
                                    .coerceIn(-durationMs / 2, durationMs / 2),
                            )
                        }
                        null -> Unit
                    }
                }
            },
    ) {
        val mid = size.height / 2f
        val maxHeight = size.height * 0.34f
        val barCount = 100
        val barWidth = size.width / barCount
        repeat(barCount) { index ->
            val x = (index + 0.5f) / barCount
            val outPeak = waveformPeak(outgoing, 1f - x)
            val inPeak = waveformPeak(incoming, x)
            val outHeight = maxHeight * outPeak
            val inHeight = maxHeight * inPeak
            drawRoundRect(
                color = primary.copy(alpha = if (x in start..end) 0.78f else 0.28f),
                topLeft = Offset(index * barWidth, mid - outHeight),
                size = Size(barWidth * 0.62f, outHeight * 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
            )
            drawRoundRect(
                color = tertiary.copy(alpha = if (x in start..end) 0.72f else 0.25f),
                topLeft = Offset(index * barWidth, mid - inHeight),
                size = Size(barWidth * 0.42f, inHeight * 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(2f, 2f),
            )
        }

        drawRoundRect(
            color = Color(0xFF34C759).copy(alpha = 0.10f),
            topLeft = Offset(start * size.width, 0f),
            size = Size((end - start) * size.width, size.height),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f, 10f),
        )
        drawLine(
            color = Color(0xFF34C759),
            start = Offset(centre * size.width, 0f),
            end = Offset(centre * size.width, size.height),
            strokeWidth = 2.dp.toPx(),
        )
        drawLine(
            color = primary,
            start = Offset(start * size.width, 12.dp.toPx()),
            end = Offset(start * size.width, size.height - 12.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tertiary,
            start = Offset(end * size.width, 12.dp.toPx()),
            end = Offset(end * size.width, size.height - 12.dp.toPx()),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // Volume curves are drawn over the overlap, making preset changes
        // visible immediately without requiring audio playback.
        val outPath = Path()
        val inPath = Path()
        val curveSteps = 48
        repeat(curveSteps + 1) { step ->
            val p = step / curveSteps.toFloat()
            val x = (start + p * (end - start)) * size.width
            val outY = mid + (1f - curveStyle.fadeOut(p, volumeCurve)) * maxHeight
            val inY = mid - curveStyle.fadeIn(p, volumeCurve) * maxHeight
            if (step == 0) {
                outPath.moveTo(x, outY)
                inPath.moveTo(x, inY)
            } else {
                outPath.lineTo(x, outY)
                inPath.lineTo(x, inY)
            }
        }
        drawPath(outPath, Color.White.copy(alpha = 0.86f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
        drawPath(inPath, Color.White.copy(alpha = 0.86f), style = androidx.compose.ui.graphics.drawscope.Stroke(2.dp.toPx()))
    }
}

private fun waveformPeak(waveform: WaveformHelper.Waveform?, position: Float): Float {
    val peaks = waveform?.peaks ?: return 0.12f
    if (peaks.isEmpty()) return 0.12f
    val index = (position.coerceIn(0f, 1f) * (peaks.lastIndex)).roundToLong().toInt()
    return (peaks[index].toInt() / 32767f).coerceIn(0.04f, 1f)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> RowScope.TransitionControlMenu(
    title: String,
    current: String,
    options: List<Pair<T, String>>,
    selected: T,
    onSelected: (T) -> Unit,
    icon: Int,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = Modifier.weight(1f)) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        painter = painterResource(icon),
                        contentDescription = null,
                        modifier = Modifier.size(17.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Text(
                    text = current,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { (value, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onSelected(value)
                        expanded = false
                    },
                    trailingIcon = if (value == selected) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.check),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    } else {
                        null
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PresetChip(
    style: MixStyle,
    durationMs: Long,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val label = when (style) {
        MixStyle.AUTO -> stringResource(R.string.transition_style_auto)
        MixStyle.FADE -> stringResource(R.string.transition_style_fade)
        MixStyle.RISE -> stringResource(R.string.transition_style_rise)
        MixStyle.BLEND, MixStyle.SMOOTH -> stringResource(R.string.transition_style_blend)
    }
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        },
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = stringResource(
                    R.string.transition_length_value,
                    durationMs / 1000f,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

private fun resolveNextTrackId(player: Player, prevId: String): String? {
    val timeline = player.currentTimeline
    if (timeline.isEmpty) return null
    val window = Timeline.Window()
    val index = (0 until timeline.windowCount).firstOrNull {
        timeline.getWindow(it, window).mediaItem.mediaId == prevId
    } ?: return null
    val nextIndex = player.nextMediaItemIndex
        .takeIf { index == player.currentMediaItemIndex }
        ?: timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, player.shuffleModeEnabled)
    if (nextIndex == C.INDEX_UNSET || nextIndex >= timeline.windowCount) return null
    return timeline.getWindow(nextIndex, window).mediaItem.mediaId
}

private fun mediaMetadataFor(player: Player, mediaId: String): MediaMetadata? {
    for (index in 0 until player.mediaItemCount) {
        val item = player.getMediaItemAt(index)
        if (item.mediaId == mediaId) return item.metadata
    }
    return null
}
