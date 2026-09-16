# Contributing to Nest Music

Thanks for your interest in contributing! Nest Music is an unofficial YouTube Music client — a
**GPL-3.0 fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist)** — that ships three
clients from one repository:

| Client | Path | Stack | License |
| --- | --- | --- | --- |
| Android | `app/` | Kotlin, Jetpack Compose, Material 3, Hilt, Room, Media3 | GPL-3.0 |
| Desktop | `desktop/` | Tauri 2 (React + Vite, Rust shell, Python Flask sidecar) | AGPL-3.0 (formerly Kodama) |
| iOS | `ios/` | SwiftUI, built through `codemagic.yaml` | GPL-3.0 |

Before you start, read these two files — they are the source of truth and this guide only
summarizes them:

- [`development_guide.md`](development_guide.md) — local setup walkthrough and useful commands.
- [`AGENTS.md`](AGENTS.md) — the full working rules (branches, commits, strings, DB, dependencies).
  They apply to human contributors and AI agents alike.

---

## Ways to contribute

- **Bug reports and fixes.** This is the most welcome kind of contribution right now.
- **Translations.** Handled on Crowdin (see [Translations](#translations)) — not through PRs.
- **Documentation.** `README.md`, `development_guide.md`, `desktop/README.md`, `AGENTS.md`.
- **Code.** Small, focused changes: fixes, cleanup, tests, performance.

> [!IMPORTANT]
> **The project is currently in a feature freeze.** As stated in the
> [pull request template](.github/pull_request_template.md): *"DO NOT PR NEW FEATURES, WE'RE ON A
> FEATURE FREEZE!!!"* Feature requests may still be opened as issues, but new-feature PRs will be
> closed. If you are unsure whether your change counts, open an issue first and ask.

---

## Reporting issues

Blank issues are disabled (`.github/ISSUE_TEMPLATE/config.yml`); use the forms.

**Bug reports** (`.github/ISSUE_TEMPLATE/bug_report.yml`) — every checkbox is required, and
duplicates or incomplete reports are closed without comment. You need:

- reproduction with the **latest debug build** from GitHub Actions;
- steps to reproduce, expected behavior, actual behavior;
- a **logcat recording** (the template links LogFox + Shizuku and a video guide) — issues without
  logs may be closed immediately;
- the app version (Settings) and your Android version.

**Feature requests** (`.github/ISSUE_TEMPLATE/feature_request.yml`) — one feature per issue, plus
the motivation behind it.

Write issues in **English** and keep one problem per issue.

---

## Getting started

Prerequisites (Android, the main product):

- **JDK 21** (Temurin recommended) — the build sets `jvmToolchain(21)`.
- **Android SDK** with the platform for `compileSdk = 37` (`minSdk = 26`, `targetSdk = 36`).
- Git + network access for the first build (Gradle, dependencies, a pinned `protoc`).

```bash
git clone https://github.com/Davidix07TV/Nest-Music.git
cd Nest-Music
git submodule update --init --recursive      # metroproto (listentogether.proto)

# Only needed if the build cannot create/use ~/.android/debug.keystore
[ ! -f "app/persistent-debug.keystore" ] && keytool -genkeypair -v \
  -keystore app/persistent-debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"

./gradlew :app:assembleFossDebug
ls app/build/outputs/apk/foss/debug/app-foss-debug.apk
```

`protobuf-compiler` is **not** required: `:app:generateProto` downloads a pinned `protoc` and
generates the Listen Together sources into `app/src/main/java/` (untracked, regenerated on every
build). If the `metroproto` submodule is missing, proto generation is skipped with a warning and
the Listen Together code will not compile.

Flavors: `foss` (default — in-app updater, no Cast), `gms` (updater + Google Cast), `izzy`
(F-Droid compliant: no updater, no Cast). Task names are `assemble<Flavor><BuildType>` and
`test<Flavor><BuildType>UnitTest`.

Desktop and iOS have their own prerequisites — see
[`desktop/README.md`](desktop/README.md) and [`codemagic.yaml`](codemagic.yaml).

---

## Branches, commits and pull requests

1. The default branch is **`master`** (not `main`). Pull the latest `master` before starting and
   **never push to it directly**.
2. Create a short-lived branch with a descriptive name, e.g. `fix/queue-on-reconnect`.
3. Commit messages follow Conventional Commits: **`type(scope): short description`**, e.g.
   `fix(playback): keep queue on reconnect`. The scope is optional.
4. Keep the diff tight: no drive-by reformatting, no unrelated dependency bumps, no wholesale file
   rewrites. A reviewer should be able to read your change in a couple of minutes.
5. Open a PR against `master` and fill in the template (Problem, Cause, Solution, Testing, Related
   issues). Describe what you actually ran — and what you could not run — in **Testing**.

### PR checklist

- [ ] The change is a bug fix, cleanup, docs or test work (feature freeze: no new features).
- [ ] `./gradlew :app:assembleFossDebug` succeeds locally.
- [ ] Unit tests pass where they cover the change: `./gradlew :app:testFossDebugUnitTest`.
- [ ] New/changed user-facing strings live in `app/src/main/res/values/metrolist_strings.xml`.
- [ ] No new dependencies, Gradle modules or Android permissions (ask first if needed).
- [ ] No database schema changes, no hand-edited `app/schemas/` files.
- [ ] No generated or local artifacts in the diff (see below).
- [ ] `versionCode` / `versionName` untouched.

---

## Project rules that block reviews

These come from [`AGENTS.md`](AGENTS.md); restated because they are the most common reasons a PR
stalls.

- **Strings.** All new user-facing English strings go in
  `app/src/main/res/values/metrolist_strings.xml`. `app/src/main/res/values/strings.xml` only holds
  inherited upstream strings — reuse them, never add new ones there. Never touch `values-*/`:
  those files are Crowdin-owned and a hand-written translation is overwritten on the next sync.
- **Database.** `MusicDatabase` is at **version 38**
  (`app/src/main/kotlin/com/nestmusic/music/db/MusicDatabase.kt`), with exported schemas up to
  `38.json` in `app/schemas/`. A new column/table/entity needs a hand-written Room `Migration`
  **plus** the exported schema JSON. If you were not explicitly asked for a migration, don't touch
  the DB.
- **Dependencies and permissions.** Don't add Gradle dependencies, modules or permissions without
  asking. APK size, startup time and battery are features here.
- **Versions.** Never bump `versionCode` / `versionName` in `app/build.gradle.kts`; releases are
  driven by the maintainers.
- **Legacy names are load-bearing.** The `com.metrolist.*` package inside the protobuf submodule,
  `metrolist_strings.xml`, `app/schemas/com.metrolist.music.db.*` and the legacy schema namespace
  must **not** be renamed to "nest": they are DB identities, translated resource files and
  generated code.
- **Flavor parity.** The Cast surface (`cast/CastOptionsProvider`, `playback/CastConnectionHandler`,
  `ui/component/CastButton`) exists in all three flavors — `gms` has the real implementation,
  `foss`/`izzy` ship no-op stubs. Changing that API means touching every flavor.
- **Kotlin style.** Coroutines and `Flow` over callbacks, `StateFlow` in ViewModels, Compose state
  hoisting, no main-thread I/O, no `GlobalScope`. Comments only where the logic is non-obvious.
- **Never commit generated or local files**: `app/src/main/java/` protobuf output, keystores
  (`app/persistent-debug.keystore`, `app/keystore/`), `local.properties`, `build/`, `*.apk`,
  `desktop/src-tauri/binaries/`, caches under `desktop/python-backend/`.
- **Desktop is a separate product.** Verify frontend changes with `npm run lint` and
  `npm run build` (Vite) — never with a plain browser preview, since Tauri APIs are unavailable
  there. The PyInstaller sidecar, Rust shell and bundled Node runtime are build outputs.

---

## Verifying your changes

**Android**

```bash
./gradlew :app:assembleFossDebug        # fastest full check (default flavor, debug)
./gradlew :app:testFossDebugUnitTest    # JVM unit tests (app/src/test/kotlin)
./gradlew :app:lintFossDebug            # lint is per-variant and NON-blocking
./gradlew :app:assembleFossRelease      # release, minified
```

Lint never gates anything (`abortOnError = false`, `checkReleaseBuilds = false`), so a clean
`assemble` is the real gate. Use `-x lint -x lintFossRelease` to keep local builds fast.

**Desktop**

```bash
cd desktop
npm ci
npm run lint        # eslint (eslint src)
npm run build       # vite build — minimum verification for frontend changes
npm run tauri dev   # real end-to-end run (needs the Python sidecar and its dependencies)
```

**iOS** — built and tested through `codemagic.yaml` (`ios/NestMusic.xcodeproj`, scheme
`NestMusic`). If you have a Mac, run the equivalent `xcodebuild`/`simctl` commands; otherwise say
explicitly in the PR that iOS was not verified.

**What CI does.** `build_pr.yml` runs on every pull request (JDK 21) with:

```bash
./gradlew --console=plain assembleFossRelease --warning-mode summary -x lint -x lintFossRelease
```

and uploads the APK as the `app-foss-release` artifact. It is skipped for PRs that only touch
`app/src/main/res/values-*/strings.xml`, `app/src/main/res/values-*/metrolist_strings.xml` or
`fastlane/**`. Other workflows: `build.yml` (APKs), `build_quick.yml` (manual),
`desktop-windows.yml`, `desktop-linux.yml`, `release.yml`.

---

## Translations

Translations are managed on **Crowdin**, so please don't submit them as PRs.

- Android: [`crowdin.yml`](crowdin.yml) declares `app/src/main/res/values/strings.xml` as the
  source, with translations written to `app/src/main/res/values-%android_code%/strings.xml`.
  Localized `metrolist_strings.xml` files also live in the `values-*/` directories.
- Desktop: [`desktop/crowdin.yml`](desktop/crowdin.yml) maps `desktop/src/locales/en.json` to
  `desktop/src/locales/%locale%.json`.

If you added a new string, add it in English only — translators pick it up from there.

---

## Security

Please don't open a public issue for security problems. Follow
[`SECURITY.md`](SECURITY.md) instead.

---

## License

By contributing, you agree that your contributions are licensed under the same license as the part
of the repository you are changing: **GPL-3.0** for the Android/iOS code ([`LICENSE`](LICENSE)) and
**AGPL-3.0** for `desktop/` ([`desktop/LICENSE`](desktop/LICENSE)). Nest Music builds on
[Metrolist](https://github.com/MetrolistGroup/Metrolist) and
[Kodama](https://github.com/KiyoshiTheDevil/Kodama); keep the existing attribution when you touch
their code.
