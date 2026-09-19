/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.nestmusic.music.LocalPlayerAwareWindowInsets
import com.nestmusic.music.R
import com.nestmusic.music.viewmodels.AiPlaylistUiState
import com.nestmusic.music.viewmodels.AiPlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiPlaylistScreen(
    navController: NavController,
    viewModel: AiPlaylistViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var prompt by rememberSaveable { mutableStateOf("") }
    var songCount by rememberSaveable { mutableStateOf(15) }
    var configured by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        configured = viewModel.isConfigured()
    }

    val isBusy = uiState is AiPlaylistUiState.Thinking || uiState is AiPlaylistUiState.Building
    val isIdle = !isBusy

    LaunchedEffect(uiState) {
        if (uiState is AiPlaylistUiState.Success) {
            val playlistId = (uiState as AiPlaylistUiState.Success).playlistId
            viewModel.reset()
            navController.navigate("local_playlist/$playlistId") {
                popUpTo("ai_playlist")
            }
        }
    }

    val suggestions =
        listOf(
            R.string.ai_playlist_suggestion_1,
            R.string.ai_playlist_suggestion_2,
            R.string.ai_playlist_suggestion_3,
            R.string.ai_playlist_suggestion_4,
        )

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(LocalPlayerAwareWindowInsets.current),
    ) {
        if (!configured) {
            AiNotConfiguredCard(onOpenSettings = { navController.navigate("settings/ai") })
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = prompt,
                onValueChange = { prompt = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 6,
                placeholder = { Text(stringResource(R.string.ai_playlist_prompt_placeholder)) },
                enabled = isIdle,
                shape = RoundedCornerShape(16.dp),
            )

            Text(
                text = stringResource(R.string.ai_playlist_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = stringResource(R.string.ai_playlist_suggestions),
                style = MaterialTheme.typography.titleSmall,
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(suggestions) { suggestionRes ->
                    val suggestionText = stringResource(suggestionRes)
                    FilterChip(
                        selected = false,
                        onClick = { if (isIdle) prompt = suggestionText },
                        label = { Text(suggestionText) },
                    )
                }
            }

            Text(
                text = stringResource(R.string.ai_playlist_songs_count),
                style = MaterialTheme.typography.titleSmall,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 15, 20, 30).forEach { count ->
                    FilterChip(
                        selected = songCount == count,
                        onClick = { songCount = count },
                        label = { Text("$count") },
                    )
                }
            }

            Button(
                onClick = {
                    if (prompt.isNotBlank()) viewModel.generate(prompt.trim(), songCount)
                },
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                enabled = isIdle && prompt.isNotBlank(),
            ) {
                Text(stringResource(R.string.ai_playlist_generate))
            }

            when (val state = uiState) {
                is AiPlaylistUiState.Thinking ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(stringResource(R.string.ai_playlist_thinking))
                    }

                is AiPlaylistUiState.Building ->
                    Column(modifier = Modifier.fillMaxWidth()) {
                        LinearProgressIndicator(
                            progress = { state.found.toFloat() / state.total.coerceAtLeast(1) },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.ai_playlist_building, state.found, state.total),
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                is AiPlaylistUiState.Error ->
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )

                else -> {}
            }
        }
    }

    TopAppBar(
        title = { Text(stringResource(R.string.ai_playlist_title)) },
        navigationIcon = {
            IconButton(onClick = { navController.navigateUp() }) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = null,
                )
            }
        },
    )
}

@Composable
private fun AiNotConfiguredCard(onOpenSettings: () -> Unit) {
    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
        ) {
            Text(
                text = stringResource(R.string.ai_playlist_not_configured_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.ai_playlist_not_configured_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(12.dp))
            FilledTonalButton(onClick = onOpenSettings) {
                Icon(
                    painter = painterResource(R.drawable.discover_tune),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.ai_playlist_open_settings))
            }
        }
    }
}
