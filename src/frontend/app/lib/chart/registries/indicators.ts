import type { Bar } from "../types";
import { createRegistry, type Registry } from "./registry";

// An indicator plugin computes an overlay series from the bars array. The
// engine renders whatever `compute` returns using a single generic overlay
// renderer (see FinancialChart.tsx) - it never special-cases SMA/EMA/etc.
// `config` is plugin-defined and opaque to the registry; a concrete plugin
// (e.g. sma.ts) owns validating/defaulting its own shape.
export type IndicatorPlugin = {
  id: string;
  label: string;
  defaultConfig: unknown;
  // Default overlay line color. Theme-controlled colors are limited to the
  // core chart surfaces (background/grid/text/candle up-down/line) per the
  // module scope, so each indicator carries its own.
  color: string;
  // One value per bar, aligned by index; `undefined` for bars that fall
  // inside the indicator's warm-up period (e.g. before `period` closes
  // exist for an SMA).
  compute(bars: Bar[], config: unknown): (number | undefined)[];
};

export type IndicatorRegistry = Registry<IndicatorPlugin>;

export function createIndicatorRegistry(): IndicatorRegistry {
  return createRegistry<IndicatorPlugin>();
}
