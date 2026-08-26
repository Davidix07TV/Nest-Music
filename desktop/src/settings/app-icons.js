// Alternate app icons for personalization (live: taskbar/window/tray + macOS Dock & bundle).
// `file` matches the PNGs in public/App-Icons/ (also bundled as a Tauri resource for Rust).
export const APP_ICON_DEFAULT = "Nest Music App Icon.png";

export const APP_ICON_GROUPS = [
  { id: "default", labelKey: "appIconDefault", icons: [
    { label: "Nest Music", file: "Nest Music App Icon.png" },
  ]},
];
