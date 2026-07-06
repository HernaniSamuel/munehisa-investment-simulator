// Local, non-verifying decode - only used to proactively drop an obviously
// expired token client-side. The backend is the actual source of truth.
export function isTokenExpired(token: string): boolean {
  const payload = token.split(".")[1];
  if (!payload) return true;

  try {
    const json = atob(payload.replace(/-/g, "+").replace(/_/g, "/"));
    const { exp } = JSON.parse(json) as { exp?: number };
    if (typeof exp !== "number") return false;
    return Date.now() >= exp * 1000;
  } catch {
    return true;
  }
}
