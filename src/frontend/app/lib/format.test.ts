import { describe, expect, it } from "vitest";
import {
  abbreviateCurrency,
  formatCurrency,
  formatCurrencyExact,
  formatCurrencyPlain,
  formatMonthYear,
  formatPercent,
  isAbbreviatedCurrency,
  truncateName,
} from "./format";

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

  it("leaves a value just under 1 billion unabbreviated", () => {
    expect(normalizeSpaces(formatCurrency(999999999, "BRL"))).toBe("R$ 999.999.999,00");
    expect(normalizeSpaces(formatCurrency(999999999, "USD"))).toBe("$999,999,999.00");
  });

  it("abbreviates exactly 1 billion with a bi/B suffix", () => {
    expect(normalizeSpaces(formatCurrency(1_000_000_000, "BRL"))).toBe("R$ 1,00 bi");
    expect(normalizeSpaces(formatCurrency(1_000_000_000, "USD"))).toBe("$1.00B");
  });

  it("abbreviates a mid-range billion value, rounded to 2 decimals", () => {
    expect(normalizeSpaces(formatCurrency(1_234_567_890, "BRL"))).toBe("R$ 1,23 bi");
    expect(normalizeSpaces(formatCurrency(1_234_567_890, "USD"))).toBe("$1.23B");
  });

  it("stays bi/B-suffixed just under 1 trillion", () => {
    expect(normalizeSpaces(formatCurrency(999_000_000_000, "BRL"))).toBe("R$ 999,00 bi");
    expect(normalizeSpaces(formatCurrency(999_000_000_000, "USD"))).toBe("$999.00B");
  });

  it("abbreviates exactly 1 trillion with a tri/T suffix", () => {
    expect(normalizeSpaces(formatCurrency(1_000_000_000_000, "BRL"))).toBe("R$ 1,00 tri");
    expect(normalizeSpaces(formatCurrency(1_000_000_000_000, "USD"))).toBe("$1.00T");
  });

  it("abbreviates a mid-range trillion value, rounded to 2 decimals", () => {
    expect(normalizeSpaces(formatCurrency(2_500_000_000_000, "BRL"))).toBe("R$ 2,50 tri");
    expect(normalizeSpaces(formatCurrency(2_500_000_000_000, "USD"))).toBe("$2.50T");
  });

  it("stays tri/T-suffixed just under 1 quadrillion", () => {
    expect(normalizeSpaces(formatCurrency(999_000_000_000_000, "BRL"))).toBe("R$ 999,00 tri");
    expect(normalizeSpaces(formatCurrency(999_000_000_000_000, "USD"))).toBe("$999.00T");
  });

  it("falls back to scientific notation at exactly 1 quadrillion", () => {
    expect(normalizeSpaces(formatCurrency(1_000_000_000_000_000, "BRL"))).toBe("R$ 1,00E15");
    expect(normalizeSpaces(formatCurrency(1_000_000_000_000_000, "USD"))).toBe("$1.00E15");
  });

  it("renders a very large value in scientific notation", () => {
    expect(normalizeSpaces(formatCurrency(2.5e18, "USD"))).toBe("$2.50E18");
  });

  it("preserves the sign, without doubling it, for negative abbreviated values", () => {
    expect(normalizeSpaces(formatCurrency(-1_234_567_890, "USD"))).toBe("-$1.23B");
    expect(normalizeSpaces(formatCurrency(-2_500_000_000_000, "BRL"))).toBe("-R$ 2,50 tri");
    expect(normalizeSpaces(formatCurrency(-2.5e18, "USD"))).toBe("-$2.50E18");
  });
});

describe("isAbbreviatedCurrency", () => {
  it("is false just under the 1 billion threshold", () => {
    expect(isAbbreviatedCurrency(999999999)).toBe(false);
  });

  it("is true at exactly 1 billion", () => {
    expect(isAbbreviatedCurrency(1_000_000_000)).toBe(true);
  });

  it("compares by magnitude, so a large negative amount is also abbreviated", () => {
    expect(isAbbreviatedCurrency(-1_000_000_000)).toBe(true);
    expect(isAbbreviatedCurrency(-999999999)).toBe(false);
  });
});

describe("formatCurrencyExact", () => {
  it("always returns full precision, even for an amount formatCurrency would abbreviate", () => {
    expect(normalizeSpaces(formatCurrencyExact(1_234_567_890, "USD"))).toBe("$1,234,567,890.00");
    expect(normalizeSpaces(formatCurrencyExact(1_234_567_890, "BRL"))).toBe("R$ 1.234.567.890,00");
  });
});

describe("abbreviateCurrency / formatCurrencyPlain (generic core)", () => {
  it("derives the bi/tri suffix from the resolved locale, not a hardcoded BRL/USD table", () => {
    // de-DE is neither BRL nor USD, and isn't pt-*, so it gets letter suffixes.
    expect(normalizeSpaces(abbreviateCurrency(1_234_567_890, "de-DE", "EUR"))).toBe("1,23 €B");
    // pt-PT isn't BRL, but is pt-*, so it gets word suffixes like pt-BR does.
    expect(normalizeSpaces(abbreviateCurrency(1_234_567_890, "pt-PT", "EUR"))).toBe("1,23 € bi");
  });

  it("formatCurrencyPlain never abbreviates, regardless of magnitude", () => {
    expect(normalizeSpaces(formatCurrencyPlain(1_234_567_890, "en-US", "USD"))).toBe("$1,234,567,890.00");
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
