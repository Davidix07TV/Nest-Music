//
//  NestMusicTests.swift
//  Nest Music
//

import XCTest
@testable import NestMusic

final class NestMusicTests: XCTestCase {

    func testSongSubtitleJoinsArtists() {
        let song = Song(
            videoId: "abc",
            title: "Track",
            artists: [
                ArtistRef(name: "Artist A", browseId: nil),
                ArtistRef(name: "Artist B", browseId: nil),
            ],
            album: nil,
            durationText: "3:21",
            thumbnail: nil,
            isExplicit: false
        )
        XCTAssertEqual(song.subtitle, "Artist A, Artist B")
    }

    func testSearchItemIDsAreUnique() {
        let song = Song(videoId: "v1", title: "T", artists: [], album: nil,
                        durationText: nil, thumbnail: nil, isExplicit: false)
        let artist = Artist(browseId: "a1", name: "A", thumbnail: nil)
        XCTAssertNotEqual(SearchItem.song(song).id, SearchItem.artist(artist).id)
    }
}
