import type { ScaleLinear } from "d3";

// A single price/volume bar for one period. No Yahoo/Munehisa-specific field
// names - any provider's OHLCV data maps onto this shape.
export type Bar = {
  date: Date;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
};

// Bars are positioned by array index, not literal elapsed time (see
// scales.ts) - both axes are therefore plain linear scales.
export type ChartScale = ScaleLinear<number, number>;
