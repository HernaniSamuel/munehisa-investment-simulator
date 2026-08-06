import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { createPriceScale, createXScale } from "../../scales";
import { sumiTheme } from "../../theme";
import type { Bar } from "../../types";
import { candlestickChartType } from "./candlestick";

function makeBar(overrides: Partial<Bar> = {}): Bar {
  return { date: new Date("2024-01-01"), open: 100, high: 110, low: 90, close: 105, volume: 1000, ...overrides };
}

describe("candlestickChartType", () => {
  it("is registered under id 'candlestick'", () => {
    expect(candlestickChartType.id).toBe("candlestick");
  });

  it("renders one candle per bar", () => {
    const bars = [makeBar(), makeBar({ close: 95 }), makeBar({ close: 108 })];
    const xScale = createXScale(bars.length, 300);
    const yScale = createPriceScale(bars, 200);
    render(
      <svg>
        <candlestickChartType.Component bars={bars} xScale={xScale} yScale={yScale} theme={sumiTheme} />
      </svg>
    );
    expect(screen.getAllByTestId("candle")).toHaveLength(3);
  });

  it("colors a rising bar with the theme's up color and a falling bar with its down color", () => {
    const bars = [makeBar({ open: 100, close: 110 }), makeBar({ open: 100, close: 90 })];
    const xScale = createXScale(bars.length, 300);
    const yScale = createPriceScale(bars, 200);
    render(
      <svg>
        <candlestickChartType.Component bars={bars} xScale={xScale} yScale={yScale} theme={sumiTheme} />
      </svg>
    );
    const candles = screen.getAllByTestId("candle");
    expect(candles[0].querySelector("rect")).toHaveAttribute("fill", sumiTheme.candleUp);
    expect(candles[1].querySelector("rect")).toHaveAttribute("fill", sumiTheme.candleDown);
  });
});
