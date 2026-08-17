import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import type { Route } from "./+types/home";
import { ProtectedRoute } from "~/components/ProtectedRoute";
import {
  Banner,
  Button,
  Select,
  TextField,
  buttonBaseClasses,
  buttonVariantClasses,
} from "~/components/ui";
import { LanguageSwitcher } from "~/components/LanguageSwitcher";
import { ToastStack, type ToastItem } from "~/components/Toast";
import { Tooltip } from "~/components/Tooltip";
import { CurrencyValue } from "~/components/CurrencyValue";
import { useAuth } from "~/lib/auth-context";
import { ApiError, simulationApi, type Simulation } from "~/lib/api";
import { formatCurrency, formatCurrencyExact, formatMonthYear, isAbbreviatedCurrency, truncateName } from "~/lib/format";
import i18n from "~/lib/i18n";

export function meta({}: Route.MetaArgs) {
  return [
    { title: "Munehisa" },
    { name: "description", content: i18n.t("home.metaDescription") },
  ];
}

export default function Home() {
  return (
    <ProtectedRoute>
      <SimulationListScreen />
    </ProtectedRoute>
  );
}

function genToastId(): string {
  return `${Date.now()}-${Math.random()}`;
}

function SimulationListScreen() {
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [simulations, setSimulations] = useState<Simulation[] | null>(null);
  // Raw error data rather than pre-formatted text, so the banner re-resolves
  // through t() on every render, including after a language switch while
  // it's still on screen.
  const [loadError, setLoadError] = useState<unknown>(null);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Simulation | null>(null);
  const [logoutConfirmOpen, setLogoutConfirmOpen] = useState(false);
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  useEffect(() => {
    if (!user) return;
    simulationApi
      .list(user.token)
      .then(setSimulations)
      .catch((err) => {
        setLoadError(err);
      });
  }, [user]);

  function handleLogout() {
    logout();
    navigate("/login", { replace: true });
  }

  function addToast(message: string) {
    setToasts((prev) => [...prev, { id: genToastId(), message }]);
  }

  function dismissToast(id: string) {
    setToasts((prev) => prev.filter((toast) => toast.id !== id));
  }

  function handleCreated(simulation: Simulation) {
    setCreateModalOpen(false);
    navigate(`/simulations/${simulation.id}`);
  }

  function handleRenamed(updated: Simulation) {
    setSimulations((prev) =>
      prev ? prev.map((s) => (s.id === updated.id ? { ...s, name: updated.name } : s)) : prev
    );
  }

  function handleDeleted(simulation: Simulation) {
    setSimulations((prev) => (prev ? prev.filter((s) => s.id !== simulation.id) : prev));
    setDeleteTarget(null);
    addToast(t("home.deletedToast", { name: truncateName(simulation.name) }));
  }

  // "YYYY-MM" YearMonth strings sort lexicographically the same as
  // chronologically, so a plain string comparison is enough here.
  const sortedSimulations = useMemo(
    () =>
      simulations
        ? [...simulations].sort((a, b) => (a.startMonth < b.startMonth ? 1 : a.startMonth > b.startMonth ? -1 : 0))
        : [],
    [simulations]
  );

  const status: "loading" | "error" | "ready" = loadError
    ? "error"
    : simulations === null
      ? "loading"
      : "ready";
  const populated = status === "ready" && sortedSimulations.length > 0;

  return (
    <main className="relative min-h-screen bg-paper px-4 py-10">
      <div className="washi-texture" aria-hidden="true" />
      <div
        className={`relative mx-auto flex flex-col gap-8 ${populated ? "max-w-[1360px]" : "max-w-[640px]"}`}
      >
        <header className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center bg-ink font-display text-xl text-paper">
              蔵
            </div>
            <div>
              <h1 className="font-display text-xl font-bold text-ink">Munehisa</h1>
              <p className="font-mono text-[10px] uppercase tracking-[.2em] text-muted">
                {t("home.tagline")}
              </p>
            </div>
          </div>
          <div className="flex flex-wrap items-center gap-3">
            <LanguageSwitcher />
            {populated && (
              <Button type="button" onClick={() => setCreateModalOpen(true)}>
                {t("home.newSimulation")}
              </Button>
            )}
            <Link to="/settings" className={`${buttonBaseClasses} ${buttonVariantClasses.ink}`}>
              {t("home.settings")}
            </Link>
            <Button variant="ink" onClick={() => setLogoutConfirmOpen(true)}>
              {t("home.logout")}
            </Button>
          </div>
        </header>

        {status === "loading" && (
          <p className="font-mono text-sm text-muted">{t("home.loadingSimulations")}</p>
        )}

        {status === "error" && (
          <Banner tone="error">
            {loadError instanceof ApiError ? loadError.message : t("common.somethingWentWrong")}
          </Banner>
        )}

        {status === "ready" && sortedSimulations.length === 0 && (
          <div className="flex flex-col items-center gap-6 border border-ink/10 bg-panel p-12 text-center shadow-[0_0_0_3px_#211E18]">
            <h2 className="font-display text-2xl font-bold text-ink">
              {t("home.welcome", { name: user?.name })}
            </h2>
            <p className="font-sans text-name">{t("home.emptyStateBody")}</p>
            <Button type="button" onClick={() => setCreateModalOpen(true)}>
              {t("home.newSimulation")}
            </Button>
          </div>
        )}

        {populated && (
          <div className="grid grid-cols-1 gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {sortedSimulations.map((simulation) => (
              <SimulationCard
                key={simulation.id}
                simulation={simulation}
                onRenamed={handleRenamed}
                onRequestDelete={setDeleteTarget}
              />
            ))}
          </div>
        )}
      </div>

      {createModalOpen && (
        <CreateSimulationDialog
          onCancel={() => setCreateModalOpen(false)}
          onCreated={handleCreated}
        />
      )}

      {deleteTarget && (
        <DeleteConfirmDialog
          simulation={deleteTarget}
          onCancel={() => setDeleteTarget(null)}
          onDeleted={handleDeleted}
        />
      )}

      {logoutConfirmOpen && (
        <LogoutConfirmDialog
          onCancel={() => setLogoutConfirmOpen(false)}
          onConfirm={() => {
            setLogoutConfirmOpen(false);
            handleLogout();
          }}
        />
      )}

      <ToastStack toasts={toasts} onDismiss={dismissToast} />
    </main>
  );
}

function SimulationCard({
  simulation,
  onRenamed,
  onRequestDelete,
}: {
  simulation: Simulation;
  onRenamed: (updated: Simulation) => void;
  onRequestDelete: (simulation: Simulation) => void;
}) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const navigate = useNavigate();
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(simulation.name);
  // "blank" or the raw caught error rather than pre-formatted text - see
  // SimulationListScreen's loadError for why.
  const [error, setError] = useState<"blank" | unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const displayName = truncateName(simulation.name);

  useEffect(() => {
    if (editing) inputRef.current?.focus();
  }, [editing]);

  function startEditing() {
    setName(simulation.name);
    setError(null);
    setEditing(true);
  }

  function cancelEditing() {
    setName(simulation.name);
    setError(null);
    setEditing(false);
  }

  async function handleSave(event: FormEvent) {
    event.preventDefault();
    if (!user) return;

    if (!name.trim()) {
      setError("blank");
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      const updated = await simulationApi.rename(simulation.id, name, user.token);
      onRenamed(updated);
      setEditing(false);
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div
      data-testid={`simulation-card-${simulation.id}`}
      className="flex flex-col gap-4 border border-ink/10 bg-panel p-6 shadow-[0_0_0_3px_#211E18]"
    >
      <div className="flex items-start gap-3">
        <div className="flex h-9 w-9 shrink-0 items-center justify-center bg-ink font-display text-base text-paper">
          札
        </div>
        {editing ? (
          <form onSubmit={handleSave} className="flex flex-1 flex-col gap-2">
            <div className="flex items-center gap-2">
              <input
                ref={inputRef}
                aria-label={t("home.simulationNameAria")}
                className="flex-1 border border-ink/15 bg-paper px-2 py-1.5 font-sans text-ink focus:border-vermilion focus:outline-none"
                maxLength={255}
                value={name}
                onChange={(e) => setName(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Escape") cancelEditing();
                }}
                disabled={submitting}
              />
              <button
                type="submit"
                aria-label={t("home.saveNameAria")}
                disabled={submitting}
                className="font-mono text-sm text-ink disabled:opacity-50"
              >
                ✓
              </button>
              <button
                type="button"
                aria-label={t("home.cancelRenameAria")}
                onClick={cancelEditing}
                disabled={submitting}
                className="font-mono text-sm text-vermilion disabled:opacity-50"
              >
                ✕
              </button>
            </div>
            {error != null && (
              <Banner tone="error">
                {error === "blank"
                  ? t("home.nameCannotBeBlank")
                  : error instanceof ApiError
                    ? error.message
                    : t("common.somethingWentWrong")}
              </Banner>
            )}
          </form>
        ) : (
          <div className="flex flex-1 items-center justify-between gap-2">
            {displayName === simulation.name ? (
              <h3 className="font-display text-lg font-bold text-ink">{displayName}</h3>
            ) : (
              <Tooltip label={simulation.name}>
                <h3 className="font-display text-lg font-bold text-ink" tabIndex={0}>
                  {displayName}
                </h3>
              </Tooltip>
            )}
            <button
              type="button"
              aria-label={t("home.editNameAria")}
              onClick={startEditing}
              className="font-mono text-sm text-muted hover:text-ink"
            >
              ✎
            </button>
          </div>
        )}
      </div>

      <div className="grid grid-cols-2 gap-3">
        <div>
          <p className="font-mono text-[10px] uppercase tracking-[.14em] text-muted">{t("home.start")}</p>
          <p className="mt-1 font-display text-sm text-ink">
            {formatMonthYear(simulation.startMonth)}
          </p>
        </div>
        <div>
          <p className="font-mono text-[10px] uppercase tracking-[.14em] text-muted">{t("home.current")}</p>
          <p className="mt-1 font-display text-sm text-ink">
            {formatMonthYear(simulation.currentMonth)}
          </p>
        </div>
      </div>

      <div>
        <p className="font-mono text-[10px] uppercase tracking-[.14em] text-muted">{t("home.cashBalance")}</p>
        <p className="mt-1 font-display text-xl font-bold text-ink">
          <CurrencyValue
            abbreviated={formatCurrency(simulation.cashBalance, simulation.baseCurrency)}
            exact={
              isAbbreviatedCurrency(simulation.cashBalance)
                ? formatCurrencyExact(simulation.cashBalance, simulation.baseCurrency)
                : null
            }
          />
        </p>
      </div>

      <div className="mt-auto flex gap-3 pt-2">
        <Button
          type="button"
          onClick={() => navigate(`/simulations/${simulation.id}`)}
          className="flex-1"
        >
          {t("home.open")}
        </Button>
        <Button type="button" variant="ink" onClick={() => onRequestDelete(simulation)}>
          {t("home.delete")}
        </Button>
      </div>
    </div>
  );
}

function DeleteConfirmDialog({
  simulation,
  onCancel,
  onDeleted,
}: {
  simulation: Simulation;
  onCancel: () => void;
  onDeleted: (simulation: Simulation) => void;
}) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const cancelButtonRef = useRef<HTMLButtonElement>(null);
  const displayName = truncateName(simulation.name);

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
      await simulationApi.remove(simulation.id, user.token);
      onDeleted(simulation);
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
      aria-labelledby="delete-simulation-title"
      onClick={(event) => {
        if (event.target === event.currentTarget) close();
      }}
    >
      <div className="relative w-full max-w-[420px] border border-ink/10 bg-panel p-5 shadow-[0_0_0_3px_#211E18] sm:p-8">
        <h3 id="delete-simulation-title" className="font-display text-xl font-bold text-ink">
          {t("home.deleteDialogTitlePrefix")}
          {displayName === simulation.name ? (
            displayName
          ) : (
            <Tooltip label={simulation.name}>
              <span tabIndex={0}>{displayName}</span>
            </Tooltip>
          )}
          {t("home.deleteDialogTitleSuffix")}
        </h3>
        <p className="mt-2 font-sans text-sm text-name">{t("home.deleteDialogBody")}</p>

        {error != null && (
          <div className="mt-4">
            <Banner tone="error">
              {error instanceof ApiError ? error.message : t("common.somethingWentWrong")}
            </Banner>
          </div>
        )}

        <div className="mt-6 flex justify-end gap-3">
          <Button
            ref={cancelButtonRef}
            type="button"
            variant="primary"
            onClick={close}
            disabled={submitting}
          >
            {t("common.cancel")}
          </Button>
          <Button type="button" variant="solid" onClick={handleConfirm} disabled={submitting}>
            {submitting ? t("home.deleting") : t("home.delete")}
          </Button>
        </div>
      </div>
    </div>
  );
}

function LogoutConfirmDialog({
  onCancel,
  onConfirm,
}: {
  onCancel: () => void;
  onConfirm: () => void;
}) {
  const { t } = useTranslation();
  const cancelButtonRef = useRef<HTMLButtonElement>(null);

  useEffect(() => {
    cancelButtonRef.current?.focus();
  }, []);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") onCancel();
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
  }, [onCancel]);

  return (
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/60 px-4"
      role="dialog"
      aria-modal="true"
      aria-labelledby="logout-confirm-title"
      onClick={(event) => {
        if (event.target === event.currentTarget) onCancel();
      }}
    >
      <div className="relative w-full max-w-[420px] border border-ink/10 bg-panel p-5 shadow-[0_0_0_3px_#211E18] sm:p-8">
        <h3 id="logout-confirm-title" className="font-display text-xl font-bold text-ink">
          {t("home.logoutDialogTitle")}
        </h3>
        <p className="mt-2 font-sans text-sm text-name">{t("home.logoutDialogBody")}</p>

        <div className="mt-6 flex justify-end gap-3">
          <Button ref={cancelButtonRef} type="button" variant="primary" onClick={onCancel}>
            {t("common.cancel")}
          </Button>
          <Button type="button" variant="solid" onClick={onConfirm}>
            {t("home.logoutConfirm")}
          </Button>
        </div>
      </div>
    </div>
  );
}

function CreateSimulationDialog({
  onCancel,
  onCreated,
}: {
  onCancel: () => void;
  onCreated: (simulation: Simulation) => void;
}) {
  const { t } = useTranslation();
  const { user } = useAuth();
  const [name, setName] = useState("");
  const [baseCurrency, setBaseCurrency] = useState<"BRL" | "USD">("BRL");
  const [startMonth, setStartMonth] = useState("");
  // "nameBlank" | "startMonthRequired" or the raw caught error rather than
  // pre-formatted text - see SimulationListScreen's loadError for why.
  const [error, setError] = useState<"nameBlank" | "startMonthRequired" | unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  const nameInputRef = useRef<HTMLInputElement>(null);

  function close() {
    if (submitting) return;
    onCancel();
  }

  useEffect(() => {
    nameInputRef.current?.focus();
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

    if (!name.trim()) {
      setError("nameBlank");
      return;
    }
    if (!startMonth) {
      setError("startMonthRequired");
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      const created = await simulationApi.create({ name, baseCurrency, startMonth }, user.token);
      onCreated(created);
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
      aria-labelledby="create-simulation-title"
      onClick={(event) => {
        if (event.target === event.currentTarget) close();
      }}
    >
      <form
        onSubmit={handleSubmit}
        className="relative w-full max-w-[420px] border border-ink/10 bg-panel p-5 shadow-[0_0_0_3px_#211E18] sm:p-8"
      >
        <h3 id="create-simulation-title" className="font-display text-xl font-bold text-ink">
          {t("home.newSimulationDialogTitle")}
        </h3>

        <div className="mt-4 flex flex-col gap-4">
          {error != null && (
            <Banner tone="error">
              {error === "nameBlank"
                ? t("home.nameCannotBeBlank")
                : error === "startMonthRequired"
                  ? t("home.startMonthRequired")
                  : error instanceof ApiError
                    ? error.message
                    : t("common.somethingWentWrong")}
            </Banner>
          )}

          <TextField
            ref={nameInputRef}
            id="simulation-name"
            label={t("home.nameLabel")}
            type="text"
            required
            maxLength={255}
            value={name}
            onChange={(e) => setName(e.target.value)}
            disabled={submitting}
          />

          <Select
            id="simulation-currency"
            label={t("home.baseCurrencyLabel")}
            value={baseCurrency}
            onChange={(e) => setBaseCurrency(e.target.value as "BRL" | "USD")}
            disabled={submitting}
          >
            <option value="BRL">BRL</option>
            <option value="USD">USD</option>
          </Select>

          <div className="flex flex-col gap-1.5">
            <label
              htmlFor="simulation-start-month"
              className="font-mono text-[10px] uppercase tracking-[.14em] text-muted"
            >
              {t("home.startMonthLabel")}
            </label>
            <input
              id="simulation-start-month"
              type="month"
              required
              value={startMonth}
              onChange={(e) => setStartMonth(e.target.value)}
              disabled={submitting}
              className="border border-ink/15 bg-paper px-3 py-2.5 font-sans text-ink focus:border-vermilion focus:outline-none"
            />
          </div>
        </div>

        <div className="mt-6 flex justify-end gap-3">
          <Button type="button" variant="ink" onClick={close} disabled={submitting}>
            {t("common.cancel")}
          </Button>
          <Button type="submit" disabled={submitting}>
            {submitting ? t("home.creating") : t("home.create")}
          </Button>
        </div>
      </form>
    </div>
  );
}
