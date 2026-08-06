import { type RouteConfig, index, route } from "@react-router/dev/routes";

export default [
  index("routes/home.tsx"),
  route("register", "routes/register.tsx"),
  route("login", "routes/login.tsx"),
  route("verify-email", "routes/verify-email.tsx"),
  route("forgot-password", "routes/forgot-password.tsx"),
  route("reset-password", "routes/reset-password.tsx"),
  route("settings", "routes/settings.tsx"),
  route("simulations/:id", "routes/simulation-dashboard.tsx"),
  route("simulations/:id/trade", "routes/trade.tsx"),
] satisfies RouteConfig;
