package com.nestmusic.music.ui.player.nest

import android.content.Intent
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nestmusic.music.R
import com.nestmusic.music.constants.DolbyAtmosKey
import com.nestmusic.music.constants.SpatialAudioKey
import com.nestmusic.music.playback.audio.NestAudioOutputDevice
import com.nestmusic.music.playback.audio.NestAudioOutputKind
import com.nestmusic.music.playback.audio.NestAudioOutputStatus
import com.nestmusic.music.playback.audio.rememberNestAudioOutputs
import com.nestmusic.music.utils.rememberPreference

/**
 * Apple Music / AirPlay-inspired Audio Output & Spatial Audio routing sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NestAudioOutputSheet(
    onDismiss: () -> Unit,
    onOpenPipelineDetails: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val outputs = rememberNestAudioOutputs()
    val pipeline by NestAudioOutputStatus.pipeline.collectAsState()
    var dolbyAtmos by rememberPreference(DolbyAtmosKey, defaultValue = true)
    var spatialAudio by rememberPreference(SpatialAudioKey, defaultValue = false)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xF218181D),
        contentColor = Color.White,
        dragHandle = {
            Box(
                modifier =
                    Modifier
                        .padding(top = 10.dp, bottom = 6.dp)
                        .width(38.dp)
                        .height(4.5.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.28f)),
            )
        },
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text(
                        text = "Speakers & Headphones",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.4).sp,
                    )
                    Text(
                        text = pipeline.detailSubtitle,
                        color = Color.White.copy(alpha = 0.56f),
                        fontSize = 13.sp,
                    )
                }

                Box(
                    modifier =
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color.White.copy(alpha = 0.12f))
                            .clickable {
                                onDismiss()
                                onOpenPipelineDetails()
                            }.padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = pipeline.badgeLabel,
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            // Quick Spatial / Dolby Atmos pill toggles (Apple Music Control Center style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickAudioModeChip(
                    title = "Dolby Atmos",
                    subtitle = if (dolbyAtmos) "Automatic" else "Off",
                    active = dolbyAtmos,
                    iconRes = R.drawable.nest_ic_dolby_atmos,
                    onClick = { dolbyAtmos = !dolbyAtmos },
                    modifier = Modifier.weight(1f),
                )
                QuickAudioModeChip(
                    title = "Spatial Audio",
                    subtitle = if (spatialAudio) "Binaural On" else "Stereo",
                    active = spatialAudio,
                    iconRes = R.drawable.nest_airplay,
                    onClick = { spatialAudio = !spatialAudio },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))

            // Output device list
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color.White.copy(alpha = 0.07f))
                        .border(0.6.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(20.dp)),
            ) {
                outputs.forEachIndexed { index, out ->
                    OutputDeviceRow(
                        output = out,
                        onSelect = {
                            NestAudioOutputStatus.selectOutputDevice(out.deviceInfo)
                        },
                    )
                    if (index < outputs.lastIndex) {
                        HorizontalDivider(
                            color = Color.White.copy(alpha = 0.08f),
                            modifier = Modifier.padding(start = 56.dp),
                        )
                    }
                }
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable {
                                runCatching {
                                    val intent =
                                        Intent("com.android.settings.panel.action.MEDIA_OUTPUT")
                                            .putExtra(
                                                "com.android.settings.panel.extra.PACKAGE_NAME",
                                                context.packageName,
                                            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    context.startActivity(intent)
                                }
                                onDismiss()
                            }.padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.nest_airplay),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "System Media Output Switcher",
                        color = Color.White.copy(alpha = 0.90f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun QuickAudioModeChip(
    title: String,
    subtitle: String,
    active: Boolean,
    iconRes: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val bg = if (active) Color(0xFFFA2D48).copy(alpha = 0.24f) else Color.White.copy(alpha = 0.07f)
    val border = if (active) Color(0xFFFA2D48).copy(alpha = 0.65f) else Color.White.copy(alpha = 0.10f)
    Row(
        modifier =
            modifier
                .clip(RoundedCornerShape(18.dp))
                .background(bg)
                .border(0.8.dp, border, RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(if (active) Color(0xFFFA2D48) else Color.White.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Column {
            Text(
                text = title,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = subtitle,
                color = Color.White.copy(alpha = 0.62f),
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
private fun OutputDeviceRow(
    output: NestAudioOutputDevice,
    onSelect: () -> Unit,
) {
    val iconRes =
        when (output.kind) {
            NestAudioOutputKind.BLUETOOTH -> R.drawable.nest_ic_output_buds
            NestAudioOutputKind.WIRED_HEADPHONES -> R.drawable.nest_ic_output_buds
            NestAudioOutputKind.USB_DAC -> R.drawable.nest_ic_lossless
            NestAudioOutputKind.HDMI_CAST -> R.drawable.nest_ic_output_speaker
            NestAudioOutputKind.SPEAKER -> R.drawable.nest_ic_output_phone
        }
    val kindSubtitle =
        when (output.kind) {
            NestAudioOutputKind.BLUETOOTH -> "Bluetooth Audio"
            NestAudioOutputKind.WIRED_HEADPHONES -> "Wired Headphones"
            NestAudioOutputKind.USB_DAC -> "USB Digital Audio"
            NestAudioOutputKind.HDMI_CAST -> "External Display / Receiver"
            NestAudioOutputKind.SPEAKER -> "Built-in Speaker"
        }

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onSelect)
                .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (output.isSelected) Color(0xFFFA2D48) else Color.White.copy(alpha = 0.10f),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = output.name,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = if (output.isSelected) FontWeight.Bold else FontWeight.Medium,
            )
            Text(
                text = kindSubtitle,
                color = Color.White.copy(alpha = 0.54f),
                fontSize = 12.sp,
            )
        }
        if (output.isSelected) {
            Icon(
                painter = painterResource(R.drawable.check),
                contentDescription = null,
                tint = Color(0xFFFA2D48),
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
