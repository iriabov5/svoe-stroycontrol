import { gateLink, type ControlGates } from "./guidance";
import { AcceptancePanel } from "./Acceptance";
import { useEffect, useRef, useState, type SubmitEvent } from "react";

import {
  api,
  ApiError,
  errorMessage,
  type Account,
  type Project,
  type Stage,
  type StagePlan,
} from "./api/client";

import { Journal } from "./Journal";
import { Field, Notice, PageTitle } from "./components";

export const workTypes: Record<Stage["workType"], string> = {
  FOUNDATION: "Фундамент",
  WALLS: "Стены",
  ROOF: "Крыша",
  UTILITIES: "Инженерные сети",
  FINISHING: "Отделка",
  OTHER: "Другое",
};
const statuses: Record<Stage["status"], string> = {
  PLANNED: "Запланирован",
  IN_PROGRESS: "В работе",
  WORK_COMPLETED: "Работы завершены",
};
const stageDate = (date: string | null | undefined) =>
  date
    ? new Intl.DateTimeFormat("ru-RU", {
        day: "numeric",
        month: "short",
        year: "numeric",
      }).format(new Date(`${date}T12:00:00`))
    : "Не указано";

export function StageTiming({ stage }: Readonly<{ stage: Stage }>) {
  const messages: [number, string][] = [
    [stage.startOverdueDays, "Начало просрочено на"],
    [stage.finishOverdueDays, "Завершение просрочено на"],
    [stage.startLateDays, "Начат позже плана на"],
    [stage.finishLateDays, "Завершён позже плана на"],
  ];
  return (
    <div className="stage-timing">
      {messages
        .filter(([days]) => days > 0)
        .map(([days, text]) => (
          <span className="delay" key={text}>
            {text} {days} дн.
          </span>
        ))}
    </div>
  );
}

export function StageForm({
  projectId,
  stage,
  onSaved,
  onCancel,
  onReload,
}: Readonly<{
  projectId: string;
  stage?: Stage;
  onSaved: (stage: Stage) => void;
  onCancel: () => void;
  onReload?: () => void;
}>) {
  const [name, setName] = useState(stage?.name ?? "");
  const [workType, setWorkType] = useState<Stage["workType"]>(
    stage?.workType ?? "FOUNDATION",
  );
  const [start, setStart] = useState(stage?.plannedStart ?? "");
  const [finish, setFinish] = useState(stage?.plannedFinish ?? "");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [invalid, setInvalid] = useState<Record<string, string>>({});
  const [stale, setStale] = useState(false);
  const formRef = useRef<HTMLFormElement>(null);
  useEffect(() => {
    formRef.current?.querySelector<HTMLInputElement>("input")?.focus();
  }, []);
  async function submit(event: SubmitEvent) {
    event.preventDefault();
    const fields = stagePlanErrors(name, start, finish);
    setInvalid(fields);
    if (Object.keys(fields).length) {
      document
        .getElementById(fields.name ? "stage-name" : "stage-finish")
        ?.focus();
      return;
    }
    setBusy(true);
    setError("");
    setStale(false);
    const body: StagePlan = {
      name,
      workType,
      plannedStart: start || null,
      plannedFinish: finish || null,
    };
    try {
      const result = stage
        ? await api.updateStage(projectId, stage.id, {
            ...body,
            version: stage.version,
          })
        : await api.createStage(projectId, body);
      onSaved(result);
    } catch (error_) {
      setError(errorMessage(error_));
      if (error_ instanceof ApiError) {
        if (error_.code === "INVALID_STAGE_DATES")
          setInvalid({ finish: error_.message });
        if (error_.code === "STALE_STAGE_VERSION") setStale(true);
      }
    } finally {
      setBusy(false);
    }
  }
  function submitLabel() {
    if (busy) {
      return "Сохраняем…";
    }
    if (stage) {
      return "Сохранить план";
    }
    return "Добавить этап";
  }
  return (
    <form
      ref={formRef}
      className="stage-form"
      onSubmit={submit}
      aria-busy={busy}
    >
      <h3>{stage ? "Изменить план этапа" : "Новый этап"}</h3>
      {error && <Notice error>{error}</Notice>}
      <Field
        id="stage-name"
        label="Название этапа"
        hint="Например, Фундамент дома"
        value={name}
        onChange={(event) => setName(event.target.value)}
        required
        maxLength={120}
        error={invalid.name}
      />
      <div className="field">
        <label htmlFor="stage-work-type">Вид работ</label>
        <select
          id="stage-work-type"
          value={workType}
          onChange={(event) =>
            setWorkType(event.target.value as Stage["workType"])
          }
        >
          {Object.entries(workTypes).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </select>
      </div>
      <p className="hint">
        Плановые даты необязательны. Их изменение не меняет фактические даты.
      </p>
      <div className="date-fields">
        <Field
          id="stage-start"
          label="Плановое начало"
          type="date"
          min="0001-01-01"
          max="9999-12-31"
          value={start}
          onChange={(event) => setStart(event.target.value)}
        />
        <Field
          id="stage-finish"
          label="Плановое завершение"
          type="date"
          min="0001-01-01"
          max="9999-12-31"
          value={finish}
          onChange={(event) => setFinish(event.target.value)}
          error={invalid.finish}
        />
      </div>
      {stale && onReload && (
        <button className="text-button" type="button" onClick={onReload}>
          Обновить этап, отменив правки
        </button>
      )}
      <div className="form-actions">
        <button
          type="submit"
          className="button primary"
          disabled={busy || stale}
        >
          {submitLabel()}
        </button>
        <button
          className="button secondary"
          type="button"
          onClick={onCancel}
          disabled={busy}
        >
          Отмена
        </button>
      </div>
    </form>
  );
}

export function StageList({
  project,
  account,
  guidance,
}: Readonly<{
  project: Project;
  account: Account;
  guidance?: ControlGates | null;
}>) {
  const [stages, setStages] = useState<Stage[] | null>(null);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [adding, setAdding] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  const owner =
    account.id === project.customerId &&
    !project.completedAt &&
    project.processMode !== "PROPOSAL_REQUIRED";
  const headingRef = useRef<HTMLHeadingElement>(null);
  useEffect(() => {
    if (notice) headingRef.current?.focus();
  }, [notice]);
  useEffect(() => {
    let current = true;
    setError("");
    api
      .listStages(project.id)
      .then((data) => {
        if (current) setStages(data);
      })
      .catch((error_) => {
        if (current) setError(errorMessage(error_));
      });
    return () => {
      current = false;
    };
  }, [project.id, attempt]);
  async function move(index: number, offset: number) {
    if (!stages || busy) return;
    const reordered = [...stages];
    [reordered[index], reordered[index + offset]] = [
      reordered[index + offset],
      reordered[index],
    ];
    setBusy(true);
    setError("");
    setNotice("");
    try {
      setStages(
        await api.reorderStages(
          project.id,
          reordered.map(({ id, version }) => ({ id, version })),
        ),
      );
      setNotice("Порядок этапов сохранён");
    } catch (error_) {
      setError(errorMessage(error_));
    } finally {
      setBusy(false);
    }
  }
  function renderStages() {
    if (stages === null) {
      return !error && <Notice>Загружаем этапы…</Notice>;
    }
    if (stages.length === 0) {
      return (
        <div className="panel stage-empty">
          <h3>План работ пока не составлен</h3>
          <p>
            {owner
              ? "Добавьте первый этап: укажите название, вид работ и при необходимости сроки."
              : "Заказчик добавит этапы. После этого здесь можно будет начать и завершить работы."}
          </p>
        </div>
      );
    }
    return (
      <ol className="stage-list">
        {stages.map((stage, index) => (
          <li
            className={
              "panel stage-row" +
              (guidance?.groups.some((g) =>
                g.points.some((p) => p.stageId === stage.id && p.canAct),
              )
                ? " stage-row-action"
                : "")
            }
            key={stage.id}
          >
            <span className="stage-number" aria-hidden="true">
              {stage.position.toString().padStart(2, "0")}
            </span>
            <div className="stage-content">
              <span className={`badge stage-status-${stage.status}`}>
                {statuses[stage.status]}
              </span>
              <h3>
                <a href={`#/projects/${project.id}/stages/${stage.id}`}>
                  {stage.name}
                </a>
              </h3>
              <p>
                {workTypes[stage.workType]} · План:{" "}
                {stageDate(stage.plannedStart)} —{" "}
                {stageDate(stage.plannedFinish)}
              </p>
              <StageTiming stage={stage} />
              {guidance?.groups
                .flatMap((g) => g.points)
                .filter((p) => p.stageId === stage.id && p.canAct && p.target)
                .map((p) => (
                  <a
                    key={p.id}
                    className="stage-required-action"
                    href={gateLink(p.target!)}
                  >
                    Требуется действие: {p.action?.label}
                  </a>
                ))}
            </div>
            {owner && (
              <div
                className="stage-order"
                aria-label={`Порядок этапа ${stage.name}`}
              >
                <button
                  type="button"
                  className="button secondary"
                  aria-label={`Поднять этап ${stage.name}`}
                  disabled={busy || index === 0}
                  onClick={() => move(index, -1)}
                >
                  ↑
                </button>
                <button
                  type="button"
                  className="button secondary"
                  aria-label={`Опустить этап ${stage.name}`}
                  disabled={busy || index === stages.length - 1}
                  onClick={() => move(index, 1)}
                >
                  ↓
                </button>
              </div>
            )}
          </li>
        ))}
      </ol>
    );
  }
  return (
    <section className="stages-section" aria-label="Этапы строительства">
      <div className="heading-row">
        <div>
          <p className="eyebrow">План и факт</p>
          <h2 ref={headingRef} tabIndex={-1}>
            Этапы строительства
          </h2>
          <p>Сроки работ и прогресс по каждому этапу.</p>
        </div>
        {owner && !adding && (
          <button
            type="button"
            className="button primary"
            onClick={() => {
              setAdding(true);
              setNotice("");
            }}
          >
            Добавить этап
          </button>
        )}
      </div>
      {notice && <Notice>{notice}</Notice>}
      {error && (
        <Notice error>
          {error}{" "}
          <button
            type="button"
            className="text-button"
            onClick={() => setAttempt(attempt + 1)}
          >
            Обновить этапы
          </button>
        </Notice>
      )}
      {adding && (
        <section className="panel">
          <StageForm
            projectId={project.id}
            onCancel={() => setAdding(false)}
            onSaved={() => {
              setAdding(false);
              setNotice("Этап добавлен в план");
              setAttempt(attempt + 1);
            }}
          />
        </section>
      )}
      {renderStages()}
    </section>
  );
}

export function StageDetail({
  projectId,
  stageId,
  account,
}: Readonly<{
  projectId: string;
  stageId: string;
  account: Account;
}>) {
  const [project, setProject] = useState<Project | null>(null);
  const [stage, setStage] = useState<Stage | null>(null);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  const [editing, setEditing] = useState(false);
  const [busy, setBusy] = useState(false);
  const [notice, setNotice] = useState("");
  useEffect(() => {
    let current = true;
    setError("");
    Promise.all([api.get(projectId), api.getStage(projectId, stageId)])
      .then(([home, data]) => {
        if (current) {
          setProject(home);
          setStage(data);
        }
      })
      .catch((error_) => {
        if (current) setError(errorMessage(error_));
      });
    return () => {
      current = false;
    };
  }, [projectId, stageId, attempt]);
  async function transition(complete: boolean) {
    if (!stage) return;
    setBusy(true);
    setError("");
    setNotice("");
    try {
      setStage(
        await (complete
          ? api.completeStage(projectId, stageId, stage.version)
          : api.startStage(projectId, stageId, stage.version)),
      );
      setNotice(
        complete
          ? "Завершение работ зафиксировано"
          : "Начало работ зафиксировано",
      );
    } catch (error_) {
      setError(errorMessage(error_));
    } finally {
      setBusy(false);
    }
  }
  function reload() {
    setEditing(false);
    setStage(null);
    setAttempt(attempt + 1);
  }
  function transitionLabel() {
    if (!stage) return "";
    if (busy) {
      return "Сохраняем…";
    }
    if (stage.status === "PLANNED") {
      return "Начать работы";
    }
    return "Завершить работы";
  }
  return (
    <>
      <a className="back-link" href={`#/projects/${projectId}`}>
        ← Карточка дома
      </a>
      {error && (
        <Notice error>
          {error}{" "}
          <button type="button" className="text-button" onClick={reload}>
            Обновить этап
          </button>
        </Notice>
      )}
      {!stage || !project ? (
        !error && <Notice>Загружаем этап…</Notice>
      ) : (
        <>
          <PageTitle
            key={stage.version}
            eyebrow={`${project.name} · ${workTypes[stage.workType]}`}
            description="Сравните плановые и фактические даты. Начало и завершение фиксирует назначенный подрядчик."
          >
            {stage.name}
          </PageTitle>
          {notice && <Notice>{notice}</Notice>}
          <div className="heading-row stage-heading">
            <span className={`badge stage-status-${stage.status}`}>
              {statuses[stage.status]}
            </span>
            {account.id === project.customerId &&
              !project.completedAt &&
              project.processMode !== "PROPOSAL_REQUIRED" &&
              !editing && (
                <button
                  type="button"
                  className="button secondary"
                  onClick={() => {
                    setEditing(true);
                    setNotice("");
                  }}
                >
                  Изменить план
                </button>
              )}
          </div>
          <StageTiming stage={stage} />
          {editing ? (
            <section className="panel">
              <StageForm
                key={stage.version}
                projectId={projectId}
                stage={stage}
                onCancel={() => setEditing(false)}
                onSaved={(data) => {
                  setStage(data);
                  setEditing(false);
                  setNotice("План этапа сохранён");
                }}
                onReload={reload}
              />
            </section>
          ) : (
            <div className="detail-grid stage-dates">
              <section className="panel">
                <h2>Плановые сроки</h2>
                <dl>
                  <div>
                    <dt>Начало</dt>
                    <dd>{stageDate(stage.plannedStart)}</dd>
                  </div>
                  <div>
                    <dt>Завершение</dt>
                    <dd>{stageDate(stage.plannedFinish)}</dd>
                  </div>
                </dl>
              </section>
              <section className="panel">
                <h2>Фактические даты</h2>
                <dl>
                  <div>
                    <dt>Начало</dt>
                    <dd>{stageDate(stage.actualStart)}</dd>
                  </div>
                  <div>
                    <dt>Завершение</dt>
                    <dd>{stageDate(stage.actualFinish)}</dd>
                  </div>
                </dl>
              </section>
            </div>
          )}
          {project.processMode === "PROPOSAL_REQUIRED" &&
            !project.approvedContractId && (
              <Notice>
                Сначала согласуйте договор подряда в карточке дома. Начало работ
                пока недоступно.
              </Notice>
            )}
          {(project.permissions?.canRecordWork ??
            account.id === project.contractorId) &&
            (project.processMode !== "PROPOSAL_REQUIRED" ||
              Boolean(project.approvedContractId)) &&
            stage.status !== "WORK_COMPLETED" && (
              <section className="panel stage-action">
                <h2>
                  {stage.status === "PLANNED"
                    ? "Готовы приступить?"
                    : "Работы по этапу закончены?"}
                </h2>
                <p>
                  Зафиксируем сегодняшнюю дату по московскому времени. Даты
                  факта после сохранения не редактируются.
                </p>
                <button
                  type="button"
                  className="button primary"
                  disabled={busy}
                  onClick={() => transition(stage.status === "IN_PROGRESS")}
                >
                  {transitionLabel()}
                </button>
              </section>
            )}
          <AcceptancePanel project={project} stage={stage} account={account} />
          <Journal project={project} stage={stage} account={account} />
          {stage.status === "WORK_COMPLETED" && (
            <p className="privacy-note">
              Завершение работ отмечено подрядчиком.
            </p>
          )}
        </>
      )}
    </>
  );
}

function stagePlanErrors(name: string, start: string, finish: string) {
  const fields: Record<string, string> = {};
  if (!name.trim()) fields.name = "Укажите название этапа";
  if (start && finish && finish < start)
    fields.finish = "Завершение не может быть раньше начала";
  return fields;
}
