import { act, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { makeToken, STORAGE_KEY } from "~/test/test-utils";
import { AuthProvider, useAuth } from "./auth-context";

// AuthProvider wires api.ts's global unauthorized handler to its own logout
// on mount. Mocking setUnauthorizedHandler lets tests capture that handler
// and invoke it directly, instead of forcing a real 401 through fetch.
let capturedUnauthorizedHandler: (() => void) | null = null;

vi.mock("./api", async (importOriginal) => {
  const actual = await importOriginal<typeof import("./api")>();
  return {
    ...actual,
    setUnauthorizedHandler: vi.fn((handler: (() => void) | null) => {
      capturedUnauthorizedHandler = handler;
    }),
  };
});

function Probe() {
  const { user, email, initialized, login, logout } = useAuth();
  return (
    <div>
      <span data-testid="initialized">{String(initialized)}</span>
      <span data-testid="user">{user ? user.name : "none"}</span>
      <span data-testid="email">{email ?? "none"}</span>
      <button onClick={() => login({ name: "Ada", token: makeToken({ sub: "ada@example.com" }) })}>
        login
      </button>
      <button onClick={() => logout()}>logout</button>
    </div>
  );
}

function renderProvider() {
  return render(
    <AuthProvider>
      <Probe />
    </AuthProvider>
  );
}

describe("AuthProvider", () => {
  beforeEach(() => {
    localStorage.clear();
    capturedUnauthorizedHandler = null;
  });

  afterEach(() => {
    vi.useRealTimers();
  });

  describe("localStorage hydration", () => {
    it("hydrates the user from a valid stored token", () => {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ name: "Ada", token: makeToken({ sub: "ada@example.com" }) })
      );

      renderProvider();

      expect(screen.getByTestId("initialized")).toHaveTextContent("true");
      expect(screen.getByTestId("user")).toHaveTextContent("Ada");
      expect(screen.getByTestId("email")).toHaveTextContent("ada@example.com");
    });

    it("drops an expired stored token and does not hydrate", () => {
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({
          name: "Ada",
          token: makeToken({ exp: Math.floor(Date.now() / 1000) - 60 }),
        })
      );

      renderProvider();

      expect(screen.getByTestId("initialized")).toHaveTextContent("true");
      expect(screen.getByTestId("user")).toHaveTextContent("none");
      expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    });

    it("clears malformed JSON in storage and does not hydrate", () => {
      localStorage.setItem(STORAGE_KEY, "{not valid json");

      renderProvider();

      expect(screen.getByTestId("initialized")).toHaveTextContent("true");
      expect(screen.getByTestId("user")).toHaveTextContent("none");
      expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    });

    it("marks initialized even when nothing is stored", () => {
      renderProvider();

      expect(screen.getByTestId("initialized")).toHaveTextContent("true");
      expect(screen.getByTestId("user")).toHaveTextContent("none");
    });
  });

  describe("login/logout persistence", () => {
    it("login() updates state and persists to localStorage", async () => {
      const user = userEvent.setup();
      renderProvider();

      await user.click(screen.getByText("login"));

      expect(screen.getByTestId("user")).toHaveTextContent("Ada");
      expect(JSON.parse(localStorage.getItem(STORAGE_KEY)!)).toMatchObject({ name: "Ada" });
    });

    it("logout() clears state and removes the stored token", async () => {
      const user = userEvent.setup();
      renderProvider();

      await user.click(screen.getByText("login"));
      await user.click(screen.getByText("logout"));

      expect(screen.getByTestId("user")).toHaveTextContent("none");
      expect(localStorage.getItem(STORAGE_KEY)).toBeNull();
    });
  });

  describe("setUnauthorizedHandler wiring", () => {
    it("registers a handler on mount that logs the user out when invoked", async () => {
      const user = userEvent.setup();
      renderProvider();

      await user.click(screen.getByText("login"));
      expect(screen.getByTestId("user")).toHaveTextContent("Ada");
      expect(capturedUnauthorizedHandler).toBeInstanceOf(Function);

      act(() => capturedUnauthorizedHandler?.());

      expect(screen.getByTestId("user")).toHaveTextContent("none");
    });

    it("clears the handler on unmount", () => {
      const { unmount } = renderProvider();
      expect(capturedUnauthorizedHandler).toBeInstanceOf(Function);

      unmount();

      expect(capturedUnauthorizedHandler).toBeNull();
    });
  });

  describe("idle-expiry interval", () => {
    it("logs the user out once the stored token expires while the tab is idle", async () => {
      vi.useFakeTimers();
      const now = Date.now();
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ name: "Ada", token: makeToken({ exp: Math.floor(now / 1000) + 10 }) })
      );

      renderProvider();
      expect(screen.getByTestId("user")).toHaveTextContent("Ada");

      await act(async () => {
        vi.setSystemTime(now + 11_000);
        await vi.advanceTimersByTimeAsync(30_000);
      });

      expect(screen.getByTestId("user")).toHaveTextContent("none");
    });

    it("does not log out while the stored token is still valid", async () => {
      vi.useFakeTimers();
      const now = Date.now();
      localStorage.setItem(
        STORAGE_KEY,
        JSON.stringify({ name: "Ada", token: makeToken({ exp: Math.floor(now / 1000) + 3600 }) })
      );

      renderProvider();

      await act(async () => {
        await vi.advanceTimersByTimeAsync(30_000);
      });

      expect(screen.getByTestId("user")).toHaveTextContent("Ada");
    });
  });
});
