import type { IndicatorPlugin } from "../../registries/indicators";
import type { Bar } from "../../types";

export type SmaConfig = { period: number };

export const defaultSmaConfig: SmaConfig = { period: 20 };

function resolveConfig(config: unknown): SmaConfig {
  if (typeof config === "object" && config !== null && typeof (config as SmaConfig).period === "number") {
    return config as SmaConfig;
  }
  return defaultSmaConfig;
}

// Simple moving average of `close` over the trailing `period` bars.
// `undefined` for the warm-up bars before `period` closes are available.
export function computeSma(bars: Bar[], config: unknown): (number | undefined)[] {
  const { period } = resolveConfig(config);
  return bars.map((_, index) => {
    if (index < period - 1) return undefined;
    let sum = 0;
    for (let i = index - period + 1; i <= index; i++) sum += bars[i].close;
    return sum / period;
  });
}

// Registered into the default indicator registry (registries/defaults.ts),
// not special-cased in the engine - see FinancialChart.tsx.
export const smaIndicator: IndicatorPlugin = {
  id: "sma",
  label: "SMA",
  defaultConfig: defaultSmaConfig,
  color: "#B08A3E",
  compute: computeSma,
};
