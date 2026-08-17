import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate, type Location } from "react-router";
import { useTranslation } from "react-i18next";
import type { Route } from "./+types/login";
import { AuthShell } from "~/components/AuthShell";
import { Banner, Button, PasswordField, TextField } from "~/components/ui";
import { useAuth } from "~/lib/auth-context";
import { ApiError, authApi } from "~/lib/api";
import i18n from "~/lib/i18n";

export function meta({}: Route.MetaArgs) {
  return [{ title: i18n.t("auth.login.metaTitle") }];
}

export default function Login() {
  const { t } = useTranslation();
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const state = location.state as { message?: string; from?: Location } | null;
  const redirectTo = state?.from ? `${state.from.pathname}${state.from.search}` : "/";

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  // Raw error/result data rather than pre-formatted text, so the banner
  // below re-resolves through t() on every render - including after a
  // language switch, while the error/status itself is still on screen.
  const [error, setError] = useState<unknown>(null);
  const [resendStatus, setResendStatus] = useState<
    | { tone: "success"; alreadySent: false }
    | { tone: "success"; alreadySent: true; resendAvailableAt: string }
    | { tone: "error"; reason: "missingEmail" | "apiError"; error?: unknown }
    | null
  >(null);
  const [submitting, setSubmitting] = useState(false);
  const [resending, setResending] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setResendStatus(null);
    setSubmitting(true);
    try {
      const response = await authApi.login({ email, password });
      login(response);
      navigate(redirectTo, { replace: true });
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  async function handleResend() {
    setError(null);
    if (!email) {
      setResendStatus({ tone: "error", reason: "missingEmail" });
      return;
    }
    setResendStatus(null);
    setResending(true);
    try {
      const result = await authApi.resendVerification(email);
      setResendStatus(
        result
          ? { tone: "success", alreadySent: true, resendAvailableAt: result.resendAvailableAt }
          : { tone: "success", alreadySent: false }
      );
    } catch (err) {
      setResendStatus({ tone: "error", reason: "apiError", error: err });
    } finally {
      setResending(false);
    }
  }

  return (
    <AuthShell seal="鍵" eyebrow={t("auth.login.eyebrow")} title={t("auth.login.title")}>
      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        {state?.message && <Banner tone="success">{state.message}</Banner>}
        {error != null && (
          <Banner tone="error">
            {error instanceof ApiError ? error.message : t("common.somethingWentWrong")}
          </Banner>
        )}
        {resendStatus && (
          <Banner tone={resendStatus.tone}>
            {resendStatus.tone === "success"
              ? resendStatus.alreadySent
                ? t("auth.login.resendAlreadySent", {
                    time: new Date(resendStatus.resendAvailableAt).toLocaleTimeString(),
                  })
                : t("auth.login.resendSuccess")
              : resendStatus.reason === "missingEmail"
                ? t("auth.login.enterEmailFirst")
                : resendStatus.error instanceof ApiError
                  ? resendStatus.error.message
                  : t("auth.login.resendFailedGeneric")}
          </Banner>
        )}

        <TextField
          id="email"
          label={t("auth.login.emailLabel")}
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <PasswordField
          id="password"
          label={t("auth.login.passwordLabel")}
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />

        <Button type="submit" disabled={submitting}>
          {submitting ? t("auth.login.signingIn") : t("auth.login.submit")}
        </Button>
      </form>

      <p className="mt-4 flex flex-col items-center gap-2">
        <Link to="/forgot-password" className="font-mono text-[11px] text-teal underline underline-offset-2">
          {t("auth.login.forgotPassword")}
        </Link>
        <button
          type="button"
          onClick={handleResend}
          disabled={resending}
          className="font-mono text-[11px] text-teal underline underline-offset-2 cursor-pointer disabled:cursor-not-allowed disabled:opacity-50"
        >
          {resending ? t("auth.login.resendingVerification") : t("auth.login.resendVerification")}
        </button>
      </p>

      <p className="mt-6 text-center font-sans text-sm text-name">
        {t("auth.login.noAccount")}{" "}
        <Link to="/register" className="text-teal underline underline-offset-2">
          {t("auth.login.registerLink")}
        </Link>
      </p>
    </AuthShell>
  );
}
