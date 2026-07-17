import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders, seedAuthenticatedUser, STORAGE_KEY } from "~/test/test-utils";
import { ApiError, authApi, userApi } from "~/lib/api";
import Settings from "./settings";

vi.mock("~/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("~/lib/api")>();
  return {
    ...actual,
    authApi: { ...actual.authApi, forgotPassword: vi.fn() },
    userApi: { ...actual.userApi, updateName: vi.fn(), deleteAccount: vi.fn() },
  };
});

const loginStub = { path: "/login", element: <div>Login page</div> };

let seededUser: { name: string; token: string };

function renderSettings() {
  return renderWithProviders(<Settings />, { route: "/settings" });
}

beforeEach(() => {
  localStorage.clear();
  seededUser = seedAuthenticatedUser({ name: "Ada Lovelace" });
  vi.mocked(authApi.forgotPassword).mockReset();
  vi.mocked(userApi.updateName).mockReset();
  vi.mocked(userApi.deleteAccount).mockReset();
});

describe("ChangeNameSection", () => {
  it("saves the new name on success", async () => {
    vi.mocked(userApi.updateName).mockResolvedValueOnce({ name: "Ada Byron" });
    const user = userEvent.setup();
    renderSettings();

    const nameField = screen.getByLabelText("Name");
    await user.clear(nameField);
    await user.type(nameField, "Ada Byron");
    await user.click(screen.getByRole("button", { name: "▸▸ Save name" }));

    expect(await screen.findByText("Name updated.")).toBeInTheDocument();
    expect(nameField).toHaveValue("Ada Byron");
    expect(userApi.updateName).toHaveBeenCalledWith("Ada Byron", seededUser.token);
  });

  it("shows an inline error when the API rejects the update", async () => {
    vi.mocked(userApi.updateName).mockRejectedValueOnce(new ApiError(400, "name: must not be blank"));
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole("button", { name: "▸▸ Save name" }));

    expect(await screen.findByText("name: must not be blank")).toBeInTheDocument();
  });

  it("rejects a whitespace-only name client-side without calling the API", async () => {
    // The input's `required` attribute already blocks a truly empty submit
    // natively - the `!name.trim()` check in the component exists to catch
    // whitespace-only values, which HTML5 `required` does not.
    const user = userEvent.setup();
    renderSettings();

    const nameField = screen.getByLabelText("Name");
    await user.clear(nameField);
    await user.type(nameField, "   ");
    await user.click(screen.getByRole("button", { name: "▸▸ Save name" }));

    expect(await screen.findByText("Name cannot be blank.")).toBeInTheDocument();
    expect(userApi.updateName).not.toHaveBeenCalled();
  });
});

describe("ChangePasswordSection", () => {
  it("sends a password reset email on success", async () => {
    vi.mocked(authApi.forgotPassword).mockResolvedValueOnce(undefined);
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole("button", { name: "▸▸ Send password reset email" }));

    expect(await screen.findByText("Check your inbox for a link to reset your password.")).toBeInTheDocument();
    expect(authApi.forgotPassword).toHaveBeenCalledWith("ada@example.com");
  });

  it("shows an inline error when the API call fails", async () => {
    vi.mocked(authApi.forgotPassword).mockRejectedValueOnce(new ApiError(500, "Request failed (500)"));
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole("button", { name: "▸▸ Send password reset email" }));

    expect(await screen.findByText("Request failed (500)")).toBeInTheDocument();
  });
});

describe("DeleteAccountSection", () => {
  it("opens the confirmation modal and closes it on cancel", async () => {
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole("button", { name: "Delete account" }));
    const dialog = screen.getByRole("dialog", { name: "Confirm deletion" });
    expect(dialog).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("keeps the modal open with an inline error on a wrong password, without triggering a global logout", async () => {
    vi.mocked(userApi.deleteAccount).mockRejectedValueOnce(new ApiError(401, "Invalid Credentials"));
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole("button", { name: "Delete account" }));
    const dialog = screen.getByRole("dialog", { name: "Confirm deletion" });
    await user.type(within(dialog).getByLabelText("Password"), "wrong-password");
    await user.click(within(dialog).getByRole("button", { name: "Delete account" }));

    expect(await within(dialog).findByText("Invalid Credentials")).toBeInTheDocument();
    // Still on the settings screen, modal still open, session untouched -
    // this is the exact regression api.test.ts's "does not trigger the
    // unauthorized handler when deleteAccount gets the wrong password" test
    // guards at the API layer; this proves the UX consequence at the
    // component level.
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Settings" })).toBeInTheDocument();
    expect(localStorage.getItem(STORAGE_KEY)).not.toBeNull();
    expect(userApi.deleteAccount).toHaveBeenCalledWith("wrong-password", seededUser.token);
  });

  it("deletes the account and logs out on a correct password", async () => {
    vi.mocked(userApi.deleteAccount).mockResolvedValueOnce(undefined);
    const user = userEvent.setup();
    // The success path logs out (clearing `user`) and lets ProtectedRoute's
    // <Navigate> take it from there - it doesn't close the modal itself, so
    // this needs a real <Routes> match to actually unmount the settings
    // screen, same as home.test.tsx's authenticated->unauthenticated cases.
    renderWithProviders(<Settings />, { route: "/settings", redirectStubs: [loginStub] });

    await user.click(screen.getByRole("button", { name: "Delete account" }));
    const dialog = screen.getByRole("dialog", { name: "Confirm deletion" });
    await user.type(within(dialog).getByLabelText("Password"), "correct-password");
    await user.click(within(dialog).getByRole("button", { name: "Delete account" }));

    expect(await screen.findByText("Login page")).toBeInTheDocument();
    expect(userApi.deleteAccount).toHaveBeenCalledWith("correct-password", seededUser.token);
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });
});
