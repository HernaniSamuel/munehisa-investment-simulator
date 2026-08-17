import { render } from "@testing-library/react";
import type { ReactElement } from "react";
import { MemoryRouter, Route, Routes } from "react-router";
import { vi } from "vitest";
import { AuthProvider } from "~/lib/auth-context";
import { ThemeProvider, type Theme } from "~/lib/theme-context";
import { LOCALE_STORAGE_KEY, type Locale } from "~/lib/i18n";

export const STORAGE_KEY = "munehisa.auth";
export const THEME_STORAGE_KEY = "munehisa.theme";

function base64url(obj: object): string {
  return Buffer.from(JSON.stringify(obj))
    .toString("base64")
    .replace(/\+/g, "-")
    .replace(/\//g, "_")
    .replace(/=+$/, "");
}

// Builds a JWT-shaped (but unsigned) token good enough for isTokenExpired /
// getEmailFromToken, which only ever decode the payload - exactly what the
// payload contains and nothing more, unlike makeToken() below. Shared with
// jwt.test.ts so there's one place that knows how to shape a fake token.
export function signPayload(payload: Record<string, unknown>): string {
  return `${base64url({ alg: "HS256", typ: "JWT" })}.${base64url(payload)}.signature`;
}

// Convenience wrapper for component tests, where the exact payload rarely
// matters: fills in a sub/exp so the token is valid and decodable by default.
export function makeToken(payload: Record<string, unknown> = {}): string {
  return signPayload({ sub: "ada@example.com", exp: Math.floor(Date.now() / 1000) + 3600, ...payload });
}

// Stubs a single fetch response, the same shape api.test.ts's local
// mockFetchOnce uses. Most component tests should mock ~/lib/api's exported
// functions directly instead of going through this - reach for it only when
// a test needs to exercise the real request() implementation (e.g. to prove
// a skipUnauthorizedHandling wiring actually holds, not just that a mocked
// promise resolves/rejects the way the test expects).
export function mockFetchOnce(status: number, body?: unknown, contentType = "application/json") {
  const response = {
    status,
    ok: status >= 200 && status < 300,
    headers: { get: (name: string) => (name === "content-type" ? contentType : null) },
    json: async () => body,
  };
  (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce(response);
  return response;
}

// Seeds localStorage the same way AuthProvider's login() does, so rendering
// it afterwards hydrates as an already-authenticated user without going
// through a real login form submission.
export function seedAuthenticatedUser(overrides: { name?: string; token?: string } = {}) {
  const stored = { name: overrides.name ?? "Ada Lovelace", token: overrides.token ?? makeToken() };
  localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
  return stored;
}

// Seeds localStorage the same way ThemeProvider's setTheme() does, so
// rendering afterwards hydrates as already having a persisted preference
// without going through a Settings click.
export function seedTheme(theme: Theme) {
  localStorage.setItem(THEME_STORAGE_KEY, theme);
}

// Seeds localStorage the same way setLocale() does, so rendering afterwards
// hydrates as already having an explicit language preference without going
// through a LanguageSwitcher click. Doesn't itself call i18n.changeLanguage -
// detectInitialLocale() only runs once at module init, so this only affects
// a render that re-initializes i18n (or asserts on the stored value itself);
// tests that need the active language switched should also call
// i18n.changeLanguage(locale) or interact with LanguageSwitcher directly.
export function seedLocale(locale: Locale) {
  localStorage.setItem(LOCALE_STORAGE_KEY, locale);
}

// Renders a route component wrapped the way root.tsx wires it in the real
// app: a router (so useNavigate/useLocation/Link work) around ThemeProvider
// and AuthProvider (so useTheme/useAuth work). `route` can carry router
// state, matching how login.tsx reads location.state.from.
//
// `redirectStubs` matters for anything that can navigate away from itself
// while still mounted (most commonly ProtectedRoute rendering <Navigate>
// after a logout, or after an unauthenticated initial render). <Navigate>
// only changes the router's location - by itself it doesn't unmount
// anything, so a component that keeps re-deciding to redirect every render
// (as ProtectedRoute does while `user` stays null) would redirect forever
// with no real <Routes> to match the new location and swap it out. List the
// destination path(s) here to render through a real <Routes>, closing that
// loop the same way the app's actual router does.
export function renderWithProviders(
  ui: ReactElement,
  {
    route = "/",
    path,
    redirectStubs = [],
  }: {
    route?: string | { pathname: string; state?: unknown };
    // The Route pattern to mount `ui` under (e.g. "/simulations/:id"),
    // distinct from `route` (the actual URL navigated to, e.g.
    // "/simulations/abc-123"). Needed whenever the component under test
    // reads a dynamic segment via useParams() - without a pattern here, an
    // exact-match Route on the literal URL captures no params. Defaults to
    // the same literal-match behavior every existing call site relies on.
    path?: string;
    redirectStubs?: { path: string; element: ReactElement }[];
  } = {}
) {
  // <Route path> matches on pathname only - strip a query string/hash from a
  // string route (e.g. "/reset-password?token=...") so it doesn't end up
  // literally in the match pattern.
  const routePath = path ?? (typeof route === "string" ? route.split(/[?#]/)[0] : route.pathname);

  return render(
    <MemoryRouter initialEntries={[route]}>
      <ThemeProvider>
        <AuthProvider>
          {redirectStubs.length > 0 || path !== undefined ? (
            <Routes>
              <Route path={routePath} element={ui} />
              {redirectStubs.map((stub) => (
                <Route key={stub.path} path={stub.path} element={stub.element} />
              ))}
            </Routes>
          ) : (
            ui
          )}
        </AuthProvider>
      </ThemeProvider>
    </MemoryRouter>
  );
}
