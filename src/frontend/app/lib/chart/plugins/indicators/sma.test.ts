import { describe, expect, it } from "vitest";
import type { Bar } from "../../types";
import { computeSma, smaIndicator } from "./sma";

function makeBar(close: number): Bar {
  return { date: new Date(2024, 0, close), open: close, high: close, low: close, close, volume: 100 };
}

describe("computeSma", () => {
  it("computes exact values for a small known dataset", () => {
    const bars = [1, 2, 3, 4, 5].map(makeBar);
    expect(computeSma(bars, { period: 3 })).toEqual([undefined, undefined, 2, 3, 4]);
  });

  it("falls back to the default 20-bar period when config is missing", () => {
    const bars = Array.from({ length: 25 }, (_, i) => makeBar(i + 1));
    const result = computeSma(bars, undefined);
    expect(result[18]).toBeUndefined();
    expect(result[19]).toBeCloseTo(10.5);
  });
});

describe("smaIndicator", () => {
  it("is registered under id 'sma'", () => {
    expect(smaIndicator.id).toBe("sma");
  });
});
