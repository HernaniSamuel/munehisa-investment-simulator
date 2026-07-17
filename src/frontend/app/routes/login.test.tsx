import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders, STORAGE_KEY } from "~/test/test-utils";
import { ApiError, authApi } from "~/lib/api";
import Login from "./login";

vi.mock("~/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("~/lib/api")>();
  return {
    ...actual,
    authApi: { ...actual.authApi, login: vi.fn(), resendVerification: vi.fn() },
  };
});

beforeEach(() => {
  localStorage.clear();
  vi.mocked(authApi.login).mockReset();
  vi.mocked(authApi.resendVerification).mockReset();
});

async function fillAndSubmit(user: ReturnType<typeof userEvent.setup>, email: string, password: string) {
  await user.type(screen.getByLabelText("Email"), email);
  await user.type(screen.getByLabelText("Password"), password);
  await user.click(screen.getByRole("button", { name: "▸▸ Log in" }));
}

describe("Login", () => {
  it("logs in, persists the session, and redirects to / on success", async () => {
    vi.mocked(authApi.login).mockResolvedValueOnce({ name: "Ada", token: "jwt" });
    const user = userEvent.setup();
    renderWithProviders(<Login />, {
      route: "/login",
      redirectStubs: [{ path: "/", element: <div>Home page</div> }],
    });

    await fillAndSubmit(user, "ada@example.com", "hunter22");

    expect(authApi.login).toHaveBeenCalledWith({ email: "ada@example.com", password: "hunter22" });
    expect(await screen.findByText("Home page")).toBeInTheDocument();
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toMatchObject({ name: "Ada", token: "jwt" });
  });

  it("shows an inline error on failure", async () => {
    vi.mocked(authApi.login).mockRejectedValueOnce(new ApiError(401, "Invalid Credentials"));
    const user = userEvent.setup();
    renderWithProviders(<Login />, { route: "/login" });

    await fillAndSubmit(user, "ada@example.com", "wrong");

    expect(await screen.findByRole("alert")).toHaveTextContent("Invalid Credentials");
  });

  it("disables the submit button and shows a loading label while submitting", async () => {
    let resolveLogin!: (value: { name: string; token: string }) => void;
    vi.mocked(authApi.login).mockReturnValueOnce(
      new Promise((resolve) => {
        resolveLogin = resolve;
      })
    );
    const user = userEvent.setup();
    renderWithProviders(<Login />, { route: "/login" });

    await fillAndSubmit(user, "ada@example.com", "hunter22");

    expect(screen.getByRole("button", { name: "Signing in…" })).toBeDisabled();
    // Flush the resolution (and the state updates it triggers) inside act()
    // before the test ends, so React doesn't warn about updates happening
    // after the test has already made its assertions.
    await act(async () => {
      resolveLogin({ name: "Ada", token: "jwt" });
    });
  });

  it("resends the verification email on success", async () => {
    vi.mocked(authApi.resendVerification).mockResolvedValueOnce(undefined);
    const user = userEvent.setup();
    renderWithProviders(<Login />, { route: "/login" });

    await user.type(screen.getByLabelText("Email"), "ada@example.com");
    await user.click(screen.getByRole("button", { name: "Resend verification email" }));

    expect(await screen.findByText("Verification email sent. Check your inbox.")).toBeInTheDocument();
  });

  it("shows an error when resending without an email first", async () => {
    const user = userEvent.setup();
    renderWithProviders(<Login />, { route: "/login" });

    await user.click(screen.getByRole("button", { name: "Resend verification email" }));

    expect(await screen.findByText("Enter your email above first.")).toBeInTheDocument();
    expect(authApi.resendVerification).not.toHaveBeenCalled();
  });

  it("shows the message passed via router state (e.g. after registering)", () => {
    renderWithProviders(<Login />, {
      route: { pathname: "/login", state: { message: "Account created. Check your inbox." } },
    });

    expect(screen.getByText("Account created. Check your inbox.")).toBeInTheDocument();
  });
});
