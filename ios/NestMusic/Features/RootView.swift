//
//  RootView.swift
//  Nest Music
//
//  Tab-based root layout mirroring the Android app's four main screens
//  (Home, Search, Listen Together, Library) with a docked mini player.
//

import SwiftUI

enum MainTab: String, CaseIterable {
    case home = "Home"
    case search = "Search"
    case together = "Listen Together"
    case library = "Library"

    var icon: String {
        switch self {
        case .home: return "house"
        case .search: return "magnifyingglass"
        case .together: return "person.2"
        case .library: return "books.vertical"
        }
    }
}

struct RootView: View {
    @StateObject private var engine = PlaybackEngine.shared
    @State private var selectedTab: MainTab = .home
    @State private var showNowPlaying = false

    @Environment(\.nestColors) private var colors

    var body: some View {
        ZStack(alignment: .bottom) {
            TabView(selection: $selectedTab) {
                HomeView(onSongTap: play)
                    .tabItem { Label(MainTab.home.rawValue, systemImage: MainTab.home.icon) }
                    .tag(MainTab.home)

                SearchView(onSongTap: play)
                    .tabItem { Label(MainTab.search.rawValue, systemImage: MainTab.search.icon) }
                    .tag(MainTab.search)

                ListenTogetherView()
                    .tabItem { Label(MainTab.together.rawValue, systemImage: MainTab.together.icon) }
                    .tag(MainTab.together)

                LibraryView()
                    .tabItem { Label(MainTab.library.rawValue, systemImage: MainTab.library.icon) }
                    .tag(MainTab.library)
            }
            .tint(colors.primary)

            if engine.currentSong != nil {
                MiniPlayerView(engine: engine) {
                    showNowPlaying = true
                }
                .padding(.bottom, 1)
            }
        }
        .sheet(isPresented: $showNowPlaying) {
            NowPlayingView(engine: engine)
        }
    }

    private func play(_ song: Song) {
        Task { await engine.play(song) }
    }
}
