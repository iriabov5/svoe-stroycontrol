import { useEffect, useRef, useState, type SubmitEvent } from "react";

import type { components } from "./api/schema";
import {
  type Project,
  type Stage,
  request,
  errorMessage,
  ApiError,
  commandKey,
} from "./api/client";

import { Area, Notice, formatDateTime as timestamp } from "./components";
import {
  AttachmentList,
  FilePicker,
  uploadFile,
  type PendingFile,
} from "./Files";

type Submission = components["schemas"]["AcceptanceSubmission"];
type Confirmation = components["schemas"]["BankConfirmation"];
type BankSubmission = components["schemas"]["BankStageSubmission"];
type KeyRef = { body: string; key: string } | null;
export const bankLabels: Record<string, string> = {
  PENDING: "Готовится отправка",
  UNKNOWN: "Результат доставки уточняется",
  RECEIVED: "Получено демо-банком",
  ACCEPTED: "Принято демо-банком",
  NEEDS_INFORMATION: "Нужно дополнить",
};
export function BankConfirmationPanel({
  project,
  stage,
  acceptance,
}: Readonly<{
  project: Project;
  stage: Stage;
  acceptance: Submission;
}>) {
  const [data, setData] = useState<Confirmation | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [review, setReview] = useState(false),
    [consent, setConsent] = useState(false),
    [note, setNote] = useState(""),
    [localFiles, setLocalFiles] = useState<PendingFile[]>([]),
    [fields, setFields] = useState<Record<string, string>>({});
  const heading = useRef<HTMLHeadingElement>(null);
  const key = useRef<KeyRef>(null),
    connectKey = useRef<KeyRef>(null);
  const root = `/projects/${project.id}/stages/${stage.id}`;
  async function load() {
    setData(await request<Confirmation>(root + "/bank-confirmation"));
  }
  useEffect(() => {
    let active = true;
    request<Confirmation>(root + "/bank-confirmation")
      .then((value) => {
        if (active) setData(value);
      })
      .catch((error_) => {
        if (active) setError(errorMessage(error_));
      });
    return () => {
      active = false;
    };
  }, [root]);
  const latest = data?.submissions[0];
  useEffect(() => {
    if (!latest || !["PENDING", "UNKNOWN", "RECEIVED"].includes(latest.state))
      return;
    const timer = window.setInterval(() => {
      if (document.visibilityState === "visible")
        load().catch((error_) => setError(errorMessage(error_)));
    }, 3000);
    return () => window.clearInterval(timer);
  }, [root, latest?.state]);
  async function connect() {
    setBusy(true);
    setError("");
    try {
      const body = { consent };
      await request(`/projects/${project.id}/bank-case`, "POST", {
        ...body,
        idempotencyKey: commandKey(connectKey, body),
      });
      await load();
    } catch (error_) {
      setError(errorMessage(error_));
    } finally {
      setBusy(false);
    }
  }
  async function send(e: SubmitEvent) {
    e.preventDefault();
    if (!data) return;
    setBusy(true);
    setError("");
    setFields({});
    try {
      const uploaded = [...localFiles];
      for (let i = 0; i < uploaded.length; i++) {
        if (!uploaded[i].uploaded) {
          uploaded[i] = {
            ...uploaded[i],
            uploaded: await uploadFile(project.id, null, "BANK", uploaded[i]),
          };
          setLocalFiles([...uploaded]);
        }
      }
      const body = {
        acceptanceId: acceptance.id,
        previousId: latest?.id ?? null,
        expectedVersion: data.version,
        note,
        attachmentIds: uploaded.map((f) => f.uploaded!.id),
      };
      await request<BankSubmission>(root + "/bank-confirmations", "POST", {
        ...body,
        idempotencyKey: commandKey(key, body),
      });
      await load();
      key.current = null;
      setReview(false);
      setNote("");
      setLocalFiles([]);
    } catch (error_) {
      setError(errorMessage(error_));
      if (error_ instanceof ApiError) setFields(error_.fieldErrors);
    } finally {
      setBusy(false);
    }
  }
  async function retry() {
    if (!latest) return;
    setBusy(true);
    setError("");
    try {
      await request<BankSubmission>(
        root + "/bank-confirmations/" + latest.id + "/retry",
        "POST",
        { expectedVersion: latest.version },
      );
      await load();
    } catch (error_) {
      setError(errorMessage(error_));
    } finally {
      setBusy(false);
    }
  }
  if (data && !data.mortgage) return null;
  return (
    <section
      className="bank-confirmation"
      aria-labelledby="bank-confirmation-title"
    >
      <div className="heading-row">
        <div>
          <p className="eyebrow">Сельская ипотека · деморежим</p>
          <h3 id="bank-confirmation-title" ref={heading} tabIndex={-1}>
            Подтверждение для банка
          </h3>
        </div>
        {latest && (
          <span className="badge bank-state">{bankLabels[latest.state]}</span>
        )}
      </div>
      {error && (
        <Notice error>
          {error}{" "}
          <button
            type="button"
            className="text-button"
            onClick={() =>
              load().catch((error_) => setError(errorMessage(error_)))
            }
          >
            Обновить сведения
          </button>
        </Notice>
      )}
      {!data && !error && <p>Готовим сведения об отправке…</p>}
      {data && !data.connected && (
        <details>
          <summary>Подключить демо-дело</summary>
          <p>
            Исходная заявка останется прежней. Подключим синтетическое дело для
            демонстрации отчётов по стройке. Банковские логин и пароль не нужны.
          </p>
          <label className="check-label">
            <input
              type="checkbox"
              checked={consent}
              onChange={(e) => setConsent(e.target.checked)}
            />{" "}
            <span>Разрешаю подключить синтетическое кредитное дело</span>{" "}
          </label>
          <button
            type="button"
            className="button secondary"
            disabled={busy || !consent}
            onClick={() => connect()}
          >
            Подключить демо-дело
          </button>
        </details>
      )}
      {data?.canSend && !review && (
        <>
          <p>
            {latest
              ? "Добавьте сведения по замечанию демо-банка. Предыдущая отправка сохранится."
              : "Этап принят. Подтверждение собрано из согласованных условий, отчётов и решения заказчика."}
          </p>
          {latest?.reason && (
            <div className="acceptance-remark">
              <strong>Что нужно дополнить</strong>
              <p>{latest.reason}</p>
            </div>
          )}
          <button
            type="button"
            className="button secondary"
            onClick={() => {
              setReview(true);
              requestAnimationFrame(() =>
                document.getElementById("bank-stage-note")?.focus(),
              );
            }}
          >
            Проверить и отправить
          </button>
        </>
      )}
      {review && data?.preview && (
        <form onSubmit={send} aria-busy={busy}>
          <div className="bank-package-preview">
            <h4>Состав подтверждения</h4>
            <dl>
              <div>
                <dt>Объект</dt>
                <dd>{data.preview.project.name}</dd>
              </div>
              <div>
                <dt>Этап</dt>
                <dd>{data.preview.stage.name}</dd>
              </div>
              <div>
                <dt>Решение заказчика</dt>
                <dd>
                  Принято ·{" "}
                  {acceptance.decidedAt ? timestamp(acceptance.decidedAt) : ""}
                </dd>
              </div>
              <div>
                <dt>Подтверждения</dt>
                <dd>
                  Записей: {acceptance.evidence.length} · Файлов:{" "}
                  {acceptance.evidence.reduce(
                    (n, e) => n + e.attachments.length,
                    0,
                  )}
                </dd>
              </div>
            </dl>
            <p className="document-text">{acceptance.summary}</p>
            <details>
              <summary>Согласованные смета и договор</summary>
              <p className="document-text">
                {data.preview.documents.estimateText ??
                  "Прежний проект без текстовой сметы"}
              </p>
              <p className="document-text">
                {data.preview.documents.contractText ??
                  "Прежний проект без текстового договора"}
              </p>
            </details>
          </div>
          <Area
            id="bank-stage-note"
            label={
              latest
                ? "Пояснение для демо-банка"
                : "Комментарий к подтверждению"
            }
            value={note}
            onChange={setNote}
            maxLength={5000}
            error={fields.note}
          />
          <FilePicker
            idPrefix="bank-files"
            items={localFiles}
            onChange={setLocalFiles}
            disabled={busy}
            externalError={
              fields.file ?? fields.caption ?? fields.attachmentIds
            }
          />
          <p className="hint">
            Отправка выполняется только в локальный демо-банк. Приёмка
            заказчиком уже сохранена.
          </p>
          <div className="form-actions">
            <button type="submit" className="button primary" disabled={busy}>
              {busy ? "Отправляем…" : "Отправить в демо-банк"}
            </button>
            <button
              type="button"
              className="button secondary"
              disabled={busy}
              onClick={() => {
                setReview(false);
                requestAnimationFrame(() => heading.current?.focus());
              }}
            >
              Вернуться к этапу
            </button>
          </div>
        </form>
      )}
      {latest && ["PENDING", "UNKNOWN", "RECEIVED"].includes(latest.state) && (
        <>
          <p>
            {latest.reason ??
              "Пакет передан. Результат проверки обновится автоматически."}
          </p>
          <button
            type="button"
            className="text-button"
            disabled={busy}
            onClick={() => retry()}
          >
            Повторить доставку
          </button>
        </>
      )}
      {data && data.submissions.length > 0 && (
        <details className="bank-history">
          <summary>История отправок · {data.submissions.length}</summary>
          {data.submissions.map((item) => (
            <article className="submission-history-item" key={item.id}>
              <strong>
                Версия {item.revision} · {bankLabels[item.state]}
              </strong>
              <small>
                {timestamp(item.createdAt)}
                {item.bankReference ? " · " + item.bankReference : ""}
              </small>
              {item.reason && <p>{item.reason}</p>}
              {item.packet.clarifications.length > 0 && (
                <p className="document-text">
                  {item.packet.clarifications.join("\n\n")}
                </p>
              )}
              <AttachmentList attachments={item.packet.additionalFiles} />
            </article>
          ))}
        </details>
      )}
    </section>
  );
}
