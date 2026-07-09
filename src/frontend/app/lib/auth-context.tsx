import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from "react";
import { getEmailFromToken, isTokenExpired } from "./jwt";
import { setUnauthorizedHandler } from "./api";

const STORAGE_KEY = "munehisa.auth";

// How often to re-check the stored token's exp against the clock while a
// tab sits idle with no outgoing requests to naturally surface a 401.
const EXPIRY_CHECK_INTERVAL_MS = 30_000;

type AuthUser = { name: string; token: string };

type AuthContextValue = {
  user: AuthUser | null;
  // Derived from the JWT's `sub` claim - login/session responses never
  // return the email directly, so this is the only place to read it.
  email: string | null;
  initialized: boolean;
  login: (user: AuthUser) => void;
  logout: () => void;
};

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(null);
  const [initialized, setInitialized] = useState(false);

  useEffect(() => {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw) {
      try {
        const stored = JSON.parse(raw) as AuthUser;
        if (stored.token && !isTokenExpired(stored.token)) {
          setUser(stored);
        } else {
          localStorage.removeItem(STORAGE_KEY);
        }
      } catch {
        localStorage.removeItem(STORAGE_KEY);
      }
    }
    setInitialized(true);
  }, []);

  function login(nextUser: AuthUser) {
    setUser(nextUser);
    localStorage.setItem(STORAGE_KEY, JSON.stringify(nextUser));
  }

  const logout = useCallback(() => {
    setUser(null);
    localStorage.removeItem(STORAGE_KEY);
  }, []);

  // Any authenticated request rejected with 401 anywhere in the app means
  // the backend no longer honors this token - log out immediately instead
  // of leaving the UI in a stale "logged in" state.
  useEffect(() => {
    setUnauthorizedHandler(logout);
    return () => setUnauthorizedHandler(null);
  }, [logout]);

  // Catches the case where the token expires while the tab is idle and no
  // request happens to trigger the 401 handler above.
  useEffect(() => {
    if (!user) return;
    const interval = setInterval(() => {
      if (isTokenExpired(user.token)) logout();
    }, EXPIRY_CHECK_INTERVAL_MS);
    return () => clearInterval(interval);
  }, [user, logout]);

  const email = user ? getEmailFromToken(user.token) : null;

  return (
    <AuthContext.Provider value={{ user, email, initialized, login, logout }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error("useAuth must be used within AuthProvider");
  return ctx;
}
