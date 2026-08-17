import { useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import { useTranslation } from "react-i18next";
import type { Route } from "./+types/reset-password";
import { AuthShell } from "~/components/AuthShell";
import { Banner, Button, TextField } from "~/components/ui";
import { useAuth } from "~/lib/auth-context";
import { ApiError, authApi } from "~/lib/api";
import i18n from "~/lib/i18n";

export function meta({}: Route.MetaArgs) {
  return [{ title: i18n.t("auth.resetPassword.metaTitle") }];
}

type FormErrorReason = "missingToken" | "passwordMismatch" | "apiError";

export default function ResetPassword() {
  const { t } = useTranslation();
  const { login } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");

  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  // A reason discriminant (plus the raw error for the apiError case) rather
  // than pre-formatted text, so the banner re-resolves through t() on every
  // render, including after a language switch while it's still on screen.
  const [error, setError] = useState<{ reason: FormErrorReason; error?: unknown } | null>(
    token ? null : { reason: "missingToken" }
  );
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!token) return;
    if (newPassword !== confirmPassword) {
      setError({ reason: "passwordMismatch" });
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      const response = await authApi.resetPassword(token, newPassword);
      login(response);
      navigate("/", { replace: true });
    } catch (err) {
      setError({ reason: "apiError", error: err });
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthShell seal="改" eyebrow={t("auth.resetPassword.eyebrow")} title={t("auth.resetPassword.title")}>
      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        {error && (
          <Banner tone="error">
            {error.reason === "missingToken"
              ? t("auth.resetPassword.missingToken")
              : error.reason === "passwordMismatch"
                ? t("auth.resetPassword.passwordMismatch")
                : error.error instanceof ApiError
                  ? error.error.message
                  : t("common.somethingWentWrong")}
          </Banner>
        )}

        <TextField
          id="newPassword"
          label={t("auth.resetPassword.newPasswordLabel")}
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={255}
          disabled={!token}
          value={newPassword}
          onChange={(e) => setNewPassword(e.target.value)}
        />
        <TextField
          id="confirmPassword"
          label={t("auth.resetPassword.confirmPasswordLabel")}
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={255}
          disabled={!token}
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
        />

        <Button type="submit" disabled={submitting || !token}>
          {submitting ? t("auth.resetPassword.resetting") : t("auth.resetPassword.submit")}
        </Button>
      </form>

      <p className="mt-6 text-center font-sans text-sm text-name">
        <Link to="/login" className="text-teal underline underline-offset-2">
          {t("auth.resetPassword.backToLogin")}
        </Link>
      </p>
    </AuthShell>
  );
}
