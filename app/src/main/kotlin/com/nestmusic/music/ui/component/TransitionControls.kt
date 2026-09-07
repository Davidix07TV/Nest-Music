/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.component

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nestmusic.music.R
import com.nestmusic.music.playback.MixStyle
import com.nestmusic.music.playback.TransitionStore
import com.nestmusic.music.playback.displayStyle

/** A stable route for an editor opened from a concrete outgoing/incoming pair. */
fun transitionRoute(prevId: String, nextId: String): String =
    "transition/${Uri.encode(prevId)}/${Uri.encode(nextId)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MixToggleButton(
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(50),
        color =
            if (selected) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f)
            },
        contentColor =
            if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.tune),
                contentDescription = null,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = stringResource(R.string.mix),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Compact Spotify-like transition chip. With no saved override the type is
 * Auto, so every pair remains discoverable without forcing a custom transition.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitionPill(
    prevId: String,
    nextId: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val revision by TransitionStore.revision.collectAsStateWithLifecycle()
    val style = remember(revision, prevId, nextId) {
        TransitionStore.get(prevId, nextId)?.style?.displayStyle() ?: MixStyle.AUTO
    }
    val label =
        when (style) {
            MixStyle.AUTO -> stringResource(R.string.transition_style_auto)
            MixStyle.FADE -> stringResource(R.string.transition_style_fade)
            MixStyle.RISE -> stringResource(R.string.transition_style_rise)
            MixStyle.BLEND, MixStyle.SMOOTH -> stringResource(R.string.transition_style_blend)
        }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.tune),
                contentDescription = null,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
