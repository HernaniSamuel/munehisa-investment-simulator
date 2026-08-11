import { describe, expect, it } from "vitest";
import { formatCurrency, formatMonthYear, formatPercent, truncateName } from "./format";

// Intl.NumberFormat's currency separator is a non-breaking space character,
// but exactly which one (U+00A0 vs the narrower U+202F) depends on the
// ICU/CLDR data bundled with the Node version running the test - it has
// changed between Node releases. Comparing on visible content only avoids
// pinning the assertion to whichever invisible character happens to be
// current on the machine that wrote the test.
function normalizeSpaces(text: string): string {
  return text.replace(/[  ]/g, " ");
}

describe("formatCurrency", () => {
  it("formats BRL with pt-BR grouping/decimal conventions", () => {
    expect(normalizeSpaces(formatCurrency(722000, "BRL"))).toBe("R$ 722.000,00");
  });

  it("formats USD with en-US grouping/decimal conventions", () => {
    expect(normalizeSpaces(formatCurrency(722000, "USD"))).toBe("$722,000.00");
  });

  it("formats zero", () => {
    expect(normalizeSpaces(formatCurrency(0, "USD"))).toBe("$0.00");
  });
});

describe("formatMonthYear", () => {
  it("formats a December month/year without rolling to November under a local timezone", () => {
    expect(formatMonthYear("2019-12")).toBe("DEC 2019");
  });

  it("formats a January month/year without rolling to the previous December", () => {
    expect(formatMonthYear("2002-01")).toBe("JAN 2002");
  });

  it("uppercases the abbreviated month", () => {
    expect(formatMonthYear("2024-06")).toBe("JUN 2024");
  });
});

describe("formatPercent", () => {
  it("formats a positive fraction with a leading plus sign", () => {
    expect(formatPercent(0.05)).toBe("+5.00%");
  });

  it("formats a negative fraction with a minus sign and no double negative", () => {
    expect(formatPercent(-0.05)).toBe("−5.00%");
  });

  it("formats zero with a leading plus sign", () => {
    expect(formatPercent(0)).toBe("+0.00%");
  });

  it("rounds to two decimal places", () => {
    expect(formatPercent(0.11111)).toBe("+11.11%");
  });
});

describe("truncateName", () => {
  it("returns names under 20 characters unchanged", () => {
    expect(truncateName("Retirement plan")).toBe("Retirement plan");
  });

  it("returns a name of exactly 20 characters unchanged, with no ellipsis", () => {
    const name = "A".repeat(20);
    expect(truncateName(name)).toBe(name);
  });

  it("truncates a name over 20 characters to 20 characters plus an ellipsis", () => {
    expect(truncateName("A".repeat(21))).toBe(`${"A".repeat(20)}…`);
    expect(truncateName("A".repeat(80))).toBe(`${"A".repeat(20)}…`);
  });

  it("returns an empty string unchanged", () => {
    expect(truncateName("")).toBe("");
  });
});
