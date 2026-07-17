# Munehisa — Frontend

React + Vite (React Router v8, framework mode, SPA build) frontend for the Munehisa investment simulator. This module only covers the authentication flows against the Spring Boot backend in [`../backend`](../backend); the investment simulator screens are a separate, later module.

Visual design follows [`DESIGN.md`](./DESIGN.md) (the "Sumi" skin).

## Stack

- React 19 + React Router 8 in **framework mode, SPA build** (`ssr: false` — no Node server at runtime, deployable as static files)
- Tailwind CSS 4
- TypeScript

## Running locally

**Prerequisites:** Node 24, and the backend running locally (see [`../backend/README.md`](../backend) — defaults to `http://localhost:8000`).

1. Install dependencies:
   ```
   npm install
   ```
2. Copy the environment template:
   ```
   cp .env.example .env
   ```
   `VITE_API_URL` defaults to `http://localhost:8000`, matching the backend's dev profile. Every backend call is made through this variable via `import.meta.env.VITE_API_URL` — there is no hardcoded backend URL in source.
3. Start the dev server:
   ```
   npm run dev
   ```
   The app is available at `http://localhost:5173` (this is also the origin the backend's `cors.allowed-origins` and `FRONTEND_URL` already expect in its dev profile — see [`../backend/.env.example`](../backend/.env.example)).

### Verifying changes

```
npm run test        # vitest
npm run typecheck    # react-router typegen + tsc
npm run build        # production build (also run in CI)
```

### Testing

Two kinds of tests live under `app/`, both run by `npm run test`:

- **Pure logic tests** (`app/lib/api.test.ts`, `app/lib/jwt.test.ts`) - DOM-free; `api.test.ts`
  additionally fetch-mocks `globalThis.fetch`.
- **Component tests** (`*.test.tsx`) - render actual components with
  [`@testing-library/react`](https://testing-library.com/docs/react-testing-library/intro/) and
  simulate user interaction with
  [`@testing-library/user-event`](https://testing-library.com/docs/user-event/intro/), under a
  `jsdom` DOM environment (`vitest.config.ts`). `app/test/setup.ts` wires up
  [`@testing-library/jest-dom`](https://github.com/testing-library/jest-dom) matchers (e.g.
  `toBeInTheDocument`) and cleans up the DOM between tests.

`app/test/test-utils.tsx` provides shared helpers for component tests:

- `renderWithProviders(ui, { route, redirectStubs })` - renders a component the way `root.tsx`
  wires it in the real app, inside a `MemoryRouter` (so `useNavigate`/`useLocation`/`Link` work)
  wrapped in the real `AuthProvider` (so `useAuth` works). Pass `redirectStubs` (a list of
  `{ path, element }`) for anything that can navigate away from itself while still mounted - most
  commonly `ProtectedRoute` rendering `<Navigate>` after a logout, or on an unauthenticated initial
  render. Without a real `<Routes>` match for the destination, `<Navigate>` only changes the
  router's location - it doesn't unmount anything, so a component that keeps re-deciding to
  redirect every render loops forever instead of settling. See `app/routes/home.test.tsx` for
  example usage.
- `seedAuthenticatedUser()` / `makeToken()` - seed `localStorage` the way a real login would, so a
  component hydrates already-authenticated without going through a login form submission.

## Routes

| Route | Purpose |
|---|---|
| `/register` | Create an account |
| `/login` | Log in; also handles "resend verification email" for unverified accounts |
| `/verify-email?token=...` | Confirms the token from the verification email and signs the user in |
| `/forgot-password` | Requests a password-reset email |
| `/reset-password?token=...` | Sets a new password using the token from the reset email |
| `/` | Protected placeholder dashboard — redirects to `/login` when unauthenticated, accessible once a valid JWT is present |

## Auth & API integration

- All auth calls hit `${VITE_API_URL}/auth/...` (see `app/lib/api.ts`).
- On login/verify/reset-password, the backend returns `{ name, token }`. The JWT is stored in `localStorage` and sent back as `Authorization: Bearer <token>` on subsequent requests (see `app/lib/auth-context.tsx`).
- **Tradeoff, documented per the issue:** `localStorage` is simpler than an httpOnly cookie but is readable by any script on the page (XSS risk). An httpOnly cookie would be safer but requires the backend to set/rotate it, which is out of scope here. Acceptable for this stage; revisit if/when the backend takes on session management.
- Error responses are parsed from either shape the backend returns — `{ message }` for business errors (`RestErrorMessage`) or `{ detail }` / `{ title }` for Spring's default validation `ProblemDetail` — and shown to the user via inline banners.

## Building for GitHub Pages

GitHub Pages only serves static files, so the app is built in SPA mode from a subpath (`/munehisa-investment-simulator/` for this repo's project page):

```
BASE_PATH=/munehisa-investment-simulator/ VITE_API_URL=<your-backend-url> npm run build
```

This produces `build/client/`. Because GitHub Pages has no server-side rewrite rules, deep links (e.g. a user landing directly on `/login`) are handled with the standard GitHub Pages SPA trick: `index.html` is duplicated as `404.html`, so any unknown path falls back to the same app shell and the client-side router takes over.

### Automated deploy

[`.github/workflows/deploy-frontend.yml`](../../.github/workflows/deploy-frontend.yml) builds and deploys this folder to GitHub Pages on every push to `main` that touches `src/frontend/**`. One-time setup:

1. In the repo's **Settings → Pages**, set **Source** to "GitHub Actions".
2. In **Settings → Secrets and variables → Actions → Variables**, add `VITE_API_URL` pointing at the backend's public URL (backend hosting is a separate, not-yet-scoped concern — until then the deployed frontend has no live backend to call).

**Deployed URL:** `https://hernanisamuel.github.io/munehisa-investment-simulator/` (live once the workflow has run at least once with Pages enabled).

## Docker

A `Dockerfile` is provided for serving the static SPA build (`build/client`) via `serve` in a container (useful for local testing); the GitHub Pages deploy above does not use it.

```
docker build -t munehisa-frontend .
docker run -p 3000:3000 munehisa-frontend
```
