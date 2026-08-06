import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { Link, useParams, useSearchParams } from "react-router";
import type { Route } from "./+types/trade";
import { ProtectedRoute } from "~/components/ProtectedRoute";
import { Banner, Button, TextField, buttonBaseClasses, buttonVariantClasses } from "~/components/ui";
import { ToastStack, type ToastItem } from "~/components/Toast";
import { useAuth } from "~/lib/auth-context";
import {
  ApiError,
  simulationApi,
  type AssetDetail,
  type PositionsResponse,
  type Simulation,
  type TickerSearchResult,
  type TradeResponse,
} from "~/lib/api";
import { formatCurrency, formatPercent } from "~/lib/format";
import { FinancialChart, type Bar } from "~/lib/chart";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Trade — Munehisa" }];
}

export default function Trade() {
  return (
    <ProtectedRoute>
      <TradeScreen />
    </ProtectedRoute>
  );
}

// How long to wait after the last keystroke before firing a search request.
const SEARCH_DEBOUNCE_MS = 1000;
const MIN_QUERY_LENGTH = 2;

function genToastId(): string {
  return `${Date.now()}-${Math.random()}`;
}

// AssetMonthData.referenceMonth is a "YYYY-MM" string (the JSON shape of the
// backend's YearMonth) - parsed as UTC for the same reason format.ts's
// formatMonthYear is, and to build the Date the chart module's Bar contract
// expects.
function parseYearMonth(yearMonth: string): Date {
  const [year, month] = yearMonth.split("-").map(Number);
  return new Date(Date.UTC(year, month - 1, 1));
}

// The asset's own currency is an arbitrary ISO code (not necessarily the
// simulation's "BRL"|"USD" base currency formatCurrency is typed to), so
// buy/sell readouts use this local formatter instead.
function formatAssetCurrency(amount: number, currency: string): string {
  return new Intl.NumberFormat(undefined, { style: "currency", currency }).format(amount);
}

// currentPrice/marketValue/gainAmount/gainPercent are nullable: there's no
// endpoint that returns a base-currency price for a ticker the simulation
// doesn't currently hold, so an unheld (or never-anchored, just-bought)
// asset has no value to show for them.
type HoldingDisplay = {
  quantity: number;
  currentPrice: number | null;
  marketValue: number | null;
  gainAmount: number | null;
  gainPercent: number | null;
};

function TradeScreen() {
  const { id } = useParams<{ id: string }>();
  const [searchParams] = useSearchParams();
  const tickerParam = searchParams.get("ticker");
  const { user } = useAuth();

  const [simulation, setSimulation] = useState<Simulation | null>(null);
  const [positionsData, setPositionsData] = useState<PositionsResponse | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  const [query, setQuery] = useState("");
  const [searchResults, setSearchResults] = useState<TickerSearchResult[]>([]);
  const [searching, setSearching] = useState(false);
  const searchRequestIdRef = useRef(0);

  const [selectedAsset, setSelectedAsset] = useState<AssetDetail | null>(null);
  const [assetLoading, setAssetLoading] = useState(false);

  // Holds the post-trade holding figures for whichever ticker was just
  // traded, keyed so it's only applied while that same ticker is selected -
  // see the "post-trade figure refresh" judgment call for why this can't
  // just overwrite positionsData with a synthetic Position.
  const [tradeOverride, setTradeOverride] = useState<{ ticker: string; holding: HoldingDisplay } | null>(null);

  const [mode, setMode] = useState<"buy" | "sell">("buy");
  const [quantity, setQuantity] = useState("");
  const [submitting, setSubmitting] = useState(false);

  function addToast(message: string) {
    setToasts((prev) => [...prev, { id: genToastId(), message }]);
  }

  function dismissToast(toastId: string) {
    setToasts((prev) => prev.filter((toast) => toast.id !== toastId));
  }

  async function loadAsset(ticker: string) {
    if (!user || !id) return;
    setAssetLoading(true);
    try {
      const asset = await simulationApi.getAsset(id, ticker, user.token);
      setSelectedAsset(asset);
      setMode("buy");
      setQuantity("");
    } catch (err) {
      setSelectedAsset(null);
      addToast(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setAssetLoading(false);
    }
  }

  useEffect(() => {
    if (!user || !id) return;
    Promise.all([simulationApi.get(id, user.token), simulationApi.positions(id, user.token)])
      .then(([sim, positions]) => {
        setSimulation(sim);
        setPositionsData(positions);
      })
      .catch((err) => {
        setLoadError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
      });

    if (tickerParam) {
      loadAsset(tickerParam);
    }
    // Only the ticker param present at mount drives the auto-load - this
    // effect intentionally does not re-run if the URL's query string changes
    // later.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [id, user]);

  useEffect(() => {
    const trimmed = query.trim();
    if (trimmed.length < MIN_QUERY_LENGTH) {
      setSearchResults([]);
      setSearching(false);
      return;
    }
    if (!user || !id) return;

    setSearching(true);
    const requestId = ++searchRequestIdRef.current;
    const timer = setTimeout(() => {
      simulationApi
        .searchAssets(id, trimmed, user.token)
        .then((results) => {
          if (searchRequestIdRef.current !== requestId) return;
          setSearchResults(results);
        })
        .catch((err) => {
          if (searchRequestIdRef.current !== requestId) return;
          setSearchResults([]);
          addToast(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
        })
        .finally(() => {
          if (searchRequestIdRef.current === requestId) setSearching(false);
        });
    }, SEARCH_DEBOUNCE_MS);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [query, id, user]);

  const holding: HoldingDisplay | null = useMemo(() => {
    if (!selectedAsset) return null;
    if (tradeOverride && tradeOverride.ticker === selectedAsset.ticker) {
      return tradeOverride.holding;
    }
    const position = positionsData?.positions.find((p) => p.ticker === selectedAsset.ticker);
    if (!position) return null;
    return {
      quantity: position.quantity,
      currentPrice: position.currentPrice,
      marketValue: position.marketValue,
      gainAmount: position.gainAmount,
      gainPercent: position.gainPercent,
    };
  }, [selectedAsset, tradeOverride, positionsData]);

  const heldQuantity = holding?.quantity ?? 0;

  // The asset's own-currency "current price" - used for the buy/sell
  // readout/affordability/Max calc, distinct from holding.currentPrice
  // above (base currency, from the positions match). See the "current price
  // means two different things" judgment call.
  const assetCurrentPrice = useMemo(() => {
    if (!selectedAsset) return 0;
    const match = selectedAsset.series.find((bar) => bar.referenceMonth === selectedAsset.returnedMonth);
    return match?.close ?? selectedAsset.series.at(-1)?.close ?? 0;
  }, [selectedAsset]);

  const bars: Bar[] = useMemo(() => {
    if (!selectedAsset) return [];
    return selectedAsset.series.map((month) => ({
      date: parseYearMonth(month.referenceMonth),
      open: month.open,
      high: month.high,
      low: month.low,
      close: month.close,
      volume: month.volume,
    }));
  }, [selectedAsset]);

  function handleMaxClick() {
    if (!selectedAsset) return;
    if (mode === "buy") {
      const max = assetCurrentPrice > 0 ? Math.floor(selectedAsset.cashBalance.amount / assetCurrentPrice) : 0;
      setQuantity(String(Math.max(max, 0)));
    } else {
      setQuantity(String(heldQuantity));
    }
  }

  function handleTradeSuccess(response: TradeResponse, submittedQuantity: number, tradeMode: "buy" | "sell") {
    const ticker = response.position.ticker;
    const newQuantity = response.position.quantity;

    // See the "post-trade figure refresh" judgment call: the trade response
    // carries no price/market-value/gain fields, so those are derived here
    // rather than taken directly from it.
    const currentPrice = holding?.currentPrice ?? (newQuantity > 0 ? response.appliedAmount / newQuantity : null);
    const marketValue = currentPrice !== null ? newQuantity * currentPrice : null;

    setTradeOverride({
      ticker,
      holding: {
        quantity: newQuantity,
        currentPrice,
        marketValue,
        gainAmount: holding?.gainAmount ?? null,
        gainPercent: holding?.gainPercent ?? null,
      },
    });

    const assetCurrencyDelta = submittedQuantity * assetCurrentPrice;
    setSelectedAsset((prev) =>
      prev
        ? {
            ...prev,
            cashBalance: {
              ...prev.cashBalance,
              amount:
                tradeMode === "buy"
                  ? prev.cashBalance.amount - assetCurrencyDelta
                  : prev.cashBalance.amount + assetCurrencyDelta,
            },
          }
        : prev
    );

    setMode(newQuantity === 0 ? "buy" : tradeMode);
    setQuantity("");
    addToast(
      `${tradeMode === "buy" ? "Bought" : "Sold"} ${submittedQuantity} share${submittedQuantity === 1 ? "" : "s"} of ${ticker}.`
    );
  }

  async function handleTradeSubmit(event: FormEvent) {
    event.preventDefault();
    if (!user || !id || !selectedAsset) return;

    const parsedQuantity = Number(quantity);
    if (!quantity || !Number.isInteger(parsedQuantity) || parsedQuantity <= 0) return;
    if (mode === "sell" && heldQuantity === 0) return;

    setSubmitting(true);
    try {
      const apiCall = mode === "buy" ? simulationApi.buy : simulationApi.sell;
      const response = await apiCall(id, { ticker: selectedAsset.ticker, quantity: parsedQuantity }, user.token);
      handleTradeSuccess(response, parsedQuantity, mode);
    } catch (err) {
      addToast(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  const status: "loading" | "error" | "ready" = loadError
    ? "error"
    : simulation === null || positionsData === null
      ? "loading"
      : "ready";

  return (
    <main className="relative min-h-screen bg-paper px-4 py-10">
      <div className="washi-texture" aria-hidden="true" />
      <div className="relative mx-auto flex max-w-[1360px] flex-col gap-8">
        {status === "loading" && <p className="font-mono text-sm text-muted">Loading simulation…</p>}

        {status === "error" && <Banner tone="error">{loadError}</Banner>}

        {status === "ready" && simulation && (
          <>
            <TradeHeader simulationId={simulation.id} />

            <div className="flex gap-4">
              <div className="flex-1">
                <SearchPanel
                  query={query}
                  onQueryChange={setQuery}
                  results={searchResults}
                  searching={searching}
                  onSelect={loadAsset}
                />
              </div>

              <div className="flex flex-[2] flex-col gap-4">
                {selectedAsset ? (
                  <>
                    <AssetInfoCard asset={selectedAsset} holding={holding} baseCurrency={simulation.baseCurrency} />
                    <TradeChartPanel bars={bars} />
                    <TradeForm
                      asset={selectedAsset}
                      mode={mode}
                      onModeChange={setMode}
                      quantity={quantity}
                      onQuantityChange={setQuantity}
                      heldQuantity={heldQuantity}
                      currentPrice={assetCurrentPrice}
                      submitting={submitting}
                      onMax={handleMaxClick}
                      onSubmit={handleTradeSubmit}
                    />
                  </>
                ) : assetLoading ? (
                  <p className="font-mono text-sm text-muted">Loading asset…</p>
                ) : (
                  <EmptySelectionState />
                )}
              </div>
            </div>
          </>
        )}
      </div>

      <ToastStack toasts={toasts} onDismiss={dismissToast} />
    </main>
  );
}

function TradeHeader({ simulationId }: { simulationId: string }) {
  return (
    <header className="flex items-center gap-4">
      <Link to={`/simulations/${simulationId}`} className={`${buttonBaseClasses} ${buttonVariantClasses.ink}`}>
        ← Back
      </Link>
      <h1 className="font-display text-xl font-bold text-ink">Trade assets</h1>
    </header>
  );
}

function SearchPanel({
  query,
  onQueryChange,
  results,
  searching,
  onSelect,
}: {
  query: string;
  onQueryChange: (value: string) => void;
  results: TickerSearchResult[];
  searching: boolean;
  onSelect: (ticker: string) => void;
}) {
  const trimmed = query.trim();
  return (
    <div className="flex flex-col border border-ink/10 bg-panel p-5">
      <h2 className="font-display text-lg font-bold text-ink">Search</h2>
      <div className="mt-3">
        <TextField
          id="asset-search"
          label="Ticker or name"
          placeholder="e.g. AAPL"
          autoComplete="off"
          value={query}
          onChange={(e) => onQueryChange(e.target.value)}
        />
      </div>
      <div className="mt-4 flex flex-col gap-1.5" data-testid="search-results">
        {searching && <p className="font-mono text-xs text-muted">Searching…</p>}
        {!searching && trimmed.length >= MIN_QUERY_LENGTH && results.length === 0 && (
          <p className="font-mono text-xs text-muted">No matches.</p>
        )}
        {results.map((result) => (
          <button
            key={result.ticker}
            type="button"
            onClick={() => onSelect(result.ticker)}
            className="flex items-center justify-between border border-ink/10 bg-paper px-3 py-2 text-left hover:bg-ink/5"
          >
            <span className="flex flex-col">
              <span className="font-mono text-sm font-bold text-ink">{result.ticker}</span>
              <span className="max-w-[220px] truncate font-sans text-xs text-name">{result.name}</span>
            </span>
            <span className="font-mono text-[10px] uppercase tracking-[.1em] text-muted">{result.exchange}</span>
          </button>
        ))}
      </div>
    </div>
  );
}

function HoldingStat({
  label,
  value,
  positive,
  testId,
}: {
  label: string;
  value: string;
  positive?: boolean;
  testId: string;
}) {
  return (
    <div>
      <p className="font-mono text-[10px] uppercase tracking-[.14em] text-muted">{label}</p>
      <p
        className={`mt-1 font-mono text-sm ${positive ? "text-vermilion" : "text-ink"}`}
        data-testid={testId}
      >
        {value}
      </p>
    </div>
  );
}

function AssetInfoCard({
  asset,
  holding,
  baseCurrency,
}: {
  asset: AssetDetail;
  holding: HoldingDisplay | null;
  baseCurrency: "BRL" | "USD";
}) {
  return (
    <div className="border border-ink/10 bg-panel p-5">
      <div className="flex items-baseline justify-between">
        <div>
          <p className="font-mono text-sm font-bold text-ink">{asset.ticker}</p>
          <p className="font-sans text-sm text-name">{asset.name}</p>
        </div>
        <p className="font-mono text-[10px] uppercase tracking-[.14em] text-muted">{asset.currency}</p>
      </div>
      <div className="mt-4 grid grid-cols-4 gap-4">
        <HoldingStat testId="holding-quantity" label="Quantity" value={String(holding?.quantity ?? 0)} />
        <HoldingStat
          testId="holding-market-value"
          label="Market value"
          value={holding?.marketValue != null ? formatCurrency(holding.marketValue, baseCurrency) : "—"}
        />
        <HoldingStat
          testId="holding-current-price"
          label="Current price"
          value={holding?.currentPrice != null ? formatCurrency(holding.currentPrice, baseCurrency) : "—"}
        />
        <HoldingStat
          testId="holding-gain-loss"
          label="Gain / loss"
          value={holding?.gainPercent != null ? formatPercent(holding.gainPercent) : "—"}
          positive={holding?.gainAmount != null && holding.gainAmount >= 0}
        />
      </div>
    </div>
  );
}

function TradeChartPanel({ bars }: { bars: Bar[] }) {
  return (
    <div className="border border-ink/10 bg-panel p-5">
      <h2 className="font-display text-lg font-bold text-ink">Chart</h2>
      <div className="mt-3">
        <FinancialChart bars={bars} chartType="candlestick" showVolume />
      </div>
    </div>
  );
}

function EmptySelectionState() {
  return (
    <div className="flex flex-1 items-center justify-center border border-ink/10 bg-panel p-10">
      <p className="font-mono text-sm text-muted">Search for an asset to begin trading.</p>
    </div>
  );
}

function TradeForm({
  asset,
  mode,
  onModeChange,
  quantity,
  onQuantityChange,
  heldQuantity,
  currentPrice,
  submitting,
  onMax,
  onSubmit,
}: {
  asset: AssetDetail;
  mode: "buy" | "sell";
  onModeChange: (mode: "buy" | "sell") => void;
  quantity: string;
  onQuantityChange: (value: string) => void;
  heldQuantity: number;
  currentPrice: number;
  submitting: boolean;
  onMax: () => void;
  onSubmit: (event: FormEvent) => void;
}) {
  const parsedQuantity = Number(quantity);
  const validQuantity = quantity !== "" && Number.isInteger(parsedQuantity) && parsedQuantity > 0;
  const estimatedAmount = validQuantity ? parsedQuantity * currentPrice : 0;
  const affordable =
    mode === "buy"
      ? !validQuantity || estimatedAmount <= asset.cashBalance.amount
      : !validQuantity || parsedQuantity <= heldQuantity;
  const sellDisabled = heldQuantity === 0;
  const canSubmit = !submitting && validQuantity && !(mode === "sell" && sellDisabled);

  return (
    <form onSubmit={onSubmit} className="border border-ink/10 bg-panel p-5">
      <h2 className="font-display text-lg font-bold text-ink">Trade</h2>

      <div className="mt-3 flex gap-2">
        <Button
          type="button"
          variant={mode === "buy" ? "primary" : "ink"}
          onClick={() => onModeChange("buy")}
          disabled={submitting}
        >
          Buy
        </Button>
        <Button
          type="button"
          variant={mode === "sell" ? "primary" : "ink"}
          onClick={() => onModeChange("sell")}
          disabled={submitting || sellDisabled}
        >
          Sell
        </Button>
      </div>

      <div className="mt-4 flex items-end gap-3">
        <div className="flex-1">
          <TextField
            id="trade-quantity"
            label="Quantity"
            type="number"
            min="1"
            step="1"
            value={quantity}
            onChange={(e) => onQuantityChange(e.target.value)}
            disabled={submitting}
          />
        </div>
        <button
          type="button"
          onClick={onMax}
          disabled={submitting}
          className="border border-ink/30 px-3 py-2.5 font-mono text-[11px] uppercase tracking-[.1em] text-ink"
        >
          Max
        </button>
      </div>

      <div className="mt-4 flex flex-col gap-1 font-mono text-sm">
        <p className="text-ink">
          {mode === "buy" ? "Estimated cost" : "Estimated proceeds"}: {formatAssetCurrency(estimatedAmount, asset.currency)}
        </p>
        <p className={affordable ? "text-muted" : "text-vermilion"}>
          {mode === "buy"
            ? `Cash available: ${formatAssetCurrency(asset.cashBalance.amount, asset.currency)}`
            : `Held: ${heldQuantity} share${heldQuantity === 1 ? "" : "s"}`}
        </p>
      </div>

      <div className="mt-6">
        <Button type="submit" disabled={!canSubmit}>
          {submitting ? (mode === "buy" ? "Buying…" : "Selling…") : mode === "buy" ? "Buy shares" : "Sell shares"}
        </Button>
      </div>
    </form>
  );
}
