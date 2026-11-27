import { useRef, useState, type DragEvent } from "react";
import { Link } from "react-router-dom";
import {
  CalendarDays,
  ShieldCheck,
  FileText,
  Upload,
  Check,
  Clock3,
  AlertCircle,
  Bell,
  House,
  BriefcaseBusiness,
  MapPin,
  Download,
  Trash2,
} from "lucide-react";
import { useApp } from "./store";
import {
  dateLabel,
  daysLeft,
  today,
  generateObligations,
  effectiveStatus,
  validateFile,
  type MortgageObligation,
  type AppData,
} from "./domain";
import { PageHeading, Field, Notice, Spinner } from "./ui";
const icons = {
  employment: BriefcaseBusiness,
  registration: MapPin,
  construction_end: House,
  insurance: ShieldCheck,
};
export function MortgageCare() {
  const { data, update, requireAuth, notify } = useApp();
  const [filter, setFilter] = useState("all");
  const [all, setAll] = useState(false);
  const setup = data.mortgageDraft;
  const obligations = data.obligations;
  const approved = obligations.filter(
    (o) => effectiveStatus(o) === "approved",
  ).length;
  const overdue = obligations.filter(
    (o) => effectiveStatus(o) === "overdue",
  ).length;
  const soon = obligations.filter(
    (o) => effectiveStatus(o) === "pending" && daysLeft(o.dueDate) <= 30,
  ).length;
  const filtered = obligations.filter(
    (o) =>
      filter === "all" ||
      (filter === "attention"
        ? effectiveStatus(o) === "overdue" ||
          (effectiveStatus(o) === "pending" && daysLeft(o.dueDate) <= 30)
        : effectiveStatus(o) === filter),
  );
  const visible = all ? filtered : filtered.slice(0, 6);
  function build() {
    const generated = generateObligations(setup);
    const latest = data.policies
      .filter((p) => p.kind === "mortgage" && p.expiresAt >= today())
      .at(-1);
    if (latest) {
      const first = generated.find(
        (o) => o.type === "insurance" && o.dueDate <= latest.expiresAt,
      );
      if (first) {
        first.status = "approved";
        first.policyId = latest.id;
      }
    }
    if (update((d) => ({ ...d, mortgageSetup: setup, obligations: generated })))
      notify("Ваш календарь готов. Все даты сохранены.");
  }
  return (
    <>
      <PageHeading
        eyebrow="КАБИНЕТ «АНТИ-ШТРАФ»"
        title="Спокойно. Всё по плану."
        description="Важные сроки и документы — чтобы льготная ставка оставалась с вами."
        action={
          data.mortgageSetup ? (
            <button
              className={`button ${data.notifications ? "secondary" : "primary"}`}
              onClick={() =>
                requireAuth("Войдите, чтобы включить напоминания", () => {
                  const enabled = !data.notifications;
                  if (update((d) => ({ ...d, notifications: enabled })))
                    notify(
                      enabled
                        ? "Напоминания включены: ближайшие сроки будут выделены в календаре."
                        : "Напоминания выключены",
                    );
                })
              }
            >
              <Bell size={18} />
              {data.notifications
                ? "Напоминания включены"
                : "Включить напоминания"}
            </button>
          ) : undefined
        }
      />
      {!data.mortgageSetup ? (
        <div className="onboarding-layout">
          <section className="panel onboarding">
            <span className="icon-tile">
              <CalendarDays />
            </span>
            <h2>Составим ваш календарь</h2>
            <p className="muted">
              Три детали о кредите — и все обязательства перед вами. Номер
              договора и банковские реквизиты не нужны.
            </p>
            <form
              onSubmit={(e) => {
                e.preventDefault();
                build();
              }}
            >
              <Field label="Дата подписания кредитного договора">
                <input
                  type="date"
                  required
                  min="2000-01-01"
                  max={today()}
                  value={setup.signedAt}
                  onChange={(e) =>
                    update((d) => ({
                      ...d,
                      mortgageDraft: {
                        ...d.mortgageDraft,
                        signedAt: e.target.value,
                      },
                      mortgageDraftTouched: true,
                    }))
                  }
                />
              </Field>
              <Field label="Тип объекта">
                <select
                  value={setup.objectType}
                  onChange={(e) =>
                    update((d) => ({
                      ...d,
                      mortgageDraft: {
                        ...d.mortgageDraft,
                        objectType: e.target.value as "construction" | "ready",
                      },
                      mortgageDraftTouched: true,
                    }))
                  }
                >
                  <option value="construction">Строительство ИЖС</option>
                  <option value="ready">Готовый дом</option>
                </select>
              </Field>
              <label className="check-label veteran-check">
                <input
                  type="checkbox"
                  checked={setup.isVeteran}
                  onChange={(e) =>
                    update((d) => ({
                      ...d,
                      mortgageDraft: {
                        ...d.mortgageDraft,
                        isVeteran: e.target.checked,
                      },
                      mortgageDraftTouched: true,
                    }))
                  }
                />
                <span>
                  Являюсь участником СВО
                  <small>
                    В демосценарии не требуется подтверждение занятости.
                  </small>
                </span>
              </label>
              <button
                className="button primary full"
                disabled={!setup.signedAt}
              >
                Создать мой календарь
              </button>
            </form>
            <p className="fine-print">
              Гостевой черновик сохраняется автоматически. Для загрузки
              документов потребуется демовход.
            </p>
          </section>
          <aside className="onboarding-preview">
            <div className="preview-badge">
              <ShieldCheck size={20} /> Ваша ставка под контролем
            </div>
            <h2>
              Занимайтесь домом.
              <br />
              <span>О сроках напомним.</span>
            </h2>
            <p>
              В календаре появятся обязательства, рассчитанные от даты вашего
              договора.
            </p>
            <div className="timeline-preview">
              {[
                ["6 месяцев", "Подтвердить занятость"],
                ["180 дней", "Подтвердить прописку"],
                ["Каждый год", "Продлить страхование"],
                ["24 месяца", "Завершить строительство"],
              ].map(([date, title], i) => (
                <div key={title}>
                  <span className="timeline-point">
                    {i === 2 ? <ShieldCheck size={17} /> : <Check size={15} />}
                  </span>
                  <div>
                    <small>{date}</small>
                    <strong>{title}</strong>
                  </div>
                </div>
              ))}
            </div>
            <Notice>
              Это демонстрационный календарь по условиям ТЗ. Фактические сроки
              проверяйте по вашему кредитному договору.
            </Notice>
          </aside>
        </div>
      ) : (
        <>
          <div className="care-overview">
            <div className="care-rate">
              <ShieldCheck size={33} />
              <div>
                <small>Льготная ставка · демо</small>
                <strong>
                  3<span>%</span>
                </strong>
              </div>
              <span>
                Держим обязательства
                <br />
                под контролем
              </span>
            </div>
            <div className="care-metric">
              <strong>
                {approved}
                <small> / {obligations.length}</small>
              </strong>
              <span>обязательств принято</span>
            </div>
            <div className="care-metric">
              <strong className={soon ? "warning-text" : ""}>{soon}</strong>
              <span>срок в ближайшие 30 дней</span>
            </div>
            <div className="care-metric">
              <strong className={overdue ? "error-text" : ""}>{overdue}</strong>
              <span>просрочено</span>
            </div>
          </div>
          <div className="contract-note">
            <CalendarDays size={17} />
            <span>
              Договор от {dateLabel(data.mortgageSetup.signedAt)} ·{" "}
              {data.mortgageSetup.objectType === "construction"
                ? "Строительство ИЖС"
                : "Готовый дом"}
              {data.mortgageSetup.isVeteran
                ? " · Без подтверждения занятости"
                : ""}
            </span>
            <span>План на 5 лет</span>
          </div>
          {data.notifications && (
            <Notice>
              Напоминания работают внутри этого демокалендаря. Письма, SMS и
              push-уведомления не отправляются.
            </Notice>
          )}
          <div className="filter-tabs" aria-label="Фильтр обязательств">
            {[
              ["all", "Все обязательства"],
              ["attention", "Требуют внимания"],
              ["under_review", "На проверке"],
              ["approved", "Принято"],
            ].map(([key, label]) => (
              <button
                key={key}
                className={filter === key ? "active" : ""}
                aria-pressed={filter === key}
                onClick={() => {
                  setFilter(key);
                  setAll(false);
                }}
              >
                {label}
              </button>
            ))}
          </div>
          <div className="obligations-grid">
            {visible.map((o) => (
              <ObligationCard key={o.id} obligation={o} update={update} />
            ))}
          </div>
          {!filtered.length && (
            <div className="empty-state">
              <Check size={32} />
              <h3>Здесь пока нет обязательств</h3>
              <p>Выберите другой фильтр, чтобы увидеть весь календарь.</p>
            </div>
          )}
          {filtered.length > 6 && !all && (
            <button
              className="button secondary show-more"
              onClick={() => setAll(true)}
            >
              Показать ещё {filtered.length - 6} обязательств
            </button>
          )}
          <p className="fine-print calendar-footnote">
            Даты и требования — демонстрационная модель по ТЗ, не персональные
            условия кредита. Статус «Принято» имитирует проверку и не
            подтверждает реальное сохранение ставки банком.
          </p>
        </>
      )}
    </>
  );
}
function ObligationCard({
  obligation: o,
  update,
}: {
  obligation: MortgageObligation;
  update: (f: (d: AppData) => AppData) => boolean;
}) {
  const { requireAuth, notify } = useApp();
  const input = useRef<HTMLInputElement>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [drag, setDrag] = useState(false);
  const status = effectiveStatus(o);
  const left = daysLeft(o.dueDate);
  const Icon = icons[o.type];
  const statusText = {
    approved: "Принято",
    under_review: "На проверке",
    overdue: "Просрочено",
    pending: left <= 30 ? "Скоро срок" : "Предстоит",
  }[status];
  const tone =
    status === "approved"
      ? "green"
      : status === "overdue"
        ? "red"
        : status === "under_review" || left <= 30
          ? "orange"
          : "neutral";
  async function saveFile(file: File) {
    const validation = validateFile(file);
    if (validation) {
      setError(validation);
      return;
    }
    setError("");
    setBusy(true);
    try {
      const content = await new Promise<string>((resolve, reject) => {
        const reader = new FileReader();
        reader.onload = () => resolve(String(reader.result));
        reader.onerror = () => reject(new Error("Не удалось прочитать файл."));
        reader.readAsDataURL(file);
      });
      if (
        update((d) => ({
          ...d,
          obligations: d.obligations.map((x) =>
            x.id === o.id
              ? {
                  ...x,
                  status: "under_review",
                  uploadedFileName: file.name,
                  fileData: content,
                  fileType: file.type,
                }
              : x,
          ),
        }))
      )
        notify("Документ сохранён локально и ожидает демопроверки.");
      else setError("Документ не сохранён: недостаточно места в браузере.");
    } catch {
      setError("Не удалось прочитать файл. Попробуйте другой документ.");
    } finally {
      setBusy(false);
    }
  }
  function drop(event: DragEvent) {
    event.preventDefault();
    setDrag(false);
    const file = event.dataTransfer.files[0];
    if (file)
      requireAuth("Войдите, чтобы отправить документы в банк", () => {
        void saveFile(file);
      });
  }
  return (
    <article className={`obligation-card status-${status}`}>
      <div className="obligation-top">
        <span className="icon-tile">
          <Icon size={22} />
        </span>
        <span className={`badge ${tone}`}>
          {status === "approved" ? (
            <Check size={14} />
          ) : status === "overdue" ? (
            <AlertCircle size={14} />
          ) : (
            <Clock3 size={14} />
          )}{" "}
          {statusText}
        </span>
      </div>
      <h3>{o.title}</h3>
      <div className="deadline">
        <span>{dateLabel(o.dueDate)}</span>
        <small
          className={
            status === "approved"
              ? ""
              : left < 0
                ? "error-text"
                : left <= 30
                  ? "warning-text"
                  : ""
          }
        >
          {status === "approved"
            ? "Выполнено"
            : status === "under_review"
              ? "Документ на проверке"
              : left < 0
                ? `Просрочено на ${Math.abs(left)} дн.`
                : left === 0
                  ? "Срок сегодня"
                  : `Осталось ${left} дн.`}
        </small>
      </div>
      <p className="obligation-description">{o.description}</p>
      {status === "approved" ? (
        <div className="approval-note">
          <ShieldCheck size={18} />
          <span>
            Принято — ставка 3% сохранена
            <small>Демонстрационное подтверждение</small>
          </span>
        </div>
      ) : (
        <>
          <div
            className={`dropzone ${drag ? "dragging" : ""}`}
            onDragOver={(e) => {
              e.preventDefault();
              setDrag(true);
            }}
            onDragLeave={() => setDrag(false)}
            onDrop={drop}
          >
            <input
              ref={input}
              type="file"
              accept=".pdf,.jpg,.jpeg,.png"
              hidden
              aria-label={`Документ: ${o.title}`}
              onChange={(e) => {
                const file = e.target.files?.[0];
                if (file)
                  requireAuth(
                    "Войдите, чтобы отправить документы в банк",
                    () => {
                      void saveFile(file);
                    },
                  );
                e.target.value = "";
              }}
            />
            <button
              className="upload-button"
              disabled={busy}
              onClick={() =>
                requireAuth("Войдите, чтобы отправить документы в банк", () =>
                  input.current?.click(),
                )
              }
            >
              {busy ? <Spinner /> : <Upload size={20} />}
              <span>
                {busy
                  ? "Сохраняем документ…"
                  : o.uploadedFileName
                    ? "Заменить документ"
                    : "Загрузить документ"}
                <small>или перетащите сюда · PDF, JPG, PNG до 1,5 МБ</small>
              </span>
            </button>
          </div>
          {error && (
            <p className="error-text" role="alert">
              {error}
            </p>
          )}
          {o.type === "insurance" && (
            <Link
              className="button secondary full renew-button"
              to={`/insurance?obligation=${o.id}`}
            >
              Продлить в 1 клик
            </Link>
          )}
        </>
      )}
      {o.uploadedFileName && (
        <div className="uploaded-file">
          <FileText size={17} />
          <span>{o.uploadedFileName}</span>
          {o.fileData && (
            <a
              className="icon-button"
              href={o.fileData}
              download={o.uploadedFileName}
              aria-label={`Скачать ${o.uploadedFileName}`}
            >
              <Download size={16} />
            </a>
          )}
          <button
            className="icon-button"
            aria-label={`Удалить документ ${o.uploadedFileName}`}
            onClick={() =>
              update((d) => ({
                ...d,
                obligations: d.obligations.map((x) =>
                  x.id === o.id
                    ? {
                        ...x,
                        status: "pending",
                        uploadedFileName: undefined,
                        fileData: undefined,
                        fileType: undefined,
                        policyId: undefined,
                      }
                    : x,
                ),
              }))
            }
          >
            <Trash2 size={16} />
          </button>
        </div>
      )}
      {status === "under_review" && (
        <button
          className="demo-approve text-button"
          onClick={() => {
            if (
              update((d) => ({
                ...d,
                obligations: d.obligations.map((x) =>
                  x.id === o.id ? { ...x, status: "approved" } : x,
                ),
              }))
            )
              notify("Демо: документ принят, ставка 3% сохранена.");
          }}
        >
          Демо: симулировать одобрение банком
        </button>
      )}
    </article>
  );
}
