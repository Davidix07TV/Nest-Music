/*
 * Adapted from Nest Music (Nest Music) PlayerQueue.kt under GPL-3.0,
 * backed by Nest Music's ExoPlayer Timeline.Window queue.
 */

package com.nestmusic.music.ui.player.nest

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.nestmusic.music.LocalPlayerConnection
import com.nestmusic.music.R
import com.nestmusic.music.extensions.metadata
import com.nestmusic.music.ui.component.BottomSheetState
import com.nestmusic.music.ui.component.LocalBottomSheetPageState
import com.nestmusic.music.ui.component.LocalMenuState
import com.nestmusic.music.ui.menu.QueueMenu
import com.nestmusic.music.ui.utils.ShowMediaInfo
import com.nestmusic.music.utils.makeTimeString

@Composable
internal fun NestQueuePanel(
    playerBottomSheetState: BottomSheetState,
    shuffleEnabled: Boolean = false,
    repeatMode: Int = androidx.media3.common.Player.REPEAT_MODE_OFF,
    autoplayEnabled: Boolean = true,
    onToggleShuffle: (() -> Unit)? = null,
    onToggleRepeat: (() -> Unit)? = null,
    onToggleAutoplay: (() -> Unit)? = null,
    bottomPadding: Dp = 20.dp,
    modifier: Modifier = Modifier,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val bottomSheetPageState = LocalBottomSheetPageState.current
    val haptics = LocalHapticFeedback.current
    val ink = LocalPlayerInk.current.value
    val alphas = LocalPlayerAlphas.current

    val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()

    val upcomingWindows = remember(queueWindows, currentWindowIndex) {
        if (currentWindowIndex in queueWindows.indices) {
            queueWindows.subList(currentWindowIndex + 1, queueWindows.size)
        } else {
            queueWindows
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(currentWindowIndex) {
        if (upcomingWindows.isNotEmpty()) {
            listState.scrollToItem(0)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(top = 6.dp),
    ) {
        if (onToggleShuffle != null && onToggleRepeat != null && onToggleAutoplay != null) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = NEST_GUTTER, vertical = 6.dp),
            ) {
                QueueModePill(
                    active = shuffleEnabled,
                    label = stringResource(R.string.shuffle),
                    onClick = onToggleShuffle,
                    modifier = Modifier.weight(1f),
                ) { tint ->
                    Icon(
                        imageVector = NestPlayerIcons.PlayerShuffle,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(17.dp),
                    )
                }
                QueueModePill(
                    active = repeatMode != androidx.media3.common.Player.REPEAT_MODE_OFF,
                    label = if (repeatMode == androidx.media3.common.Player.REPEAT_MODE_ONE) "Repeat 1" else stringResource(R.string.repeat),
                    onClick = onToggleRepeat,
                    modifier = Modifier.weight(1f),
                ) { tint ->
                    Icon(
                        imageVector = NestPlayerIcons.PlayerRepeat,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(17.dp),
                    )
                }
                QueueModePill(
                    active = autoplayEnabled,
                    label = stringResource(R.string.autoplay),
                    onClick = onToggleAutoplay,
                    modifier = Modifier.weight(1f),
                ) { tint ->
                    Icon(
                        imageVector = NestPlayerIcons.PlayerAutoplay,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(17.dp),
                    )
                }
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = NEST_GUTTER, vertical = 6.dp),
        ) {
            Column {
                Text(
                    text = "Playing Next",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.3).sp,
                    ),
                    color = ink.copy(alpha = alphas.primary),
                )
                if (upcomingWindows.isNotEmpty()) {
                    Text(
                        text = "${upcomingWindows.size} tracks",
                        style = MaterialTheme.typography.labelMedium,
                        color = ink.copy(alpha = 0.56f),
                    )
                }
            }
        }

        if (upcomingWindows.isEmpty()) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
            ) {
                Text(
                    text = stringResource(R.string.nest_queue_empty),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                    color = ink.copy(alpha = 0.56f),
                )
            }
        } else {
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(
                    start = NEST_GUTTER,
                    end = NEST_GUTTER - 8.dp,
                    top = 6.dp,
                    bottom = bottomPadding + 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
            ) {
                itemsIndexed(
                    items = upcomingWindows,
                    key = { idx, window -> "${window.uid}_$idx" },
                ) { offsetIdx, window ->
                    val targetWindowIndex = if (currentWindowIndex in queueWindows.indices) {
                        currentWindowIndex + 1 + offsetIdx
                    } else {
                        offsetIdx
                    }
                    val meta = window.mediaItem.metadata ?: return@itemsIndexed

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable {
                                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                playerConnection.player.seekToDefaultPosition(targetWindowIndex)
                            }
                            .padding(vertical = 7.dp, horizontal = 4.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(ink.copy(alpha = 0.10f)),
                        ) {
                            AsyncImage(
                                model = meta.thumbnailUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = meta.title,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    ),
                                    color = ink.copy(alpha = alphas.primary),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (meta.explicit) {
                                    Spacer(Modifier.width(5.dp))
                                    NestExplicitBadge(color = ink)
                                }
                            }
                            val artistLine = remember(meta.artists, meta.duration) {
                                val names = meta.artists.joinToString(", ") { it.name }
                                val dur = if (meta.duration > 0) makeTimeString(meta.duration * 1000L) else null
                                if (dur != null && names.isNotBlank()) "$names · $dur" else names
                            }
                            if (artistLine.isNotBlank()) {
                                Text(
                                    text = artistLine,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.5.sp),
                                    color = ink.copy(alpha = 0.62f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                menuState.show {
                                    QueueMenu(
                                        mediaMetadata = meta,
                                        playerBottomSheetState = playerBottomSheetState,
                                        onShowDetailsDialog = {
                                            bottomSheetPageState.show {
                                                ShowMediaInfo(meta.id)
                                            }
                                        },
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                            modifier = Modifier.size(36.dp),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_horiz),
                                contentDescription = null,
                                tint = ink.copy(alpha = 0.62f),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueModePill(
    active: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable (androidx.compose.ui.graphics.Color) -> Unit,
) {
    val ink = LocalPlayerInk.current.value
    val haptics = LocalHapticFeedback.current
    val bg = if (active) ink.copy(alpha = 0.90f) else ink.copy(alpha = 0.14f)
    val contentTint = if (active) androidx.compose.ui.graphics.Color(0xFF141418) else ink.copy(alpha = 0.88f)
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 10.dp, vertical = 9.dp),
    ) {
        icon(contentTint)
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            color = contentTint,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

