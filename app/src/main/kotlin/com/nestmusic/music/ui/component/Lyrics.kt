/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nestmusic.music.LocalDatabase
import com.nestmusic.music.LocalPlayerConnection
import com.nestmusic.music.constants.ExperimentalLyricsKey
import com.nestmusic.music.db.entities.LyricsEntity
import com.nestmusic.music.di.LyricsHelperEntryPoint
import com.nestmusic.music.utils.rememberPreference
import com.nestmusic.music.viewmodels.LyricsViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Ensures the current track's lyrics are present in the database, fetching them when missing.
 *
 * This used to live only inside the NEST player's InlineLyricsView, so the Apple Music player —
 * which renders [Lyrics] directly — showed the loading shimmer forever for uncached tracks.
 * Every player style must call this (the Apple player does while its lyrics pane is visible).
 */
@Composable
fun EnsureCurrentLyricsLoaded(enabled: Boolean = true) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current
    val context = LocalContext.current
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(mediaMetadata?.id, currentLyrics, enabled) {
        val metadata = mediaMetadata ?: return@LaunchedEffect
        if (!LyricsFetchPolicy.shouldFetch(enabled, metadata.id, currentLyrics != null)) return@LaunchedEffect
        delay(500)
        withContext(Dispatchers.IO) {
            try {
                val entryPoint = EntryPointAccessors.fromApplication(
                    context.applicationContext,
                    LyricsHelperEntryPoint::class.java,
                )
                val fetched = entryPoint.lyricsHelper().getLyrics(metadata)
                database.query {
                    upsert(LyricsEntity(metadata.id, fetched.lyrics, fetched.provider))
                }
            } catch (e: Exception) {
                // Previously swallowed silently ("// Handle error"): a failed fetch looked
                // exactly like "still loading" forever.
                Timber.tag("Lyrics").w(e, "Failed to fetch lyrics for %s", metadata.id)
            }
        }
    }
}

@Composable
fun Lyrics(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    showLyrics: Boolean,
    lyricsViewModel: LyricsViewModel = hiltViewModel()
) {
    val (experimentalLyrics, _) = rememberPreference(key = ExperimentalLyricsKey, defaultValue = true)

    if (experimentalLyrics) {
        ExperimentalLyrics(
            sliderPositionProvider = sliderPositionProvider,
            modifier = modifier,
            showLyrics = showLyrics,
            lyricsViewModel = lyricsViewModel
        )
    } else {
        OriginalLyrics(
            sliderPositionProvider = sliderPositionProvider,
            modifier = modifier,
            showLyrics = showLyrics
        )
    }
}
