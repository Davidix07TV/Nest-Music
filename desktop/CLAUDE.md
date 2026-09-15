# Nest Music — desktop sub-project: agent notes

This file only covers `desktop/`. The repo-wide rules (branches, commits, versioning, what never
to commit) live in [`../AGENTS.md`](../AGENTS.md) and apply here too.

## What this is

A **Tauri 2.x** desktop client: React 19 + Vite frontend, a Rust shell (`src-tauri/`), and a Python
Flask sidecar (`python-backend/server.py`). Formerly the **Kodama** project by KiyoshiTheDevil; Nest
Music continues it under **AGPL-3.0** with the original attribution kept. Windows and Linux are the
supported targets (a macOS config exists: `src-tauri/tauri.macos.conf.json`).

## Verifying changes

A browser-based preview (`preview_start`, opening `vite dev` in a browser) is **not** a valid check:
the app calls Tauri APIs (`@tauri-apps/api`, `plugin-dialog`, `plugin-fs`, …) that throw immediately
outside a Tauri window. It will look broken even when the code is fine.

Minimum verification, from `desktop/`:

```bash
npm ci
npm run lint     # eslint — deliberately narrow: catches undefined/use-before-declaration that the build misses
npm run build    # vite build — a clean "✓ built in X.XXs" is the baseline proof the frontend compiles
```

Warnings about dynamic vs. static imports in the Vite output are pre-existing and can be ignored.

Real end-to-end verification is `npm run tauri dev` with the Python dependencies installed — that is
what actually exercises Tauri IPC and the sidecar. Neither `vite build` nor `tauri dev` validates the
Rust shell or the PyInstaller packaging; `npm run tauri build` does (see `README.md` for the
per-OS sidecar/node steps), but it is slow, so run it when you touch `src-tauri/` or the sidecar.

## Structure

- `src/App.jsx` — almost the whole frontend (several thousand lines). Read it before changing it;
  keep edits local instead of restructuring it.
- `src/views/`, `src/settings/`, `src/modals/`, `src/ui/` — screens, settings panels, dialogs, shared components.
- `src/overlay/`, `src/bigpicture/`, `src/miniplayer/`, `src/visualizer/`, `src/effects/` — OBS overlay, TV/big-picture UI, mini player, visualizer, effects.
- `src/lyrics/`, `src/unison/` — synced lyrics rendering and Unison community lyrics.
- `src/i18n.js` + `src/locales/*.json` — UI translations (Crowdin, see `crowdin.yml`); add strings to
  `locales/en.json` and never hand-edit other locales.
- `python-backend/server.py` — Flask sidecar: lyrics proxy, YTMusic API calls, caching (caches are
  written next to it and are gitignored).
- `src-tauri/` — Rust shell (`main.rs` plus `audio`, `discord`, `media`, `obs`, `server`, `window`,
  `appicon`), capabilities, and the per-OS `tauri.*.conf.json` overrides.
- `composer/`, `analytics/`, `docs/` — vendored Lyrics Composer (own toolchain), Cloudflare analytics
  worker, and the static download page.

Rebuild output (`dist/`, `src-tauri/target/`, `src-tauri/gen/`, `src-tauri/binaries/`,
`python-backend/.venv/`, caches) is gitignored — never commit it, and never commit a locally built
sidecar binary.
