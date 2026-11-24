/*
 * Connects Nest Music's LyricClock + SweptLyricLine + NestLyricsPanel to Nest Music's
 * LyricsViewModel, LyricsEntity, Romanization, AI/DeepL Translation, and LyricsMenu.
 */

package com.nestmusic.music.ui.player.nest

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nestmusic.music.LocalDatabase
import com.nestmusic.music.LocalListenTogetherManager
import com.nestmusic.music.LocalPlayerConnection
import com.nestmusic.music.ui.component.LocalMenuState
import com.nestmusic.music.R
import com.nestmusic.music.constants.AiProviderKey
import com.nestmusic.music.constants.AiSystemPromptKey
import com.nestmusic.music.constants.DeeplApiKey
import com.nestmusic.music.constants.DeeplFormalityKey
import com.nestmusic.music.constants.EnableBetterLyricsKey
import com.nestmusic.music.constants.LyricsClickKey
import com.nestmusic.music.constants.LyricsRomanizeCyrillicByLineKey
import com.nestmusic.music.constants.LyricsRomanizeList
import com.nestmusic.music.constants.OpenRouterApiKey
import com.nestmusic.music.constants.OpenRouterBaseUrlKey
import com.nestmusic.music.constants.OpenRouterDefaultBaseUrl
import com.nestmusic.music.constants.OpenRouterDefaultModel
import com.nestmusic.music.constants.OpenRouterModelKey
import com.nestmusic.music.constants.RespectAgentPositioningKey
import com.nestmusic.music.constants.ShowIntervalIndicatorKey
import com.nestmusic.music.constants.TranslateLanguageKey
import com.nestmusic.music.constants.TranslateModeKey
import com.nestmusic.music.db.entities.LyricsEntity
import com.nestmusic.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.nestmusic.music.di.LyricsHelperEntryPoint
import com.nestmusic.music.lyrics.BetterLyricsProvider
import com.nestmusic.music.lyrics.LyricsEntry
import com.nestmusic.music.lyrics.LyricsTranslationHelper
import com.nestmusic.music.lyrics.LyricsUtils
import com.nestmusic.music.lyrics.lyricsTextLooksSynced
import com.nestmusic.music.ui.menu.LyricsMenu
import com.nestmusic.music.ui.screens.settings.defaultList
import com.nestmusic.music.utils.rememberPreference
import com.nestmusic.music.viewmodels.LyricsViewModel
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.withContext
import timber.log.Timber

@Stable
internal class NestLyricsSession(
    val lines: List<NestLyricLine>,
    val rawEntries: List<LyricsEntry>,
    val clock: State<Long>,
    val isSynced: Boolean,
    val isLoading: Boolean,
    val lyricsUnavailable: Boolean,
    val showRomanized: Boolean,
    val showTranslated: Boolean,
    val onToggleRomanized: (() -> Unit)?,
    val onToggleTranslated: (() -> Unit)?,
    val onSeekToMs: (Long) -> Unit,
    val onOpenLyricsMenu: () -> Unit,
)

@Composable
internal fun rememberNestLyricsSession(
    sliderPositionProvider: () -> Long?,
    activeForTranslation: Boolean = true,
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
): NestLyricsSession? {
    val playerConnection = LocalPlayerConnection.current ?: return null
    val database = LocalDatabase.current
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val scope = rememberCoroutineScope()
    val listenTogetherManager = LocalListenTogetherManager.current
    val isGuest = listenTogetherManager?.isInRoom == true && !listenTogetherManager.isHost

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val currentSong by playerConnection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val currentLyricsEntity by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)

    val changeLyrics by rememberPreference(LyricsClickKey, true)
    val enableBetterLyrics by rememberPreference(EnableBetterLyricsKey, true)
    val romanizeLyricsList = rememberPreference(LyricsRomanizeList, "")
    val romanizeCyrillicByLine by rememberPreference(LyricsRomanizeCyrillicByLineKey, false)
    val respectAgentPositioning by rememberPreference(RespectAgentPositioningKey, true)
    val showIntervalIndicator by rememberPreference(ShowIntervalIndicatorKey, true)

    val betterLyricsUpgradeChecked = remember { HashSet<String>() }

    LaunchedEffect(mediaMetadata?.id, currentLyricsEntity, enableBetterLyrics) {
        val metadata = mediaMetadata ?: return@LaunchedEffect
        val existing = currentLyricsEntity
        if (existing == null) {
            delay(350)
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
                    Timber.tag("NestLyricsHost").w(e, "Failed to fetch lyrics for %s", metadata.id)
                }
            }
        } else if (enableBetterLyrics &&
            existing.id == metadata.id &&
            existing.provider != "BetterLyrics" &&
            existing.provider != "Manual" &&
            !existing.lyrics.contains('<') &&
            betterLyricsUpgradeChecked.add(metadata.id)
        ) {
            withContext(Dispatchers.IO) {
                try {
                    val cleanedTitle = LyricsUtils.cleanTitleForSearch(metadata.title)
                    val betterResult = BetterLyricsProvider.getLyrics(
                        context = context.applicationContext,
                        id = metadata.id,
                        title = cleanedTitle,
                        artist = metadata.artists.joinToString { it.name },
                        duration = metadata.duration,
                        album = metadata.album?.title,
                    )
                    val rawBetter = betterResult.getOrNull()?.takeIf { it.isNotBlank() }
                    if (rawBetter != null) {
                        val filtered = LyricsUtils.filterLyricsCreditLines(rawBetter)
                        database.query {
                            upsert(LyricsEntity(metadata.id, filtered, "BetterLyrics"))
                        }
                    }
                } catch (e: Exception) {
                    Timber.tag("NestLyricsHost").d(e, "BetterLyrics upgrade skipped for %s", metadata.id)
                }
            }
        }
    }

    val openRouterApiKey by rememberPreference(OpenRouterApiKey, "")
    val deeplApiKey by rememberPreference(DeeplApiKey, "")
    val aiProvider by rememberPreference(AiProviderKey, "OpenRouter")
    val openRouterBaseUrl by rememberPreference(OpenRouterBaseUrlKey, OpenRouterDefaultBaseUrl)
    val openRouterModel by rememberPreference(OpenRouterModelKey, OpenRouterDefaultModel)
    val translateLanguage by rememberPreference(TranslateLanguageKey, "en")
    val translateMode by rememberPreference(TranslateModeKey, "Literal")
    val deeplFormality by rememberPreference(DeeplFormalityKey, "default")
    val aiSystemPrompt by rememberPreference(AiSystemPromptKey, "")

    val enabledLanguages = remember(romanizeLyricsList.value) {
        if (romanizeLyricsList.value.isEmpty()) {
            defaultList
        } else {
            romanizeLyricsList.value.split(",").mapNotNull { entry ->
                val parts = entry.split(":")
                if (parts.size == 2) parts[0] to parts[1].toBoolean() else null
            }
        }.filter { it.second }.map { it.first }
    }

    val lyricsText = remember(currentLyricsEntity) { currentLyricsEntity?.lyrics?.trim() }
    val rawEntries by lyricsViewModel.lines.collectAsStateWithLifecycle()

    LaunchedEffect(lyricsText, enabledLanguages, romanizeCyrillicByLine, showIntervalIndicator) {
        lyricsViewModel.processLyrics(
            lyricsText,
            enabledLanguages,
            romanizeCyrillicByLine,
            showIntervalIndicator,
        )
    }

    val isSynced = remember(lyricsText) { lyricsTextLooksSynced(lyricsText) }
    val lyricsUnavailable = lyricsText == LYRICS_NOT_FOUND
    val isLoading = currentLyricsEntity == null && mediaMetadata != null

    val nestLines = remember(rawEntries, isSynced, showIntervalIndicator, respectAgentPositioning) {
        if (lyricsUnavailable) {
            emptyList()
        } else {
            rawEntries.toNestLyricLines(
                isSynced = isSynced,
                showIntervalIndicators = showIntervalIndicator,
                respectAgentPositioning = respectAgentPositioning,
            )
        }
    }

    val lyricsOffsetMs = (currentSong?.song?.lyricsOffset ?: 0).toLong()
    val clock = rememberNestLyricClock(
        trackKey = mediaMetadata?.id ?: "none",
        player = playerConnection.player,
        isPlaying = isPlaying,
        lyricsOffsetMs = lyricsOffsetMs,
        sliderOverrideProvider = sliderPositionProvider,
    )

    var showRomanized by rememberSaveable(mediaMetadata?.id) { mutableStateOf(true) }
    var showTranslated by rememberSaveable(mediaMetadata?.id) { mutableStateOf(false) }

    DisposableEffect(activeForTranslation) {
        if (activeForTranslation) {
            LyricsTranslationHelper.setCompositionActive(true)
        }
        onDispose {
            if (activeForTranslation) {
                LyricsTranslationHelper.setCompositionActive(false)
            }
        }
    }

    LaunchedEffect(rawEntries, currentLyricsEntity, translateLanguage, translateMode) {
        if (rawEntries.isNotEmpty() && currentLyricsEntity != null) {
            LyricsTranslationHelper.loadTranslationsFromDatabase(
                lyrics = rawEntries,
                lyricsEntity = currentLyricsEntity!!,
                targetLanguage = translateLanguage,
                mode = translateMode,
            )
        }
    }

    val hasAnyRomanized = remember(rawEntries) {
        rawEntries.any { !it.romanizedTextFlow.value.isNullOrBlank() }
    }

    val onToggleTranslated: () -> Unit = {
        val effectiveApiKey = if (aiProvider == "DeepL") deeplApiKey else openRouterApiKey
        if (!showTranslated) {
            val alreadyTranslated = rawEntries.any { !it.translatedTextFlow.value.isNullOrBlank() }
            if (alreadyTranslated) {
                showTranslated = true
            } else if (effectiveApiKey.isNotBlank() && rawEntries.isNotEmpty()) {
                showTranslated = true
                LyricsTranslationHelper.translateLyrics(
                    lyrics = rawEntries,
                    targetLanguage = translateLanguage,
                    apiKey = openRouterApiKey,
                    baseUrl = openRouterBaseUrl,
                    model = openRouterModel,
                    mode = translateMode,
                    scope = scope,
                    context = context,
                    provider = aiProvider,
                    deeplApiKey = deeplApiKey,
                    deeplFormality = deeplFormality,
                    useStreaming = true,
                    songId = currentSong?.id ?: mediaMetadata?.id ?: "",
                    database = database,
                    systemPrompt = aiSystemPrompt,
                )
            } else {
                Toast.makeText(context, context.getString(R.string.ai_api_key_required), Toast.LENGTH_SHORT).show()
            }
        } else {
            showTranslated = false
        }
    }

    LaunchedEffect(
        activeForTranslation,
        rawEntries,
        aiProvider,
        openRouterApiKey,
        deeplApiKey,
        openRouterBaseUrl,
        openRouterModel,
        translateLanguage,
        translateMode,
        deeplFormality,
        aiSystemPrompt,
        currentSong,
    ) {
        if (!activeForTranslation) return@LaunchedEffect
        LyricsTranslationHelper.manualTrigger.collectLatest {
            onToggleTranslated()
        }
    }

    val onOpenLyricsMenu: () -> Unit = {
        val meta = mediaMetadata
        if (meta != null) {
            menuState.show {
                LyricsMenu(
                    lyricsProvider = { currentLyricsEntity },
                    songProvider = { currentSong?.song },
                    mediaMetadataProvider = { meta },
                    onDismiss = menuState::dismiss,
                )
            }
        }
    }

    val onSeekToMs: (Long) -> Unit = { lineTimeMs ->
        if (changeLyrics && !isGuest) {
            val target = (lineTimeMs - lyricsOffsetMs).coerceAtLeast(0L)
            runCatching { playerConnection.player.seekTo(target) }
        }
    }

    return NestLyricsSession(
        lines = nestLines,
        rawEntries = rawEntries,
        clock = clock,
        isSynced = isSynced,
        isLoading = isLoading,
        lyricsUnavailable = lyricsUnavailable,
        showRomanized = showRomanized,
        showTranslated = showTranslated,
        onToggleRomanized = if (hasAnyRomanized || enabledLanguages.isNotEmpty()) {
            { showRomanized = !showRomanized }
        } else {
            null
        },
        onToggleTranslated = onToggleTranslated,
        onSeekToMs = onSeekToMs,
        onOpenLyricsMenu = onOpenLyricsMenu,
    )
}

/**
 * Standalone Nest Music Song Lyrics host used when [Lyrics] is rendered in NEST player
 * or fullscreen LyricsScreen with `LyricsAnimationStyle.APPLE`.
 */
@Composable
fun NestStandaloneLyricsHost(
    sliderPositionProvider: () -> Long?,
    showLyrics: Boolean,
    modifier: Modifier = Modifier,
    bottomPadding: Dp = 32.dp,
    lyricsViewModel: LyricsViewModel = hiltViewModel(),
) {
    val session = rememberNestLyricsSession(
        sliderPositionProvider = sliderPositionProvider,
        activeForTranslation = showLyrics,
        lyricsViewModel = lyricsViewModel,
    ) ?: return

    NestLyricsPanel(
        lines = session.lines,
        rawEntries = session.rawEntries,
        clock = session.clock,
        isSynced = session.isSynced,
        isLoading = session.isLoading,
        lyricsUnavailable = session.lyricsUnavailable,
        showRomanized = session.showRomanized,
        showTranslated = session.showTranslated,
        onToggleRomanized = session.onToggleRomanized,
        onToggleTranslated = session.onToggleTranslated,
        onSeekToMs = session.onSeekToMs,
        onLineLongPress = { session.onOpenLyricsMenu() },
        bottomPadding = bottomPadding,
        modifier = modifier,
    )
}
