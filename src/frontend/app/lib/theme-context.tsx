import { createContext, useContext, useEffect, useState, type ReactNode } from "react";

const STORAGE_KEY = "munehisa.theme";

export type Theme = "sumi" | "zankyo";

type ThemeContextValue = {
  theme: Theme;
  setTheme: (theme: Theme) => void;
};

const ThemeContext = createContext<ThemeContextValue | null>(null);

export function ThemeProvider({ children }: { children: ReactNode }) {
  const [theme, setThemeState] = useState<Theme>("sumi");

  useEffect(() => {
    const raw = localStorage.getItem(STORAGE_KEY);
    if (raw === "sumi" || raw === "zankyo") setThemeState(raw);
  }, []);

  // Repaints every bg-paper/text-ink/bg-panel/... utility - see the
  // [data-theme="zankyo"] token overrides in app.css.
  useEffect(() => {
    document.documentElement.dataset.theme = theme;
  }, [theme]);

  function setTheme(next: Theme) {
    setThemeState(next);
    localStorage.setItem(STORAGE_KEY, next);
  }

  return <ThemeContext.Provider value={{ theme, setTheme }}>{children}</ThemeContext.Provider>;
}

export function useTheme() {
  const ctx = useContext(ThemeContext);
  if (!ctx) throw new Error("useTheme must be used within ThemeProvider");
  return ctx;
}
