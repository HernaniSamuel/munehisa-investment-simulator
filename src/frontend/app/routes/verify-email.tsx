import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useTranslation } from "react-i18next";
import type { Route } from "./+types/verify-email";
import { AuthShell } from "~/components/AuthShell";
import { Banner, buttonBaseClasses, buttonVariantClasses } from "~/components/ui";
import { useAuth } from "~/lib/auth-context";
import { ApiError, authApi } from "~/lib/api";
import i18n from "~/lib/i18n";

export function meta({}: Route.MetaArgs) {
  return [{ title: i18n.t("auth.verifyEmail.metaTitle") }];
}

type Status = "verifying" | "success" | "error";

export default function VerifyEmail() {
  const { t } = useTranslation();
  const { login } = useAuth();
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");

  const [status, setStatus] = useState<Status>(token ? "verifying" : "error");
  // Raw error data (or null for the "missing token" case, which is derived
  // from `token` directly) rather than pre-formatted text, so the banner
  // re-resolves through t() on every render, including after a language
  // switch while the error is still on screen.
  const [apiError, setApiError] = useState<unknown>(null);

  // The verification token is single-use on the backend: once accepted, it's
  // cleared server-side. React's StrictMode intentionally double-invokes
  // effects in development, which would otherwise fire this request twice
  // and turn a successful verification into a false "token not found" on
  // the second call. Guard with a ref (persists across the double-invoke)
  // so each token value is only ever sent once.
  const requestedToken = useRef<string | null>(null);

  useEffect(() => {
    if (!token || requestedToken.current === token) return;
    requestedToken.current = token;

    authApi
      .verifyEmail(token)
      .then((response) => {
        login(response);
        setStatus("success");
      })
      .catch((err) => {
        setStatus("error");
        setApiError(err);
      });
    // Only run once per token value.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  return (
    <AuthShell seal="状" eyebrow={t("auth.verifyEmail.eyebrow")} title={t("auth.verifyEmail.title")}>
      {status === "verifying" && (
        <p className="font-sans text-name">{t("auth.verifyEmail.verifying")}</p>
      )}

      {status === "success" && (
        <div className="flex flex-col gap-5">
          <Banner tone="success">{t("auth.verifyEmail.success")}</Banner>
          <Link
            to="/"
            className={`${buttonBaseClasses} ${buttonVariantClasses.primary}`}
          >
            {t("auth.verifyEmail.continueButton")}
          </Link>
        </div>
      )}

      {status === "error" && (
        <div className="flex flex-col gap-5">
          <Banner tone="error">
            {!token
              ? t("auth.verifyEmail.missingToken")
              : apiError instanceof ApiError
                ? apiError.message
                : t("auth.verifyEmail.verifyFailedGeneric")}
          </Banner>
          <p className="font-sans text-sm text-name">
            <Link to="/login" className="text-teal underline underline-offset-2">
              {t("auth.verifyEmail.backToLogin")}
            </Link>
          </p>
        </div>
      )}
    </AuthShell>
  );
}
