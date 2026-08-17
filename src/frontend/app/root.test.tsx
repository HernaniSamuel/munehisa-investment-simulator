import { act, render, screen } from "@testing-library/react";
import { renderToStaticMarkup } from "react-dom/server";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ErrorBoundary, Layout } from "./root";
import type { Route } from "./+types/root";
import i18n from "~/lib/i18n";

// Layout renders <Meta>/<Links>/<Scripts>/<ScrollRestoration>, which read a
// framework router context only a real <HydratedRouter> provides - this
// test only cares about the <html lang> attribute Layout itself sets, so
// those are stubbed out rather than standing up a full router.
vi.mock("react-router", async (importOriginal) => {
  const actual = await importOriginal<typeof import("react-router")>();
  return { ...actual, Meta: () => null, Links: () => null, Scripts: () => null, ScrollRestoration: () => null };
});

describe("Layout", () => {
  beforeEach(() => {
    i18n.changeLanguage("en");
  });

  // A live RTL render can't hold a literal <html> element (jsdom/React
  // refuse to attach it as a child of RTL's container div - a real content-
  // model restriction, not a bug in the component), so this renders to a
  // markup string instead, once per locale, to check the attribute
  // Layout's useTranslation()-driven lang={i18n.language} actually emits.
  it("sets <html lang> to the active locale", async () => {
    expect(renderToStaticMarkup(<Layout><div>content</div></Layout>)).toContain('lang="en"');

    await act(async () => {
      await i18n.changeLanguage("pt-BR");
    });

    expect(renderToStaticMarkup(<Layout><div>content</div></Layout>)).toContain('lang="pt-BR"');
  });
});

describe("ErrorBoundary", () => {
  beforeEach(() => {
    i18n.changeLanguage("en");
  });

  // A plain object (not a react-router route error response and not an
  // Error instance) exercises the component's default fallback text, which
  // is what's actually driven by the translation catalog - the branch this
  // test cares about, independent of react-router's own error-shape
  // internals. The whole props object is cast (rather than typed field by
  // field) since Route.ErrorBoundaryProps is a generated, opaque shape.
  const props = { error: {} } as unknown as Route.ErrorBoundaryProps;

  it("renders translated fallback text and re-translates it on a language switch", () => {
    render(<ErrorBoundary {...props} />);

    expect(screen.getByText("Oops!")).toBeInTheDocument();
    expect(screen.getByText("An unexpected error occurred.")).toBeInTheDocument();

    act(() => {
      i18n.changeLanguage("pt-BR");
    });

    expect(screen.getByText("Ops!")).toBeInTheDocument();
    expect(screen.getByText("Ocorreu um erro inesperado.")).toBeInTheDocument();
  });
});
