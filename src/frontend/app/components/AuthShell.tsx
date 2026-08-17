import type { ReactNode } from "react";
import { LanguageSwitcher } from "~/components/LanguageSwitcher";

export function AuthShell({
  seal,
  eyebrow,
  title,
  children,
  footer,
}: {
  seal: string;
  eyebrow: string;
  title: string;
  children: ReactNode;
  footer?: ReactNode;
}) {
  return (
    <main className="relative flex min-h-screen items-center justify-center bg-paper px-4 py-10">
      <div className="washi-texture" aria-hidden="true" />
      <div className="absolute right-4 top-4">
        <LanguageSwitcher />
      </div>
      <div className="relative w-full max-w-[420px]">
        <div className="mb-8 flex flex-col items-center gap-2">
          <div className="flex h-11 w-11 items-center justify-center bg-ink font-display text-xl text-paper">
            {seal}
          </div>
          <h1 className="font-display text-2xl font-bold text-ink">Munehisa</h1>
          <p className="font-mono text-[10px] uppercase tracking-[.2em] text-muted">
            {eyebrow}
          </p>
        </div>

        <div className="relative border border-ink/10 bg-panel p-5 shadow-[0_0_0_3px_#211E18] sm:p-8">
          <h2 className="mb-6 font-display text-xl font-bold text-ink">{title}</h2>
          {children}
        </div>

        {footer && <div className="mt-6 text-center">{footer}</div>}
      </div>
    </main>
  );
}
