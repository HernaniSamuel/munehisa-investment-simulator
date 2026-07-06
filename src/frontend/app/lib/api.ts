const API_URL = import.meta.env.VITE_API_URL;

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.status = status;
  }
}

type RequestOptions = {
  method?: "GET" | "POST";
  body?: unknown;
  token?: string;
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

  if (!response.ok) {
    // Only requests made on behalf of a logged-in user carry a token, so a
    // 401/403 here means the backend rejected that session (expired/invalid
    // JWT) rather than e.g. a bad login attempt (no token attached) or a
    // pending-verification 403 from login/register (also no token attached).
    // 403 matters because this backend has no custom AuthenticationEntryPoint,
    // so Spring Security's fallback for a missing/invalid bearer token on a
    // protected endpoint is 403, not 401.
    if ((response.status === 401 || response.status === 403) && options.token) {
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
    }),

  verifyEmail: (verificationToken: string) =>
    request<LoginResponse>(
      `/auth/verify?verificationToken=${encodeURIComponent(verificationToken)}`
    ),

  forgotPassword: (email: string) =>
    request<PendingEmailResponse | undefined>("/auth/forgot-password", {
      method: "POST",
      body: { email },
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
