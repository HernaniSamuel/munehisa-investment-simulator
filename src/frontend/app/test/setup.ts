import { afterEach } from "vitest";
import { cleanup } from "@testing-library/react";
import "@testing-library/jest-dom/vitest";
import i18n from "~/lib/i18n";

// @testing-library/react's auto-cleanup only registers itself when it detects
// a global `afterEach` (e.g. Jest's globals) - this project runs vitest with
// globals disabled, so unmounting between tests has to be done explicitly.
afterEach(() => {
  cleanup();
});

// A test that switches locale (e.g. via seedLocale + a language-switch
// interaction) would otherwise leak that language into every later test in
// the same file - vitest isolates modules across test files, but not
// between tests within one file, and i18next's language is module-level
// state. Runs after cleanup() above, so it never touches a still-mounted
// component.
afterEach(() => {
  i18n.changeLanguage("en");
});
