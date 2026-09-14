/**
 * Invisible resize borders for Tauri's borderless windows.
 *
 * Windows and macOS resize an undecorated (`decorations: false`) window natively — tao
 * hit-tests the frame edges for them. Linux has no such fallback: with the decorations gone
 * there is nothing left to grab, so a resizable borderless window (the mini player, the
 * overlay editor) can be moved but never resized. These eight strips call Tauri's
 * `startResizeDragging`, which asks the windowing system to begin an interactive resize
 * exactly like a real frame edge would.
 *
 * Renders nothing on Windows and macOS, where the native behaviour must not be shadowed.
 */
import { getCurrentWindow } from "@tauri-apps/api/window";
import { IS_LINUX } from "../platform.js";

const EDGE = 6;    // px of grabbable border along each side
const CORNER = 12; // px square in each corner, where both axes resize at once

// Tauri's ResizeDirection is a string union type, not a runtime enum, so the eight
// directions are spelled out here (they are what window.startResizeDragging accepts).
const STRIPS = [
  { dir: "North",     pos: { top: 0, left: CORNER, right: CORNER, height: EDGE },    cursor: "ns-resize" },
  { dir: "South",     pos: { bottom: 0, left: CORNER, right: CORNER, height: EDGE }, cursor: "ns-resize" },
  { dir: "West",      pos: { left: 0, top: CORNER, bottom: CORNER, width: EDGE },    cursor: "ew-resize" },
  { dir: "East",      pos: { right: 0, top: CORNER, bottom: CORNER, width: EDGE },   cursor: "ew-resize" },
  { dir: "NorthWest", pos: { top: 0, left: 0, width: CORNER, height: CORNER },       cursor: "nwse-resize" },
  { dir: "SouthEast", pos: { bottom: 0, right: 0, width: CORNER, height: CORNER },   cursor: "nwse-resize" },
  { dir: "NorthEast", pos: { top: 0, right: 0, width: CORNER, height: CORNER },      cursor: "nesw-resize" },
  { dir: "SouthWest", pos: { bottom: 0, left: 0, width: CORNER, height: CORNER },    cursor: "nesw-resize" },
];

export function WindowResizeEdges() {
  if (!IS_LINUX) return null;

  const startResize = (direction) => (e) => {
    if (e.button !== 0) return;
    // Keep the press from reaching whatever sits under the strip (a drag region would
    // otherwise start moving the window at the same time).
    e.preventDefault();
    e.stopPropagation();
    getCurrentWindow().startResizeDragging(direction).catch(() => {});
  };

  return (
    <>
      {STRIPS.map(({ dir, pos, cursor }) => (
        <div
          key={dir}
          onPointerDown={startResize(dir)}
          style={{
            position: "fixed",
            ...pos,
            cursor,
            touchAction: "none",
            zIndex: 2147483000,
            background: "transparent",
          }}
        />
      ))}
    </>
  );
}
