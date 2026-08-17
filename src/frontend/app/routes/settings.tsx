import { useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import type { Route } from "./+types/settings";
import { ProtectedRoute } from "~/components/ProtectedRoute";
import { Banner, Button, TextField, buttonBaseClasses, buttonVariantClasses } from "~/components/ui";
import { LanguageSwitcher } from "~/components/LanguageSwitcher";
import { useAuth } from "~/lib/auth-context";
import { useTheme } from "~/lib/theme-context";
import { ApiError, authApi, userApi } from "~/lib/api";
import i18n from "~/lib/i18n";

export function meta({}: Route.MetaArgs) {
  return [{ title: i18n.t("settings.metaTitle") }];
}

export default function Settings() {
  return (
    <ProtectedRoute>
      <SettingsScreen />
    </ProtectedRoute>
  );
}

function SettingsScreen() {
  const { t } = useTranslation();
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
              <h1 className="font-display text-xl font-bold text-ink">{t("settings.heading")}</h1>
              <p className="font-mono text-[10px] uppercase tracking-[.2em] text-muted">
                {t("settings.account")}
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <LanguageSwitcher />
            <Link to="/" className={`${buttonBaseClasses} ${buttonVariantClasses.ink}`}>
              {t("settings.back")}
            </Link>
          </div>
        </header>

        <ChangeNameSection />
        <ThemeSection />
        <ChangePasswordSection email={email} />
        <DeleteAccountSection />
      </div>
    </main>
  );
}

function ChangeNameSection() {
  const { t } = useTranslation();
  const { user, login } = useAuth();
  const [name, setName] = useState(user?.name ?? "");
  // "blank" or the raw caught error rather than pre-formatted text, plus a
  // separate success flag, so the banner re-resolves through t() on every
  // render, including after a language switch while it's still on screen.
  const [error, setError] = useState<"blank" | unknown>(null);
  const [success, setSuccess] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!user) return;

    if (!name.trim()) {
      setError("blank");
      setSuccess(false);
      return;
    }

    setError(null);
    setSuccess(false);
    setSubmitting(true);
    try {
      const result = await userApi.updateName(name, user.token);
      login({ name: result.name, token: user.token });
      setName(result.name);
      setSuccess(true);
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="border border-ink/10 bg-panel p-8 shadow-[0_0_0_3px_#211E18]">
      <h2 className="font-display text-xl font-bold text-ink">{t("settings.nameSectionTitle")}</h2>

      <form onSubmit={handleSubmit} className="mt-4 flex flex-col gap-4">
        {error != null && (
          <Banner tone="error">
            {error === "blank"
              ? t("settings.nameCannotBlank")
              : error instanceof ApiError
                ? error.message
                : t("common.somethingWentWrong")}
          </Banner>
        )}
        {success && <Banner tone="success">{t("settings.nameUpdated")}</Banner>}

        <TextField
          id="name"
          label={t("settings.nameLabel")}
          type="text"
          autoComplete="name"
          required
          maxLength={255}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />

        <Button type="submit" disabled={submitting} className="self-start">
          {submitting ? t("settings.saving") : t("settings.saveName")}
        </Button>
      </form>
    </section>
  );
}

function ThemeSection() {
  const { t } = useTranslation();
  const { theme, setTheme } = useTheme();

  return (
    <section className="border border-ink/10 bg-panel p-8 shadow-[0_0_0_3px_#211E18]">
      <h2 className="font-display text-xl font-bold text-ink">{t("settings.themeTitle")}</h2>
      <p className="mt-2 font-sans text-sm text-name">{t("settings.themeDescription")}</p>

      <div className="mt-4 flex gap-3" role="group" aria-label={t("settings.themeTitle")}>
        <Button
          type="button"
          variant={theme === "sumi" ? "primary" : "ink"}
          aria-pressed={theme === "sumi"}
          onClick={() => setTheme("sumi")}
        >
          {t("settings.sumi")}
        </Button>
        <Button
          type="button"
          variant={theme === "zankyo" ? "primary" : "ink"}
          aria-pressed={theme === "zankyo"}
          onClick={() => setTheme("zankyo")}
        >
          {t("settings.zankyo")}
        </Button>
      </div>
    </section>
  );
}

function ChangePasswordSection({ email }: { email: string | null }) {
  const { t } = useTranslation();
  // Raw error/result data rather than pre-formatted text, so the banner
  // re-resolves through t() on every render, including after a language
  // switch while it's still on screen.
  const [error, setError] = useState<unknown>(null);
  const [success, setSuccess] = useState<
    { alreadySent: false } | { alreadySent: true; resendAvailableAt: string } | null
  >(null);
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
          ? { alreadySent: true, resendAvailableAt: result.resendAvailableAt }
          : { alreadySent: false }
      );
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="border border-ink/10 bg-panel p-8 shadow-[0_0_0_3px_#211E18]">
      <h2 className="font-display text-xl font-bold text-ink">{t("settings.passwordTitle")}</h2>
      <p className="mt-2 font-sans text-sm text-name">{t("settings.passwordDescription")}</p>

      <div className="mt-4 flex flex-col gap-4">
        {error != null && (
          <Banner tone="error">
            {error instanceof ApiError ? error.message : t("common.somethingWentWrong")}
          </Banner>
        )}
        {success && (
          <Banner tone="success">
            {success.alreadySent
              ? t("settings.resendAlreadySent", {
                  time: new Date(success.resendAvailableAt).toLocaleTimeString(),
                })
              : t("settings.passwordCheckInbox")}
          </Banner>
        )}

        <Button
          type="button"
          variant="secondary"
          onClick={handleClick}
          disabled={submitting || !email}
          className="self-start"
        >
          {submitting ? t("settings.sendingReset") : t("settings.sendResetEmail")}
        </Button>
      </div>
    </section>
  );
}

function DeleteAccountSection() {
  const { t } = useTranslation();
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [modalOpen, setModalOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);
  // Starts true and is cleared on the field's first focus. Chromium-family
  // browsers skip a readonly field when deciding whether to show their
  // saved-credentials suggestion dropdown, so this blocks that decision at
  // the moment the field is auto-focused - autoComplete="new-password" alone
  // isn't enough in some of them (e.g. Brave), even though it's the
  // documented cross-browser signal for "this isn't a login field".
  const [passwordReadOnly, setPasswordReadOnly] = useState(true);
  const passwordInputRef = useRef<HTMLInputElement>(null);

  function openModal() {
    setPassword("");
    setError(null);
    setPasswordReadOnly(true);
    setModalOpen(true);
  }

  function closeModal() {
    if (submitting) return;
    setModalOpen(false);
  }

  useEffect(() => {
    if (modalOpen) passwordInputRef.current?.focus();
  }, [modalOpen]);

  useEffect(() => {
    if (!modalOpen) return;
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === "Escape") closeModal();
    }
    document.addEventListener("keydown", handleKeyDown);
    return () => document.removeEventListener("keydown", handleKeyDown);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [modalOpen, submitting]);

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
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="border border-vermilion/30 bg-panel p-8 shadow-[0_0_0_3px_#211E18]">
      <h2 className="font-display text-xl font-bold text-ink">{t("settings.deleteAccountTitle")}</h2>
      <p className="mt-2 font-sans text-sm text-name">{t("settings.deleteAccountDescription")}</p>

      <Button type="button" onClick={openModal} className="mt-4">
        {t("settings.deleteAccountTitle")}
      </Button>

      {modalOpen && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-ink/60 px-4"
          role="dialog"
          aria-modal="true"
          aria-labelledby="delete-account-title"
          onClick={(event) => {
            if (event.target === event.currentTarget) closeModal();
          }}
        >
          <form
            onSubmit={handleConfirm}
            className="relative w-full max-w-[420px] border border-ink/10 bg-panel p-5 shadow-[0_0_0_3px_#211E18] sm:p-8"
          >
            <h3 id="delete-account-title" className="font-display text-xl font-bold text-ink">
              {t("settings.confirmDeletionTitle")}
            </h3>
            <p className="mt-2 font-sans text-sm text-name">{t("settings.confirmDeletionBody")}</p>

            {error != null && (
              <div className="mt-4">
                <Banner tone="error">
                  {error instanceof ApiError ? error.message : t("common.somethingWentWrong")}
                </Banner>
              </div>
            )}

            <div className="mt-4">
              <TextField
                ref={passwordInputRef}
                id="delete-password"
                label={t("settings.passwordFieldLabel")}
                type="password"
                autoComplete="new-password"
                readOnly={passwordReadOnly}
                onFocus={() => setPasswordReadOnly(false)}
                required
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>

            <div className="mt-6 flex justify-end gap-3">
              <Button type="button" variant="primary" onClick={closeModal} disabled={submitting}>
                {t("common.cancel")}
              </Button>
              <Button type="submit" variant="solid" disabled={submitting || !password}>
                {submitting ? t("settings.deleting") : t("settings.deleteAccountConfirm")}
              </Button>
            </div>
          </form>
        </div>
      )}
    </section>
  );
}
