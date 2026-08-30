//
//  LibraryView.swift
//  Nest Music
//
//  Library tab. Downloads, playlists and YouTube Music account sync from the
//  Android app are not yet available; this tab explains the current state.
//

import SwiftUI

struct LibraryView: View {
    @Environment(\.nestColors) private var colors

    var body: some View {
        NavigationStack {
            MessageView(
                icon: "books.vertical",
                title: "Library",
                message: "Playlists, downloads and account sync arrive in an upcoming update."
            )
            .navigationTitle("Library")
            .toolbarBackground(colors.background, for: .navigationBar)
        }
    }
}
