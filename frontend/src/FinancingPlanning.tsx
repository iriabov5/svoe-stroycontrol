import { useEffect, useRef, useState } from "react";
import { ApiError, errorMessage, request } from "./api/client";
import type { components } from "./api/schema";
import { Field, Notice } from "./components";

type Plan = components["schemas"]["FinancingPlanningView"];
type Calculation = components["schemas"]["ProjectMortgageCalculation"];
const examples = [
  ["Татарстан · 3%", "16:24:070801:412"],
  ["Приграничье · 0,1%", "31:15:1203001:88"],
  ["Московская область", "50:21:0040112:105"],
] as const;
const rub = (n: number, cents = false) =>
  new Intl.NumberFormat("ru-RU", {
    style: "currency",
    currency: "RUB",
    maximumFractionDigits: cents ? 2 : 0,
    minimumFractionDigits: cents ? 2 : 0,
  }).format(n);
const date = (s: string) => new Date(s).toLocaleString("ru-RU");
export function previewPayment(principal: number, rate: number, years: number) {
  const r = rate / 1200;
  return r === 0
    ? principal / (years * 12)
    : (principal * r) / (1 - (1 + r) ** (-years * 12));
}

type Props = {
  projectId: string;
  requestVersion: number;
  cadastralNumber: string | null | undefined;
  dirty: boolean;
  editable: boolean;
  onExample: (number: string) => void;
  onSaveDraft: () => Promise<boolean>;
  onFieldErrors: (fields: Record<string, string>) => void;
};
export function FinancingPlanning(props: Readonly<Props>) {
  const {
    projectId,
    requestVersion,
    cadastralNumber,
    dirty,
    editable,
    onExample,
    onSaveDraft,
    onFieldErrors,
  } = props;
  const [data, setData] = useState<Plan | null>(null);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [busy, setBusy] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const [savedNotice, setSavedNotice] = useState("");
  const [cost, setCost] = useState(5000000);
  const [deposit, setDeposit] = useState(20);
  const [years, setYears] = useState(20);
  const [joint, setJoint] = useState(false);
  const pending = useRef<{ signature: string; key: string } | null>(null);
  const failed = useRef<"plot-checks" | "calculations" | null>(null);
  const active = useRef(true);
  useEffect(() => {
    active.current = true;
    return () => {
      active.current = false;
    };
  }, []);
  useEffect(() => {
    let current = true;
    request<Plan>(`/projects/${projectId}/financing-planning`)
      .then((value) => {
        if (current) {
          setData(value);
          if (!failed.current) setError("");
        }
      })
      .catch((e) => {
        if (current) setError(errorMessage(e));
      });
    return () => {
      current = false;
    };
  }, [projectId, requestVersion, attempt]);
  const calculation = data?.calculation;
  useEffect(() => {
    if (!calculation) return;
    setCost(calculation.cost);
    setDeposit(calculation.depositPercent);
    setYears(calculation.years);
    setJoint(calculation.joint);
  }, [calculation?.id]);

  async function command(kind: "plot-checks" | "calculations") {
    if (!data) return;
    if (kind === "plot-checks" && !validCadastre(cadastralNumber)) {
      const message =
        "Введите номер в формате 16:24:070801:412 (6 или 7 цифр в квартале)";
      onFieldErrors({ cadastralNumber: message });
      setError(message);
      document.getElementById("cadastralNumber")?.focus();
      return;
    }
    failed.current = kind;
    setBusy(true);
    setError("");
    setFields({});
    setSavedNotice("");
    const body = {
      requestVersion,
      planningVersion: data.version,
      ...(kind === "calculations"
        ? {
            plotCheckId: data.plotCheck?.id,
            cost,
            depositPercent: deposit,
            years,
            joint,
          }
        : {}),
    };
    const signature = JSON.stringify([projectId, kind, body]);
    if (pending.current?.signature !== signature)
      pending.current = { signature, key: crypto.randomUUID() };
    try {
      const value = await request<Plan>(
        `/projects/${projectId}/financing-planning/${kind}`,
        "POST",
        { ...body, idempotencyKey: pending.current.key },
      );
      if (!active.current) return;
      setData(value);
      pending.current = null;
      failed.current = null;
      setSavedNotice(
        kind === "calculations"
          ? "Предварительный расчёт сохранён"
          : "Проверка участка сохранена",
      );
    } catch (e) {
      if (!active.current) return;
      setError(errorMessage(e));
      showCommandError(e);
    } finally {
      if (active.current) setBusy(false);
    }
  }

  function showCommandError(e: unknown) {
    if (!(e instanceof ApiError)) return;
    setFields(e.fieldErrors);
    onFieldErrors(e.fieldErrors);
    if (e.status === 409) {
      pending.current = null;
      setAttempt((v) => v + 1);
    }
  }

  const check = data?.plotCheck;
  const currentNumber = cadastralNumber?.trim();
  const checkCurrent =
    !!check &&
    data?.plotCheckCurrent &&
    check.cadastralNumber === currentNumber;
  const canEdit = editable && !!data?.editable;
  const canCalculate = checkCurrent && check.status === "ELIGIBLE";
  const rate = check?.rate ?? 0;
  const principal = cost * (1 - deposit / 100);
  const limit = joint ? 12000000 : 6000000;
  const matchesSaved =
    calculationMatches(calculation, cost, deposit, years, joint) &&
    data?.calculationCurrent &&
    checkCurrent;
  return (
    <section
      className="panel financing-planning"
      aria-labelledby="financing-title"
      aria-busy={busy}
    >
      <div className="section-heading">
        <div>
          <p className="eyebrow">До начала строительства</p>
          <h2 id="financing-title" tabIndex={-1}>
            Участок и ипотека
          </h2>
        </div>
        <span className="badge">Демонстрационный режим</span>
      </div>
      <p className="financing-intro">
        Проверьте участок своей карточки и оцените ежемесячный платёж.
        Результаты видны только вам.
      </p>
      <p className="hint">
        Используем синтетические сведения. Проверка не является заключением об
        участке, а расчёт — решением банка.
      </p>
      {error && (
        <Notice error>
          {error}{" "}
          <button
            type="button"
            className="text-button"
            onClick={() =>
              failed.current
                ? void command(failed.current)
                : setAttempt((v) => v + 1)
            }
            disabled={busy || (!!data && (!canEdit || dirty))}
          >
            Повторить действие
          </button>
        </Notice>
      )}
      {!data && !error && <Notice>Загружаем план участка и ипотеки…</Notice>}
      {savedNotice && <Notice>{savedNotice}</Notice>}
      {!canEdit && data && (
        <Notice>
          Планирование доступно только для чтения. Новые проверки и расчёты
          выполняются в открытом черновике заявки.
        </Notice>
      )}
      <div className="plot-check-start">
        <div>
          <span className="hint">Номер из заявки на строительство</span>
          <p className="plot-number">
            {cadastralNumber || "Кадастровый номер пока не указан"}
          </p>
        </div>
        {canEdit && (
          <button
            type="button"
            className="button primary"
            disabled={busy || dirty}
            onClick={() => void command("plot-checks")}
          >
            {busy ? "Сохраняем…" : "Проверить участок"}
          </button>
        )}
      </div>
      {canEdit && dirty && (
        <Notice>
          В заявке есть несохранённые изменения. Сохраните черновик перед
          проверкой или расчётом.{" "}
          <button
            type="button"
            className="text-button"
            disabled={busy}
            onClick={async () => {
              setBusy(true);
              try {
                await onSaveDraft();
              } finally {
                if (active.current) setBusy(false);
              }
            }}
          >
            Сохранить черновик для проверки
          </button>
        </Notice>
      )}
      {canEdit && (
        <div className="plot-examples">
          <span className="hint">
            Тестовые примеры — заменяют номер в форме, но не адрес карточки:
          </span>
          <div className="form-actions">
            {examples.map(([label, number]) => (
              <button
                type="button"
                className="button secondary"
                disabled={busy}
                key={number}
                onClick={() => {
                  onExample(number);
                  setSavedNotice("");
                  setError("");
                }}
              >
                {label}
              </button>
            ))}
          </div>
        </div>
      )}
      {check && (
        <section
          className={`plot-result ${check.status === "ELIGIBLE" ? "plot-eligible" : ""}`}
          aria-label="Результат проверки участка"
        >
          <div className="section-heading">
            <h3>{plotStatusLabel(check.status)}</h3>
            {check.rate !== null && (
              <span className="badge">Демоставка {check.rate}%</span>
            )}
          </div>
          <p>
            {check.address ??
              "Для этого номера нет синтетических сведений. Это не означает, что участок не подходит."}
          </p>
          <dl className="financing-details">
            <div>
              <dt>Кадастровый номер</dt>
              <dd>{check.cadastralNumber}</dd>
            </div>
            {check.landCategory && (
              <div>
                <dt>Категория земель</dt>
                <dd>{check.landCategory}</dd>
              </div>
            )}
            {check.permittedUse && (
              <div>
                <dt>Использование</dt>
                <dd>{check.permittedUse}</dd>
              </div>
            )}
          </dl>
          {check.reason && check.status === "INELIGIBLE" && (
            <Notice>
              {check.reason} Можно продолжить строительную заявку без этой
              проверки.
            </Notice>
          )}
          <p className="hint">Демоданные · Проверено {date(check.checkedAt)}</p>
          {!checkCurrent && (
            <Notice>
              Этот результат относится к прежнему номеру участка. Сохраните
              текущий номер и повторите проверку.
            </Notice>
          )}
        </section>
      )}
      {canCalculate && canEdit && (
        <section
          className="mortgage-calculator"
          aria-labelledby="calculator-title"
        >
          <div>
            <p className="eyebrow">Предварительная оценка</p>
            <h3 id="calculator-title">Рассчитайте платёж</h3>
            <p>
              Стоимость включает дом и участок. Она не заменяет желаемый бюджет
              стройки.
            </p>
          </div>
          <div className="calculator-layout">
            <div className="calculator-fields">
              <Field
                id="mortgage-cost"
                label="Стоимость дома и участка, ₽"
                type="number"
                min={1000000}
                max={15000000}
                step={100000}
                value={cost}
                error={
                  fields.cost ||
                  (cost < 1000000 || cost > 15000000 || cost % 100000 !== 0
                    ? "Стоимость — от 1 до 15 млн ₽ с шагом 100 000 ₽"
                    : undefined)
                }
                onChange={(e) => {
                  setCost(Number(e.target.value));
                  setSavedNotice("");
                }}
              />
              <input
                aria-label="Стоимость дома и участка — ползунок"
                type="range"
                min={1000000}
                max={15000000}
                step={100000}
                value={cost}
                onChange={(e) => {
                  setCost(Number(e.target.value));
                  setSavedNotice("");
                }}
              />
              <Field
                id="mortgage-deposit"
                label="Первоначальный взнос, %"
                type="number"
                min={20}
                max={80}
                step={5}
                value={deposit}
                error={
                  fields.depositPercent ||
                  (!Number.isInteger(deposit) ||
                  deposit < 20 ||
                  deposit > 80 ||
                  deposit % 5 !== 0
                    ? "Взнос — от 20 до 80% с шагом 5%"
                    : undefined)
                }
                onChange={(e) => {
                  setDeposit(Number(e.target.value));
                  setSavedNotice("");
                }}
              />
              <input
                aria-label="Первоначальный взнос — ползунок"
                type="range"
                min={20}
                max={80}
                step={5}
                value={deposit}
                onChange={(e) => {
                  setDeposit(Number(e.target.value));
                  setSavedNotice("");
                }}
              />
              <Field
                id="mortgage-years"
                label="Срок кредита, лет"
                type="number"
                min={1}
                max={25}
                step={1}
                value={years}
                error={
                  fields.years ||
                  (!Number.isInteger(years) || years < 1 || years > 25
                    ? "Срок — от 1 до 25 целых лет"
                    : undefined)
                }
                onChange={(e) => {
                  setYears(Number(e.target.value));
                  setSavedNotice("");
                }}
              />
              <input
                aria-label="Срок кредита — ползунок"
                type="range"
                min={1}
                max={25}
                step={1}
                value={years}
                onChange={(e) => {
                  setYears(Number(e.target.value));
                  setSavedNotice("");
                }}
              />
              <label className="check-label">
                <input
                  type="checkbox"
                  checked={joint}
                  onChange={(e) => {
                    setJoint(e.target.checked);
                    setSavedNotice("");
                  }}
                />{" "}
                Совместное оформление · демолимит до 12 млн ₽
              </label>
            </div>
            <div className="payment-panel">
              <span className="badge">Демоставка {rate}%</span>
              <p>Ежемесячный платёж</p>
              <strong className="payment-value">
                {validInputs(cost, deposit, years)
                  ? rub(previewPayment(principal, rate, years), true)
                  : "Проверьте параметры"}
              </strong>
              <dl className="financing-details">
                <div>
                  <dt>Первоначальный взнос</dt>
                  <dd>{rub((cost * deposit) / 100)}</dd>
                </div>
                <div>
                  <dt>Сумма кредита</dt>
                  <dd>{rub(principal)}</dd>
                </div>
                <div>
                  <dt>Демонстрационный лимит</dt>
                  <dd>{rub(limit)}</dd>
                </div>
              </dl>
              <p className="hint">
                {matchesSaved
                  ? "Показан сохранённый расчёт"
                  : "Предварительный просмотр · не сохранён"}
              </p>
              {principal > limit && (
                <Notice>
                  Сумма кредита превышает демонстрационный лимит на{" "}
                  {rub(principal - limit)}. Уменьшите стоимость или увеличьте
                  взнос.
                </Notice>
              )}
              <button
                type="button"
                className="button primary"
                disabled={busy || dirty || !validInputs(cost, deposit, years)}
                onClick={() => void command("calculations")}
              >
                Сохранить расчёт
              </button>
            </div>
          </div>
          <p className="hint">
            Предварительный деморасчёт. Ставки и лимиты — условия тестовых
            примеров, не предложение банка. Сохранение не оформляет кредит и не
            меняет заявку на строительство.
          </p>
        </section>
      )}
      {calculation && (
        <SavedCalculation
          calculation={calculation}
          current={!!data?.calculationCurrent && !!checkCurrent}
        />
      )}
    </section>
  );
}
function validInputs(cost: number, deposit: number, years: number) {
  return (
    Number.isFinite(cost) &&
    cost >= 1000000 &&
    cost <= 15000000 &&
    cost % 100000 === 0 &&
    Number.isInteger(deposit) &&
    deposit >= 20 &&
    deposit <= 80 &&
    deposit % 5 === 0 &&
    Number.isInteger(years) &&
    years >= 1 &&
    years <= 25
  );
}
function SavedCalculation({
  calculation: c,
  current,
}: Readonly<{ calculation: Calculation; current: boolean }>) {
  return (
    <section
      className="saved-calculation"
      aria-label="Сохранённый предварительный расчёт"
    >
      <h3>Сохранённый предварительный расчёт</h3>
      <p className="hint">
        Демоданные · Сохранено {date(c.calculatedAt)} · {c.termsVersion}
      </p>
      {!current && (
        <Notice>
          Расчёт сохранён ранее и не относится к текущей проверке или условиям.
          Для актуального результата повторите проверку и расчёт.
        </Notice>
      )}
      <dl className="financing-details">
        <div>
          <dt>Стоимость дома и участка</dt>
          <dd>{rub(c.cost)}</dd>
        </div>
        <div>
          <dt>Первоначальный взнос</dt>
          <dd>
            {c.depositPercent}% · {rub(c.depositAmount)}
          </dd>
        </div>
        <div>
          <dt>Сумма кредита</dt>
          <dd>{rub(c.principal)}</dd>
        </div>
        <div>
          <dt>Ставка и срок</dt>
          <dd>
            {c.rate}% · {c.years} лет
          </dd>
        </div>
        <div>
          <dt>Ежемесячный платёж</dt>
          <dd>{rub(c.monthlyPayment, true)}</dd>
        </div>
        <div>
          <dt>Демонстрационный лимит</dt>
          <dd>
            {rub(c.creditLimit)} ·{" "}
            {c.joint ? "совместное оформление" : "один заёмщик"}
          </dd>
        </div>
      </dl>
      {!c.withinLimit && (
        <Notice>Сохранённый расчёт превышает демонстрационный лимит.</Notice>
      )}
      <p className="hint">
        Это предварительная оценка, а не одобрение или выдача кредита.
      </p>
    </section>
  );
}

function validCadastre(value: string | null | undefined) {
  return /^\d{2}:\d{2}:\d{6,7}:\d{1,7}$/.test(value?.trim() ?? "");
}
function calculationMatches(
  c: Calculation | null | undefined,
  cost: number,
  deposit: number,
  years: number,
  joint: boolean,
) {
  return (
    !!c &&
    c.cost === cost &&
    c.depositPercent === deposit &&
    c.years === years &&
    c.joint === joint
  );
}
function plotStatusLabel(
  status: components["schemas"]["ProjectPlotCheck"]["status"],
) {
  switch (status) {
    case "ELIGIBLE":
      return "Подходит по демопроверке";
    case "INELIGIBLE":
      return "Не подходит по демопроверке";
    case "NOT_FOUND":
      return "Участок отсутствует в демобазе";
  }
}
