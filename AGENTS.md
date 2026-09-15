# Working with Nest Music as an AI agent

Nest Music is an unofficial YouTube Music client. It is a **GPL-3.0 fork of
[Metrolist](https://github.com/MetrolistGroup/Metrolist)** that has grown beyond it and now ships
three clients from one repository:

- **Android** (`app/`) — Kotlin + Jetpack Compose, Material 3, Hilt, Room, Media3. This is the
  main product and where the vast majority of work happens.
- **Desktop** (`desktop/`) — Tauri 2 (React + Vite frontend, Rust shell, Python sidecar),
  Windows/Linux (macOS config exists too). Kept under **AGPL-3.0** with the original
  **Kodama** attribution (`desktop/README.md`).
- **iOS** (`ios/`) — SwiftUI app, built/tested through `codemagic.yaml`.

The Android app is where legacy Metrolist naming survives (package `com.metrolist.*` inside the
protobuf submodule, `metrolist_strings.xml`, `app/schemas/com.metrolist.music.db.*`). Do **not**
rename those to "nest" — they are load-bearing (DB identities, translated resource files,
generated code).

## Repository layout

| Path | What it is |
| --- | --- |
| `app/` | Android application module (`com.nestmusic.music`). Sources in `app/src/main/kotlin/`. |
| `app/src/{foss,gms,izzy}/` | Per-flavor source sets. The Cast surface (`cast/CastOptionsProvider`, `playback/CastConnectionHandler`, `ui/component/CastButton`) exists in **all three** flavors: `gms` has the real implementation, `foss`/`izzy` ship no-op stubs, so changing that API means touching every flavor. |
| `app/src/test/kotlin/` | JVM unit tests (JUnit4 + Robolectric + `ktor-client-mock`). |
| `app/schemas/` | Exported Room schemas. **Do not edit by hand.** |
| `innertube/`, `kugou/`, `lrclib/`, `lastfm/`, `betterlyrics/`, `shazamkit/`, `paxsenix/` | Android library modules: YouTube InnerTube client, Kugou/LRCLIB/Paxsenix lyrics providers, Last.fm scrobbling, Better Lyrics (TTML, word-level sync), ShazamKit recognition. All namespaced `com.nestmusic.*`. |
| `metroproto/` | Git **submodule** with `listentogether.proto` (Listen Together wire format). |
| `desktop/` | Tauri desktop client + Flask sidecar. See `desktop/README.md` and `desktop/CLAUDE.md`. |
| `ios/` | SwiftUI client + `NestMusicTests`. |
| `codemagic.yaml`, `fastlane/` | iOS CI + store metadata. |
| `.github/workflows/` | `build.yml` (APKs), `build_pr.yml`, `build_quick.yml` (manual), `desktop-{windows,linux}.yml`, `release.yml`. |
| `development_guide.md` | Local setup walkthrough. Keep it in sync when setup steps change. |

## Rules for working on the project

1. The default branch is **`master`** (not `main`). Pull the latest `master` before starting, and
   never push to it directly: work on the branch you were given and open a PR from it.
2. Commit messages follow `type(scope): short description`, e.g. `fix(playback): keep queue on
   reconnect`. The scope is optional.
3. **All user-facing English strings go in `app/src/main/res/values/metrolist_strings.xml`.**
   `app/src/main/res/values/strings.xml` only holds inherited upstream InnerTune strings — reuse
   them, but never add new ones there. Never touch `values-*/` (Crowdin-owned translations) or any
   other locale file; a translation added by hand is overwritten on the next sync.
4. Follow Android/Kotlin best practices: coroutines and `Flow` over callbacks, `StateFlow` in
   ViewModels, Compose state hoisting, no main-thread I/O, no `GlobalScope`.
5. **Never change the database schema.** `MusicDatabase` is at version 38 and its history lives in
   `app/schemas/`. A new column/table/entity requires a hand-written Room `Migration` plus the
   exported schema JSON — if you are not explicitly asked for a migration, don't touch the DB.
6. Do not add Gradle dependencies, modules, or permissions without asking. APK size, startup time
   and battery are features here.
7. `desktop/` is a separate product: frontend verification is `npx vite build` / `npm run lint`,
   never a browser preview (Tauri APIs are unavailable in a plain browser). The Python sidecar,
   Rust shell and bundled Node runtime are build outputs — never commit them.
8. Follow the instructions of the human contributor you are working with, and ask when the request
   is ambiguous. Never invent product decisions (naming, defaults, UX) that you were not asked for.

## AI-specific guidelines

1. Documentation may be edited — this file included — when a change makes it wrong. Keep edits
   minimal and factual: update the specific stale lines instead of rewriting or "beautifying"
   unrelated docs, and never present an unverified claim as verified.
2. Only commit, push, or merge on the branch you were explicitly given. Never rewrite published
   history, force-push a shared branch, delete branches, or touch tags without an explicit
   instruction from a human. Work belongs on the session branch; PRs target `master`.
3. **Never bump the version.** `versionCode` / `versionName` in `app/build.gradle.kts` are released
   by the maintainers, and `release.yml` reacts to changes in that file. Note that `release.yml`
   listens on `main` while the default branch is `master`, so that automation does not fire on
   normal `master` pushes — do not "fix" this by editing versions yourself.
4. Do not commit generated or local artifacts: `app/src/main/java/` protobuf output, keystores
   (`app/persistent-debug.keystore`, `app/keystore/`), `local.properties`, `build/`, `*.apk`,
   `desktop/src-tauri/binaries/`, caches under `desktop/python-backend/`.
5. Keep the diff tight: no drive-by reformatting, no unrelated dependency bumps, no wholesale file
   rewrites. Reviewers should be able to read your change in a couple of minutes.
6. Comments only where the logic is non-obvious (why, not what). Match the surrounding code style,
   imports and naming.
7. Test before asking for review, and report honestly what you ran and what you could not run.
   If a build was impossible in your environment, say so explicitly instead of implying success.

## Building and testing your changes

Prerequisites: **JDK 21**, a recent Android SDK (`compileSdk = 37`, `minSdk = 26`,
`targetSdk = 36`), and an initialized submodule:

```bash
git clone https://github.com/Davidix07TV/Nest-Music.git
cd Nest-Music
git submodule update --init --recursive   # metroproto — required for Listen Together
```

`protoc` does **not** need to be installed: the `:app:generateProto` task downloads a pinned
`protoc` and writes Kotlin sources into `app/src/main/java/` (untracked, regenerated on build). If
the submodule is missing, proto generation is skipped with a warning and the Listen Together code
will not compile.

### Android

```bash
./gradlew :app:assembleFossDebug            # fastest full check (default flavor, debug)
./gradlew :app:testFossDebugUnitTest        # JVM unit tests (also :app:testGmsDebugUnitTest, etc.)
./gradlew :app:lintFossDebug                # lint is per-variant and NON-blocking
./gradlew :app:assembleFossRelease          # release (minified); CI adds -x lint -x lintFossRelease
```

- Flavors: `foss` (default — in-app updater, no Cast), `gms` (updater + Google Cast), `izzy`
  (F-Droid compliant: no updater, no Cast). Task names are
  `assemble<Variant><BuildType>` / `test<Variant><BuildType>UnitTest`.
- Debug APK: `app/build/outputs/apk/foss/debug/app-foss-debug.apk` (release:
  `app/build/outputs/apk/<flavor>/release/`). ABI filters are `arm64-v8a` and `armeabi-v7a`, and
  `ARCHITECTURE` is reported as `universal`; there is no separate per-ABI APK anymore.
- Lint never gated anything (`abortOnError = false`, `checkReleaseBuilds = false`), so a clean
  `assemble` is the real gate. Use `-x lint -x lintFossRelease` to keep local builds fast.
- CI (`build_pr.yml`) verifies PRs with `./gradlew --console=plain assembleFossRelease --warning-mode
  summary -x lint -x lintFossRelease` on JDK 21.
- Last.fm keys are read from `local.properties` or the environment (`LASTFM_API_KEY`,
  `LASTFM_SECRET`); the build works with empty values. Debug builds can be rebranded/renamed via
  `NESTMUSIC_APPLICATION_ID`, `NESTMUSIC_APP_NAME`, `NESTMUSIC_DEBUG_KEYSTORE_PATH`.

### Desktop

```bash
cd desktop
npm ci
npm run lint        # eslint
npm run build       # vite build — minimum verification for frontend changes
npm run tauri dev   # real end-to-end run (needs the Python sidecar/deps)
```

Bundles (`npm run tauri build`) require the PyInstaller sidecar and the bundled Node runtime first —
see `desktop/README.md` and `desktop/CLAUDE.md` for the exact per-OS steps.

### iOS

Built and tested through `codemagic.yaml` (`ios/NestMusic.xcodeproj`, scheme `NestMusic`). The
Codemagic workflow mirrors the Xcode commands; run them locally with `xcodebuild`/`simctl` if you
have a Mac, otherwise state that iOS was not verified.

## Known quirks worth remembering

- Agent scratch dirs (`.claude`, `.gemini`, `.cursor*`, `.codeium`, `.opencode`, …) are gitignored,
  so files dropped there are invisible to git history and to other contributors.
- `.aislop/config.yml` defines an external quality gate (format/lint/security/AI-slop) that only
  scores the JS/Python tree under `desktop/`; Kotlin and Swift are reported as "not scoreable".
  It is not wired into `.github/workflows/`.
- `changelog.md` is inherited from Metrolist and describes upstream releases; don't treat it as the
  changelog of this fork.
