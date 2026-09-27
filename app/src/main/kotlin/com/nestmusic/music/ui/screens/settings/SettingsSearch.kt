/**
 * Metrolist Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.nestmusic.music.ui.screens.settings

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.nestmusic.music.R

/**
 * One searchable settings destination: its label, where it lives and the
 * extra words users may type to find it.
 */
data class SettingsSearchEntry(
    @StringRes val title: Int,
    @DrawableRes val icon: Int,
    val route: String,
    /** Screen the entry belongs to; shown under results, null for top level. */
    @StringRes val parent: Int? = null,
    /** Extra lowercase synonyms matched alongside the visible labels. */
    val keywords: String = "",
)

/** Flat registry of every settings destination worth finding quickly. */
val SETTINGS_SEARCH_ENTRIES: List<SettingsSearchEntry> = listOf(
    // Top level
    SettingsSearchEntry(R.string.appearance, R.drawable.palette, "settings/appearance"),
    SettingsSearchEntry(
        R.string.player_and_audio, R.drawable.play, "settings/player",
        keywords = "audio playback sound",
    ),
    SettingsSearchEntry(
        R.string.stream_sources, R.drawable.radio, "settings/stream_sources",
        keywords = "source youtube quality",
    ),
    SettingsSearchEntry(R.string.content, R.drawable.language, "settings/content"),
    SettingsSearchEntry(
        R.string.ai_lyrics_translation, R.drawable.translate, "settings/ai",
        keywords = "lyrics translate",
    ),
    SettingsSearchEntry(
        R.string.integrations, R.drawable.link, "settings/integrations",
        keywords = "services accounts",
    ),
    SettingsSearchEntry(R.string.privacy, R.drawable.security, "settings/privacy"),
    SettingsSearchEntry(R.string.storage, R.drawable.storage, "settings/storage"),
    SettingsSearchEntry(
        R.string.backup_restore, R.drawable.restore, "settings/backup_restore",
        keywords = "export import csv spotify",
    ),
    SettingsSearchEntry(
        R.string.updater, R.drawable.update, "settings/updater",
        keywords = "update version",
    ),
    SettingsSearchEntry(
        R.string.about, R.drawable.info, "settings/about",
        keywords = "version license credits",
    ),

    // Player & audio
    SettingsSearchEntry(
        R.string.crossfade, R.drawable.timer, "settings/player",
        parent = R.string.player_and_audio, keywords = "fade gapless mix",
    ),
    SettingsSearchEntry(
        R.string.sleep_timer, R.drawable.timer, "settings/player",
        parent = R.string.player_and_audio, keywords = "night alarm stop",
    ),
    SettingsSearchEntry(
        R.string.skip_silence, R.drawable.speed, "settings/player",
        parent = R.string.player_and_audio, keywords = "silence gaps",
    ),
    SettingsSearchEntry(
        R.string.audio_normalization, R.drawable.speed, "settings/player",
        parent = R.string.player_and_audio, keywords = "volume loudness replaygain",
    ),
    SettingsSearchEntry(
        R.string.equalizer, R.drawable.equalizer, "settings/player",
        parent = R.string.player_and_audio, keywords = "eq bass treble autoeq",
    ),
    SettingsSearchEntry(
        R.string.audio_quality, R.drawable.speed, "settings/player",
        parent = R.string.player_and_audio, keywords = "hq flac lossless bitrate",
    ),
    SettingsSearchEntry(
        R.string.google_cast, R.drawable.sync, "settings/player",
        parent = R.string.player_and_audio, keywords = "chromecast device",
    ),
    SettingsSearchEntry(
        R.string.auto_skip_segments, R.drawable.skip_next, "settings/player",
        parent = R.string.player_and_audio, keywords = "sponsorblock sponsor segments skip",
    ),
    SettingsSearchEntry(
        R.string.persistent_queue, R.drawable.list, "settings/player",
        parent = R.string.player_and_audio, keywords = "queue restore",
    ),
    SettingsSearchEntry(
        R.string.autoplay, R.drawable.playlist_play, "settings/player",
        parent = R.string.player_and_audio, keywords = "radio endless",
    ),
    SettingsSearchEntry(
        R.string.low_data_mode_title, R.drawable.download, "settings/player",
        parent = R.string.player_and_audio, keywords = "data saver bandwidth",
    ),
    SettingsSearchEntry(
        R.string.auto_download_on_like, R.drawable.download, "settings/player",
        parent = R.string.player_and_audio, keywords = "download offline like",
    ),
    SettingsSearchEntry(
        R.string.pause_music_when_media_is_muted, R.drawable.speed, "settings/player",
        parent = R.string.player_and_audio, keywords = "mute silent",
    ),
    SettingsSearchEntry(
        R.string.scrobble_delay_percent, R.drawable.history, "settings/integrations/lastfm",
        parent = R.string.lastfm_integration, keywords = "scrobble timing listen",
    ),

    // Content
    SettingsSearchEntry(
        R.string.hide_explicit, R.drawable.hide_image, "settings/content",
        parent = R.string.content, keywords = "explicit filter clean",
    ),
    SettingsSearchEntry(
        R.string.content_language, R.drawable.language, "settings/content",
        parent = R.string.content, keywords = "language music taste",
    ),
    SettingsSearchEntry(
        R.string.content_country, R.drawable.language, "settings/content",
        parent = R.string.content, keywords = "country region location",
    ),
    SettingsSearchEntry(
        R.string.hide_video_songs, R.drawable.hide_image, "settings/content",
        parent = R.string.content, keywords = "video only songs",
    ),
    SettingsSearchEntry(
        R.string.set_quick_picks, R.drawable.playlist_add, "settings/content",
        parent = R.string.content, keywords = "quick picks recommendations",
    ),
    SettingsSearchEntry(
        R.string.lyrics_provider_selection, R.drawable.music_note, "settings/content",
        parent = R.string.content, keywords = "lyrics source",
    ),
    SettingsSearchEntry(
        R.string.config_proxy, R.drawable.link, "settings/content",
        parent = R.string.content, keywords = "proxy network http",
    ),
    SettingsSearchEntry(
        R.string.lyrics_romanization, R.drawable.translate, "settings/content/romanization",
        parent = R.string.content, keywords = "romanization lyrics transliteration",
    ),

    // Appearance
    SettingsSearchEntry(
        R.string.player_background_style, R.drawable.palette, "settings/appearance",
        parent = R.string.appearance, keywords = "background blur gradient color",
    ),
    SettingsSearchEntry(
        R.string.nest_ui, R.drawable.palette, "settings/appearance",
        parent = R.string.appearance, keywords = "theme colors nest",
    ),
    SettingsSearchEntry(
        R.string.hide_player_thumbnail, R.drawable.favorite, "settings/appearance",
        parent = R.string.appearance, keywords = "thumbnail artwork cover",
    ),
    SettingsSearchEntry(
        R.string.crop_album_art, R.drawable.favorite, "settings/appearance",
        parent = R.string.appearance, keywords = "album art crop",
    ),
    SettingsSearchEntry(
        R.string.display_density, R.drawable.storage, "settings/appearance",
        parent = R.string.appearance, keywords = "layout size compact",
    ),
    SettingsSearchEntry(
        R.string.default_lib_chips, R.drawable.list, "settings/appearance",
        parent = R.string.appearance, keywords = "library tabs chips",
    ),

    // Privacy
    SettingsSearchEntry(
        R.string.pause_listen_history, R.drawable.history, "settings/privacy",
        parent = R.string.privacy, keywords = "history incognito",
    ),
    SettingsSearchEntry(
        R.string.disable_screenshot, R.drawable.security, "settings/privacy",
        parent = R.string.privacy, keywords = "screenshot secure",
    ),

    // Storage
    SettingsSearchEntry(
        R.string.enable_song_cache, R.drawable.download, "settings/storage",
        parent = R.string.storage, keywords = "cache songs",
    ),
    SettingsSearchEntry(
        R.string.downloaded_songs, R.drawable.download, "settings/storage",
        parent = R.string.storage, keywords = "downloads offline",
    ),
    SettingsSearchEntry(
        R.string.clear_song_cache, R.drawable.restore, "settings/storage",
        parent = R.string.storage, keywords = "clear cache delete",
    ),

    // Integrations
    SettingsSearchEntry(
        R.string.discord_integration, R.drawable.discord, "settings/integrations/discord",
        parent = R.string.integrations, keywords = "discord presence activity",
    ),
    SettingsSearchEntry(
        R.string.lastfm_integration, R.drawable.music_note, "settings/integrations/lastfm",
        parent = R.string.integrations, keywords = "scrobble lastfm last.fm",
    ),
    SettingsSearchEntry(
        R.string.listenbrainz_integration, R.drawable.history, "settings/integrations/listenbrainz",
        parent = R.string.integrations, keywords = "scrobble listenbrainz",
    ),
    SettingsSearchEntry(
        R.string.listen_together, R.drawable.sync, "settings/integrations/listen_together",
        parent = R.string.integrations, keywords = "room share friend",
    ),
)

/** A registry entry with its labels resolved for display and matching. */
data class ResolvedSettingsEntry(
    val entry: SettingsSearchEntry,
    val title: String,
    /** Lowercased haystack: title + parent label + keywords. */
    val text: String,
)

/**
 * Case-insensitive AND-match: every whitespace-separated token of [query]
 * must appear somewhere in the entry's haystack.
 */
internal fun matchSettingsEntries(
    resolved: List<ResolvedSettingsEntry>,
    query: String,
): List<ResolvedSettingsEntry> {
    val tokens = query.lowercase()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return emptyList()
    return resolved.filter { candidate -> tokens.all { candidate.text.contains(it) } }
}
