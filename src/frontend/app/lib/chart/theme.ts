// Colors a consumer can inject to skin the chart. Kept to exactly the
// surfaces the module scope calls for - candle up/down, background, grid,
// text, and the default line-chart-type stroke - so the engine never needs
// to know about a specific plugin's colors (those are plugin-owned, see
// plugins/indicators/*.ts).
export type ChartTheme = {
  background: string;
  grid: string;
  text: string;
  candleUp: string;
  candleDown: string;
  line: string;
};

// Matches DESIGN.md's Sumi (light) palette. Vermilion (#DD3A22) means
// rise/gain and black (#1B1611) means fall/loss - this mapping is called out
// in DESIGN.md as non-negotiable and is reused as-is, never swapped for
// conventional red/green.
export const sumiTheme: ChartTheme = {
  background: "#EFE8DA",
  grid: "rgba(122, 112, 95, 0.18)",
  text: "#1B1611",
  candleUp: "#DD3A22",
  candleDown: "#1B1611",
  line: "#1B1611",
};

// Matches DESIGN.md's Zankyō (dark) palette. Vermilion stays the same as
// Sumi (DESIGN.md: "vermilion stays the same in both, rise/action"); the
// fall color swaps to the dark theme's "2nd accent" (phosphor cyan), not to
// black-in-the-dark.
export const zankyoTheme: ChartTheme = {
  background: "#14100C",
  grid: "rgba(142, 132, 116, 0.18)",
  text: "#EDE4D3",
  candleUp: "#DD3A22",
  candleDown: "#57E3D0",
  line: "#EDE4D3",
};
