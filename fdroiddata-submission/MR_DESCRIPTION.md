# MR draft — not submitted

**Title:** `New app: Nest Music`

## App

Nest Music (`com.nestmusic.music`) is a free and open-source Android client for YouTube Music, licensed under GPL-3.0-only. It is a fork of [Metrolist](https://github.com/MetrolistGroup/Metrolist), itself derived from [InnerTune](https://github.com/z-huang/InnerTune), with attribution retained in the source repository. Because playback depends on YouTube Music, the recipe declares the `NonFreeNet` anti-feature with a reason string.

I am the author of the app and I am submitting it myself.

## Why include it alongside similar apps

Nest Music is a maintained fork rather than a rebrand. Concrete additions over upstream include playlist generation from a natural-language prompt and a project-maintained Listen Together service, so users can open a synchronised listening room without configuring their own server. This description makes no claim that synchronised listening, Discord presence, music recognition or a desktop client are unique across all similar apps. The desktop client is a separate target and is not part of this Android build.

## Source build

- Package: `com.nestmusic.music`
- License: GPL-3.0-only (matches the `LICENSE` file in the repository root)
- Source: https://github.com/Davidix07TV/Nest-Music
- Tag `v1.0.12` → `versionName` 1.0.12, `versionCode` 159. The recipe pins the tag name, consistent with `UpdateCheckMode: Tags` and `AutoUpdateMode: Version`; happy to pin the commit SHA instead if you prefer.
- Build variant: Gradle `izzy` product flavour, which disables Google Cast and the in-app updater. It is the only F-Droid-compliant flavour; `foss` and `gms` both ship the in-app updater.
- The build checks out the `metroproto` Git submodule, hence `submodules: true`.
- `subdir: app` — the Android application module lives in `app/`, alongside the library modules `innertube`, `kugou`, `lrclib`, `lastfm`, `betterlyrics`, `shazamkit` and `paxsenix`.

### Build environment requirements

Please note these before running the build, as the toolchain is recent:

- **JDK 21** — `sourceCompatibility`/`targetCompatibility` are `VERSION_21` and `jvmTarget` is `JVM_21`
- **Gradle 9.7.0** — pinned in `gradle/wrapper/gradle-wrapper.properties`
- **Android Gradle Plugin 9.3.0**, `compileSdk`/`targetSdk` 36, `minSdk` 26
- The `foojay-resolver` toolchain plugin is deliberately commented out in `settings.gradle.kts` because F-Droid does not support it

## Points a reviewer will want to check

Listed up front rather than left to be discovered:

1. **JitPack dependencies.** Three artifacts resolve through JitPack: `com.github.MetrolistGroup:MetrolistExtractor`, `com.github.yalantis:ucrop` and `com.github.promeG:tinypinyin`. All three are FLOSS and built from public sources, but they are fetched as prebuilt artifacts. Happy to discuss substitutions if this is a blocker.
2. **Extra Maven mirror.** `settings.gradle.kts` lists `https://maven.aliyun.com/repository/public` as an additional repository. It is a mirror of Maven Central kept for contributors behind restrictive networks, not a source of unique artifacts. I can remove it from the recipe with a `prebuild` step, or from the project outright, if preferred.
3. **`protoc` download.** The protobuf Gradle plugin downloads the FLOSS `protoc` compiler from Maven Central at build time for the `metroproto` submodule. This has not yet been exercised inside the F-Droid build environment.
4. **Reproducibility.** Not tested, and no reproducibility claim is made in this MR.
5. **`RECORD_AUDIO`.** The only dangerous permission. It is used solely by the optional music recognition feature, which records a single bounded sample of ~12 seconds (`RECORDING_DURATION_MS` in `MusicRecognitionService.kt`), keeps it in memory, never writes it to storage, and transmits only a computed audio fingerprint — not the recording. The listing describes this explicitly under "Permissions".

## Prior review by IzzyOnDroid

The app was proposed to IzzyOnDroid ([repodata issue #686](https://codeberg.org/IzzyOnDroid/repodata/issues/686)) and was declined there under that repository's App Inclusion AI Policy. Independently of that decision, the maintainer ran a courtesy APK scan on the `v1.0.10` release (SHA-256 `e905d7e4a5ae2f85a5356c7d9fcde66dae2db22a5dbe163e8238bcda71ab0f85`) and reported: *"Scan results look fine, fastlane tree looks fine – you might consider heading over to F-Droid.org for inclusion."*

The scan found 12 permissions with `RECORD_AUDIO` as the only dangerous one, and detected libraries that are all Apache-2.0 or compatible. The scan covered `v1.0.10`; this MR submits `v1.0.12`. The differences are the updater change in 1.0.11 and the listing and CI changes in 1.0.12, both described in the release notes — no change to the permission set.

## Use of AI assistance

Stated plainly, because I would rather disclose it than have it inferred: parts of this project were developed with AI assistance, and the repository description says so. Every release is reviewed, built and tested by me before tagging, and the project carries CI covering build, lint and CodeQL analysis. F-Droid does not currently have a policy on this; I am flagging it so the decision is made with full information rather than after the fact.

## Listing metadata and screenshots

Localized listing metadata lives upstream at `fastlane/metadata/android/`, covering 31 locales plus the English summary and full description, the changelog for version code 158, the icon, and six phone screenshots (`en-US/images/phoneScreenshots/1.jpg` … `6.jpg`).

F-Droid's guidance for apps entering the main repository is to consume upstream Fastlane metadata and **not** to add screenshot assets to fdroiddata, so the MR contains only the recipe. A `metadata/com.nestmusic.music/<locale>/` tree using F-Droid's `summary.txt` / `description.txt` naming is staged in the source repository for reference and can be supplied if maintainers want it.

Screenshots for review:

- [1](https://raw.githubusercontent.com/Davidix07TV/Nest-Music/v1.0.12/fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg) · [2](https://raw.githubusercontent.com/Davidix07TV/Nest-Music/v1.0.12/fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg) · [3](https://raw.githubusercontent.com/Davidix07TV/Nest-Music/v1.0.12/fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg) · [4](https://raw.githubusercontent.com/Davidix07TV/Nest-Music/v1.0.12/fastlane/metadata/android/en-US/images/phoneScreenshots/4.jpg) · [5](https://raw.githubusercontent.com/Davidix07TV/Nest-Music/v1.0.12/fastlane/metadata/android/en-US/images/phoneScreenshots/5.jpg) · [6](https://raw.githubusercontent.com/Davidix07TV/Nest-Music/v1.0.12/fastlane/metadata/android/en-US/images/phoneScreenshots/6.jpg)

## Inclusion checklist

- [x] One Android app in this MR
- [x] Public source and a GPL license matching the repository `LICENSE`
- [x] Application ID distinct from the upstream projects
- [x] Submitted by the app author
- [x] Version tag and version code exist, and the pinned commit matches the tag
- [x] Upstream listing metadata, changelog, icon and screenshots present
- [x] `NonFreeNet` declared with a reason string
- [x] Dangerous permission documented in the listing
- [x] CI compiles the exact `izzy` flavour this recipe builds, on every push to master
- [x] `fdroid lint` and `fdroid readmeta` pass clean against the recipe (fdroidserver 2.4.5)
- [ ] Build `assembleIzzyRelease` inside the F-Droid build environment
- [ ] Confirm the Maven Central `protoc` download works in that environment
- [ ] Check reproducibility

## CI status

The `v1.0.11` tag's Android release workflow run [37668543387](https://github.com/Davidix07TV/Nest-Music/actions/runs/37668543387) succeeded, including the `izzy` release build, signing and verification. `v1.0.12` is cut by the same workflow from master.

A later change ([PR #46](https://github.com/Davidix07TV/Nest-Music/pull/46)) dropped the `izzy` APK from the release workflow, since no repository consumed it once the IzzyOnDroid listing was declined. That left the F-Droid flavour built by nothing. A dedicated `build_fdroid_flavor` job has since been added to `.github/workflows/build.yml`: it runs `./gradlew assembleIzzyRelease` on every push to master as a compile check (no signing, no artifact upload), so the variant this recipe builds cannot break silently. The published GitHub release continues to expose the standard FOSS APK only; F-Droid builds from tagged source regardless.

`fdroid lint` and `fdroid readmeta` (fdroidserver 2.4.5) both pass against this recipe with no warnings. `fdroid build` has not been run — it needs the F-Droid build environment, see the open checklist items above. The upstream build script is intentionally unmodified.
