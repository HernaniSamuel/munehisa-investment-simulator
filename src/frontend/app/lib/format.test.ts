import { describe, expect, it } from "vitest";
import { formatCurrency, formatMonthYear } from "./format";

describe("formatCurrency", () => {
  it("formats BRL with pt-BR grouping/decimal conventions", () => {
    expect(formatCurrency(722000, "BRL")).toBe("R$ 722.000,00");
  });

  it("formats USD with en-US grouping/decimal conventions", () => {
    expect(formatCurrency(722000, "USD")).toBe("$722,000.00");
  });

  it("formats zero", () => {
    expect(formatCurrency(0, "USD")).toBe("$0.00");
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
