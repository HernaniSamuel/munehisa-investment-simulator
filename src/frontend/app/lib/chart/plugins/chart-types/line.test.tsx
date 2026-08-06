import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { createPriceScale, createXScale } from "../../scales";
import { sumiTheme } from "../../theme";
import type { Bar } from "../../types";
import { lineChartType } from "./line";

function makeBar(overrides: Partial<Bar> = {}): Bar {
  return { date: new Date("2024-01-01"), open: 100, high: 110, low: 90, close: 105, volume: 1000, ...overrides };
}

describe("lineChartType", () => {
  it("is registered under id 'line'", () => {
    expect(lineChartType.id).toBe("line");
  });

  it("draws a single path through the bars' close prices", () => {
    const bars = [makeBar({ close: 100 }), makeBar({ close: 110 }), makeBar({ close: 105 })];
    const xScale = createXScale(bars.length, 300);
    const yScale = createPriceScale(bars, 200);
    const { container } = render(
      <svg>
        <lineChartType.Component bars={bars} xScale={xScale} yScale={yScale} theme={sumiTheme} />
      </svg>
    );
    const path = container.querySelector("path");
    expect(path).not.toBeNull();
    expect(path?.getAttribute("d")).toMatch(/^M/);
    expect(path).toHaveAttribute("stroke", sumiTheme.line);
  });
});
