import { line as d3Line } from "d3";
import type { ChartTypePlugin, ChartTypeRenderProps } from "../../registries/chart-types";
import type { Bar } from "../../types";

function LineSeries({ bars, xScale, yScale, theme }: ChartTypeRenderProps) {
  const path = d3Line<Bar>()
    .x((_, index) => xScale(index))
    .y((bar) => yScale(bar.close))(bars);

  return (
    <g data-testid="chart-type-line">
      <path d={path ?? undefined} fill="none" stroke={theme.line} strokeWidth={2} />
    </g>
  );
}

// Registered into the default chart-type registry (registries/defaults.ts),
// not special-cased in the engine - see FinancialChart.tsx.
export const lineChartType: ChartTypePlugin = {
  id: "line",
  label: "Line",
  Component: LineSeries,
};
