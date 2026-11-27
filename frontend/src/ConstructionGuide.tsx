import type { components } from "./api/schema";
import { Notice, formatDateTime } from "./components";
import {
  gateLink,
  useGuidance,
  type ControlGates,
  type GatePoint,
  type GuidanceState,
} from "./guidance";

type Actions = components["schemas"]["ConstructionActionsView"];
type Profile = components["schemas"]["ConstructionProfile"];
const labels = {
  BLOCKED: "Нужно подготовиться",
  WAITING_CUSTOMER: "Ждём заказчика",
  WAITING_CONTRACTOR: "Ждём подрядчика",
  COMPLETED: "Действие выполнено",
};
function UpdateNotice<T>({ state }: Readonly<{ state: GuidanceState<T> }>) {
  if (state.error)
    return (
      <Notice error>
        Не удалось обновить сведения. {state.error}{" "}
        {state.data && "Показаны прежние данные."}{" "}
        <button type="button" className="text-button" onClick={state.reload}>
          Повторить обновление
        </button>
      </Notice>
    );
  if (state.refreshing)
    return (
      <output className="hint guidance-update">
        Обновляем сведения о строительстве…
      </output>
    );
  return null;
}
function Point({
  point,
  fresh,
  primary = false,
}: Readonly<{
  point: GatePoint;
  fresh: boolean;
  primary?: boolean;
}>) {
  return (
    <article
      className={`control-point ${primary ? "control-point-primary" : ""}`}
      aria-label={point.title}
    >
      <span className="badge">{labels[point.status]}</span>
      {primary ? <h3>{point.title}</h3> : <h4>{point.title}</h4>}
      <p>{point.detail}</p>
      {point.responsibleRole && (
        <p className="hint">
          Ответственная сторона:{" "}
          {point.responsibleRole === "CUSTOMER" ? "заказчик" : "подрядчик"}
        </p>
      )}
      {fresh && point.canAct && point.action && point.target ? (
        <a className="button primary" href={gateLink(point.target)}>
          {point.action.label}
        </a>
      ) : (
        point.target && <a href={gateLink(point.target)}>Посмотреть сведения</a>
      )}
    </article>
  );
}
export function ControlGatesPanel({
  state,
}: Readonly<{
  state: GuidanceState<ControlGates>;
}>) {
  const data = state.data;
  const primary = data?.groups
    .flatMap((g) => g.points)
    .find((p) => p.id === data.primaryPointId);
  const fresh = !state.error && !state.refreshing;
  return (
    <section
      className="panel construction-guide"
      aria-labelledby="control-gates-heading"
      aria-busy={state.refreshing}
    >
      <p className="eyebrow">Что сделать следующим</p>
      <h2 id="control-gates-heading">Контрольные точки</h2>
      <p className="hint">
        Подсказки помогают найти следующий шаг. Они не заменяют технический
        надзор и приёмку.
      </p>
      <UpdateNotice state={state} />
      {data?.completedAt && (
        <p className="notice">
          Строительство завершено · {formatDateTime(data.completedAt)}
        </p>
      )}
      {primary && <Point point={primary} fresh={fresh} primary />}
      {data && (
        <details className="control-groups">
          <summary>Все контрольные точки</summary>
          {data.groups.map((g) => (
            <section key={g.key} aria-label={g.title}>
              <h3>{g.title}</h3>
              {g.points.length === 0 ? (
                <p className="hint">В плане нет этапов этого вида.</p>
              ) : (
                g.points.map((p) => (
                  <Point key={p.id} point={p} fresh={fresh} />
                ))
              )}
            </section>
          ))}
        </details>
      )}
    </section>
  );
}
const sourceLabels = {
  REQUEST_DRAFT: "Черновик пожеланий заказчика",
  REQUEST_SUBMITTED: "Отправленная заявка",
  AGREED_PLAN: "Согласованный план",
  LEGACY_PLAN: "Действующий план",
  PROFILE: "Общие сведения дома",
};
export function ConstructionProfile({
  projectId,
}: Readonly<{ projectId: string }>) {
  const state = useGuidance<Profile>(
    `/projects/${projectId}/construction-profile`,
  );
  const profile = state.data;
  const date = (value: string | null) =>
    value
      ? new Intl.DateTimeFormat("ru-RU", { dateStyle: "long" }).format(
          new Date(value + "T12:00:00"),
        )
      : null;
  const entries = profile
    ? [
        [
          "Площадь",
          profile.areaM2 === null ? null : `${profile.areaM2} м²`,
          profile.sources.areaM2,
        ],
        ["Этажность", profile.floors, profile.sources.floors],
        ["Материал стен", profile.wallMaterial, profile.sources.wallMaterial],
        ["Фундамент", profile.foundationType, profile.sources.foundationType],
        ["Кровля", profile.roofType, profile.sources.roofType],
        [
          "Плановое завершение",
          date(profile.plannedFinish),
          profile.sources.plannedFinish,
        ],
        ...(profile.plannedFinish
          ? []
          : [
              [
                "Желаемое завершение",
                date(profile.desiredFinish),
                profile.sources.desiredFinish,
              ],
            ]),
      ]
    : [];
  const known = entries.some((e) => e[1] !== null);
  return (
    <section
      className="panel construction-profile"
      aria-labelledby="construction-profile-title"
      aria-busy={state.refreshing}
    >
      <h2 id="construction-profile-title">Паспорт дома</h2>
      <UpdateNotice state={state} />
      {profile &&
        (known ? (
          <>
            <dl>
              {entries.map(([label, value, source]) => (
                <div key={String(label)}>
                  <dt>{label}</dt>
                  <dd>
                    {value ?? "Не указано"}
                    {source && (
                      <span className="hint">
                        {sourceLabels[source as keyof typeof sourceLabels]}
                      </span>
                    )}
                  </dd>
                </div>
              ))}
            </dl>
            <p className="hint">
              Параметры из заявки и плана не являются подтверждением фактических
              характеристик дома.
            </p>
          </>
        ) : (
          <p className="hint">
            Характеристики дома пока не указаны. Известные параметры появятся из
            заявки и плана.
          </p>
        ))}
    </section>
  );
}
function objectsLabel(count: number) {
  const last = count % 10,
    hundred = count % 100;
  if (hundred >= 11 && hundred <= 14) return "объектов";
  if (last === 1) return "объект";
  return last >= 2 && last <= 4 ? "объекта" : "объектов";
}
export function ConstructionActionsIndicator() {
  const state = useGuidance<Actions>("/construction-actions");
  if (state.error)
    return (
      <output className="actions-unavailable">
        Не удалось обновить действия.{" "}
        <button type="button" className="text-button" onClick={state.reload}>
          Обновить действия
        </button>
      </output>
    );
  if (state.refreshing)
    return <output className="hint">Обновляем действия…</output>;
  const first = state.data?.projects[0],
    count = state.data?.projectCount ?? 0;
  if (!first || count === 0) return null;
  return (
    <a
      className="construction-actions-indicator"
      href={gateLink(first.target)}
      aria-label={`Требуется действие: ${count} ${objectsLabel(count)}. Открыть ${first.projectName}`}
    >
      <span aria-hidden="true">!</span> {count} {objectsLabel(count)}
    </a>
  );
}
