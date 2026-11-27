import { useEffect, useRef, useState, type SubmitEvent } from "react";

import type { components } from "./api/schema";
import {
  type Account,
  type Project,
  type Stage,
  request,
  errorMessage,
  ApiError,
  commandKey,
} from "./api/client";

import { Area, Notice, formatDateTime as timestamp } from "./components";
import { ActorLabel } from "./Participants";
import { AttachmentList } from "./Files";
import { BankConfirmationPanel, bankLabels } from "./BankConfirmation";
type AcceptancePage = components["schemas"]["AcceptancePage"];
type Submission = components["schemas"]["AcceptanceSubmission"];
type JournalPage = components["schemas"]["JournalPage"];
type Entry = components["schemas"]["JournalEntry"];
type KeyRef = { body: string; key: string } | null;
const acceptanceLabels = {
  NOT_SUBMITTED: "Не передан на приёмку",
  OPEN: "На приёмке",
  ACCEPTED: "Принят заказчиком",
  RETURNED: "Нужны исправления",
};
export function AcceptancePanel({
  project,
  stage,
  account,
}: Readonly<{
  project: Project;
  stage: Stage;
  account: Account;
}>) {
  const [data, setData] = useState<AcceptancePage | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [mode, setMode] = useState<"submit" | "return" | null>(null),
    [summary, setSummary] = useState(""),
    [comment, setComment] = useState(""),
    [selected, setSelected] = useState<string[]>([]),
    [entries, setEntries] = useState<Entry[]>([]),
    [next, setNext] = useState<string | null>(null),
    [fields, setFields] = useState<Record<string, string>>({}),
    [notice, setNotice] = useState("");
  const key = useRef<KeyRef>(null),
    heading = useRef<HTMLHeadingElement>(null),
    form = useRef<HTMLDivElement>(null);
  const root = `/projects/${project.id}/stages/${stage.id}`;
  const owner = account.id === project.customerId;
  const works =
    (project.permissions?.canRecordWork ??
      account.id === project.contractorId) &&
    !project.completedAt;
  async function load() {
    setData(await request<AcceptancePage>(root + "/acceptance"));
  }
  useEffect(() => {
    if (stage.status !== "WORK_COMPLETED") return;
    let active = true;
    request<AcceptancePage>(root + "/acceptance")
      .then((value) => {
        if (active) setData(value);
      })
      .catch((error_) => {
        if (active) setError(errorMessage(error_));
      });
    return () => {
      active = false;
    };
  }, [root, stage.status]);
  useEffect(() => {
    if (mode)
      form.current?.querySelector<HTMLTextAreaElement>("textarea")?.focus();
  }, [mode]);
  async function chooseEntries(cursor: string | null = null) {
    setBusy(true);
    setError("");
    try {
      const page = await request<JournalPage>(
        root +
          "/journal" +
          (cursor ? "?cursor=" + encodeURIComponent(cursor) : ""),
      );
      setEntries((previous) =>
        cursor ? [...previous, ...page.items] : page.items,
      );
      setNext(page.nextCursor ?? null);
      setMode("submit");
    } catch (error_) {
      setError(errorMessage(error_));
    } finally {
      setBusy(false);
    }
  }
  async function submit(e: SubmitEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setFields({});
    try {
      const body = {
        expectedStageVersion: stage.version,
        summary,
        entryIds: selected,
      };
      await request<Submission>(root + "/acceptance", "POST", {
        ...body,
        idempotencyKey: commandKey(key, body),
      });
      await load();
      setMode(null);
      setSummary("");
      setSelected([]);
      key.current = null;
      setNotice("Этап передан на приёмку");
      requestAnimationFrame(() => heading.current?.focus());
    } catch (error_) {
      setError(errorMessage(error_));
      if (error_ instanceof ApiError) setFields(error_.fieldErrors);
    } finally {
      setBusy(false);
    }
  }
  async function decide(decision: "ACCEPT" | "RETURN") {
    const latest = data?.submissions[0];
    if (!latest) return;
    setBusy(true);
    setError("");
    setFields({});
    try {
      const body = {
        expectedVersion: latest.version,
        decision,
        comment: decision === "RETURN" ? comment : null,
      };
      await request<Submission>(
        root + "/acceptance/" + latest.id + "/decision",
        "POST",
        { ...body, idempotencyKey: commandKey(key, body) },
      );
      await load();
      setMode(null);
      setComment("");
      key.current = null;
      setNotice(
        decision === "ACCEPT"
          ? "Этап принят заказчиком"
          : "Замечания переданы подрядчику",
      );
      requestAnimationFrame(() => heading.current?.focus());
    } catch (error_) {
      setError(errorMessage(error_));
      if (error_ instanceof ApiError) setFields(error_.fieldErrors);
    } finally {
      setBusy(false);
    }
  }
  if (stage.status !== "WORK_COMPLETED") return null;
  const latest = data?.submissions[0];
  return (
    <section
      className="panel acceptance-panel"
      data-acceptance-loaded={data ? "true" : "false"}
      data-submission-id={latest?.id}
      aria-labelledby="acceptance-title"
    >
      <div className="heading-row">
        <div>
          <p className="eyebrow">Проверка результата</p>
          <h2 id="acceptance-title" ref={heading} tabIndex={-1}>
            Приёмка этапа
          </h2>
        </div>
        {data && (
          <span className={"badge acceptance-state-" + data.state}>
            {acceptanceLabels[data.state]}
          </span>
        )}
      </div>
      {notice && <Notice>{notice}</Notice>}
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
            Обновить приёмку
          </button>
        </Notice>
      )}
      {!data && !error && <p>Загружаем историю сдач…</p>}
      {latest && (
        <div className="acceptance-current">
          <p className="document-text">{latest.summary}</p>
          <div className="attribution-row">
            <ActorLabel snapshot={latest.authorSnapshot} />
            <time dateTime={latest.createdAt}>
              {timestamp(latest.createdAt)}
            </time>
          </div>
          {latest.state === "RETURNED" && (
            <div className="acceptance-remark">
              <strong>Что нужно исправить</strong>
              <p className="document-text">{latest.comment}</p>
              <ActorLabel snapshot={latest.decidedBy} role="CUSTOMER" />
            </div>
          )}
          {latest.state === "OPEN" && (
            <p>
              {owner
                ? "Проверьте результат и подтверждения подрядчика."
                : "Заказчик проверяет результат. Решение появится здесь."}
            </p>
          )}
          {latest.state === "ACCEPTED" && (
            <p className="acceptance-success">
              Решение заказчика сохранено
              {latest.decidedAt ? " · " + timestamp(latest.decidedAt) : ""}.
            </p>
          )}
          <details className="acceptance-evidence">
            <summary>Подтверждения сдачи · {latest.evidence.length}</summary>
            {latest.evidence.map((item) => (
              <div className="evidence-entry" key={item.id}>
                <p className="document-text">{item.text}</p>
                <AttachmentList attachments={item.attachments} />
              </div>
            ))}
          </details>
        </div>
      )}
      {data?.state === "NOT_SUBMITTED" && (
        <p>
          {owner
            ? "Подрядчик отметил выполнение работ. Дождитесь сдачи с итогом и фотографиями."
            : "Добавьте итог работ и выберите подтверждения из журнала."}
        </p>
      )}
      {owner &&
        data?.state === "OPEN" &&
        !project.completedAt &&
        mode !== "return" && (
          <div className="form-actions">
            <button
              type="button"
              className="button primary"
              disabled={busy}
              onClick={() => decide("ACCEPT")}
            >
              Принять этап
            </button>
            <button
              type="button"
              className="button secondary"
              disabled={busy}
              onClick={() => setMode("return")}
            >
              Вернуть с замечаниями
            </button>
          </div>
        )}
      {works &&
        data &&
        (data.state === "NOT_SUBMITTED" || data.state === "RETURNED") &&
        mode !== "submit" && (
          <button
            type="button"
            className="button primary"
            disabled={busy}
            onClick={() => chooseEntries()}
          >
            {data.state === "RETURNED"
              ? "Сдать повторно"
              : "Передать на приёмку"}
          </button>
        )}
      <div ref={form}>
        {mode === "return" && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              decide("RETURN");
            }}
            aria-busy={busy}
          >
            <Area
              id="acceptance-comment"
              label="Замечания к этапу"
              value={comment}
              onChange={setComment}
              maxLength={5000}
              required
              error={fields.comment}
            />
            <div className="form-actions">
              <button type="submit" className="button primary" disabled={busy}>
                Отправить замечания
              </button>
              <button
                type="button"
                className="button secondary"
                disabled={busy}
                onClick={() => {
                  setMode(null);
                  requestAnimationFrame(() => heading.current?.focus());
                }}
              >
                Отмена
              </button>
            </div>
          </form>
        )}
        {mode === "submit" && (
          <form onSubmit={submit} aria-busy={busy}>
            <Area
              id="acceptance-summary"
              label="Итог выполненного этапа"
              value={summary}
              onChange={setSummary}
              maxLength={5000}
              required
              error={fields.summary}
            />
            <fieldset
              className="evidence-picker"
              disabled={busy}
              aria-describedby={
                fields.entryIds ? "evidence-error" : "evidence-hint"
              }
            >
              <legend>Подтверждения из журнала</legend>
              <p className="hint" id="evidence-hint">
                Выберите до 20 записей. Нужна хотя бы одна загруженная
                фотография; внешняя ссылка не заменяет её.
              </p>
              {fields.entryIds && (
                <p className="field-error" id="evidence-error" role="alert">
                  {fields.entryIds}
                </p>
              )}
              {entries.map((entry) => (
                <label
                  aria-label={entry.text}
                  key={entry.id}
                  className="evidence-option"
                >
                  <input
                    type="checkbox"
                    checked={selected.includes(entry.id)}
                    onChange={(e) =>
                      setSelected(
                        e.target.checked
                          ? [...selected, entry.id]
                          : selected.filter((id) => id !== entry.id),
                      )
                    }
                  />{" "}
                  <span>
                    <strong>{entry.text}</strong>
                    <small>
                      {timestamp(entry.createdAt)} · Закрытых вложений:{" "}
                      {entry.attachments?.length ?? 0}
                    </small>
                  </span>
                </label>
              ))}
              {entries.length === 0 && (
                <p>
                  В журнале пока нет записей. Добавьте фотоотчёт, затем
                  вернитесь к сдаче.
                </p>
              )}
              {next && (
                <button
                  type="button"
                  className="text-button"
                  onClick={() => chooseEntries(next)}
                >
                  Показать ещё записи
                </button>
              )}
            </fieldset>
            <div className="form-actions">
              <button
                type="submit"
                className="button primary"
                disabled={busy || selected.length === 0 || selected.length > 20}
              >
                {busy ? "Передаём…" : "Отправить на приёмку"}
              </button>
              <button
                type="button"
                className="button secondary"
                disabled={busy}
                onClick={() => {
                  setMode(null);
                  requestAnimationFrame(() => heading.current?.focus());
                }}
              >
                Отмена
              </button>
            </div>
          </form>
        )}
      </div>
      {data && data.submissions.length > 0 && (
        <details className="acceptance-history">
          <summary>История сдач · {data.submissions.length}</summary>
          {data.submissions.map((item) => (
            <article
              className="submission-history-item"
              data-submission-id={item.id}
              aria-label={"Сдача №" + item.cycleNumber}
              key={item.id}
            >
              <div className="heading-row">
                <strong>Сдача №{item.cycleNumber}</strong>
                <span>{acceptanceLabels[item.state]}</span>
              </div>
              <p className="document-text">{item.summary}</p>
              <ActorLabel snapshot={item.authorSnapshot} />
              {item.comment && (
                <blockquote className="journal-quote">
                  {item.comment}
                </blockquote>
              )}
              {item.decidedBy && (
                <ActorLabel snapshot={item.decidedBy} role="CUSTOMER" />
              )}
              <small>
                {timestamp(item.createdAt)}
                {item.decidedAt
                  ? " · Решение: " + timestamp(item.decidedAt)
                  : ""}
              </small>
            </article>
          ))}
        </details>
      )}
      {data?.state === "ACCEPTED" && data.mortgage && owner && latest && (
        <BankConfirmationPanel
          project={project}
          stage={stage}
          acceptance={latest}
        />
      )}
      {data?.bankStatus && !owner && (
        <p className="bank-brief">
          Подтверждение для демо-банка: {bankLabels[data.bankStatus]}
        </p>
      )}
    </section>
  );
}
