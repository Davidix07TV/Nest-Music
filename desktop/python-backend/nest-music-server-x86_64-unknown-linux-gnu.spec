# -*- mode: python ; coding: utf-8 -*-
import os, importlib.util

_ytm = importlib.util.find_spec('ytmusicapi')
_ytm_locales = os.path.join(os.path.dirname(_ytm.origin), 'locales')

# Vendored Boidu Composer — built static site (repo ./composer/dist) bundled as data,
# extracted to sys._MEIPASS/composer_dist at runtime (served by _composer_dist_dir in
# server.py). Built by build_server.sh --with-composer / by CI before this runs; a local
# build without it still produces a working server (the Composer routes just 404).
_composer_dist = os.path.abspath(os.path.join(SPECPATH, '..', 'composer', 'dist'))
_composer_datas = [(_composer_dist, 'composer_dist')] if os.path.isdir(_composer_dist) else []

# Discord feedback webhook config (gitignored). CI writes it from a secret before building;
# bundled to _MEIPASS root so _load_feedback_webhook() finds it at runtime. Absent → no feedback.
_feedback_cfg = os.path.join(SPECPATH, 'feedback_config.json')
_extra_datas = [(_feedback_cfg, '.')] if os.path.exists(_feedback_cfg) else []

# Last.fm API key + secret (gitignored, same pattern as feedback). CI writes it from secrets.
_lastfm_cfg = os.path.join(SPECPATH, 'lastfm_config.json')
if os.path.exists(_lastfm_cfg):
    _extra_datas.append((_lastfm_cfg, '.'))

# PO-token stack: bundle the bgutil yt-dlp plugin + the yt-dlp-ejs solver scripts so the
# frozen server can discover them (plugins via the yt_dlp_plugins namespace, EJS via its
# data files). The node generator itself ships separately as a Tauri resource (potgen/).
from PyInstaller.utils.hooks import collect_all as _collect_all
_pot_datas = []
_pot_hidden = [
    "yt_dlp_plugins",
    "yt_dlp_plugins.extractor.getpot_bgutil",
    "yt_dlp_plugins.extractor.getpot_bgutil_http",
    "yt_dlp_plugins.extractor.getpot_bgutil_script",
]
for _pkg in ("yt_dlp_ejs", "yt_dlp_plugins"):
    _pd, _pb, _ph = _collect_all(_pkg)
    _pot_datas += _pd
    _pot_hidden += _ph

# pykakasi (romaji conversion) ships its kana/hepburn dictionaries as package data
# (pykakasi/data/*.db) — hiddenimports alone only pulls in the code, not those .db
# files, so romaji silently failed in packaged builds while working in dev (where the
# data files are just sitting on disk next to the installed package).
_kakasi_datas, _kakasi_binaries, _kakasi_hidden = _collect_all("pykakasi")

a = Analysis(
    ['server.py'],
    pathex=[],
    binaries=_kakasi_binaries,
    datas=[(_ytm_locales, 'ytmusicapi/locales')] + _composer_datas + _extra_datas + _pot_datas + _kakasi_datas,
    hiddenimports=["jaconv"] + _pot_hidden + _kakasi_hidden,
    hookspath=[],
    hooksconfig={},
    runtime_hooks=[],
    excludes=[],
    noarchive=False,
    optimize=0,
)
pyz = PYZ(a.pure)

exe = EXE(
    pyz,
    a.scripts,
    a.binaries,
    a.datas,
    [],
    name='nest-music-server-x86_64-unknown-linux-gnu',
    debug=False,
    bootloader_ignore_signals=False,
    strip=False,
    upx=False,  # UPX-compressing a ~100 MB onefile bundle is slow and has broken the
                # bootloader's self-extraction before; the size win is not worth the risk.
    upx_exclude=[],
    runtime_tmpdir=None,
    console=True,
    disable_windowed_traceback=False,
    argv_emulation=False,
    target_arch=None,
    codesign_identity=None,
    entitlements_file=None,
)
