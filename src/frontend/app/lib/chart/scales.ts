import { max, min, scaleLinear } from "d3";
import type { Bar, ChartScale } from "./types";

// Bars sit at evenly spaced integer positions (0, 1, 2, ...) rather than a
// true calendar-time scale. This is what mainstream trading platforms do:
// it keeps daily bars from opening gaps on weekends/holidays, and it keeps
// zoom/pan/crosshair pixel math a simple, deterministic linear mapping.
// Axis ticks and the crosshair readout still show each bar's real `date`.
//
// `Math.max(barCount - 1, 1)` guards the single-bar case: a domain of
// [0, 0] would give d3-zoom a zero-width extent to scale against.
export function createXScale(barCount: number, width: number): ChartScale {
  return scaleLinear()
    .domain([0, Math.max(barCount - 1, 1)])
    .range([0, width]);
}

// Domain covers the full high/low range across all bars, padded 5% on each
// side so the highest/lowest wick isn't drawn flush against the panel edge.
// Falls back to a 1-unit pad when every bar has the same high/low (a flat
// or single-bar dataset), so the domain is never zero-width.
export function createPriceScale(bars: Bar[], height: number): ChartScale {
  const lo = min(bars, (bar) => bar.low) ?? 0;
  const hi = max(bars, (bar) => bar.high) ?? 1;
  const pad = hi - lo > 0 ? (hi - lo) * 0.05 : 1;
  return scaleLinear()
    .domain([lo - pad, hi + pad])
    .range([height, 0]);
}

// Domain covers 0 to the largest volume in the dataset - volume bars always
// grow up from the panel's bottom.
export function createVolumeScale(bars: Bar[], height: number): ChartScale {
  const top = max(bars, (bar) => bar.volume) ?? 1;
  return scaleLinear()
    .domain([0, top || 1])
    .range([height, 0]);
}
