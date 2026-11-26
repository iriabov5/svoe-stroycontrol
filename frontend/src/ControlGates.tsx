import { useEffect, useState } from "react";
import {
  api,
  errorMessage,
  type Account,
  type ControlGate,
} from "./api/client";
import { Notice } from "./components";

const statusLabels = {
  BLOCKED: "Нужно подготовиться",
  WAITING_CUSTOMER: "Ждём заказчика",
  WAITING_CONTRACTOR: "Ждём подрядчика",
  COMPLETED: "Точка пройдена",
} as const;

/** Показывает участнику следующий шаг до необратимой работы без подмены технадзора. */
export function ControlGates({
  projectId,
  account,
}: Readonly<{ projectId: string; account: Account }>) {
  const [gates, setGates] = useState<ControlGate[] | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let current = true;
    api
      .listControlGates(projectId)
      .then((result) => current && setGates(result))
      .catch((reason) => current && setError(errorMessage(reason)));
    return () => {
      current = false;
    };
  }, [projectId]);

  if (error) return <Notice error>{error}</Notice>;
  if (!gates) return <Notice>Проверяем контрольные точки…</Notice>;
  if (gates.length === 0) {
    return (
      <Notice>
        Контрольные точки появятся, когда в плане строительства будут этапы.
      </Notice>
    );
  }

  const primary =
    gates.find((gate) => gate.status.startsWith("WAITING")) ?? gates[0];
  const next = gates.filter((gate) => gate.key !== primary.key);
  const responsibility =
    primary.responsibleRole === account.role
      ? "Требуется действие"
      : "Ожидается действие";
  return (
    <section className="control-gates" aria-labelledby="control-gates-title">
      <div className="control-gates-heading">
        <div>
          <p className="eyebrow">Навигатор следующего действия</p>
          <h2 id="control-gates-title">Контрольные точки</h2>
        </div>
        <p>Подсказки не заменяют технический надзор и приёмку.</p>
      </div>
      <article
        className={`control-gate-primary ${primary.status.toLowerCase()}`}
      >
        <div className="control-gate-primary-top">
          <span className="badge">{responsibility}</span>
          <span>{statusLabels[primary.status]}</span>
        </div>
        <div className="control-gate-primary-content">
          <div>
            <p className="eyebrow">Контрольная точка</p>
            <h3>{primary.title}</h3>
            <p>{primary.detail}</p>
          </div>
          <div className="control-gate-action">
            <p>Следующий шаг</p>
            <strong>{primary.action}</strong>
            <a
              className="button primary"
              href={
                primary.stageId
                  ? `#/projects/${projectId}/stages/${primary.stageId}`
                  : `#/projects/${projectId}`
              }
            >
              Открыть этап
            </a>
          </div>
        </div>
      </article>
      <div className="control-gates-timeline">
        <p>Что будет дальше</p>
        <ol>
          {next.map((gate) => (
            <li key={gate.key}>
              <strong>{gate.title}</strong>
              <small>{statusLabels[gate.status]}</small>
            </li>
          ))}
        </ol>
      </div>
    </section>
  );
}
