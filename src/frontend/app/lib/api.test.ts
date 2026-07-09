import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, authApi, setUnauthorizedHandler, userApi } from "./api";

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
});
