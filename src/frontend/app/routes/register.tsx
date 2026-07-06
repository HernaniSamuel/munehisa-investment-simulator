import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router";
import type { Route } from "./+types/register";
import { AuthShell } from "~/components/AuthShell";
import { Banner, Button, TextField } from "~/components/ui";
import { ApiError, authApi } from "~/lib/api";

export function meta({}: Route.MetaArgs) {
  return [{ title: "Register — Munehisa" }];
}

export default function Register() {
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (password !== confirmPassword) {
      setError("Passwords do not match.");
      return;
    }
    setError(null);
    setSubmitting(true);
    try {
      await authApi.register({ name, email, password });
      navigate("/login", {
        state: {
          message: "Account created. Check your inbox to verify your email before signing in.",
        },
      });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Something went wrong. Please try again.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <AuthShell seal="印" eyebrow="New account" title="Register">
      <form onSubmit={handleSubmit} className="flex flex-col gap-5">
        {error && <Banner tone="error">{error}</Banner>}

        <TextField
          id="name"
          label="Name"
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
          label="Email"
          type="email"
          autoComplete="email"
          required
          maxLength={255}
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <TextField
          id="password"
          label="Password"
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={255}
          value={password}
          onChange={(e) => setPassword(e.target.value)}
        />
        <TextField
          id="confirmPassword"
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          required
          minLength={8}
          maxLength={255}
          value={confirmPassword}
          onChange={(e) => setConfirmPassword(e.target.value)}
        />

        <Button type="submit" disabled={submitting}>
          {submitting ? "Creating account…" : "▸▸ Create account"}
        </Button>
      </form>

      <p className="mt-6 text-center font-sans text-sm text-name">
        Already have an account?{" "}
        <Link to="/login" className="text-teal underline underline-offset-2">
          Log in
        </Link>
      </p>
    </AuthShell>
  );
}
