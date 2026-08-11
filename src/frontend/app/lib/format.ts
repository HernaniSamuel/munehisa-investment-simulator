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

// fraction is a plain ratio (0.05 = 5%), matching the backend's weight/
// gainPercent convention - never pre-multiplied by 100.
export function formatPercent(fraction: number): string {
  const sign = fraction >= 0 ? "+" : "−";
  return `${sign}${Math.abs(fraction * 100).toFixed(2)}%`;
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

const NAME_DISPLAY_LIMIT = 20;

// For read-only displays only (cards, headers, dialogs) - never apply this to
// an editable field's value, which must keep showing/submitting the full name.
export function truncateName(name: string): string {
  return name.length > NAME_DISPLAY_LIMIT ? `${name.slice(0, NAME_DISPLAY_LIMIT)}…` : name;
}
