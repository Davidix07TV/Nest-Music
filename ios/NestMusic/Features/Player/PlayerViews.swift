//
//  PlayerViews.swift
//  Nest Music
//
//  Mini player (docked above the tab bar) and full now-playing screen.
//

import Foundation
import SwiftUI

/// Compact player bar shown when a song is loaded.
struct MiniPlayerView: View {
    @ObservedObject var engine: PlaybackEngine
    let onExpand: () -> Void

    @Environment(\.nestColors) private var colors

    var body: some View {
        if let song = engine.currentSong {
            HStack(spacing: 12) {
                ArtworkView(url: song.thumbnail, cornerRadius: 8)
                    .frame(width: 44, height: 44)
                    .onTapGesture(perform: onExpand)

                VStack(alignment: .leading, spacing: 2) {
                    Text(song.title)
                        .font(.subheadline.weight(.medium))
                        .foregroundColor(colors.onSurface)
                        .lineLimit(1)
                    Text(song.subtitle)
                        .font(.caption)
                        .foregroundColor(colors.onSurface.opacity(0.6))
                        .lineLimit(1)
                }
                .onTapGesture(perform: onExpand)

                Spacer()

                Button {
                    engine.togglePlayPause()
                } label: {
                    Image(systemName: engine.isPlaying ? "pause.fill" : "play.fill")
                        .font(.title2)
                        .foregroundColor(colors.onSurface)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 8)
            .background(colors.surfaceContainerHigh)
            .contentShape(Rectangle())
        }
    }
}

/// Full-screen now-playing view presented as a sheet.
struct NowPlayingView: View {
    @ObservedObject var engine: PlaybackEngine

    @Environment(\.nestColors) private var colors

    var body: some View {
        VStack(spacing: 24) {
            if let song = engine.currentSong {
                ArtworkView(url: song.thumbnail, cornerRadius: 18)
                    .frame(maxWidth: .infinity)
                    .aspectRatio(1, contentMode: .fit)
                    .shadow(radius: 20)
                    .padding(.horizontal, 32)
                    .padding(.top, 40)

                VStack(spacing: 6) {
                    Text(song.title)
                        .font(.title2.weight(.semibold))
                        .foregroundColor(colors.onSurface)
                        .lineLimit(1)
                    Text(song.subtitle)
                        .font(.subheadline)
                        .foregroundColor(colors.onSurface.opacity(0.6))
                        .lineLimit(1)
                }
            }

            if engine.isLoading {
                ProgressView().tint(colors.primary)
            }

            if let error = engine.errorMessage {
                Text(error)
                    .font(.footnote)
                    .foregroundColor(.red)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal)
            }

            VStack(spacing: 8) {
                Slider(
                    value: Binding(
                        get: { engine.progress },
                        set: { engine.seek(to: $0) }
                    ),
                    in: 0...1
                )
                .tint(colors.primary)

                HStack {
                    Text(formatted(engine.progress * engine.duration))
                    Spacer()
                    Text(formatted(engine.duration))
                }
                .font(.caption)
                .foregroundColor(colors.onSurface.opacity(0.6))
            }
            .padding(.horizontal)

            HStack(spacing: 56) {
                Button {
                    engine.seek(to: 0)
                } label: {
                    Image(systemName: "backward.end.fill")
                        .font(.title2)
                        .foregroundColor(colors.onSurface)
                }

                Button {
                    engine.togglePlayPause()
                } label: {
                    Image(systemName: engine.isPlaying ? "pause.circle.fill" : "play.circle.fill")
                        .font(.system(size: 72))
                        .foregroundColor(colors.primary)
                }

                Button {
                    engine.seek(to: 1)
                } label: {
                    Image(systemName: "forward.end.fill")
                        .font(.title2)
                        .foregroundColor(colors.onSurface)
                }
            }

            Spacer()
        }
        .padding()
        .background(colors.background.ignoresSafeArea())
    }

    private func formatted(_ seconds: Double) -> String {
        guard seconds.isFinite, seconds >= 0 else { return "0:00" }
        let total = Int(seconds)
        return String(format: "%d:%02d", total / 60, total % 60)
    }
}
