import { candlestickChartType } from "../plugins/chart-types/candlestick";
import { lineChartType } from "../plugins/chart-types/line";
import { emaIndicator } from "../plugins/indicators/ema";
import { smaIndicator } from "../plugins/indicators/sma";
import { createChartTypeRegistry, type ChartTypeRegistry } from "./chart-types";
import { createDrawingToolRegistry, type DrawingToolRegistry } from "./drawing-tools";
import { createIndicatorRegistry, type IndicatorRegistry } from "./indicators";

// Pre-populated registries FinancialChart.tsx falls back to when a consumer
// doesn't pass its own. Registering candlestick/line/SMA/EMA here - rather
// than inside the engine - is what proves they're ordinary plugins: a
// consumer could build an equivalent registry themselves by calling
// `.register()` the same way (see FinancialChart.test.tsx's synthetic
// fake-plugin tests).
export const defaultChartTypeRegistry: ChartTypeRegistry = createChartTypeRegistry();
defaultChartTypeRegistry.register(candlestickChartType);
defaultChartTypeRegistry.register(lineChartType);

export const defaultIndicatorRegistry: IndicatorRegistry = createIndicatorRegistry();
defaultIndicatorRegistry.register(smaIndicator);
defaultIndicatorRegistry.register(emaIndicator);

// No concrete drawing tool ships in this module (see the registry's own
// docs in drawing-tools.ts) - this registry starts, and stays, empty.
export const defaultDrawingToolRegistry: DrawingToolRegistry = createDrawingToolRegistry();
