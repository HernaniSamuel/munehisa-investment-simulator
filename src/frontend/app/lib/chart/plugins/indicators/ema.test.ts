import { describe, expect, it } from "vitest";
import type { Bar } from "../../types";
import { computeEma, emaIndicator } from "./ema";

function makeBar(close: number): Bar {
  return { date: new Date(2024, 0, close), open: close, high: close, low: close, close, volume: 100 };
}

describe("computeEma", () => {
  it("computes exact values for a small known dataset", () => {
    const bars = [1, 2, 3, 4, 5].map(makeBar);
    const result = computeEma(bars, { period: 3 });
    expect(result[0]).toBeUndefined();
    expect(result[1]).toBeUndefined();
    // seed = SMA(1,2,3) = 2; k = 2/(3+1) = 0.5
    expect(result[2]).toBeCloseTo(2);
    // 4*0.5 + 2*0.5 = 3
    expect(result[3]).toBeCloseTo(3);
    // 5*0.5 + 3*0.5 = 4
    expect(result[4]).toBeCloseTo(4);
  });

  it("returns all-undefined when there aren't enough bars for a seed", () => {
    const bars = [1, 2].map(makeBar);
    expect(computeEma(bars, { period: 3 })).toEqual([undefined, undefined]);
  });
});

describe("emaIndicator", () => {
  it("is registered under id 'ema'", () => {
    expect(emaIndicator.id).toBe("ema");
  });
});
