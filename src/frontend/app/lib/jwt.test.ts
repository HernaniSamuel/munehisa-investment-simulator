import { describe, expect, it } from "vitest";
import { getEmailFromToken, isTokenExpired } from "./jwt";

function makeToken(payload: Record<string, unknown>): string {
  const base64url = (obj: object) =>
    Buffer.from(JSON.stringify(obj))
      .toString("base64")
      .replace(/\+/g, "-")
      .replace(/\//g, "_")
      .replace(/=+$/, "");

  return `${base64url({ alg: "HS256", typ: "JWT" })}.${base64url(payload)}.signature`;
}

describe("isTokenExpired", () => {
  it("returns true for a malformed token", () => {
    expect(isTokenExpired("not-a-jwt")).toBe(true);
    expect(isTokenExpired("")).toBe(true);
  });

  it("returns true for a token whose payload isn't valid base64/JSON", () => {
    expect(isTokenExpired("header.%%%not-base64%%%.signature")).toBe(true);
  });

  it("returns true once exp is in the past", () => {
    const token = makeToken({ exp: Math.floor(Date.now() / 1000) - 60 });
    expect(isTokenExpired(token)).toBe(true);
  });

  it("returns false while exp is in the future", () => {
    const token = makeToken({ exp: Math.floor(Date.now() / 1000) + 3600 });
    expect(isTokenExpired(token)).toBe(false);
  });

  it("returns false when the payload has no exp claim", () => {
    const token = makeToken({ sub: "user@example.com" });
    expect(isTokenExpired(token)).toBe(false);
  });
});

describe("getEmailFromToken", () => {
  it("returns the sub claim", () => {
    const token = makeToken({ sub: "user@example.com" });
    expect(getEmailFromToken(token)).toBe("user@example.com");
  });

  it("returns null for a malformed token", () => {
    expect(getEmailFromToken("not-a-jwt")).toBeNull();
  });

  it("returns null when the payload has no sub claim", () => {
    const token = makeToken({ exp: Math.floor(Date.now() / 1000) + 3600 });
    expect(getEmailFromToken(token)).toBeNull();
  });
});
