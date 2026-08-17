import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useLocation } from "react-router";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "~/test/test-utils";
import { ApiError, authApi } from "~/lib/api";
import i18n from "~/lib/i18n";
import Register from "./register";

vi.mock("~/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("~/lib/api")>();
  return {
    ...actual,
    authApi: { ...actual.authApi, register: vi.fn() },
  };
});

// Reads the router state Register passes on redirect, so the test can assert
// on the actual navigation + message instead of just the API call.
function LoginRedirectStub() {
  const location = useLocation();
  const state = location.state as { message?: string } | null;
  return <div>Login page{state?.message ? `: ${state.message}` : ""}</div>;
}

const loginStub = { path: "/login", element: <LoginRedirectStub /> };

beforeEach(() => {
  vi.mocked(authApi.register).mockReset();
});

async function fillForm(
  user: ReturnType<typeof userEvent.setup>,
  {
    name = "Ada Lovelace",
    email = "ada@example.com",
    password = "hunter22",
    confirmPassword = password,
  }: { name?: string; email?: string; password?: string; confirmPassword?: string } = {}
) {
  await user.type(screen.getByLabelText("Name"), name);
  await user.type(screen.getByLabelText("Email"), email);
  await user.type(screen.getByLabelText("Password"), password);
  await user.type(screen.getByLabelText("Confirm password"), confirmPassword);
}

describe("Register", () => {
  it("registers and redirects to /login with a confirmation message on success", async () => {
    vi.mocked(authApi.register).mockResolvedValueOnce(undefined);
    const user = userEvent.setup();
    renderWithProviders(<Register />, { route: "/register", redirectStubs: [loginStub] });

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "▸▸ Create account" }));

    expect(
      await screen.findByText(
        /Login page: Account created\. Check your inbox to verify your email before signing in\. If you don't see it, check your spam or junk folder\./
      )
    ).toBeInTheDocument();
    expect(authApi.register).toHaveBeenCalledWith({
      name: "Ada Lovelace",
      email: "ada@example.com",
      password: "hunter22",
    });
  });

  it("shows an inline error when the API rejects registration", async () => {
    vi.mocked(authApi.register).mockRejectedValueOnce(new ApiError(409, "Email already in use"));
    const user = userEvent.setup();
    renderWithProviders(<Register />, { route: "/register" });

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "▸▸ Create account" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Email already in use");
  });

  it("rejects mismatched passwords client-side without calling the API", async () => {
    const user = userEvent.setup();
    renderWithProviders(<Register />, { route: "/register" });

    await fillForm(user, { password: "hunter22", confirmPassword: "different1" });
    await user.click(screen.getByRole("button", { name: "▸▸ Create account" }));

    expect(await screen.findByText("Passwords do not match.")).toBeInTheDocument();
    expect(authApi.register).not.toHaveBeenCalled();
  });

  it("disables the submit button and shows a loading label while submitting", async () => {
    let resolveRegister!: () => void;
    vi.mocked(authApi.register).mockReturnValueOnce(
      new Promise((resolve) => {
        resolveRegister = resolve;
      })
    );
    const user = userEvent.setup();
    renderWithProviders(<Register />, { route: "/register" });

    await fillForm(user);
    await user.click(screen.getByRole("button", { name: "▸▸ Create account" }));

    expect(screen.getByRole("button", { name: "Creating account…" })).toBeDisabled();
    await act(async () => {
      resolveRegister();
    });
  });

  it("gives the password and confirm-password fields independent visibility toggles", async () => {
    const user = userEvent.setup();
    renderWithProviders(<Register />, { route: "/register" });

    const passwordInput = screen.getByLabelText("Password");
    const confirmInput = screen.getByLabelText("Confirm password");
    const passwordToggle = passwordInput.parentElement!.querySelector("button")!;
    const confirmToggle = confirmInput.parentElement!.querySelector("button")!;

    await user.click(passwordToggle);

    expect(passwordInput).toHaveAttribute("type", "text");
    expect(passwordToggle).toHaveAttribute("aria-label", "Hide password");
    expect(confirmInput).toHaveAttribute("type", "password");
    expect(confirmToggle).toHaveAttribute("aria-label", "Show password");
  });

  it("renders in pt-BR when that's the active locale", async () => {
    await act(async () => {
      await i18n.changeLanguage("pt-BR");
    });
    renderWithProviders(<Register />, { route: "/register" });

    expect(screen.getByText("Registrar")).toBeInTheDocument();
    expect(screen.getByLabelText("Nome")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "▸▸ Criar conta" })).toBeInTheDocument();
  });
});
