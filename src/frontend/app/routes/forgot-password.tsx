import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import { useTranslation } from "react-i18next";
import type { Route } from "./+types/forgot-password";
import { AuthShell } from "~/components/AuthShell";
import { Banner, Button, TextField } from "~/components/ui";
import { ApiError, authApi } from "~/lib/api";
import i18n from "~/lib/i18n";

export function meta({}: Route.MetaArgs) {
  return [{ title: i18n.t("auth.forgotPassword.metaTitle") }];
}

export default function ForgotPassword() {
  const { t } = useTranslation();
  const [email, setEmail] = useState("");
  // Raw error data / a discriminant for the success case rather than
  // pre-formatted text, so the banner re-resolves through t() on every
  // render, including after a language switch while it's still on screen.
  const [error, setError] = useState<unknown>(null);
  const [success, setSuccess] = useState<{ alreadySent: false } | { alreadySent: true; resendAvailableAt: string } | null>(
    null
  );
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
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
    <AuthShell seal="問" eyebrow={t("auth.forgotPassword.eyebrow")} title={t("auth.forgotPassword.title")}>
      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        {error != null && (
          <Banner tone="error">
            {error instanceof ApiError ? error.message : t("common.somethingWentWrong")}
          </Banner>
        )}
        {success && (
          <Banner tone="success">
            {success.alreadySent
              ? t("auth.forgotPassword.resendAlreadySent", {
                  time: new Date(success.resendAvailableAt).toLocaleTimeString(),
                })
              : t("auth.forgotPassword.successGeneric")}
          </Banner>
        )}

        <TextField
          id="email"
          label={t("auth.forgotPassword.emailLabel")}
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />

        <Button type="submit" disabled={submitting}>
          {submitting ? t("auth.forgotPassword.sending") : t("auth.forgotPassword.submit")}
        </Button>
      </form>

      <p className="mt-6 text-center font-sans text-sm text-name">
        <Link to="/login" className="text-teal underline underline-offset-2">
          {t("auth.forgotPassword.backToLogin")}
        </Link>
      </p>
    </AuthShell>
  );
}
