import { screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import {
  mockFetchOnce,
  renderWithProviders,
  seedAuthenticatedUser,
  seedTheme,
  STORAGE_KEY,
  THEME_STORAGE_KEY,
} from "~/test/test-utils";
import { ApiError, authApi, userApi } from "~/lib/api";
import Settings from "./settings";

// deleteAccount is deliberately NOT mocked here (unlike updateName/
// forgotPassword below) - the DeleteAccountSection tests need it to run its
// real implementation, which is what actually carries the
// skipUnauthorizedHandling flag being tested. They drive it via a mocked
// fetch instead (see mockFetchOnce usage below).
vi.mock("~/lib/api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("~/lib/api")>();
  return {
    ...actual,
    authApi: { ...actual.authApi, forgotPassword: vi.fn() },
    userApi: { ...actual.userApi, updateName: vi.fn() },
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
});

describe("ChangeNameSection", () => {
  it("saves the new name, persists it to the session, on success", async () => {
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
    // login({ name: result.name, token: user.token }) re-persists the
    // session under the new name - confirms that side effect, not just the
    // local input value.
    expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toMatchObject({
      name: "Ada Byron",
      token: seededUser.token,
    });
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

describe("ThemeSection", () => {
  it("shows Sumi as the selected theme by default", () => {
    renderSettings();

    expect(screen.getByRole("button", { name: "Sumi" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Zankyō" })).toHaveAttribute("aria-pressed", "false");
  });

  it("selecting Zankyō marks it as selected and persists the preference", async () => {
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole("button", { name: "Zankyō" }));

    expect(screen.getByRole("button", { name: "Zankyō" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "Sumi" })).toHaveAttribute("aria-pressed", "false");
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe("zankyo");
  });

  it("renders with Zankyō already selected when it was the persisted preference", () => {
    seedTheme("zankyo");
    renderSettings();

    expect(screen.getByRole("button", { name: "Zankyō" })).toHaveAttribute("aria-pressed", "true");
  });

  it("switching back to Sumi restores the selection and persists it", async () => {
    seedTheme("zankyo");
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole("button", { name: "Sumi" }));

    expect(screen.getByRole("button", { name: "Sumi" })).toHaveAttribute("aria-pressed", "true");
    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe("sumi");
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
  beforeEach(() => {
    vi.stubGlobal("fetch", vi.fn());
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("opens the confirmation modal and closes it on cancel", async () => {
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole("button", { name: "Delete account" }));
    const dialog = screen.getByRole("dialog", { name: "Confirm deletion" });
    expect(dialog).toBeInTheDocument();

    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));

    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });

  it("styles Cancel as vermilion/red and Confirm as a solid ink fill", async () => {
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole("button", { name: "Delete account" }));
    const dialog = screen.getByRole("dialog", { name: "Confirm deletion" });

    const cancelButton = within(dialog).getByRole("button", { name: "Cancel" });
    const confirmButton = within(dialog).getByRole("button", { name: "Delete account" });

    expect(cancelButton).toHaveClass("bg-vermilion");
    expect(confirmButton).toHaveClass("bg-ink", "text-paper");
    expect(confirmButton).not.toHaveClass("bg-vermilion");
    expect(confirmButton).not.toHaveClass("border-ink/25");
  });

  it("uses a new-password autoComplete value so the browser doesn't offer saved-credential suggestions", async () => {
    // Standard cross-browser trick (issue #111): "current-password" makes
    // Chromium-family browsers offer a saved-credentials dropdown on this
    // field, which doesn't belong here since this is a re-entry field, not a
    // login. In some Chromium-family browsers (observed in Brave) this
    // attribute alone isn't enough - see the readOnly-until-focus test below
    // for the other half of the fix. The matching reveal-icon suppression
    // (app.css's #delete-password::-ms-reveal rule) is browser-rendering
    // behavior that can't be observed in jsdom - see the PR description's
    // manual verification note.
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole("button", { name: "Delete account" }));
    const dialog = screen.getByRole("dialog", { name: "Confirm deletion" });
    const passwordField = within(dialog).getByLabelText("Password");

    expect(passwordField).toHaveAttribute("autocomplete", "new-password");
    expect(passwordField).toHaveAttribute("id", "delete-password");
  });

  it("clears readOnly once the field is auto-focused on open, after blocking Chromium's credential-suggestion dropdown at the initial focus", async () => {
    // Chromium-family browsers decide whether to show their saved-credential
    // dropdown at the moment a password field is focused, using the DOM's
    // readOnly attribute at that instant. The field renders readOnly, and the
    // modal's autofocus effect focuses it as soon as it opens; the onFocus
    // handler then clears readOnly so typing still works normally (exercised
    // by the "deletes the account..." test below, which types into it).
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole("button", { name: "Delete account" }));
    const dialog = screen.getByRole("dialog", { name: "Confirm deletion" });
    const passwordField = within(dialog).getByLabelText("Password");

    expect(passwordField).not.toHaveAttribute("readonly");
  });

  it("keeps the modal open with an inline error on a wrong password, without triggering a global logout", async () => {
    // Goes through the real userApi.deleteAccount -> request() rather than a
    // mocked function, so this actually exercises the
    // skipUnauthorizedHandling flag api.ts sets on this call - removing that
    // flag would make this test fail (a real 401 with a token would
    // otherwise fire the global unauthorized handler and log the user out,
    // same as the "does not trigger the unauthorized handler when
    // deleteAccount gets the wrong password" regression test in api.test.ts,
    // just proven here at the component/UX level instead of the API level).
    mockFetchOnce(401, { status: "UNAUTHORIZED", message: "Invalid Credentials" });
    const user = userEvent.setup();
    renderSettings();

    await user.click(screen.getByRole("button", { name: "Delete account" }));
    const dialog = screen.getByRole("dialog", { name: "Confirm deletion" });
    await user.type(within(dialog).getByLabelText("Password"), "wrong-password");
    await user.click(within(dialog).getByRole("button", { name: "Delete account" }));

    expect(await within(dialog).findByText("Invalid Credentials")).toBeInTheDocument();
    // Still on the settings screen, modal still open, session untouched.
    expect(screen.getByRole("dialog")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Settings" })).toBeInTheDocument();
    expect(localStorage.getItem(STORAGE_KEY)).not.toBeNull();

    const [url, init] = (globalThis.fetch as ReturnType<typeof vi.fn>).mock.calls[0];
    expect(url).toContain("/user/delete");
    expect(JSON.parse((init as RequestInit).body as string)).toEqual({ password: "wrong-password" });
    expect((init as RequestInit & { headers: Record<string, string> }).headers.Authorization).toBe(
      `Bearer ${seededUser.token}`
    );
  });

  it("deletes the account and logs out on a correct password", async () => {
    mockFetchOnce(204);
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
    expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
  });
});
