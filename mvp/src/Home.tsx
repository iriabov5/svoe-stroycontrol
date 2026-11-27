import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import {
  MapPin,
  Search,
  ShieldCheck,
  Calculator,
  CalendarDays,
  Check,
  Leaf,
  CircleHelp,
  LocateFixed,
  History,
  X,
  BadgeCheck,
} from "lucide-react";
import { useApp } from "./store";
import {
  money,
  number,
  maskCadastre,
  validCadastre,
  plotExamples,
  payment,
  type PlotCheckResult,
} from "./domain";
import { Field, Notice, Spinner } from "./ui";
export function Home() {
  const { data, update, openLead } = useApp();
  const [value, setValue] = useState("");
  const [result, setResult] = useState<PlotCheckResult | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const timeout = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);
  const output = useRef<HTMLDivElement>(null);
  useEffect(() => () => clearTimeout(timeout.current), []);
  function check(cadastre = value) {
    if (!validCadastre(cadastre)) {
      setError(
        "Введите номер в формате 16:24:070801:412. Кадастровый квартал может содержать 6 или 7 цифр.",
      );
      return;
    }
    setValue(cadastre);
    setError("");
    setBusy(true);
    setResult(null);
    clearTimeout(timeout.current);
    timeout.current = setTimeout(() => {
      const found = plotExamples.find((x) => x.cadastralNumber === cadastre);
      setBusy(false);
      if (!found) {
        setError(
          "В демонстрационной базе такого участка нет. Выберите один из трёх примеров ниже.",
        );
        return;
      }
      setResult(found);
      update((d) => ({
        ...d,
        plotChecks: [
          found,
          ...d.plotChecks.filter(
            (x) => x.cadastralNumber !== found.cadastralNumber,
          ),
        ].slice(0, 30),
      }));
      requestAnimationFrame(() =>
        output.current?.scrollIntoView({ behavior: "smooth", block: "start" }),
      );
    }, 800);
  }
  return (
    <>
      <section className="hero">
        <div className="hero-copy">
          <div className="eyebrow">
            <span className="tiny-leaf">
              <Leaf size={15} />
            </span>
            СВОЙ ДОМ. СВОЯ ИСТОРИЯ.
          </div>
          <h1>
            Ближе к природе.
            <br />
            <span>Ближе к своему дому.</span>
          </h1>
          <p className="hero-description">
            Найдите подходящий участок, спланируйте стройку
            <br className="desktop-break" /> и сохраните спокойствие на каждом
            этапе.
          </p>
          <div className="hero-proof">
            <span>
              <Check size={16} /> Без карты банка
            </span>
            <span>
              <Check size={16} /> Бесплатные инструменты
            </span>
          </div>
          <form
            className="search-box"
            onSubmit={(e) => {
              e.preventDefault();
              check();
            }}
          >
            <label htmlFor="cadastre">
              Подходит ли ваш участок для сельской ипотеки?
            </label>
            <div className="search-row">
              <div className="input-icon">
                <MapPin size={21} />
                <input
                  id="cadastre"
                  value={value}
                  onChange={(e) => {
                    setValue(maskCadastre(e.target.value));
                    setError("");
                  }}
                  placeholder="Кадастровый номер участка"
                  inputMode="numeric"
                  aria-describedby="cadastre-hint"
                  aria-invalid={!!error}
                  disabled={busy}
                />
              </div>
              <button className="button primary" disabled={busy}>
                {busy ? <Spinner /> : <Search size={18} />}
                <span>{busy ? "Проверяем…" : "Проверить участок"}</span>
              </button>
            </div>
            <small id="cadastre-hint">
              Номер можно найти в выписке ЕГРН или на кадастровой карте.
            </small>
            {error && (
              <p className="error-text" role="alert">
                {error}
              </p>
            )}
          </form>
          <div className="sample-label">Попробуйте на примере</div>
          <div className="sample-chips">
            {plotExamples.map((p, i) => (
              <button
                key={p.cadastralNumber}
                onClick={() => check(p.cadastralNumber)}
                disabled={busy}
                title={p.cadastralNumber}
              >
                <span className={`sample-dot dot-${i}`} />
                {
                  [
                    "Московская область",
                    "Татарстан · 3%",
                    "Приграничье · 0,1%",
                  ][i]
                }
              </button>
            ))}
          </div>
        </div>
        <div className="hero-photo">
          <img
            src="/images/country-house.jpg"
            alt="Современный загородный дом с панорамными окнами среди зелени"
            fetchPriority="high"
          />
          <div className="photo-shade" />
          <div className="photo-label">
            <MapPin size={15} /> Место, где хочется жить
          </div>
          <div className="rate-card">
            <div className="rate-icon">
              <Leaf size={24} />
            </div>
            <div>
              <span>Сельская ипотека</span>
              <strong>
                от 0,1<span>%</span>
              </strong>
              <small>Ваш новый горизонт</small>
            </div>
            <span className="rate-demo">Демо</span>
          </div>
          <div className="photo-caption">
            Большие планы
            <br />
            начинаются с участка.
          </div>
        </div>
      </section>
      {result && (
        <div className="result-section" ref={output} tabIndex={-1}>
          <div
            className={`result-card ${result.isEligible ? "eligible" : "ineligible"}`}
          >
            <div className="result-title">
              <div className={`icon-tile ${result.isEligible ? "" : "red"}`}>
                {result.isEligible ? <BadgeCheck /> : <X />}
              </div>
              <div>
                <span
                  className={`badge ${result.isEligible ? "green" : "red"}`}
                >
                  {result.isEligible
                    ? "Подходит по демопроверке"
                    : "Не подходит по демопроверке"}
                </span>
                <h2>
                  {result.isEligible
                    ? "У вашего дома есть перспектива"
                    : "Рассмотрим другие возможности"}
                </h2>
              </div>
              {result.rate !== null && (
                <div className="result-rate">
                  {String(result.rate).replace(".", ",")}
                  <span>%</span>
                </div>
              )}
            </div>
            <p className="address">
              <MapPin size={17} />
              {result.address}
            </p>
            <dl className="plot-details">
              <div>
                <dt>Кадастровый номер</dt>
                <dd>{result.cadastralNumber}</dd>
              </div>
              <div>
                <dt>Категория земель</dt>
                <dd>{result.landCategory}</dd>
              </div>
              <div>
                <dt>Использование</dt>
                <dd>{result.permittedUse}</dd>
              </div>
            </dl>
            {!result.isEligible && (
              <>
                <Notice tone="warning">{result.rejectionReason}</Notice>
                <div className="alternative">
                  <div>
                    <h3>Семейная ипотека от 6% или базовая ИЖС</h3>
                    <p>
                      Сохраните заявку, чтобы сравнить альтернативные варианты в
                      демо.
                    </p>
                  </div>
                  <button
                    className="button secondary"
                    onClick={() =>
                      openLead("Заявка: семейная ипотека 6% или базовая ИЖС")
                    }
                  >
                    Рассмотреть варианты
                  </button>
                </div>
              </>
            )}
            <p className="fine-print">
              Результат основан на тестовых данных и не является решением банка
              или заключением о земельном участке.
            </p>
          </div>
          {result.isEligible && <MortgageCalculator result={result} />}
        </div>
      )}
      <section className="tools-section">
        <div className="section-heading">
          <div>
            <div className="eyebrow">ОТ ПЕРВОЙ ИДЕИ ДО НОВОСЕЛЬЯ</div>
            <h2>Всё для вашего дома — в одном месте</h2>
          </div>
          <p>
            Поможем разобраться в деталях,
            <br />
            чтобы вы могли думать о главном.
          </p>
        </div>
        <div className="tool-grid">
          <Link className="tool-card" to="/estimate">
            <div className="tool-top">
              <div className="icon-tile">
                <Calculator />
              </div>
              <span className="mini-tag">Планируйте</span>
            </div>
            <h3>Стройка без сюрпризов</h3>
            <p>
              Соберите смету, следите за расходами и держите бюджет под
              контролем.
            </p>
            <span className="tool-link">Составить смету</span>
            <div className="mini-budget">
              <div>
                <span>Ваш план</span>
                <strong>4 500 000 ₽</strong>
              </div>
              <div className="mini-bars">
                <i />
                <i />
                <i />
                <i />
                <i />
                <i />
              </div>
            </div>
          </Link>
          <Link className="tool-card" to="/mortgage-care">
            <div className="tool-top">
              <div className="icon-tile gold">
                <CalendarDays />
              </div>
              <span className="mini-tag">Не упускайте</span>
            </div>
            <h3>Спокойствие по календарю</h3>
            <p>
              Важные даты, документы и напоминания — для сохранения вашей
              ставки.
            </p>
            <span className="tool-link">Открыть «Анти-штраф»</span>
            <div className="mini-calendar">
              <span>
                <ShieldCheck size={19} /> Ваша ставка под контролем
              </span>
              <strong>3%</strong>
            </div>
          </Link>
          <Link className="tool-card" to="/insurance">
            <div className="tool-top">
              <div className="icon-tile sage">
                <ShieldCheck />
              </div>
              <span className="mini-tag">Защищайте</span>
            </div>
            <h3>Защита того, что важно</h3>
            <p>
              Рассчитайте страховку для ипотеки или дома, который ещё строится.
            </p>
            <span className="tool-link">Подобрать страховку</span>
            <div className="mini-insurance">
              <ShieldCheck size={29} />
              <span>
                Дом, вложения
                <br />
                <strong>и уверенность в завтра</strong>
              </span>
            </div>
          </Link>
        </div>
      </section>
      {data.plotChecks.length > 0 && (
        <section className="history-section">
          <h3>
            <History size={20} /> Недавние проверки
          </h3>
          <div className="history-list">
            {data.plotChecks.slice(0, 3).map((p) => (
              <button
                key={p.cadastralNumber}
                onClick={() => check(p.cadastralNumber)}
                disabled={busy}
              >
                <MapPin size={18} />
                <span>
                  {p.cadastralNumber}
                  <small>{p.region}</small>
                </span>
                <span className={`badge ${p.isEligible ? "green" : "red"}`}>
                  {p.isEligible
                    ? `${String(p.rate).replace(".", ",")}%`
                    : "Не подходит"}
                </span>
              </button>
            ))}
          </div>
        </section>
      )}
      <section className="trust-strip">
        <div className="icon-tile">
          <LocateFixed />
        </div>
        <div>
          <h3>Выбирайте свой путь. Мы поможем с остальным.</h3>
          <p>
            Нашими инструментами может пользоваться каждый — даже без карты или
            кредита банка.
          </p>
        </div>
        <span className="badge neutral">
          <CircleHelp size={15} /> Открытый сервис
        </span>
      </section>
    </>
  );
}
function MortgageCalculator({ result }: { result: PlotCheckResult }) {
  const { openLead } = useApp();
  const [cost, setCost] = useState(5000000);
  const [deposit, setDeposit] = useState(20);
  const [years, setYears] = useState(20);
  const [couple, setCouple] = useState(false);
  const principal = cost * (1 - deposit / 100);
  const limit = couple ? 12000000 : 6000000;
  const valid = principal <= limit;
  const rate = result.rate ?? 3;
  return (
    <div className="calculator-card">
      <div>
        <span className="eyebrow">СЛЕДУЮЩИЙ ШАГ</span>
        <h2>Прикинем платёж?</h2>
        <p className="muted">
          Посмотрите, как мечта о доме вписывается в ваш бюджет.
        </p>
        <div className="range-grid">
          <Field label="Стоимость дома и участка">
            <strong className="range-value">{money(cost)}</strong>
            <input
              type="range"
              min="1000000"
              max="15000000"
              step="100000"
              value={cost}
              onChange={(e) => setCost(+e.target.value)}
            />
            <span className="range-ends">
              <span>1 млн ₽</span>
              <span>15 млн ₽</span>
            </span>
          </Field>
          <Field label="Первоначальный взнос">
            <strong className="range-value">
              {deposit}% <small>· {money((cost * deposit) / 100)}</small>
            </strong>
            <input
              type="range"
              min="20"
              max="80"
              step="5"
              value={deposit}
              onChange={(e) => setDeposit(+e.target.value)}
            />
          </Field>
          <Field label="Срок кредита">
            <strong className="range-value">{years} лет</strong>
            <input
              type="range"
              min="1"
              max="25"
              value={years}
              onChange={(e) => setYears(+e.target.value)}
            />
          </Field>
        </div>
        <label className="check-label">
          <input
            type="checkbox"
            checked={couple}
            onChange={(e) => setCouple(e.target.checked)}
          />{" "}
          Оформляем вместе с супругом — до 12 млн ₽
        </label>
        {!valid && (
          <Notice tone="warning">
            Сумма кредита {money(principal)} превышает лимит {money(limit)}.
            Увеличьте взнос или уменьшите стоимость дома.
          </Notice>
        )}
      </div>
      <div className="payment-panel">
        <span className="badge gold">
          Ставка {String(rate).replace(".", ",")}%
        </span>
        <p>Ежемесячный платёж</p>
        <strong className="payment-value">
          {number(payment(principal, rate, years))} <span>₽</span>
        </strong>
        <small>Сумма кредита: {money(principal)}</small>
        <button
          className="button primary full"
          disabled={!valid}
          onClick={() =>
            openLead(`Подать заявку на ${String(rate).replace(".", ",")}%`)
          }
        >
          Подать заявку на {String(rate).replace(".", ",")}%
        </button>
        <button
          className="text-button"
          disabled={!valid}
          onClick={() => openLead("Лист ожидания лимитов сельской ипотеки")}
        >
          Встать в лист ожидания лимитов
        </button>
        <p className="fine-print">
          Предварительный деморасчёт. Не является офертой. Доступность программы
          и условия уточняются в банке.
        </p>
      </div>
    </div>
  );
}
