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
  focusOnMount = true,
}: Readonly<{
  eyebrow: string;
  children: ReactNode;
  description: string;
  focusOnMount?: boolean;
}>) {
  const ref = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (focusOnMount) ref.current?.focus();
  }, [children, focusOnMount]);
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
  external = !compact,
  imageLabel = false,
}: Readonly<{
  url: string;
  caption: string;
  compact?: boolean;
  external?: boolean;
  imageLabel?: boolean;
}>) {
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const trigger = useRef<HTMLButtonElement>(null);
  function retry() {
    setFailed(false);
    setAttempt((value) => value + 1);
  }
  const dialog = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => setFailed(false), [url]);
  const image = (
    <img
      key={attempt}
      src={url}
      alt={caption}
      referrerPolicy="no-referrer"
      loading="lazy"
      onError={() => setFailed(true)}
    />
  );
  function renderIllustration() {
    if (failed) {
      return (
        <>
          <p>
            {imageLabel
              ? "Не удалось показать изображение"
              : "Изображение недоступно"}
          </p>
          <button type="button" className="text-button" onClick={retry}>
            Повторить
          </button>
        </>
      );
    }
    if (compact) {
      return (
        <button
          type="button"
          ref={trigger}
          className="photo-trigger"
          aria-label={
            (imageLabel ? "Открыть изображение: " : "Открыть фото: ") + caption
          }
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
      {external && (
        <small>
          Изображение из внешнего источника может измениться или стать
          недоступным.
        </small>
      )}
      {compact && (
        <dialog
          ref={dialog}
          className="photo-dialog"
          aria-labelledby={titleId}
          onClose={() => trigger.current?.focus()}
        >
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
            {imageLabel ? "Закрыть изображение" : "Закрыть фото"}
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
