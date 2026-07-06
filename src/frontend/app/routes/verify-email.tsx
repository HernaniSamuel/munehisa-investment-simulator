import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router";
import type { Route } from "./+types/verify-email";
import { AuthShell } from "~/components/AuthShell";
import { Banner, buttonBaseClasses, buttonVariantClasses } from "~/components/ui";
import { useAuth } from "~/lib/auth-context";
import { ApiError, authApi } from "~/lib/api";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Verify email — Munehisa" }];
}

type Status = "verifying" | "success" | "error";

export default function VerifyEmail() {
  const { login } = useAuth();
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");

  const [status, setStatus] = useState<Status>(token ? "verifying" : "error");
  const [message, setMessage] = useState<string | null>(
    token ? null : "This verification link is missing its token."
  );

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
        setMessage(err instanceof ApiError ? err.message : "Could not verify this email.");
      });
    // Only run once per token value.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [token]);

  return (
    <AuthShell seal="状" eyebrow="Email verification" title="Verify email">
      {status === "verifying" && (
        <p className="font-sans text-name">Verifying your email…</p>
      )}

      {status === "success" && (
        <div className="flex flex-col gap-5">
          <Banner tone="success">Your email has been verified.</Banner>
          <Link
            to="/"
            className={`${buttonBaseClasses} ${buttonVariantClasses.primary}`}
          >
            ▸▸ Continue
          </Link>
        </div>
      )}

      {status === "error" && (
        <div className="flex flex-col gap-5">
          <Banner tone="error">{message}</Banner>
          <p className="font-sans text-sm text-name">
            <Link to="/login" className="text-teal underline underline-offset-2">
              Back to log in
            </Link>
          </p>
        </div>
      )}
    </AuthShell>
  );
}
