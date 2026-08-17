import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { useTranslation } from "react-i18next";
import { LanguageSwitcher } from "./LanguageSwitcher";
import i18n, { LOCALE_STORAGE_KEY } from "~/lib/i18n";

// A sibling element whose text comes from the same translation catalog as
// LanguageSwitcher's own labels - proves that clicking the switcher
// re-renders *other* currently-visible translated text too, not just its
// own buttons.
function TranslatedSibling() {
  const { t } = useTranslation();
  return <span>{t("common.cancel")}</span>;
}

describe("LanguageSwitcher", () => {
  beforeEach(() => {
    localStorage.clear();
    i18n.changeLanguage("en");
  });

  it("marks the active locale's button as pressed", () => {
    render(<LanguageSwitcher />);

    expect(screen.getByRole("button", { name: "EN" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByRole("button", { name: "PT-BR" })).toHaveAttribute("aria-pressed", "false");
  });

  it("switches the active language, persists it, and re-renders other translated text", async () => {
    const user = userEvent.setup();
    render(
      <>
        <LanguageSwitcher />
        <TranslatedSibling />
      </>
    );

    expect(screen.getByText("Cancel")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "PT-BR" }));

    expect(i18n.language).toBe("pt-BR");
    expect(localStorage.getItem(LOCALE_STORAGE_KEY)).toBe("pt-BR");
    expect(screen.getByRole("button", { name: "PT-BR" })).toHaveAttribute("aria-pressed", "true");
    expect(screen.getByText("Cancelar")).toBeInTheDocument();
  });
});
