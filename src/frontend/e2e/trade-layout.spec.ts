import { expect, test } from "@playwright/test";
import { mockSimulationApi, seedAuth, TRADE_ROUTE, VIEWPORTS } from "./support";

test.describe("trade.tsx sidebar/chart layout", () => {
  test("stacks vertically below 768px", async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.tablet);
    await seedAuth(page);
    await mockSimulationApi(page);
    await page.goto(TRADE_ROUTE);

    const sidebar = page.getByTestId("trade-sidebar");
    const chartPanel = page.getByTestId("trade-chart-panel");
    await expect(sidebar).toBeVisible();
    await expect(chartPanel).toBeVisible();

    const sidebarBox = await sidebar.boundingBox();
    const chartBox = await chartPanel.boundingBox();
    if (!sidebarBox || !chartBox) throw new Error("Expected both trade panels to have a bounding box");

    // Stacked: same left edge, chart starts at or below the sidebar's bottom.
    expect(chartBox.x).toBeCloseTo(sidebarBox.x, 0);
    expect(chartBox.y).toBeGreaterThanOrEqual(sidebarBox.y + sidebarBox.height - 1);
  });

  test("sits side by side at 1024px+", async ({ page }) => {
    await page.setViewportSize(VIEWPORTS.desktop);
    await seedAuth(page);
    await mockSimulationApi(page);
    await page.goto(TRADE_ROUTE);

    const sidebar = page.getByTestId("trade-sidebar");
    const chartPanel = page.getByTestId("trade-chart-panel");
    await expect(sidebar).toBeVisible();
    await expect(chartPanel).toBeVisible();

    const sidebarBox = await sidebar.boundingBox();
    const chartBox = await chartPanel.boundingBox();
    if (!sidebarBox || !chartBox) throw new Error("Expected both trade panels to have a bounding box");

    // Side by side: roughly the same top, chart starts to the right of the sidebar.
    expect(Math.abs(chartBox.y - sidebarBox.y)).toBeLessThan(2);
    expect(chartBox.x).toBeGreaterThanOrEqual(sidebarBox.x + sidebarBox.width - 1);
  });

  for (const [widthName, viewport] of Object.entries(VIEWPORTS)) {
    test(`financial chart stays within its container at ${widthName} (${viewport.width}px)`, async ({ page }) => {
      await page.setViewportSize(viewport);
      await seedAuth(page);
      await mockSimulationApi(page);
      await page.goto(TRADE_ROUTE);

      const chartPanel = page.getByTestId("trade-chart-panel");
      const svg = chartPanel.locator("svg[aria-label='Financial chart']");
      await expect(svg).toBeVisible();

      const panelBox = await chartPanel.boundingBox();
      const svgBox = await svg.boundingBox();
      if (!panelBox || !svgBox) throw new Error("Expected chart panel and svg to have a bounding box");

      expect(svgBox.x).toBeGreaterThanOrEqual(panelBox.x - 1);
      expect(svgBox.x + svgBox.width).toBeLessThanOrEqual(panelBox.x + panelBox.width + 1);

      const scrollWidth = await page.evaluate(() => document.documentElement.scrollWidth);
      expect(scrollWidth).toBeLessThanOrEqual(viewport.width);
    });
  }
});
