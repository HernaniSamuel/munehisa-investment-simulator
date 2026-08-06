import type { ChartTypePlugin, ChartTypeRenderProps } from "../../registries/chart-types";

const BODY_WIDTH_RATIO = 0.6;

function Candlesticks({ bars, xScale, yScale, theme }: ChartTypeRenderProps) {
  // Bars sit at evenly spaced integer positions (see scales.ts), so the
  // column width is constant - one step of the x-scale.
  const step = xScale(1) - xScale(0);
  const bodyWidth = Math.max(Math.abs(step) * BODY_WIDTH_RATIO, 1);

  return (
    <g data-testid="chart-type-candlestick">
      {bars.map((bar, index) => {
        const x = xScale(index);
        const color = bar.close >= bar.open ? theme.candleUp : theme.candleDown;
        const bodyTop = yScale(Math.max(bar.open, bar.close));
        const bodyBottom = yScale(Math.min(bar.open, bar.close));
        return (
          <g key={index} data-testid="candle">
            <line x1={x} x2={x} y1={yScale(bar.high)} y2={yScale(bar.low)} stroke={color} strokeWidth={1} />
            <rect
              x={x - bodyWidth / 2}
              y={bodyTop}
              width={bodyWidth}
              height={Math.max(bodyBottom - bodyTop, 1)}
              fill={color}
            />
          </g>
        );
      })}
    </g>
  );
}

// Registered into the default chart-type registry (registries/defaults.ts),
// not special-cased in the engine - see FinancialChart.tsx.
export const candlestickChartType: ChartTypePlugin = {
  id: "candlestick",
  label: "Candlestick",
  Component: Candlesticks,
};
