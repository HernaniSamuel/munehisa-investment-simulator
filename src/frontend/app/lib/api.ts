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

  checkSession: (token: string) => request<string>("/user", { token }),
};
