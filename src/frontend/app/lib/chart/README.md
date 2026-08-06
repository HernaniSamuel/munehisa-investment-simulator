# `lib/chart` — financial charting engine

A standalone, themeable D3-based charting engine for OHLCV price data. It knows nothing about
Munehisa's API shapes, routes, or screens — it renders a generic bars array and is extended
entirely through three plugin registries. Nothing outside this directory is imported (besides
`react` and `d3`), so it can be lifted into another project unchanged.

Consuming it from a real screen (e.g. Munehisa's asset search/trade view) is out of scope here
— see issue #91.

## Quick start

```tsx
import { FinancialChart } from "~/lib/chart";

<FinancialChart
  bars={bars} // { date: Date; open: number; high: number; low: number; close: number; volume: number }[]
  chartType="candlestick" // or "line" — omit to use whichever plugin is first in the registry
  indicators={[{ id: "sma", config: { period: 20 } }, { id: "ema" }]}
  showVolume
/>;
```

Every registry (`chartTypeRegistry`, `indicatorRegistry`, `drawingToolRegistry`) and the
`theme` are props with sensible defaults — pass your own to extend or override without
touching this module.

## The `Bar` type

```ts
type Bar = { date: Date; open: number; high: number; low: number; close: number; volume: number };
```

Bars are positioned on the time axis by array index, not literal elapsed time — this is what
mainstream trading platforms do so daily data doesn't open gaps on weekends/holidays. Axis
ticks and the crosshair readout still show each bar's real `date`.

## Theming

```ts
type ChartTheme = {
  background: string;
  grid: string;
  text: string;
  candleUp: string; // "rise" color
  candleDown: string; // "fall" color
  line: string; // default line-chart-type stroke
};
```

Two presets ship: `sumiTheme` (light) and `zankyoTheme` (dark), matching `DESIGN.md`. Indicator
overlay colors are **not** part of the theme — each indicator plugin owns its own default line
color (see below), since the theme prop is scoped to the chart's structural surfaces.

## Extending the engine

Each of the three registries below is a `{ register(plugin), get(id), list() }` instance
(`createXRegistry()` builds an empty one; `default*Registry`, exported from this module,
already has this issue's concrete plugins registered into it). Registering a plugin never
requires editing `FinancialChart.tsx`, `scales.ts`, `crosshair.ts`, or `theme.ts` — those files
never reference a plugin by name.

### 1. Chart-type plugins

Draws the bars array onto the price panel (e.g. candlestick, line).

```ts
type ChartTypeRenderProps = { bars: Bar[]; xScale: ChartScale; yScale: ChartScale; theme: ChartTheme };
type ChartTypePlugin = { id: string; label: string; Component: FC<ChartTypeRenderProps> };
```

```tsx
import { createChartTypeRegistry, defaultChartTypeRegistry } from "~/lib/chart";

const myChartType: ChartTypePlugin = {
  id: "mountain",
  label: "Mountain",
  Component: ({ bars, xScale, yScale, theme }) => (/* draw an area path */),
};

defaultChartTypeRegistry.register(myChartType);
// or build an isolated registry: const registry = createChartTypeRegistry(); registry.register(myChartType);
```

Pass `chartType="mountain"` (and, if using an isolated registry, `chartTypeRegistry={registry}`)
to `<FinancialChart>`.

### 2. Indicator plugins

Computes an overlay series from the bars array; the engine draws whatever `compute` returns
with one generic line renderer — it never special-cases a specific indicator.

```ts
type IndicatorPlugin = {
  id: string;
  label: string;
  defaultConfig: unknown;
  color: string; // default overlay line color
  compute(bars: Bar[], config: unknown): (number | undefined)[]; // one value per bar, undefined during warm-up
};
```

```ts
import { defaultIndicatorRegistry } from "~/lib/chart";

const rsi: IndicatorPlugin = {
  id: "rsi",
  label: "RSI",
  defaultConfig: { period: 14 },
  color: "#9B7BE0",
  compute: (bars, config) => /* ... */,
};

defaultIndicatorRegistry.register(rsi);
```

Pass `indicators={[{ id: "rsi", config: { period: 14 } }]}` to `<FinancialChart>`.

### 3. Drawing-tool plugins

Turns a pointer interaction (bar index + price, in data space so it survives zoom/pan/resize)
into a persisted, renderable instance. No concrete tool ships in this module — only the
interface and an empty default registry, so one can be added later without touching the engine.

```ts
type DrawingToolPointer = { index: number; price: number };
type DrawingToolRenderProps = { instance: unknown; xScale: ChartScale; yScale: ChartScale; theme: ChartTheme };
type DrawingToolPlugin = {
  id: string;
  label: string;
  createInstance(pointer: DrawingToolPointer): unknown;
  Component: FC<DrawingToolRenderProps>;
};
```

```ts
import { defaultDrawingToolRegistry } from "~/lib/chart";

const horizontalLine: DrawingToolPlugin = {
  id: "horizontal-line",
  label: "Horizontal line",
  createInstance: (pointer) => ({ price: pointer.price }),
  Component: ({ instance, xScale, yScale, theme }) => (/* draw a horizontal line at instance.price */),
};

defaultDrawingToolRegistry.register(horizontalLine);
```

Set `activeDrawingTool="horizontal-line"` on `<FinancialChart>`; a pointerdown on the chart
surface then calls `createInstance` and renders the result via `Component`.
