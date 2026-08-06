import { describe, expect, it } from "vitest";
import type { Position } from "./api";
import { computeDonutSegments, DONUT_PALETTE } from "./donut";

function makePosition(overrides: Partial<Position> = {}): Position {
  return {
    ticker: "AAPL",
    assetName: "Apple Inc.",
    quantity: 10,
    currentPrice: 100,
    wasTruncated: false,
    marketValue: 1000,
    weight: 1,
    costBasis: 900,
    gainAmount: 100,
    gainPercent: 0.11,
    ...overrides,
  };
}

describe("computeDonutSegments", () => {
  it("returns an empty list for zero positions", () => {
    expect(computeDonutSegments([])).toEqual([]);
  });

  it("returns one full-circle segment for a single position at 100% weight", () => {
    const [segment] = computeDonutSegments([makePosition({ weight: 1 })], 10);
    const circumference = 2 * Math.PI * 10;
    expect(segment.dashArray).toBe(`${circumference} 0`);
    expect(segment.dashOffset).toBeCloseTo(0);
  });

  it("assigns one segment per position, never grouped", () => {
    const positions = [
      makePosition({ ticker: "AAPL", weight: 0.5 }),
      makePosition({ ticker: "MSFT", weight: 0.5 }),
    ];
    const segments = computeDonutSegments(positions);
    expect(segments).toHaveLength(2);
    expect(segments.map((s) => s.ticker)).toEqual(["AAPL", "MSFT"]);
  });

  it("offsets each segment by the cumulative arc length of prior segments", () => {
    const positions = [
      makePosition({ ticker: "AAPL", weight: 0.25 }),
      makePosition({ ticker: "MSFT", weight: 0.25 }),
      makePosition({ ticker: "GOOG", weight: 0.5 }),
    ];
    const radius = 10;
    const circumference = 2 * Math.PI * radius;
    const segments = computeDonutSegments(positions, radius);

    expect(segments[0].dashOffset).toBeCloseTo(0);
    expect(segments[1].dashOffset).toBeCloseTo(-(0.25 * circumference));
    expect(segments[2].dashOffset).toBeCloseTo(-(0.5 * circumference));
  });

  it("cycles the fixed 5-color palette in order", () => {
    const positions = Array.from({ length: 7 }, (_, i) =>
      makePosition({ ticker: `T${i}`, weight: 1 / 7 })
    );
    const segments = computeDonutSegments(positions);
    expect(segments.map((s) => s.color)).toEqual([
      DONUT_PALETTE[0],
      DONUT_PALETTE[1],
      DONUT_PALETTE[2],
      DONUT_PALETTE[3],
      DONUT_PALETTE[4],
      DONUT_PALETTE[0],
      DONUT_PALETTE[1],
    ]);
  });

  it("carries through the fields needed by a slice's hover detail", () => {
    const [segment] = computeDonutSegments([
      makePosition({
        ticker: "AAPL",
        assetName: "Apple Inc.",
        quantity: 10,
        weight: 1,
        costBasis: 900,
        marketValue: 1000,
      }),
    ]);
    expect(segment).toMatchObject({
      ticker: "AAPL",
      assetName: "Apple Inc.",
      quantity: 10,
      weight: 1,
      costBasis: 900,
      marketValue: 1000,
    });
  });
});
