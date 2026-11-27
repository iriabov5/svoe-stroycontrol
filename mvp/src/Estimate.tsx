import { useState } from "react";
import { Link } from "react-router-dom";
import {
  Calculator,
  Download,
  Plus,
  ChevronDown,
  Check,
  Trash2,
  Wallet,
  TrendingUp,
  ShieldCheck,
  Bookmark,
  Layers3,
  House,
  Info,
  RotateCcw,
} from "lucide-react";
import { useApp } from "./store";
import {
  makeEstimate,
  templates,
  totals,
  overrun,
  money,
  number,
  payment,
  uid,
  download,
  estimateCSV,
  type EstimateStage,
} from "./domain";
import { PageHeading, Modal, Field, Notice } from "./ui";
export function Estimate() {
  const { data, update, requireAuth, notify, openLead } = useApp();
  const [expanded, setExpanded] = useState<string[]>(["foundation"]);
  const [format, setFormat] = useState("csv");
  const [choose, setChoose] = useState(false);
  const [credit, setCredit] = useState(false);
  const [creditYears, setCreditYears] = useState(3);
  const [creditRate, setCreditRate] = useState(20);
  const total = totals(data.estimate);
  const extra = overrun(data.estimate);
  const insured =
    total.actual > 2000000 ||
    data.estimate.some((s) => s.id === "walls" && s.isCompleted);
  const done = data.estimate.filter((s) => s.isCompleted).length;
  function selectTemplate(index: number) {
    const template = templates[index];
    update((d) => ({
      ...d,
      estimate: makeEstimate(template?.budget ?? 0),
      estimateName: template
        ? `${template.name} · ${template.area} м²`
        : "Моя смета",
      estimateArchives: d.estimate.length
        ? [
            ...d.estimateArchives,
            { id: uid(), name: d.estimateName, stages: d.estimate },
          ]
        : d.estimateArchives,
      insurance: { ...d.insurance, material: template?.material ?? "stone" },
    }));
    setChoose(false);
    setExpanded(["foundation"]);
  }
  function editStage(id: string, f: (s: EstimateStage) => EstimateStage) {
    update((d) => ({
      ...d,
      estimate: d.estimate.map((s) => (s.id === id ? f(s) : s)),
    }));
  }
  function exportEstimate() {
    requireAuth("Войдите, чтобы скачать смету", () => {
      download(
        `Смета-Своё-Село.${format}`,
        format === "csv"
          ? estimateCSV(data.estimate)
          : JSON.stringify(
              { name: data.estimateName, stages: data.estimate, totals: total },
              null,
              2,
            ),
        format === "csv" ? "text/csv;charset=utf-8" : "application/json",
      );
      notify("Смета скачана. Данные сохранены в профиле.");
    });
  }
  const picker = (
    <>
      <p className="muted">
        Выберите основу — мы распределим бюджет по этапам. Любую сумму и позицию
        можно изменить.
      </p>
      <div className="template-grid">
        {templates.map((t, i) => (
          <button
            key={t.id}
            className={`template-card template-${i}`}
            onClick={() => selectTemplate(i)}
          >
            <div className="template-art">
              <House size={70} strokeWidth={1} />
              <span className="badge neutral">{t.tag}</span>
              <span className="area">{t.area} м²</span>
            </div>
            <div className="template-body">
              <h3>{t.name}</h3>
              <p>{t.description}</p>
              <strong>≈ {money(t.budget)}</strong>
              <span className="text-link">Выбрать шаблон</span>
            </div>
          </button>
        ))}
      </div>
      <button className="blank-template" onClick={() => selectTemplate(-1)}>
        <div className="icon-tile">
          <Plus />
        </div>
        <span>
          <strong>Начать с чистого листа</strong>
          <small>Пустая смета — только ваши планы и цифры</small>
        </span>
        <Plus size={20} />
      </button>
      <p className="fine-print">
        Шаблоны содержат примерные бюджеты для демонстрации и не заменяют расчёт
        подрядчика.
      </p>
    </>
  );
  return (
    <>
      <PageHeading
        eyebrow="ПЛАНИРУЙТЕ С УВЕРЕННОСТЬЮ"
        title="Ваш дом. Ваш бюджет."
        description="От первого куба бетона до последнего штриха — всё под контролем."
        action={
          data.estimate.length > 0 ? (
            <button
              className="button secondary"
              onClick={() => setChoose(true)}
            >
              <Layers3 size={18} /> Другой шаблон
            </button>
          ) : undefined
        }
      />
      {!data.estimate.length ? (
        <section className="panel template-section">
          <div className="section-heading">
            <div>
              <h2>Какой дом вы планируете?</h2>
              <p>Шесть этапов стройки. Одна понятная смета.</p>
            </div>
            <span className="badge green">Можно без регистрации</span>
          </div>
          {picker}
        </section>
      ) : (
        <>
          <section className="budget-dashboard">
            <div className="metric">
              <span>
                <Wallet size={16} /> Плановый бюджет
              </span>
              <strong>{money(total.planned)}</strong>
            </div>
            <div className="metric">
              <span>
                <Calculator size={16} /> Потрачено · факт
              </span>
              <strong>{money(total.actual)}</strong>
            </div>
            <div
              className={`metric ${total.actual > total.planned ? "over-budget" : "remaining"}`}
            >
              <span>
                <TrendingUp size={16} />
                {total.actual > total.planned
                  ? "Перерасход"
                  : "Остаток бюджета"}
              </span>
              <strong>{money(Math.abs(total.planned - total.actual))}</strong>
            </div>
            <div className="budget-progress">
              <div
                className="progress-track"
                role="progressbar"
                aria-label="Освоение бюджета"
                aria-valuemin={0}
                aria-valuemax={100}
                aria-valuenow={Math.min(
                  100,
                  total.planned
                    ? Math.round((total.actual / total.planned) * 100)
                    : 0,
                )}
              >
                <div
                  style={{
                    width: `${Math.min(100, total.planned ? (total.actual / total.planned) * 100 : 0)}%`,
                  }}
                />
              </div>
              <span>
                {total.planned
                  ? Math.round((total.actual / total.planned) * 100)
                  : 0}
                % бюджета · {done} из 6 этапов завершено
              </span>
            </div>
          </section>
          <div className="estimate-layout">
            <div>
              <div className="estimate-toolbar">
                <div>
                  <input
                    className="title-input"
                    aria-label="Название сметы"
                    value={data.estimateName}
                    maxLength={100}
                    onChange={(e) =>
                      update((d) => ({ ...d, estimateName: e.target.value }))
                    }
                  />
                  <span className="autosave">
                    <Check size={13} /> Автоматически сохраняется в браузере
                  </span>
                </div>
                <button
                  className="text-button"
                  onClick={() =>
                    requireAuth("Войдите, чтобы сохранить смету", () =>
                      notify("Смета сохранена в вашем профиле"),
                    )
                  }
                >
                  <Bookmark size={17} /> В профиль
                </button>
              </div>
              <div className="stage-list">
                {data.estimate.map((s, index) => {
                  const t = totals([s]),
                    open = expanded.includes(s.id);
                  return (
                    <section
                      className={`stage ${s.isCompleted ? "completed" : ""}`}
                      key={s.id}
                    >
                      <button
                        className="stage-heading"
                        aria-expanded={open}
                        aria-controls={"stage-" + s.id}
                        onClick={() =>
                          setExpanded((v) =>
                            v.includes(s.id)
                              ? v.filter((x) => x !== s.id)
                              : [...v, s.id],
                          )
                        }
                      >
                        <div className="stage-number">
                          {s.isCompleted ? (
                            <Check size={20} />
                          ) : (
                            String(index + 1).padStart(2, "0")
                          )}
                        </div>
                        <div className="stage-name">
                          <h3>{s.title}</h3>
                          <span>
                            {s.isCompleted
                              ? "Этап завершён"
                              : `${s.items.length} позиции`}
                          </span>
                        </div>
                        <div className="stage-summary">
                          <small>План / Факт</small>
                          <span>
                            {number(t.planned)} <i>/</i>{" "}
                            <strong
                              className={
                                t.actual > t.planned ? "error-text" : ""
                              }
                            >
                              {money(t.actual)}
                            </strong>
                          </span>
                        </div>
                        <ChevronDown
                          className={open ? "rotated" : ""}
                          size={20}
                        />
                      </button>
                      {open && (
                        <div className="stage-body" id={"stage-" + s.id}>
                          <div className="item-headers">
                            <span>Работы и материалы</span>
                            <span>План, ₽</span>
                            <span>Факт, ₽</span>
                            <span />
                          </div>
                          {s.items.map((item, i) => (
                            <div className="estimate-item" key={item.id}>
                              <input
                                aria-label={`${s.title}: позиция ${i + 1}`}
                                value={item.name}
                                maxLength={120}
                                onChange={(e) =>
                                  editStage(s.id, (st) => ({
                                    ...st,
                                    items: st.items.map((x) =>
                                      x.id === item.id
                                        ? { ...x, name: e.target.value }
                                        : x,
                                    ),
                                  }))
                                }
                              />
                              {(["plannedAmount", "actualAmount"] as const).map(
                                (key, j) => (
                                  <label className="amount-field" key={key}>
                                    <span>{j ? "Факт, ₽" : "План, ₽"}</span>
                                    <input
                                      aria-label={`${s.title}: ${j ? "Факт" : "План"} ${i + 1}`}
                                      type="number"
                                      inputMode="decimal"
                                      min="0"
                                      max="100000000"
                                      value={item[key] || ""}
                                      placeholder="0"
                                      onChange={(e) => {
                                        const v = Math.max(
                                          0,
                                          Math.min(
                                            100000000,
                                            Number(e.target.value) || 0,
                                          ),
                                        );
                                        editStage(s.id, (st) => ({
                                          ...st,
                                          items: st.items.map((x) =>
                                            x.id === item.id
                                              ? { ...x, [key]: v }
                                              : x,
                                          ),
                                        }));
                                      }}
                                    />
                                  </label>
                                ),
                              )}
                              <button
                                className="icon-button delete-item"
                                aria-label={`Удалить позицию ${item.name}`}
                                onClick={() =>
                                  editStage(s.id, (st) => ({
                                    ...st,
                                    items: st.items.filter(
                                      (x) => x.id !== item.id,
                                    ),
                                  }))
                                }
                              >
                                <Trash2 size={17} />
                              </button>
                            </div>
                          ))}
                          <div className="stage-actions">
                            <button
                              className="text-button"
                              onClick={() =>
                                editStage(s.id, (st) => ({
                                  ...st,
                                  items: [
                                    ...st.items,
                                    {
                                      id: uid(),
                                      name: "Новая позиция",
                                      plannedAmount: 0,
                                      actualAmount: 0,
                                    },
                                  ],
                                }))
                              }
                            >
                              <Plus size={17} /> Добавить позицию
                            </button>
                            <label className="check-label">
                              <input
                                type="checkbox"
                                checked={s.isCompleted}
                                onChange={(e) =>
                                  editStage(s.id, (st) => ({
                                    ...st,
                                    isCompleted: e.target.checked,
                                  }))
                                }
                              />
                              {s.id === "walls"
                                ? "Коробка завершена"
                                : "Этап завершён"}
                            </label>
                          </div>
                        </div>
                      )}
                    </section>
                  );
                })}
              </div>
              <div className="export-bar">
                <div>
                  <h3>Вся смета — в одном файле</h3>
                  <p>Для себя, семьи или разговора с подрядчиком.</p>
                </div>
                <div className="export-actions">
                  <select
                    aria-label="Формат экспорта"
                    value={format}
                    onChange={(e) => setFormat(e.target.value)}
                  >
                    <option value="csv">CSV</option>
                    <option value="json">JSON</option>
                  </select>
                  <button className="button primary" onClick={exportEstimate}>
                    <Download size={18} /> Скачать смету
                  </button>
                </div>
              </div>
            </div>
            <aside className="estimate-aside">
              <div className="panel advice-card">
                <span className="icon-tile">
                  <Info />
                </span>
                <h3>
                  Большая стройка —<br />
                  маленькими шагами
                </h3>
                <p>
                  Вносите расходы по мере покупки материалов и оплаты работ. Так
                  вы заранее увидите, где меняется бюджет.
                </p>
                <div className="aside-divider" />
                <span className="muted">
                  Смета на основе этапов
                  <br />
                  «СВОЁ СтройКонтроль»
                </span>
              </div>
              {extra && (
                <div className="offer-card credit-offer">
                  <div className="icon-tile gold">
                    <TrendingUp />
                  </div>
                  <span className="badge orange">Бюджету нужно внимание</span>
                  <h3>Продолжайте строить</h3>
                  <p>
                    Расходы превысили план {extra.title} на{" "}
                    <strong>{money(extra.amount)}</strong>. Рассчитайте
                    потребительский кредит или добор на ИЖС.
                  </p>
                  <button
                    className="button secondary full"
                    onClick={() => setCredit(true)}
                  >
                    Рассчитать
                  </button>
                </div>
              )}
              {insured && (
                <div className="offer-card insurance-offer">
                  <ShieldCheck size={28} />
                  <h3>Защитите то, что уже построено</h3>
                  <p>
                    {total.actual > 2000000
                      ? "В стройку вложено более 2 млн ₽."
                      : "Коробка дома уже готова."}{" "}
                    Защитите коробку и материалы от пожара и кражи — полис от
                    РСХБ-Страхование.
                  </p>
                  <Link
                    className="button primary full"
                    to={`/insurance?type=construction&amount=${Math.max(500000, total.actual)}`}
                  >
                    Рассчитать страховку
                  </Link>
                </div>
              )}
              {data.estimateArchives.length > 0 && (
                <div className="panel">
                  <h3>Сохранённые сметы</h3>
                  <div className="archive-list">
                    {data.estimateArchives.map((a) => (
                      <button
                        className="text-button"
                        key={a.id}
                        onClick={() =>
                          update((d) => ({
                            ...d,
                            estimate: a.stages,
                            estimateName: a.name,
                            estimateArchives: [
                              ...d.estimateArchives.filter(
                                (x) => x.id !== a.id,
                              ),
                              {
                                id: uid(),
                                name: d.estimateName,
                                stages: d.estimate,
                              },
                            ],
                          }))
                        }
                      >
                        <RotateCcw size={16} />
                        {a.name}
                      </button>
                    ))}
                  </div>
                </div>
              )}
            </aside>
          </div>
        </>
      )}
      {choose && (
        <Modal
          title="Выберите новый шаблон"
          onClose={() => setChoose(false)}
          wide
        >
          <Notice>
            Текущая смета останется в разделе «Сохранённые сметы».
          </Notice>
          {picker}
        </Modal>
      )}
      {credit && (
        <Modal
          title="Рассчитайте дополнительный бюджет"
          onClose={() => setCredit(false)}
        >
          <p className="muted">
            Ориентировочная сумма перерасхода:{" "}
            <strong>{money(extra?.amount || 0)}</strong>
          </p>
          <Field label="Срок кредита">
            <strong>{creditYears} лет</strong>
            <input
              type="range"
              min="1"
              max="7"
              value={creditYears}
              onChange={(e) => setCreditYears(+e.target.value)}
            />
          </Field>
          <Field label="Ставка для расчёта, %">
            <input
              type="number"
              min="1"
              max="60"
              value={creditRate}
              onChange={(e) =>
                setCreditRate(Math.max(1, Math.min(60, +e.target.value)))
              }
            />
          </Field>
          <div className="calculated-total">
            <span>Платёж в месяц</span>
            <strong>
              {money(payment(extra?.amount || 0, creditRate, creditYears))}
            </strong>
          </div>
          <p className="fine-print">
            Условный расчёт без комиссий и дополнительных услуг. Ставку можно
            изменить; это не предложение банка.
          </p>
          <button
            className="button primary full"
            onClick={() => {
              setCredit(false);
              openLead("Дополнительный бюджет на строительство");
            }}
          >
            Оставить заявку
          </button>
        </Modal>
      )}
    </>
  );
}
