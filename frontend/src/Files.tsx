import { useEffect, useState } from "react";
import type { components } from "./api/schema";
import { request, ApiError } from "./api/client";
import { Field, Illustration } from "./components";

const formatFileSize = (size: number) =>
  size < 1024 ? `${size} Б` : `${(size / 1024).toFixed(0)} КБ`;

export type Attachment = components["schemas"]["Attachment"];
export type PendingFile = {
  file?: File;
  image?: { url: string; attachmentId?: string | null };
  caption: string;
  key: string;
  uploaded?: Attachment;
};

export async function uploadFile(
  projectId: string,
  stageId: string | null,
  purpose: "JOURNAL" | "BANK" | "PROPOSAL",
  item: PendingFile,
): Promise<Attachment> {
  if (item.uploaded) return item.uploaded;
  if (!item.file) throw new Error("Выберите файл для загрузки");
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

function captionLabel(file?: File) {
  if (file?.type.startsWith("image/")) return "Подпись загруженной фотографии";
  return "Пояснение к документу";
}

export function FilePicker({
  items,
  onChange,
  disabled = false,
  idPrefix = "journal-files",
  externalError,
  imagesOnly = false,
}: Readonly<{
  items: PendingFile[];
  onChange: (items: PendingFile[]) => void;
  disabled?: boolean;
  idPrefix?: string;
  externalError?: string;
  imagesOnly?: boolean;
}>) {
  const [error, setError] = useState("");
  function select(files: FileList | null) {
    if (!files) return;
    const chosen = Array.from(files);
    const imageTypes = new Set(["image/jpeg", "image/png", "image/webp"]);
    if (
      imagesOnly &&
      chosen.some((f) => !imageTypes.has(f.type) || f.size === 0)
    ) {
      setError("Выберите непустое изображение JPEG, PNG или WebP");
      return;
    }
    if (chosen.length + items.length > 10) {
      setError(
        imagesOnly
          ? "В смету можно добавить до 10 изображений"
          : "На запись можно добавить до 10 файлов",
      );
      return;
    }
    if (
      chosen.some(
        (f) => f.size > (f.type === "application/pdf" ? 20 : 10) * 1024 * 1024,
      )
    ) {
      setError(
        imagesOnly
          ? "Изображение — до 10 МиБ"
          : "Фото — до 10 МиБ, PDF — до 20 МиБ",
      );
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
  function move(index: number, offset: number) {
    const next = [...items];
    [next[index], next[index + offset]] = [next[index + offset], next[index]];
    onChange(next);
  }
  return (
    <div className="file-picker">
      <label className="file-picker-label">
        {" "}
        <span>
          {imagesOnly ? "Добавить изображения" : "Добавить фото или PDF"}
        </span>{" "}
        <input
          type="file"
          className="sr-only"
          accept={
            imagesOnly
              ? "image/jpeg,image/png,image/webp"
              : "image/jpeg,image/png,image/webp,application/pdf"
          }
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
        {imagesOnly
          ? "JPEG, PNG или WebP — до 10 МиБ каждый. До 10 изображений. Укажите подпись к каждому."
          : "Фото — до 10 МиБ, PDF — до 20 МиБ. До 10 файлов на запись. Доступ только участникам стройки."}
      </p>
      {(error || externalError) && (
        <p id={idPrefix + "-error"} role="alert" className="field-error">
          {error || externalError}
        </p>
      )}
      {items.map((item, i) => (
        <div className="file-preview" key={item.key}>
          {item.file?.type.startsWith("image/") && (
            <LocalPreview file={item.file} caption={item.caption} />
          )}
          {item.image && (
            <Illustration
              url={item.image.url}
              caption={item.caption}
              compact
              external={!item.image.attachmentId}
              imageLabel
            />
          )}
          <div className="file-preview-body">
            <strong>{item.file?.name ?? `Изображение ${i + 1}`}</strong>
            <small>
              {item.file ? formatFileSize(item.file.size) : "Сохранено"}
              {item.uploaded ? " · Загружен" : ""}
            </small>
            <Field
              id={idPrefix + "-caption-" + i}
              label={
                imagesOnly ? "Подпись изображения" : captionLabel(item.file)
              }
              required={imagesOnly || item.file?.type.startsWith("image/")}
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
                          key: imagesOnly ? f.key : crypto.randomUUID(),
                          uploaded: imagesOnly ? f.uploaded : undefined,
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
              {imagesOnly ? "Убрать из сметы" : `Убрать ${item.file?.name}`}
            </button>
            {imagesOnly && (
              <div className="actions">
                <button
                  type="button"
                  className="text-button"
                  disabled={disabled || i === 0}
                  aria-label={`Переместить изображение ${i + 1} выше`}
                  onClick={() => move(i, -1)}
                >
                  Выше
                </button>
                <button
                  type="button"
                  className="text-button"
                  disabled={disabled || i === items.length - 1}
                  aria-label={`Переместить изображение ${i + 1} ниже`}
                  onClick={() => move(i, 1)}
                >
                  Ниже
                </button>
              </div>
            )}
          </div>
        </div>
      ))}
    </div>
  );
}
function LocalPreview({
  file,
  caption,
}: Readonly<{ file: File; caption: string }>) {
  const [url, setUrl] = useState("");
  useEffect(() => {
    const local = URL.createObjectURL(file);
    setUrl(local);
    return () => URL.revokeObjectURL(local);
  }, [file]);
  if (!url) return null;
  return (
    <Illustration
      url={url}
      caption={caption || "Предпросмотр выбранной фотографии"}
      compact
      external={false}
      imageLabel
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
