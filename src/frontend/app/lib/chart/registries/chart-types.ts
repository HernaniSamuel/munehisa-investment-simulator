import type { FC } from "react";
import type { ChartTheme } from "../theme";
import type { Bar, ChartScale } from "../types";
import { createRegistry, type Registry } from "./registry";

export type ChartTypeRenderProps = {
  bars: Bar[];
  xScale: ChartScale;
  yScale: ChartScale;
  theme: ChartTheme;
};

// A chart-type plugin knows how to draw a bars array onto the price panel
// (e.g. candlestick, line). It receives only the generic render props above
// - never anything app-specific - and is free to render whatever SVG it
// needs from them.
export type ChartTypePlugin = {
  id: string;
  label: string;
  Component: FC<ChartTypeRenderProps>;
};

export type ChartTypeRegistry = Registry<ChartTypePlugin>;

export function createChartTypeRegistry(): ChartTypeRegistry {
  return createRegistry<ChartTypePlugin>();
}
