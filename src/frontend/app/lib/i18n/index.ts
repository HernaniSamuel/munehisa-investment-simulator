import i18next from "i18next";
import { initReactI18next } from "react-i18next";
import en from "./locales/en";
import ptBR from "./locales/pt-BR";

export const LOCALE_STORAGE_KEY = "munehisa.locale";

export const SUPPORTED_LOCALES = ["en", "pt-BR"] as const;
export type Locale = (typeof SUPPORTED_LOCALES)[number];

function isSupportedLocale(value: string | null): value is Locale {
  return value === "en" || value === "pt-BR";
}

// Only a stored, explicit selection is read here - browser auto-detection
// itself is never written to storage (see setLocale), so a fresh visit with
// no stored preference re-detects from navigator.language every time rather
// than freezing whatever was first observed.
//
// This module is imported at the top of root.tsx and runs at module-eval
// time, before any component mounts. The app itself is SPA-only
// (react-router.config.ts sets ssr: false), but React Router's dev server
// still evaluates the route module graph once in a plain Node context (no
// window/localStorage/navigator) to serve the initial document - so this
// has to tolerate running outside a browser, unlike ThemeProvider's
// equivalent check, which is safe inside a useEffect.
export function detectInitialLocale(): Locale {
  if (typeof localStorage === "undefined" || typeof navigator === "undefined") return "en";

  const stored = localStorage.getItem(LOCALE_STORAGE_KEY);
  if (isSupportedLocale(stored)) return stored;

  // Only "en" and "pt-BR" ship as catalogs, so any Portuguese variant
  // (pt, pt-PT, pt-BR) maps to the one Portuguese catalog available.
  const browserLang = navigator.language.toLowerCase();
  if (browserLang.startsWith("pt")) return "pt-BR";
  return "en";
}

// Resources are bundled statically (no backend/detector plugin), so
// i18next resolves synchronously - i18n.language and t() are already
// correct immediately after this call, which root.tsx's Layout relies on
// for its very first render.
i18next.use(initReactI18next).init({
  resources: { en: { translation: en }, "pt-BR": { translation: ptBR } },
  lng: detectInitialLocale(),
  fallbackLng: "en",
  interpolation: { escapeValue: false },
});

// Changes the active language and persists the choice together, mirroring
// ThemeProvider's setTheme - the pairing keeps "explicit selection" (written
// to storage) and "detected default" (never written) unambiguous.
export function setLocale(locale: Locale) {
  i18next.changeLanguage(locale);
  localStorage.setItem(LOCALE_STORAGE_KEY, locale);
}

export default i18next;
