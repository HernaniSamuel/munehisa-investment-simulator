import { useTranslation } from "react-i18next";
import { setLocale, SUPPORTED_LOCALES, type Locale } from "~/lib/i18n";

const LOCALE_LABEL_KEYS: Record<Locale, "en" | "ptBR"> = {
  en: "en",
  "pt-BR": "ptBR",
};

export function LanguageSwitcher() {
  const { t, i18n } = useTranslation();
  const active = i18n.language as Locale;

  return (
    <div className="flex gap-1.5" role="group" aria-label={t("languageSwitcher.label")}>
      {SUPPORTED_LOCALES.map((locale) => (
        <button
          key={locale}
          type="button"
          aria-pressed={active === locale}
          onClick={() => setLocale(locale)}
          className={`border px-2.5 py-1.5 font-mono text-[10px] uppercase tracking-[.1em] transition-colors cursor-pointer ${
            active === locale
              ? "border-ink bg-ink text-paper"
              : "border-ink/25 bg-paper text-ink hover:bg-ink/5"
          }`}
        >
          {t(`languageSwitcher.${LOCALE_LABEL_KEYS[locale]}`)}
        </button>
      ))}
    </div>
  );
}
