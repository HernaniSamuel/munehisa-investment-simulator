import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from "react";
import { Link, useParams } from "react-router";
import { useTranslation } from "react-i18next";
import type { Route } from "./+types/simulation-dashboard";
import { ProtectedRoute } from "~/components/ProtectedRoute";
import { Banner, Button, TextField, buttonBaseClasses, buttonVariantClasses } from "~/components/ui";
import { LanguageSwitcher } from "~/components/LanguageSwitcher";
import { Tooltip } from "~/components/Tooltip";
import { CurrencyValue } from "~/components/CurrencyValue";
import { ToastStack, type ToastItem } from "~/components/Toast";
import { useAuth } from "~/lib/auth-context";
import {
  ApiError,
  simulationApi,
  type AdvanceMonthResponse,
  type CashMovementResponse,
  type Position,
  type PositionsResponse,
  type Simulation,
  type Transaction,
  type TransactionType,
} from "~/lib/api";
import {
  formatCurrency,
  formatCurrencyExact,
  formatMonthYear,
  formatPercent,
  isAbbreviatedCurrency,
  truncateName,
} from "~/lib/format";
import { computeDonutSegments, type DonutSegment } from "~/lib/donut";
import i18n from "~/lib/i18n";

export function meta({}: Route.MetaArgs) {
  return [{ title: i18n.t("dashboard.metaTitle") }];
}

export default function SimulationDashboard() {
  return (
    <ProtectedRoute>
      <SimulationDashboardScreen />
    </ProtectedRoute>
  );
}

function genToastId(): string {
  return `${Date.now()}-${Math.random()}`;
}

// Shared by every non-toast, non-donut-tooltip currency display in this
// file - see CurrencyValue for why abbreviated/exact are precomputed here
// rather than passed as formatter callbacks.
function currencyValue(amount: number, baseCurrency: "BRL" | "USD") {
  return (
    <CurrencyValue
      abbreviated={formatCurrency(amount, baseCurrency)}
      exact={isAbbreviatedCurrency(amount) ? formatCurrencyExact(amount, baseCurrency) : null}
    />
  );
}

function SimulationDashboardScreen() {
  const { t } = useTranslation();
  const { id } = useParams<{ id: string }>();
  const { user } = useAuth();

  const [simulation, setSimulation] = useState<Simulation | null>(null);
  const [positionsData, setPositionsData] = useState<PositionsResponse | null>(null);
  const [transactions, setTransactions] = useState<Transaction[] | null>(null);
  // Raw error data rather than pre-formatted text, so the banner re-resolves
  // through t() on every render, including after a language switch while
  // it's still on screen.
  const [loadError, setLoadError] = useState<unknown>(null);
  const [toasts, setToasts] = useState<ToastItem[]>([]);
  const [cashDialogMode, setCashDialogMode] = useState<"deposit" | "withdraw" | null>(null);
  const [advanceDialogOpen, setAdvanceDialogOpen] = useState(false);
  const [resetDialogOpen, setResetDialogOpen] = useState(false);

  useEffect(() => {
    if (!user || !id) return;
    Promise.all([
      simulationApi.get(id, user.token),
      simulationApi.positions(id, user.token),
      simulationApi.transactions(id, user.token),
    ])
      .then(([sim, positions, txs]) => {
        setSimulation(sim);
        setPositionsData(positions);
        setTransactions(txs);
      })
      .catch((err) => {
        setLoadError(err);
      });
  }, [id, user]);

  function refetchPositions() {
    if (!user || !id) return;
    simulationApi
      .positions(id, user.token)
      .then(setPositionsData)
      .catch(() => {});
  }

  function refetchTransactions() {
    if (!user || !id) return;
    simulationApi
      .transactions(id, user.token)
      .then(setTransactions)
      .catch(() => {});
  }

  function addToast(message: ReactNode) {
    setToasts((prev) => [...prev, { id: genToastId(), message }]);
  }

  function dismissToast(toastId: string) {
    setToasts((prev) => prev.filter((toast) => toast.id !== toastId));
  }

  function handleCashMovementSuccess(mode: "deposit" | "withdraw", response: CashMovementResponse) {
    setSimulation((prev) =>
      prev ? { ...prev, cashBalance: response.cashBalance, totalPatrimony: response.totalPatrimony } : prev
    );
    setCashDialogMode(null);
    const verb = mode === "deposit" ? t("dashboard.depositedVerb") : t("dashboard.withdrewVerb");
    addToast(
      <>
        {verb} {currencyValue(response.appliedAmount, simulation?.baseCurrency ?? "BRL")}.
      </>
    );
    refetchTransactions();
  }

  function handleResetSuccess(response: Simulation) {
    setSimulation(response);
    setResetDialogOpen(false);
    addToast(t("dashboard.resetToast"));
    refetchPositions();
    refetchTransactions();
  }

  function handleAdvanceSuccess(response: AdvanceMonthResponse) {
    setSimulation((prev) =>
      prev
        ? {
            ...prev,
            currentMonth: response.currentMonth,
            cashBalance: response.cashBalance,
            totalPatrimony: response.totalPatrimony,
          }
        : prev
    );
    setPositionsData((prev) => (prev ? { ...prev, totalAssetValue: response.totalAssetValue } : prev));
    setAdvanceDialogOpen(false);
    addToast(t("dashboard.advancedToast", { month: formatMonthYear(response.currentMonth) }));
    for (const position of response.positions) {
      if (position.dividendReceived > 0) {
        addToast(
          <>
            {position.assetName} {t("dashboard.paidDividendOf")}{" "}
            {currencyValue(position.dividendReceived, simulation?.baseCurrency ?? "BRL")}.
          </>
        );
      }
    }
    refetchPositions();
    refetchTransactions();
  }

  const status: "loading" | "error" | "ready" = loadError
    ? "error"
    : simulation === null || positionsData === null || transactions === null
      ? "loading"
      : "ready";

  const canReset = simulation ? simulation.currentMonth !== simulation.startMonth : false;

  return (
    <main className="relative min-h-screen bg-paper px-4 py-10">
      <div className="washi-texture" aria-hidden="true" />
      <div className="relative mx-auto flex max-w-[1360px] flex-col gap-8">
        {status === "loading" && (
          <p className="font-mono text-sm text-muted">{t("dashboard.loadingSimulation")}</p>
        )}

        {status === "error" && (
          <Banner tone="error">
            {loadError instanceof ApiError ? loadError.message : t("common.somethingWentWrong")}
          </Banner>
        )}

        {status === "ready" && simulation && positionsData && transactions && (
          <>
            <DashboardHeader
              simulation={simulation}
              canReset={canReset}
              onResetClick={() => setResetDialogOpen(true)}
              onAdvanceClick={() => setAdvanceDialogOpen(true)}
            />

            <div data-testid="kpi-grid" className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-4">
              <CashBalanceCard
                simulation={simulation}
                onContribute={() => setCashDialogMode("deposit")}
                onWithdraw={() => setCashDialogMode("withdraw")}
              />
              <PortfolioValueCard positionsData={positionsData} baseCurrency={simulation.baseCurrency} />
              <TotalValueCard simulation={simulation} positionsData={positionsData} />
              <GainLossCard positionsData={positionsData} baseCurrency={simulation.baseCurrency} />
            </div>

            <div className="flex flex-col gap-4 lg:flex-row">
              <div data-testid="positions-column" className="lg:flex-[1.75]">
                <PositionsTable
                  positions={positionsData.positions}
                  simulationId={simulation.id}
                  baseCurrency={simulation.baseCurrency}
                />
              </div>
              <div data-testid="allocation-column" className="lg:flex-1">
                <AllocationDonut positions={positionsData.positions} baseCurrency={simulation.baseCurrency} />
              </div>
            </div>

            <TransactionHistory transactions={transactions} baseCurrency={simulation.baseCurrency} />
          </>
        )}
      </div>

      {cashDialogMode && simulation && (
        <CashMovementDialog
          mode={cashDialogMode}
          simulationId={simulation.id}
          baseCurrency={simulation.baseCurrency}
          onCancel={() => setCashDialogMode(null)}
          onSuccess={handleCashMovementSuccess}
        />
      )}

      {advanceDialogOpen && simulation && (
        <AdvanceConfirmDialog
          simulationId={simulation.id}
          onCancel={() => setAdvanceDialogOpen(false)}
          onSuccess={handleAdvanceSuccess}
        />
      )}

      {resetDialogOpen && simulation && (
        <ResetConfirmDialog
          simulationId={simulation.id}
          onCancel={() => setResetDialogOpen(false)}
          onSuccess={handleResetSuccess}
        />
      )}

      <ToastStack toasts={toasts} onDismiss={dismissToast} />
    </main>
  );
}

function DashboardHeader({
  simulation,
  canReset,
  onResetClick,
  onAdvanceClick,
}: {
  simulation: Simulation;
  canReset: boolean;
  onResetClick: () => void;
  onAdvanceClick: () => void;
}) {
  const { t } = useTranslation();
  const displayName = truncateName(simulation.name);
  return (
    <header className="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
      <div className="flex min-w-0 items-center gap-4">
        <Link to="/" className={`${buttonBaseClasses} ${buttonVariantClasses.ink}`}>
          {t("dashboard.back")}
        </Link>
        <div className="min-w-0 flex-1">
          {displayName === simulation.name ? (
            <h1 className="font-display text-xl font-bold text-ink">{displayName}</h1>
          ) : (
            <Tooltip label={simulation.name}>
              <h1 className="font-display text-xl font-bold text-ink">{displayName}</h1>
            </Tooltip>
          )}
          <p className="mt-1 font-mono text-[11px] uppercase tracking-[.14em] text-muted">
            {t("dashboard.startedLabel", {
              month: formatMonthYear(simulation.startMonth),
              currency: simulation.baseCurrency,
            })}
          </p>
        </div>
      </div>

      <div className="flex flex-wrap items-center gap-4">
        <LanguageSwitcher />

        <div className="border border-ink/15 bg-panel px-4 py-2 text-center">
          <p className="font-mono text-[10px] uppercase tracking-[.14em] text-muted">{t("dashboard.currentDate")}</p>
          <p className="mt-1 font-display text-lg font-bold text-ink" data-testid="current-date-value">
            {formatMonthYear(simulation.currentMonth)}
          </p>
        </div>

        <Tooltip label={canReset ? t("dashboard.resetExplanation") : t("dashboard.resetDisabledExplanation")}>
          <span className="inline-block">
            <Button type="button" variant="ink" disabled={!canReset} onClick={onResetClick}>
              {t("dashboard.reset")}
            </Button>
          </span>
        </Tooltip>

        <Link
          to={`/simulations/${simulation.id}/trade`}
          className={`${buttonBaseClasses} ${buttonVariantClasses.secondary}`}
        >
          {t("dashboard.tradeAssets")}
        </Link>

        <Tooltip label={t("dashboard.advanceExplanation")}>
          <span className="inline-block">
            <Button type="button" onClick={onAdvanceClick}>
              {t("dashboard.advanceMonth")}
            </Button>
          </span>
        </Tooltip>
      </div>
    </header>
  );
}

function StatCardHeader({ seal, label, highlight }: { seal: string; label: string; highlight?: boolean }) {
  return (
    <div className="flex items-center gap-2">
      <span
        className={`flex h-9 w-9 items-center justify-center font-display text-base text-paper ${
          highlight ? "bg-vermilion" : "bg-ink"
        }`}
      >
        {seal}
      </span>
      <span className="font-mono text-[10px] uppercase tracking-[.14em] text-muted">{label}</span>
    </div>
  );
}

function CashBalanceCard({
  simulation,
  onContribute,
  onWithdraw,
}: {
  simulation: Simulation;
  onContribute: () => void;
  onWithdraw: () => void;
}) {
  const { t } = useTranslation();
  return (
    <div className="border border-ink/[.12] bg-panel p-5">
      <Tooltip label={t("dashboard.cashBalanceExplanation")}>
        <div>
          <StatCardHeader seal="金" label={t("dashboard.cashBalance")} />
          <p className="mt-3 font-display text-[31px] font-bold text-ink" data-testid="cash-balance-value">
            {currencyValue(simulation.cashBalance, simulation.baseCurrency)}
          </p>
        </div>
      </Tooltip>
      <div className="mt-3 flex gap-2">
        <Tooltip label={t("dashboard.contributeExplanation")}>
          <span className="inline-block">
            <button
              type="button"
              onClick={onContribute}
              className="border border-ink/30 px-3 py-1.5 font-mono text-[11px] uppercase tracking-[.1em] text-ink"
            >
              {t("dashboard.contribute")}
            </button>
          </span>
        </Tooltip>
        <Tooltip label={t("dashboard.withdrawExplanation")}>
          <span className="inline-block">
            <button
              type="button"
              onClick={onWithdraw}
              className="border border-ink/30 px-3 py-1.5 font-mono text-[11px] uppercase tracking-[.1em] text-ink"
            >
              {t("dashboard.withdraw")}
            </button>
          </span>
        </Tooltip>
      </div>
    </div>
  );
}

function PortfolioValueCard({
  positionsData,
  baseCurrency,
}: {
  positionsData: PositionsResponse;
  baseCurrency: "BRL" | "USD";
}) {
  const { t } = useTranslation();
  return (
    <div className="border border-ink/[.12] bg-panel p-5">
      <Tooltip label={t("dashboard.portfolioValueExplanation")}>
        <div>
          <StatCardHeader seal="株" label={t("dashboard.portfolioValue")} />
          <p className="mt-3 font-display text-[31px] font-bold text-ink">
            {currencyValue(positionsData.totalAssetValue, baseCurrency)}
          </p>
          <p className="mt-1 font-mono text-xs text-muted">
            {t("dashboard.positionsCount", { count: positionsData.positions.length })}
          </p>
        </div>
      </Tooltip>
    </div>
  );
}

function TotalValueCard({
  simulation,
  positionsData,
}: {
  simulation: Simulation;
  positionsData: PositionsResponse;
}) {
  const { t } = useTranslation();
  const total = simulation.cashBalance + positionsData.totalAssetValue;
  return (
    <div className="border border-ink/[.12] bg-panel p-5">
      <Tooltip label={t("dashboard.totalValueExplanation")}>
        <div>
          <StatCardHeader seal="総" label={t("dashboard.totalValue")} />
          <p className="mt-3 font-display text-[31px] font-bold text-ink">
            {currencyValue(total, simulation.baseCurrency)}
          </p>
        </div>
      </Tooltip>
    </div>
  );
}

function GainLossCard({
  positionsData,
  baseCurrency,
}: {
  positionsData: PositionsResponse;
  baseCurrency: "BRL" | "USD";
}) {
  const { t } = useTranslation();
  const positive = positionsData.totalGainAmount >= 0;
  return (
    <div className="border border-vermilion/30 bg-panel p-5">
      <Tooltip label={t("dashboard.gainLossExplanation")}>
        <div>
          <StatCardHeader seal="利" label={t("dashboard.gainLoss")} highlight />
          <p
            className={`mt-3 font-display text-[31px] font-bold ${positive ? "text-vermilion" : "text-ink"}`}
            data-testid="gain-loss-value"
          >
            {currencyValue(positionsData.totalGainAmount, baseCurrency)}
          </p>
          <p className={`mt-1 font-mono text-xs ${positive ? "text-vermilion" : "text-ink"}`}>
            {formatPercent(positionsData.totalGainPercent)}
          </p>
        </div>
      </Tooltip>
    </div>
  );
}

function PositionsTable({
  positions,
  simulationId,
  baseCurrency,
}: {
  positions: Position[];
  simulationId: string;
  baseCurrency: "BRL" | "USD";
}) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col border border-ink/10 bg-panel p-5">
      <h2 className="font-display text-lg font-bold text-ink">{t("dashboard.positions")}</h2>
      <div className="mt-3 max-h-[320px] overflow-auto pr-3 pb-3" data-testid="positions-table-scroll">
        <table className="w-full min-w-[640px] border-collapse">
          <thead>
            <tr className="border-b border-ink/30 text-left">
              <th className="py-2 font-mono text-[10px] uppercase tracking-[.08em] text-muted">{t("dashboard.ticker")}</th>
              <th className="py-2 font-mono text-[10px] uppercase tracking-[.08em] text-muted">{t("dashboard.name")}</th>
              <th className="py-2 text-right font-mono text-[10px] uppercase tracking-[.08em] text-muted">
                {t("dashboard.qty")}
              </th>
              <th className="py-2 text-right font-mono text-[10px] uppercase tracking-[.08em] text-muted">
                {t("dashboard.price")}
              </th>
              <th className="py-2 text-right font-mono text-[10px] uppercase tracking-[.08em] text-muted">
                {t("dashboard.marketValue")}
              </th>
              <th className="py-2 text-right font-mono text-[10px] uppercase tracking-[.08em] text-muted">
                {t("dashboard.gain")}
              </th>
            </tr>
          </thead>
          <tbody>
            {positions.map((position) => (
              <tr key={position.ticker} className="border-b border-ink/[.08]">
                <td className="py-2 font-mono text-sm font-bold text-ink">{position.ticker}</td>
                <td className="py-2">
                  <Tooltip
                    label={t("dashboard.assetTooltip", { assetName: position.assetName })}
                  >
                    <Link
                      to={`/simulations/${simulationId}/trade?ticker=${encodeURIComponent(position.ticker)}`}
                      className="block max-w-[180px] truncate font-sans text-sm text-name hover:text-teal"
                    >
                      {position.assetName}
                    </Link>
                  </Tooltip>
                </td>
                <td className="py-2 text-right font-mono text-sm text-ink">{position.quantity}</td>
                <td className="py-2 text-right font-mono text-sm text-ink">
                  {currencyValue(position.currentPrice, baseCurrency)}
                </td>
                <td className="py-2 text-right font-mono text-sm text-ink">
                  {currencyValue(position.marketValue, baseCurrency)}
                </td>
                <td
                  className={`py-2 text-right font-mono text-sm ${
                    position.gainAmount >= 0 ? "text-vermilion" : "text-ink"
                  }`}
                >
                  {currencyValue(position.gainAmount, baseCurrency)} ({formatPercent(position.gainPercent)})
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

// SliceDetail only ever renders as another Tooltip's own portaled label
// content (see AllocationDonut below), which has no hover handlers of its
// own to stay open - so a second, nested Tooltip here would be unreachable
// by mouse. The exact value is appended inline instead: it's still shown on
// hover, just as more text within the one Tooltip that's already open.
function currencyValueInline(amount: number, baseCurrency: "BRL" | "USD") {
  const abbreviated = formatCurrency(amount, baseCurrency);
  if (!isAbbreviatedCurrency(amount)) return abbreviated;
  return `${abbreviated} (exact: ${formatCurrencyExact(amount, baseCurrency)})`;
}

function SliceDetail({ segment, baseCurrency }: { segment: DonutSegment; baseCurrency: "BRL" | "USD" }) {
  const { t } = useTranslation();
  return (
    <div className="flex flex-col gap-0.5">
      <span className="font-bold">
        {segment.assetName} ({segment.ticker})
      </span>
      <span>{t("dashboard.weightLabel")} {formatPercent(segment.weight)}</span>
      <span>{t("dashboard.quantityLabel")} {segment.quantity}</span>
      <span>{t("dashboard.investedLabel")} {currencyValueInline(segment.costBasis, baseCurrency)}</span>
      <span>{t("dashboard.marketValueLabel")} {currencyValueInline(segment.marketValue, baseCurrency)}</span>
    </div>
  );
}

function AllocationDonut({
  positions,
  baseCurrency,
}: {
  positions: Position[];
  baseCurrency: "BRL" | "USD";
}) {
  const { t } = useTranslation();
  const segments = useMemo(() => computeDonutSegments(positions), [positions]);
  const radius = 60;
  const size = radius * 2 + 20;
  const center = size / 2;

  return (
    <div className="flex max-h-[420px] flex-col border border-ink/10 bg-panel p-5">
      <h2 className="font-display text-lg font-bold text-ink">{t("dashboard.allocation")}</h2>
      <div className="mt-3 flex shrink-0 items-center justify-center" data-testid="donut-chart">
        <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden={segments.length === 0}>
          <g transform={`rotate(-90 ${center} ${center})`}>
            {segments.map((segment) => (
              <Tooltip key={segment.ticker} label={<SliceDetail segment={segment} baseCurrency={baseCurrency} />}>
                <circle
                  cx={center}
                  cy={center}
                  r={radius}
                  fill="none"
                  stroke={segment.color}
                  strokeWidth={20}
                  strokeDasharray={segment.dashArray}
                  strokeDashoffset={segment.dashOffset}
                  role="img"
                  aria-label={t("dashboard.sliceAriaLabel", { ticker: segment.ticker, assetName: segment.assetName })}
                  tabIndex={0}
                />
              </Tooltip>
            ))}
          </g>
          <text
            x={center}
            y={center - 4}
            textAnchor="middle"
            data-testid="donut-core-count"
            className="fill-ink font-display text-xl font-bold"
          >
            {positions.length}
          </text>
          <text x={center} y={center + 14} textAnchor="middle" className="fill-muted font-mono text-[9px] uppercase">
            {t("dashboard.positions")}
          </text>
        </svg>
      </div>
      <div className="mt-4 max-h-[140px] overflow-y-auto pr-3" data-testid="donut-legend">
        <ul className="flex flex-col gap-1.5">
          {segments.map((segment) => (
            <li key={segment.ticker} className="flex items-center gap-2 font-mono text-[11px] text-name">
              <span className="h-2.5 w-2.5 shrink-0" style={{ backgroundColor: segment.color }} />
              <span className="flex-1 truncate">{segment.ticker}</span>
              <span>{formatPercent(segment.weight)}</span>
            </li>
          ))}
        </ul>
      </div>
    </div>
  );
}

// Color derives from type, never from amount's sign - amount is always a
// positive magnitude per the transactions API contract.
const VERMILION_TYPES: TransactionType[] = ["SELL", "DEPOSIT", "DIVIDEND"];

function TransactionHistory({
  transactions,
  baseCurrency,
}: {
  transactions: Transaction[];
  baseCurrency: "BRL" | "USD";
}) {
  const { t } = useTranslation();
  const groups = useMemo(() => {
    const byMonth = new Map<string, Transaction[]>();
    for (const tx of transactions) {
      const list = byMonth.get(tx.month) ?? [];
      list.push(tx);
      byMonth.set(tx.month, list);
    }
    // The API already orders results by month descending, so the Map's
    // first-insertion-order iteration reproduces that same group order.
    return Array.from(byMonth.entries());
  }, [transactions]);

  return (
    <div className="flex flex-col border border-ink/10 bg-panel p-5">
      <h2 className="font-display text-lg font-bold text-ink">{t("dashboard.transactionHistory")}</h2>
      <div className="mt-3 max-h-[400px] overflow-y-auto pr-3" data-testid="transaction-history-scroll">
        {groups.length === 0 && <p className="font-sans text-sm text-muted">{t("dashboard.noTransactions")}</p>}
        {groups.map(([month, txs]) => (
          <div key={month} className="mb-4">
            <h3 className="font-mono text-[11px] uppercase tracking-[.14em] text-muted">
              {formatMonthYear(month)}
            </h3>
            <ul className="mt-2 flex flex-col gap-1.5">
              {txs.map((tx, index) => (
                <li
                  key={`${month}-${index}`}
                  className="flex items-center justify-between border-b border-ink/[.08] py-1.5"
                >
                  <span className="font-sans text-sm text-name">
                    {tx.type === "SELL" ? (
                      <Tooltip label={t("dashboard.sellSplitExplanation")}>
                        <span tabIndex={0}>{t(`dashboard.transactionType.${tx.type}`)}</span>
                      </Tooltip>
                    ) : (
                      t(`dashboard.transactionType.${tx.type}`)
                    )}
                    {tx.ticker ? ` · ${tx.ticker}` : ""}
                  </span>
                  <span
                    className={`font-mono text-sm ${
                      VERMILION_TYPES.includes(tx.type) ? "text-vermilion" : "text-ink"
                    }`}
                  >
                    {currencyValue(tx.amount, baseCurrency)}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    </div>
  );
}

function CashMovementDialog({
  mode,
  simulationId,
  baseCurrency,
  onCancel,
  onSuccess,
}: {
  mode: "deposit" | "withdraw";
  simulationId: string;
  baseCurrency: "BRL" | "USD";
  onCancel: () => void;
  onSuccess: (mode: "deposit" | "withdraw", response: CashMovementResponse) => void;
}) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const [amount, setAmount] = useState("");
  const [todaysMoney, setTodaysMoney] = useState(false);
  // "invalidAmount" or the raw caught error rather than pre-formatted text,
  // so the banner re-resolves through t() on every render.
  const [error, setError] = useState<"invalidAmount" | unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const amountInputRef = useRef<HTMLInputElement>(null);

  function close() {
    if (submitting) return;
    onCancel();
  }

  useEffect(() => {
    amountInputRef.current?.focus();
  }, []);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") close();
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [submitting]);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!user) return;

    const parsedAmount = Number(amount);
    if (!amount || !(parsedAmount > 0)) {
      setError("invalidAmount");
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      const apiCall = mode === "deposit" ? simulationApi.deposit : simulationApi.withdraw;
      const response = await apiCall(simulationId, { amount: parsedAmount, todaysMoney }, user.token);
      onSuccess(mode, response);
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  const title = mode === "deposit" ? t("dashboard.cashMovementTitleDeposit") : t("dashboard.cashMovementTitleWithdraw");

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/60 px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="cash-movement-title"
      onClick={(event) => {
        if (event.target === event.currentTarget) close();
      }}
    >
      <form
        onSubmit={handleSubmit}
        className="relative w-full max-w-[420px] border border-ink/10 bg-panel p-5 shadow-[0_0_0_3px_#211E18] sm:p-8"
      >
        <h3 id="cash-movement-title" className="font-display text-xl font-bold text-ink">
          {title}
        </h3>

        <div className="mt-4 flex flex-col gap-4">
          {error != null && (
            <Banner tone="error">
              {error === "invalidAmount"
                ? t("dashboard.amountGreaterThanZero")
                : error instanceof ApiError
                  ? error.message
                  : t("common.somethingWentWrong")}
            </Banner>
          )}

          <TextField
            ref={amountInputRef}
            id="cash-movement-amount"
            label={t("dashboard.amountLabel", { currency: baseCurrency })}
            type="number"
            min="0"
            step="0.01"
            required
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            disabled={submitting}
          />

          <label className="flex items-center gap-2 font-sans text-sm text-name">
            <input
              type="checkbox"
              checked={todaysMoney}
              onChange={(e) => setTodaysMoney(e.target.checked)}
              disabled={submitting}
            />
            {t("dashboard.removeInflation")}
          </label>
        </div>

        <div className="mt-6 flex justify-end gap-3">
          <Button type="button" variant="ink" onClick={close} disabled={submitting}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" disabled={submitting}>
            {submitting ? t("dashboard.submitting") : title}
          </Button>
        </div>
      </form>
    </div>
  );
}

function AdvanceConfirmDialog({
  simulationId,
  onCancel,
  onSuccess,
}: {
  simulationId: string;
  onCancel: () => void;
  onSuccess: (response: AdvanceMonthResponse) => void;
}) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const cancelButtonRef = useRef<HTMLButtonElement>(null);

  function close() {
    if (submitting) return;
    onCancel();
  }

  useEffect(() => {
    cancelButtonRef.current?.focus();
  }, []);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") close();
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [submitting]);

  async function handleConfirm() {
    if (!user) return;
    setError(null);
    setSubmitting(true);
    try {
      const response = await simulationApi.advance(simulationId, user.token);
      onSuccess(response);
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/60 px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="advance-month-title"
      onClick={(event) => {
        if (event.target === event.currentTarget) close();
      }}
    >
      <div className="relative w-full max-w-[420px] border border-ink/10 bg-panel p-5 shadow-[0_0_0_3px_#211E18] sm:p-8">
        <h3 id="advance-month-title" className="font-display text-xl font-bold text-ink">
          {t("dashboard.advanceDialogTitle")}
        </h3>
        <p className="mt-2 font-sans text-sm text-name">{t("dashboard.advanceExplanation")}</p>

        {error != null && (
          <div className="mt-4">
            <Banner tone="error">
              {error instanceof ApiError ? error.message : t("common.somethingWentWrong")}
            </Banner>
          </div>
        )}

        <div className="mt-6 flex justify-end gap-3">
          <Button ref={cancelButtonRef} type="button" variant="ink" onClick={close} disabled={submitting}>
            {t("common.cancel")}
          </Button>
          <Button type="button" onClick={handleConfirm} disabled={submitting}>
            {submitting ? t("dashboard.advancing") : t("dashboard.advanceMonth")}
          </Button>
        </div>
      </div>
    </div>
  );
}

function ResetConfirmDialog({
  simulationId,
  onCancel,
  onSuccess,
}: {
  simulationId: string;
  onCancel: () => void;
  onSuccess: (response: Simulation) => void;
}) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const cancelButtonRef = useRef<HTMLButtonElement>(null);

  function close() {
    if (submitting) return;
    onCancel();
  }

  useEffect(() => {
    cancelButtonRef.current?.focus();
  }, []);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") close();
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [submitting]);

  async function handleConfirm() {
    if (!user) return;
    setError(null);
    setSubmitting(true);
    try {
      const response = await simulationApi.reset(simulationId, user.token);
      onSuccess(response);
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/60 px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="reset-month-title"
      onClick={(event) => {
        if (event.target === event.currentTarget) close();
      }}
    >
      <div className="relative w-full max-w-[420px] border border-ink/10 bg-panel p-5 shadow-[0_0_0_3px_#211E18] sm:p-8">
        <h3 id="reset-month-title" className="font-display text-xl font-bold text-ink">
          {t("dashboard.resetDialogTitle")}
        </h3>
        <p className="mt-2 font-sans text-sm text-name">{t("dashboard.resetExplanation")}</p>

        {error != null && (
          <div className="mt-4">
            <Banner tone="error">
              {error instanceof ApiError ? error.message : t("common.somethingWentWrong")}
            </Banner>
          </div>
        )}

        <div className="mt-6 flex justify-end gap-3">
          <Button ref={cancelButtonRef} type="button" variant="ink" onClick={close} disabled={submitting}>
            {t("common.cancel")}
          </Button>
          <Button type="button" onClick={handleConfirm} disabled={submitting}>
            {submitting ? t("dashboard.resetting") : t("dashboard.reset")}
          </Button>
        </div>
      </div>
    </div>
  );
}
