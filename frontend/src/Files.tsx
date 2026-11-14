import { useEffect, useState } from "react";
import type { components } from "./api/schema";
import { request, ApiError } from "./api/client";
import { Field, Illustration } from "./components";

const formatFileSize = (size: number) =>
  size < 1024 ? `${size} Б` : `${(size / 1024).toFixed(0)} КБ`;

export type Attachment = components["schemas"]["Attachment"];
export type PendingFile = {
  file: File;
  caption: string;
  key: string;
  uploaded?: Attachment;
};

export async function uploadFile(
  projectId: string,
  stageId: string | null,
  purpose: "JOURNAL" | "BANK",
  item: PendingFile,
): Promise<Attachment> {
  if (item.uploaded) return item.uploaded;
  const csrf = await request<components["schemas"]["CsrfToken"]>("/auth/csrf");
  const body = new FormData();
  body.append("file", item.file);
  body.append("purpose", purpose);
  body.append("caption", item.caption);
  if (stageId) body.append("stageId", stageId);
  const response = await fetch(
    `/api/v1/projects/${encodeURIComponent(projectId)}/attachments`,
    {
      method: "POST",
      credentials: "same-origin",
      headers: { "X-CSRF-TOKEN": csrf.token, "Idempotency-Key": item.key },
      body,
    },
  );
  if (!response.ok) {
    const problem = await response.json().catch(() => ({}));
    throw new ApiError(
      response.status,
      problem.code ?? "UNKNOWN",
      problem.title ?? "Не удалось загрузить файл",
      problem.fieldErrors ?? {},
    );
  }
  return response.json();
}

export function FilePicker({
  items,
  onChange,
  disabled = false,
  idPrefix = "journal-files",
  externalError,
}: Readonly<{
  items: PendingFile[];
  onChange: (items: PendingFile[]) => void;
  disabled?: boolean;
  idPrefix?: string;
  externalError?: string;
}>) {
  const [error, setError] = useState("");
  function select(files: FileList | null) {
    if (!files) return;
    const chosen = Array.from(files);
    if (chosen.length + items.length > 10) {
      setError("На запись можно добавить до 10 файлов");
      return;
    }
    if (
      chosen.some(
        (f) => f.size > (f.type === "application/pdf" ? 20 : 10) * 1024 * 1024,
      )
    ) {
      setError("Фото — до 10 МиБ, PDF — до 20 МиБ");
      return;
    }
    setError("");
    onChange([
      ...items,
      ...chosen.map((file) => ({
        file,
        caption: "",
        key: crypto.randomUUID(),
      })),
    ]);
  }
  return (
    <div className="file-picker">
      <label className="file-picker-label">
        {" "}
        <span>Добавить фото или PDF</span>{" "}
        <input
          type="file"
          accept="image/jpeg,image/png,image/webp,application/pdf"
          multiple
          disabled={disabled || items.length >= 10}
          onChange={(e) => {
            select(e.target.files);
            e.target.value = "";
          }}
          aria-describedby={
            idPrefix +
            "-limits" +
            (error || externalError ? " " + idPrefix + "-error" : "")
          }
          aria-invalid={Boolean(error || externalError)}
        />
      </label>
      <p id={idPrefix + "-limits"} className="hint">
        Фото — до 10 МиБ, PDF — до 20 МиБ. До 10 файлов на запись. Доступ только
        участникам стройки.
      </p>
      {(error || externalError) && (
        <p id={idPrefix + "-error"} role="alert" className="field-error">
          {error || externalError}
        </p>
      )}
      {items.map((item, i) => (
        <div className="file-preview" key={item.key}>
          {item.file.type.startsWith("image/") && (
            <LocalPreview file={item.file} />
          )}
          <div className="file-preview-body">
            <strong>{item.file.name}</strong>
            <small>
              {formatFileSize(item.file.size)}
              {item.uploaded ? " · Загружен" : ""}
            </small>
            <Field
              id={idPrefix + "-caption-" + i}
              label={
                item.file.type.startsWith("image/")
                  ? "Подпись загруженной фотографии"
                  : "Пояснение к документу"
              }
              required={item.file.type.startsWith("image/")}
              maxLength={300}
              value={item.caption}
              disabled={disabled}
              onChange={(e) =>
                onChange(
                  items.map((f, j) =>
                    j === i
                      ? {
                          ...f,
                          caption: e.target.value,
                          key: crypto.randomUUID(),
                          uploaded: undefined,
                        }
                      : f,
                  ),
                )
              }
            />
            <button
              type="button"
              className="text-button"
              disabled={disabled}
              onClick={() => onChange(items.filter((_, j) => j !== i))}
            >
              Убрать {item.file.name}
            </button>
          </div>
        </div>
      ))}
    </div>
  );
}
function LocalPreview({ file }: Readonly<{ file: File }>) {
  const [url, setUrl] = useState("");
  useEffect(() => {
    const local = URL.createObjectURL(file);
    setUrl(local);
    return () => URL.revokeObjectURL(local);
  }, [file]);
  return (
    <img
      src={url}
      alt="Предпросмотр выбранной фотографии"
      className="file-thumbnail"
    />
  );
}
export function AttachmentList({
  attachments,
}: Readonly<{ attachments: Attachment[] }>) {
  return (
    <div className="attachment-list">
      {attachments.map((file) =>
        file.contentType.startsWith("image/") ? (
          <Illustration
            key={file.id}
            url={file.contentUrl}
            caption={file.caption}
            compact
          />
        ) : (
          <a
            key={file.id}
            className="file-download"
            href={file.contentUrl}
            download={file.name}
          >
            <span aria-hidden="true">▤</span>
            <span>
              <strong>{file.name}</strong>
              <small>
                PDF · {formatFileSize(file.size)}
                {file.caption ? " · " + file.caption : ""}
              </small>
            </span>
            <span>Скачать</span>
          </a>
        ),
      )}
    </div>
  );
}
