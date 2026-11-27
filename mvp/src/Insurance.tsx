import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import {
  ShieldCheck,
  House,
  Heart,
  Check,
  Flame,
  FileCheck2,
  LockKeyhole,
  CalendarCheck2,
} from "lucide-react";
import { useApp } from "./store";
import {
  money,
  today,
  addMonths,
  uid,
  insurancePrice,
  maskPhone,
  validPhone,
  applyPolicy,
  type AppData,
  type Policy,
} from "./domain";
import { PageHeading, Field, Modal, Notice, Spinner } from "./ui";
export function Insurance() {
  const { data, update, requireAuth, notify } = useApp();
  const [params, setParams] = useSearchParams();
  const kind =
    params.get("type") === "construction" ? "construction" : "mortgage";
  const obligation = params.get("obligation") ?? undefined;
  const amountParam = params.get("amount");
  const prefs = data.insurance;
  const [checkout, setCheckout] = useState<{
    name: string;
    phone: string;
    email: string;
  } | null>(null);
  const [paid, setPaid] = useState<Policy | null>(null);
  const [busy, setBusy] = useState(false);
  const operation = useRef(0);
  useEffect(
    () => () => {
      operation.current++;
    },
    [],
  );
  useEffect(() => {
    if (amountParam && Number.isFinite(+amountParam))
      update((d) => ({
        ...d,
        insurance: {
          ...d.insurance,
          amount: Math.max(500000, Math.min(12000000, +amountParam)),
        },
        insuranceTouched: true,
      }));
  }, [amountParam]);
  const price = insurancePrice(prefs.amount, prefs.material, prefs.life, kind);
  function change(patch: Partial<AppData["insurance"]>) {
    update((d) => ({
      ...d,
      insurance: { ...d.insurance, ...patch },
      insuranceTouched: true,
    }));
  }
  const validAge = prefs.age >= 18 && prefs.age <= 85;
  async function buy() {
    if (!checkout || !validPhone(checkout.phone) || busy) return;
    setBusy(true);
    const token = ++operation.current;
    await new Promise((r) => setTimeout(r, 700));
    if (operation.current !== token) return;
    const policy: Policy = {
      id: uid(),
      kind,
      premium: price.total,
      insuredAmount: prefs.amount,
      createdAt: today(),
      expiresAt: addMonths(today(), 12),
      obligationId: obligation,
    };
    const saved = update((d) => applyPolicy(d, policy));
    setBusy(false);
    if (saved) {
      setCheckout(null);
      setPaid(policy);
      notify("Демополис оформлен. Данные сохранены в профиле.");
    }
  }
  return (
    <>
      <PageHeading
        eyebrow="ЗАЩИТА ДОМА И ВАШИХ ПЛАНОВ"
        title="Уверенность начинается с защиты."
        description="Подберите страховку для ипотеки или для дома, который ещё строится."
      />
      <div
        className="insurance-tabs"
        role="tablist"
        aria-label="Вид страхования"
      >
        <button
          role="tab"
          aria-selected={kind === "mortgage"}
          className={kind === "mortgage" ? "active" : ""}
          onClick={() => setParams(obligation ? { obligation } : {})}
        >
          <ShieldCheck size={20} /> Ипотечное страхование{" "}
          <span>(для банка)</span>
        </button>
        <button
          role="tab"
          aria-selected={kind === "construction"}
          className={kind === "construction" ? "active" : ""}
          onClick={() => setParams({ type: "construction" })}
        >
          <House size={20} /> Страхование недостроя
        </button>
      </div>
      <div className="insurance-layout">
        <section className="panel insurance-form">
          <div className="section-heading">
            <div>
              <h2>
                {kind === "mortgage"
                  ? "Рассчитайте стоимость полиса"
                  : "Пока дом растёт — пусть будет защищён"}
              </h2>
              <p>Пара деталей, и предварительный расчёт готов.</p>
            </div>
            <span className="icon-tile">
              <ShieldCheck />
            </span>
          </div>
          {obligation && kind === "mortgage" && (
            <Notice tone="success">
              Данные календаря подставлены. После демооплаты это обязательство
              обновится автоматически.
            </Notice>
          )}
          <Field
            label={
              kind === "mortgage"
                ? "Остаток долга по ипотеке"
                : "Стоимость коробки и материалов"
            }
          >
            <div className="range-value">{money(prefs.amount)}</div>
            <input
              type="range"
              min="500000"
              max="12000000"
              step="50000"
              value={prefs.amount}
              onChange={(e) => change({ amount: +e.target.value })}
            />
            <span className="range-ends">
              <span>500 тыс. ₽</span>
              <span>12 млн ₽</span>
            </span>
          </Field>
          <div className="form-grid">
            <Field label="Материал стен">
              <select
                value={prefs.material}
                onChange={(e) =>
                  change({ material: e.target.value as "stone" | "wood" })
                }
              >
                <option value="stone">Кирпич / газобетон</option>
                <option value="wood">Дерево / каркас</option>
              </select>
            </Field>
            <Field
              label="Возраст заёмщика"
              hint="В демоформуле возраст не меняет цену."
            >
              <input
                type="number"
                min="18"
                max="85"
                value={prefs.age || ""}
                onChange={(e) => change({ age: +e.target.value })}
                aria-invalid={!validAge}
              />
            </Field>
          </div>
          {!validAge && (
            <p className="error-text" role="alert">
              Укажите возраст от 18 до 85 лет.
            </p>
          )}
          <h3 className="coverage-heading">Что защитим</h3>
          <label className="coverage checked">
            <input type="checkbox" checked disabled />
            <span className="coverage-icon">
              <House size={22} />
            </span>
            <span>
              <strong>
                {kind === "mortgage"
                  ? "Конструктив дома"
                  : "Коробка и строительные материалы"}
              </strong>
              <small>
                {kind === "mortgage"
                  ? "Стены, перекрытия, кровля и фундамент"
                  : "Пожар, стихийные бедствия и кража"}
              </small>
            </span>
            <span className="badge neutral">Обязательно</span>
          </label>
          {kind === "mortgage" && (
            <label className={`coverage ${prefs.life ? "checked" : ""}`}>
              <input
                type="checkbox"
                checked={prefs.life}
                onChange={(e) => change({ life: e.target.checked })}
              />
              <span className="coverage-icon">
                <Heart size={22} />
              </span>
              <span>
                <strong>Жизнь и здоровье</strong>
                <small>Для сохранения минимальной ставки в демосценарии</small>
              </span>
            </label>
          )}
          <p className="fine-print">
            Расчёт по условной формуле MVP: стоимость × 0,25% × коэффициент
            материала
            {kind === "mortgage"
              ? " + 0,3% за жизнь и здоровье при выборе покрытия"
              : ""}
            . Это не тариф страховщика.
          </p>
          <div className="auto-transfer">
            <span className="icon-tile">
              <FileCheck2 />
            </span>
            <div>
              <span className="badge green">
                Автоматическая передача в банк · демо
              </span>
              <h3>Меньше действий. Больше спокойствия.</h3>
              <p>
                {kind === "mortgage"
                  ? "Вам не нужно скачивать полис и отправлять его в офис — статус в календаре «Анти-штраф» обновится сам. В прототипе меняется только локальный статус."
                  : "Демополис сохранится в вашем профиле. Страхование недостроя не заменяет полис ипотечного страхования."}
              </p>
            </div>
          </div>
        </section>
        <aside>
          <div className="policy-summary">
            <div className="insurer">
              <span className="insurer-mark">
                <ShieldCheck size={22} />
              </span>
              <div>
                <strong>РСХБ-Страхование</strong>
                <small>Демонстрационный расчёт</small>
              </div>
            </div>
            <div className="policy-price">
              <span>Ориентировочная стоимость</span>
              <strong>{money(price.total)}</strong>
              <small>за 1 год защиты</small>
            </div>
            <dl className="price-details">
              <div>
                <dt>
                  {kind === "mortgage" ? "Конструктив" : "Коробка и материалы"}
                </dt>
                <dd>{money(price.structure)}</dd>
              </div>
              {kind === "mortgage" && (
                <div>
                  <dt>Жизнь и здоровье</dt>
                  <dd>{prefs.life ? money(price.life) : "Не включено"}</dd>
                </div>
              )}
              <div>
                <dt>Страховая сумма</dt>
                <dd>{money(prefs.amount)}</dd>
              </div>
            </dl>
            <button
              className="button primary full"
              disabled={!validAge}
              onClick={() =>
                requireAuth("Войдите, чтобы оформить полис", (u) =>
                  setCheckout({
                    name: u.fullName || "",
                    phone: maskPhone(u.phone),
                    email: "",
                  }),
                )
              }
            >
              Оформить полис
            </button>
            <div className="safe-note">
              <LockKeyhole size={14} /> Демо: без настоящего списания денег
            </div>
            <div className="summary-benefits">
              <span>
                <Check size={16} /> Расчёт без звонков и регистрации
              </span>
              <span>
                <Check size={16} /> Полис остаётся в вашем профиле
              </span>
              <span>
                <Check size={16} /> Всё в одном удобном сервисе
              </span>
            </div>
          </div>
          <div className="insurance-tip">
            <Flame size={23} />
            <div>
              <h3>Забота о доме начинается раньше новоселья</h3>
              <p>
                Материалы и готовая коробка тоже нуждаются в защите. Рассчитайте
                отдельный полис для этапа стройки.
              </p>
            </div>
          </div>
        </aside>
      </div>
      {checkout && (
        <Modal
          title="Оформим вашу защиту"
          onClose={() => {
            operation.current++;
            setBusy(false);
            setCheckout(null);
          }}
        >
          <p className="muted">
            {kind === "mortgage"
              ? "Ипотечное страхование"
              : "Страхование недостроя"}{" "}
            · {money(price.total)} за год
          </p>
          <form
            onSubmit={(e) => {
              e.preventDefault();
              void buy();
            }}
          >
            <Field label="ФИО">
              <input
                autoComplete="name"
                required
                maxLength={120}
                value={checkout.name}
                onChange={(e) =>
                  setCheckout({ ...checkout, name: e.target.value })
                }
              />
            </Field>
            <Field label="Телефон">
              <input
                type="tel"
                autoComplete="tel"
                required
                value={checkout.phone}
                onChange={(e) =>
                  setCheckout({ ...checkout, phone: maskPhone(e.target.value) })
                }
              />
            </Field>
            <Field label="Электронная почта">
              <input
                type="email"
                autoComplete="email"
                required
                value={checkout.email}
                onChange={(e) =>
                  setCheckout({ ...checkout, email: e.target.value })
                }
              />
            </Field>
            <Notice>
              Демонстрационная оплата. Деньги не списываются, договор
              страхования не заключается. Контакты не отправляются страховщику.
            </Notice>
            <button
              className="button primary full"
              disabled={
                busy || !validPhone(checkout.phone) || !checkout.name.trim()
              }
            >
              {busy ? (
                <>
                  <Spinner /> Оформляем…
                </>
              ) : (
                `Подтвердить демооплату · ${money(price.total)}`
              )}
            </button>
          </form>
        </Modal>
      )}
      {paid && (
        <Modal title="Ваши планы под защитой" onClose={() => setPaid(null)}>
          <div className="success-illustration">
            <ShieldCheck size={52} />
          </div>
          <span className="badge green">Демополис оформлен</span>
          <h3>№ {paid.id.slice(0, 8)}</h3>
          <p className="muted">
            {paid.kind === "mortgage" && data.mortgageSetup
              ? "Статус страхового обязательства в календаре обновлён автоматически."
              : paid.kind === "mortgage"
                ? "Полис сохранён. Когда вы создадите календарь, он будет учтён в страховых обязательствах."
                : "Полис страхования недостроя сохранён в вашем профиле."}
          </p>
          <Link
            className="button primary full"
            to="/mortgage-care"
            onClick={() => setPaid(null)}
          >
            <CalendarCheck2 size={19} /> Перейти в календарь
          </Link>
          <p className="fine-print">
            Демонстрация. Полис не имеет юридической силы.
          </p>
        </Modal>
      )}
    </>
  );
}
