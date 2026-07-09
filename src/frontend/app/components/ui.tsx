import {
  forwardRef,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
} from "react";

type ButtonVariant = "primary" | "secondary" | "ink";

export const buttonVariantClasses: Record<ButtonVariant, string> = {
  primary:
    "bg-vermilion text-on-verm shadow-[0_2px_0_#9E2413] hover:bg-[#C7331D]",
  secondary:
    "bg-paper text-teal border border-teal shadow-[0_2px_0_#0C615622] hover:bg-teal/5",
  ink: "bg-paper text-ink border border-ink/25 shadow-[0_1px_0_#1B161114] hover:bg-ink/5",
};

export const buttonBaseClasses =
  "font-mono text-[11px] uppercase tracking-[.1em] px-5 py-3 transition-colors cursor-pointer disabled:cursor-not-allowed disabled:opacity-50 inline-block text-center";

export const Button = forwardRef<
  HTMLButtonElement,
  ButtonHTMLAttributes<HTMLButtonElement> & { variant?: ButtonVariant }
>(({ variant = "primary", className = "", ...props }, ref) => (
  <button
    ref={ref}
    className={`${buttonBaseClasses} ${buttonVariantClasses[variant]} ${className}`}
    {...props}
  />
));
Button.displayName = "Button";

type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string;
};

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(
  ({ label, id, error, className = "", ...props }, ref) => (
    <div className="flex flex-col gap-1.5">
      <label
        htmlFor={id}
        className="font-mono text-[10px] uppercase tracking-[.14em] text-muted"
      >
        {label}
      </label>
      <input
        ref={ref}
        id={id}
        className={`border px-3 py-2.5 font-sans text-ink placeholder:text-muted/60 focus:outline-none ${
          error ? "border-vermilion" : "border-ink/15 focus:border-vermilion"
        } bg-paper ${className}`}
        {...props}
      />
      {error && <p className="font-mono text-[11px] text-vermilion">{error}</p>}
    </div>
  )
);
TextField.displayName = "TextField";

export function Banner({
  tone = "error",
  children,
}: {
  tone?: "error" | "success";
  children: ReactNode;
}) {
  // "success" deliberately avoids teal: teal is defined as FALL/negative in
  // the financial UI (see DESIGN.md's rise/fall color rule), so using it here
  // would make the same token mean opposite things across the app.
  const toneClasses =
    tone === "error"
      ? "border-vermilion/30 bg-vermilion/10 text-vermilion"
      : "border-ink/20 bg-ink/5 text-ink";

  return (
    <div
      className={`border px-4 py-3 font-sans text-sm ${toneClasses}`}
      role={tone === "error" ? "alert" : "status"}
    >
      {children}
    </div>
  );
}
