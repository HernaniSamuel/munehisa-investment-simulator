import type { ReactNode } from "react";
import { Navigate, useLocation } from "react-router";
import { useAuth } from "~/lib/auth-context";

export function ProtectedRoute({ children }: { children: ReactNode }) {
  const { user, initialized } = useAuth();
  const location = useLocation();

  if (!initialized) return null;

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location }} />;
  }

  return <>{children}</>;
}
