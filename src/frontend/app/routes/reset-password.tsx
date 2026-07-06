import { useState, type FormEvent } from "react";
import { Link, useNavigate, useSearchParams } from "react-router";
import type { Route } from "./+types/reset-password";
import { AuthShell } from "~/components/AuthShell";
import { Banner, Button, TextField } from "~/components/ui";
import { useAuth } from "~/lib/auth-context";
import { ApiError, authApi } from "~/lib/api";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Reset password — Munehisa" }];
}

export default function ResetPassword() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const token = searchParams.get("token");

  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(
    token ? null : "This reset link is missing its token."
  );
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!token) return;
    if (newPassword !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      const response = await authApi.resetPassword(token, newPassword);
      login(response);
      navigate("/", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthShell seal="改" eyebrow="Account recovery" title="Reset password">
      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        {error && <Banner tone="error">{error}</Banner>}

        <TextField
          id="newPassword"
          label="New password"
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
          label="Confirm new password"
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
          {submitting ? "Resetting…" : "▸▸ Reset password"}
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
