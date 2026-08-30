//
//  HomeView.swift
//  Nest Music
//
//  Home tab: quick picks, moods & genres and recommended shelves from YouTube Music.
//

import SwiftUI

struct HomeView: View {
    let onSongTap: (Song) -> Void

    @Environment(\.nestColors) private var colors
    @State private var shelves: [HomeShelf] = []
    @State private var isLoading = true
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Group {
                if isLoading {
                    LoadingView()
                } else if let errorMessage {
                    MessageView(icon: "wifi.exclamationmark", title: "Couldn't load Home", message: errorMessage)
                } else if shelves.isEmpty {
                    MessageView(icon: "music.note.list", title: "Nothing here yet", message: "Pull to refresh.")
                } else {
                    ScrollView {
                        LazyVStack(alignment: .leading, spacing: 22) {
                            ForEach(shelves) { shelf in
                                SongGridShelf(title: shelf.title, songs: shelf.songs, onSongTap: onSongTap)
                            }
                        }
                        .padding(.vertical)
                    }
                    .refreshable { await load() }
                }
            }
            .navigationTitle("Home")
            .toolbarBackground(colors.background, for: .navigationBar)
        }
        .task { await load() }
    }

    private func load() async {
        isLoading = shelves.isEmpty
        errorMessage = nil
        do {
            shelves = try await InnerTubeClient.shared.homeShelves()
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
}
