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

test.describe("simulation-dashboard.tsx header actions", () => {
  test("stacks each control into its own full-width row below sm (640px)", async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.mobile);
    await seedAuth(page);
    await mockSimulationApi(page);
    await page.goto(DASHBOARD_ROUTE);

    const actions = page.getByTestId("header-actions");
    await expect(actions).toBeVisible();

    const containerBox = await actions.boundingBox();
    if (!containerBox) throw new Error("Expected header-actions to have a bounding box");

    const controls = actions.locator(":scope > *");
    await expect(controls).toHaveCount(5);

    const boxes = [];
    for (let i = 0; i < 5; i++) {
      const box = await controls.nth(i).boundingBox();
      if (!box) throw new Error(`Expected control ${i} to have a bounding box`);
      boxes.push(box);
      // Each control spans (approximately) the full width of its row.
      expect(box.width).toBeGreaterThan(containerBox.width - 2);
    }

    // Stacked top to bottom, in DOM order - each control starts at or after the previous
    // control's bottom edge, never sharing a row with it.
    for (let i = 1; i < boxes.length; i++) {
      expect(boxes[i].y).toBeGreaterThanOrEqual(boxes[i - 1].y + boxes[i - 1].height - 1);
    }
  });

  test("keeps the flex-wrap/items-center layout unchanged at sm (640px) and above", async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.tablet);
    await seedAuth(page);
    await mockSimulationApi(page);
    await page.goto(DASHBOARD_ROUTE);

    const actions = page.getByTestId("header-actions");
    await expect(actions).toBeVisible();

    const controls = actions.locator(":scope > *");
    const languageSwitcher = await controls.nth(0).boundingBox();
    const dateBox = await controls.nth(1).boundingBox();
    if (!languageSwitcher || !dateBox) {
      throw new Error("Expected the language switcher and date box to have a bounding box");
    }

    // Same row (not stacked): the language switcher and the date box sit side by side, as
    // they did before this change - flex-wrap only drops a control to the next line once it
    // no longer fits, never stacks it unconditionally. items-center vertically centers controls
    // of different heights on that shared line, so their top edges differ even on the same
    // row - overlapping vertical ranges (rather than equal y) is the correct same-row check.
    const overlapTop = Math.max(languageSwitcher.y, dateBox.y);
    const overlapBottom = Math.min(languageSwitcher.y + languageSwitcher.height, dateBox.y + dateBox.height);
    expect(overlapBottom).toBeGreaterThan(overlapTop);
    expect(dateBox.x).toBeGreaterThanOrEqual(languageSwitcher.x + languageSwitcher.width - 1);
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
