#!/usr/bin/env bash
#
# Nest Music — Python sidecar build (Linux x86_64).
#
# Linux counterpart of build_server.bat. Produces exactly what Tauri's `externalBin`
# entry ("binaries/nest-music-server", see src-tauri/tauri.conf.json) expects:
#
#   src-tauri/binaries/nest-music-server-x86_64-unknown-linux-gnu
#
# plus the bundled `node` runtime that yt-dlp needs for EJS signature/n-sig solving
# (Tauri resource, see src-tauri/tauri.linux.conf.json).
#
# Usage:
#   ./build_server.sh                        # venv + pip + PyInstaller + node download
#   PYTHON=python3.12 ./build_server.sh      # freeze with a specific interpreter
#
# Optional extras, mirroring the release CI:
#   --with-composer   build the vendored Lyrics Composer (composer/dist) first
#   --with-potgen     build the bgutil PO-token generator into src-tauri/resources/potgen/server
#
set -euo pipefail

# Always run from this script's folder so relative paths hold no matter where it is invoked.
cd "$(dirname "${BASH_SOURCE[0]}")"

SIDECAR_TRIPLE="x86_64-unknown-linux-gnu"
SIDECAR_NAME="nest-music-server-${SIDECAR_TRIPLE}"
SIDECAR_SPEC="${SIDECAR_NAME}.spec"
SIDECAR_BIN="../src-tauri/binaries/${SIDECAR_NAME}"
NODE_VERSION="22.18.0"   # >= 22 required by yt-dlp EJS (same version as the Windows/macOS builds)
POTGEN_VERSION="1.3.1"

WITH_COMPOSER=0
WITH_POTGEN=0
for arg in "$@"; do
  case "$arg" in
    --with-composer) WITH_COMPOSER=1 ;;
    --with-potgen)   WITH_POTGEN=1 ;;
    -h|--help)       sed -n '2,25p' "${BASH_SOURCE[0]}"; exit 0 ;;
    *) echo "ERROR: unknown argument '$arg' (see --help)" >&2; exit 1 ;;
  esac
done

echo "=== Nest Music - Python sidecar build (Linux x86_64) ==="

if [ "$(uname -s)" != "Linux" ]; then
  echo "ERROR: this script is for Linux. On Windows use build_server.bat." >&2
  exit 1
fi
ARCH="$(uname -m)"
if [ "$ARCH" != "x86_64" ]; then
  echo "ERROR: only x86_64 Linux builds are supported (found '$ARCH')." >&2
  echo "       A ${SIDECAR_NAME} sidecar requires a matching Rust target and PyInstaller spec." >&2
  exit 1
fi

PYTHON="${PYTHON:-python3}"
command -v "$PYTHON" >/dev/null 2>&1 || { echo "ERROR: $PYTHON not found." >&2; exit 1; }

# PyInstaller cannot freeze an interpreter that has no *shared* libpython: Debian and Ubuntu
# link /usr/bin/python3 statically and ship libpython3.x.so in a separate package, so the
# build dies with "Python shared library ('libpython3.11.so.1.0') was not found". Fail here
# with the fix instead of 90 seconds later inside PyInstaller. (GitHub Actions is fine —
# actions/setup-python installs a standalone build that always has the shared library.)
if ! "$PYTHON" - <<'PY'
import ctypes.util, os, sys, sysconfig
v = sysconfig.get_config_vars()
libdir = v.get("LIBDIR") or ""
names = [v.get("LDLIBRARY"), v.get("INSTSONAME"), v.get("LIBRARY")]
dirs = [libdir, os.path.join(libdir, "x86_64-linux-gnu"), "/usr/lib/x86_64-linux-gnu"]
found = any(n and os.path.exists(os.path.join(d, n)) for d in dirs for n in names)
found = found or bool(ctypes.util.find_library("python%d.%d" % sys.version_info[:2]))
sys.exit(0 if found else 1)
PY
then
  echo "ERROR: no shared libpython found for $PYTHON — PyInstaller needs it." >&2
  echo "       Debian/Ubuntu: sudo apt install python3-venv libpython3.\$(python3 -c 'import sys;print(\"%d.%d\"%sys.version_info[:2])')" >&2
  echo "       Fedora:        sudo dnf install python3-libs" >&2
  echo "       Or point PYTHON at an interpreter that has one: PYTHON=~/.venvs/py/bin/python $0" >&2
  exit 1
fi

# ── Python virtualenv ────────────────────────────────────────────────────────
if [ ! -d .venv ]; then
  "$PYTHON" -m venv .venv || { echo "ERROR: could not create .venv (is python3-venv installed?)" >&2; exit 1; }
fi
# shellcheck disable=SC1091
source .venv/bin/activate

python -m pip install --upgrade pip --quiet
python -m pip install -r requirements.txt --quiet
python -m pip install pyinstaller --quiet

# ── Optional: vendored Lyrics Composer (bundled into the frozen server) ──────
# The .spec packs ../composer/dist as data; without it the Composer routes 404 in the
# packaged app. Release CI always builds it, local builds may skip it.
if [ "$WITH_COMPOSER" = "1" ]; then
  echo "Building the vendored Composer (composer/dist)..."
  ( cd ../composer
    if ! command -v pnpm >/dev/null 2>&1; then npm install -g pnpm@9; fi
    pnpm install --frozen-lockfile
    pnpm build )
fi
if [ ! -d ../composer/dist ]; then
  echo "WARNING: ../composer/dist is missing — the Lyrics Composer will not work in the"
  echo "         packaged app. Re-run with --with-composer (or build it manually) if you need it."
fi

# ── Build the frozen server with the Tauri target-triple name ────────────────
mkdir -p ../src-tauri/binaries
echo "Compiling server.py with PyInstaller (${SIDECAR_SPEC})..."
pyinstaller "$SIDECAR_SPEC" \
  --distpath ../src-tauri/binaries \
  --workpath "build_tmp/${SIDECAR_NAME}" \
  --noconfirm
chmod +x "$SIDECAR_BIN"

# ── Bundled node runtime (Tauri resource, see src-tauri/tauri.linux.conf.json) ─
mkdir -p ../src-tauri/resources
if [ ! -f ../src-tauri/resources/node ]; then
  echo "Downloading bundled node (v${NODE_VERSION}, linux-x64)..."
  NODE_TARBALL="node-v${NODE_VERSION}-linux-x64.tar.gz"
  TMP_DIR="$(mktemp -d)"
  trap 'rm -rf "$TMP_DIR"' EXIT
  curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/${NODE_TARBALL}" -o "${TMP_DIR}/${NODE_TARBALL}"
  tar -xzf "${TMP_DIR}/${NODE_TARBALL}" -C "$TMP_DIR"
  cp "${TMP_DIR}/node-v${NODE_VERSION}-linux-x64/bin/node" ../src-tauri/resources/node
fi
chmod +x ../src-tauri/resources/node

# ── Optional: bgutil PO-token generator (registered as a resource by CI only) ─
# Kept out of the committed tauri.linux.conf.json so a plain `tauri build` never needs it.
if [ "$WITH_POTGEN" = "1" ]; then
  echo "Building the bgutil PO-token generator (v${POTGEN_VERSION})..."
  POTGEN_SRC="$(mktemp -d)/potgen-src"
  git clone --single-branch --branch "$POTGEN_VERSION" --depth 1 \
    https://github.com/Brainicism/bgutil-ytdlp-pot-provider.git "$POTGEN_SRC"
  ( cd "$POTGEN_SRC/server"
    npm ci
    npx tsc
    npm prune --omit=dev )
  DEST="../src-tauri/resources/potgen/server"
  mkdir -p "$DEST"
  cp -r "$POTGEN_SRC/server/build" "$POTGEN_SRC/server/package.json" "$POTGEN_SRC/server/node_modules" "$DEST/"
  rm -rf "$(dirname "$POTGEN_SRC")"
  CFG="../src-tauri/tauri.linux.conf.json"
  # Register the generator as a Tauri resource for THIS build only.
  python - "$CFG" <<'PY'
import json, sys
path = sys.argv[1]
with open(path, encoding="utf-8") as f:
    cfg = json.load(f)
cfg["bundle"]["resources"]["resources/potgen/server"] = "potgen/server"
with open(path, "w", encoding="utf-8") as f:
    json.dump(cfg, f, indent=2)
    f.write("\n")
print(f"merged potgen resource into {path}")
PY
fi

# ── Result ───────────────────────────────────────────────────────────────────
echo
if [ -x "$SIDECAR_BIN" ]; then
  echo "Done: $(cd .. && pwd)/src-tauri/binaries/${SIDECAR_NAME}"
  echo "You can now run: npm run tauri build"
else
  echo "ERROR: the sidecar was not created at ${SIDECAR_BIN}" >&2
  exit 1
fi
