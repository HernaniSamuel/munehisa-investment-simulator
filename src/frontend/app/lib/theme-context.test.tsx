import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { THEME_STORAGE_KEY } from "~/test/test-utils";
import { ThemeProvider, useTheme } from "./theme-context";

function Probe() {
  const { theme, setTheme } = useTheme();
  return (
    <div>
      <span data-testid="theme">{theme}</span>
      <button onClick={() => setTheme("zankyo")}>zankyo</button>
      <button onClick={() => setTheme("sumi")}>sumi</button>
    </div>
  );
}

function renderProvider() {
  return render(
    <ThemeProvider>
      <Probe />
    </ThemeProvider>
  );
}

describe("ThemeProvider", () => {
  beforeEach(() => {
    localStorage.clear();
    delete document.documentElement.dataset.theme;
  });

  describe("localStorage hydration", () => {
    it("defaults to sumi with nothing stored", () => {
      renderProvider();

      expect(screen.getByTestId("theme")).toHaveTextContent("sumi");
    });

    it("hydrates a stored zankyo preference on mount", () => {
      localStorage.setItem(THEME_STORAGE_KEY, "zankyo");

      renderProvider();

      expect(screen.getByTestId("theme")).toHaveTextContent("zankyo");
    });

    it("ignores an invalid stored value and stays on sumi", () => {
      localStorage.setItem(THEME_STORAGE_KEY, "not-a-real-theme");

      renderProvider();

      expect(screen.getByTestId("theme")).toHaveTextContent("sumi");
    });
  });

  describe("setTheme persistence and app-wide repaint", () => {
    it("setTheme('zankyo') updates state, persists it, and sets data-theme on <html>", async () => {
      const user = userEvent.setup();
      renderProvider();

      await user.click(screen.getByText("zankyo"));

      expect(screen.getByTestId("theme")).toHaveTextContent("zankyo");
      expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe("zankyo");
      expect(document.documentElement.dataset.theme).toBe("zankyo");
    });

    it("setTheme('sumi') restores the light palette and its persisted value", async () => {
      localStorage.setItem(THEME_STORAGE_KEY, "zankyo");
      const user = userEvent.setup();
      renderProvider();
      expect(document.documentElement.dataset.theme).toBe("zankyo");

      await user.click(screen.getByText("sumi"));

      expect(screen.getByTestId("theme")).toHaveTextContent("sumi");
      expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe("sumi");
      expect(document.documentElement.dataset.theme).toBe("sumi");
    });
  });

  it("useTheme throws when used outside a ThemeProvider", () => {
    const consoleError = vi.spyOn(console, "error").mockImplementation(() => {});

    expect(() => render(<Probe />)).toThrow("useTheme must be used within ThemeProvider");

    consoleError.mockRestore();
  });
});
