import { useEffect, useRef, useState } from "react";
import type { components } from "./api/schema";
import {
  type Account,
  type Project,
  api,
  request,
  errorMessage,
  commandKey,
} from "./api/client";

import { Notice, formatDateTime as timestamp } from "./components";
import { ActorLabel } from "./Participants";
type Completion = components["schemas"]["Completion"];
type KeyRef = { body: string; key: string } | null;
export function CompletionPanel({
  project,
  account,
  onChanged,
}: Readonly<{
  project: Project;
  account: Account;
  onChanged: (project: Project) => void;
}>) {
  const [data, setData] = useState<Completion | null>(null),
    [error, setError] = useState(""),
    [confirm, setConfirm] = useState(false),
    [busy, setBusy] = useState(false);
  const key = useRef<KeyRef>(null),
    heading = useRef<HTMLHeadingElement>(null);
  const root = `/projects/${project.id}/completion`;
  useEffect(() => {
    let active = true;
    request<Completion>(root)
      .then((value) => {
        if (active) setData(value);
      })
      .catch((error_) => {
        if (active) setError(errorMessage(error_));
      });
    return () => {
      active = false;
    };
  }, [root, project.completedAt]);
  async function complete() {
    if (!data) return;
    setBusy(true);
    setError("");
    try {
      const body = { expectedVersion: data.version };
      setData(
        await request<Completion>(root, "POST", {
          ...body,
          idempotencyKey: commandKey(key, body),
        }),
      );
      onChanged(await api.get(project.id));
      setConfirm(false);
      requestAnimationFrame(() => heading.current?.focus());
    } catch (error_) {
      setError(errorMessage(error_));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section
      className="panel completion-panel"
      aria-labelledby="completion-title"
    >
      <div>
        <p className="eyebrow">Общий итог</p>
        <h2 id="completion-title" ref={heading} tabIndex={-1}>
          {data?.completedAt
            ? "Строительство завершено"
            : "Готовность строительства"}
        </h2>
      </div>
      {error && (
        <Notice error>
          {error}{" "}
          <button
            type="button"
            className="text-button"
            onClick={() =>
              request<Completion>(root)
                .then(setData)
                .catch((error_) => setError(errorMessage(error_)))
            }
          >
            Обновить итог
          </button>
        </Notice>
      )}
      {!data && !error && <p>Проверяем принятые этапы…</p>}
      {data && (
        <>
          <p className="completion-count">
            Принято {data.accepted} из {data.total} этапов
          </p>
          <progress
            aria-label="Принято этапов строительства"
            max={data.total || 1}
            value={data.accepted}
          />
          {data.completedAt ? (
            <>
              <p>
                Заказчик подтвердил завершение · {timestamp(data.completedAt)}
              </p>
              <ActorLabel snapshot={data.completedBy} role="CUSTOMER" />
              <p className="hint">
                История сохранена. Банковские подтверждения и дополнения
                доступны в принятых этапах.
              </p>
            </>
          ) : (
            <>
              <p>
                {data.reason ??
                  "Все этапы приняты. Подтвердите общий итог строительства."}
              </p>
              {data.canComplete &&
                account.id === project.customerId &&
                !confirm && (
                  <button
                    type="button"
                    className="button primary"
                    onClick={() => {
                      setConfirm(true);
                      requestAnimationFrame(() =>
                        document
                          .getElementById("confirm-construction-complete")
                          ?.focus(),
                      );
                    }}
                  >
                    Подтвердить завершение строительства
                  </button>
                )}
              {confirm && (
                <div className="confirmation-box">
                  <h3>Завершить строительство?</h3>
                  <p>
                    Вы приняли все {data.total} этапов. После подтверждения
                    выполнение и приёмка закроются, история останется доступной.
                    Банковская проверка ведётся отдельно.
                  </p>
                  <div className="form-actions">
                    <button
                      type="button"
                      className="button primary"
                      disabled={busy}
                      id="confirm-construction-complete"
                      onClick={() => complete()}
                    >
                      {busy ? "Сохраняем…" : "Подтверждаю завершение"}
                    </button>
                    <button
                      type="button"
                      className="button secondary"
                      disabled={busy}
                      onClick={() => {
                        setConfirm(false);
                        requestAnimationFrame(() => heading.current?.focus());
                      }}
                    >
                      Продолжить просмотр
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </>
      )}
    </section>
  );
}
