import { describe, expect, it } from "vitest";
import { createPriceScale, createVolumeScale, createXScale } from "./scales";
import type { Bar } from "./types";

function makeBar(overrides: Partial<Bar> = {}): Bar {
  return {
    date: new Date("2024-01-01"),
    open: 100,
    high: 105,
    low: 95,
    close: 102,
    volume: 1000,
    ...overrides,
  };
}

describe("createXScale", () => {
  it("spaces bars evenly by index across the full width", () => {
    const scale = createXScale(5, 400);
    expect(scale(0)).toBe(0);
    expect(scale(4)).toBe(400);
    expect(scale(2)).toBe(200);
  });

  it("does not collapse to a zero-width domain for a single bar", () => {
    const scale = createXScale(1, 400);
    expect(scale.domain()).toEqual([0, 1]);
    expect(Number.isFinite(scale(0))).toBe(true);
  });
});

describe("createPriceScale", () => {
  it("maps the highest high near the top (y=0) and lowest low near the bottom", () => {
    const bars = [makeBar({ high: 110, low: 90 }), makeBar({ high: 120, low: 80 })];
    const scale = createPriceScale(bars, 300);
    expect(scale(120)).toBeLessThan(scale(80));
    expect(scale(120)).toBeGreaterThanOrEqual(0);
    expect(scale(80)).toBeLessThanOrEqual(300);
  });

  it("pads a flat single-value dataset so the domain isn't zero-width", () => {
    const bars = [makeBar({ high: 100, low: 100 })];
    const scale = createPriceScale(bars, 300);
    const [lo, hi] = scale.domain();
    expect(hi).toBeGreaterThan(lo);
  });
});

describe("createVolumeScale", () => {
  it("maps 0 volume to the panel bottom and the max volume to the top", () => {
    const bars = [makeBar({ volume: 500 }), makeBar({ volume: 1500 })];
    const scale = createVolumeScale(bars, 100);
    expect(scale(0)).toBe(100);
    expect(scale(1500)).toBe(0);
  });

  it("does not divide by zero when every bar has zero volume", () => {
    const bars = [makeBar({ volume: 0 })];
    const scale = createVolumeScale(bars, 100);
    expect(Number.isFinite(scale(0))).toBe(true);
  });
});
