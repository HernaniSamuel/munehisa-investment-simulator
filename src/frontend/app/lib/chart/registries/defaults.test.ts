import { describe, expect, it } from "vitest";
import { defaultChartTypeRegistry, defaultDrawingToolRegistry, defaultIndicatorRegistry } from "./defaults";

describe("defaultChartTypeRegistry", () => {
  it("includes candlestick and line via the public registration mechanism, not hardcoded", () => {
    expect(
      defaultChartTypeRegistry
        .list()
        .map((plugin) => plugin.id)
        .sort()
    ).toEqual(["candlestick", "line"]);
  });
});

describe("defaultIndicatorRegistry", () => {
  it("includes sma and ema via the public registration mechanism, not hardcoded", () => {
    expect(
      defaultIndicatorRegistry
        .list()
        .map((plugin) => plugin.id)
        .sort()
    ).toEqual(["ema", "sma"]);
  });
});

describe("defaultDrawingToolRegistry", () => {
  it("ships empty - no concrete drawing tool in this issue", () => {
    expect(defaultDrawingToolRegistry.list()).toEqual([]);
  });
});
