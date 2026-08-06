import { cloneElement, useId, useState, type ReactElement, type ReactNode, type SyntheticEvent } from "react";
import { createPortal } from "react-dom";

type TriggerProps = {
  onMouseEnter?: (event: SyntheticEvent) => void;
  onMouseLeave?: (event: SyntheticEvent) => void;
  onFocus?: (event: SyntheticEvent) => void;
  onBlur?: (event: SyntheticEvent) => void;
  "aria-describedby"?: string;
};

type TooltipProps = {
  label: ReactNode;
  children: ReactElement<TriggerProps>;
};

// Clones the show/hide handlers directly onto the single trigger element
// (composing with any handlers it already has) instead of wrapping it in an
// extra DOM node - that lets the same component wrap ordinary HTML elements
// and SVG elements (e.g. a donut slice's <circle>) without ever needing an
// HTML element nested inside an <svg>, which the DOM disallows. The bubble
// itself is portaled to document.body for the same reason: it never needs to
// live inside the (possibly SVG) trigger's subtree.
export function Tooltip({ label, children }: TooltipProps) {
  const [open, setOpen] = useState(false);
  const [coords, setCoords] = useState({ top: 0, left: 0 });
  const id = useId();

  function show(event: SyntheticEvent) {
    const rect = (event.currentTarget as Element).getBoundingClientRect();
    setCoords({ top: rect.bottom + window.scrollY + 6, left: rect.left + window.scrollX });
    setOpen(true);
  }

  function hide() {
    setOpen(false);
  }

  const trigger = cloneElement(children, {
    "aria-describedby": id,
    onMouseEnter: composeHandler(children.props.onMouseEnter, show),
    onMouseLeave: composeHandler(children.props.onMouseLeave, hide),
    onFocus: composeHandler(children.props.onFocus, show),
    onBlur: composeHandler(children.props.onBlur, hide),
  } satisfies TriggerProps);

  return (
    <>
      {trigger}
      {open &&
        createPortal(
          <span
            role="tooltip"
            id={id}
            style={{ position: "absolute", top: coords.top, left: coords.left, zIndex: 100 }}
            className="max-w-[260px] border border-ink/20 bg-ink px-3 py-2 font-sans text-xs text-paper shadow-[0_0_0_3px_#211E18]"
          >
            {label}
          </span>,
          document.body
        )}
    </>
  );
}

function composeHandler<E>(
  existing: ((event: E) => void) | undefined,
  next: (event: E) => void
): (event: E) => void {
  return (event: E) => {
    existing?.(event);
    next(event);
  };
}
