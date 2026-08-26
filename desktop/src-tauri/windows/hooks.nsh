; Kiyoshi Music — NSIS Installer Hooks
; Cleans up leftover temp folders on uninstall.

!macro NSIS_HOOK_PREINSTALL
  ; On an in-app update the bgutil PO-token Node child (node.exe in the install dir) and the
  ; Python sidecar can outlive the old app and keep a lock on their own files — which makes
  ; extraction fail with "Error opening file for writing: ...\node.exe". Ensure they're gone
  ; before we write. This runs in the NEW installer, so it fixes updates FROM an older build
  ; whose graceful shutdown didn't yet kill these children. From the fixed builds onward they
  ; are already terminated by the time the installer runs, so this is just a safety net.
  nsExec::Exec 'taskkill /F /T /IM node.exe'
  nsExec::Exec 'taskkill /F /T /IM kodama-server.exe'
  Sleep 500
!macroend

!macro NSIS_HOOK_POSTINSTALL
!macroend

!macro NSIS_HOOK_PREUNINSTALL
!macroend

!macro NSIS_HOOK_POSTUNINSTALL
  ; Remove kiyoshi-audio temp folder (used by the audio backend)
  RMDir /r "$TEMP\kiyoshi-audio"

  ; Remove updater staging folders (e.g. "Kiyoshi Music-0.9.5-alpha-updater-XXXXXX")
  FindFirst $R0 $R1 "$TEMP\Kiyoshi Music-*-updater-*"
  _KiyoshiCleanLoop:
    StrCmp $R1 "" _KiyoshiCleanDone
    RMDir /r "$TEMP\$R1"
    FindNext $R0 $R1
    Goto _KiyoshiCleanLoop
  _KiyoshiCleanDone:
  FindClose $R0
!macroend
