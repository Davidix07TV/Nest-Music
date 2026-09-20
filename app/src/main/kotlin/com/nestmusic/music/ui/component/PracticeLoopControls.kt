/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackParameters
import com.nestmusic.music.LocalPlayerConnection
import com.nestmusic.music.R
import com.nestmusic.music.constants.PracticeLoopCountInKey
import com.nestmusic.music.utils.makeTimeString
import com.nestmusic.music.utils.rememberPreference
import kotlinx.coroutines.delay

private const val LIVE_POSITION_TICK_MS = 200L

private val PRACTICE_SPEEDS = listOf(0.5f, 0.75f, 0.9f, 1f)

/**
 * Compact A-B controls shown in the player while a practice session is running: set or move the
 * two loop points without leaving the player, plus the loop progress and repeat count.
 */
@Composable
fun PracticeLoopBar(modifier: Modifier = Modifier) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val practiceLoop = playerConnection.service.practiceLoop ?: return
    val bottomSheetPageState = LocalBottomSheetPageState.current

    val enabled = practiceLoop.enabled
    val isActive = practiceLoop.isActive
    val startMs = practiceLoop.startMs
    val endMs = practiceLoop.endMs
    val repetitions = practiceLoop.repetitions
    val isCountingIn = practiceLoop.isCountingIn

    var position by remember { mutableLongStateOf(0L) }
    LaunchedEffect(isActive) {
        if (!isActive) return@LaunchedEffect
        while (true) {
            position =
                runCatching { playerConnection.player.currentPosition }.getOrDefault(position)
            delay(LIVE_POSITION_TICK_MS)
        }
    }

    AnimatedVisibility(
        visible = enabled,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier,
    ) {
        val textColor = MaterialTheme.colorScheme.onSurface

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = textColor,
        ) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 12.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.repeat),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text =
                            when {
                                isCountingIn -> stringResource(R.string.practice_loop_counting_in)
                                isActive ->
                                    stringResource(
                                        R.string.practice_loop_active,
                                        makeTimeString(startMs),
                                        makeTimeString(endMs),
                                        repetitions,
                                    )
                                else -> stringResource(R.string.practice_loop_idle)
                            },
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { practiceLoop.clear() },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.practice_loop_clear),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    IconButton(
                        onClick = { bottomSheetPageState.show { PracticeLoopSheet() } },
                        modifier = Modifier.size(36.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.tune),
                            contentDescription = stringResource(R.string.practice_loop),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                val loopProgress = if (isActive) practiceLoop.progress(position) else null
                if (loopProgress != null) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(end = 8.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(textColor.copy(alpha = 0.2f)),
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxWidth(loopProgress.coerceIn(0f, 1f))
                                    .fillMaxHeight()
                                    .background(textColor),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
                ) {
                    FilledTonalButton(
                        onClick = { practiceLoop.setStartHere() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.practice_loop_set_a))
                    }
                    FilledTonalButton(
                        onClick = { practiceLoop.setEndHere() },
                        enabled = startMs != null,
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(stringResource(R.string.practice_loop_set_b))
                    }
                }
            }
        }
    }
}

/**
 * Full practice loop panel: both points with their current values, repeat count, playback speed
 * presets to slow a hard part down and the optional count-in before every repetition.
 */
@Composable
fun PracticeLoopSheet() {
    val playerConnection = LocalPlayerConnection.current ?: return
    val practiceLoop = playerConnection.service.practiceLoop ?: return
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val (countIn, onCountInChange) = rememberPreference(PracticeLoopCountInKey, false)

    val isActive = practiceLoop.isActive
    val startMs = practiceLoop.startMs
    val endMs = practiceLoop.endMs
    val repetitions = practiceLoop.repetitions

    var position by remember { mutableLongStateOf(0L) }
    var speed by remember { mutableFloatStateOf(1f) }

    LaunchedEffect(Unit) {
        practiceLoop.startSession()
        while (true) {
            position =
                runCatching { playerConnection.player.currentPosition }.getOrDefault(position)
            speed =
                runCatching { playerConnection.player.playbackParameters.speed }.getOrDefault(speed)
            delay(LIVE_POSITION_TICK_MS)
        }
    }

    LazyColumn(
        state = rememberLazyListState(),
        modifier = Modifier.padding(WindowInsets.systemBars.asPaddingValues()),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.practice_loop),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { bottomSheetPageState.dismiss() }) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = stringResource(R.string.close),
                    )
                }
            }
            Text(
                text = stringResource(R.string.practice_loop_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
        }

        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    PracticeLoopStatRow(
                        label = stringResource(R.string.practice_loop_position),
                        value = makeTimeString(position),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    PracticeLoopStatRow(
                        label = stringResource(R.string.practice_loop_point_a),
                        value =
                            startMs?.let { makeTimeString(it) }
                                ?: stringResource(R.string.practice_loop_point_unset),
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                    PracticeLoopStatRow(
                        label = stringResource(R.string.practice_loop_point_b),
                        value =
                            endMs?.let { makeTimeString(it) }
                                ?: stringResource(R.string.practice_loop_point_unset),
                    )
                    if (isActive) {
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PracticeLoopStatRow(
                            label = stringResource(R.string.practice_loop_length),
                            value = makeTimeString(practiceLoop.lengthMs),
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                        PracticeLoopStatRow(
                            label = stringResource(R.string.practice_loop_repeats_label),
                            value = stringResource(R.string.practice_loop_repeats, repetitions),
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        item {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilledTonalButton(
                    onClick = { practiceLoop.setStartHere() },
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.practice_loop_set_a))
                }
                FilledTonalButton(
                    onClick = { practiceLoop.setEndHere() },
                    enabled = startMs != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.practice_loop_set_b))
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = { practiceLoop.clear() },
                enabled = startMs != null || endMs != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.practice_loop_clear))
            }
            Spacer(Modifier.height(16.dp))
        }

        item {
            Text(
                text = stringResource(R.string.practice_loop_speed),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PRACTICE_SPEEDS.forEach { value ->
                    FilterChip(
                        label = { Text("x$value") },
                        selected = kotlin.math.abs(speed - value) < 0.01f,
                        onClick = {
                            speed = value
                            runCatching {
                                playerConnection.player.playbackParameters =
                                    PlaybackParameters(value, value)
                            }
                        },
                    )
                }
            }
            Text(
                text = stringResource(R.string.practice_loop_speed_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.practice_loop_count_in),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = stringResource(R.string.practice_loop_count_in_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = countIn,
                    onCheckedChange = onCountInChange,
                )
            }
            Spacer(Modifier.height(20.dp))
        }

        item {
            OutlinedButton(
                onClick = {
                    practiceLoop.endSession()
                    bottomSheetPageState.dismiss()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.practice_loop_end))
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun PracticeLoopStatRow(
    label: String,
    value: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
