# MR draft — not submitted

**Title:** `New app: Nest Music`

## App

Nest Music (`com.nestmusic.music`) is a free and open-source Android client for YouTube Music, licensed under GPL-3.0-only. It is based on Metrolist, itself derived from InnerTune, with attribution retained in the upstream repository. Since it connects to YouTube Music, the package declares the `NonFreeNet` anti-feature.

## Why include it alongside similar apps?

Nest Music is a maintained fork, not only a name/icon change. A documented Android addition is generating playlists from a natural-language prompt. It also ships with a Nest-maintained default Listen Together service, so users can start a listening room without configuring a server. These are concrete additions; this description does **not** claim that AI, synchronized listening, Discord presence, music recognition, or a desktop client are unique across all similar apps. The desktop client is not part of this Android build.

## Source build

- Package: `com.nestmusic.music`
- License: GPL-3.0-only
- Source: https://github.com/Davidix07TV/Nest-Music
- Release/tag: `v1.0.11` (`versionName` 1.0.11, `versionCode` 158), pointing to commit `04a422bb9f4fd9ee7096c76274cff43694714452`
- Build variant: Gradle `izzy` flavor, which excludes Google Cast and the in-app updater
- The build uses the `metroproto` Git submodule; the recipe enables submodule checkout.

## Listing metadata and screenshots

The localized listing metadata is already in the app source at `fastlane/metadata/android/`, including 31 locales, the English summary/full description, changelog for version code 158, icon, and six phone screenshots (`en-US/images/phoneScreenshots/1.jpg` through `6.jpg`). I also staged the requested `metadata/com.nestmusic.music/<locale>/` tree with F-Droid's `summary.txt` / `description.txt` naming and locale subfolders for review. Important: F-Droid's official guidance for apps entering the main repository recommends using upstream Fastlane metadata and specifically says not to add screenshot assets to fdroiddata; these staged locale copies are not required in the final MR unless maintainers request them. The build recipe remains the sibling file `metadata/com.nestmusic.music.yml`.

Screenshot links for review:

- [Screenshot 1](https://raw.githubusercontent.com/Davidix07TV/Nest-Music/v1.0.11/fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg)
- [Screenshot 2](https://raw.githubusercontent.com/Davidix07TV/Nest-Music/v1.0.11/fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg)
- [Screenshot 3](https://raw.githubusercontent.com/Davidix07TV/Nest-Music/v1.0.11/fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg)
- [Screenshot 4](https://raw.githubusercontent.com/Davidix07TV/Nest-Music/v1.0.11/fastlane/metadata/android/en-US/images/phoneScreenshots/4.jpg)
- [Screenshot 5](https://raw.githubusercontent.com/Davidix07TV/Nest-Music/v1.0.11/fastlane/metadata/android/en-US/images/phoneScreenshots/5.jpg)
- [Screenshot 6](https://raw.githubusercontent.com/Davidix07TV/Nest-Music/v1.0.11/fastlane/metadata/android/en-US/images/phoneScreenshots/6.jpg)

## Inclusion checklist

- [x] One Android app in this MR
- [x] Public source and GPL license
- [x] Distinct application ID from the upstream projects
- [x] Upstream author/creator has confirmed the inclusion
- [x] Version tag and version code exist
- [x] Upstream listing metadata, changelog, icon, and screenshots are present
- [x] `NonFreeNet` declared for reliance on YouTube Music
- [ ] Run `fdroid lint` / metadata validation
- [ ] Build `assembleIzzyRelease` in the F-Droid build environment
- [ ] Confirm whether the direct Maven Central download of the FLOSS `protoc` compiler works in that build environment; it has not been tested here
- [ ] Check reproducibility; no reproducibility claim is made in this draft

## CI and binary notes (checked 2026-10-08)

The GitHub `v1.0.11` tag exists and points to the commit listed above. Its Android release workflow run [37668543387](https://github.com/Davidix07TV/Nest-Music/actions/runs/37668543387) succeeded, including the `izzy` release build, signing, and verification steps. A later merge ([PR #46](https://github.com/Davidix07TV/Nest-Music/pull/46)) removed the `izzy` build from future release workflows; the latest Android release workflow succeeded for the FOSS build only. The current GitHub release page exposes the standard APK only. F-Droid would build from the tagged source rather than use that APK.

No `fdroid build` or metadata lint was run in this session. The Gradle task downloads the FLOSS `protoc` compiler from Maven Central when the protobuf submodule is present. F-Droid's policy allows certain FLOSS build binaries from trusted Maven repositories subject to review, but the exact recipe still needs an F-Droid build-environment test. The upstream build script is intentionally unchanged. Reproducibility has not been checked; do not make a reproducibility claim.

## Maintainer notes (do not publish as a policy declaration)

The app creator confirmed they created Nest Music and wants to proceed with inclusion. The use of AI in writing the code is unknown; this draft makes no claim about it.
