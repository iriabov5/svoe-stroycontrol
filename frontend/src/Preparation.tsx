import {
  useEffect,
  useState,
  useRef,
  type SubmitEvent,
  type ReactNode,
} from "react";

import {
  api,
  request,
  errorMessage,
  ApiError,
  type Project,
  type Account,
} from "./api/client";

import type { components } from "./api/schema";
import { ActorLabel } from "./Participants";
import { ReissuePanel } from "./Reissue";
import { Field, Notice, Area, Illustration } from "./components";
type RequestData = components["schemas"]["ConstructionRequest"];
type Proposal = components["schemas"]["ConstructionProposal"];
type Contract = components["schemas"]["ConstructionContract"];
type Plan = components["schemas"]["ProposalStage"];
type Mortgage = components["schemas"]["MortgageResult"];
type Doc = Proposal | Contract;
const rub = (n: number | null | undefined) =>
  n == null
    ? "Не указано"
    : new Intl.NumberFormat("ru-RU", {
        style: "currency",
        currency: "RUB",
        maximumFractionDigits: 0,
      }).format(n);
const day = (s: string | null | undefined) =>
  s ? new Date(s + "T12:00:00").toLocaleDateString("ru-RU") : "Не указано";
const states: Record<string, string> = {
  DRAFT: "Черновик",
  SUBMITTED: "На согласовании",
  CHANGES_REQUESTED: "Нужно уточнить",
  APPROVED: "Согласовано",
  REJECTED: "Отклонено",
};
const kinds: Record<string, string> = {
  FOUNDATION: "Фундамент",
  WALLS: "Стены",
  ROOF: "Крыша",
  UTILITIES: "Инженерные сети",
  FINISHING: "Отделка",
  OTHER: "Другое",
};
const base = (id: string) => `/projects/${encodeURIComponent(id)}`;
function Row({
  label,
  children,
}: Readonly<{ label: string; children: ReactNode }>) {
  return (
    <div>
      <dt>{label}</dt>
      <dd>{children}</dd>
    </div>
  );
}
export function Preparation({
  project,
  account,
  onChanged,
}: Readonly<{
  project: Project;
  account: Account;
  onChanged: (p: Project) => void;
}>) {
  const [data, setData] = useState<RequestData | null>(null),
    [proposals, setProposals] = useState<Proposal[]>([]),
    [contracts, setContracts] = useState<Contract[]>([]),
    [error, setError] = useState(""),
    [attempt, setAttempt] = useState(0);
  const owner = account.id === project.customerId;
  async function reload() {
    const [r, p, c] = await Promise.all([
      request<RequestData>(base(project.id) + "/request"),
      request<Proposal[]>(base(project.id) + "/proposals"),
      request<Contract[]>(base(project.id) + "/contracts"),
    ]);
    setData(r);
    setProposals(p);
    setContracts(c);
    onChanged(await api.get(project.id));
  }
  useEffect(() => {
    let current = true;
    Promise.all([
      request<RequestData>(base(project.id) + "/request"),
      request<Proposal[]>(base(project.id) + "/proposals"),
      request<Contract[]>(base(project.id) + "/contracts"),
    ])
      .then(([r, p, c]) => {
        if (current) {
          setData(r);
          setProposals(p);
          setContracts(c);
        }
      })
      .catch((error_) => {
        if (current) setError(errorMessage(error_));
      });
    return () => {
      current = false;
    };
  }, [project.id, attempt]);
  const step = currentStep();
  function currentStep() {
    if (!project.requestSubmitted) {
      return 0;
    }
    if (!project.approvedProposalId) {
      return 1;
    }
    if (!project.approvedContractId) {
      return 2;
    }
    return 3;
  }
  return (
    <section className="preparation" aria-label="Подготовка строительства">
      <ol className="process-steps">
        {["Заявка", "Смета и график", "Договор", "Строительство"].map(
          (label, i) => (
            <li
              key={label}
              aria-current={
                !project.preparationClosed && step === i ? "step" : undefined
              }
              className={i <= step ? "active" : ""}
            >
              <span>{i + 1}</span>
              {label}
            </li>
          ),
        )}
      </ol>
      {project.preparationClosed && (
        <Notice>
          Предложение отклонено. История сохранена, подготовка в этой карточке
          закрыта.
        </Notice>
      )}
      {error && (
        <Notice error>
          {error}{" "}
          <button
            type="button"
            className="text-button"
            onClick={() => {
              setError("");
              setAttempt(attempt + 1);
            }}
          >
            Повторить загрузку
          </button>
        </Notice>
      )}
      {!data ? (
        <Notice>Загружаем подготовку стройки…</Notice>
      ) : (
        <>
          {project.requestSubmitted && proposals.length > 0 ? (
            <details className="request-history">
              <summary>
                Исходная заявка заказчика · {rub(data.desiredBudget)}
              </summary>
              <RequestPanel
                data={data}
                project={project}
                owner={owner}
                onSaved={setData}
                onSent={reload}
              />
            </details>
          ) : (
            <RequestPanel
              data={data}
              project={project}
              owner={owner}
              onSaved={setData}
              onSent={reload}
            />
          )}
          {project.requestSubmitted && (
            <>
              <DocumentPanel
                key="proposal"
                project={project}
                owner={owner}
                documents={proposals}
                contract={false}
                requestData={data}
                onChanged={reload}
              />
              {project.approvedProposalId && (
                <DocumentPanel
                  key="contract"
                  project={project}
                  owner={owner}
                  documents={contracts}
                  contract
                  requestData={data}
                  onChanged={reload}
                />
              )}
            </>
          )}
          {owner && project.preparationClosed && (
            <ReissuePanel project={project} />
          )}
          {project.approvedProposalId && !project.approvedContractId && (
            <Notice>
              План согласован. Начать работы можно после согласования договора
              подряда.
            </Notice>
          )}
        </>
      )}
    </section>
  );
}
function RequestPanel({
  data,
  project,
  owner,
  onSaved,
  onSent,
}: Readonly<{
  data: RequestData;
  project: Project;
  owner: boolean;
  onSaved: (r: RequestData) => void;
  onSent: () => Promise<void>;
}>) {
  const [form, setForm] = useState(data),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [notice, setNotice] = useState("");
  const [invalid, setInvalid] = useState<Record<string, string>>({});
  useEffect(() => setForm(data), [data]);
  const draft = owner && data.state === "DRAFT";
  function value<K extends keyof RequestData>(key: K, v: RequestData[K]) {
    setForm({ ...form, [key]: v });
  }
  async function save(send = false) {
    setError("");
    setInvalid({});
    setNotice("");
    setBusy(true);
    try {
      const {
        id: _id,
        projectId: _projectId,
        state: _state,
        location: _location,
        submittedAt: _submittedAt,
        ...body
      } = form;
      const r = await request<RequestData>(
        base(project.id) + "/request",
        "PATCH",
        body,
      );
      setForm(r);
      onSaved(r);
      if (send) {
        await request(base(project.id) + "/request/submit", "POST", {
          version: r.version,
        });
        await onSent();
      } else setNotice("Черновик заявки сохранён");
    } catch (error_) {
      setError(errorMessage(error_));
      if (error_ instanceof ApiError) setInvalid(error_.fieldErrors);
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="panel request-panel">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Шаг 1 · Пожелания заказчика</p>
          <h2>Заявка на строительство</h2>
        </div>
        <span className="badge">
          {data.state === "DRAFT" ? "Черновик" : "Передана подрядчику"}
        </span>
      </div>
      {error && <Notice error>{error}</Notice>}
      {notice && <Notice>{notice}</Notice>}
      {draft ? (
        <form
          onSubmit={(e: SubmitEvent) => {
            e.preventDefault();
            save(true);
          }}
          aria-busy={busy}
        >
          <Area
            error={invalid.description}
            id="request-description"
            label="Какой дом вы хотите"
            value={form.description ?? ""}
            onChange={(v) => value("description", v)}
            maxLength={10000}
            hint="Для кого дом, сколько комнат, что особенно важно"
            required
          />
          <div className="detail-grid">
            {(
              [
                [
                  "technology",
                  "Технология строительства",
                  "technologyRecommendation",
                ],
                ["materials", "Желаемые материалы", "materialsRecommendation"],
              ] as const
            ).map(([key, label, flag]) => (
              <div key={key}>
                <Field
                  error={invalid[key]}
                  id={key}
                  label={label}
                  value={form[key] ?? ""}
                  maxLength={2000}
                  disabled={form[flag]}
                  onChange={(e) => value(key, e.target.value || null)}
                />
                <label className="check-label">
                  <input
                    type="checkbox"
                    checked={form[flag] ?? false}
                    onChange={(e) =>
                      setForm({
                        ...form,
                        [flag]: e.target.checked,
                        [key]: e.target.checked ? null : form[key],
                      })
                    }
                  />{" "}
                  <span>Нужна рекомендация подрядчика</span>{" "}
                </label>
              </div>
            ))}
          </div>
          <div className="form-grid">
            {(
              [
                ["lengthM", "Длина дома, м"],
                ["widthM", "Ширина дома, м"],
                ["areaM2", "Площадь, м²"],
                ["floors", "Этажность"],
              ] as const
            ).map(([key, label]) => (
              <Field
                key={key}
                error={invalid[key]}
                id={key}
                label={label}
                type="number"
                min={key === "floors" ? 1 : 0.01}
                step={key === "floors" ? 1 : 0.01}
                max={key === "floors" ? 10 : undefined}
                value={form[key] ?? ""}
                onChange={(e) =>
                  value(key, e.target.value ? Number(e.target.value) : null)
                }
              />
            ))}
          </div>
          <p className="hint">
            Укажите площадь или оба габарита. Для многоэтажного дома площадь
            может отличаться от площади основания.
          </p>
          <Field
            error={invalid.completionScope}
            id="completionScope"
            label="Комплектация"
            hint="Например, тёплый контур или под ключ"
            required
            maxLength={1000}
            value={form.completionScope ?? ""}
            onChange={(e) => value("completionScope", e.target.value)}
          />
          <div className="detail-grid">
            <Field
              error={invalid.desiredBudget}
              id="desiredBudget"
              label="Желаемый бюджет, ₽"
              type="number"
              min="0.01"
              step="0.01"
              required
              value={form.desiredBudget ?? ""}
              onChange={(e) =>
                value(
                  "desiredBudget",
                  e.target.value ? Number(e.target.value) : null,
                )
              }
            />
            <Field
              error={invalid.cadastralNumber}
              id="cadastralNumber"
              label="Кадастровый номер участка"
              hint="Необязательно, например 50:12:1234567:89"
              maxLength={50}
              value={form.cadastralNumber ?? ""}
              onChange={(e) => value("cadastralNumber", e.target.value || null)}
            />
          </div>
          <p>
            Участок: <strong>{project.address}</strong>
          </p>
          <div className="detail-grid">
            <Field
              error={invalid.desiredStart}
              id="desiredStart"
              label="Желаемое начало строительства"
              type="date"
              required
              value={form.desiredStart ?? ""}
              onChange={(e) => value("desiredStart", e.target.value || null)}
            />
            <Field
              error={invalid.desiredFinish}
              id="desiredFinish"
              label="Желаемое завершение строительства"
              type="date"
              min={form.desiredStart ?? undefined}
              required
              value={form.desiredFinish ?? ""}
              onChange={(e) => value("desiredFinish", e.target.value || null)}
            />
          </div>
          <FinancingChoice
            source={form.financingSource}
            error={invalid.financingSource}
            onChange={(source) => value("financingSource", source)}
          />
          <div className="form-actions">
            <button
              type="button"
              className="button secondary"
              disabled={busy}
              onClick={() => save()}
            >
              Сохранить черновик
            </button>
            <button
              type="submit"
              className="button primary"
              disabled={busy || !project.contractorId}
            >
              {busy ? "Сохраняем…" : "Отправить заявку подрядчику"}
            </button>
          </div>
          {!project.contractorId && (
            <p className="hint">
              Сначала назначьте подрядчика в карточке выше.
            </p>
          )}
        </form>
      ) : (
        <>
          <RequestSummary data={data} />
          <p className="hint">
            Это исходные пожелания. Окончательные состав, стоимость и сроки
            согласуются в предложении подрядчика.
          </p>
        </>
      )}
      {owner && data.financingSource === "RSHB_RURAL_MORTGAGE" && (
        <MortgagePanel
          projectId={project.id}
          version={data.version ?? 0}
          editable={draft}
          onImported={async () =>
            onSaved(await request<RequestData>(base(project.id) + "/request"))
          }
        />
      )}
    </section>
  );
}
function MortgagePanel({
  projectId,
  version,
  editable,
  onImported,
}: Readonly<{
  projectId: string;
  version: number;
  editable: boolean;
  onImported: () => Promise<void>;
}>) {
  const [data, setData] = useState<Mortgage | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [consent, setConsent] = useState(false);
  const dialog = useRef<HTMLDialogElement>(null),
    trigger = useRef<HTMLButtonElement>(null);
  const key = useRef<string | null>(null);
  useEffect(() => {
    request<Mortgage>(base(projectId) + "/mortgage-approval")
      .then(setData)
      .catch((error_) => setError(errorMessage(error_)));
  }, [projectId]);
  async function load() {
    setBusy(true);
    setError("");
    try {
      key.current ??= crypto.randomUUID();
      setData(
        await request<Mortgage>(
          base(projectId) + "/mortgage-approval/import",
          "POST",
          { version, demoConsent: true, idempotencyKey: key.current },
        ),
      );
      await onImported();
      key.current = null;
      dialog.current?.close();
      trigger.current?.focus();
    } catch (error_) {
      setError(errorMessage(error_));
    } finally {
      setBusy(false);
    }
  }
  function approvalLabel() {
    if (!data) return "";
    if (data.status === "EXPIRED") {
      return "Срок решения истёк";
    }
    if (data.status === "NOT_FOUND") {
      return "Актуальное одобрение не найдено";
    }
    return "Одобрено · демоданные";
  }
  return (
    <aside className="bank-card">
      <div className="section-heading">
        <div>
          <p className="eyebrow">Россельхозбанк</p>
          <h3>Сведения об ипотеке</h3>
        </div>
        <span className="badge">Демо</span>
      </div>
      <p>
        Подключите кабинет, чтобы подгрузить сведения об одобренном кредите.
        Сейчас используются только демонстрационные данные.
      </p>
      {data?.snapshot && (
        <>
          <p>
            <strong>{approvalLabel()}</strong>
          </p>
          <div className="credit-amount">{rub(data.snapshot.amount)}</div>
          <p>
            {data.snapshot.rate}% · {data.snapshot.termMonths} мес.
          </p>
          <p>
            Решение от {day(data.snapshot.decisionDate)}, действует до{" "}
            {day(data.snapshot.validUntil)}
          </p>
          <small>
            Получено {new Date(data.snapshot.fetchedAt).toLocaleString("ru-RU")}
            . Это не подтверждение выдачи кредита.
          </small>
        </>
      )}
      {data?.status === "NOT_FOUND" && (
        <Notice>
          Одобренный кредит не найден. Можно продолжить без подгрузки.
        </Notice>
      )}
      {error && !dialog.current?.open && <Notice error>{error}</Notice>}
      {editable && (
        <button
          ref={trigger}
          type="button"
          className="button secondary"
          onClick={() => {
            setConsent(false);
            setError("");
            dialog.current?.showModal();
          }}
        >
          {data?.snapshot
            ? "Обновить сведения"
            : "Подключить Интернет-банк РСХБ"}
        </button>
      )}
      <dialog
        ref={dialog}
        className="bank-dialog"
        aria-labelledby="bank-dialog-title"
        onCancel={(e) => {
          if (busy) e.preventDefault();
        }}
      >
        <p className="eyebrow">Демонстрационный режим</p>
        <h2 id="bank-dialog-title">Интернет-банк Россельхозбанка</h2>
        <p className="lead">Сведения о кредите — в вашей заявке</p>
        <ol className="bank-steps">
          <li>Подключение</li>
          <li>Получение сведений</li>
          <li>Результат</li>
        </ol>
        <p>
          Покажем тестовые сумму, ставку, срок и дату одобрения. Вход в
          настоящий банк не выполняется.
        </p>
        <p>Банковские логин, пароль и SMS-код не нужны.</p>
        {error && <Notice error>{error}</Notice>}
        <label className="check-label">
          <input
            type="checkbox"
            checked={consent}
            disabled={busy}
            onChange={(e) => setConsent(e.target.checked)}
          />{" "}
          <span>Показать демонстрационные сведения о кредите</span>{" "}
        </label>
        <div className="form-actions">
          <button
            type="button"
            className="button primary"
            disabled={!consent || busy}
            onClick={() => load()}
          >
            {busy
              ? "Получаем демонстрационные сведения…"
              : "Продолжить в деморежиме"}
          </button>
          <button
            type="button"
            className="button secondary"
            disabled={busy}
            onClick={() => {
              dialog.current?.close();
              trigger.current?.focus();
            }}
          >
            Отмена
          </button>
        </div>
      </dialog>
    </aside>
  );
}
function DocumentPanel({
  project,
  owner,
  documents,
  contract,
  requestData,
  onChanged,
}: Readonly<{
  project: Project;
  owner: boolean;
  documents: Doc[];
  contract: boolean;
  requestData: RequestData;
  onChanged: () => Promise<void>;
}>) {
  const labels = contract
    ? {
        collection: "/contracts",
        eyebrow: "Шаг 3 · Условия строительства",
        title: "Договор подряда",
        versionId: "contract-version",
        emptyDescription:
          "Зафиксируйте в договоре стороны, объект, работы, цену, порядок оплаты, сроки, приёмку и ответственность.",
        commentId: "contract-comment",
        approveLabel: "Согласовать договор",
        documentName: "договор",
      }
    : {
        collection: "/proposals",
        eyebrow: "Шаг 2 · Предложение подрядчика",
        title: "Смета и график работ",
        versionId: "proposal-version",
        emptyDescription:
          "Опишите работы, материалы и цены. Добавьте иллюстрации и разделите строительство на этапы со сроками.",
        commentId: "proposal-comment",
        approveLabel: "Согласовать предложение",
        documentName: "предложение",
      };

  const [error, setError] = useState(""),
    [busy, setBusy] = useState(false),
    [comment, setComment] = useState(""),
    [confirm, setConfirm] = useState(false),
    [selected, setSelected] = useState<string | null>(null);
  const [rejecting, setRejecting] = useState(false),
    [rejectionReason, setRejectionReason] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [editing, setEditing] = useState<Doc | null>(null);
  const [editorBusy, setEditorBusy] = useState(false),
    [dirty, setDirty] = useState(false);
  const latest = documents[0],
    current = documents.find((d) => d.id === selected) ?? latest;
  const root = base(project.id) + labels.collection;
  async function action(path: string, body?: unknown) {
    setBusy(true);
    setError("");
    setFields({});
    try {
      await request(root + path, "POST", body);
      setEditing(null);
      setConfirm(false);
      setRejecting(false);
      setSelected(null);
      await onChanged();
    } catch (error_) {
      setError(errorMessage(error_));
      if (error_ instanceof ApiError) setFields(error_.fieldErrors);
    } finally {
      setBusy(false);
    }
  }
  const contractor = project.permissions?.canPrepareDocuments ?? !owner;
  const draft = current?.state === "DRAFT" && contractor;
  function emptyTitle() {
    if (owner || !contractor) {
      if (contract) {
        return "Ожидаем договор от подрядчика";
      }
      return "Ожидаем предложение подрядчика";
    }
    if (contract) {
      return "Смета согласована — подготовьте договор";
    }
    return "Подготовьте смету и план строительства";
  }
  function approvalQuestion() {
    if (contract) {
      return "Подтвердить согласование этой версии договора? После этого подрядчик сможет начать этапы.";
    }
    return `Согласовать версию ${current.revisionNo} на ${rub("totalPrice" in current ? current.totalPrice : null)}? После согласования подрядчик подготовит договор.`;
  }
  function prepareLabel() {
    if (latest) {
      return "Подготовить новую версию";
    }
    if (contract) {
      return "Подготовить договор";
    }
    return "Подготовить предложение";
  }
  return (
    <section className="panel document-panel">
      <div className="section-heading">
        <div>
          <p className="eyebrow">{labels.eyebrow}</p>
          <h2>{labels.title}</h2>
        </div>
        {current && (
          <span className="badge">
            {states[current.state]} · версия {current.revisionNo}
          </span>
        )}
      </div>
      {current && (
        <div className="document-attribution">
          <p className="eyebrow">
            {current.state === "DRAFT"
              ? "Подготовил"
              : "Отправил на согласование"}
          </p>
          <ActorLabel
            snapshot={
              current.state === "DRAFT"
                ? current.createdBySnapshot
                : current.submittedBySnapshot
            }
          />
          <details>
            <summary>Авторство версии</summary>
            <p className="hint">Создатель черновика</p>
            <ActorLabel snapshot={current.createdBySnapshot} />
            {current.submittedById && (
              <>
                <p className="hint">Отправитель пакета</p>
                <ActorLabel snapshot={current.submittedBySnapshot} />
              </>
            )}
            {!current.submittedById && current.state !== "DRAFT" && (
              <p className="hint">
                Отправитель прежней версии не был сохранён.
              </p>
            )}
          </details>
        </div>
      )}
      {error && (
        <Notice error>
          {error} {Object.values(fields).join(". ")}
        </Notice>
      )}
      {contract && (
        <Notice>
          Согласование условий в сервисе. Электронная подпись и подтверждение
          юридического заключения договора пока не предусмотрены.
        </Notice>
      )}
      {documents.length > 1 && (
        <div className="field">
          <label htmlFor={labels.versionId}>История версий</label>
          <select
            id={labels.versionId}
            value={current?.id}
            onChange={(e) => {
              setSelected(e.target.value);
              setEditing(null);
              setConfirm(false);
            }}
          >
            {documents.map((d) => (
              <option key={d.id} value={d.id}>
                Версия {d.revisionNo} · {states[d.state]}
              </option>
            ))}
          </select>
        </div>
      )}
      {!current ? (
        <div className="document-empty">
          <h3>{emptyTitle()}</h3>
          <p>{labels.emptyDescription}</p>
        </div>
      ) : (
        <>
          {draft ? (
            <DocumentEditor
              key={current.id + ":" + current.version}
              document={editing ?? current}
              contract={contract}
              project={project}
              onSaved={async () => {
                setEditing(null);
                await onChanged();
              }}
              errors={fields}
              onFieldErrors={setFields}
              onBusy={setEditorBusy}
              onDirty={setDirty}
              onError={setError}
            />
          ) : (
            <>
              {<DocumentContent document={current} requestData={requestData} />}
            </>
          )}
          {current.decision && (
            <div className="decision-note">
              <strong>
                {states[current.decision.decision ?? ""]} ·{" "}
                {current.decision.decidedAt &&
                  new Date(current.decision.decidedAt).toLocaleString("ru-RU")}
              </strong>
              <ActorLabel
                snapshot={current.decision.actorSnapshot}
                role="CUSTOMER"
              />
              {current.decision.comment && (
                <p className="document-text">{current.decision.comment}</p>
              )}
            </div>
          )}
          {owner &&
            current.state === "SUBMITTED" &&
            current.id === latest?.id && (
              <div className="decision-actions">
                <Area
                  id={labels.commentId}
                  label="Замечания для подрядчика"
                  value={comment}
                  onChange={setComment}
                  maxLength={5000}
                />
                <div className="form-actions">
                  <button
                    type="button"
                    className="button secondary"
                    disabled={busy || !comment.trim()}
                    onClick={() =>
                      action("/" + current.id + "/request-changes", {
                        version: current.version,
                        comment,
                      })
                    }
                  >
                    Вернуть на уточнение
                  </button>
                  <button
                    type="button"
                    className="button primary"
                    disabled={busy}
                    onClick={() => setConfirm(true)}
                  >
                    {labels.approveLabel}
                  </button>
                </div>
                {!contract && (
                  <button
                    type="button"
                    className="text-button danger-text"
                    disabled={busy}
                    onClick={() => {
                      setRejecting(true);
                      setConfirm(false);
                    }}
                  >
                    Отклонить предложение
                  </button>
                )}
                {rejecting && !contract && (
                  <div className="rejection-box">
                    <h3>Окончательный отказ</h3>
                    <p>
                      Подготовка в этой карточке закроется. История останется у
                      вас и этого подрядчика. Затем можно создать копию заявки
                      другому исполнителю.
                    </p>
                    <Area
                      id="proposal-rejection-reason"
                      label="Причина отказа"
                      value={rejectionReason}
                      onChange={setRejectionReason}
                      maxLength={5000}
                      error={fields.comment}
                    />
                    <div className="form-actions">
                      <button
                        type="button"
                        className="button secondary danger-button"
                        disabled={busy || !rejectionReason.trim()}
                        onClick={() =>
                          action("/" + current.id + "/reject", {
                            version: current.version,
                            comment: rejectionReason,
                          })
                        }
                      >
                        Подтвердить отказ
                      </button>
                      <button
                        type="button"
                        className="button secondary"
                        disabled={busy}
                        onClick={() => setRejecting(false)}
                      >
                        Отмена
                      </button>
                    </div>
                  </div>
                )}
                {confirm && (
                  <div className="confirm-box">
                    <p>{approvalQuestion()}</p>
                    <button
                      type="button"
                      className="button primary"
                      disabled={busy}
                      onClick={() =>
                        action("/" + current.id + "/approve", {
                          version: current.version,
                        })
                      }
                    >
                      Подтвердить согласование
                    </button>
                    <button
                      type="button"
                      className="button secondary"
                      onClick={() => setConfirm(false)}
                    >
                      Отмена
                    </button>
                  </div>
                )}
              </div>
            )}
          {draft && (
            <p className="hint">
              {dirty ? "Есть несохранённые изменения. " : ""}Сначала сохраните
              изменения. Затем отправьте сохранённую версию заказчику.
            </p>
          )}
          {draft && contract && (
            <label className="check-label">
              <input
                type="checkbox"
                checked={confirm}
                onChange={(e) => setConfirm(e.target.checked)}
              />{" "}
              <span>Условия проверены, отправить заказчику</span>{" "}
            </label>
          )}
          {draft && (
            <button
              type="button"
              className="button primary"
              disabled={busy || editorBusy || dirty || (contract && !confirm)}
              onClick={() =>
                action("/" + current.id + "/submit", {
                  version: current.version,
                  ...(contract ? { contractorConfirmed: true } : {}),
                })
              }
            >
              Отправить {labels.documentName} на согласование
            </button>
          )}
        </>
      )}
      {!project.preparationClosed &&
        contractor &&
        (!latest || latest.state === "CHANGES_REQUESTED") && (
          <button
            type="button"
            className="button primary"
            disabled={busy}
            onClick={() => action("")}
          >
            {prepareLabel()}
          </button>
        )}
    </section>
  );
}
function DocumentEditor({
  errors,
  onFieldErrors,
  onBusy,
  onDirty,
  document,
  contract,
  project,
  onSaved,
  onError,
}: Readonly<{
  document: Doc;
  contract: boolean;
  project: Project;
  onSaved: () => Promise<void>;
  onError: (s: string) => void;
  errors: Record<string, string>;
  onFieldErrors: (e: Record<string, string>) => void;
  onBusy: (b: boolean) => void;
  onDirty: (b: boolean) => void;
}>) {
  const [text, setText] = useState(
      "contractText" in document
        ? document.contractText
        : document.estimateText,
    ),
    [terms, setTerms] = useState(
      "termsText" in document ? (document.termsText ?? "") : "",
    ),
    [price, setPrice] = useState(
      "totalPrice" in document ? document.totalPrice : null,
    ),
    [images, setImages] = useState(() =>
      ("images" in document ? document.images : []).map((image) => ({
        ...image,
        key: crypto.randomUUID(),
      })),
    ),
    [plans, setPlans] = useState<Plan[]>(
      "stages" in document ? document.stages : [],
    ),
    [busy, setBusy] = useState(false),
    [notice, setNotice] = useState("");
  async function save(e: SubmitEvent) {
    e.preventDefault();
    onError("");
    onFieldErrors({});
    onBusy(true);
    setBusy(true);
    try {
      const body = contract
        ? { version: document.version, contractText: text }
        : {
            version: document.version,
            estimateText: text,
            termsText: terms || null,
            totalPrice: price,
            images: images.map(({ url, caption }) => ({ url, caption })),
            stages: plans,
          };
      await request(
        base(project.id) +
          (contract ? "/contracts/" : "/proposals/") +
          document.id,
        "PUT",
        body,
      );
      await onSaved();
      onDirty(false);
      setNotice("Черновик сохранён");
    } catch (error_) {
      onError(errorMessage(error_));
      if (error_ instanceof ApiError) onFieldErrors(error_.fieldErrors);
    } finally {
      setBusy(false);
      onBusy(false);
    }
  }
  const setPlan = (index: number, p: Partial<Plan>) =>
    setPlans(plans.map((v, i) => (i === index ? { ...v, ...p } : v)));
  function saveLabel() {
    if (busy) {
      return "Сохраняем…";
    }
    return "Сохранить " + (contract ? "договор" : "предложение");
  }
  return (
    <form
      onSubmit={save}
      aria-busy={busy}
      onChangeCapture={() => onDirty(true)}
      onClickCapture={(e) => {
        if ((e.target as HTMLElement).closest('button[type="button"]'))
          onDirty(true);
      }}
    >
      {notice && <Notice>{notice}</Notice>}
      <Area
        error={errors[contract ? "contractText" : "estimateText"]}
        id={contract ? "contract-text" : "estimate-text"}
        label={
          contract
            ? "Текст договора подряда"
            : "Смета: работы, материалы и цены"
        }
        value={text}
        onChange={setText}
        maxLength={contract ? 50000 : 30000}
        hint={
          contract
            ? "Укажите стороны и реквизиты, объект, предмет работ, стоимость, порядок оплаты, сроки, сдачу и приёмку, ответственность, изменение и расторжение."
            : "Укажите работы, материалы и цены. Общую стоимость заполните отдельно — текст не рассчитывается автоматически."
        }
      />
      {!contract && (
        <>
          <Field
            error={errors.totalPrice}
            id="total-price"
            label="Общая стоимость, ₽"
            type="number"
            min="0.01"
            step="0.01"
            value={price ?? ""}
            onChange={(e) =>
              setPrice(e.target.value ? Number(e.target.value) : null)
            }
          />
          <Area
            id="proposal-terms"
            label="Условия и исключения"
            value={terms}
            onChange={setTerms}
            maxLength={10000}
          />
          <h3>Иллюстрации</h3>
          <p className="hint">
            Используйте HTTPS-ссылки на изображения, которыми можно делиться.
            Загрузка файлов появится позже.
          </p>
          {images.map((im, i) => (
            <fieldset key={im.key}>
              <legend>Иллюстрация {i + 1}</legend>
              <Field
                id={"image-url-" + i}
                label="Ссылка на изображение"
                type="url"
                pattern="https://.*"
                maxLength={2048}
                required
                value={im.url}
                onChange={(e) =>
                  setImages(
                    images.map((v, j) =>
                      i === j ? { ...v, url: e.target.value } : v,
                    ),
                  )
                }
              />
              <Field
                id={"image-caption-" + i}
                label="Подпись изображения"
                required
                maxLength={300}
                value={im.caption}
                onChange={(e) =>
                  setImages(
                    images.map((v, j) =>
                      i === j ? { ...v, caption: e.target.value } : v,
                    ),
                  )
                }
              />
              <button
                type="button"
                className="text-button"
                onClick={() => setImages(images.filter((_, j) => j !== i))}
              >
                Удалить изображение {i + 1}
              </button>
            </fieldset>
          ))}
          <button
            type="button"
            className="button secondary"
            disabled={images.length >= 10}
            onClick={() =>
              setImages([
                ...images,
                { key: crypto.randomUUID(), url: "", caption: "" },
              ])
            }
          >
            Добавить изображение
          </button>
          <h3>Этапы и сроки</h3>
          {errors.stages && (
            <p className="field-error" role="alert">
              {errors.stages}
            </p>
          )}
          {plans.map((p, i) => (
            <fieldset key={p.id}>
              <legend>Этап {i + 1}</legend>
              <Field
                id={"plan-name-" + i}
                label="Название этапа"
                required
                maxLength={120}
                value={p.name}
                onChange={(e) => setPlan(i, { name: e.target.value })}
              />
              <div className="field">
                <label htmlFor={"plan-type-" + i}>Вид работ</label>
                <select
                  id={"plan-type-" + i}
                  value={p.workType}
                  onChange={(e) =>
                    setPlan(i, { workType: e.target.value as Plan["workType"] })
                  }
                >
                  {Object.entries(kinds).map(([k, v]) => (
                    <option key={k} value={k}>
                      {v}
                    </option>
                  ))}
                </select>
              </div>
              <Area
                error={errors[`stages.${i}.expectedResult`]}
                id={"plan-result-" + i}
                label="Ожидаемый результат этапа"
                value={p.expectedResult}
                onChange={(v) => setPlan(i, { expectedResult: v })}
                maxLength={2000}
              />
              <div className="detail-grid">
                <Field
                  error={errors[`stages.${i}.plannedStart`]}
                  id={"plan-start-" + i}
                  label="Плановое начало"
                  type="date"
                  value={p.plannedStart ?? ""}
                  onChange={(e) =>
                    setPlan(i, { plannedStart: e.target.value || null })
                  }
                />
                <Field
                  error={errors[`stages.${i}.plannedFinish`]}
                  id={"plan-finish-" + i}
                  label="Плановое завершение"
                  type="date"
                  min={p.plannedStart ?? undefined}
                  value={p.plannedFinish ?? ""}
                  onChange={(e) =>
                    setPlan(i, { plannedFinish: e.target.value || null })
                  }
                />
              </div>
              <div className="form-actions">
                <button
                  type="button"
                  className="button secondary"
                  disabled={i === 0}
                  onClick={() => {
                    const a = [...plans];
                    [a[i - 1], a[i]] = [a[i], a[i - 1]];
                    setPlans(a);
                  }}
                  aria-label={"Поднять этап " + p.name}
                >
                  ↑
                </button>
                <button
                  type="button"
                  className="button secondary"
                  disabled={i === plans.length - 1}
                  onClick={() => {
                    const a = [...plans];
                    [a[i + 1], a[i]] = [a[i], a[i + 1]];
                    setPlans(a);
                  }}
                  aria-label={"Опустить этап " + p.name}
                >
                  ↓
                </button>
                <button
                  type="button"
                  className="text-button"
                  onClick={() => setPlans(plans.filter((_, j) => j !== i))}
                >
                  Удалить этап {i + 1}
                </button>
              </div>
            </fieldset>
          ))}
          <button
            type="button"
            className="button secondary"
            disabled={plans.length >= 100}
            onClick={() =>
              setPlans([
                ...plans,
                {
                  id: crypto.randomUUID(),
                  name: "",
                  workType: "OTHER",
                  expectedResult: "",
                  plannedStart: null,
                  plannedFinish: null,
                },
              ])
            }
          >
            Добавить этап в предложение
          </button>
        </>
      )}
      <div className="form-actions">
        <button type="submit" className="button primary" disabled={busy}>
          {saveLabel()}
        </button>
      </div>
    </form>
  );
}

function DocumentContent({
  document: current,
  requestData,
}: Readonly<{ document: Doc; requestData: RequestData }>) {
  if ("estimateText" in current)
    return (
      <>
        <div className="proposal-comparison">
          <div>
            <span>Бюджет заказчика</span>
            <strong>{rub(requestData.desiredBudget)}</strong>
          </div>
          <div>
            <span>Предложение подрядчика</span>
            <strong>{rub(current.totalPrice)}</strong>
          </div>
        </div>
        {requestData.desiredBudget != null &&
          current.totalPrice != null &&
          current.totalPrice > requestData.desiredBudget && (
            <Notice>
              Выше желаемого бюджета на{" "}
              {rub(current.totalPrice - requestData.desiredBudget)}. Решение о
              согласовании принимаете вы.
            </Notice>
          )}
        <h3>Работы, материалы и цены</h3>
        <p className="document-text">{current.estimateText}</p>
        {current.termsText && (
          <>
            <h3>Условия и исключения</h3>
            <p className="document-text">{current.termsText}</p>
          </>
        )}
        {current.images.map((im, i) => (
          <Illustration key={im.url + i} url={im.url} caption={im.caption} />
        ))}
        <h3>График строительства</h3>
        <p className="hint">
          Желаемые сроки: {day(requestData.desiredStart)} —{" "}
          {day(requestData.desiredFinish)}
        </p>
        <ol className="proposal-plan">
          {current.stages.map((p) => (
            <li key={p.id}>
              <strong>{p.name}</strong>
              <span>
                {day(p.plannedStart)} — {day(p.plannedFinish)}
              </span>
              <p>{p.expectedResult}</p>
              {((requestData.desiredStart &&
                p.plannedStart &&
                p.plannedStart < requestData.desiredStart) ||
                (requestData.desiredFinish &&
                  p.plannedFinish &&
                  p.plannedFinish > requestData.desiredFinish)) && (
                <span className="field-error">Выходит за желаемые сроки</span>
              )}
            </li>
          ))}
        </ol>
      </>
    );
  return (
    <>
      <p>
        Договор к согласованной смете. Цена и график доступны в предложении
        выше.
      </p>
      <p className="document-text">{current.contractText}</p>
    </>
  );
}

function FinancingChoice({
  source,
  error,
  onChange,
}: Readonly<{
  source: RequestData["financingSource"];
  error?: string;
  onChange: (source: NonNullable<RequestData["financingSource"]>) => void;
}>) {
  return (
    <fieldset>
      <legend>Источник финансирования</legend>
      {error && (
        <p className="field-error" role="alert">
          {error}
        </p>
      )}
      <div className="financing-options">
        {(
          [
            [
              "OWN_FUNDS",
              "Собственные средства",
              "Наличные или деньги на счёте",
            ],
            [
              "RSHB_RURAL_MORTGAGE",
              "Сельская ипотека РСХБ",
              "Можно подгрузить демонстрационные сведения о кредите",
            ],
          ] as const
        ).map(([key, label, hint]) => (
          <label
            aria-label={label}
            key={key}
            className={
              source === key ? "finance-option selected" : "finance-option"
            }
          >
            <input
              type="radio"
              name="financingSource"
              value={key}
              checked={source === key}
              onChange={() => onChange(key)}
              required
            />{" "}
            <span>
              <strong>{label}</strong>
              <small>{hint}</small>
            </span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}

function RequestSummary({ data }: Readonly<{ data: RequestData }>) {
  return (
    <dl className="request-summary">
      <Row label="Описание">{data.description}</Row>
      <Row label="Технология и материалы">
        {data.technologyRecommendation
          ? "Технология: нужна рекомендация"
          : data.technology}{" "}
        ·{" "}
        {data.materialsRecommendation
          ? "Материалы: нужна рекомендация"
          : data.materials}
      </Row>
      <Row label="Размеры">
        {data.lengthM && data.widthM
          ? `${data.lengthM} × ${data.widthM} м · `
          : ""}
        {data.areaM2 ? `${data.areaM2} м² · ` : ""}
        {data.floors} эт.
      </Row>
      <Row label="Комплектация">{data.completionScope}</Row>
      <Row label="Бюджет">{rub(data.desiredBudget)}</Row>
      <Row label="Участок">
        {data.location}
        {data.cadastralNumber && ` · ${data.cadastralNumber}`}
      </Row>
      <Row label="Желаемые сроки">
        {day(data.desiredStart)} — {day(data.desiredFinish)}
      </Row>
      <Row label="Финансирование">
        {data.financingSource === "OWN_FUNDS"
          ? "Собственные средства"
          : "Сельская ипотека РСХБ"}
      </Row>
    </dl>
  );
}
