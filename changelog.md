# Nest Music changelog

This file contains Nest Music release notes consumed by the Android release workflow. Each `---v<version>` line starts a release-note
block. Nest Music's history here starts at v1.0.0.

---v1.0.10
# v1.0.10 — Listen Together on our own server (2026-10-07)

### Added
- Listen Together now ships with Nest Music's own server
  (`wss://nest-music-listen-together.onrender.com/ws`) as the default, so a room can be hosted
  without configuring anything first. Any other server can still be set under
  **Settings → Listen Together → Server URL**.

### Fixed
- Update notifications are back. The app read the version out of the release *title*, which is
  free-form ("🎵 Nest Music v1.0.9 - …"), so every release was compared as 0.0.0 and the prompt
  never appeared. The version now comes from the release tag, and release titles start with the
  version.
- The Listen Together server no longer needs to wake up from Render's idle state before the first
  connection: a workflow pings its `/health` endpoint every 10 minutes.

---v1.0.9
# v1.0.9 — Sunset, Night and Aurora (2026-10-05)

### Added
- Added Night and Aurora launcher icons alongside the default Sunset icon. Choose one under
  **Settings → Appearance → App icon**.
- Replaced remaining Metrolist marks with Nest Music branding in notifications, lyric share cards,
  widgets, and Wrapped.

### Unchanged
- Playback, queues, library, downloads, lyrics, Listen Together, and AI playlist behavior.

---v1.0.8
# v1.0.8 — Queue search and sharing (2026-09-30)

### Added
- Search the current queue by song title or artist, with the filtered track count and total play
  time.
- Share the full queue as a numbered list with artist names and YouTube Music links.

---v1.0.7
# v1.0.7 (2026-09-30)

### Fixed
- Removed remaining Metrolist leftovers and fixed the release workflow's push trigger.

---v1.0.6-lossless
# v1.0.6-lossless — Android prerelease (2026-09-27)

### Added
- Added optional Qobuz-backed lossless FLAC streaming and downloads, with CD, Hi-Res, and Max
  quality options.
- Added automatic fallback to YouTube Music when a matching FLAC stream is unavailable.

---v1.0.5
# v1.0.5 — Sunset interface (2026-09-24)

### Added
- Introduced the Sunset interface, inspired by the Nest Music logo, with a refreshed home screen,
  floating navigation dock, and updated player styling.
- Kept the original Material interface available as **Classic**; choose either look at first launch
  or under **Settings → Appearance → Interface**.

---v1.0.4
# v1.0.4 — AI playlists and recurring Wrapped (2026-09-19)

### Added
- Create playlists from natural-language prompts, preview the generated tracks, and save them to
  the library.
- Wrapped now calculates stats for the current year and can be replayed from
  **Settings → Content → View Wrapped**.
- Added dedicated yearly playlist artwork through 2030 and released Linux desktop packages
  (AppImage and .deb).

### Changed
- Completed the Nest Music branding and polished typography, dialogs, and navigation.

---v1.0.3
# v1.0.3 — Mix transitions (2026-09-07)

### Added
- Added per-track-pair Mix transitions for queues and playlists, with an editor, waveform controls,
  adjustable duration, and Auto, Fade, Rise, and Blend presets.
- Added transition controls for volume curves, EQ swapping, and high-pass/low-pass filters. BPM is
  shown when available in track metadata.

---v1.0.2
# v1.0.2 — Official branding and stability fixes (2026-08-26)

### Added
- Introduced the Nest Music sunset logo and icons, a customized About screen, and an updater
  targeting the official Nest Music repository.
- Added permanent CI signing for release builds and published a Windows desktop build.

### Fixed
- Fixed the crash in **Settings → About** and corrected the Protobuf imports used by Listen Together.

---v1.0.1
# v1.0.1 — Universal release (2026-08-18)

### Added
- Introduced a universal Android APK with YouTube Music library synchronization and time-synced
  lyrics.

### Changed
- Improved background audio handling, session stability, and API response performance.

### Known issue
- Opening **Settings → About** could crash the app; fixed in v1.0.2.

---v1.0.0
# v1.0.0 — First Nest Music release (2026-08-15)

- Published the first universal Android build under the Nest Music name, with a redesigned
  interface, refreshed visual identity, and stability improvements.
- Nest Music is based on the open-source Metrolist project; see the repository's credits and license
  for attribution.
