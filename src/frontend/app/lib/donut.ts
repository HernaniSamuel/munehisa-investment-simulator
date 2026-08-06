import type { Position } from "./api";

// Fixed order per DESIGN.md's donut/category palette - cycles by index, never
// reassigned per-ticker, so the same position can shift color across renders
// if the position order changes (matches the "cycling" wording in the issue).
export const DONUT_PALETTE = ["#DD3A22", "#1B1611", "#B08A3E", "#6E7A5E", "#A79A86"] as const;

export type DonutSegment = {
  ticker: string;
  assetName: string;
  quantity: number;
  weight: number;
  costBasis: number;
  marketValue: number;
  color: string;
  // SVG stroke-dasharray/-dashoffset pair for a circle of the given radius,
  // arranged so consecutive segments' visible arcs are contiguous.
  dashArray: string;
  dashOffset: number;
};

// One segment per position - never grouped by category, since positions
// carry no such field. Segments are laid out in the given order, each
// starting exactly where the previous one's arc ended.
export function computeDonutSegments(positions: Position[], radius = 60): DonutSegment[] {
  const circumference = 2 * Math.PI * radius;
  let cumulative = 0;

  return positions.map((position, index) => {
    const arcLength = position.weight * circumference;
    const segment: DonutSegment = {
      ticker: position.ticker,
      assetName: position.assetName,
      quantity: position.quantity,
      weight: position.weight,
      costBasis: position.costBasis,
      marketValue: position.marketValue,
      color: DONUT_PALETTE[index % DONUT_PALETTE.length],
      dashArray: `${arcLength} ${circumference - arcLength}`,
      dashOffset: -cumulative,
    };
    cumulative += arcLength;
    return segment;
  });
}
