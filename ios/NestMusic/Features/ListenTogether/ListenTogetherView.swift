//
//  ListenTogetherView.swift
//  Nest Music
//
//  Placeholder for the "Listen Together" feature. The Android app powers this
//  with a multiplayer session service; the iOS foundation will follow.
//

import SwiftUI

struct ListenTogetherView: View {
    @Environment(\.nestColors) private var colors

    var body: some View {
        NavigationStack {
            MessageView(
                icon: "person.2.wave.2",
                title: "Listen Together",
                message: "Synced listening sessions are coming to the iOS app in a future update."
            )
            .navigationTitle("Listen Together")
            .toolbarBackground(colors.background, for: .navigationBar)
        }
    }
}
