//
//  Models.swift
//  Nest Music
//
//  App-level domain models used across the UI and playback engine.
//  These are intentionally separate from the raw InnerTube response DTOs.
//

import Foundation

/// A playable song. Maps directly onto Android's `SongItem`.
struct Song: Identifiable, Hashable, Sendable {
    let videoId: String
    let title: String
    let artists: [ArtistRef]
    let album: AlbumRef?
    let durationText: String?
    let thumbnail: String?
    let isExplicit: Bool

    var id: String { videoId }

    var subtitle: String {
        artists.map(\.name).joined(separator: ", ")
    }
}

/// A lightweight artist reference (name + optional browse id).
struct ArtistRef: Hashable, Sendable {
    let name: String
    let browseId: String?
}

struct AlbumRef: Hashable, Sendable {
    let name: String
    let browseId: String
}

struct Artist: Identifiable, Hashable, Sendable {
    let browseId: String
    let name: String
    let thumbnail: String?

    var id: String { browseId }
}

struct Album: Identifiable, Hashable, Sendable {
    let browseId: String
    let title: String
    let artist: String?
    let thumbnail: String?

    var id: String { browseId }
}

struct Playlist: Identifiable, Hashable, Sendable {
    let browseId: String
    let title: String
    let subtitle: String?
    let thumbnail: String?

    var id: String { browseId }
}

/// A single search result entry, mirroring Android's mixed search results
/// (songs, artists, albums and playlists all returned by one query).
enum SearchItem: Identifiable, Hashable, Sendable {
    case song(Song)
    case artist(Artist)
    case album(Album)
    case playlist(Playlist)

    var id: String {
        switch self {
        case .song(let song): return "song:\(song.videoId)"
        case .artist(let artist): return "artist:\(artist.browseId)"
        case .album(let album): return "album:\(album.browseId)"
        case .playlist(let playlist): return "playlist:\(playlist.browseId)"
        }
    }
}

/// A shelf on the home screen (quick picks, moods & genres, ...).
struct HomeShelf: Identifiable, Sendable {
    let id = UUID()
    let title: String?
    let songs: [Song]
}

extension Song {
    static let placeholder = Song(
        videoId: "",
        title: "—",
        artists: [ArtistRef(name: "—", browseId: nil)],
        album: nil,
        durationText: nil,
        thumbnail: nil,
        isExplicit: false
    )
}
