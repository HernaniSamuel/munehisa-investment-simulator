import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";

// @testing-library/react's auto-cleanup only registers itself when it detects
// a global `afterEach` (e.g. Jest's globals) - this project runs vitest with
// globals disabled, so unmounting between tests has to be done explicitly.
afterEach(() => {
  cleanup();
});
