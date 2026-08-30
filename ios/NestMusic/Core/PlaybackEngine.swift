//
//  PlaybackEngine.swift
//  Nest Music
//
//  A small observable playback engine wrapping AVPlayer. Resolves streams via
//  InnerTube and drives the mini player + now-playing UI.
//

import AVFoundation
import Combine
import Foundation

final class PlaybackEngine: ObservableObject {
    static let shared = PlaybackEngine()

    @Published private(set) var currentSong: Song?
    @Published private(set) var isPlaying = false
    @Published private(set) var progress: Double = 0
    @Published private(set) var isLoading = false
    @Published private(set) var errorMessage: String?

    let player = AVPlayer()

    private var timeObserver: Any?
    private var cancellables = Set<AnyCancellable>()

    private init() {
        configureAudioSession()

        player.publisher(for: \.timeControlStatus)
            .receive(on: RunLoop.main)
            .sink { [weak self] status in
                self?.isPlaying = (status == .playing)
            }
            .store(in: &cancellables)

        let interval = CMTime(seconds: 0.5, preferredTimescale: 600)
        timeObserver = player.addPeriodicTimeObserver(forInterval: interval, queue: .main) { [weak self] time in
            guard let self, let duration = self.player.currentItem?.duration.seconds,
                  duration.isFinite, duration > 0 else {
                self?.progress = 0
                return
            }
            self.progress = time.seconds / duration
        }

        NotificationCenter.default.publisher(for: AVPlayerItem.didPlayToEndTimeNotification)
            .receive(on: RunLoop.main)
            .sink { [weak self] _ in
                self?.isPlaying = false
                self?.progress = 0
            }
            .store(in: &cancellables)
    }

    private func configureAudioSession() {
        let session = AVAudioSession.sharedInstance()
        try? session.setCategory(.playback, mode: .default)
        try? session.setActive(true)
    }

    var duration: Double {
        guard let item = player.currentItem, item.duration.seconds.isFinite else { return 0 }
        return item.duration.seconds
    }

    // MARK: - Intents

    /// Play a single song (or a queue of songs starting at `index`).
    func play(_ song: Song, queue: [Song] = []) async {
        await MainActor.run {
            isLoading = true
            errorMessage = nil
            currentSong = song
        }

        do {
            let url = try await InnerTubeClient.shared.streamURL(for: song.videoId)
            await MainActor.run {
                let item = AVPlayerItem(url: url)
                player.replaceCurrentItem(with: item)
                player.play()
            }
        } catch {
            await MainActor.run {
                errorMessage = error.localizedDescription
            }
        }

        await MainActor.run { isLoading = false }
    }

    func togglePlayPause() {
        if player.rate > 0 {
            player.pause()
        } else {
            player.play()
        }
    }

    func seek(to fraction: Double) {
        let seconds = duration * fraction
        guard seconds.isFinite else { return }
        player.seek(to: CMTime(seconds: seconds, preferredTimescale: 600))
    }
}
