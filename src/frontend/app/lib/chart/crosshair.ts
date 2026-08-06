import type { ChartScale } from "./types";

// Inverts the pointer's pixel x-position back through the (possibly
// zoom/pan-transformed) x-scale to the nearest bar index, rounding to the
// closest whole bar and clamping to the dataset's bounds. Returns
// `undefined` only when there are no bars to find - hiding the readout when
// the pointer leaves the chart is the component's job, not this function's.
export function findNearestBarIndex(
  scale: ChartScale,
  barCount: number,
  pointerX: number
): number | undefined {
  if (barCount === 0) return undefined;
  const index = Math.round(scale.invert(pointerX));
  return Math.min(Math.max(index, 0), barCount - 1);
}
