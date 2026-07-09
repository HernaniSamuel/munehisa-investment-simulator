import { useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate, type Location } from "react-router";
import type { Route } from "./+types/login";
import { AuthShell } from "~/components/AuthShell";
import { Banner, Button, TextField } from "~/components/ui";
import { useAuth } from "~/lib/auth-context";
import { ApiError, authApi } from "~/lib/api";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Log in — Munehisa" }];
}

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const state = location.state as { message?: string; from?: Location } | null;
  const redirectTo = state?.from ? `${state.from.pathname}${state.from.search}` : "/";

  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [resendStatus, setResendStatus] = useState<{ tone: "success" | "error"; text: string } | null>(
    null
  );
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
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  async function handleResend() {
    setError(null);
    if (!email) {
      setResendStatus({ tone: "error", text: "Enter your email above first." });
      return;
    }
    setResendStatus(null);
    setResending(true);
    try {
      const result = await authApi.resendVerification(email);
      setResendStatus({
        tone: "success",
        text: result
          ? `A verification email was already sent. Try again after ${new Date(
              result.resendAvailableAt
            ).toLocaleTimeString()}.`
          : "Verification email sent. Check your inbox.",
      });
    } catch (err) {
      setResendStatus({
        tone: "error",
        text: err instanceof ApiError ? err.message : "Could not resend the email.",
      });
    } finally {
      setResending(false);
    }
  }

  return (
    <AuthShell seal="鍵" eyebrow="Welcome back" title="Log in">
      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        {state?.message && <Banner tone="success">{state.message}</Banner>}
        {error && <Banner tone="error">{error}</Banner>}
        {resendStatus && <Banner tone={resendStatus.tone}>{resendStatus.text}</Banner>}

        <TextField
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <TextField
          id="password"
          label="Password"
          type="password"
          autoComplete="current-password"
          required
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />

        <Button type="submit" disabled={submitting}>
          {submitting ? "Signing in…" : "▸▸ Log in"}
        </Button>
      </form>

      <p className="mt-4 flex flex-col items-center gap-2">
        <Link to="/forgot-password" className="font-mono text-[11px] text-teal underline underline-offset-2">
          Forgot your password?
        </Link>
        <button
          type="button"
          onClick={handleResend}
          disabled={resending}
          className="font-mono text-[11px] text-teal underline underline-offset-2 cursor-pointer disabled:cursor-not-allowed disabled:opacity-50"
        >
          {resending ? "Resending verification email…" : "Resend verification email"}
        </button>
      </p>

      <p className="mt-6 text-center font-sans text-sm text-name">
        Don&apos;t have an account?{" "}
        <Link to="/register" className="text-teal underline underline-offset-2">
          Register
        </Link>
      </p>
    </AuthShell>
  );
}
