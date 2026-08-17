import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { detectInitialLocale, LOCALE_STORAGE_KEY, setLocale } from "./index";

function stubNavigatorLanguage(language: string) {
  vi.stubGlobal("navigator", { ...navigator, language });
}

describe("detectInitialLocale", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("prefers a stored, explicit locale over the browser language", () => {
    localStorage.setItem(LOCALE_STORAGE_KEY, "pt-BR");
    stubNavigatorLanguage("en-US");

    expect(detectInitialLocale()).toBe("pt-BR");
  });

  it("ignores an unsupported stored value and falls back to detection", () => {
    localStorage.setItem(LOCALE_STORAGE_KEY, "fr");
    stubNavigatorLanguage("en-US");

    expect(detectInitialLocale()).toBe("en");
  });

  it.each([
    ["en", "en"],
    ["en-US", "en"],
    ["en-GB", "en"],
    ["pt", "pt-BR"],
    ["pt-BR", "pt-BR"],
    ["pt-PT", "pt-BR"],
    ["fr", "en"],
    ["de-DE", "en"],
  ])("with no stored preference, navigator.language %s resolves to %s", (browserLang, expected) => {
    stubNavigatorLanguage(browserLang);

    expect(detectInitialLocale()).toBe(expected);
  });

  it("never writes the auto-detected locale to storage", () => {
    stubNavigatorLanguage("pt-BR");

    detectInitialLocale();

    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBeNull();
  });
});

describe("setLocale", () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it("changes the active language and persists the explicit selection", () => {
    setLocale("pt-BR");

    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe("pt-BR");
  });
});
