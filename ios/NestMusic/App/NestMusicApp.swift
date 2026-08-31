//
//  NestMusicApp.swift
//  Nest Music
//

import SwiftUI

@main
struct NestMusicApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) private var appDelegate

    var body: some Scene {
        WindowGroup {
            RootView()
                .nestTheme()
        }
    }
}
