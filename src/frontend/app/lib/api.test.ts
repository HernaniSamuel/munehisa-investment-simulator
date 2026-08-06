import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, authApi, setUnauthorizedHandler, simulationApi, userApi } from "./api";

function mockFetchOnce(status: number, body?: unknown, contentType = "application/json") {
  const response = {
    status,
    ok: status >= 200 && status < 300,
    headers: { get: (name: string) => (name === "content-type" ? contentType : null) },
    json: async () => body,
  };
  (globalThis.fetch as ReturnType<typeof vi.fn>).mockResolvedValueOnce(response);
  return response;
}

describe("api request/error parsing", () => {
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    setUnauthorizedHandler(null);
    vi.unstubAllGlobals();
  });

  it("resolves to undefined on 204 No Content", async () => {
    mockFetchOnce(204);
    await expect(authApi.forgotPassword("a@b.com")).resolves.toBeUndefined();
  });

  it("resolves to the pending-email body when forgotPassword is rate-limited", async () => {
    mockFetchOnce(429, { message: "Already sent", resendAvailableAt: "2026-01-01T00:00:00Z" });
    await expect(authApi.forgotPassword("a@b.com")).resolves.toMatchObject({
      message: "Already sent",
      resendAvailableAt: "2026-01-01T00:00:00Z",
    });
  });

  it("parses the backend's RestErrorMessage { message } shape", async () => {
    mockFetchOnce(401, { status: "UNAUTHORIZED", message: "Invalid Credentials" });
    await expect(authApi.login({ email: "a@b.com", password: "x" })).rejects.toMatchObject({
      status: 401,
      message: "Invalid Credentials",
    });
  });

  it("parses Spring's default ProblemDetail { detail } shape", async () => {
    mockFetchOnce(400, { title: "Bad Request", detail: "email must be a well-formed address" });
    await expect(authApi.login({ email: "bad", password: "x" })).rejects.toMatchObject({
      status: 400,
      message: "email must be a well-formed address",
    });
  });

  it("falls back to title when detail is absent", async () => {
    mockFetchOnce(400, { title: "Bad Request" });
    await expect(authApi.login({ email: "bad", password: "x" })).rejects.toMatchObject({
      status: 400,
      message: "Bad Request",
    });
  });

  it("falls back to a generic message when the body matches no known shape", async () => {
    mockFetchOnce(500, undefined, "text/plain");
    await expect(authApi.login({ email: "a@b.com", password: "x" })).rejects.toMatchObject({
      status: 500,
      message: "Request failed (500)",
    });
  });

  it("attaches an Authorization header for token-bearing requests", async () => {
    mockFetchOnce(200, undefined, "text/plain");
    await authApi.checkSession("my-jwt");
    const [, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect((init as RequestInit & { headers: Record<string, string> }).headers.Authorization).toBe(
      "Bearer my-jwt"
    );
  });

  it("does not attach an Authorization header for anonymous requests", async () => {
    mockFetchOnce(200, { name: "A", token: "t" });
    await authApi.login({ email: "a@b.com", password: "x" });
    const [, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(
      (init as RequestInit & { headers: Record<string, string> }).headers.Authorization
    ).toBeUndefined();
  });

  it("triggers the unauthorized handler on a 401 for a token-bearing request", async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    mockFetchOnce(401, { message: "expired" });
    await expect(authApi.checkSession("stale-jwt")).rejects.toBeInstanceOf(ApiError);
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it("triggers the unauthorized handler on a 403 for a token-bearing request", async () => {
    // This backend has no custom AuthenticationEntryPoint, so Spring
    // Security's fallback for a rejected bearer token is 403, not 401.
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    mockFetchOnce(403, undefined, "text/plain");
    await expect(authApi.checkSession("stale-jwt")).rejects.toBeInstanceOf(ApiError);
    expect(handler).toHaveBeenCalledTimes(1);
  });

  it("does not trigger the unauthorized handler on a 401 for an anonymous request", async () => {
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    mockFetchOnce(401, { message: "Invalid Credentials" });
    await expect(
      authApi.login({ email: "a@b.com", password: "wrong" })
    ).rejects.toBeInstanceOf(ApiError);
    expect(handler).not.toHaveBeenCalled();
  });

  it("does not trigger the unauthorized handler on a 403 for an anonymous request", async () => {
    // e.g. login/register's pending-verification 403 - a business error,
    // not a rejected session, and never carries a token.
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    mockFetchOnce(403, { message: "The email should be verified." });
    await expect(
      authApi.login({ email: "a@b.com", password: "x" })
    ).rejects.toBeInstanceOf(ApiError);
    expect(handler).not.toHaveBeenCalled();
  });

  it("sends a POST request with name/email/password for register", async () => {
    mockFetchOnce(201);
    await authApi.register({ name: "Ada Lovelace", email: "a@b.com", password: "hunter22" });
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/auth/register");
    expect((init as RequestInit).method).toBe("POST");
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      name: "Ada Lovelace",
      email: "a@b.com",
      password: "hunter22",
    });
  });

  it("rejects with a 409 ApiError when register is given an already-verified email", async () => {
    mockFetchOnce(409, { status: "CONFLICT", message: "Email already in use" });
    await expect(
      authApi.register({ name: "Ada Lovelace", email: "a@b.com", password: "hunter22" })
    ).rejects.toMatchObject({ status: 409, message: "Email already in use" });
  });

  it("sends a GET request with the encoded token for verifyEmail", async () => {
    mockFetchOnce(200, { name: "Ada Lovelace", token: "jwt" });
    await authApi.verifyEmail("a token/with special+chars");
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain(`/auth/verify?verificationToken=${encodeURIComponent("a token/with special+chars")}`);
    expect((init as RequestInit).method ?? "GET").toBe("GET");
  });

  it("sends a POST request with the token and new password for resetPassword", async () => {
    mockFetchOnce(200, { name: "Ada Lovelace", token: "jwt" });
    await authApi.resetPassword("reset-token", "brand-new-password");
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/auth/reset-password");
    expect((init as RequestInit).method).toBe("POST");
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      resetPasswordToken: "reset-token",
      newPassword: "brand-new-password",
    });
  });

  it("resolves to undefined on 204 No Content for resendVerification", async () => {
    mockFetchOnce(204);
    await expect(authApi.resendVerification("a@b.com")).resolves.toBeUndefined();
  });

  it("resolves to the pending-email body when resendVerification is rate-limited", async () => {
    mockFetchOnce(429, { message: "Already sent", resendAvailableAt: "2026-01-01T00:00:00Z" });
    await expect(authApi.resendVerification("a@b.com")).resolves.toMatchObject({
      message: "Already sent",
      resendAvailableAt: "2026-01-01T00:00:00Z",
    });
  });

  it("sends a PATCH request with the token for updateName", async () => {
    mockFetchOnce(200, { name: "New Name" });
    await userApi.updateName("New Name", "my-jwt");
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/user");
    expect((init as RequestInit).method).toBe("PATCH");
    expect((init as RequestInit & { headers: Record<string, string> }).headers.Authorization).toBe(
      "Bearer my-jwt"
    );
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({ name: "New Name" });
  });

  it("rejects with a 400 ApiError when updateName is given a blank name", async () => {
    mockFetchOnce(400, { status: "BAD_REQUEST", message: "name: must not be blank" });
    await expect(userApi.updateName("", "my-jwt")).rejects.toMatchObject({
      status: 400,
      message: "name: must not be blank",
    });
  });

  it("sends a POST request with the token and password for deleteAccount", async () => {
    mockFetchOnce(204);
    await userApi.deleteAccount("hunter2", "my-jwt");
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/user/delete");
    expect((init as RequestInit).method).toBe("POST");
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({ password: "hunter2" });
  });

  it("rejects with a 401 ApiError when deleteAccount is given the wrong password", async () => {
    mockFetchOnce(401, { status: "UNAUTHORIZED", message: "Invalid Credentials" });
    await expect(userApi.deleteAccount("wrong", "my-jwt")).rejects.toMatchObject({
      status: 401,
      message: "Invalid Credentials",
    });
  });

  it("does not trigger the unauthorized handler when deleteAccount gets the wrong password", async () => {
    // Regression: /user/delete reuses 401 for "wrong password", the same
    // status code used for a rejected session elsewhere. Without
    // skipUnauthorizedHandling this used to fire the global logout and bounce
    // the user out of the still-open delete modal instead of showing the
    // inline error.
    const handler = vi.fn();
    setUnauthorizedHandler(handler);
    mockFetchOnce(401, { status: "UNAUTHORIZED", message: "Invalid Credentials" });
    await expect(userApi.deleteAccount("wrong", "my-jwt")).rejects.toBeInstanceOf(ApiError);
    expect(handler).not.toHaveBeenCalled();
  });

  it("sends a GET request with the token for simulationApi.list", async () => {
    mockFetchOnce(200, []);
    await simulationApi.list("my-jwt");
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/simulations");
    expect((init as RequestInit).method ?? "GET").toBe("GET");
    expect((init as RequestInit & { headers: Record<string, string> }).headers.Authorization).toBe(
      "Bearer my-jwt"
    );
  });

  it("sends a POST request with the body and token for simulationApi.create", async () => {
    mockFetchOnce(201, { id: "1", name: "Retirement plan" });
    await simulationApi.create(
      { name: "Retirement plan", baseCurrency: "BRL", startMonth: "2024-01" },
      "my-jwt"
    );
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/simulations");
    expect((init as RequestInit).method).toBe("POST");
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({
      name: "Retirement plan",
      baseCurrency: "BRL",
      startMonth: "2024-01",
    });
    expect((init as RequestInit & { headers: Record<string, string> }).headers.Authorization).toBe(
      "Bearer my-jwt"
    );
  });

  it("rejects with a 400 ApiError when simulationApi.create is given an invalid body", async () => {
    mockFetchOnce(400, { status: "BAD_REQUEST", message: "baseCurrency: must be BRL or USD" });
    await expect(
      simulationApi.create({ name: "X", baseCurrency: "EUR", startMonth: "2024-01" }, "my-jwt")
    ).rejects.toMatchObject({ status: 400, message: "baseCurrency: must be BRL or USD" });
  });

  it("sends a PATCH request with the name and token for simulationApi.rename", async () => {
    mockFetchOnce(200, { id: "1", name: "New name" });
    await simulationApi.rename("1", "New name", "my-jwt");
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/simulations/1");
    expect((init as RequestInit).method).toBe("PATCH");
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({ name: "New name" });
    expect((init as RequestInit & { headers: Record<string, string> }).headers.Authorization).toBe(
      "Bearer my-jwt"
    );
  });

  it("rejects with a 404 ApiError when simulationApi.rename targets a missing simulation", async () => {
    mockFetchOnce(404, { status: "NOT_FOUND", message: "Simulation not found" });
    await expect(simulationApi.rename("missing", "New name", "my-jwt")).rejects.toMatchObject({
      status: 404,
      message: "Simulation not found",
    });
  });

  it("sends a DELETE request with the token for simulationApi.remove", async () => {
    mockFetchOnce(204);
    await simulationApi.remove("1", "my-jwt");
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/simulations/1");
    expect((init as RequestInit).method).toBe("DELETE");
    expect((init as RequestInit & { headers: Record<string, string> }).headers.Authorization).toBe(
      "Bearer my-jwt"
    );
  });

  it("resolves to undefined on a 204 No Content for simulationApi.remove", async () => {
    mockFetchOnce(204);
    await expect(simulationApi.remove("1", "my-jwt")).resolves.toBeUndefined();
  });

  it("rejects with a 404 ApiError when simulationApi.remove targets a missing simulation", async () => {
    mockFetchOnce(404, { status: "NOT_FOUND", message: "Simulation not found" });
    await expect(simulationApi.remove("missing", "my-jwt")).rejects.toMatchObject({
      status: 404,
      message: "Simulation not found",
    });
  });

  it("sends a GET request with the token for simulationApi.get", async () => {
    mockFetchOnce(200, { id: "1", name: "Retirement plan" });
    await simulationApi.get("1", "my-jwt");
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/simulations/1");
    expect((init as RequestInit).method ?? "GET").toBe("GET");
    expect((init as RequestInit & { headers: Record<string, string> }).headers.Authorization).toBe(
      "Bearer my-jwt"
    );
  });

  it("rejects with a 404 ApiError when simulationApi.get targets a missing simulation", async () => {
    mockFetchOnce(404, { status: "NOT_FOUND", message: "Simulation not found" });
    await expect(simulationApi.get("missing", "my-jwt")).rejects.toMatchObject({
      status: 404,
      message: "Simulation not found",
    });
  });

  it("sends a GET request with the token for simulationApi.positions", async () => {
    mockFetchOnce(200, { positions: [], totalAssetValue: 0, totalGainAmount: 0, totalGainPercent: 0 });
    await simulationApi.positions("1", "my-jwt");
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/simulations/1/positions");
    expect((init as RequestInit).method ?? "GET").toBe("GET");
    expect((init as RequestInit & { headers: Record<string, string> }).headers.Authorization).toBe(
      "Bearer my-jwt"
    );
  });

  it("sends a GET request with the token for simulationApi.transactions", async () => {
    mockFetchOnce(200, []);
    await simulationApi.transactions("1", "my-jwt");
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/simulations/1/transactions");
    expect((init as RequestInit).method ?? "GET").toBe("GET");
    expect((init as RequestInit & { headers: Record<string, string> }).headers.Authorization).toBe(
      "Bearer my-jwt"
    );
  });

  it("sends a POST request with amount/todaysMoney and the token for simulationApi.deposit", async () => {
    mockFetchOnce(200, { simulationId: "1", appliedAmount: 100, cashBalance: 100, totalPatrimony: 100, deflation: null });
    await simulationApi.deposit("1", { amount: 100, todaysMoney: true }, "my-jwt");
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/simulations/1/deposits");
    expect((init as RequestInit).method).toBe("POST");
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({ amount: 100, todaysMoney: true });
    expect((init as RequestInit & { headers: Record<string, string> }).headers.Authorization).toBe(
      "Bearer my-jwt"
    );
  });

  it("rejects with a 400 ApiError when simulationApi.deposit is given an invalid amount", async () => {
    mockFetchOnce(400, { status: "BAD_REQUEST", message: "amount: must be greater than 0" });
    await expect(
      simulationApi.deposit("1", { amount: -1, todaysMoney: false }, "my-jwt")
    ).rejects.toMatchObject({ status: 400, message: "amount: must be greater than 0" });
  });

  it("sends a POST request with amount/todaysMoney and the token for simulationApi.withdraw", async () => {
    mockFetchOnce(200, { simulationId: "1", appliedAmount: 50, cashBalance: 50, totalPatrimony: 50, deflation: null });
    await simulationApi.withdraw("1", { amount: 50, todaysMoney: false }, "my-jwt");
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/simulations/1/withdrawals");
    expect((init as RequestInit).method).toBe("POST");
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({ amount: 50, todaysMoney: false });
    expect((init as RequestInit & { headers: Record<string, string> }).headers.Authorization).toBe(
      "Bearer my-jwt"
    );
  });

  it("rejects with a 400 ApiError when simulationApi.withdraw exceeds the cash balance", async () => {
    mockFetchOnce(400, { status: "BAD_REQUEST", message: "Insufficient cash balance" });
    await expect(
      simulationApi.withdraw("1", { amount: 999999, todaysMoney: false }, "my-jwt")
    ).rejects.toMatchObject({ status: 400, message: "Insufficient cash balance" });
  });

  it("sends a POST request with the token and no body for simulationApi.reset", async () => {
    mockFetchOnce(200, { id: "1", currentMonth: "2020-02" });
    await simulationApi.reset("1", "my-jwt");
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/simulations/1/reset");
    expect((init as RequestInit).method).toBe("POST");
    expect((init as RequestInit & { headers: Record<string, string> }).headers.Authorization).toBe(
      "Bearer my-jwt"
    );
  });

  it("rejects with an ApiError when simulationApi.reset has no snapshot to revert to", async () => {
    mockFetchOnce(404, { status: "NOT_FOUND", message: "No snapshot found" });
    await expect(simulationApi.reset("1", "my-jwt")).rejects.toMatchObject({
      status: 404,
      message: "No snapshot found",
    });
  });

  it("sends a POST request with the token and no body for simulationApi.advance", async () => {
    mockFetchOnce(200, {
      simulationId: "1",
      currentMonth: "2020-02",
      cashBalance: 100,
      totalAssetValue: 200,
      totalPatrimony: 300,
      positions: [],
    });
    await simulationApi.advance("1", "my-jwt");
    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/simulations/1/advance");
    expect((init as RequestInit).method).toBe("POST");
    expect((init as RequestInit & { headers: Record<string, string> }).headers.Authorization).toBe(
      "Bearer my-jwt"
    );
  });

  it("rejects with an ApiError when simulationApi.advance fails", async () => {
    mockFetchOnce(500, undefined, "text/plain");
    await expect(simulationApi.advance("1", "my-jwt")).rejects.toMatchObject({ status: 500 });
  });
});
