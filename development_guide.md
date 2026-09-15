# Nest Music Dev Guide

This file outlines how to set up a local development environment for Nest Music.

For the Android app there is nothing exotic: JDK 21 + an Android SDK and Gradle does the rest.
The desktop client and the iOS app have their own prerequisites, linked at the end.

## Prerequisites

- **JDK 21** (Temurin recommended) — the build sets `jvmToolchain(21)`.
- **Android SDK** with the platform for `compileSdk = 37` installed, plus platform tools if you want
  to install on a device/emulator (`minSdk = 26`, `targetSdk = 36`).
- Git, plus network access on the first build (Gradle, dependencies, a pinned `protoc`).

`protobuf-compiler` is **not** required: the `:app:generateProto` task downloads the pinned `protoc`
itself and generates the Listen Together sources into `app/src/main/java/` (untracked, regenerated
on every build).

## Basic setup

This has been tested on Linux, but should work on other platforms with minor adjustments.

```bash
git clone https://github.com/Davidix07TV/Nest-Music.git
cd Nest-Music
git submodule update --init --recursive      # metroproto (listentogether.proto)

# Optional: only needed if the build cannot create/use ~/.android/debug.keystore
[ ! -f "app/persistent-debug.keystore" ] && keytool -genkeypair -v -keystore app/persistent-debug.keystore -storepass android -keypass android -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US" || echo "Keystore already exists."

./gradlew :app:assembleFossDebug
ls app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

If the submodule is missing, the build only warns and **skips** proto generation — the Listen
Together code will fail to compile, so initialize it before building.

### Useful commands

```bash
./gradlew :app:assembleFossDebug        # default flavor (foss) + debug
./gradlew :app:assembleFossRelease      # release, minified
./gradlew :app:testFossDebugUnitTest    # JVM unit tests (app/src/test/kotlin)
./gradlew :app:lintFossDebug            # lint, per variant and non-blocking
adb install app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

Build variants: `foss` (default — updater, no Cast), `gms` (updater + Google Cast) and `izzy`
(F-Droid compliant: no updater, no Cast). Add `-x lint -x lintFossRelease` to skip lint, which is
what CI does.

### GitHub Secrets Configuration

This project uses GitHub Secrets to securely store API keys for building releases:

1. Go to your GitHub repository settings
2. Navigate to **Settings** → **Secrets and variables** → **Actions**
3. Add the following repository secrets:
   - `LASTFM_API_KEY`: Your LastFM API key
   - `LASTFM_SECRET`: Your LastFM secret key
   - `KEYSTORE`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`: release signing (base64 keystore)

4. Get your LastFM API credentials from: https://www.last.fm/api/account/create

**Note:** These secrets are automatically injected into the build process via GitHub Actions and are
not visible in the source code. Locally, put the same values in `local.properties` or export them as
environment variables. Never commit keystores, `local.properties`, or API keys.

## Desktop (Windows / Linux / macOS)

Tauri 2 app: React + Vite frontend, Rust shell, Python Flask sidecar.

```bash
cd desktop
npm ci
npm run tauri dev      # needs the Python dependencies, see desktop/README.md for the per-OS list
```

Full build instructions (PyInstaller sidecar + bundled Node runtime, AppImage/`.deb`/NSIS) are in
[`desktop/README.md`](desktop/README.md); agent-oriented notes for that sub-project live in
[`desktop/CLAUDE.md`](desktop/CLAUDE.md).

## iOS

The SwiftUI app in `ios/` is built and tested through the Codemagic workflow in
[`codemagic.yaml`](codemagic.yaml) (on a macOS runner, Xcode latest). Open `ios/NestMusic.xcodeproj`
directly if you have a Mac.

## Contributing

Read [`AGENTS.md`](AGENTS.md) — the working rules there (branches, commit format, strings,
migrations, versioning) apply to humans and agents alike.
