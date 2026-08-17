import {
  forwardRef,
  useState,
  type ButtonHTMLAttributes,
  type InputHTMLAttributes,
  type ReactNode,
  type SelectHTMLAttributes,
} from "react";

type ButtonVariant = "primary" | "secondary" | "ink" | "solid";

export const buttonVariantClasses: Record<ButtonVariant, string> = {
  primary:
    "bg-vermilion text-on-verm shadow-[0_2px_0_#9E2413] hover:bg-[#C7331D]",
  secondary:
    "bg-paper text-teal border border-teal shadow-[0_2px_0_#1B161122] hover:bg-teal/5",
  ink: "bg-paper text-ink border border-ink/25 shadow-[0_1px_0_#1B161114] hover:bg-ink/5",
  solid: "bg-ink text-paper shadow-[0_2px_0_#00000040] hover:bg-ink/90",
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
  trailing?: ReactNode;
};

export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(
  ({ label, id, error, trailing, className = "", ...props }, ref) => {
    const input = (
      <input
        ref={ref}
        id={id}
        className={`border px-3 py-2.5 font-sans text-ink placeholder:text-muted/60 focus:outline-none ${
          error ? "border-vermilion" : "border-ink/15 focus:border-vermilion"
        } bg-paper ${trailing ? "pr-10" : ""} ${className}`}
        {...props}
      />
    );

    return (
      <div className="flex flex-col gap-1.5">
        <label
          htmlFor={id}
          className="font-mono text-[10px] uppercase tracking-[.14em] text-muted"
        >
          {label}
        </label>
        {trailing ? (
          <div className="relative">
            {input}
            <div className="absolute inset-y-0 right-0 flex items-center">{trailing}</div>
          </div>
        ) : (
          input
        )}
        {error && <p className="font-mono text-[11px] text-vermilion">{error}</p>}
      </div>
    );
  }
);
TextField.displayName = "TextField";

function EyeIcon() {
  return (
    <svg viewBox="0 0 20 14" width="16" height="16" fill="none" aria-hidden="true">
      <path
        d="M1 7C2.6 4 6 1.5 10 1.5C14 1.5 17.4 4 19 7C17.4 10 14 12.5 10 12.5C6 12.5 2.6 10 1 7Z"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinejoin="round"
      />
      <circle cx="10" cy="7" r="2.4" fill="currentColor" />
    </svg>
  );
}

function EyeOffIcon() {
  return (
    <svg viewBox="0 0 20 14" width="16" height="16" fill="none" aria-hidden="true">
      <path
        d="M1 7C2.6 4 6 1.5 10 1.5C14 1.5 17.4 4 19 7C17.4 10 14 12.5 10 12.5C6 12.5 2.6 10 1 7Z"
        stroke="currentColor"
        strokeWidth="1.4"
        strokeLinejoin="round"
      />
      <circle cx="10" cy="7" r="2.4" fill="currentColor" />
      <line x1="2" y1="12.5" x2="18" y2="1.5" stroke="currentColor" strokeWidth="1.4" strokeLinecap="round" />
    </svg>
  );
}

type PasswordFieldProps = Omit<TextFieldProps, "type" | "trailing">;

export const PasswordField = forwardRef<HTMLInputElement, PasswordFieldProps>(
  (props, ref) => {
    const [visible, setVisible] = useState(false);

    return (
      <TextField
        ref={ref}
        type={visible ? "text" : "password"}
        trailing={
          <button
            type="button"
            onClick={() => setVisible((v) => !v)}
            aria-label={visible ? "Hide password" : "Show password"}
            className="flex h-full items-center px-3 text-muted transition-colors hover:text-ink focus:outline-none focus:text-ink cursor-pointer"
          >
            {visible ? <EyeOffIcon /> : <EyeIcon />}
          </button>
        }
        {...props}
      />
    );
  }
);
PasswordField.displayName = "PasswordField";

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
  error?: string;
};

export const Select = forwardRef<HTMLSelectElement, SelectProps>(
  ({ label, id, error, className = "", children, ...props }, ref) => (
    <div className="flex flex-col gap-1.5">
      <label
        htmlFor={id}
        className="font-mono text-[10px] uppercase tracking-[.14em] text-muted"
      >
        {label}
      </label>
      <select
        ref={ref}
        id={id}
        className={`border px-3 py-2.5 font-sans text-ink focus:outline-none ${
          error ? "border-vermilion" : "border-ink/15 focus:border-vermilion"
        } bg-paper ${className}`}
        {...props}
      >
        {children}
      </select>
      {error && <p className="font-mono text-[11px] text-vermilion">{error}</p>}
    </div>
  )
);
Select.displayName = "Select";

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
