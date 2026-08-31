//
//  Components.swift
//  Nest Music
//
//  Shared UI building blocks used across the home, search and player screens.
//

import SwiftUI

/// Async-loaded artwork with a themed placeholder while the image downloads.
struct ArtworkView: View {
    let url: String?
    var cornerRadius: CGFloat = 12

    @Environment(\.nestColors) private var colors

    var body: some View {
        GeometryReader { geo in
            ZStack {
                RoundedRectangle(cornerRadius: cornerRadius)
                    .fill(colors.surfaceContainer)

                if let url, let resolved = URL(string: url) {
                    AsyncImage(url: resolved) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .aspectRatio(contentMode: .fill)
                        case .failure:
                            placeholder
                        case .empty:
                            ProgressView().opacity(0.4)
                        @unknown default:
                            placeholder
                        }
                    }
                    .frame(width: geo.size.width, height: geo.size.height)
                    .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
                } else {
                    placeholder
                }
            }
        }
    }

    private var placeholder: some View {
        Image(systemName: "music.note")
            .font(.title2)
            .foregroundColor(colors.primary)
    }
}

/// A song row: artwork, title, artist, duration and explicit badge.
struct SongRow: View {
    let song: Song
    var onTap: (() -> Void)?

    @Environment(\.nestColors) private var colors

    var body: some View {
        HStack(spacing: 12) {
            ArtworkView(url: song.thumbnail, cornerRadius: 8)
                .frame(width: 48, height: 48)

            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 4) {
                    Text(song.title)
                        .font(.body.weight(.medium))
                        .foregroundColor(colors.onSurface)
                        .lineLimit(1)
                    if song.isExplicit {
                        Text("E")
                            .font(.caption2.weight(.bold))
                            .padding(.horizontal, 3)
                            .padding(.vertical, 1)
                            .background(colors.onSurface.opacity(0.15))
                            .foregroundColor(colors.onSurface)
                            .cornerRadius(3)
                    }
                }
                Text(song.subtitle)
                    .font(.subheadline)
                    .foregroundColor(colors.onSurface.opacity(0.6))
                    .lineLimit(1)
            }

            Spacer()

            if let duration = song.durationText {
                Text(duration)
                    .font(.caption)
                    .foregroundColor(colors.onSurface.opacity(0.5))
            }
        }
        .contentShape(Rectangle())
        .onTapGesture { onTap?() }
    }
}

/// A horizontally scrolling shelf of square song tiles (quick picks style).
struct SongGridShelf: View {
    let title: String?
    let songs: [Song]
    var onSongTap: (Song) -> Void

    @Environment(\.nestColors) private var colors

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            if let title {
                Text(title)
                    .font(.title3.weight(.semibold))
                    .foregroundColor(colors.onSurface)
                    .padding(.horizontal)
            }

            ScrollView(.horizontal, showsIndicators: false) {
                LazyHStack(spacing: 14) {
                    ForEach(songs) { song in
                        VStack(alignment: .leading, spacing: 6) {
                            ArtworkView(url: song.thumbnail, cornerRadius: 10)
                                .frame(width: 140, height: 140)
                            Text(song.title)
                                .font(.subheadline.weight(.medium))
                                .foregroundColor(colors.onSurface)
                                .lineLimit(1)
                            Text(song.subtitle)
                                .font(.caption)
                                .foregroundColor(colors.onSurface.opacity(0.6))
                                .lineLimit(1)
                        }
                        .frame(width: 140)
                        .contentShape(Rectangle())
                        .onTapGesture { onSongTap(song) }
                    }
                }
                .padding(.horizontal)
            }
        }
    }
}

/// Simple loading state with a subtle spinner.
struct LoadingView: View {
    @Environment(\.nestColors) private var colors

    var body: some View {
        VStack(spacing: 12) {
            ProgressView()
                .tint(colors.primary)
            Text("Loading…")
                .font(.footnote)
                .foregroundColor(colors.onSurface.opacity(0.6))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

/// Empty / error state.
struct MessageView: View {
    let icon: String
    let title: String
    let message: String?

    @Environment(\.nestColors) private var colors

    var body: some View {
        VStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 44))
                .foregroundColor(colors.primary)
            Text(title)
                .font(.headline)
                .foregroundColor(colors.onSurface)
            if let message {
                Text(message)
                    .font(.subheadline)
                    .foregroundColor(colors.onSurface.opacity(0.6))
                    .multilineTextAlignment(.center)
            }
        }
        .padding()
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}
