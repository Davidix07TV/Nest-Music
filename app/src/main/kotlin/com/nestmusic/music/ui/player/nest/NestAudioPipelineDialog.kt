package com.nestmusic.music.ui.player.nest

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nestmusic.music.R
import com.nestmusic.music.constants.DolbyAtmosKey
import com.nestmusic.music.constants.SpatialAudioKey
import com.nestmusic.music.playback.audio.NestAudioOutputStatus
import com.nestmusic.music.playback.audio.NestDeviceCodecs
import com.nestmusic.music.playback.audio.rememberNestAudioOutputs
import com.nestmusic.music.utils.rememberPreference

/**
 * Apple Music / iOS 26 frosted Audio Quality & Dolby Atmos Signal Path modal card.
 * Opens when tapping the Dolby Atmos / Lossless / Spatial Audio badge in Now Playing.
 */
@Composable
fun NestAudioPipelineDialog(
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val snapshot by NestAudioOutputStatus.pipeline.collectAsState()
    val outputs = rememberNestAudioOutputs()
    val activeOutput = outputs.firstOrNull { it.isSelected } ?: outputs.firstOrNull()

    val (dolbyAtmosEnabled, onDolbyAtmosChange) = rememberPreference(DolbyAtmosKey, defaultValue = true)
    val (spatialAudioEnabled, onSpatialAudioChange) = rememberPreference(SpatialAudioKey, defaultValue = false)

    val hwAtmosSupported = remember(context) { NestDeviceCodecs.playsDolbyAtmos(context) }
    val spatializerAvailable = remember(context) { NestDeviceCodecs.hasPlatformSpatializer(context) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.46f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onDismiss,
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                modifier =
                    Modifier
                        .padding(horizontal = 24.dp)
                        .widthIn(max = 340.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xF0222228),
                                    Color(0xF516161B),
                                ),
                            ),
                        ).border(
                            width = 0.75.dp,
                            brush =
                                Brush.verticalGradient(
                                    listOf(
                                        Color.White.copy(alpha = 0.24f),
                                        Color.White.copy(alpha = 0.07f),
                                    ),
                                ),
                            shape = RoundedCornerShape(28.dp),
                        ).clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = {},
                        ).padding(22.dp),
            ) {
                // Header badge + title
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White.copy(alpha = 0.12f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            painter = painterResource(
                                if (snapshot.isDolbyAtmos || dolbyAtmosEnabled || spatialAudioEnabled) {
                                    R.drawable.nest_ic_dolby_atmos
                                } else {
                                    R.drawable.nest_ic_lossless
                                },
                            ),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = snapshot.badgeLabel,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-0.3).sp,
                        )
                        Text(
                            text = snapshot.detailSubtitle,
                            color = Color.White.copy(alpha = 0.62f),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Signal path steps
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(Color.White.copy(alpha = 0.06f))
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SignalNodeRow(
                        stage = "SOURCE",
                        value = buildString {
                            append(snapshot.codecLabel)
                            if (snapshot.sampleRateHz > 0) {
                                val khz = snapshot.sampleRateHz / 1000f
                                val khzStr =
                                    if (snapshot.sampleRateHz % 1000 == 0) {
                                        "${snapshot.sampleRateHz / 1000}"
                                    } else {
                                        String.format("%.1f", khz)
                                    }
                                append(" · ${snapshot.bitDepth}-bit/$khzStr kHz")
                            }
                        },
                        dotColor = Color(0xFF34C759),
                    )
                    SignalNodeRow(
                        stage = "SPATIAL ENGINE",
                        value = when {
                            snapshot.channelCount > 2 && hwAtmosSupported -> "Hardware Dolby Atmos (${snapshot.channelCount}ch)"
                            dolbyAtmosEnabled || spatialAudioEnabled ->
                                if (spatializerAvailable) "Dolby Spatializer + Haas Binaural"
                                else "Mid/Side + 15ms Haas Binaural"
                            else -> "Bit-Accurate Direct Stereo"
                        },
                        dotColor = if (dolbyAtmosEnabled || spatialAudioEnabled) Color(0xFFFA2D48) else Color.White.copy(alpha = 0.45f),
                    )
                    SignalNodeRow(
                        stage = "OUTPUT",
                        value = activeOutput?.name ?: "System Speaker",
                        dotColor = Color(0xFF0A84FF),
                    )
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
                Spacer(Modifier.height(12.dp))

                // Dolby Atmos Switch
                PipelineSwitchRow(
                    title = "Dolby Atmos",
                    subtitle = if (hwAtmosSupported) {
                        "Hardware E-AC-3 JOC & Spatializer ready"
                    } else {
                        "Binaural Atmos rendering on stereo tracks"
                    },
                    checked = dolbyAtmosEnabled,
                    onCheckedChange = onDolbyAtmosChange,
                )

                Spacer(Modifier.height(10.dp))

                // Spatial Audio Switch
                PipelineSwitchRow(
                    title = "Spatial Audio",
                    subtitle = "Immersive 3D soundstage & crossfeed",
                    checked = spatialAudioEnabled,
                    onCheckedChange = onSpatialAudioChange,
                )

                Spacer(Modifier.height(16.dp))

                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Done",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun SignalNodeRow(
    stage: String,
    value: String,
    dotColor: Color,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(dotColor),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stage,
                color = Color.White.copy(alpha = 0.44f),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.8.sp,
            )
            Text(
                text = value,
                color = Color.White.copy(alpha = 0.94f),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun PipelineSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.56f),
                fontSize = 12.sp,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors =
                SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Color(0xFFFA2D48),
                    uncheckedThumbColor = Color.White.copy(alpha = 0.85f),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.18f),
                    uncheckedBorderColor = Color.Transparent,
                ),
        )
    }
}
