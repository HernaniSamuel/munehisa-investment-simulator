const CURRENCY_LOCALES: Record<"BRL" | "USD", string> = {
  BRL: "pt-BR",
  USD: "en-US",
};

export function formatCurrency(amount: number, baseCurrency: "BRL" | "USD"): string {
  return new Intl.NumberFormat(CURRENCY_LOCALES[baseCurrency], {
    style: "currency",
    currency: baseCurrency,
  }).format(amount);
}

// yearMonth is a "YYYY-MM" string (the JSON shape of the backend's
// YearMonth). Parsed as UTC so a local timezone west of UTC can't shift the
// 1st of the month back into the previous month.
export function formatMonthYear(yearMonth: string): string {
  const [year, month] = yearMonth.split("-").map(Number);
  const date = new Date(Date.UTC(year, month - 1, 1));
  return new Intl.DateTimeFormat("en-US", {
    month: "short",
    year: "numeric",
    timeZone: "UTC",
  })
    .format(date)
    .toUpperCase();
}
