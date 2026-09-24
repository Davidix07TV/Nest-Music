/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.nestmusic.music.LocalPlayerConnection
import com.nestmusic.music.R
import com.nestmusic.music.extensions.togglePlayPause
import com.nestmusic.music.ui.theme.NestRaspberry
import com.nestmusic.music.ui.theme.NestSunsetBrush
import com.nestmusic.music.ui.theme.NestTangerine
import com.nestmusic.music.ui.theme.NestViolet
import com.nestmusic.music.ui.theme.NestWater
import com.nestmusic.music.utils.joinToArtistString
import java.time.LocalTime

/**
 * Editorial stage at the top of Home in Ember Room:
 * serif greeting, the record that's on, and four shortcuts.
 */
@Composable
fun NestHomeHero(
    modifier: Modifier = Modifier,
    accountName: String? = null,
    onHistory: () -> Unit = {},
    onStats: () -> Unit = {},
    onRecognize: () -> Unit = {},
    onSearch: () -> Unit = {},
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val playbackState by playerConnection.playbackState.collectAsState()

    val greetingRes = when (LocalTime.now().hour) {
        in 5..11 -> R.string.greeting_morning
        in 12..17 -> R.string.greeting_afternoon
        else -> R.string.greeting_evening
    }
    val cardShape = RoundedCornerShape(30.dp)

    Column(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(cardShape)
                .background(NestSunsetBrush),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 36.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 18.dp, top = 18.dp, end = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        val name = accountName?.takeIf { it.isNotBlank() }
                        if (name != null) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White.copy(alpha = 0.86f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(2.dp))
                        }
                        Text(
                            text = stringResource(greetingRes),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.now_playing).uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.78f),
                            letterSpacing = 1.4.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        val metadata = mediaMetadata
                        if (metadata != null) {
                            Text(
                                text = metadata.title,
                                style = MaterialTheme.typography.titleLarge,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = metadata.artists.joinToArtistString(" ${stringResource(R.string.and)} ") { it.name },
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.82f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        } else {
                            Text(
                                text = stringResource(R.string.tap_to_start),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color.White.copy(alpha = 0.82f),
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Box(modifier = Modifier.size(108.dp)) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(alpha = 0.16f)),
                        ) {
                            val art = mediaMetadata?.thumbnailUrl
                            if (art != null) {
                                AsyncImage(
                                    model = art,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            } else {
                                Icon(
                                    painter = painterResource(R.drawable.album),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(36.dp),
                                )
                            }
                        }
                        if (mediaMetadata != null) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .clickable {
                                        if (playbackState == Player.STATE_ENDED) {
                                            playerConnection.player.seekTo(0, 0)
                                            playerConnection.player.playWhenReady = true
                                        } else {
                                            playerConnection.togglePlayPause()
                                        }
                                    },
                            ) {
                                Icon(
                                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                                    contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                                    tint = NestViolet,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
            LogoWater(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(42.dp),
            )
        }

        Spacer(Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NestShortcut(R.drawable.history, R.string.history, onHistory, Modifier.weight(1f))
            NestShortcut(R.drawable.stats, R.string.stats, onStats, Modifier.weight(1f))
            NestShortcut(R.drawable.mic, R.string.recognize_music, onRecognize, Modifier.weight(1f))
            NestShortcut(R.drawable.search, R.string.search, onSearch, Modifier.weight(1f))
        }
    }
}

@Composable
private fun LogoWater(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color.Transparent, NestWater),
            ),
        )
        val ripples = listOf(
            Triple(0.16f, 0.42f, NestTangerine),
            Triple(0.38f, 0.58f, NestRaspberry),
            Triple(0.62f, 0.40f, Color(0xFF3A46C8)),
            Triple(0.82f, 0.62f, NestTangerine.copy(alpha = 0.85f)),
            Triple(0.28f, 0.82f, NestRaspberry.copy(alpha = 0.7f)),
            Triple(0.54f, 0.84f, Color(0xFF241E78)),
            Triple(0.74f, 0.86f, NestTangerine.copy(alpha = 0.55f)),
        )
        ripples.forEach { (xf, yf, color) ->
            val w = size.width * 0.2f
            val h = size.height * 0.32f
            drawOval(
                color = color,
                topLeft = Offset(size.width * xf - w / 2f, size.height * yf - h / 2f),
                size = Size(w, h),
            )
        }
    }
}

@Composable
private fun NestShortcut(
    icon: Int,
    label: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(46.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = stringResource(label),
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(label),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
