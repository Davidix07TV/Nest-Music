/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nestmusic.music.R
import com.nestmusic.music.ui.theme.NestSunsetBrush
import com.nestmusic.music.ui.theme.NestWater

/**
 * Asks whether to use the logo look or the classic interface.
 * First launch treats a dismiss as "keep the new UI". Settings passes
 * [onDismiss] so closing the dialog does not change the current choice.
 */
@Composable
fun NestUiChoiceDialog(
    onChoose: (useNestUi: Boolean) -> Unit,
    onDismiss: () -> Unit = { onChoose(true) },
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Brand header
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(NestSunsetBrush),
            ) {
                Icon(
                    painter = painterResource(R.drawable.small_icon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(34.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.nest_ui_choice_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(R.string.nest_ui_choice_message),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(20.dp))

            NestUiChoiceCard(
                preview = {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(NestSunsetBrush),
                    ) {
                        Spacer(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(Color.White),
                        )
                        Spacer(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(14.dp)
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color.Transparent, NestWater),
                                    ),
                                ),
                        )
                    }
                },
                title = stringResource(R.string.nest_ui_new),
                badge = stringResource(R.string.nest_ui_choice_recommended),
                description = stringResource(R.string.nest_ui_new_desc),
                highlight = true,
                onClick = { onChoose(true) },
            )

            Spacer(modifier = Modifier.height(10.dp))

            NestUiChoiceCard(
                preview = {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFFE8EAED))
                            .padding(8.dp),
                    ) {
                        Spacer(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFFED5564)),
                        )
                        Spacer(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(Color.White),
                        )
                    }
                },
                title = stringResource(R.string.nest_ui_legacy),
                badge = null,
                description = stringResource(R.string.nest_ui_legacy_desc),
                highlight = false,
                onClick = { onChoose(false) },
            )
        }
    }
}

@Composable
private fun NestUiChoiceCard(
    preview: @Composable () -> Unit,
    title: String,
    badge: String?,
    description: String,
    highlight: Boolean,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clip(
                if (highlight) RoundedCornerShape(20.dp)
                else RoundedCornerShape(20.dp),
            )
            .background(
                if (highlight) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            )
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        preview()

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                if (badge != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (highlight) {
            Spacer(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
            )
        }
    }
}
