import { screen } from "@testing-library/react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders, STORAGE_KEY } from "~/test/test-utils";
import { ApiError, authApi } from "~/lib/api";
import VerifyEmail from "./verify-email";

vi.mock("~/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("~/lib/api")>();
  return {
    ...actual,
    authApi: { ...actual.authApi, verifyEmail: vi.fn() },
  };
});

beforeEach(() => {
  localStorage.clear();
  vi.mocked(authApi.verifyEmail).mockReset();
});

describe("VerifyEmail", () => {
  it("shows an error immediately when the URL has no token", () => {
    renderWithProviders(<VerifyEmail />, { route: "/verify-email" });

    expect(screen.getByRole("alert")).toHaveTextContent("This verification link is missing its token.");
    expect(authApi.verifyEmail).not.toHaveBeenCalled();
  });

  it("shows a verifying state while the request is in flight", () => {
    vi.mocked(authApi.verifyEmail).mockReturnValueOnce(new Promise(() => {}));
    renderWithProviders(<VerifyEmail />, { route: "/verify-email?token=verify-tok" });

    // "verifying" is also the component's synchronous initial state
    // whenever a token is present, so also assert the request actually
    // fired - otherwise this test would pass even if the effect calling
    // authApi.verifyEmail were deleted entirely.
    expect(screen.getByText("Verifying your email…")).toBeInTheDocument();
    expect(authApi.verifyEmail).toHaveBeenCalledWith("verify-tok");
  });

  it("logs the user in and shows success once verified", async () => {
    vi.mocked(authApi.verifyEmail).mockResolvedValueOnce({ name: "Ada", token: "jwt" });
    renderWithProviders(<VerifyEmail />, { route: "/verify-email?token=verify-tok" });

    expect(await screen.findByText("Your email has been verified.")).toBeInTheDocument();
    expect(authApi.verifyEmail).toHaveBeenCalledWith("verify-tok");
    expect(authApi.verifyEmail).toHaveBeenCalledTimes(1);
    // login(response) persists the session - confirms that side effect, not
    // just the success banner text.
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toMatchObject({ name: "Ada", token: "jwt" });
  });

  it("shows an inline error when verification fails", async () => {
    vi.mocked(authApi.verifyEmail).mockRejectedValueOnce(new ApiError(400, "Token expired"));
    renderWithProviders(<VerifyEmail />, { route: "/verify-email?token=verify-tok" });

    expect(await screen.findByRole("alert")).toHaveTextContent("Token expired");
  });
});
