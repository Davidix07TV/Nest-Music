<div align="center">
  <img width="210" height="48" alt="Nest Music Logo Full" src="https://github.com/user-attachments/assets/e003560b-1760-4657-a8fc-454195293937" />
</div>

<div align="center">
  <p>An unofficial desktop player for YouTube Music.</p>

  [![Version](https://img.shields.io/github/v/release/KiyoshiTheDevil/Nest Music?include_prereleases&style=for-the-badge&color=a855f7&label=version)](https://github.com/KiyoshiTheDevil/Nest Music/releases/latest)
  [![Downloads](https://img.shields.io/github/downloads/KiyoshiTheDevil/Nest Music/total?style=for-the-badge&color=a855f7&label=downloads)](https://github.com/KiyoshiTheDevil/Nest Music/releases)
  [![Active users](https://img.shields.io/endpoint?style=for-the-badge&url=https%3A%2F%2Fnest-music-stats.kiyoshidesign.workers.dev%2Fbadge%3Fmetric%3Dmau)](https://github.com/KiyoshiTheDevil/Nest Music)
  [![Platform](https://img.shields.io/badge/platform-Windows_%7C_macOS-0078d4?style=for-the-badge)](https://github.com/KiyoshiTheDevil/Nest Music/releases/latest)
  [![Tauri](https://img.shields.io/badge/Tauri-2.x-24c8db?style=for-the-badge&logo=tauri&logoColor=white)](https://tauri.app)
  [![Crowdin](https://img.shields.io/badge/translate-Crowdin-2e3340?style=for-the-badge&logo=crowdin&logoColor=white)](https://crowdin.com/project/kiyoshi-music)
  [![License](https://img.shields.io/badge/license-AGPL_v3-3da639?style=for-the-badge)](LICENSE)
</div>

---

> AI notice: This app has been created with an LLM called **Claude Code**. If you're against the usage of LLMs or AI in any capacity, this app won't be for you. I hope you understand.

## Features

- **Synced lyrics** with word- and syllable-level timing, plus **Unison** community lyrics.
- **Lyrics Composer** for creating and editing your own.
- **Crossfade** and a built-in **visualizer**.
- **Remote control** from your phone.
- **OBS overlay** for streaming.
- **Offline downloads**, Discord Rich Presence, and Last.fm scrobbling.

## Reporting issues and bugs

I highly recommend to send a bug report in the new [Discord-Server](https://discord.gg/rhreShDJxn), because that's easier for me to access, track, sort and handle in contrast to GitHub's unstable platform.
**Starting August, I will stop actively checking issues on GitHub.**

## Download

Grab the latest build from the [**Releases**](https://github.com/KiyoshiTheDevil/Nest Music/releases/latest) page:

**Windows:** download and run the `*_x64-setup.exe` installer from the latest release.

**macOS (Apple Silicon):** the build is **unsigned**, so install it with this command (it
downloads the latest release and avoids Gatekeeper's quarantine):

```bash
curl -fsSL https://raw.githubusercontent.com/KiyoshiTheDevil/Nest Music/master/install.sh | bash
```

Automatic Updates on macOS are currently not functional. Hopefully I can fix it in the near future.

## Screenshots

<!-- TODO: add fresh screenshots of the current app (player, lyrics, library, settings). -->
No screenshot available... yet.


> A Google account is not required to use the player, Premium isn't required either.
> Please be aware, that some content might be inaccessable due to Premium restrictions.

## NEW! Discord Server

Hey! I made a dedicated Discord server for the App, where you can chat about the project and send in bugs and suggestions more directly

>> [https://discord.gg/PzSsPF7KW](https://discord.gg/rhreShDJxn)

## For Developers

### Prerequisites

- [Node.js](https://nodejs.org/) v18+
- [Rust](https://rustup.rs/) (stable)
- [Python](https://www.python.org/) 3.10+

### Setup

```bash
# 1. Clone
git clone https://github.com/Davidix07TV/Nest-Music.git
cd Nest-Music/desktop

# 2. Frontend dependencies
npm ci

# 3. Python backend dependencies
cd python-backend
py -m pip install -r requirements.txt
py -m pip install pyinstaller
cd ..

# 4. (Optional) Authenticate with your YouTube account
cd python-backend
python setup_auth.py
cd ..
```

### Run in development mode

```bash
npm run tauri dev
```

### Build (Windows installer)

The Tauri bundle requires the Python sidecar to be built first — Tauri expects it at
exactly `src-tauri/binaries/nest-music-server-x86_64-pc-windows-msvc.exe` (the
`externalBin` entry `binaries/nest-music-server` plus the Rust target triple).
`build_server.bat` produces that file and also downloads the bundled `node.exe`
resource into `src-tauri/resources/` (required by `tauri.windows.conf.json`).

```bat
cd desktop
npm ci
cd python-backend
py -m pip install -r requirements.txt
py -m pip install pyinstaller
.\build_server.bat
cd ..
npm run tauri build
```

The NSIS installer is written to
`desktop/src-tauri/target/release/bundle/nsis/*-setup.exe`.

On CI the same steps run automatically via the manual
**Desktop Windows Installer** workflow (`.github/workflows/desktop-windows.yml`),
which uploads the installer as a downloadable artifact.

---

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for a full version history.

## License

Nest Music is licensed under the **[GNU Affero General Public License v3.0](LICENSE)** (AGPL-3.0).
You are free to use, study, modify and redistribute it, provided derivative works remain under
the same license and their source is made available.

The bundled lyrics Composer is a vendored component licensed under the AGPL-3.0 as well.

## Disclaimer

Nest Music is an **unofficial** client and is **not affiliated with or endorsed by YouTube or
Google**. It relies on the unofficial YouTube Music API and is provided for personal use, as-is
and without warranty. Use at your own risk.
