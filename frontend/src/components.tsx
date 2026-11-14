import {
  useEffect,
  useRef,
  useState,
  useId,
  type InputHTMLAttributes,
  type ReactNode,
} from "react";

export function Notice({
  children,
  error = false,
}: Readonly<{
  children: ReactNode;
  error?: boolean;
}>) {
  const ref = useRef<HTMLDivElement>(null);
  useEffect(() => {
    if (error) ref.current?.focus();
  }, [children, error]);
  return (
    <div
      ref={ref}
      className={`notice ${error ? "notice-error" : ""}`}
      role={error ? "alert" : "status"}
      tabIndex={error ? -1 : undefined}
    >
      {children}
    </div>
  );
}
export function Field({
  label,
  hint,
  error,
  id,
  ...props
}: Readonly<
  InputHTMLAttributes<HTMLInputElement> & {
    id: string;
    label: string;
    hint?: string;
    error?: string;
  }
>) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {hint && (
        <span className="hint" id={`${id}-hint`}>
          {hint}
        </span>
      )}
      <input
        id={id}
        {...props}
        aria-invalid={error ? true : undefined}
        aria-describedby={
          [hint && `${id}-hint`, error && `${id}-error`]
            .filter(Boolean)
            .join(" ") || undefined
        }
      />
      {error && (
        <span className="field-error" id={`${id}-error`}>
          {error}
        </span>
      )}
    </div>
  );
}
export function PageTitle({
  eyebrow,
  children,
  description,
}: Readonly<{
  eyebrow: string;
  children: ReactNode;
  description: string;
}>) {
  const ref = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    ref.current?.focus();
  }, [children]);
  return (
    <div className="page-title">
      <p className="eyebrow">{eyebrow}</p>
      <h1 ref={ref} tabIndex={-1}>
        {children}
      </h1>
      <p className="lead">{description}</p>
    </div>
  );
}

export function Area({
  id,
  label,
  value,
  onChange,
  maxLength = 30000,
  hint,
  required = false,
  error,
}: Readonly<{
  id: string;
  label: string;
  value: string;
  onChange: (s: string) => void;
  maxLength?: number;
  hint?: string;
  required?: boolean;
  error?: string;
}>) {
  return (
    <div className="field">
      <label htmlFor={id}>{label}</label>
      {hint && (
        <span className="hint" id={id + "-hint"}>
          {hint}
        </span>
      )}
      <textarea
        id={id}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        maxLength={maxLength}
        required={required}
        aria-invalid={error ? true : undefined}
        aria-describedby={
          [hint ? id + "-hint" : "", error ? id + "-error" : ""]
            .filter(Boolean)
            .join(" ") || undefined
        }
        rows={6}
      />
      {error && (
        <span className="field-error" id={id + "-error"}>
          {error}
        </span>
      )}
    </div>
  );
}

export function Illustration({
  url,
  caption,
  compact = false,
}: Readonly<{
  url: string;
  caption: string;
  compact?: boolean;
}>) {
  const [failed, setFailed] = useState(false);
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => setFailed(false), [url]);
  const image = (
    <img
      src={url}
      alt={caption}
      referrerPolicy="no-referrer"
      loading="lazy"
      onError={() => setFailed(true)}
    />
  );
  function renderIllustration() {
    if (failed) {
      return <p>Изображение недоступно</p>;
    }
    if (compact) {
      return (
        <button
          type="button"
          className="photo-trigger"
          aria-label={"Открыть фото: " + caption}
          onClick={() => dialog.current?.showModal()}
        >
          {image}
        </button>
      );
    }
    return image;
  }
  return (
    <figure className={compact ? "journal-photo" : "proposal-image"}>
      {renderIllustration()}
      <figcaption>{caption}</figcaption>
      {!compact && (
        <small>
          Изображение из внешнего источника может измениться или стать
          недоступным.
        </small>
      )}
      {compact && (
        <dialog ref={dialog} className="photo-dialog" aria-labelledby={titleId}>
          <h3 id={titleId}>{caption}</h3>
          {failed ? (
            <p>Изображение недоступно</p>
          ) : (
            <img
              src={url}
              alt={caption}
              referrerPolicy="no-referrer"
              onError={() => setFailed(true)}
            />
          )}
          <button
            type="button"
            className="button secondary"
            onClick={() => dialog.current?.close()}
          >
            Закрыть фото
          </button>
        </dialog>
      )}
    </figure>
  );
}

export function formatDateTime(value: string) {
  return new Date(value).toLocaleString("ru-RU", {
    day: "numeric",
    month: "long",
    hour: "2-digit",
    minute: "2-digit",
  });
}
