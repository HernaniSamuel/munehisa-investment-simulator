import { describe, expect, it } from "vitest";
import { sumiTheme, zankyoTheme } from "./theme";

describe("theme presets", () => {
  it("keeps the same rise color (vermilion) in both themes", () => {
    expect(sumiTheme.candleUp).toBe("#DD3A22");
    expect(zankyoTheme.candleUp).toBe("#DD3A22");
    expect(sumiTheme.candleUp).toBe(zankyoTheme.candleUp);
  });

  it("gives Sumi and Zankyō visibly different background, grid, text, fall, and line colors", () => {
    expect(sumiTheme.background).not.toBe(zankyoTheme.background);
    expect(sumiTheme.grid).not.toBe(zankyoTheme.grid);
    expect(sumiTheme.text).not.toBe(zankyoTheme.text);
    expect(sumiTheme.candleDown).not.toBe(zankyoTheme.candleDown);
    expect(sumiTheme.line).not.toBe(zankyoTheme.line);
  });
});
