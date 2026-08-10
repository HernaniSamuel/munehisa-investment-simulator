import { expect, test, type Locator, type Page } from "@playwright/test";
import { DASHBOARD_ROUTE, mockSimulationApi, ROUTES, seedAuth, VIEWPORTS } from "./support";

// Acceptance criterion: "all modals remain within the viewport bounds (no horizontal or
// vertical overflow past the screen edge) at 320px width."
async function expectDialogWithinViewport(page: Page, dialog: Locator) {
  await expect(dialog).toBeVisible();
  const box = await dialog.boundingBox();
  if (!box) throw new Error("Expected the dialog to have a bounding box");
  const viewport = page.viewportSize();
  if (!viewport) throw new Error("Expected a viewport size");

  expect(box.x).toBeGreaterThanOrEqual(0);
  expect(box.y).toBeGreaterThanOrEqual(0);
  expect(box.x + box.width).toBeLessThanOrEqual(viewport.width + 1);
  expect(box.y + box.height).toBeLessThanOrEqual(viewport.height + 1);
}

test.describe("modals stay within the viewport at 320px", () => {
  test.use({ viewport: VIEWPORTS.iphone5 });

  test("settings: delete account", async ({ page }) => {
    await seedAuth(page);
    await page.goto(ROUTES.settings.path);
    await page.getByRole("button", { name: "Delete account" }).click();
    await expectDialogWithinViewport(page, page.getByRole("dialog", { name: /Confirm deletion/ }));
  });

  test("home: delete simulation", async ({ page }) => {
    await seedAuth(page);
    await mockSimulationApi(page);
    await page.goto(ROUTES.home.path);
    await page.getByTestId("simulation-card-sim-1").getByRole("button", { name: "Delete" }).click();
    await expectDialogWithinViewport(page, page.getByRole("dialog"));
  });

  test("home: create simulation", async ({ page }) => {
    await seedAuth(page);
    await mockSimulationApi(page);
    await page.goto(ROUTES.home.path);
    await page.getByRole("button", { name: "New Simulation" }).click();
    await expectDialogWithinViewport(page, page.getByRole("dialog", { name: /New simulation/ }));
  });

  test("simulation-dashboard: cash movement (contribute)", async ({ page }) => {
    await seedAuth(page);
    await mockSimulationApi(page);
    await page.goto(DASHBOARD_ROUTE);
    await page.getByRole("button", { name: /Contribute/ }).click();
    await expectDialogWithinViewport(page, page.getByRole("dialog"));
  });

  test("simulation-dashboard: advance month", async ({ page }) => {
    await seedAuth(page);
    await mockSimulationApi(page);
    await page.goto(DASHBOARD_ROUTE);
    await page.getByRole("button", { name: /Advance month/ }).click();
    await expectDialogWithinViewport(page, page.getByRole("dialog", { name: /Advance to the next month/ }));
  });

  test("simulation-dashboard: reset month", async ({ page }) => {
    await seedAuth(page);
    await mockSimulationApi(page);
    await page.goto(DASHBOARD_ROUTE);
    await page.getByRole("button", { name: "⟲ Reset" }).click();
    await expectDialogWithinViewport(page, page.getByRole("dialog", { name: /Reset this month/ }));
  });
});
