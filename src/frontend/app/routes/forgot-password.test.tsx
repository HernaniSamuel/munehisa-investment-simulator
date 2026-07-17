import { act, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "~/test/test-utils";
import { ApiError, authApi } from "~/lib/api";
import ForgotPassword from "./forgot-password";

vi.mock("~/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("~/lib/api")>();
  return {
    ...actual,
    authApi: { ...actual.authApi, forgotPassword: vi.fn() },
  };
});

beforeEach(() => {
  vi.mocked(authApi.forgotPassword).mockReset();
});

async function submit(user: ReturnType<typeof userEvent.setup>, email = "ada@example.com") {
  await user.type(screen.getByLabelText("Email"), email);
  await user.click(screen.getByRole("button", { name: "▸▸ Send reset link" }));
}

describe("ForgotPassword", () => {
  it("shows a generic success message so it doesn't leak account existence", async () => {
    vi.mocked(authApi.forgotPassword).mockResolvedValueOnce(undefined);
    const user = userEvent.setup();
    renderWithProviders(<ForgotPassword />, { route: "/forgot-password" });

    await submit(user);

    expect(
      await screen.findByText("If that email has a verified account, a reset link is on its way.")
    ).toBeInTheDocument();
  });

  it("shows the resend-availability message when already pending (429)", async () => {
    vi.mocked(authApi.forgotPassword).mockResolvedValueOnce({
      message: "Already sent",
      resendAvailableAt: "2026-01-01T00:00:00Z",
    });
    const user = userEvent.setup();
    renderWithProviders(<ForgotPassword />, { route: "/forgot-password" });

    await submit(user);

    expect(await screen.findByText(/A reset email was already sent\. Try again after/)).toBeInTheDocument();
  });

  it("shows an inline error when the API call fails", async () => {
    vi.mocked(authApi.forgotPassword).mockRejectedValueOnce(new ApiError(500, "Request failed (500)"));
    const user = userEvent.setup();
    renderWithProviders(<ForgotPassword />, { route: "/forgot-password" });

    await submit(user);

    expect(await screen.findByRole("alert")).toHaveTextContent("Request failed (500)");
  });

  it("disables the submit button and shows a loading label while submitting", async () => {
    let resolveRequest!: (value: undefined) => void;
    vi.mocked(authApi.forgotPassword).mockReturnValueOnce(
      new Promise((resolve) => {
        resolveRequest = resolve;
      })
    );
    const user = userEvent.setup();
    renderWithProviders(<ForgotPassword />, { route: "/forgot-password" });

    await submit(user);

    expect(screen.getByRole("button", { name: "Sending…" })).toBeDisabled();
    await act(async () => {
      resolveRequest(undefined);
    });
  });
});
