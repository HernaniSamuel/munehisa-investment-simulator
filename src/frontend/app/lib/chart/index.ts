export { FinancialChart, type FinancialChartProps } from "./FinancialChart";
export { sumiTheme, zankyoTheme, type ChartTheme } from "./theme";
export type { Bar } from "./types";

export {
  createChartTypeRegistry,
  type ChartTypePlugin,
  type ChartTypeRegistry,
  type ChartTypeRenderProps,
} from "./registries/chart-types";
export {
  createIndicatorRegistry,
  type IndicatorPlugin,
  type IndicatorRegistry,
} from "./registries/indicators";
export {
  createDrawingToolRegistry,
  type DrawingToolPlugin,
  type DrawingToolPointer,
  type DrawingToolRegistry,
  type DrawingToolRenderProps,
} from "./registries/drawing-tools";
export {
  defaultChartTypeRegistry,
  defaultDrawingToolRegistry,
  defaultIndicatorRegistry,
} from "./registries/defaults";

export { candlestickChartType } from "./plugins/chart-types/candlestick";
export { lineChartType } from "./plugins/chart-types/line";
export { computeSma, smaIndicator, type SmaConfig } from "./plugins/indicators/sma";
export { computeEma, emaIndicator, type EmaConfig } from "./plugins/indicators/ema";
