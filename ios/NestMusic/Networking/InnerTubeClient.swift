//
//  InnerTubeClient.swift
//  Nest Music
//
//  A Swift reimplementation of the `innertube` Kotlin module: a minimal client
//  for music.youtube.com's InnerTube endpoints (search, browse, player).
//  Header/context values are kept in sync with the Android app.
//

import Foundation

enum InnerTubeError: LocalizedError {
    case invalidResponse
    case notPlayable(String)

    var errorDescription: String? {
        switch self {
        case .invalidResponse:
            return "Invalid response from YouTube Music."
        case .notPlayable(let reason):
            return reason.isEmpty ? "This track is not playable." : reason
        }
    }
}

/// A concrete InnerTube client definition (clientName/version/id/UA), mirroring
/// the Kotlin `YouTubeClient` companion object.
struct YTClient {
    let clientName: String
    let clientVersion: String
    let clientId: String
    let userAgent: String
    let osName: String?
    let osVersion: String?
    let deviceMake: String?
    let deviceModel: String?
    let androidSdkVersion: String?
    let useSignatureTimestamp: Bool
    let isEmbedded: Bool

    static let userAgentWeb = "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:140.0) Gecko/20100101 Firefox/140.0"

    /// YouTube Music web client — used for search & browse.
    static let webRemix = YTClient(
        clientName: "WEB_REMIX",
        clientVersion: "1.20260114.03.00",
        clientId: "67",
        userAgent: userAgentWeb,
        osName: nil, osVersion: nil, deviceMake: nil, deviceModel: nil, androidSdkVersion: nil,
        useSignatureTimestamp: true,
        isEmbedded: false
    )

    /// iOS client — frequently returns deciphered (direct) audio URLs.
    static let ios = YTClient(
        clientName: "IOS",
        clientVersion: "21.03.1",
        clientId: "5",
        userAgent: "com.google.ios.youtube/21.03.1 (iPhone16,2; U; CPU iOS 18_2 like Mac OS X;)",
        osName: nil,
        osVersion: "18.2.22C152",
        deviceMake: nil, deviceModel: nil, androidSdkVersion: nil,
        useSignatureTimestamp: false,
        isEmbedded: false
    )

    /// Android VR client — no auth required, good fallback for playback.
    static let androidVR = YTClient(
        clientName: "ANDROID_VR",
        clientVersion: "1.61.48",
        clientId: "28",
        userAgent: "com.google.android.apps.youtube.vr.oculus/1.61.48 (Linux; U; Android 12; en_US; Oculus Quest 3; Build/SQ3A.220605.009.A1; Cronet/132.0.6808.3)",
        osName: "Android",
        osVersion: "12",
        deviceMake: "Oculus",
        deviceModel: "Quest 3",
        androidSdkVersion: "32",
        useSignatureTimestamp: false,
        isEmbedded: false
    )
}

final class InnerTubeClient {
    static let shared = InnerTubeClient()

    private let baseURL = URL(string: "https://music.youtube.com/youtubei/v1/")!
    private let session: URLSession

    private let jsonEncoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = []
        return encoder
    }()

    private init() {
        let config = URLSessionConfiguration.default
        config.timeoutIntervalForRequest = 30
        config.timeoutIntervalForResource = 60
        config.httpAdditionalHeaders = ["User-Agent": YTClient.userAgentWeb]
        session = URLSession(configuration: config)
    }

    // MARK: - Context

    private func context(for client: YTClient, visitorData: String?, videoId: String? = nil) -> InnerTubeContext {
        let thirdParty: InnerTubeContext.ThirdParty? = client.isEmbedded
            ? InnerTubeContext.ThirdParty(embedUrl: "https://www.youtube.com/watch?v=\(videoId ?? "")")
            : nil

        return InnerTubeContext(
            client: .init(
                clientName: client.clientName,
                clientVersion: client.clientVersion,
                userAgent: nil,
                osName: client.osName,
                osVersion: client.osVersion,
                deviceMake: client.deviceMake,
                deviceModel: client.deviceModel,
                androidSdkVersion: client.androidSdkVersion,
                gl: Locale.current.region?.identifier ?? "US",
                hl: Locale.current.language.languageCode?.identifier ?? "en",
                visitorData: visitorData
            ),
            thirdParty: thirdParty,
            request: .init(useSsl: true),
            user: .init(lockedSafetyMode: false, onBehalfOfUser: nil)
        )
    }

    // MARK: - HTTP plumbing

    private func post<T: Decodable>(_ path: String, client: YTClient, body: Data) async throws -> T {
        let url = baseURL.appendingPathComponent(path)
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("1", forHTTPHeaderField: "X-Goog-Api-Format-Version")
        request.setValue(client.clientId, forHTTPHeaderField: "X-YouTube-Client-Name")
        request.setValue(client.clientVersion, forHTTPHeaderField: "X-YouTube-Client-Version")
        request.setValue("https://music.youtube.com", forHTTPHeaderField: "X-Origin")
        request.setValue("https://music.youtube.com/", forHTTPHeaderField: "Referer")
        request.setValue(client.userAgent, forHTTPHeaderField: "User-Agent")
        request.httpBody = body

        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse, (200..<300).contains(http.statusCode) else {
            throw InnerTubeError.invalidResponse
        }
        let decoder = JSONDecoder()
        return try decoder.decode(T.self, from: data)
    }

    // MARK: - Search

    /// Perform a music search, returning mixed results (songs/artists/albums/playlists).
    func search(query: String) async throws -> [SearchItem] {
        let body = SearchBody(
            context: context(for: .webRemix, visitorData: nil),
            query: query,
            params: nil
        )
        let response: SearchResponse = try await post("search", client: .webRemix, body: try jsonEncoder.encode(body))

        var items: [SearchItem] = []
        let sections = response.contents?.tabbedSearchResultsRenderer?.tabs?
            .first?.tabRenderer?.content?.sectionListRenderer?.contents ?? []

        for section in sections {
            if let shelf = section.musicShelfRenderer, let contents = shelf.contents {
                items.append(contentsOf: Self.searchItems(from: contents.compactMap(\.musicResponsiveListItemRenderer)))
            } else if let itemSection = section.itemSectionRenderer, let contents = itemSection.contents {
                items.append(contentsOf: Self.searchItems(from: contents.compactMap(\.musicResponsiveListItemRenderer)))
            }
        }

        // Deduplicate by id, preserving order.
        var seen = Set<String>()
        return items.filter { seen.insert($0.id).inserted }
    }

    /// Fetch search suggestions for a prefix (used by the search field).
    func searchSuggestions(query: String) async throws -> [String] {
        struct SuggestionBody: Encodable {
            let context: InnerTubeContext
            let input: String
        }
        struct SuggestionResponse: Decodable {
            let contents: [Content]?
            struct Content: Decodable {
                let searchSuggestionsSectionRenderer: SearchSuggestionsSectionRenderer?
            }
            struct SearchSuggestionsSectionRenderer: Decodable {
                let contents: [SuggestionContent]?
            }
            struct SuggestionContent: Decodable {
                let searchSuggestionRenderer: SearchSuggestionRenderer?
            }
            struct SearchSuggestionRenderer: Decodable {
                let suggestion: Suggestion?
            }
            struct Suggestion: Decodable {
                let runs: [Run]?
            }
        }

        let body = SuggestionBody(context: context(for: .webRemix, visitorData: nil), input: query)
        let response: SuggestionResponse = try await post(
            "music/get_search_suggestions",
            client: .webRemix,
            body: try jsonEncoder.encode(body)
        )
        return response.contents?
            .first?.searchSuggestionsSectionRenderer?.contents?
            .compactMap { $0.searchSuggestionRenderer?.suggestion?.runs?.map { $0.text ?? "" }.joined() } ?? []
    }

    // MARK: - Home (quick picks)

    func homeShelves() async throws -> [HomeShelf] {
        let body = BrowseBody(
            context: context(for: .webRemix, visitorData: nil),
            browseId: "FEmusic_home",
            params: nil,
            continuation: nil
        )
        let response: BrowseResponse = try await post("browse", client: .webRemix, body: try jsonEncoder.encode(body))

        let sections = response.contents?.singleColumnBrowseResultsRenderer?.tabs?
            .first?.tabRenderer?.content?.sectionListRenderer?.contents ?? []

        var shelves: [HomeShelf] = []
        for section in sections {
            if let carousel = section.musicCarouselShelfRenderer {
                let title = carousel.header?.musicCarouselShelfBasicHeaderRenderer?.title?.runs?
                    .compactMap { $0.text }.joined()
                let songs = carousel.contents?
                    .compactMap(\.musicResponsiveListItemRenderer)
                    .compactMap(Self.song) ?? []
                if !songs.isEmpty {
                    shelves.append(HomeShelf(title: title, songs: songs))
                }
            } else if let shelf = section.musicShelfRenderer {
                let title = shelf.title?.runs?.compactMap { $0.text }.joined()
                let songs = shelf.contents?
                    .compactMap(\.musicResponsiveListItemRenderer)
                    .compactMap(Self.song) ?? []
                if !songs.isEmpty {
                    shelves.append(HomeShelf(title: title, songs: songs))
                }
            }
        }
        return shelves
    }

    // MARK: - Player (stream resolution)

    /// Resolve a playable audio stream URL for `videoId`, trying multiple clients in order.
    func streamURL(for videoId: String) async throws -> URL {
        let clients: [YTClient] = [.ios, .androidVR, .webRemix]
        var lastError: Error = InnerTubeError.notPlayable("")

        for client in clients {
            do {
                let response = try await player(videoId: videoId, client: client)
                if response.playabilityStatus.status != "OK" {
                    lastError = InnerTubeError.notPlayable(response.playabilityStatus.reason ?? "")
                    continue
                }
                if let url = Self.bestAudioURL(from: response.streamingData) {
                    return url
                }
                lastError = InnerTubeError.notPlayable("No audio stream found.")
            } catch {
                lastError = error
            }
        }
        throw lastError
    }

    private func player(videoId: String, client: YTClient) async throws -> PlayerResponse {
        var signatureTimestamp: Int? = nil
        if client.useSignatureTimestamp {
            signatureTimestamp = try? await Self.signatureTimestamp()
        }
        let body = PlayerBody(
            context: context(for: client, visitorData: nil, videoId: videoId),
            videoId: videoId,
            playlistId: nil,
            playbackContext: signatureTimestamp.map {
                .init(contentPlaybackContext: .init(signatureTimestamp: $0))
            },
            serviceIntegrityDimensions: nil,
            contentCheckOk: true,
            racyCheckOk: true
        )
        return try await post("player", client: client, body: try jsonEncoder.encode(body))
    }

    /// Prefer the highest-bitrate audio adaptive format with a direct (deciphered) URL.
    private static func bestAudioURL(from streamingData: PlayerResponse.StreamingData?) -> URL? {
        guard let streamingData else { return nil }
        let candidates = (streamingData.adaptiveFormats ?? []) + (streamingData.formats ?? [])
        let audio = candidates
            .filter { ($0.mimeType?.contains("audio") ?? false) || ($0.audioQuality != nil) }
            .compactMap { format -> (Int, URL)? in
                guard let urlString = format.url, let url = URL(string: urlString) else { return nil }
                let bitrate = format.averageBitrate ?? format.bitrate ?? 0
                return (bitrate, url)
            }
            .sorted { $0.0 > $1.0 }
        return audio.first?.1
    }

    /// Extract the current `sts` (signature timestamp) from music.youtube.com/sw.js_data.
    private static func signatureTimestamp() async throws -> Int {
        let url = URL(string: "https://music.youtube.com/sw.js_data")!
        let (data, _) = try await URLSession.shared.data(from: url)
        let text = String(decoding: data, as: UTF8.self)
        // sw.js_data is a JS object literal, e.g. `{...sts:20023,...}`.
        if let range = text.range(of: #"sts:\s*(\d+)"#, options: .regularExpression),
           let num = Int(text[range].replacingOccurrences(of: "sts:", with: "").trimmingCharacters(in: .whitespaces)) {
            return num
        }
        // Fallback regex over the whole blob.
        if let match = text.range(of: #"\d+"#, options: .regularExpression),
           let num = Int(text[match]) {
            return num
        }
        return 0
    }
}

// MARK: - Response -> domain mapping

private extension InnerTubeClient {
    static func searchItems(from renderers: [MusicResponsiveListItemRenderer]) -> [SearchItem] {
        renderers.compactMap(Self.searchItem)
    }

    static func searchItem(_ r: MusicResponsiveListItemRenderer) -> SearchItem? {
        if let song = song(r) { return .song(song) }
        if let artist = artist(r) { return .artist(artist) }
        if let album = album(r) { return .album(album) }
        if let playlist = playlist(r) { return .playlist(playlist) }
        return nil
    }

    static func song(_ r: MusicResponsiveListItemRenderer) -> Song? {
        let pageType = r.navigationEndpoint?.browseEndpoint?
            .browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType

        // Non-song types bail out early so we don't mislabel them.
        if pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_LIBRARY_ARTIST"
            || pageType == "MUSIC_PAGE_TYPE_ALBUM" || pageType == "MUSIC_PAGE_TYPE_AUDIOBOOK"
            || pageType == "MUSIC_PAGE_TYPE_PLAYLIST" || pageType == "MUSIC_PAGE_TYPE_USER_CHANNEL"
            || pageType == "MUSIC_PAGE_TYPE_PODCAST_SHOW_DETAIL_PAGE" {
            return nil
        }

        guard let videoId = r.playlistItemData?.videoId
            ?? r.navigationEndpoint?.watchEndpoint?.videoId
            ?? r.overlay?.musicItemThumbnailOverlayRenderer?.content?.musicPlayButtonRenderer?
                .playNavigationEndpoint?.watchEndpoint?.videoId
        else { return nil }

        let titleColumn = r.flexColumns?.first?.musicResponsiveListItemFlexColumnRenderer
        let title = titleColumn?.text?.runs?.first?.text ?? ""

        // Artists live in the subtitle (second) column.
        let subtitleRuns = r.flexColumns?.dropFirst()
            .first?.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?? []
        let artists = artists(from: subtitleRuns)
        let durationText = duration(from: subtitleRuns)

        // Album is the run in the subtitle that links to an album browse page.
        let album: AlbumRef? = subtitleRuns.first(where: { run in
            run.navigationEndpoint?.browseEndpoint?.browseEndpointContextSupportedConfigs?
                .browseEndpointContextMusicConfig?.pageType == "MUSIC_PAGE_TYPE_ALBUM"
        }).flatMap { run -> AlbumRef? in
            guard let id = run.navigationEndpoint?.browseEndpoint?.browseId else { return nil }
            return AlbumRef(name: run.text ?? "", browseId: id)
        }

        let isExplicit = r.badges?.contains {
            $0.musicInlineBadgeRenderer?.icon?.iconType == "MUSIC_EXPLICIT_BADGE"
        } ?? false

        return Song(
            videoId: videoId,
            title: title.isEmpty ? "Unknown" : title,
            artists: artists,
            album: album,
            durationText: durationText,
            thumbnail: r.thumbnail?.bestUrl,
            isExplicit: isExplicit
        )
    }

    static func artist(_ r: MusicResponsiveListItemRenderer) -> Artist? {
        let pageType = r.navigationEndpoint?.browseEndpoint?
            .browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
        guard pageType == "MUSIC_PAGE_TYPE_ARTIST" || pageType == "MUSIC_PAGE_TYPE_LIBRARY_ARTIST",
              let browseId = r.navigationEndpoint?.browseEndpoint?.browseId else { return nil }
        let name = r.flexColumns?.first?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.first?.text ?? ""
        return Artist(browseId: browseId, name: name.isEmpty ? "Unknown" : name, thumbnail: r.thumbnail?.bestUrl)
    }

    static func album(_ r: MusicResponsiveListItemRenderer) -> Album? {
        let pageType = r.navigationEndpoint?.browseEndpoint?
            .browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
        guard pageType == "MUSIC_PAGE_TYPE_ALBUM" || pageType == "MUSIC_PAGE_TYPE_AUDIOBOOK",
              let browseId = r.navigationEndpoint?.browseEndpoint?.browseId else { return nil }
        let title = r.flexColumns?.first?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.first?.text ?? ""
        let subtitle = r.flexColumns?.dropFirst().first?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?
            .compactMap { $0.text }.joined(separator: " ")
        return Album(browseId: browseId, title: title.isEmpty ? "Unknown" : title, artist: subtitle, thumbnail: r.thumbnail?.bestUrl)
    }

    static func playlist(_ r: MusicResponsiveListItemRenderer) -> Playlist? {
        let pageType = r.navigationEndpoint?.browseEndpoint?
            .browseEndpointContextSupportedConfigs?.browseEndpointContextMusicConfig?.pageType
        guard pageType == "MUSIC_PAGE_TYPE_PLAYLIST",
              let browseId = r.navigationEndpoint?.browseEndpoint?.browseId else { return nil }
        let title = r.flexColumns?.first?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.first?.text ?? ""
        let subtitle = r.flexColumns?.dropFirst().first?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?
            .compactMap { $0.text }.joined(separator: " ")
        return Playlist(browseId: browseId, title: title.isEmpty ? "Unknown" : title, subtitle: subtitle, thumbnail: r.thumbnail?.bestUrl)
    }

    /// Split the subtitle runs on the "•" separator and extract artist names.
    private static func artists(from runs: [Run]) -> [ArtistRef] {
        var segments: [[Run]] = []
        var current: [Run] = []
        for run in runs {
            if run.text?.trimmingCharacters(in: .whitespaces) == "•" {
                segments.append(current)
                current = []
            } else {
                current.append(run)
            }
        }
        segments.append(current)

        guard let first = segments.first else { return [] }
        return first.compactMap { run -> ArtistRef? in
            let name = run.text?.trimmingCharacters(in: .whitespaces) ?? ""
            guard !name.isEmpty else { return nil }
            return ArtistRef(name: name, browseId: run.navigationEndpoint?.browseEndpoint?.browseId)
        }
    }

    /// The duration is conventionally the final run of the subtitle line.
    private static func duration(from runs: [Run]) -> String? {
        runs.last?.text?.trimmingCharacters(in: .whitespaces).nilIfEmpty
    }
}

private extension String {
    var nilIfEmpty: String? { isEmpty ? nil : self }
}
