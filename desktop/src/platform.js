/**
 * Which OS the desktop shell is running on.
 *
 * Tauri's own platform info would come from plugin-os, which this app does not depend on,
 * so the user agent is read the same way the analytics ping in App.jsx already does.
 * Order matters: macOS WebKit reports "Mac OS X", Linux WebKitGTK reports
 * "X11; Linux x86_64" (on Wayland too), and whatever is left is WebView2 on Windows.
 *
 * The three flags drive the window-chrome decisions that differ per platform:
 *   IS_MAC     — native titled window (traffic lights), no custom titlebar
 *   IS_LINUX   — native decorations as well (see src-tauri/tauri.linux.conf.json), so no
 *                custom titlebar either, but borderless secondary windows need resize edges
 *   IS_WINDOWS — borderless main window with the app's own titlebar
 */
const UA = navigator.userAgent || "";

export const IS_MAC = /Mac OS X|Macintosh/.test(UA);
export const IS_LINUX = !IS_MAC && /Linux|X11/.test(UA);
export const IS_WINDOWS = !IS_MAC && !IS_LINUX;
