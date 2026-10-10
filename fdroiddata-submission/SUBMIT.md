# How to submit Nest Music to F-Droid.org

Everything in this folder is prepared. What remains is manual, because it happens
on GitLab.

## What goes in the merge request

**Exactly one file**, created at `metadata/com.nestmusic.music.yml` in your fork of
fdroiddata, with the contents of
[`metadata/com.nestmusic.music.yml`](metadata/com.nestmusic.music.yml) from this
folder.

Nothing else. Do **not** copy the `metadata/com.nestmusic.music/` locale tree into
fdroiddata — F-Droid's guidance for the main repository is to consume the upstream
Fastlane metadata already present in this repository at `fastlane/metadata/android/`,
and explicitly not to add screenshot assets to fdroiddata. The locale tree here is
staged only in case maintainers ask for it.

## Steps

1. Create an account on https://gitlab.com if you do not have one.
2. Fork https://gitlab.com/fdroid/fdroiddata.
3. Clone your fork, then:

   ```sh
   git checkout -b nest-music
   # paste the recipe into metadata/com.nestmusic.music.yml
   git add metadata/com.nestmusic.music.yml
   git commit -m "New app: Nest Music"
   git push origin nest-music
   ```

4. Open a merge request against `fdroid/fdroiddata`, branch `master`.
5. **Title:** `New app: Nest Music`
6. **Description:** paste the contents of [`MR_DESCRIPTION.md`](MR_DESCRIPTION.md),
   starting from the `## App` heading — skip the `# MR draft — not submitted` line
   at the top.

## Before you open it

- [ ] The release the recipe targets exists and is public: https://github.com/Davidix07TV/Nest-Music/releases/tag/v1.0.12
- [ ] The repository is public and the `LICENSE` file is GPL-3.0
- [ ] You are signed in to GitLab as the account that will own the MR

## What to expect

Review is slow — weeks is normal, not a sign anything is wrong. Maintainers will
likely ask about at least one of the items listed under "Points a reviewer will
want to check" in the MR description. Answer plainly; the three most likely are
the JitPack dependencies, the Aliyun Maven mirror in `settings.gradle.kts`, and
the build-time `protoc` download.

If they ask for a commit SHA instead of the tag name in `commit:`, the SHA for
`v1.0.12` is `644cf390f2cba724c973fb63e994c3f203107dbe`.

## Keeping the recipe honest afterwards

`AutoUpdateMode: Version` with `UpdateCheckMode: Tags ^v[0-9.]+$` means F-Droid
picks up future releases on its own, as long as tags keep the `vX.Y.Z` shape and
`versionCode` keeps increasing. The `build_fdroid_flavor` CI job guards the
variant F-Droid builds, so a change that breaks `assembleIzzyRelease` fails on
master rather than inside F-Droid's build farm.
