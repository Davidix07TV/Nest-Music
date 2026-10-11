# Accessibility

Nest Music should be usable by everyone, including people who use screen readers, keyboards, game controllers, larger text, higher contrast, or less motion. This statement explains what we try to do, what we know does not work yet, and how to report a barrier.

It covers the Android app (`app/`), the Windows and Linux desktop app (`desktop/`), the iOS app (`ios/`), and the project documentation. You do not need a disability, special equipment, or technical knowledge to report a problem. If something got in your way, that is enough reason to tell us.

This statement was written on 2026-10-11 from a review of the repository. It describes what we knew at that date.

## Priorities

We put the most weight on barriers that stop someone from finishing a task: starting or pausing playback, finding a song, reading lyrics, managing a playlist, and changing settings. Small visual problems matter too, but we fix blocking problems first.

The areas we prioritize:

- **Screen readers.** Buttons and controls should have names a screen reader can announce. On Android, icon-only buttons are expected to have a content description. A control with no name is a barrier.
- **Keyboard and controller use.** The desktop app supports keyboard navigation with configurable shortcuts. Its "Big Picture" mode is designed for keyboard and gamepad navigation.
- **Text size, contrast and motion.** The Android app follows the system font size setting. The desktop app has its own font scale, UI zoom, high-contrast mode, lyrics font sizes, and an animations on/off switch.
- **Languages.** The apps are translated through Crowdin. Android has translation folders for dozens of languages, and the desktop app ships a set of its own language files.

We have not set a formal accessibility target, and no formal accessibility evaluation has been carried out. If we ever publish a conformance claim, it will name the scope, date, method, and evaluator.

## Contributor expectations

The project does not yet have an accessibility test suite. These are the expectations for changes that touch the user interface:

- **Android (Kotlin, Jetpack Compose):** give every icon-only button and every meaningful image a `contentDescription`, or another accessible name. Put new user-facing English strings in `app/src/main/res/values/metrolist_strings.xml`. Do not edit `values-*/` folders, because Crowdin owns them.
- **Desktop (React, Tauri):** use native buttons or the existing React Aria components where possible. Give icon-only controls an `aria-label`. Keep keyboard focus visible and reachable, and do not remove a focus outline unless you add a replacement.
- **iOS (SwiftUI):** add accessibility labels to custom controls. The current iOS source does not set any custom accessibility labels.

Automated checks available today:

- `./gradlew :app:lintFossDebug` runs Android lint. Lint does not block builds here (`abortOnError = false`), so accessibility warnings do not stop a merge.
- `npm run lint` in `desktop/` runs a deliberately narrow ESLint setup. It does not check accessibility rules, and the CI workflows do not run it.
- CI runs the JVM unit tests (`.github/workflows/pr-checks.yml`). No UI or accessibility tests run automatically.

Evidence for user-facing changes: in the **Testing** section of your pull request, say what you ran, which device, OS, or browser you used, and which assistive technology you tested with (for example TalkBack, NVDA, Narrator, or Orca). Say what you could not test. Screenshots or a short recording help with visual changes but are optional. For accessibility fixes, a short description of what the screen reader announced before and after is the most useful evidence.

Under the current feature freeze (see `CONTRIBUTING.md`), fixing an existing barrier is a bug fix and is welcome. A new accessibility feature counts as a new feature, so open an issue first.

## Reporting accessibility issues

Report barriers through GitHub Issues, or through Discord for the desktop app.

- **GitHub Issues:** <https://github.com/Davidix07TV/Nest-Music/issues>. Use the bug report form, because blank issues are disabled. Write in English and keep one problem per issue. The Android form asks for a logcat recording. If you use assistive technology and cannot capture a log, say so in the issue and describe what you can observe. That is still useful, but the report may need more follow-up.
- **Discord (desktop):** the desktop README asks for desktop bug reports on the Discord server: <https://discord.gg/rhreShDJxn>.

Useful details, if you have them. None are required:

- The task you were trying to finish, and where you got stuck.
- The screen or section of the app involved.
- What happened, and what you expected to happen.
- The app version (shown in Settings), your device or operating system, and its version.
- The assistive technology you used and its version, or whether you used only the keyboard or a controller.
- A screenshot or recording, if it is easy to share. This is never required.

You never need to disclose a disability or medical information to report a barrier.

### Severity

The project does not use formal severity labels yet. The bug form only applies the `bug` label. The levels below are guidance for triage. Maintainers assign or confirm the level. Reporters do not need to.

- **Blocking:** the task cannot be completed at all. For example, a screen reader user cannot start playback, or a keyboard-only user cannot reach the settings screen.
- **Major:** the task is possible but much harder. For example, a button has no name, so the user has to guess what it does.
- **Minor:** the task works, but it is awkward or unclear. For example, a label is vague or a focus indicator is hard to see.

### How we respond

We read new accessibility reports and may ask follow-up questions to reproduce the problem. We cannot promise a fixed response time. Where we can, we will:

- point out a workaround while a fix is pending;
- treat accessibility fixes as bug fixes, even during the feature freeze;
- ask you to check a fix before we close the issue, when that is practical.

## Ownership and maintenance

There is no dedicated accessibility owner yet. The repository owner, [@Davidix07TV](https://github.com/Davidix07TV), handles triage for the project, including accessibility reports. The owner is responsible for triaging accessibility reports and confirming severity, reviewing accessibility-related pull requests, and keeping this statement current.

The review cadence has not been set. Until it is, this statement should be reviewed when a release is prepared and whenever a large accessibility change is merged. If maintenance responsibility changes, the new maintainer should review this document and the open accessibility issues.

## Supported environments

| Product | Platform | Versions and build | Status |
| --- | --- | --- | --- |
| Android (`app/`) | Phones and tablets | Android 8.0+ (API 26+), target API 36. Published APK is the FOSS flavor. | Supported. Screen reader testing not recorded. |
| Desktop (`desktop/`) | Windows | NSIS installer built by CI | Supported. Screen reader testing not recorded. |
| Desktop (`desktop/`) | Linux, x86_64 | AppImage and `.deb` built by CI | Supported. Screen reader testing not recorded. |
| Desktop (`desktop/`) | macOS (Apple Silicon) | Build from source only. No published bundles. | Not supported. |
| iOS (`ios/`) | iPhone | SwiftUI, built through Codemagic | Not evaluated for accessibility. |

Input methods:

- **Android:** touch. System font size is respected.
- **Desktop:** mouse, keyboard (with configurable shortcuts), and a gamepad in Big Picture mode. On Linux, media keys work through MPRIS.
- **Assistive technology:** we have not recorded testing with any specific screen reader (TalkBack, VoiceOver, NVDA, Narrator, or Orca). If you test one, please tell us the version and what you found.

The desktop app is a Tauri 2 app, so it renders in the system web view (WebView2 on Windows, WebKitGTK on Linux).

## Known limitations

We have not carried out a formal accessibility audit of any part of the project, so this list is incomplete. Known or suspected barriers:

- **Screen readers:** neither the Android app nor the desktop app has been checked with a screen reader in a recorded test. Behavior may vary between screen readers and versions.
- **Android icon labels:** a large number of Android files set content descriptions, but we have not audited every screen. Some icon-only controls may still be unnamed.
- **iOS:** the code does not set custom accessibility labels, so VoiceOver may announce some controls poorly. The iOS app has not been tested.
- **Desktop Big Picture mode:** it is built for keyboard, gamepad, and TV-style navigation. It has not been evaluated with a screen reader.
- **Motion:** animated lyrics and visual effects have not been checked against reduced-motion needs on Android. The desktop app has an animations on/off switch.
- **Video:** the app plays music videos from YouTube Music. We have not checked whether caption controls are available in the video view.

Workarounds that exist today:

- **Android:** set the system font size. The app follows it.
- **Desktop:** adjust the font scale, UI zoom, lyrics font sizes, and high-contrast mode in Settings. Remap keyboard shortcuts. Turn off animations. Use Big Picture mode for keyboard or gamepad navigation.

No tracked accessibility issues are linked from this document yet.

## Feedback and improvements

To suggest a change to this statement or to the project's accessibility practices, open an issue or send a pull request against `master`. Documentation changes are welcome during the feature freeze.

If you are describing an active barrier in the app, use the reporting process above instead.
