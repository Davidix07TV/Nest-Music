package com.nestmusic.innertube.pages

import com.nestmusic.innertube.models.Album
import com.nestmusic.innertube.models.AlbumItem
import com.nestmusic.innertube.models.Artist
import com.nestmusic.innertube.models.ArtistItem
import com.nestmusic.innertube.models.MusicResponsiveListItemRenderer
import com.nestmusic.innertube.models.MusicTwoRowItemRenderer
import com.nestmusic.innertube.models.PlaylistItem
import com.nestmusic.innertube.models.SongItem
import com.nestmusic.innertube.models.YTItem
import com.nestmusic.innertube.models.oddElements
import com.nestmusic.innertube.utils.parseTime

data class LibraryAlbumsPage(
    val albums: List<AlbumItem>,
    val continuation: String?,
) {
    companion object {
        fun fromMusicTwoRowItemRenderer(renderer: MusicTwoRowItemRenderer): AlbumItem? {
            return AlbumItem(
                        browseId = renderer.navigationEndpoint.browseEndpoint?.browseId ?: return null,
                        playlistId = renderer.thumbnailOverlay?.musicItemThumbnailOverlayRenderer?.content
                            ?.musicPlayButtonRenderer?.playNavigationEndpoint
                            ?.watchPlaylistEndpoint?.playlistId ?: return null,
                        title = renderer.title.runs?.firstOrNull()?.text ?: return null,
                        artists = null,
                        year = renderer.subtitle?.runs?.lastOrNull()?.text?.toIntOrNull(),
                        thumbnail = renderer.thumbnailRenderer.getThumbnailUrl() ?: return null,
                        explicit = renderer.subtitleBadges?.find {
                            it.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
                        } != null
                    )
        }
    }
}
