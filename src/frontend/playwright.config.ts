import { defineConfig, devices } from "@playwright/test";

// Layout/overflow behavior under test is CSS-driven, not browser-engine-specific, so this
// suite only runs against Chromium - see the "Chromium only" judgment call in the PR description.
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? "github" : "list",
  use: {
    baseURL: "http://localhost:5173",
    trace: "on-first-retry",
  },
  projects: [
    {
      name: "chromium",
      use: { ...devices["Desktop Chrome"] },
    },
  ],
  webServer: {
    command: "npm run dev",
    url: "http://localhost:5173",
    reuseExistingServer: !process.env.CI,
    env: {
      VITE_API_URL: "http://localhost:8000",
    },
  },
});
