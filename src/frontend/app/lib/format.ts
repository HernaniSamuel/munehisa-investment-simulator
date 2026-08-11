const CURRENCY_LOCALES: Record<"BRL" | "USD", string> = {
  BRL: "pt-BR",
  USD: "en-US",
};

const BILLION = 1_000_000_000;
const TRILLION = 1_000_000_000_000;

// Whether amount is large enough that formatCurrency/formatAssetCurrency
// abbreviate it (and callers should show the exact value in a Tooltip).
export function isAbbreviatedCurrency(amount: number): boolean {
  return Math.abs(amount) >= BILLION;
}

// Always the full, unabbreviated currency string - what a Tooltip shows for
// an abbreviated value. locale is passed straight through to
// Intl.NumberFormat, so undefined means "browser default", matching
// trade.tsx's formatAssetCurrency.
export function formatCurrencyPlain(amount: number, locale: string | undefined, currency: string): string {
  return new Intl.NumberFormat(locale, { style: "currency", currency }).format(amount);
}

// bi/tri are word suffixes (pt-*) and need a separating space; B/T are
// letter suffixes (everything else, per the issue's en-US example) and
// don't - baking the space into the suffix itself keeps abbreviateCurrency's
// concatenation a single rule instead of a locale-conditional one.
function abbreviationSuffixes(locale: string | undefined): { billion: string; trillion: string } {
  const resolved = new Intl.NumberFormat(locale).resolvedOptions().locale.toLowerCase();
  return resolved.startsWith("pt") ? { billion: " bi", trillion: " tri" } : { billion: "B", trillion: "T" };
}

const BUCKETS: { divisor: number; suffixKey: "billion" | "trillion" }[] = [
  { divisor: BILLION, suffixKey: "billion" },
  { divisor: TRILLION, suffixKey: "trillion" },
];

function formatScaled(amount: number, divisor: number, locale: string | undefined, currency: string): string {
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency,
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount / divisor);
}

// Generic core shared by formatCurrency (BRL|USD) and trade.tsx's
// formatAssetCurrency (arbitrary asset currency, browser-default locale) -
// see the "formatAssetCurrency's suffix language" judgment call for why the
// pt-prefix check above is locale-string-driven rather than a BRL/USD table.
export function abbreviateCurrency(amount: number, locale: string | undefined, currency: string): string {
  if (Math.abs(amount) < BILLION) return formatCurrencyPlain(amount, locale, currency);

  for (const { divisor, suffixKey } of BUCKETS) {
    // Round first, then check whether the *rounded* number still reads as
    // under 1000 in this bucket's unit. A raw magnitude like 999.9995
    // billion is < 1 trillion, but rounds to "1,000.00 bi" - which is really
    // 1 trillion and belongs in the next bucket up (1000 bi = 1 tri). Basing
    // the bucket choice on the pre-rounding magnitude alone (as an earlier
    // version of this function did) let that mismatch slip through; this
    // escalates to the next bucket whenever rounding crosses the boundary.
    const rounded = Math.round((Math.abs(amount) / divisor) * 100) / 100;
    if (rounded < 1000) {
      return formatScaled(amount, divisor, locale, currency) + abbreviationSuffixes(locale)[suffixKey];
    }
  }

  // Rounds to >= 1000 even in the largest (trillion) bucket, i.e. >= 1
  // quadrillion once rounded - the scientific-notation fallback.
  return new Intl.NumberFormat(locale, {
    style: "currency",
    currency,
    notation: "scientific",
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  }).format(amount);
}

export function formatCurrency(amount: number, baseCurrency: "BRL" | "USD"): string {
  return abbreviateCurrency(amount, CURRENCY_LOCALES[baseCurrency], baseCurrency);
}

// The exact, unabbreviated value for baseCurrency - what a Tooltip shows
// next to formatCurrency's (possibly abbreviated) display string.
export function formatCurrencyExact(amount: number, baseCurrency: "BRL" | "USD"): string {
  return formatCurrencyPlain(amount, CURRENCY_LOCALES[baseCurrency], baseCurrency);
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
