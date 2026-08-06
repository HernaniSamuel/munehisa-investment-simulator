import { useEffect } from "react";

const AUTO_DISMISS_MS = 5000;

export type ToastItem = { id: string; message: string };

export function ToastStack({
  toasts,
  onDismiss,
}: {
  toasts: ToastItem[];
  onDismiss: (id: string) => void;
}) {
  return (
    <div className="fixed bottom-4 right-4 z-50 flex flex-col gap-2">
      {toasts.map((toast) => (
        <Toast key={toast.id} toast={toast} onDismiss={onDismiss} />
      ))}
    </div>
  );
}

// Each toast owns its own timer, keyed off its own id, so dismissing one
// (by hand or by timeout) never resets or cancels another's countdown.
function Toast({ toast, onDismiss }: { toast: ToastItem; onDismiss: (id: string) => void }) {
  useEffect(() => {
    const timer = setTimeout(() => onDismiss(toast.id), AUTO_DISMISS_MS);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [toast.id]);

  return (
    <div
      role="status"
      className="border border-ink/20 bg-panel px-4 py-3 font-sans text-sm text-ink shadow-[0_0_0_3px_#211E18]"
    >
      {toast.message}
    </div>
  );
}
