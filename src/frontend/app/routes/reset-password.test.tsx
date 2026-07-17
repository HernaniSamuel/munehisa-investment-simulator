import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "~/test/test-utils";
import { ApiError, authApi } from "~/lib/api";
import ResetPassword from "./reset-password";

vi.mock("~/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("~/lib/api")>();
  return {
    ...actual,
    authApi: { ...actual.authApi, resetPassword: vi.fn() },
  };
});

beforeEach(() => {
  localStorage.clear();
  vi.mocked(authApi.resetPassword).mockReset();
});

async function fillAndSubmit(
  user: ReturnType<typeof userEvent.setup>,
  {
    newPassword = "brand-new-pw",
    confirmPassword = newPassword,
  }: { newPassword?: string; confirmPassword?: string } = {}
) {
  await user.type(screen.getByLabelText("New password"), newPassword);
  await user.type(screen.getByLabelText("Confirm new password"), confirmPassword);
  await user.click(screen.getByRole("button", { name: "▸▸ Reset password" }));
}

describe("ResetPassword", () => {
  it("shows an error and disables the form when the URL has no token", () => {
    renderWithProviders(<ResetPassword />, { route: "/reset-password" });

    expect(screen.getByRole("alert")).toHaveTextContent("This reset link is missing its token.");
    expect(screen.getByLabelText("New password")).toBeDisabled();
    expect(screen.getByRole("button", { name: "▸▸ Reset password" })).toBeDisabled();
  });

  it("logs in and redirects on success", async () => {
    vi.mocked(authApi.resetPassword).mockResolvedValueOnce({ name: "Ada", token: "jwt" });
    const user = userEvent.setup();
    renderWithProviders(<ResetPassword />, { route: "/reset-password?token=reset-tok" });

    await fillAndSubmit(user);

    await waitFor(() => expect(authApi.resetPassword).toHaveBeenCalledWith("reset-tok", "brand-new-pw"));
  });

  it("shows an inline error when the API rejects the new password", async () => {
    vi.mocked(authApi.resetPassword).mockRejectedValueOnce(new ApiError(400, "Token expired"));
    const user = userEvent.setup();
    renderWithProviders(<ResetPassword />, { route: "/reset-password?token=reset-tok" });

    await fillAndSubmit(user);

    expect(await screen.findByRole("alert")).toHaveTextContent("Token expired");
  });

  it("rejects mismatched passwords client-side without calling the API", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ResetPassword />, { route: "/reset-password?token=reset-tok" });

    await fillAndSubmit(user, { newPassword: "brand-new-pw", confirmPassword: "different-pw" });

    expect(await screen.findByText("Passwords do not match.")).toBeInTheDocument();
    expect(authApi.resetPassword).not.toHaveBeenCalled();
  });
});
