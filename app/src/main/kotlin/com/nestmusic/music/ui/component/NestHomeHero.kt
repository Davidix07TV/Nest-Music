package com.nestmusic.music.ui.component

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import com.nestmusic.music.LocalPlayerConnection
import com.nestmusic.music.R
import com.nestmusic.music.ui.theme.NestAppleRed
import com.nestmusic.music.utils.joinToArtistString

/**
 * Apple Music / iOS 26 Home Editorial Top Pick & Quick Action Pills.
 * Replaces the legacy sunset gradient banner with a refined neutral dark/light frosted card
 * tinted subtly by the active album cover and crisp #FA2D48 Apple Music Red accents.
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

    Column(modifier = modifier.fillMaxWidth()) {
        // Sleek Apple Music quick-action pill chips
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AppleQuickChip(
                icon = R.drawable.history,
                label = stringResource(R.string.history),
                onClick = onHistory,
                modifier = Modifier.weight(1f),
            )
            AppleQuickChip(
                icon = R.drawable.stats,
                label = stringResource(R.string.stats),
                onClick = onStats,
                modifier = Modifier.weight(1f),
            )
            AppleQuickChip(
                icon = R.drawable.mic,
                label = stringResource(R.string.recognize_music),
                onClick = onRecognize,
                modifier = Modifier.weight(1f),
            )
            AppleQuickChip(
                icon = R.drawable.search,
                label = stringResource(R.string.search),
                onClick = onSearch,
                modifier = Modifier.weight(1f),
            )
        }

        val metadata = mediaMetadata
        if (metadata != null) {
            Spacer(Modifier.height(16.dp))

            val cardShape = RoundedCornerShape(22.dp)
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(cardShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .border(
                            width = 0.6.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                            shape = cardShape,
                        ).clickable {
                            if (playbackState == Player.STATE_ENDED) {
                                playerConnection.player.seekTo(0, 0)
                                playerConnection.player.playWhenReady = true
                            } else {
                                playerConnection.togglePlayPause()
                            }
                        },
            ) {
                // Subtle blurred album glow behind the glass card on Android 12+
                val art = metadata.thumbnailUrl
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && art != null) {
                    AsyncImage(
                        model = art,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .matchParentSize()
                                .graphicsLayer {
                                    scaleX = 1.4f
                                    scaleY = 1.4f
                                    alpha = 0.32f
                                }.blur(56.dp),
                    )
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(
                                            Color.Black.copy(alpha = 0.64f),
                                            Color.Black.copy(alpha = 0.36f),
                                        ),
                                    ),
                                ),
                    )
                }

                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(72.dp)
                                .shadow(10.dp, RoundedCornerShape(14.dp))
                                .clip(RoundedCornerShape(14.dp))
                                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                    ) {
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
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                    Modifier
                                        .align(Alignment.Center)
                                        .size(28.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.now_playing).uppercase(),
                            fontSize = 10.5.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.85.sp,
                            color = NestAppleRed,
                            maxLines = 1,
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            text = metadata.title,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && art != null) Color.White else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = metadata.artists.joinToArtistString(" ${stringResource(R.string.and)} ") { it.name },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && art != null) Color.White.copy(alpha = 0.72f) else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.width(12.dp))

                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(42.dp)
                                .clip(CircleShape)
                                .background(NestAppleRed),
                    ) {
                        Icon(
                            painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                            contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                            tint = Color.White,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AppleQuickChip(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier =
            modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(
                    width = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(16.dp),
                ).clickable(onClick = onClick)
                .padding(horizontal = 8.dp, vertical = 11.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
            tint = NestAppleRed,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.5.sp,
            ),
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
