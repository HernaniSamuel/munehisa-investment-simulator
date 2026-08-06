import type { IndicatorPlugin } from "../../registries/indicators";
import type { Bar } from "../../types";

export type EmaConfig = { period: number };

export const defaultEmaConfig: EmaConfig = { period: 20 };

function resolveConfig(config: unknown): EmaConfig {
  if (typeof config === "object" && config !== null && typeof (config as EmaConfig).period === "number") {
    return config as EmaConfig;
  }
  return defaultEmaConfig;
}

// Exponential moving average of `close`: seeded with a plain SMA over the
// first `period` bars, then smoothed forward with weight k = 2/(period+1).
// `undefined` until the seed bar (index `period - 1`).
export function computeEma(bars: Bar[], config: unknown): (number | undefined)[] {
  const { period } = resolveConfig(config);
  const result: (number | undefined)[] = new Array(bars.length).fill(undefined);
  if (bars.length < period) return result;

  const k = 2 / (period + 1);
  let seed = 0;
  for (let i = 0; i < period; i++) seed += bars[i].close;
  seed /= period;
  result[period - 1] = seed;

  let previous = seed;
  for (let i = period; i < bars.length; i++) {
    const value = bars[i].close * k + previous * (1 - k);
    result[i] = value;
    previous = value;
  }
  return result;
}

// Registered into the default indicator registry (registries/defaults.ts),
// not special-cased in the engine - see FinancialChart.tsx.
export const emaIndicator: IndicatorPlugin = {
  id: "ema",
  label: "EMA",
  defaultConfig: defaultEmaConfig,
  color: "#6E7A5E",
  compute: computeEma,
};
