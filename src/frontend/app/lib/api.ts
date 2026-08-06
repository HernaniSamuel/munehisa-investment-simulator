const API_URL = import.meta.env.VITE_API_URL;

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

type RequestOptions = {
  method?: "GET" | "POST" | "PATCH" | "DELETE";
  body?: unknown;
  token?: string;
  // Most authenticated endpoints only ever return 401/403 for a
  // rejected/expired session, so request() treats that as a global logout
  // signal by default. A few endpoints (e.g. delete-account) can also return
  // 401 for a business-logic reason (wrong password) using the same status
  // code as an invalid token - set this to opt out of the global-logout side
  // effect for those, and let the caller's own catch block handle the error.
  skipUnauthorizedHandling?: boolean;
  // A handful of endpoints (forgot-password, resend-verification) use 429
  // not as a rate-limit error but as a normal "already pending" outcome that
  // carries a body the caller needs (message + resendAvailableAt). Listing a
  // status here makes request() resolve with that body instead of throwing.
  additionalSuccessStatuses?: number[];
};

// Set by AuthProvider so that any authenticated request whose token was
// rejected triggers a client-side logout, not just the one call that
// happened to notice.
let onUnauthorized: (() => void) | null = null;

export function setUnauthorizedHandler(handler: (() => void) | null) {
  onUnauthorized = handler;
}

function extractErrorMessage(body: unknown, status: number): string {
  if (body && typeof body === "object") {
    const record = body as Record<string, unknown>;
    // RestErrorMessage / 429 DTOs: { message: string, ... }
    if (typeof record.message === "string") return record.message;
    // Spring's default ProblemDetail for @Valid failures: { detail, title, ... }
    if (typeof record.detail === "string") return record.detail;
    if (typeof record.title === "string") return record.title;
  }
  return `Request failed (${status})`;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { "Content-Type": "application/json" };
  if (options.token) headers.Authorization = `Bearer ${options.token}`;

  const response = await fetch(`${API_URL}${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  if (response.status === 204) return undefined as T;

  const isJson = response.headers.get("content-type")?.includes("application/json");
  const data = isJson ? await response.json().catch(() => undefined) : undefined;

  if (options.additionalSuccessStatuses?.includes(response.status)) return data as T;

  if (!response.ok) {
    // Only requests made on behalf of a logged-in user carry a token, so a
    // 401/403 here means the backend rejected that session (expired/invalid
    // JWT) rather than e.g. a bad login attempt (no token attached) or a
    // pending-verification 403 from login/register (also no token attached).
    // 403 matters because this backend has no custom AuthenticationEntryPoint,
    // so Spring Security's fallback for a missing/invalid bearer token on a
    // protected endpoint is 403, not 401.
    if (
      (response.status === 401 || response.status === 403) &&
      options.token &&
      !options.skipUnauthorizedHandling
    ) {
      onUnauthorized?.();
    }
    throw new ApiError(response.status, extractErrorMessage(data, response.status));
  }

  return data as T;
}

export type LoginResponse = { name: string; token: string };
export type PendingEmailResponse = { message: string; resendAvailableAt: string };

export const authApi = {
  register: (data: { name: string; email: string; password: string }) =>
    request<void>("/auth/register", { method: "POST", body: data }),

  login: (data: { email: string; password: string }) =>
    request<LoginResponse>("/auth/login", { method: "POST", body: data }),

  resendVerification: (email: string) =>
    request<PendingEmailResponse | undefined>("/auth/resend-verification", {
      method: "POST",
      body: { email },
      additionalSuccessStatuses: [429],
    }),

  verifyEmail: (verificationToken: string) =>
    request<LoginResponse>(
      `/auth/verify?verificationToken=${encodeURIComponent(verificationToken)}`
    ),

  forgotPassword: (email: string) =>
    request<PendingEmailResponse | undefined>("/auth/forgot-password", {
      method: "POST",
      body: { email },
      additionalSuccessStatuses: [429],
    }),

  resetPassword: (resetPasswordToken: string, newPassword: string) =>
    request<LoginResponse>("/auth/reset-password", {
      method: "POST",
      body: { resetPasswordToken, newPassword },
    }),

  // GET /user returns text/plain; request() only parses JSON bodies, so this
  // always resolves to undefined on success. Only success-vs-failure (via
  // the thrown ApiError) is meaningful here.
  checkSession: (token: string) => request<void>("/user", { token }),
};

export type UpdateNameResponse = { name: string };

export const userApi = {
  updateName: (name: string, token: string) =>
    request<UpdateNameResponse>("/user", { method: "PATCH", body: { name }, token }),

  // Wrong password is a 401 from this endpoint, same status code Spring
  // Security's entry point uses for an invalid/expired token - skip the
  // global-logout side effect so the caller can show it as an inline error
  // instead of the whole app bouncing to /login mid-modal.
  deleteAccount: (password: string, token: string) =>
    request<void>("/user/delete", {
      method: "POST",
      body: { password },
      token,
      skipUnauthorizedHandling: true,
    }),
};

export type Simulation = {
  id: string;
  name: string;
  baseCurrency: "BRL" | "USD";
  startMonth: string;
  currentMonth: string;
  cashBalance: number;
  totalPatrimony: number;
};

export type Position = {
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

export type PositionsResponse = {
  positions: Position[];
  totalAssetValue: number;
  totalGainAmount: number;
  totalGainPercent: number;
};

export type TransactionType = "BUY" | "SELL" | "DEPOSIT" | "WITHDRAWAL" | "DIVIDEND";

export type Transaction = {
  type: TransactionType;
  month: string;
  amount: number;
  ticker: string | null;
  assetName: string | null;
  quantity: number | null;
};

export type CashMovementResponse = {
  simulationId: string;
  appliedAmount: number;
  cashBalance: number;
  totalPatrimony: number;
  // Not rendered by the dashboard - only appliedAmount/cashBalance/totalPatrimony
  // are used, so the nested inflation/deflation lookup shape is left untyped.
  deflation: unknown | null;
};

export type AdvanceMonthPosition = {
  ticker: string;
  assetName: string;
  quantity: number;
  price: number;
  wasTruncated: boolean;
  dividendReceived: number;
  weight: number;
  costBasis: number;
  totalDividendsReceived: number;
};

export type AdvanceMonthResponse = {
  simulationId: string;
  currentMonth: string;
  cashBalance: number;
  totalAssetValue: number;
  totalPatrimony: number;
  positions: AdvanceMonthPosition[];
};

export type TickerSearchResult = {
  ticker: string;
  name: string;
  exchange: string;
  assetType: string;
};

export type AssetMonthData = {
  referenceMonth: string;
  open: number;
  high: number;
  low: number;
  close: number;
  volume: number;
  dividends: number;
  splits: number;
};

export type ConvertedCashBalance = {
  amount: number;
  currency: string;
  wasConverted: boolean;
};

export type AssetDetail = {
  ticker: string;
  name: string;
  currency: string;
  requestedMonth: string;
  returnedMonth: string;
  truncated: boolean;
  series: AssetMonthData[];
  cashBalance: ConvertedCashBalance;
};

export type TradePosition = {
  ticker: string;
  assetName: string;
  quantity: number;
  costBasis: number;
  weight: number;
  totalDividendsReceived: number;
};

export type TradeResponse = {
  simulationId: string;
  type: TransactionType;
  position: TradePosition;
  appliedAmount: number;
  cashBalance: number;
  totalAssetValue: number;
  totalPatrimony: number;
};

export const simulationApi = {
  list: (token: string) => request<Simulation[]>("/simulations", { token }),

  create: (data: { name: string; baseCurrency: string; startMonth: string }, token: string) =>
    request<Simulation>("/simulations", { method: "POST", body: data, token }),

  get: (id: string, token: string) => request<Simulation>(`/simulations/${id}`, { token }),

  rename: (id: string, name: string, token: string) =>
    request<Simulation>(`/simulations/${id}`, { method: "PATCH", body: { name }, token }),

  remove: (id: string, token: string) =>
    request<void>(`/simulations/${id}`, { method: "DELETE", token }),

  positions: (id: string, token: string) =>
    request<PositionsResponse>(`/simulations/${id}/positions`, { token }),

  searchAssets: (id: string, query: string, token: string) =>
    request<TickerSearchResult[]>(
      `/simulations/${id}/assets/search?query=${encodeURIComponent(query)}`,
      { token }
    ),

  getAsset: (id: string, ticker: string, token: string) =>
    request<AssetDetail>(`/simulations/${id}/assets/${encodeURIComponent(ticker)}`, { token }),

  buy: (id: string, data: { ticker: string; quantity: number }, token: string) =>
    request<TradeResponse>(`/simulations/${id}/buy`, { method: "POST", body: data, token }),

  sell: (id: string, data: { ticker: string; quantity: number }, token: string) =>
    request<TradeResponse>(`/simulations/${id}/sell`, { method: "POST", body: data, token }),

  transactions: (id: string, token: string) =>
    request<Transaction[]>(`/simulations/${id}/transactions`, { token }),

  deposit: (id: string, data: { amount: number; todaysMoney: boolean }, token: string) =>
    request<CashMovementResponse>(`/simulations/${id}/deposits`, {
      method: "POST",
      body: data,
      token,
    }),

  withdraw: (id: string, data: { amount: number; todaysMoney: boolean }, token: string) =>
    request<CashMovementResponse>(`/simulations/${id}/withdrawals`, {
      method: "POST",
      body: data,
      token,
    }),

  reset: (id: string, token: string) =>
    request<Simulation>(`/simulations/${id}/reset`, { method: "POST", token }),

  advance: (id: string, token: string) =>
    request<AdvanceMonthResponse>(`/simulations/${id}/advance`, { method: "POST", token }),
};
