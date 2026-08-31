//
//  SearchView.swift
//  Nest Music
//
//  Search tab: mixed song / artist / album / playlist results with suggestions.
//

import SwiftUI

struct SearchView: View {
    let onSongTap: (Song) -> Void

    @Environment(\.nestColors) private var colors
    @State private var query = ""
    @State private var results: [SearchItem] = []
    @State private var suggestions: [String] = []
    @State private var loadState: LoadState = .idle

    // Note: this enum is intentionally named `LoadState` (not `State`) to avoid
    // shadowing SwiftUI's `State` property wrapper, which would silently turn
    // the `@State` properties above into plain (immutable) stored properties.
    private enum LoadState {
        case idle, loading, loaded, error
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if loadState == .idle {
                    MessageView(icon: "magnifyingglass", title: "Search", message: "Songs, artists, albums and playlists.")
                } else if loadState == .loading {
                    LoadingView()
                } else if loadState == .error {
                    MessageView(icon: "wifi.exclamationmark", title: "Search failed", message: "Check your connection and try again.")
                } else {
                    List {
                        ForEach(results) { item in
                            switch item {
                            case .song(let song):
                                SongRow(song: song) { onSongTap(song) }
                            case .artist(let artist):
                                ArtistRow(artist: artist)
                            case .album(let album):
                                AlbumRow(album: album)
                            case .playlist(let playlist):
                                PlaylistRow(playlist: playlist)
                            }
                        }
                        .listRowBackground(colors.background)
                    }
                    .listStyle(.plain)
                }
            }
            .navigationTitle("Search")
            .toolbarBackground(colors.background, for: .navigationBar)
            .searchable(text: $query, placement: .navigationBarDrawer(displayMode: .always), prompt: "Songs, artists, albums…")
            .onSubmit(of: .search) { Task { await runSearch() } }
        }
        .task(id: query) { await updateSuggestions() }
    }

    private func runSearch() async {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        loadState = .loading
        do {
            results = try await InnerTubeClient.shared.search(query: trimmed)
            loadState = .loaded
        } catch {
            loadState = .error
        }
    }

    private func updateSuggestions() async {
        let trimmed = query.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { suggestions = []; return }
        suggestions = (try? await InnerTubeClient.shared.searchSuggestions(query: trimmed)) ?? []
    }
}

// MARK: - Result rows

private struct ArtistRow: View {
    let artist: Artist
    @Environment(\.nestColors) private var colors

    var body: some View {
        HStack(spacing: 12) {
            ArtworkView(url: artist.thumbnail, cornerRadius: 24)
                .frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 3) {
                Text(artist.name)
                    .font(.body.weight(.medium))
                    .foregroundColor(colors.onSurface)
                    .lineLimit(1)
                Text("Artist")
                    .font(.subheadline)
                    .foregroundColor(colors.onSurface.opacity(0.6))
            }
            Spacer()
        }
    }
}

private struct AlbumRow: View {
    let album: Album
    @Environment(\.nestColors) private var colors

    var body: some View {
        HStack(spacing: 12) {
            ArtworkView(url: album.thumbnail, cornerRadius: 8)
                .frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 3) {
                Text(album.title)
                    .font(.body.weight(.medium))
                    .foregroundColor(colors.onSurface)
                    .lineLimit(1)
                Text("Album" + (album.artist.map { " · \($0)" } ?? ""))
                    .font(.subheadline)
                    .foregroundColor(colors.onSurface.opacity(0.6))
                    .lineLimit(1)
            }
            Spacer()
        }
    }
}

private struct PlaylistRow: View {
    let playlist: Playlist
    @Environment(\.nestColors) private var colors

    var body: some View {
        HStack(spacing: 12) {
            ArtworkView(url: playlist.thumbnail, cornerRadius: 8)
                .frame(width: 48, height: 48)
            VStack(alignment: .leading, spacing: 3) {
                Text(playlist.title)
                    .font(.body.weight(.medium))
                    .foregroundColor(colors.onSurface)
                    .lineLimit(1)
                Text(playlist.subtitle ?? "Playlist")
                    .font(.subheadline)
                    .foregroundColor(colors.onSurface.opacity(0.6))
                    .lineLimit(1)
            }
            Spacer()
        }
    }
}
