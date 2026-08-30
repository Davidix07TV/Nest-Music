//
//  InnerTubeModels.swift
//  Nest Music
//
//  Raw Codable DTOs matching the InnerTube (music.youtube.com/youtubei/v1)
//  JSON responses. Field names mirror the Kotlin `innertube` module.
//

import Foundation

// MARK: - Context / request bodies

struct InnerTubeContext: Encodable {
    struct Client: Encodable {
        let clientName: String
        let clientVersion: String
        let userAgent: String?
        let osName: String?
        let osVersion: String?
        let deviceMake: String?
        let deviceModel: String?
        let androidSdkVersion: String?
        let gl: String
        let hl: String
        let visitorData: String?
    }
    struct ThirdParty: Encodable {
        let embedUrl: String
    }
    struct Request: Encodable {
        let useSsl: Bool
    }
    struct User: Encodable {
        let lockedSafetyMode: Bool
        let onBehalfOfUser: String?
    }

    let client: Client
    let thirdParty: ThirdParty?
    let request: Request
    let user: User
}

struct SearchBody: Encodable {
    let context: InnerTubeContext
    let query: String?
    let params: String?
}

struct BrowseBody: Encodable {
    let context: InnerTubeContext
    let browseId: String?
    let params: String?
    let continuation: String?
}

struct PlayerBody: Encodable {
    struct PlaybackContext: Encodable {
        struct ContentPlaybackContext: Encodable {
            let signatureTimestamp: Int
        }
        let contentPlaybackContext: ContentPlaybackContext
    }
    struct ServiceIntegrityDimensions: Encodable {
        let poToken: String
    }

    let context: InnerTubeContext
    let videoId: String
    let playlistId: String?
    let playbackContext: PlaybackContext?
    let serviceIntegrityDimensions: ServiceIntegrityDimensions?
    let contentCheckOk: Bool
    let racyCheckOk: Bool
}

// MARK: - Search response

struct SearchResponse: Decodable {
    let contents: Contents?
    let continuationContents: ContinuationContents?

    struct Contents: Decodable {
        let tabbedSearchResultsRenderer: TabbedSearchResultsRenderer?
    }
    struct TabbedSearchResultsRenderer: Decodable {
        let tabs: [Tab]?
    }
    struct Tab: Decodable {
        let tabRenderer: TabRenderer?
    }
    struct TabRenderer: Decodable {
        let content: Content?
    }
    struct Content: Decodable {
        let sectionListRenderer: SectionListRenderer?
    }
    struct SectionListRenderer: Decodable {
        let contents: [Section]?
    }
    struct Section: Decodable {
        let musicShelfRenderer: MusicShelfRenderer?
        let itemSectionRenderer: ItemSectionRenderer?
    }
    struct MusicShelfRenderer: Decodable {
        let title: Runs?
        let contents: [ShelfContent]?
    }
    struct ShelfContent: Decodable {
        let musicResponsiveListItemRenderer: MusicResponsiveListItemRenderer?
    }
    struct ItemSectionRenderer: Decodable {
        let contents: [ShelfContent]?
    }
    struct ContinuationContents: Decodable {
        let musicShelfContinuation: MusicShelfContinuation?
    }
    struct MusicShelfContinuation: Decodable {
        let contents: [ShelfContent]?
    }
}

// MARK: - Browse (home) response

struct BrowseResponse: Decodable {
    let contents: BrowseContents?

    struct BrowseContents: Decodable {
        let singleColumnBrowseResultsRenderer: SingleColumnBrowseResultsRenderer?
    }
    struct SingleColumnBrowseResultsRenderer: Decodable {
        let tabs: [BrowseTab]?
    }
    struct BrowseTab: Decodable {
        let tabRenderer: BrowseTabRenderer?
    }
    struct BrowseTabRenderer: Decodable {
        let content: BrowseContent?
    }
    struct BrowseContent: Decodable {
        let sectionListRenderer: BrowseSectionListRenderer?
    }
    struct BrowseSectionListRenderer: Decodable {
        let contents: [BrowseSection]?
    }
    struct BrowseSection: Decodable {
        let musicCarouselShelfRenderer: MusicCarouselShelfRenderer?
        let musicShelfRenderer: SearchResponse.Section.MusicShelfRenderer?
    }
    struct MusicCarouselShelfRenderer: Decodable {
        let header: CarouselHeader?
        let contents: [SearchResponse.Section.ShelfContent]?
    }
    struct CarouselHeader: Decodable {
        let musicCarouselShelfBasicHeaderRenderer: MusicCarouselShelfBasicHeaderRenderer?
    }
    struct MusicCarouselShelfBasicHeaderRenderer: Decodable {
        let title: Runs?
    }
}

// MARK: - Shared list item renderer

struct MusicResponsiveListItemRenderer: Decodable {
    let badges: [Badge]?
    let flexColumns: [FlexColumn]?
    let thumbnail: ThumbnailRenderer?
    let navigationEndpoint: NavigationEndpoint?
    let playlistItemData: PlaylistItemData?
    let overlay: Overlay?

    struct Badge: Decodable {
        let musicInlineBadgeRenderer: MusicInlineBadgeRenderer?
    }
    struct MusicInlineBadgeRenderer: Decodable {
        let icon: Icon?
    }
    struct Icon: Decodable {
        let iconType: String?
    }

    struct FlexColumn: Decodable {
        let musicResponsiveListItemFlexColumnRenderer: FlexColumnRenderer?
        let musicResponsiveListItemFixedColumnRenderer: FlexColumnRenderer?
    }
    struct FlexColumnRenderer: Decodable {
        let text: Runs?
    }

    struct PlaylistItemData: Decodable {
        let videoId: String?
        let playlistSetVideoId: String?
    }

    struct Overlay: Decodable {
        let musicItemThumbnailOverlayRenderer: MusicItemThumbnailOverlayRenderer?
    }
    struct MusicItemThumbnailOverlayRenderer: Decodable {
        let content: OverlayContent?
    }
    struct OverlayContent: Decodable {
        let musicPlayButtonRenderer: MusicPlayButtonRenderer?
    }
    struct MusicPlayButtonRenderer: Decodable {
        let playNavigationEndpoint: NavigationEndpoint?
    }
}

struct ThumbnailRenderer: Decodable {
    let musicThumbnailRenderer: MusicThumbnailRenderer?
    let croppedSquareThumbnailRenderer: MusicThumbnailRenderer?

    struct MusicThumbnailRenderer: Decodable {
        let thumbnail: Thumbnails?
    }
    struct Thumbnails: Decodable {
        let thumbnails: [Thumbnail]?
    }
    struct Thumbnail: Decodable {
        let url: String?
        let width: Int?
        let height: Int?
    }

    var bestUrl: String? {
        musicThumbnailRenderer?.thumbnail?.thumbnails?.last?.url
            ?? croppedSquareThumbnailRenderer?.thumbnail?.thumbnails?.last?.url
    }
}

struct Runs: Decodable {
    let runs: [Run]?
}

struct Run: Decodable {
    let text: String?
    let navigationEndpoint: NavigationEndpoint?
}

struct NavigationEndpoint: Decodable {
    let watchEndpoint: WatchEndpoint?
    let browseEndpoint: BrowseEndpoint?

    struct WatchEndpoint: Decodable {
        let videoId: String?
        let playlistId: String?
    }
    struct BrowseEndpoint: Decodable {
        let browseId: String?
        let browseEndpointContextSupportedConfigs: BrowseEndpointContextSupportedConfigs?
    }
    struct BrowseEndpointContextSupportedConfigs: Decodable {
        let browseEndpointContextMusicConfig: BrowseEndpointContextMusicConfig?
    }
    struct BrowseEndpointContextMusicConfig: Decodable {
        let pageType: String?
    }
}

// MARK: - Player response

struct PlayerResponse: Decodable {
    let playabilityStatus: PlayabilityStatus
    let streamingData: StreamingData?
    let videoDetails: VideoDetails?

    struct PlayabilityStatus: Decodable {
        let status: String?
        let reason: String?
    }
    struct StreamingData: Decodable {
        let formats: [Format]?
        let adaptiveFormats: [Format]?
    }
    struct Format: Decodable {
        let itag: Int?
        let url: String?
        let mimeType: String?
        let bitrate: Int?
        let contentLength: String?
        let audioQuality: String?
        let averageBitrate: Int?
        let audioSampleRate: String?
        let audioChannels: Int?
        let signatureCipher: String?
        let cipher: String?
    }
    struct VideoDetails: Decodable {
        let videoId: String?
        let title: String?
        let author: String?
        let lengthSeconds: String?
        let thumbnail: PlayerThumbnail?
    }
    struct PlayerThumbnail: Decodable {
        let thumbnails: [ThumbnailRenderer.Thumbnails.Thumbnail]?
    }
}
