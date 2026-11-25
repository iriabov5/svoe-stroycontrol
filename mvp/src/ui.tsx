import { useEffect, useRef, type ReactNode } from "react";
import { X, Info, Check, LoaderCircle } from "lucide-react";
export function Modal({
  title,
  onClose,
  children,
  wide = false,
}: {
  title: string;
  onClose: () => void;
  children: ReactNode;
  wide?: boolean;
}) {
  const ref = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    const node = ref.current;
    const previous = document.activeElement as HTMLElement;
    node?.showModal();
    document.body.style.overflow = "hidden";
    return () => {
      node?.close();
      document.body.style.overflow = "";
      previous?.focus();
    };
  }, []);
  return (
    <dialog
      ref={ref}
      className={`modal ${wide ? "modal-wide" : ""}`}
      onCancel={(e) => {
        e.preventDefault();
        onClose();
      }}
      onClick={(e) => {
        if (e.target === ref.current) onClose();
      }}
      aria-label={title}
    >
      <div className="modal-content">
        <button
          className="icon-button modal-close"
          onClick={onClose}
          aria-label="Закрыть окно"
        >
          <X size={21} />
        </button>
        <h2>{title}</h2>
        {children}
      </div>
    </dialog>
  );
}
export function Field({
  label,
  hint,
  children,
  className = "",
}: {
  label: string;
  hint?: string;
  children: ReactNode;
  className?: string;
}) {
  return (
    <label className={`field ${className}`}>
      <span>{label}</span>
      {children}
      {hint && <small>{hint}</small>}
    </label>
  );
}
export function Notice({
  children,
  tone = "info",
}: {
  children: ReactNode;
  tone?: "info" | "success" | "warning" | "error";
}) {
  return (
    <div
      className={`notice ${tone}`}
      role={tone === "error" ? "alert" : undefined}
    >
      {tone === "success" ? <Check size={19} /> : <Info size={19} />}
      <div>{children}</div>
    </div>
  );
}
export function Spinner() {
  return <LoaderCircle size={18} className="spin" aria-hidden="true" />;
}
export function PageHeading({
  eyebrow,
  title,
  description,
  action,
}: {
  eyebrow: string;
  title: string;
  description: string;
  action?: ReactNode;
}) {
  return (
    <div className="page-heading">
      <div>
        <div className="eyebrow">{eyebrow}</div>
        <h1>{title}</h1>
        <p>{description}</p>
      </div>
      {action}
    </div>
  );
}
export function Empty({
  icon,
  children,
}: {
  icon: ReactNode;
  children: ReactNode;
}) {
  return (
    <div className="empty-state">
      <div className="icon-tile">{icon}</div>
      {children}
    </div>
  );
}
