// Local, non-verifying decode - only used to proactively drop an obviously
// expired token client-side, or to read claims for display. The backend is
// the actual source of truth.
function decodePayload(token: string): Record<string, unknown> | null {
  const payload = token.split(".")[1];
  if (!payload) return null;

  try {
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    return JSON.parse(json) as Record<string, unknown>;
  } catch {
    return null;
  }
}

export function isTokenExpired(token: string): boolean {
  const payload = decodePayload(token);
  if (!payload) return true;

  const { exp } = payload as { exp?: number };
  if (typeof exp !== "number") return false;
  return Date.now() >= exp * 1000;
}

// The backend signs the JWT with the user's email as the `sub` claim (see
// TokenService#generateToken) - this is the only place the frontend can read
// the logged-in user's email from, since login/session responses never
// include it.
export function getEmailFromToken(token: string): string | null {
  const payload = decodePayload(token);
  const sub = payload?.sub;
  return typeof sub === "string" ? sub : null;
}
