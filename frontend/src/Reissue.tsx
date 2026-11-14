import { useEffect, useRef, useState, type SubmitEvent } from "react";

import { type Project, ApiError, errorMessage, request } from "./api/client";

import { Field, Notice } from "./components";

export function ReissuePanel({ project }: Readonly<{ project: Project }>) {
  const [target, setTarget] = useState<string | null>(null),
    [email, setEmail] = useState(""),
    [confirm, setConfirm] = useState(false),
    [busy, setBusy] = useState(false),
    [loading, setLoading] = useState(true),
    [error, setError] = useState(""),
    [fieldError, setFieldError] = useState("");
  const previous = useRef<{ email: string; key: string } | null>(null);
  const root = `/projects/${encodeURIComponent(project.id)}/request/reissue`;
  useEffect(() => {
    let current = true;
    request<{ targetProjectId: string | null }>(root)
      .then((r) => {
        if (current) setTarget(r.targetProjectId);
      })
      .catch((error_) => {
        if (current) setError(errorMessage(error_));
      })
      .finally(() => {
        if (current) setLoading(false);
      });
    return () => {
      current = false;
    };
  }, [root]);
  async function submit(e: SubmitEvent) {
    e.preventDefault();
    if (!confirm) {
      setConfirm(true);
      return;
    }
    setBusy(true);
    setError("");
    setFieldError("");
    try {
      const value = email.trim().toLowerCase();
      if (previous.current?.email !== value)
        previous.current = { email: value, key: crypto.randomUUID() };
      const next = await request<Project>(root, "POST", {
        contractorEmail: value,
        idempotencyKey: previous.current.key,
      });
      setTarget(next.id);
      window.location.hash = `/projects/${next.id}?created`;
    } catch (error_) {
      setError(errorMessage(error_));
      if (error_ instanceof ApiError)
        setFieldError(
          error_.fieldErrors.contractorEmail ??
            (error_.status === 422 ? error_.message : ""),
        );
      setConfirm(false);
      try {
        const result = await request<{ targetProjectId: string | null }>(root);
        setTarget(result.targetProjectId);
      } catch {
        /* Основная ошибка уже видна; повтор команды сохранит ключ. */
      }
    } finally {
      setBusy(false);
    }
  }
  function submitLabel() {
    if (busy) {
      return "Создаём копию…";
    }
    if (confirm) {
      return "Создать копию заявки";
    }
    return "Выбрать другого подрядчика";
  }
  function renderReissue() {
    if (loading) {
      return <Notice>Проверяем, создана ли копия…</Notice>;
    }
    if (target) {
      return (
        <a className="button primary" href={`#/projects/${target}`}>
          Открыть копию заявки
        </a>
      );
    }
    return (
      <form onSubmit={submit} aria-busy={busy}>
        <Field
          id="reissue-email"
          label="Email другого подрядчика"
          type="email"
          maxLength={320}
          required
          value={email}
          readOnly={confirm}
          disabled={busy}
          error={fieldError}
          onChange={(e) => setEmail(e.target.value)}
        />
        {confirm && (
          <div className="confirm-box">
            <p>
              Создать копию для <strong>{email.trim().toLowerCase()}</strong>?
              Она будет черновиком: перед отправкой проверьте бюджет, сроки и
              другие пожелания.
            </p>
            <p className="hint">
              Новый подрядчик не получит историю прежнего исполнителя.
            </p>
          </div>
        )}
        <div className="form-actions">
          <button type="submit" className="button primary" disabled={busy}>
            {submitLabel()}
          </button>
          {confirm && (
            <button
              type="button"
              className="button secondary"
              disabled={busy}
              onClick={() => setConfirm(false)}
            >
              Изменить email
            </button>
          )}
        </div>
      </form>
    );
  }
  return (
    <section className="panel reissue-panel" aria-labelledby="reissue-heading">
      <p className="eyebrow">Следующий шаг заказчика</p>
      <h2 id="reissue-heading">Другой подрядчик</h2>
      <p>
        Исходное предложение остаётся в истории. Для другого подрядчика создадим
        отдельную карточку с вашими пожеланиями — без чужой сметы, договора и
        кредитного снимка.
      </p>
      {error && <Notice error>{error}</Notice>}
      {renderReissue()}
    </section>
  );
}
