import { expect, test } from "@playwright/test";
import { ROUTES } from "./support";

// Acceptance criteria (issue #103): the scrollbar thumb/track use the app's --color-muted /
// --color-panel tokens, not the browser default and not new hardcoded colors. Resolves the
// tokens in-page via a throwaway probe element rather than hardcoding an expected rgb() here,
// so the assertion stays correct if the tokens' hex values ever change.
test.describe("app.css scrollbar theme", () => {
  test("html scrollbar-color resolves to the muted/panel tokens", async ({ page }) => {
    await page.goto(ROUTES.login.path);
    await expect(page.getByRole("heading", { name: "Log in" })).toBeVisible();

    const { scrollbarColor, mutedRgb, panelRgb } = await page.evaluate(() => {
      const probe = document.createElement("div");
      document.body.appendChild(probe);

      probe.style.color = "var(--color-muted)";
      const mutedRgb = getComputedStyle(probe).color;

      probe.style.color = "var(--color-panel)";
      const panelRgb = getComputedStyle(probe).color;

      probe.remove();

      return {
        scrollbarColor: getComputedStyle(document.documentElement).scrollbarColor,
        mutedRgb,
        panelRgb,
      };
    });

    expect(scrollbarColor).toBe(`${mutedRgb} ${panelRgb}`);
  });
});
