import type { Page } from "@playwright/test";

// The 4 target widths from issue #101 - iPhone 5 up through desktop. Height is arbitrary
// (tall enough that vertical scrolling never masks a horizontal-overflow bug).
export const VIEWPORTS = {
  iphone5: { width: 320, height: 700 },
  mobile: { width: 375, height: 700 },
  tablet: { width: 768, height: 900 },
  desktop: { width: 1024, height: 900 },
} as const;

// Routes from app/routes.ts. Protected ones need seedAuth() + mockSimulationApi() before
// navigating; the rest render straight away.
export const ROUTES = {
  home: { path: "/", protected: true },
  register: { path: "/register", protected: false },
  login: { path: "/login", protected: false },
  verifyEmail: { path: "/verify-email", protected: false },
  forgotPassword: { path: "/forgot-password", protected: false },
  resetPassword: { path: "/reset-password", protected: false },
  settings: { path: "/settings", protected: true },
} as const;

export const SIMULATION_ID = "sim-1";
export const DASHBOARD_ROUTE = `/simulations/${SIMULATION_ID}`;
// A ticker param so TradeScreen's mount effect auto-loads an asset (via loadAsset()),
// which is what makes FinancialChart render real bars instead of the empty-selection state.
export const TRADE_ROUTE = `/simulations/${SIMULATION_ID}/trade?ticker=AAPL`;

const STORAGE_KEY = "munehisa.auth";

function base64url(obj: object): string {
  return Buffer.from(JSON.stringify(obj)).toString("base64").replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

// Same unsigned-JWT shape app/test/test-utils.tsx's makeToken() produces for the Vitest
// suite - AuthProvider only ever decodes the payload locally (see app/lib/jwt.ts), it never
// verifies a signature, so this is sufficient to hydrate as a logged-in user.
function fakeToken(): string {
  const header = base64url({ alg: "HS256", typ: "JWT" });
  const payload = base64url({ sub: "ada@example.com", exp: Math.floor(Date.now() / 1000) + 3600 });
  return `${header}.${payload}.signature`;
}

// Seeds localStorage the same way AuthProvider.login() does, before any app script runs, so
// ProtectedRoute routes render immediately instead of redirecting to /login.
export async function seedAuth(page: Page) {
  const stored = { name: "Ada Lovelace", token: fakeToken() };
  await page.addInitScript(
    ({ key, value }) => {
      window.localStorage.setItem(key, value);
    },
    { key: STORAGE_KEY, value: JSON.stringify(stored) }
  );
}

type Simulation = {
  id: string;
  name: string;
  baseCurrency: "BRL" | "USD";
  startMonth: string;
  currentMonth: string;
  cashBalance: number;
  totalPatrimony: number;
};

type Position = {
  ticker: string;
  assetName: string;
  quantity: number;
  currentPrice: number;
  wasTruncated: boolean;
  marketValue: number;
  weight: number;
  costBasis: number;
  gainAmount: number;
  gainPercent: number;
};

function buildSimulation(id: string): Simulation {
  return {
    id,
    name: "Retirement Plan Alpha",
    baseCurrency: "USD",
    startMonth: "2024-01",
    currentMonth: "2024-06",
    cashBalance: 12500.5,
    totalPatrimony: 48250.75,
  };
}

function buildPositions(): { positions: Position[]; totalAssetValue: number; totalGainAmount: number; totalGainPercent: number } {
  return {
    positions: [
      {
        ticker: "AAPL",
        assetName: "Apple Inc.",
        quantity: 10,
        currentPrice: 190.12,
        wasTruncated: false,
        marketValue: 1901.2,
        weight: 0.4,
        costBasis: 1700,
        gainAmount: 201.2,
        gainPercent: 0.118,
      },
      {
        ticker: "MSFT",
        assetName: "Microsoft Corporation",
        quantity: 5,
        currentPrice: 410.5,
        wasTruncated: false,
        marketValue: 2052.5,
        weight: 0.35,
        costBasis: 1900,
        gainAmount: 152.5,
        gainPercent: 0.08,
      },
      {
        ticker: "GOOGL",
        assetName: "Alphabet Inc. Class A",
        quantity: 8,
        currentPrice: 165.3,
        wasTruncated: false,
        marketValue: 1322.4,
        weight: 0.25,
        costBasis: 1250,
        gainAmount: 72.4,
        gainPercent: 0.058,
      },
    ],
    totalAssetValue: 5276.1,
    totalGainAmount: 426.1,
    totalGainPercent: 0.088,
  };
}

function buildTransactions() {
  return [
    { type: "DEPOSIT", month: "2024-01", amount: 5000, ticker: null, assetName: null, quantity: null },
    { type: "BUY", month: "2024-02", amount: 1700, ticker: "AAPL", assetName: "Apple Inc.", quantity: 10 },
    { type: "DIVIDEND", month: "2024-05", amount: 12.4, ticker: "AAPL", assetName: "Apple Inc.", quantity: null },
  ];
}

function buildAssetDetail(ticker: string) {
  const series = Array.from({ length: 6 }, (_, i) => ({
    referenceMonth: `2024-${String(i + 1).padStart(2, "0")}`,
    open: 180 + i,
    high: 186 + i,
    low: 177 + i,
    close: 182 + i * 1.5,
    volume: 1_000_000 + i * 15_000,
    dividends: 0,
    splits: 0,
  }));
  return {
    ticker,
    name: "Apple Inc.",
    currency: "USD",
    requestedMonth: "2024-06",
    returnedMonth: "2024-06",
    truncated: false,
    series,
    cashBalance: { amount: 12500.5, currency: "USD", wasConverted: false },
  };
}

// Matches app/.env.example's VITE_API_URL, also set as the dev server's env in
// playwright.config.ts. The frontend's own routes (e.g. "/simulations/sim-1") reuse the exact
// same *pathnames* as this API, so every predicate below must also check the origin - matching
// on pathname alone would intercept the page's own navigation request, not just its API calls.
const API_ORIGIN = "http://localhost:8000";

function isApiRequest(url: URL, pathname: string): boolean {
  return url.origin === API_ORIGIN && url.pathname === pathname;
}

// Intercepts every simulationApi call the protected routes make on initial render, so the
// suite never needs a live backend - see the "Mocking over a live backend" judgment call in
// the PR description. Covers home.tsx (list), simulation-dashboard.tsx (get/positions/
// transactions) and trade.tsx (get/positions/asset detail).
export async function mockSimulationApi(page: Page, simulationId: string = SIMULATION_ID) {
  const simulation = buildSimulation(simulationId);
  const positions = buildPositions();
  const transactions = buildTransactions();

  await page.route(
    (url) => isApiRequest(url, "/simulations"),
    (route) => route.fulfill({ json: [simulation] })
  );
  await page.route(
    (url) => isApiRequest(url, `/simulations/${simulationId}`),
    (route) => route.fulfill({ json: simulation })
  );
  await page.route(
    (url) => isApiRequest(url, `/simulations/${simulationId}/positions`),
    (route) => route.fulfill({ json: positions })
  );
  await page.route(
    (url) => isApiRequest(url, `/simulations/${simulationId}/transactions`),
    (route) => route.fulfill({ json: transactions })
  );
  await page.route(
    (url) => isApiRequest(url, `/simulations/${simulationId}/assets/search`),
    (route) => route.fulfill({ json: [] })
  );
  await page.route(
    (url) =>
      url.origin === API_ORIGIN &&
      url.pathname.startsWith(`/simulations/${simulationId}/assets/`) &&
      !url.pathname.endsWith("/search"),
    (route) => {
      const ticker = decodeURIComponent(route.request().url().split("/assets/")[1] ?? "AAPL");
      return route.fulfill({ json: buildAssetDetail(ticker) });
    }
  );
}
