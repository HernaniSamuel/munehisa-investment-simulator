import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import type { Route } from "./+types/settings";
import { ProtectedRoute } from "~/components/ProtectedRoute";
import { Banner, Button, TextField, buttonBaseClasses, buttonVariantClasses } from "~/components/ui";
import { useAuth } from "~/lib/auth-context";
import { ApiError, authApi, userApi } from "~/lib/api";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Settings — Munehisa" }];
}

export default function Settings() {
  return (
    <ProtectedRoute>
      <SettingsScreen />
    </ProtectedRoute>
  );
}

function SettingsScreen() {
  const { email } = useAuth();

  return (
    <main className="relative min-h-screen bg-paper px-4 py-10">
      <div className="washi-texture" aria-hidden="true" />
      <div className="relative mx-auto flex max-w-[640px] flex-col gap-8">
        <header className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center bg-ink font-display text-xl text-paper">
              設
            </div>
            <div>
              <h1 className="font-display text-xl font-bold text-ink">Settings</h1>
              <p className="font-mono text-[10px] uppercase tracking-[.2em] text-muted">
                Account
              </p>
            </div>
          </div>
          <Link to="/" className={`${buttonBaseClasses} ${buttonVariantClasses.ink}`}>
            ← Back
          </Link>
        </header>

        <ChangeNameSection />
        <ChangePasswordSection email={email} />
        <DeleteAccountSection />
      </div>
    </main>
  );
}

function ChangeNameSection() {
  const { user, login } = useAuth();
  const [name, setName] = useState(user?.name ?? "");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!user) return;

    if (!name.trim()) {
      setError("Name cannot be blank.");
      setSuccess(null);
      return;
    }

    setError(null);
    setSuccess(null);
    setSubmitting(true);
    try {
      const result = await userApi.updateName(name, user.token);
      login({ name: result.name, token: user.token });
      setName(result.name);
      setSuccess("Name updated.");
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="border border-ink/10 bg-panel p-8 shadow-[0_0_0_3px_#211E18]">
      <h2 className="font-display text-xl font-bold text-ink">Name</h2>

      <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-4">
        {error && <Banner tone="error">{error}</Banner>}
        {success && <Banner tone="success">{success}</Banner>}

        <TextField
          id="name"
          label="Name"
          type="text"
          autoComplete="name"
          required
          maxLength={255}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />

        <Button type="submit" disabled={submitting} className="self-start">
          {submitting ? "Saving…" : "▸▸ Save name"}
        </Button>
      </form>
    </section>
  );
}

function ChangePasswordSection({ email }: { email: string | null }) {
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleClick() {
    if (!email) return;
    setError(null);
    setSuccess(null);
    setSubmitting(true);
    try {
      const result = await authApi.forgotPassword(email);
      setSuccess(
        result
          ? `A reset email was already sent. Try again after ${new Date(
              result.resendAvailableAt
            ).toLocaleTimeString()}.`
          : "Check your inbox for a link to reset your password."
      );
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="border border-ink/10 bg-panel p-8 shadow-[0_0_0_3px_#211E18]">
      <h2 className="font-display text-xl font-bold text-ink">Password</h2>
      <p className="mt-2 font-sans text-sm text-name">
        We&apos;ll email you a link to set a new password.
      </p>

      <div className="mt-4 flex flex-col gap-4">
        {error && <Banner tone="error">{error}</Banner>}
        {success && <Banner tone="success">{success}</Banner>}

        <Button
          type="button"
          variant="secondary"
          onClick={handleClick}
          disabled={submitting || !email}
          className="self-start"
        >
          {submitting ? "Sending…" : "▸▸ Send password reset email"}
        </Button>
      </div>
    </section>
  );
}

function DeleteAccountSection() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [modalOpen, setModalOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  function openModal() {
    setPassword("");
    setError(null);
    setModalOpen(true);
  }

  function closeModal() {
    if (submitting) return;
    setModalOpen(false);
  }

  async function handleConfirm(event: FormEvent) {
    event.preventDefault();
    if (!user || !password) return;

    setError(null);
    setSubmitting(true);
    try {
      await userApi.deleteAccount(password, user.token);
      logout();
      navigate("/login", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="border border-vermilion/30 bg-panel p-8 shadow-[0_0_0_3px_#211E18]">
      <h2 className="font-display text-xl font-bold text-ink">Delete account</h2>
      <p className="mt-2 font-sans text-sm text-name">
        This permanently deletes your account and cannot be undone.
      </p>

      <Button type="button" onClick={openModal} className="mt-4">
        Delete account
      </Button>

      {modalOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-ink/60 px-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="delete-account-title"
        >
          <form
            onSubmit={handleConfirm}
            className="relative w-full max-w-[420px] border border-ink/10 bg-panel p-8 shadow-[0_0_0_3px_#211E18]"
          >
            <h3 id="delete-account-title" className="font-display text-xl font-bold text-ink">
              Confirm deletion
            </h3>
            <p className="mt-2 font-sans text-sm text-name">
              Enter your password to permanently delete your account.
            </p>

            {error && (
              <div className="mt-4">
                <Banner tone="error">{error}</Banner>
              </div>
            )}

            <div className="mt-4">
              <TextField
                id="delete-password"
                label="Password"
                type="password"
                autoComplete="current-password"
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>

            <div className="mt-6 flex justify-end gap-3">
              <Button type="button" variant="ink" onClick={closeModal} disabled={submitting}>
                Cancel
              </Button>
              <Button type="submit" disabled={submitting || !password}>
                {submitting ? "Deleting…" : "Delete account"}
              </Button>
            </div>
          </form>
        </div>
      )}
    </section>
  );
}
