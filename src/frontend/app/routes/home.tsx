import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router";
import type { Route } from "./+types/home";
import { ProtectedRoute } from "~/components/ProtectedRoute";
import { Button, buttonBaseClasses, buttonVariantClasses } from "~/components/ui";
import { useAuth } from "~/lib/auth-context";
import { authApi } from "~/lib/api";

export function meta({}: Route.MetaArgs) {
  return [
    { title: "Munehisa" },
    { name: "description", content: "Munehisa investment simulator" },
  ];
}

export default function Home() {
  return (
    <ProtectedRoute>
      <Dashboard />
    </ProtectedRoute>
  );
}

function Dashboard() {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const [sessionStatus, setSessionStatus] = useState<"checking" | "ok" | "failed">(
    "checking"
  );

  useEffect(() => {
    if (!user) return;
    // A 401/403 here is already handled globally (api.ts's unauthorized
    // handler logs out and ProtectedRoute redirects away before this state
    // would ever paint) - "failed" in practice means a network/server
    // error, not a rejected session.
    authApi
      .checkSession(user.token)
      .then(() => setSessionStatus("ok"))
      .catch(() => setSessionStatus("failed"));
  }, [user]);

  function handleLogout() {
    logout();
    navigate("/login", { replace: true });
  }

  return (
    <main className="relative min-h-screen bg-paper px-4 py-10">
      <div className="washi-texture" aria-hidden="true" />
      <div className="relative mx-auto flex max-w-[640px] flex-col gap-8">
        <header className="flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-11 w-11 items-center justify-center bg-ink font-display text-xl text-paper">
              蔵
            </div>
            <div>
              <h1 className="font-display text-xl font-bold text-ink">Munehisa</h1>
              <p className="font-mono text-[10px] uppercase tracking-[.2em] text-muted">
                Investment cockpit
              </p>
            </div>
          </div>
          <div className="flex items-center gap-3">
            <Link to="/settings" className={`${buttonBaseClasses} ${buttonVariantClasses.ink}`}>
              Settings
            </Link>
            <Button variant="ink" onClick={handleLogout}>
              Log out
            </Button>
          </div>
        </header>

        <div className="border border-ink/10 bg-panel p-8 shadow-[0_0_0_3px_#211E18]">
          <h2 className="font-display text-2xl font-bold text-ink">
            Welcome, {user?.name}
          </h2>
          <p className="mt-2 font-sans text-name">
            You are signed in. The investment simulator screens live in a
            separate module built after this authentication flow.
          </p>

          <div className="mt-6 border-t border-ink/10 pt-4">
            <p className="font-mono text-[10px] uppercase tracking-[.14em] text-muted">
              Authenticated backend request
            </p>
            <p className="mt-1 font-mono text-sm text-ink">
              {sessionStatus === "checking" && "Checking session against the API…"}
              {sessionStatus === "ok" && "GET /user → 200 (JWT accepted)"}
              {sessionStatus === "failed" && "GET /user failed - network or server error"}
            </p>
          </div>
        </div>
      </div>
    </main>
  );
}
