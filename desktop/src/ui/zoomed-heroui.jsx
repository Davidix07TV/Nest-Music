// Zoom-aware wrappers around HeroUI's DropdownMenu / ModalDialog.
//
// react-aria-components positions the *outer* Popover/Modal wrapper using the trigger's
// real (already-zoomed) screen coordinates, set directly as inline left/top on that same
// element — putting the app's `zoom` on that element too would multiply its own offset
// again (an element's own `zoom` scales its own position, not just its content), throwing
// the popover/modal off by however much zoom is applied. Applying `zoom` to a plain child
// instead scales the content visually without touching where react-aria placed the wrapper.
//
// DropdownMenu/ModalDialog are exactly that plain child (see @heroui/react's dropdown.js:
// DropdownPopover wraps react-aria's positioned Popover, DropdownMenu wraps its Menu; and
// modal.js: ModalContainer wraps react-aria's Modal, ModalDialog wraps its Dialog) — so
// re-exporting zoom-aware versions of just these two, and importing from here instead of
// "@heroui/react" wherever they're used, applies the fix without touching every call site.
import { DropdownMenu as HeroDropdownMenu, ModalDialog as HeroModalDialog } from "@heroui/react";
import { useZoom } from "../context.jsx";

export function DropdownMenu({ style, ...props }) {
  const zoom = useZoom();
  return <HeroDropdownMenu style={{ ...style, zoom }} {...props} />;
}

export function ModalDialog({ style, ...props }) {
  const zoom = useZoom();
  // HeroUI's .modal__dialog has no max-height of its own — it's a flex item inside
  // .modal__container (which IS capped to the real, unzoomed viewport height) and relies on
  // shrinking to fit, with .modal__body--scroll-inside scrolling any overflow. Flex items
  // default to min-height:auto (shrink-resistant), so once `zoom` inflates the dialog's
  // intrinsic content size past what the container allows, it pokes out past the container's
  // edges (no bottom margin, clipped) instead of capping and letting the body scroll
  // internally. maxHeight:100% (of the container's real fixed height) + minHeight:0 lets it
  // actually shrink to fit, handing overflow back to the body's own scroll.
  return <HeroModalDialog style={{ ...style, zoom, maxHeight: "100%", minHeight: 0 }} {...props} />;
}
