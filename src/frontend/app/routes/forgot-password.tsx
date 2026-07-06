import { useState, type FormEvent } from "react";
import { Link } from "react-router";
import type { Route } from "./+types/forgot-password";
import { AuthShell } from "~/components/AuthShell";
import { Banner, Button, TextField } from "~/components/ui";
import { ApiError, authApi } from "~/lib/api";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Forgot password — Munehisa" }];
}

export default function ForgotPassword() {
  const [email, setEmail] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
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
          ? `A reset email was already sent. Try again after ${new Date(
              result.resendAvailableAt
            ).toLocaleTimeString()}.`
          : "If that email has a verified account, a reset link is on its way."
      );
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthShell seal="問" eyebrow="Account recovery" title="Forgot password">
      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        {error && <Banner tone="error">{error}</Banner>}
        {success && <Banner tone="success">{success}</Banner>}

        <TextField
          id="email"
          label="Email"
          type="email"
          autoComplete="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />

        <Button type="submit" disabled={submitting}>
          {submitting ? "Sending…" : "▸▸ Send reset link"}
        </Button>
      </form>

      <p className="mt-6 text-center font-sans text-sm text-name">
        <Link to="/login" className="text-teal underline underline-offset-2">
          Back to log in
        </Link>
      </p>
    </AuthShell>
  );
}
