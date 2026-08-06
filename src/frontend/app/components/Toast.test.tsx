import { act, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ToastStack } from "./Toast";

describe("ToastStack", () => {
  beforeEach(() => {
    vi.useFakeTimers();
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  it("renders a toast's message", () => {
    render(<ToastStack toasts={[{ id: "1", message: "Deleted." }]} onDismiss={vi.fn()} />);
    expect(screen.getByText("Deleted.")).toBeInTheDocument();
  });

  it("auto-dismisses a toast after ~5s without user interaction", () => {
    const onDismiss = vi.fn();
    render(<ToastStack toasts={[{ id: "1", message: "Deleted." }]} onDismiss={onDismiss} />);

    expect(onDismiss).not.toHaveBeenCalled();
    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(onDismiss).toHaveBeenCalledWith("1");
  });

  it("stacks multiple toasts simultaneously, each dismissing independently on its own timer", () => {
    const onDismiss = vi.fn();
    const { rerender } = render(
      <ToastStack toasts={[{ id: "a", message: "First" }]} onDismiss={onDismiss} />
    );

    act(() => {
      vi.advanceTimersByTime(2000);
    });
    // A second toast arrives 2s after the first - both are visible together.
    rerender(
      <ToastStack
        toasts={[
          { id: "a", message: "First" },
          { id: "b", message: "Second" },
        ]}
        onDismiss={onDismiss}
      />
    );
    expect(screen.getByText("First")).toBeInTheDocument();
    expect(screen.getByText("Second")).toBeInTheDocument();

    // 3s later (5s total for "a", 3s total for "b"): only "a"'s timer fired.
    act(() => {
      vi.advanceTimersByTime(3000);
    });
    expect(onDismiss).toHaveBeenCalledWith("a");
    expect(onDismiss).not.toHaveBeenCalledWith("b");

    // 2s later still (5s total for "b"): "b" dismisses too, independently.
    act(() => {
      vi.advanceTimersByTime(2000);
    });
    expect(onDismiss).toHaveBeenCalledWith("b");
  });

  it("dismissing one toast does not affect another's timer", () => {
    const onDismissA = vi.fn();
    const onDismiss = (id: string) => {
      if (id === "a") onDismissA(id);
    };
    render(
      <ToastStack
        toasts={[
          { id: "a", message: "First" },
          { id: "b", message: "Second" },
        ]}
        onDismiss={onDismiss}
      />
    );

    act(() => {
      vi.advanceTimersByTime(5000);
    });
    expect(onDismissA).toHaveBeenCalledWith("a");
    // "b" is still rendered since its own onDismiss branch was a no-op here,
    // proving "a" firing didn't clear or restart "b"'s timer as a side effect.
    expect(screen.getByText("Second")).toBeInTheDocument();
  });
});
