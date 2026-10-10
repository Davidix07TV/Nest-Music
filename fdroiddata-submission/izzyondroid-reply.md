# Draft reply — IzzyOnDroid repodata issue #686

Post at: https://codeberg.org/IzzyOnDroid/repodata/issues/686

Context: the request was declined on 2026-10-08 under the App Inclusion AI Policy.
On 2026-10-09 izzy ran a courtesy APK scan anyway and suggested F-Droid.org.

Guidance for this reply: thank, do not relitigate the decision, do not ask for
reconsideration. Report concretely what the scan changed. Keep it short.

---

Thank you — genuinely. You had already closed the request, so running a scan and
pointing me somewhere else was not something you owed me, and I appreciate it.

No argument about the decision itself: it is your repository and your policy, and
you applied it consistently. I would rather have been declined for being upfront
than have slipped through by staying quiet about it.

The scan was useful beyond the verdict. Three things came out of it:

- `RECORD_AUDIO` showed up as the only dangerous permission, which made me realise
  the listing never explained it. The description now documents exactly what the
  microphone is used for: a single ~12-second sample for music recognition, held
  in memory, never written to storage, and only an audio fingerprint leaves the
  device — not the recording.
- The `izzy` flavour (no Cast, no in-app updater) had quietly stopped being built
  by CI once the listing was declined. It is now compile-checked on every push,
  so it cannot rot.
- Reviewing the F-Droid recipe alongside your scan surfaced a broken `subdir`
  entry that would have failed the build outright.

Taking your suggestion and heading to F-Droid.org next. The `izzy` flavour stays
in the project and stays maintained either way.

Thanks again for the time, and for the way you handled it.
