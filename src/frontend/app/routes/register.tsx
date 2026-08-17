import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import { useTranslation } from "react-i18next";
import type { Route } from "./+types/register";
import { AuthShell } from "~/components/AuthShell";
import { Banner, Button, PasswordField, TextField } from "~/components/ui";
import { ApiError, authApi } from "~/lib/api";
import i18n from "~/lib/i18n";

export function meta({}: Route.MetaArgs) {
  return [{ title: i18n.t("auth.register.metaTitle") }];
}

export default function Register() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  // "mismatch" or the raw caught error rather than pre-formatted text, so
  // the banner re-resolves through t() on every render, including after a
  // language switch while it's still on screen.
  const [error, setError] = useState<"mismatch" | unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (password !== confirmPassword) {
      setError("mismatch");
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await authApi.register({ name, email, password });
      navigate("/login", {
        state: {
          message: t("auth.register.success"),
        },
      });
    } catch (err) {
      setError(err);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthShell seal="印" eyebrow={t("auth.register.eyebrow")} title={t("auth.register.title")}>
      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        {error != null && (
          <Banner tone="error">
            {error === "mismatch"
              ? t("auth.register.passwordMismatch")
              : error instanceof ApiError
                ? error.message
                : t("common.somethingWentWrong")}
          </Banner>
        )}

        <TextField
          id="name"
          label={t("auth.register.nameLabel")}
          type="text"
          autoComplete="name"
          required
          minLength={1}
          maxLength={255}
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <TextField
          id="email"
          label={t("auth.register.emailLabel")}
          type="email"
          autoComplete="email"
          required
          maxLength={255}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <PasswordField
          id="password"
          label={t("auth.register.passwordLabel")}
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={255}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <PasswordField
          id="confirmPassword"
          label={t("auth.register.confirmPasswordLabel")}
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={255}
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
        />

        <Button type="submit" disabled={submitting}>
          {submitting ? t("auth.register.creatingAccount") : t("auth.register.submit")}
        </Button>
      </form>

      <p className="mt-6 text-center font-sans text-sm text-name">
        {t("auth.register.haveAccount")}{" "}
        <Link to="/login" className="text-teal underline underline-offset-2">
          {t("auth.register.loginLink")}
        </Link>
      </p>
    </AuthShell>
  );
}
