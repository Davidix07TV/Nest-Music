@echo off
setlocal enabledelayedexpansion
REM Always run from this script's folder so relative paths hold no matter where it is invoked.
cd /d "%~dp0"
echo === Nest Music - Python sidecar build (Windows x86_64) ===
echo.

REM Tauri externalBin is "binaries/nest-music-server" (see src-tauri/tauri.conf.json).
REM Tauri expects the file name to carry the Rust target triple suffix:
REM   binaries\nest-music-server-x86_64-pc-windows-msvc.exe
set "SIDECAR_NAME=nest-music-server-x86_64-pc-windows-msvc"
set "SIDECAR_EXE=..\src-tauri\binaries\%SIDECAR_NAME%.exe"

REM ── Python virtualenv ────────────────────────────────────────────────────────
py -m venv .venv
if errorlevel 1 (
    echo FEHLER/ERROR: could not create the .venv with "py -m venv". Is the Python launcher installed?
    goto :fail
)
call .venv\Scripts\activate

python -m pip install --upgrade pip --quiet
python -m pip install -r requirements.txt --quiet
python -m pip install pyinstaller --quiet

if errorlevel 1 (
    echo FEHLER/ERROR: pip install failed.
    goto :fail
)

REM ── Build the server executable with the Tauri platform suffix ──────────────
if not exist "..\src-tauri\binaries" mkdir "..\src-tauri\binaries"

echo Compiling server.py with PyInstaller...
pyinstaller --onefile ^
  --name %SIDECAR_NAME% ^
  --distpath ..\src-tauri\binaries ^
  --workpath .\build_tmp ^
  --specpath .\build_tmp ^
  --hidden-import=ytmusicapi ^
  --hidden-import=flask ^
  --hidden-import=flask_cors ^
  --hidden-import=yt_dlp ^
  --hidden-import=pykakasi ^
  --hidden-import=jaconv ^
  --collect-all ytmusicapi ^
  --collect-all yt_dlp ^
  --collect-all pykakasi ^
  --collect-all yt_dlp_ejs ^
  --collect-all yt_dlp_plugins ^
  --add-data "..\.venv\Lib\site-packages\ytmusicapi\locales;ytmusicapi/locales" ^
  server.py

if errorlevel 1 (
    echo FEHLER/ERROR: PyInstaller failed.
    goto :fail
)

REM ── Bundled node.exe (Tauri resource, see src-tauri/tauri.windows.conf.json) ─
REM yt-dlp EJS (signature / n-sig solving) needs Node >= 22 next to the app exe.
if not exist "..\src-tauri\resources" mkdir "..\src-tauri\resources"
if not exist "..\src-tauri\resources\node.exe" (
    echo Downloading bundled node.exe ^(v22.18.0^)...
    curl.exe -fL -o "..\src-tauri\resources\node.exe" https://nodejs.org/dist/v22.18.0/win-x64/node.exe
    if errorlevel 1 (
        echo FEHLER/ERROR: could not download node.exe ^(required Tauri resource^).
        goto :fail
    )
)

REM ── Result ───────────────────────────────────────────────────────────────────
echo.
if exist "%SIDECAR_EXE%" (
    echo Erfolgreich! %SIDECAR_NAME%.exe wurde erstellt:
    echo   %SIDECAR_EXE%
    echo Jetzt kannst du "npm run tauri build" ausfuehren.
) else (
    echo FEHLER: Die .exe wurde nicht erstellt!
    goto :fail
)
echo.
if not defined CI pause
exit /b 0

:fail
echo.
if not defined CI pause
exit /b 1
