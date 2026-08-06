import { describe, expect, it } from "vitest";
import { findNearestBarIndex } from "./crosshair";
import { createXScale } from "./scales";

describe("findNearestBarIndex", () => {
  it("returns the exact index when the pointer sits on a bar's position", () => {
    const scale = createXScale(5, 400);
    expect(findNearestBarIndex(scale, 5, 200)).toBe(2);
  });

  it("rounds to the closest bar between two positions", () => {
    const scale = createXScale(5, 400);
    expect(findNearestBarIndex(scale, 5, 220)).toBe(2);
    expect(findNearestBarIndex(scale, 5, 260)).toBe(3);
  });

  it("clamps to the first/last bar when the pointer is outside the chart area", () => {
    const scale = createXScale(5, 400);
    expect(findNearestBarIndex(scale, 5, -50)).toBe(0);
    expect(findNearestBarIndex(scale, 5, 1000)).toBe(4);
  });

  it("returns undefined for an empty dataset", () => {
    const scale = createXScale(0, 400);
    expect(findNearestBarIndex(scale, 0, 100)).toBeUndefined();
  });
});
