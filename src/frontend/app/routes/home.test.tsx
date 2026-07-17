import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders, seedAuthenticatedUser, STORAGE_KEY } from "~/test/test-utils";
import { authApi } from "~/lib/api";
import Home from "./home";

vi.mock("~/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("~/lib/api")>();
  return {
    ...actual,
    authApi: { ...actual.authApi, checkSession: vi.fn() },
  };
});

const loginStub = { path: "/login", element: <div>Login page</div> };

beforeEach(() => {
  localStorage.clear();
  vi.mocked(authApi.checkSession).mockReset();
});

describe("Home", () => {
  it("redirects to /login when unauthenticated", () => {
    renderWithProviders(<Home />, { route: "/", redirectStubs: [loginStub] });

    expect(screen.getByText("Login page")).toBeInTheDocument();
    expect(screen.queryByText(/Welcome,/)).not.toBeInTheDocument();
  });

  it("greets the authenticated user and reports a successful session check", async () => {
    seedAuthenticatedUser({ name: "Ada Lovelace" });
    vi.mocked(authApi.checkSession).mockResolvedValueOnce(undefined);

    renderWithProviders(<Home />, { route: "/" });

    expect(screen.getByText("Welcome, Ada Lovelace")).toBeInTheDocument();
    expect(screen.getByText("Checking session against the API…")).toBeInTheDocument();
    expect(await screen.findByText("GET /user → 200 (JWT accepted)")).toBeInTheDocument();
  });

  it("reports a failed session check on a network/server error", async () => {
    seedAuthenticatedUser({ name: "Ada Lovelace" });
    vi.mocked(authApi.checkSession).mockRejectedValueOnce(new Error("network error"));

    renderWithProviders(<Home />, { route: "/" });

    expect(await screen.findByText("GET /user failed - network or server error")).toBeInTheDocument();
  });

  it("logs out and clears the stored session on logout", async () => {
    seedAuthenticatedUser({ name: "Ada Lovelace" });
    vi.mocked(authApi.checkSession).mockResolvedValueOnce(undefined);
    const user = userEvent.setup();

    renderWithProviders(<Home />, { route: "/", redirectStubs: [loginStub] });
    await screen.findByText("Welcome, Ada Lovelace");

    await user.click(screen.getByRole("button", { name: "Log out" }));

    expect(await screen.findByText("Login page")).toBeInTheDocument();
    expect(screen.queryByText(/Welcome,/)).not.toBeInTheDocument();
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });
});
