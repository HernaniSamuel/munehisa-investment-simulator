import { act, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { FinancialChart } from "./FinancialChart";
import { createChartTypeRegistry } from "./registries/chart-types";
import { createDrawingToolRegistry } from "./registries/drawing-tools";
import { createIndicatorRegistry } from "./registries/indicators";
import { installResizeObserverMock, MockResizeObserver, stubBoundingClientRect } from "./test-support";
import { sumiTheme, zankyoTheme } from "./theme";
import type { Bar } from "./types";

function makeBars(count: number): Bar[] {
  return Array.from({ length: count }, (_, i) => ({
    date: new Date(2024, 0, i + 1),
    open: 100 + i,
    high: 105 + i,
    low: 95 + i,
    close: 102 + i,
    volume: 1000 + i * 10,
  }));
}

describe("FinancialChart", () => {
  beforeEach(() => {
    stubBoundingClientRect({ left: 0, top: 0, width: 300, height: 200 });
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  it("renders candlestick and line via the default registry when chartType is switched", () => {
    const bars = makeBars(5);
    const { rerender } = render(
      <FinancialChart bars={bars} width={400} height={300} chartType="candlestick" />
    );
    expect(screen.getByTestId("chart-type-candlestick")).toBeInTheDocument();

    rerender(<FinancialChart bars={bars} width={400} height={300} chartType="line" />);
    expect(screen.getByTestId("chart-type-line")).toBeInTheDocument();
  });

  it("registers and renders a synthetic fake chart type without touching the default registry", () => {
    const bars = makeBars(3);
    const registry = createChartTypeRegistry();
    registry.register({
      id: "fake-chart-type",
      label: "Fake",
      Component: () => <g data-testid="fake-chart-type-render" />,
    });

    render(
      <FinancialChart
        bars={bars}
        width={400}
        height={300}
        chartType="fake-chart-type"
        chartTypeRegistry={registry}
      />
    );

    expect(screen.getByTestId("fake-chart-type-render")).toBeInTheDocument();
  });

  it("registers and renders a synthetic fake indicator, drawing its overlay from its own compute output", () => {
    const bars = makeBars(3);
    const registry = createIndicatorRegistry();
    registry.register({
      id: "fake-indicator",
      label: "Fake",
      defaultConfig: {},
      color: "#123456",
      compute: () => [1, 2, 3],
    });

    render(
      <FinancialChart
        bars={bars}
        width={400}
        height={300}
        indicators={[{ id: "fake-indicator" }]}
        indicatorRegistry={registry}
      />
    );

    const path = screen.getByTestId("indicator-overlay-fake-indicator");
    expect(path).toHaveAttribute("stroke", "#123456");
    expect(path.getAttribute("d")).toMatch(/^M/);
  });

  it("registers a synthetic fake drawing tool and invokes it via the same activation mechanism", async () => {
    const bars = makeBars(3);
    const registry = createDrawingToolRegistry();
    registry.register({
      id: "fake-tool",
      label: "Fake tool",
      createInstance: (pointer) => pointer,
      Component: ({ instance }) => <g data-testid="fake-drawing-tool-render">{JSON.stringify(instance)}</g>,
    });

    render(
      <FinancialChart
        bars={bars}
        width={400}
        height={300}
        activeDrawingTool="fake-tool"
        drawingToolRegistry={registry}
      />
    );

    expect(screen.queryByTestId("fake-drawing-tool-render")).not.toBeInTheDocument();

    fireEvent.pointerDown(screen.getByTestId("chart-overlay"), { clientX: 50, clientY: 50 });

    expect(await screen.findByTestId("fake-drawing-tool-render")).toBeInTheDocument();
  });

  it("toggles the volume histogram panel via showVolume", () => {
    const bars = makeBars(5);
    const { rerender } = render(<FinancialChart bars={bars} width={400} height={300} showVolume />);
    expect(screen.getByTestId("chart-volume-panel")).toBeInTheDocument();

    rerender(<FinancialChart bars={bars} width={400} height={300} showVolume={false} />);
    expect(screen.queryByTestId("chart-volume-panel")).not.toBeInTheDocument();
  });

  it("renders visibly different colors for the Sumi and Zankyō theme presets", () => {
    const bars = makeBars(5);
    const { container: sumiContainer } = render(
      <FinancialChart bars={bars} width={400} height={300} theme={sumiTheme} />
    );
    const { container: zankyoContainer } = render(
      <FinancialChart bars={bars} width={400} height={300} theme={zankyoTheme} />
    );

    const sumiBg = sumiContainer.querySelector('[data-testid="chart-background"]');
    const zankyoBg = zankyoContainer.querySelector('[data-testid="chart-background"]');

    expect(sumiBg).toHaveAttribute("fill", sumiTheme.background);
    expect(zankyoBg).toHaveAttribute("fill", zankyoTheme.background);
    expect(sumiBg?.getAttribute("fill")).not.toBe(zankyoBg?.getAttribute("fill"));
  });

  it("shows a hover readout for the nearest bar and hides it when the pointer leaves", async () => {
    const bars = makeBars(5);
    render(<FinancialChart bars={bars} width={400} height={300} />);
    const overlay = screen.getByTestId("chart-overlay");

    expect(screen.queryByTestId("chart-crosshair")).not.toBeInTheDocument();

    fireEvent.pointerMove(overlay, { clientX: 2, clientY: 50 });
    expect(await screen.findByTestId("chart-crosshair")).toHaveTextContent(/O 100\.00/);

    fireEvent.pointerLeave(overlay);
    expect(screen.queryByTestId("chart-crosshair")).not.toBeInTheDocument();
  });

  it("zooms and pans without throwing across dataset sizes from 1 to several hundred bars", () => {
    for (const count of [1, 300]) {
      const bars = makeBars(count);
      const { unmount } = render(<FinancialChart bars={bars} width={400} height={300} />);
      const overlay = screen.getByTestId("chart-overlay");

      expect(() => {
        fireEvent.wheel(overlay, { deltaY: -100, clientX: 100, clientY: 100 });
        fireEvent.pointerDown(overlay, { clientX: 100, clientY: 100, pointerId: 1 });
        fireEvent.pointerMove(overlay, { clientX: 150, clientY: 100, pointerId: 1 });
        fireEvent.pointerUp(overlay, { clientX: 150, clientY: 100, pointerId: 1 });
      }).not.toThrow();

      unmount();
    }
  });

  it("redraws at the container's new size on resize, without throwing", () => {
    installResizeObserverMock();
    const bars = makeBars(5);
    const { container } = render(<FinancialChart bars={bars} />);

    // 600x400 is the component's fallback size when no width/height props
    // and no resize observation has fired yet.
    expect(container.querySelector("svg")).toHaveAttribute("width", "600");
    expect(container.querySelector("svg")).toHaveAttribute("height", "400");

    const [observer] = MockResizeObserver.instances;
    expect(() => {
      act(() => observer.trigger({ width: 800, height: 500 }));
    }).not.toThrow();

    expect(container.querySelector("svg")).toHaveAttribute("width", "800");
    expect(container.querySelector("svg")).toHaveAttribute("height", "500");
  });

  it("renders an explicit empty-state instead of an error or blank canvas when bars is empty", () => {
    render(<FinancialChart bars={[]} width={400} height={300} />);
    expect(screen.getByRole("status")).toHaveTextContent(/no data/i);
    expect(document.querySelector("svg")).not.toBeInTheDocument();
  });
});
