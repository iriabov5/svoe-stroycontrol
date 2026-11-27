import {
  FilePicker,
  AttachmentList,
  uploadFile,
  type PendingFile,
} from "./Files";

import { ActorLabel } from "./Participants";
import { useEffect, useRef, useState, type SubmitEvent } from "react";

import type { components } from "./api/schema";
import {
  request,
  ApiError,
  errorMessage,
  commandKey,
  type Account,
  type Project,
  type Stage,
} from "./api/client";

import { Area, Field, Illustration, Notice } from "./components";

type Entry = components["schemas"]["JournalEntry"];
type Page = components["schemas"]["JournalPage"];
type Kind = components["schemas"]["JournalKind"];
type Photo = components["schemas"]["ProposalImage"];
const kinds: Record<Kind, string> = {
  NOTE: "Ход работ",
  QUESTION: "Вопрос",
  APPROVAL: "На согласование",
};
const statuses: Record<Entry["status"], string> = {
  PUBLISHED: "Опубликовано",
  PENDING: "Требует ответа",
  ANSWERED: "Ответ получен",
  APPROVED: "Согласовано",
  DECLINED: "Не согласовано",
};
const when = (date: string) =>
  new Date(date).toLocaleString("ru-RU", {
    day: "numeric",
    month: "short",
    hour: "2-digit",
    minute: "2-digit",
  });
const path = (project: string, stage: string) =>
  `/projects/${encodeURIComponent(project)}/stages/${encodeURIComponent(stage)}/journal`;

export function Journal({
  project,
  stage,
  account,
}: Readonly<{
  project: Project;
  stage: Stage;
  account: Account;
}>) {
  const [data, setData] = useState<Page | null>(null),
    [error, setError] = useState(""),
    [loading, setLoading] = useState(false),
    [onlyPending, setOnlyPending] = useState(false),
    [attempt, setAttempt] = useState(0),
    [adding, setAdding] = useState(false),
    [source, setSource] = useState<Entry | null>(null),
    [notice, setNotice] = useState(""),
    [focusId, setFocusId] = useState<string | null>(null);
  const heading = useRef<HTMLHeadingElement>(null);
  const pendingFilter = useRef(false);
  const owner = account.id === project.customerId,
    contractor =
      !project.completedAt &&
      (project.permissions?.canPublishJournal ??
        account.id === project.contractorId);
  const root = path(project.id, stage.id),
    canPublish = contractor && stage.status !== "PLANNED";
  useEffect(() => {
    let current = true;
    setLoading(true);
    setError("");
    request<Page>(root + `?awaitingOnly=${onlyPending}`)
      .then((value) => {
        if (current) setData(value);
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
  }, [root, onlyPending, attempt]);
  useEffect(() => {
    if (!focusId) return;
    const element = document.getElementById("journal-entry-" + focusId);
    (element ?? heading.current)?.focus();
    setFocusId(null);
  }, [focusId, data]);
  async function older() {
    if (!data?.nextCursor || loading) return;
    setLoading(true);
    setError("");
    try {
      const next = await request<Page>(
        root +
          `?awaitingOnly=${onlyPending}&cursor=${encodeURIComponent(data.nextCursor)}`,
      );
      setData({
        ...next,
        items: [
          ...new Map(
            [...data.items, ...next.items].map((e) => [e.id, e]),
          ).values(),
        ],
      });
    } catch (error_) {
      setError(errorMessage(error_));
    } finally {
      setLoading(false);
    }
  }
  function selectFilter(pending: boolean) {
    pendingFilter.current = pending;
    setOnlyPending(pending);
  }
  async function published(entry: Entry) {
    setAdding(false);
    setSource(null);
    setNotice("Запись опубликована");
    if (pendingFilter.current) selectFilter(false);
    const first = await request<Page>(root + "?awaitingOnly=false");
    setData(first);
    setFocusId(entry.id);
  }
  function answered(entry: Entry) {
    setData((old) =>
      old
        ? {
            ...old,
            awaitingCount: Math.max(0, old.awaitingCount - 1),
            items: old.items
              .map((e) => (e.id === entry.id ? entry : e))
              .filter((e) => !pendingFilter.current || e.status === "PENDING"),
          }
        : old,
    );
    if (pendingFilter.current) setAttempt((value) => value + 1);
    setNotice("Ответ сохранён");
    setFocusId(entry.id);
  }
  function emptyMessage() {
    if (onlyPending) {
      return "Сейчас нет вопросов, ожидающих ответа.";
    }
    if (contractor) {
      return "Начните с короткой записи о ходе работ. Фотографии можно добавить по ссылкам.";
    }
    return "Здесь появятся записи подрядчика о работах и вопросы для вас.";
  }
  return (
    <section
      className="panel journal"
      aria-labelledby="journal-heading"
      data-journal-loaded={data ? "true" : "false"}
    >
      <div className="section-heading">
        <div>
          <p className="eyebrow">Общий журнал этапа</p>
          <h2 id="journal-heading" tabIndex={-1} ref={heading}>
            Ход работ и вопросы
          </h2>
          <p className="hint">
            Фотографии, уточнения и решения рядом с работами.
          </p>
        </div>
        {data && data.awaitingCount > 0 && (
          <span className="badge journal-awaiting">
            {owner ? "Ждут вашего ответа" : "Ждём заказчика"}:{" "}
            {data.awaitingCount}
          </span>
        )}
      </div>
      <div className="journal-toolbar">
        <fieldset className="journal-filters" aria-label="Фильтр ленты">
          <button
            type="button"
            className="button secondary"
            aria-pressed={!onlyPending}
            disabled={loading}
            onClick={() => selectFilter(false)}
          >
            Все
          </button>
          <button
            type="button"
            className="button secondary"
            aria-pressed={onlyPending}
            disabled={loading}
            onClick={() => selectFilter(true)}
          >
            Требуют ответа
          </button>
        </fieldset>
        <button
          type="button"
          className="text-button"
          disabled={loading}
          onClick={() => setAttempt((a) => a + 1)}
        >
          Обновить ленту
        </button>
      </div>
      {notice && <Notice>{notice}</Notice>}
      {error && <Notice error>{error}</Notice>}
      {loading && !data && <Notice>Загружаем ленту…</Notice>}
      {canPublish && !adding && (
        <button
          type="button"
          className="button primary"
          onClick={() => {
            setAdding(true);
            setSource(null);
            setNotice("");
          }}
        >
          Добавить запись
        </button>
      )}
      {adding && canPublish && (
        <PublishForm
          stage={stage}
          source={source}
          root={root}
          onSaved={published}
          onCancel={() => {
            setAdding(false);
            setSource(null);
            heading.current?.focus();
          }}
        />
      )}
      {stage.status === "PLANNED" && (
        <p className="hint">
          После начала этапа подрядчик сможет добавлять фотографии и вопросы.
        </p>
      )}
      {!loading && data?.items.length === 0 && (
        <div className="journal-empty">
          <h3>{onlyPending ? "Всё обсуждено" : "Здесь будет история этапа"}</h3>
          <p>{emptyMessage()}</p>
        </div>
      )}
      <div className="journal-feed">
        {data?.items.map((entry) => (
          <JournalCard
            key={entry.id}
            entry={entry}
            root={root}
            owner={owner}
            readOnly={Boolean(project.completedAt)}
            onAnswered={answered}
            onFollowUp={
              contractor && stage.status === "IN_PROGRESS"
                ? (e) => {
                    setSource(e);
                    setAdding(true);
                    setNotice("");
                  }
                : undefined
            }
          />
        ))}
      </div>
      {data?.nextCursor && (
        <button
          type="button"
          className="button secondary"
          disabled={loading}
          onClick={() => older()}
        >
          {loading ? "Загружаем…" : "Показать предыдущие записи"}
        </button>
      )}
    </section>
  );
}
function PublishForm({
  stage,
  source,
  root,
  onSaved,
  onCancel,
}: Readonly<{
  stage: Stage;
  source: Entry | null;
  root: string;
  onSaved: (entry: Entry) => Promise<void>;
  onCancel: () => void;
}>) {
  const [kind, setKind] = useState<Kind>(
      source && source.kind !== "NOTE" ? source.kind : "NOTE",
    ),
    [text, setText] = useState(""),
    [photos, setPhotos] = useState<(Photo & { key: string })[]>([]),
    [localFiles, setLocalFiles] = useState<PendingFile[]>([]),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [fields, setFields] = useState<Record<string, string>>({});
  const key = useRef<{ body: string; key: string } | null>(null);
  const textId = "journal-text";
  useEffect(() => {
    document.getElementById(textId)?.focus();
  }, []);
  useEffect(() => {
    if (source) {
      setKind(source.kind === "NOTE" ? "QUESTION" : source.kind);
      document.getElementById(textId)?.focus();
    }
  }, [source]);
  async function publish(e: SubmitEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setFields({});
    try {
      const uploaded = [...localFiles];
      for (let i = 0; i < uploaded.length; i++) {
        if (!uploaded[i].uploaded) {
          const result = await uploadFile(
            root.split("/")[2],
            stage.id,
            "JOURNAL",
            uploaded[i],
          );
          uploaded[i] = { ...uploaded[i], uploaded: result };
          setLocalFiles([...uploaded]);
        }
      }
      const body = {
        kind,
        text,
        photos: photos.map(({ url, caption }) => ({ url, caption })),
        attachmentIds: uploaded.map((f) => f.uploaded!.id),
        sourceEntryId: source?.id ?? null,
      };
      const idempotencyKey = commandKey(key, body);
      const entry = await request<Entry>(root, "POST", {
        ...body,
        idempotencyKey,
      });
      await onSaved(entry);
      key.current = null;
    } catch (error_) {
      setError(errorMessage(error_));
      if (error_ instanceof ApiError) setFields(error_.fieldErrors);
    } finally {
      setBusy(false);
    }
  }
  function fieldLabel() {
    if (kind === "NOTE") {
      return "Что сделано";
    }
    if (kind === "QUESTION") {
      return "Вопрос заказчику";
    }
    return "Что нужно согласовать";
  }
  function publishLabel() {
    if (busy) {
      return "Отправляем…";
    }
    if (kind === "NOTE") {
      return "Опубликовать";
    }
    if (kind === "QUESTION") {
      return "Отправить вопрос";
    }
    return "Запросить согласование";
  }
  return (
    <form className="journal-composer" onSubmit={publish} aria-busy={busy}>
      <h3>{source ? "Уточнение к записи" : "Новая запись"}</h3>
      {source && (
        <blockquote className="journal-quote">{source.text}</blockquote>
      )}
      {error && <Notice error>{error}</Notice>}
      <fieldset disabled={busy} className="journal-form-fields">
        <legend className="sr-only">Содержание записи</legend>
        <div className="field">
          <label htmlFor="journal-kind">Тип записи</label>
          <select
            id="journal-kind"
            value={kind}
            onChange={(e) => setKind(e.target.value as Kind)}
          >
            {Object.entries(kinds).map(([k, v]) => (
              <option
                key={k}
                value={k}
                disabled={k !== "NOTE" && stage.status !== "IN_PROGRESS"}
              >
                {v}
              </option>
            ))}
          </select>
        </div>
        <Area
          id={textId}
          label={fieldLabel()}
          hint={
            kind === "APPROVAL"
              ? "Решение по записи не меняет согласованные цену, сроки и договор."
              : undefined
          }
          value={text}
          onChange={setText}
          maxLength={10000}
          error={fields.text}
          required
        />
        <FilePicker
          items={localFiles}
          onChange={setLocalFiles}
          disabled={busy}
          externalError={fields.file ?? fields.caption ?? fields.attachmentIds}
        />
        <div className="journal-photo-fields">
          {photos.map((photo, i) => (
            <div className="journal-photo-input" key={photo.key}>
              <h4>Фото {i + 1}</h4>
              <Field
                id={"journal-photo-url-" + i}
                label="Ссылка на фото"
                type="url"
                pattern="https://.*"
                required
                maxLength={2048}
                value={photo.url}
                error={fields[`photos.${i}.url`]}
                onChange={(e) =>
                  setPhotos(
                    photos.map((p, j) =>
                      i === j ? { ...p, url: e.target.value } : p,
                    ),
                  )
                }
              />
              <Field
                id={"journal-photo-caption-" + i}
                label="Что на фотографии"
                required
                maxLength={300}
                value={photo.caption}
                error={fields[`photos.${i}.caption`]}
                onChange={(e) =>
                  setPhotos(
                    photos.map((p, j) =>
                      i === j ? { ...p, caption: e.target.value } : p,
                    ),
                  )
                }
              />
              <button
                type="button"
                className="text-button"
                onClick={() => setPhotos(photos.filter((_, j) => j !== i))}
              >
                Удалить фото {i + 1}
              </button>
            </div>
          ))}
        </div>
        <button
          type="button"
          className="button secondary"
          disabled={photos.length >= 10}
          onClick={() =>
            setPhotos([
              ...photos,
              { key: crypto.randomUUID(), url: "", caption: "" },
            ])
          }
        >
          Добавить фото по ссылке
        </button>
        <p className="hint">
          Изображения по ссылке остаются иллюстрациями. Для закрытых фото и
          документов используйте загрузку с устройства.
        </p>
        <div className="form-actions">
          <button
            type="submit"
            className="button primary"
            disabled={kind !== "NOTE" && stage.status !== "IN_PROGRESS"}
          >
            {publishLabel()}
          </button>
          <button type="button" className="button secondary" onClick={onCancel}>
            Отмена
          </button>
        </div>
      </fieldset>
    </form>
  );
}
function JournalCard({
  entry,
  root,
  owner,
  readOnly,
  onAnswered,
  onFollowUp,
}: Readonly<{
  entry: Entry;
  root: string;
  owner: boolean;
  readOnly: boolean;
  onAnswered: (entry: Entry) => void;
  onFollowUp?: (entry: Entry) => void;
}>) {
  const [source, setSource] = useState<Entry | null>(null),
    [sourceError, setSourceError] = useState(""),
    [loading, setLoading] = useState(false);
  async function showSource() {
    setLoading(true);
    setSourceError("");
    try {
      setSource(await request<Entry>(root + "/" + entry.sourceEntryId));
    } catch (error_) {
      setSourceError(errorMessage(error_));
    } finally {
      setLoading(false);
    }
  }
  function entryStatus() {
    if (entry.status === "PENDING") {
      if (readOnly) {
        return "Без ответа · история";
      }
      if (owner) {
        return "Ждёт вашего ответа";
      }
      return "Ждём заказчика";
    }
    return statuses[entry.status];
  }
  return (
    <article
      className={
        "journal-entry " + (entry.status === "PENDING" ? "journal-pending" : "")
      }
      id={"journal-entry-" + entry.id}
      tabIndex={-1}
      aria-label={kinds[entry.kind]}
    >
      <div className="journal-entry-meta">
        <span className={"badge journal-kind-" + entry.kind}>
          {kinds[entry.kind]}
        </span>
        <span className="journal-state">{entryStatus()}</span>
      </div>
      <div className="journal-author">
        <ActorLabel snapshot={entry.authorSnapshot} />
        <time dateTime={entry.createdAt}>{when(entry.createdAt)}</time>
      </div>
      {entry.sourceEntryId && !source && (
        <button
          type="button"
          className="text-button"
          disabled={loading}
          onClick={() => showSource()}
        >
          {loading ? "Загружаем…" : "Посмотреть исходную запись"}
        </button>
      )}
      {sourceError && <Notice error>{sourceError}</Notice>}
      {source && (
        <blockquote className="journal-quote">
          <small>Исходная запись · {when(source.createdAt)}</small>
          <p>{source.text}</p>
        </blockquote>
      )}
      <p className="document-text">{entry.text}</p>
      {entry.photos.length > 0 && (
        <div className="journal-photos">
          {entry.photos.map((photo) => (
            <Illustration
              key={photo.id}
              url={photo.url}
              caption={photo.caption}
              compact
            />
          ))}
        </div>
      )}
      {(entry.attachments?.length ?? 0) > 0 && (
        <AttachmentList attachments={entry.attachments!} />
      )}
      {entry.response && (
        <div className="journal-response">
          <strong>
            {entry.kind === "QUESTION"
              ? "Ответ заказчика"
              : statuses[entry.status]}
          </strong>
          <time dateTime={entry.response.createdAt}>
            {when(entry.response.createdAt)}
          </time>
          <ActorLabel
            snapshot={entry.response.authorSnapshot}
            role="CUSTOMER"
          />
          {entry.response.text && (
            <p className="document-text">{entry.response.text}</p>
          )}
        </div>
      )}
      {owner && !readOnly && entry.status === "PENDING" && (
        <ReplyForm entry={entry} root={root} onSaved={onAnswered} />
      )}
      {onFollowUp && entry.status !== "PENDING" && (
        <button
          type="button"
          className="text-button"
          onClick={() => onFollowUp(entry)}
        >
          Уточнить
        </button>
      )}
    </article>
  );
}
function ReplyForm({
  entry,
  root,
  onSaved,
}: Readonly<{
  entry: Entry;
  root: string;
  onSaved: (entry: Entry) => void;
}>) {
  const [text, setText] = useState(""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [fields, setFields] = useState<Record<string, string>>({});
  const key = useRef<{ body: string; key: string } | null>(null);
  async function send(decision: "APPROVED" | "DECLINED" | null) {
    setError("");
    setFields({});
    if (
      (entry.kind === "QUESTION" || decision === "DECLINED") &&
      !text.trim()
    ) {
      setFields({
        text:
          entry.kind === "QUESTION"
            ? "Напишите ответ"
            : "Объясните, что нужно изменить",
      });
      document.getElementById("journal-answer-" + entry.id)?.focus();
      return;
    }
    setBusy(true);
    try {
      const body = {
        version: entry.version,
        text: text.trim() || null,
        decision,
      };
      const idempotencyKey = commandKey(key, body);
      const result = await request<Entry>(
        root + "/" + entry.id + "/response",
        "POST",
        { ...body, idempotencyKey },
      );
      onSaved(result);
      key.current = null;
    } catch (error_) {
      setError(errorMessage(error_));
      if (error_ instanceof ApiError) setFields(error_.fieldErrors);
    } finally {
      setBusy(false);
    }
  }
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        if (entry.kind === "QUESTION") send(null);
      }}
      className="journal-reply"
      aria-busy={busy}
    >
      {error && <Notice error>{error}</Notice>}
      <fieldset disabled={busy} className="journal-form-fields">
        <legend className="sr-only">Ответ заказчика</legend>
        <Area
          id={"journal-answer-" + entry.id}
          label={
            entry.kind === "QUESTION" ? "Ваш ответ" : "Комментарий к решению"
          }
          value={text}
          onChange={setText}
          maxLength={5000}
          required={entry.kind === "QUESTION"}
          error={fields.text}
        />
        {entry.kind === "APPROVAL" && (
          <p className="hint">
            Согласование этой записи не меняет цену, график, договор и приёмку
            этапа.
          </p>
        )}
        <div className="form-actions">
          {entry.kind === "QUESTION" ? (
            <button type="submit" className="button primary">
              {busy ? "Отправляем…" : "Ответить"}
            </button>
          ) : (
            <>
              <button
                type="button"
                className="button primary"
                onClick={() => send("APPROVED")}
              >
                Согласовать
              </button>
              <button
                type="button"
                className="button secondary"
                onClick={() => send("DECLINED")}
              >
                Не согласовать
              </button>
            </>
          )}
        </div>
      </fieldset>
    </form>
  );
}
