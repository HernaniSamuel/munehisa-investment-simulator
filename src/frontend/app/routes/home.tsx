import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
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
import { ToastStack, type ToastItem } from "~/components/Toast";
import { useAuth } from "~/lib/auth-context";
import { ApiError, simulationApi, type Simulation } from "~/lib/api";
import { formatCurrency, formatMonthYear } from "~/lib/format";

export function meta({}: Route.MetaArgs) {
  return [
    { title: "Munehisa" },
    { name: "description", content: "Munehisa investment simulator" },
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
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [simulations, setSimulations] = useState<Simulation[] | null>(null);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [deleteTarget, setDeleteTarget] = useState<Simulation | null>(null);
  const [toasts, setToasts] = useState<ToastItem[]>([]);

  useEffect(() => {
    if (!user) return;
    simulationApi
      .list(user.token)
      .then(setSimulations)
      .catch((err) => {
        setLoadError(
          err instanceof ApiError ? err.message : "Something went wrong. Please try again."
        );
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
    setSimulations((prev) => (prev ? [...prev, simulation] : [simulation]));
    setCreateModalOpen(false);
  }

  function handleRenamed(updated: Simulation) {
    setSimulations((prev) =>
      prev ? prev.map((s) => (s.id === updated.id ? { ...s, name: updated.name } : s)) : prev
    );
  }

  function handleDeleted(simulation: Simulation) {
    setSimulations((prev) => (prev ? prev.filter((s) => s.id !== simulation.id) : prev));
    setDeleteTarget(null);
    addToast(`"${simulation.name}" was permanently deleted.`);
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
        <header className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center bg-ink font-display text-xl text-paper">
              蔵
            </div>
            <div>
              <h1 className="font-display text-xl font-bold text-ink">Munehisa</h1>
              <p className="font-mono text-[10px] uppercase tracking-[.2em] text-muted">
                Investment simulator
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            {populated && (
              <Button type="button" onClick={() => setCreateModalOpen(true)}>
                New Simulation
              </Button>
            )}
            <Link to="/settings" className={`${buttonBaseClasses} ${buttonVariantClasses.ink}`}>
              Settings
            </Link>
            <Button variant="ink" onClick={handleLogout}>
              Log out
            </Button>
          </div>
        </header>

        {status === "loading" && (
          <p className="font-mono text-sm text-muted">Loading simulations…</p>
        )}

        {status === "error" && <Banner tone="error">{loadError}</Banner>}

        {status === "ready" && sortedSimulations.length === 0 && (
          <div className="flex flex-col items-center gap-6 border border-ink/10 bg-panel p-12 text-center shadow-[0_0_0_3px_#211E18]">
            <h2 className="font-display text-2xl font-bold text-ink">Welcome, {user?.name}</h2>
            <p className="font-sans text-name">
              You don&apos;t have any simulations yet. Create one to get started.
            </p>
            <Button type="button" onClick={() => setCreateModalOpen(true)}>
              New Simulation
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
  const { user } = useAuth();
  const navigate = useNavigate();
  const [editing, setEditing] = useState(false);
  const [name, setName] = useState(simulation.name);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);

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
      setError("Name cannot be blank.");
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      const updated = await simulationApi.rename(simulation.id, name, user.token);
      onRenamed(updated);
      setEditing(false);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
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
                aria-label="Simulation name"
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
                aria-label="Save name"
                disabled={submitting}
                className="font-mono text-sm text-ink disabled:opacity-50"
              >
                ✓
              </button>
              <button
                type="button"
                aria-label="Cancel rename"
                onClick={cancelEditing}
                disabled={submitting}
                className="font-mono text-sm text-vermilion disabled:opacity-50"
              >
                ✕
              </button>
            </div>
            {error && <Banner tone="error">{error}</Banner>}
          </form>
        ) : (
          <div className="flex flex-1 items-center justify-between gap-2">
            <h3 className="font-display text-lg font-bold text-ink">{simulation.name}</h3>
            <button
              type="button"
              aria-label="Edit name"
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
          <p className="font-mono text-[10px] uppercase tracking-[.14em] text-muted">Start</p>
          <p className="mt-1 font-display text-sm text-ink">
            {formatMonthYear(simulation.startMonth)}
          </p>
        </div>
        <div>
          <p className="font-mono text-[10px] uppercase tracking-[.14em] text-muted">Current</p>
          <p className="mt-1 font-display text-sm text-ink">
            {formatMonthYear(simulation.currentMonth)}
          </p>
        </div>
      </div>

      <div>
        <p className="font-mono text-[10px] uppercase tracking-[.14em] text-muted">Cash balance</p>
        <p className="mt-1 font-display text-xl font-bold text-ink">
          {formatCurrency(simulation.cashBalance, simulation.baseCurrency)}
        </p>
      </div>

      <div className="mt-auto flex gap-3 pt-2">
        <Button
          type="button"
          onClick={() => navigate(`/simulations/${simulation.id}`)}
          className="flex-1"
        >
          Open
        </Button>
        <Button type="button" variant="ink" onClick={() => onRequestDelete(simulation)}>
          Delete
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
  const { user } = useAuth();
  const [error, setError] = useState<string | null>(null);
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
      await simulationApi.remove(simulation.id, user.token);
      onDeleted(simulation);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
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
      <div className="relative w-full max-w-[420px] border border-ink/10 bg-panel p-8 shadow-[0_0_0_3px_#211E18]">
        <h3 id="delete-simulation-title" className="font-display text-xl font-bold text-ink">
          Delete &quot;{simulation.name}&quot;?
        </h3>
        <p className="mt-2 font-sans text-sm text-name">
          This permanently deletes this simulation and cannot be undone.
        </p>

        {error && (
          <div className="mt-4">
            <Banner tone="error">{error}</Banner>
          </div>
        )}

        <div className="mt-6 flex justify-end gap-3">
          <Button
            ref={cancelButtonRef}
            type="button"
            variant="ink"
            onClick={close}
            disabled={submitting}
          >
            Cancel
          </Button>
          <Button type="button" onClick={handleConfirm} disabled={submitting}>
            {submitting ? "Deleting…" : "Delete"}
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
  const { user } = useAuth();
  const [name, setName] = useState("");
  const [baseCurrency, setBaseCurrency] = useState<"BRL" | "USD">("BRL");
  const [startMonth, setStartMonth] = useState("");
  const [error, setError] = useState<string | null>(null);
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
      setError("Name cannot be blank.");
      return;
    }
    if (!startMonth) {
      setError("Start month is required.");
      return;
    }

    setError(null);
    setSubmitting(true);
    try {
      const created = await simulationApi.create({ name, baseCurrency, startMonth }, user.token);
      onCreated(created);
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
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
        className="relative w-full max-w-[420px] border border-ink/10 bg-panel p-8 shadow-[0_0_0_3px_#211E18]"
      >
        <h3 id="create-simulation-title" className="font-display text-xl font-bold text-ink">
          New simulation
        </h3>

        <div className="mt-4 flex flex-col gap-4">
          {error && <Banner tone="error">{error}</Banner>}

          <TextField
            ref={nameInputRef}
            id="simulation-name"
            label="Name"
            type="text"
            required
            maxLength={255}
            value={name}
            onChange={(e) => setName(e.target.value)}
            disabled={submitting}
          />

          <Select
            id="simulation-currency"
            label="Base currency"
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
              Start month
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
            Cancel
          </Button>
          <Button type="submit" disabled={submitting}>
            {submitting ? "Creating…" : "Create"}
          </Button>
        </div>
      </form>
    </div>
  );
}
