import { expect, test } from "@playwright/test";
import { DASHBOARD_ROUTE, mockSimulationApi, seedAuth, VIEWPORTS } from "./support";

async function gridColumnCount(page: import("@playwright/test").Page, testId: string): Promise<number> {
  return page.getByTestId(testId).evaluate((el) => {
    const template = getComputedStyle(el).gridTemplateColumns;
    return template.split(" ").filter(Boolean).length;
  });
}

test.describe("simulation-dashboard.tsx KPI grid", () => {
  test("renders fewer columns below 768px than at 1024px+", async ({ page }) => {
    await seedAuth(page);
    await mockSimulationApi(page);

    await page.setViewportSize(VIEWPORTS.tablet);
    await page.goto(DASHBOARD_ROUTE);
    await expect(page.getByTestId("kpi-grid")).toBeVisible();
    const columnsAtTablet = await gridColumnCount(page, "kpi-grid");

    await page.setViewportSize(VIEWPORTS.desktop);
    const columnsAtDesktop = await gridColumnCount(page, "kpi-grid");

    expect(columnsAtTablet).toBeLessThan(columnsAtDesktop);
  });
});

test.describe("simulation-dashboard.tsx positions/allocation layout", () => {
  test("stacks vertically below 768px", async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.tablet);
    await seedAuth(page);
    await mockSimulationApi(page);
    await page.goto(DASHBOARD_ROUTE);

    const positions = page.getByTestId("positions-column");
    const allocation = page.getByTestId("allocation-column");
    await expect(positions).toBeVisible();
    await expect(allocation).toBeVisible();

    const positionsBox = await positions.boundingBox();
    const allocationBox = await allocation.boundingBox();
    if (!positionsBox || !allocationBox) throw new Error("Expected both columns to have a bounding box");

    expect(allocationBox.x).toBeCloseTo(positionsBox.x, 0);
    expect(allocationBox.y).toBeGreaterThanOrEqual(positionsBox.y + positionsBox.height - 1);
  });

  test("sits side by side at 1024px+", async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.desktop);
    await seedAuth(page);
    await mockSimulationApi(page);
    await page.goto(DASHBOARD_ROUTE);

    const positions = page.getByTestId("positions-column");
    const allocation = page.getByTestId("allocation-column");
    await expect(positions).toBeVisible();
    await expect(allocation).toBeVisible();

    const positionsBox = await positions.boundingBox();
    const allocationBox = await allocation.boundingBox();
    if (!positionsBox || !allocationBox) throw new Error("Expected both columns to have a bounding box");

    expect(Math.abs(allocationBox.y - positionsBox.y)).toBeLessThan(2);
    expect(allocationBox.x).toBeGreaterThanOrEqual(positionsBox.x + positionsBox.width - 1);
  });
});

test.describe("simulation-dashboard.tsx positions table horizontal scroll", () => {
  for (const widthName of ["iphone5", "mobile"] as const) {
    test(`table scrolls horizontally within its own box at ${widthName} (${VIEWPORTS[widthName].width}px)`, async ({
      page,
    }) => {
      const viewport = VIEWPORTS[widthName];
      await page.setViewportSize(viewport);
      await seedAuth(page);
      await mockSimulationApi(page);
      await page.goto(DASHBOARD_ROUTE);

      const scrollBox = page.getByTestId("positions-table-scroll");
      await expect(scrollBox).toBeVisible();

      const { scrollWidth, clientWidth, overflowX } = await scrollBox.evaluate((el) => ({
        scrollWidth: el.scrollWidth,
        clientWidth: el.clientWidth,
        overflowX: getComputedStyle(el).overflowX,
      }));

      expect(["auto", "scroll"]).toContain(overflowX);
      expect(scrollWidth).toBeGreaterThan(clientWidth);

      const pageScrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
      expect(pageScrollWidth).toBeLessThanOrEqual(viewport.width);
    });
  }
});
