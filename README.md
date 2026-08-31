<div align="center">

<img src="https://raw.githubusercontent.com/Davidix07TV/Nest-Music/refs/heads/master/assets/nest-music-logo.png" alt="Nest Music app icon" width="200" />

# Nest Music

### YouTube Music client for Android and Windows!

[![Latest release](https://img.shields.io/github/v/release/Davidix07TV/Nest-Music?style=for-the-badge&labelColor=0d1117)](https://github.com/Davidix07TV/Nest-Music/releases)
[![License](https://img.shields.io/github/license/Davidix07TV/Nest-Music?style=for-the-badge&labelColor=0d1117)](https://github.com/Davidix07TV/Nest-Music/blob/master/LICENSE)
[![Downloads](https://img.shields.io/github/downloads/Davidix07TV/Nest-Music/total?style=for-the-badge&labelColor=0d1117)](https://github.com/Davidix07TV/Nest-Music/releases) 

<br/>

[![GitHub](https://img.shields.io/badge/GitHub-%23121011.svg?style=for-the-badge&logo=github&logoColor=white&labelColor=0d1117)](https://github.com/Davidix07TV/Nest-Music)

<a href="https://www.producthunt.com/products/nest-music?embed=true&amp;utm_source=badge-featured&amp;utm_medium=badge&amp;utm_campaign=badge-nest-music" target="_blank" rel="noopener noreferrer"><img alt="Nest Music - A music app YouTube-based | Product Hunt" width="250" height="54" src="https://api.producthunt.com/widgets/embed-image/v1/featured.svg?post_id=1223725&amp;theme=light&amp;t=1786885683725"></a>

<br/>

[**Download**](#download-now) · [**Features**](#features) · [**FAQ**](#faq) · [**Support**](#support-the-project)

</div>

> [!NOTE]
> **Nest Music** is a modern fork of the popular YouTube Music client, rebranded with a fresh new identity and improved features.

> [!WARNING]
> **Regional Restriction** - If YouTube Music is unavailable in your region, this app will not work without a **VPN or proxy** connecting to a supported region.

<div align="center">

<h1><a id="features"></a>Features</h1>

<table>
  <tr>
    <td width="50%" valign="top">

#### Playback
- Stream any song or video from YouTube Music
- Background playback
- Download & cache for offline use
- Skip silence
- Sleep timer

</td>
    <td width="50%" valign="top">

#### Audio
- Audio normalization
- Tempo & pitch control
- Equalizer with AutoEQ support

</td>
  </tr>
  <tr>
    <td width="50%" valign="top">

#### Lyrics & Discovery
- Live synced lyrics with word-by-word highlighting
- AI-powered lyrics translation
- Music recognition (Shazam integration)
- Personalized quick picks
- Search songs, albums, artists, videos, and playlists

</td>
    <td width="50%" valign="top">

#### Library & Account
- Full library management
- Local and cloud playlists
- Import/export playlists
- Reorder songs in playlist or queue
- YouTube Music account login & sync

</td>
  </tr>
  <tr>
    <td width="50%" valign="top">

#### Social Features
- Listen together with friends in real-time
- Discord Rich Presence integration
- Share what you're listening to

</td>
    <td width="50%" valign="top">

#### Interface
- Home screen widget
- Multiple theme modes (Light / Dark / Black / Dynamic)
- Dynamic color + 19 preset color palettes
- Built with Material 3 design
- Android Auto support

</td>
  </tr>
</table>

</div>

---

<div align="center">

<h1><a id="download-now"></a>Download Now</h1>

<h2>Latest Release</h2>

<table>
  <tr>
    <th align="center">GitHub Releases</th>
    <th align="center">Build from Source</th>
  </tr>
  <tr>
    <td align="center">
      <a href="https://github.com/Davidix07TV/Nest-Music/releases/latest">
        <img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Download from GitHub" height="60">
      </a>
    </td>
    <td align="center">
      <pre><code>git clone https://github.com/Davidix07TV/Nest-Music
cd Nest-Music
./gradlew :app:assembleFossRelease</code></pre>
    </td>
  </tr>
</table>

<h2>Nightly Build</h2>

<table>
  <tr>
    <th align="center">GitHub Actions</th>
  </tr>
  <tr>
    <td align="center">
      <a href="https://github.com/Davidix07TV/Nest-Music/actions">
        <img src="https://github.com/machiav3lli/oandbackupx/blob/034b226cea5c1b30eb4f6a6f313e4dadcbb0ece4/badge_github.png" alt="Download from GitHub Actions" height="75">
      </a>
    </td>
  </tr>
</table>

</div>

---

<div align="center">

<h1><a id="requirements"></a>Requirements</h1>

- **Android 8.0+** (API 26+)
- **YouTube Music** subscription (for some features)
- **Internet connection** required for streaming
- Optional: VPN/Proxy if YouTube Music is region-restricted

</div>

---

<div align="center">

<h1><a id="faq"></a>FAQ</h1>

### Can I use this without a YouTube Music subscription?
Yes, but some features require an active subscription. Free users can still browse and search.

### Is this legal?
Yes! Nest Music is a third-party client that connects to YouTube Music's official API. It's similar to how web browsers access YouTube Music.

### What's the difference between FOSS and GMS builds?
- **FOSS**: Contains no Google libraries, supports music recognition via Shazam
- **GMS**: Includes Google Play Services for enhanced features

### Do I need to log in?
Yes, you need a Google account to access your library and playlists.

### Can I download songs for offline playback?
Yes! The app supports caching and downloading songs for offline use (available space permitting).

</div>

---

<div align="center">

<h1><a id="building"></a>Building from Source</h1>

### Prerequisites
- [Android SDK](https://developer.android.com/studio)
- [Java 21+](https://adoptium.net/temurin/releases/?version=21) (Temurin recommended)
- [Gradle 9.7+](https://gradle.org/)

### Build Steps

```bash
# Clone the repository
git clone https://github.com/Davidix07TV/Nest-Music.git
cd Nest-Music

# Build debug APK (FOSS variant)
./gradlew :app:assembleFossDebug

# Build release APK
./gradlew :app:assembleFossRelease

# Install on connected device
adb install app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

APK output location: `app/build/outputs/apk/foss/debug/`

</div>

---

<div align="center">

<h1><a id="support-the-project"></a>Support the Project</h1>

<h3>Nest Music is free and open-source. If you enjoy it, please consider supporting the project!</h3>

#### Star the Repository ⭐
If you find Nest Music useful, please star this repository on GitHub!

#### Report Issues & Suggestions 🐛
Found a bug or have a feature request? [Open an issue](https://github.com/Davidix07TV/Nest-Music/issues)!

#### Contribute Code 💻
Pull requests are welcome! Please ensure your changes align with the project goals.

</div>

---

<div align="center">

<h1>Credits & Attribution</h1>

<h3>Nest Music is built upon the excellent work of the Metrolist and Kodama project.</h3>

**Original Projects**:
[Metrolist](https://github.com/MetrolistGroup/Metrolist) by MetrolistGroup and
[Kodama](https://github.com/KiyoshiTheDevil/Kodama) by KiyoshiTheDevil

**This fork**: Rebranded as Nest Music with UI/UX improvements and ongoing maintenance.

### Libraries & Integrations

<table>
  <thead>
    <tr>
      <th align="center">Project</th>
      <th align="center">Purpose</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td align="center"><a href="https://better-lyrics.boidu.dev"><strong>Better Lyrics</strong></a></td>
      <td>Time-synced lyrics with word-by-word highlighting</td>
    </tr>
    <tr>
      <td align="center"><a href="https://github.com/MetrolistGroup/metroserver"><strong>metroserver</strong></a></td>
      <td>Real-time listen-together backend</td>
    </tr>
    <tr>
      <td align="center"><a href="https://github.com/aleksey-saenko/MusicRecognizer"><strong>MusicRecognizer</strong></a></td>
      <td>Music recognition and Shazam integration</td>
    </tr>
    <tr>
      <td align="center"><a href="https://github.com/ZemerTeam/zemer-cipher"><strong>zemer-cipher</strong></a></td>
      <td>YouTube cipher deobfuscation</td>
    </tr>
    <tr>
      <td align="center"><a href="https://developer.android.com/jetpack/compose"><strong>Jetpack Compose</strong></a></td>
      <td>Modern Android UI framework</td>
    </tr>
  </tbody>
</table>

</div>

---

<div align="center">

## License

Nest Music is licensed under the [GNU General Public License v3.0](LICENSE). See the LICENSE file for details.

### Disclaimer
This project is not affiliated with YouTube, YouTube Music, or Google. All trademarks are the property of their respective owners.

---

**Repository**: https://github.com/Davidix07TV/Nest-Music

**Last Updated**: August 2026

</div>

## Desktop (Windows)

Nest Music includes a full desktop app for Windows, built with **Tauri 2** (React + Vite frontend, Rust shell, Python sidecar). It supports synced lyrics, offline downloads, Discord Rich Presence, Last.fm scrobbling, an OBS overlay and more.

> The desktop app was formerly known as **Kodama** (by [KiyoshiTheDevil](https://github.com/KiyoshiTheDevil/Kodama)); Nest Music continues it as its own desktop client under the **AGPL-3.0** license, keeping the original attribution.

### Install

Download the latest installer from GitHub Actions (workflow **Desktop Windows Installer** → artifact `nest-music-windows-installer`) or build it yourself:

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

The NSIS installer is written to `desktop/src-tauri/target/release/bundle/nsis/`.

### Development

```bat
cd desktop
npm ci
npm run tauri dev
```

See `desktop/README.md` for the full desktop documentation.
