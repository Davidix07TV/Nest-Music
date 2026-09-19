/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.viewmodels

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nestmusic.innertube.YouTube
import com.nestmusic.innertube.models.SongItem
import com.nestmusic.innertube.models.WatchEndpoint.WatchEndpointMusicSupportedConfigs.WatchEndpointMusicConfig.Companion.MUSIC_VIDEO_TYPE_ATV
import com.nestmusic.music.R
import com.nestmusic.music.api.AiPlaylistGenerator
import com.nestmusic.music.api.OpenRouterService
import com.nestmusic.music.constants.AiProviderKey
import com.nestmusic.music.constants.HideVideoSongsKey
import com.nestmusic.music.constants.OpenRouterApiKey
import com.nestmusic.music.constants.OpenRouterBaseUrlKey
import com.nestmusic.music.constants.OpenRouterModelKey
import com.nestmusic.music.db.MusicDatabase
import com.nestmusic.music.db.entities.Playlist
import com.nestmusic.music.db.entities.PlaylistEntity
import com.nestmusic.music.models.toMediaMetadata
import com.nestmusic.music.utils.dataStore
import com.nestmusic.music.utils.get
import com.nestmusic.music.utils.reportException
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.util.Collections
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

sealed interface AiPlaylistUiState {
    data object Idle : AiPlaylistUiState
    data object Thinking : AiPlaylistUiState
    data class Building(val found: Int, val total: Int) : AiPlaylistUiState
    data class Error(val message: String) : AiPlaylistUiState
    data class Success(val playlistId: String) : AiPlaylistUiState
}

@HiltViewModel
class AiPlaylistViewModel
@Inject
constructor(
    @ApplicationContext val context: Context,
    private val database: MusicDatabase,
) : ViewModel() {
    private val _uiState = MutableStateFlow<AiPlaylistUiState>(AiPlaylistUiState.Idle)
    val uiState: StateFlow<AiPlaylistUiState> = _uiState.asStateFlow()

    /**
     * True when the configured AI provider can be used for chat completions
     * (i.e. it is not DeepL and a key or custom base URL is set).
     */
    fun isConfigured(): Boolean {
        val provider = context.dataStore.get(AiProviderKey, "OpenRouter")
        if (provider == "DeepL") return false
        val apiKey = context.dataStore.get(OpenRouterApiKey, "")
        val baseUrl = context.dataStore.get(OpenRouterBaseUrlKey, "")
        return if (provider == "Custom") baseUrl.isNotBlank() else apiKey.isNotBlank()
    }

    fun generate(
        prompt: String,
        songCount: Int,
    ) {
        if (_uiState.value is AiPlaylistUiState.Thinking || _uiState.value is AiPlaylistUiState.Building) return

        viewModelScope.launch {
            val provider = context.dataStore.get(AiProviderKey, "OpenRouter")
            val apiKey = context.dataStore.get(OpenRouterApiKey, "")
            val baseUrl = context.dataStore.get(OpenRouterBaseUrlKey, "")
            val model = context.dataStore.get(OpenRouterModelKey, "")
            val hideVideoSongs = context.dataStore.get(HideVideoSongsKey, false)

            if (provider == "DeepL") {
                _uiState.value = AiPlaylistUiState.Error(context.getString(R.string.ai_playlist_error_deepl))
                return@launch
            }
            if (!isConfigured()) {
                _uiState.value = AiPlaylistUiState.Error(context.getString(R.string.ai_playlist_error_not_configured))
                return@launch
            }

            _uiState.value = AiPlaylistUiState.Thinking

            // 1) Ask the AI for a playlist plan (title + search queries)
            val planResult =
                OpenRouterService
                    .complete(
                        systemPrompt = AiPlaylistGenerator.systemPrompt(songCount),
                        userPrompt = AiPlaylistGenerator.userPrompt(prompt),
                        apiKey = apiKey,
                        baseUrl = baseUrl,
                        model = model,
                    ).mapCatching { AiPlaylistGenerator.parsePlan(it) }

            if (planResult.isFailure) {
                reportException(planResult.exceptionOrNull() ?: Exception("AI plan failed"))
                _uiState.value = AiPlaylistUiState.Error(context.getString(R.string.ai_playlist_error_ai))
                return@launch
            }
            val plan = planResult.getOrThrow()

            // 2) Resolve each query to a real song on YouTube Music
            _uiState.value = AiPlaylistUiState.Building(0, songCount)
            val queries = plan.queries.take(songCount + 5)
            val found = CopyOnWriteArrayList<SongItem>()
            val foundCount = AtomicInteger(0)
            val seen = Collections.synchronizedSet(mutableSetOf<String>())

            try {
                coroutineScope {
                    val semaphore = Semaphore(4)
                    queries.forEach { query ->
                        launch {
                            semaphore.withPermit {
                                if (foundCount.get() >= songCount) return@launch
                                YouTube
                                    .search(query, YouTube.SearchFilter.FILTER_SONG)
                                    .onSuccess { result ->
                                        val candidates =
                                            result.items
                                                .filterIsInstance<SongItem>()
                                                .filter { song ->
                                                    if (!hideVideoSongs) true
                                                    else song.musicVideoType.isNullOrBlank() || song.musicVideoType == MUSIC_VIDEO_TYPE_ATV
                                                }
                                        val song = candidates.firstOrNull { seen.add(it.id) } ?: return@onSuccess
                                        if (foundCount.incrementAndGet() > songCount) return@onSuccess
                                        found.add(song)
                                        withContext(Dispatchers.IO) {
                                            try {
                                                database.insert(song.toMediaMetadata())
                                            } catch (e: Exception) {
                                                reportException(e)
                                            }
                                        }
                                        _uiState.value = AiPlaylistUiState.Building(foundCount.get(), songCount)
                                    }
                                    .onFailure {
                                        // Individual query failures are expected and skippable
                                    }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                reportException(e)
            }

            if (found.isEmpty()) {
                _uiState.value = AiPlaylistUiState.Error(context.getString(R.string.ai_playlist_error_no_songs))
                return@launch
            }

            // 3) Create the local playlist and add the resolved songs
            withContext(Dispatchers.IO) {
                val entity =
                    PlaylistEntity(
                        name = plan.title,
                        bookmarkedAt = LocalDateTime.now(),
                        isEditable = true,
                    )
                database.insert(entity)
                database.addSongToPlaylist(
                    Playlist(
                        playlist = entity,
                        songCount = 0,
                        songThumbnails = emptyList(),
                    ),
                    found.map { it.id },
                )
                _uiState.value = AiPlaylistUiState.Success(entity.id)
            }
        }
    }

    fun reset() {
        _uiState.value = AiPlaylistUiState.Idle
    }
}
