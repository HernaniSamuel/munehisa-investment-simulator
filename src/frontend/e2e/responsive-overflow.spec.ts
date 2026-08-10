import { expect, test, type Page } from "@playwright/test";
import { DASHBOARD_ROUTE, mockSimulationApi, ROUTES, seedAuth, TRADE_ROUTE, VIEWPORTS } from "./support";

// Acceptance criterion: "the page has no horizontal overflow
// (document.documentElement.scrollWidth does not exceed the viewport width)" for each of the
// 9 routes at each of the 4 target widths.
async function expectNoHorizontalOverflow(page: Page, viewportWidth: number) {
  const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
  expect(scrollWidth).toBeLessThanOrEqual(viewportWidth);
}

const PUBLIC_ROUTES = [
  { name: "register", path: ROUTES.register.path, ready: (page: Page) => page.getByRole("heading", { name: "Register" }) },
  { name: "login", path: ROUTES.login.path, ready: (page: Page) => page.getByRole("heading", { name: "Log in" }) },
  { name: "verify-email", path: ROUTES.verifyEmail.path, ready: (page: Page) => page.getByRole("heading", { name: "Verify email" }) },
  { name: "forgot-password", path: ROUTES.forgotPassword.path, ready: (page: Page) => page.getByRole("heading", { name: "Forgot password" }) },
  { name: "reset-password", path: ROUTES.resetPassword.path, ready: (page: Page) => page.getByRole("heading", { name: "Reset password" }) },
];

const PROTECTED_ROUTES = [
  { name: "home", path: ROUTES.home.path, ready: (page: Page) => page.getByTestId("simulation-card-sim-1") },
  { name: "settings", path: ROUTES.settings.path, ready: (page: Page) => page.getByRole("heading", { name: "Settings" }) },
  { name: "simulation-dashboard", path: DASHBOARD_ROUTE, ready: (page: Page) => page.getByTestId("current-date-value") },
  { name: "trade", path: TRADE_ROUTE, ready: (page: Page) => page.getByTestId("trade-chart-panel") },
];

for (const [widthName, viewport] of Object.entries(VIEWPORTS)) {
  test.describe(`no horizontal overflow at ${widthName} (${viewport.width}px)`, () => {
    test.use({ viewport });

    for (const route of PUBLIC_ROUTES) {
      test(route.name, async ({ page }) => {
        await page.goto(route.path);
        await expect(route.ready(page)).toBeVisible();
        await expectNoHorizontalOverflow(page, viewport.width);
      });
    }

    for (const route of PROTECTED_ROUTES) {
      test(route.name, async ({ page }) => {
        await seedAuth(page);
        await mockSimulationApi(page);
        await page.goto(route.path);
        await expect(route.ready(page)).toBeVisible();
        await expectNoHorizontalOverflow(page, viewport.width);
      });
    }
  });
}
