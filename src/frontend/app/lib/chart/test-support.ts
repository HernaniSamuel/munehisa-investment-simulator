import { vi } from "vitest";

// Test-only helpers - never imported by the module itself or re-exported
// from index.ts. jsdom implements neither ResizeObserver nor a non-zero
// getBoundingClientRect() by default, both of which FinancialChart.tsx
// relies on for auto-sizing and pointer-position math.
export class MockResizeObserver {
  static instances: MockResizeObserver[] = [];
  #callback: ResizeObserverCallback;

  constructor(callback: ResizeObserverCallback) {
    this.#callback = callback;
    MockResizeObserver.instances.push(this);
  }

  observe() {}
  unobserve() {}
  disconnect() {}

  trigger(rect: { width: number; height: number }) {
    this.#callback([{ contentRect: rect } as ResizeObserverEntry], this as unknown as ResizeObserver);
  }
}

export function installResizeObserverMock() {
  MockResizeObserver.instances = [];
  vi.stubGlobal("ResizeObserver", MockResizeObserver);
}

export function stubBoundingClientRect(rect: Partial<DOMRect> = {}) {
  return vi.spyOn(Element.prototype, "getBoundingClientRect").mockReturnValue({
    x: 0,
    y: 0,
    top: 0,
    left: 0,
    right: 300,
    bottom: 200,
    width: 300,
    height: 200,
    toJSON() {
      return {};
    },
    ...rect,
  });
}
