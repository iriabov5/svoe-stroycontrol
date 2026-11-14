import { useEffect, useState, useRef, type SubmitEvent } from "react";

import {
  api,
  request,
  errorMessage,
  ApiError,
  type Account,
} from "./api/client";

import type { components } from "./api/schema";
import { Field, Notice, PageTitle } from "./components";
type Org = components["schemas"]["Organization"];
type Member = components["schemas"]["OrganizationMember"];
type Invitation = components["schemas"]["Invitation"];
type Mine = components["schemas"]["MyOrganization"];

export function Profile({
  account,
  onAccount,
  onContinue,
  embedded = false,
}: Readonly<{
  account: Account;
  onAccount: (a: Account) => void;
  onContinue: () => void;
  embedded?: boolean;
}>) {
  const [name, setName] = useState(account.displayName ?? ""),
    [busy, setBusy] = useState(false),
    [error, setError] = useState(""),
    [saved, setSaved] = useState(""),
    [fields, setFields] = useState<Record<string, string>>({});
  async function save(e: SubmitEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    setFields({});
    try {
      onAccount(
        await request<Account>("/auth/profile", "PATCH", {
          version: account.profileVersion ?? 0,
          displayName: name,
        }),
      );
      setSaved("Имя сохранено");
    } catch (error_) {
      setError(errorMessage(error_));
      if (error_ instanceof ApiError) setFields(error_.fieldErrors);
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      {!embedded && (
        <a className="back-link" href="#/projects">
          ← Мои объекты
        </a>
      )}
      <PageTitle
        eyebrow="Личный кабинет"
        description="Ваше имя в обсуждениях и участники строительной команды."
      >
        Мой профиль
      </PageTitle>
      {!account.displayName && (
        <Notice>
          Укажите имя перед отправкой документов и сообщений. Читать свои
          объекты можно и без заполнения.
        </Notice>
      )}
      <section className="panel form-panel">
        <h2>Как вас представлять</h2>
        {error && <Notice error>{error}</Notice>}
        {saved && <Notice>{saved}</Notice>}
        <form onSubmit={save} aria-busy={busy}>
          <Field
            id="profile-name"
            label={account.role === "CUSTOMER" ? "Ваше имя" : "Имя и фамилия"}
            value={name}
            onChange={(e) => setName(e.target.value)}
            required
            minLength={2}
            maxLength={120}
            autoComplete="name"
            error={fields.displayName}
          />
          <p className="hint">Email для входа: {account.email}</p>
          <div className="form-actions">
            <button type="submit" className="button primary" disabled={busy}>
              {busy ? "Сохраняем…" : "Сохранить имя"}
            </button>
            {account.displayName && (
              <button
                type="button"
                className="button secondary"
                onClick={onContinue}
              >
                Вернуться к работе
              </button>
            )}
          </div>
        </form>
      </section>
      {!embedded && account.role === "CONTRACTOR" && account.displayName && (
        <OrganizationWorkspace onAccount={onAccount} />
      )}
    </>
  );
}

function OrganizationWorkspace({
  onAccount,
}: Readonly<{
  onAccount: (a: Account) => void;
}>) {
  const [mine, setMine] = useState<Mine | null>(null),
    [members, setMembers] = useState<Member[]>([]),
    [invitations, setInvitations] = useState<Invitation[]>([]),
    [personal, setPersonal] = useState<Invitation[]>([]),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [busy, setBusy] = useState(false),
    [attempt, setAttempt] = useState(0),
    [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  async function load() {
    const current = await request<Mine>("/organizations/me");
    setMine(current);
    setPersonal(await request<Invitation[]>("/auth/invitations"));
    if (current.organization) {
      setMembers(
        await request<Member[]>(
          `/organizations/${current.organization.id}/members`,
        ),
      );
      setInvitations(
        current.membership?.role === "OWNER"
          ? await request<Invitation[]>(
              `/organizations/${current.organization.id}/invitations`,
            )
          : [],
      );
    } else {
      setMembers([]);
      setInvitations([]);
    }
  }
  useEffect(() => {
    let active = true;
    load().catch((error_) => {
      if (active) setError(errorMessage(error_));
    });
    return () => {
      active = false;
    };
  }, [attempt]);
  async function run(action: () => Promise<unknown>, message: string) {
    setBusy(true);
    setError("");
    setFieldErrors({});
    try {
      await action();
      await load();
      onAccount(await api.me());
      setNotice(message);
      return true;
    } catch (error_) {
      setError(errorMessage(error_));
      if (error_ instanceof ApiError) setFieldErrors(error_.fieldErrors);
      return false;
    } finally {
      setBusy(false);
    }
  }
  function renderOrganization() {
    if (!mine) {
      return !error && <Notice>Загружаем организацию…</Notice>;
    }
    if (!mine.organization) {
      return (
        <OrgForm
          errors={fieldErrors}
          key="create"
          onSave={(body) =>
            run(
              () => request("/organizations", "POST", body),
              "Организация создана",
            )
          }
          busy={busy}
        />
      );
    }
    return (
      <>
        <section className="panel">
          <p className="eyebrow">Ваш подрядчик</p>
          <h3>{mine.organization.displayName}</h3>
          <p>
            {mine.membership?.position} ·{" "}
            {mine.membership?.role === "OWNER"
              ? "Руководитель профиля"
              : "Сотрудник"}
          </p>
          <p className="hint">
            Реквизиты указаны подрядчиком. Проверка ФНС в сервисе не
            выполняется.
          </p>
          {mine.membership?.role === "OWNER" ? (
            <details>
              <summary>Изменить реквизиты</summary>
              <OrgForm
                errors={fieldErrors}
                key={mine.organization.version}
                org={mine.organization}
                onSave={(body) =>
                  run(
                    () =>
                      request(
                        `/organizations/${mine.organization!.id}`,
                        "PATCH",
                        body,
                      ),
                    "Реквизиты сохранены",
                  )
                }
                busy={busy}
              />
            </details>
          ) : (
            <dl className="organization-details">
              <div>
                <dt>Полное название</dt>
                <dd>{mine.organization.legalName}</dd>
              </div>
              <div>
                <dt>ИНН</dt>
                <dd>{mine.organization.inn}</dd>
              </div>
              <div>
                <dt>Адрес</dt>
                <dd>{mine.organization.address}</dd>
              </div>
            </dl>
          )}
        </section>
        <section className="panel">
          <h3>Сотрудники организации</h3>
          <p className="hint">
            Членство не открывает все стройки. Два доступа выдаются отдельно в
            команде конкретного объекта.
          </p>
          <ul className="member-list">
            {members.map((m) => (
              <MemberRow
                key={`${m.id}-${m.version}`}
                member={m}
                owner={mine.membership?.role === "OWNER"}
                busy={busy}
                run={run}
              />
            ))}
          </ul>
          {mine.membership?.role === "OWNER" && (
            <>
              <InviteForm
                busy={busy}
                onInvite={(body) =>
                  run(
                    () =>
                      request(
                        `/organizations/${mine.organization!.id}/invitations`,
                        "POST",
                        body,
                      ),
                    "Приглашение появится в личном профиле сотрудника",
                  )
                }
              />
              {invitations.length > 0 && (
                <details>
                  <summary>Отправленные приглашения</summary>
                  <ul className="member-list">
                    {invitations.map((i) => (
                      <li key={i.id}>
                        <strong>
                          {i.recipientName || "Имя пока не заполнено"}
                        </strong>
                        <span>
                          {i.position} ·{" "}
                          {
                            {
                              PENDING: "Ждём принятия",
                              ACCEPTED: "Принято",
                              DECLINED: "Отклонено",
                              CANCELLED: "Отменено",
                            }[i.state]
                          }
                        </span>
                        <small>{i.recipientEmail}</small>
                        {i.state === "PENDING" && (
                          <button
                            type="button"
                            className="text-button"
                            disabled={busy}
                            onClick={() =>
                              run(
                                () =>
                                  request(
                                    `/organizations/invitations/${i.id}/cancel`,
                                    "POST",
                                    { version: i.version },
                                  ),
                                "Приглашение отменено",
                              )
                            }
                          >
                            Отменить приглашение
                          </button>
                        )}
                      </li>
                    ))}
                  </ul>
                </details>
              )}
            </>
          )}
        </section>
      </>
    );
  }
  return (
    <section
      className="organization-workspace"
      aria-label="Организация и команда"
    >
      <div className="heading-row">
        <div>
          <p className="eyebrow">Кабинет подрядчика</p>
          <h2>Организация и команда</h2>
          <p>
            Работайте самостоятельно или пригласите коллег с личными аккаунтами.
          </p>
        </div>
      </div>
      {error && (
        <Notice error>
          {error}{" "}
          <button
            type="button"
            className="text-button"
            onClick={() => setAttempt((a) => a + 1)}
          >
            Обновить профиль организации
          </button>
        </Notice>
      )}
      {notice && <Notice>{notice}</Notice>}
      {personal.map((i) => (
        <section key={i.id} className="panel invitation-card">
          <p className="eyebrow">Приглашение в команду</p>
          <h3>{i.organizationName}</h3>
          <p>
            Ваша должность: {i.position}. Доступ к стройкам руководитель выдаст
            отдельно.
          </p>
          <div className="form-actions">
            <button
              type="button"
              className="button primary"
              disabled={busy}
              onClick={() =>
                run(
                  () =>
                    request(
                      `/organizations/invitations/${i.id}/accept`,
                      "POST",
                      { version: i.version },
                    ),
                  "Приглашение принято",
                )
              }
            >
              Принять приглашение
            </button>
            <button
              type="button"
              className="button secondary"
              disabled={busy}
              onClick={() =>
                run(
                  () =>
                    request(
                      `/organizations/invitations/${i.id}/decline`,
                      "POST",
                      { version: i.version },
                    ),
                  "Приглашение отклонено",
                )
              }
            >
              Отклонить приглашение
            </button>
          </div>
        </section>
      ))}
      {renderOrganization()}
    </section>
  );
}

type Run = (action: () => Promise<unknown>, notice: string) => Promise<boolean>;
function MemberRow({
  member,
  owner,
  busy,
  run,
}: Readonly<{
  member: Member;
  owner: boolean;
  busy: boolean;
  run: Run;
}>) {
  const [position, setPosition] = useState(member.position),
    [confirm, setConfirm] = useState(false);
  return (
    <li>
      <strong>{member.displayName || "Профиль без имени"}</strong>
      <span>
        {member.position}
        {member.role === "OWNER" ? " · руководитель" : ""}
      </span>
      <details>
        <summary>Контакт и должность</summary>
        <p className="hint">{member.email}</p>
        {owner && (
          <form
            onSubmit={(e) => {
              e.preventDefault();
              run(
                () =>
                  request(
                    `/organizations/${member.organizationId}/members/${member.id}`,
                    "PATCH",
                    { version: member.version, position },
                  ),
                "Должность сохранена",
              );
            }}
          >
            <Field
              id={`member-position-${member.id}`}
              label="Должность сотрудника"
              value={position}
              onChange={(e) => setPosition(e.target.value)}
              required
              maxLength={120}
            />
            <button type="submit" className="button secondary" disabled={busy}>
              Сохранить должность
            </button>
          </form>
        )}
        {owner && member.role !== "OWNER" && (
          <>
            <button
              type="button"
              className="text-button"
              disabled={busy}
              onClick={() => {
                if (!confirm) {
                  setConfirm(true);
                  return;
                }
                run(
                  () =>
                    request(
                      `/organizations/${member.organizationId}/members/${member.id}?expectedVersion=${member.version}`,
                      "DELETE",
                    ),
                  "Доступ сотрудника отозван",
                );
              }}
            >
              {confirm ? "Подтвердить исключение" : "Исключить сотрудника"}
            </button>
            {confirm && (
              <p className="hint">
                Сотрудник потеряет доступ ко всем стройкам организации. История
                сохранится.
              </p>
            )}
          </>
        )}
      </details>
    </li>
  );
}

function OrgForm({
  errors = {},
  org,
  onSave,
  busy,
}: Readonly<{
  errors?: Record<string, string>;
  org?: Org;
  onSave: (body: unknown) => Promise<boolean>;
  busy: boolean;
}>) {
  const [type, setType] = useState(org?.type ?? "LEGAL_ENTITY"),
    [name, setName] = useState(org?.displayName ?? ""),
    [legal, setLegal] = useState(org?.legalName ?? ""),
    [inn, setInn] = useState(org?.inn ?? ""),
    [address, setAddress] = useState(org?.address ?? ""),
    [phone, setPhone] = useState(org?.phone ?? "");
  const key = useRef({ payload: "", id: "" });
  async function submit(e: SubmitEvent) {
    e.preventDefault();
    const fields = {
      displayName: name,
      legalName: legal || name,
      address,
      phone: phone || null,
    };
    if (org) {
      await onSave({ version: org.version, ...fields });
      return;
    }
    const body = { ...fields, type, inn };
    const value = JSON.stringify(body);
    if (key.current.payload !== value)
      key.current = { payload: value, id: crypto.randomUUID() };
    await onSave({ ...body, idempotencyKey: key.current.id });
  }
  function innHint() {
    if (org) {
      return "ИНН сохраняет идентичность организации и не меняется в этой форме.";
    }
    if (type === "LEGAL_ENTITY") {
      return "10 цифр, проверим контрольную цифру.";
    }
    return "12 цифр, проверим контрольные цифры.";
  }
  function saveLabel() {
    if (busy) {
      return "Сохраняем…";
    }
    if (org) {
      return "Сохранить реквизиты";
    }
    return "Создать организацию";
  }
  return (
    <form
      className="panel organization-form"
      onSubmit={submit}
      aria-busy={busy}
    >
      <h3>{org ? "Реквизиты подрядчика" : "Представьте свою организацию"}</h3>
      {!org && (
        <div className="field">
          <label htmlFor="organization-type">Вид подрядчика</label>
          <select
            id="organization-type"
            value={type}
            onChange={(e) => setType(e.target.value as typeof type)}
          >
            <option value="LEGAL_ENTITY">Организация</option>
            <option value="SOLE_TRADER">Индивидуальный предприниматель</option>
          </select>
        </div>
      )}
      <Field
        id="organization-name"
        error={errors.displayName}
        label="Название для показа"
        required
        maxLength={160}
        value={name}
        onChange={(e) => setName(e.target.value)}
        hint="Например, ООО «Тёплый дом»"
      />
      <Field
        id="organization-legal"
        error={errors.legalName}
        label="Полное название"
        maxLength={500}
        value={legal}
        onChange={(e) => setLegal(e.target.value)}
        hint="Если не отличается, можно оставить пустым — используем название для показа."
      />
      <Field
        id="organization-inn"
        error={errors.inn}
        label="ИНН"
        required
        inputMode="numeric"
        pattern={type === "LEGAL_ENTITY" ? "[0-9]{10}" : "[0-9]{12}"}
        maxLength={12}
        value={inn}
        readOnly={Boolean(org)}
        onChange={(e) => setInn(e.target.value)}
        hint={innHint()}
      />
      <Field
        id="organization-address"
        error={errors.address}
        label="Адрес организации"
        required
        maxLength={500}
        value={address}
        onChange={(e) => setAddress(e.target.value)}
      />
      <Field
        id="organization-phone"
        error={errors.phone}
        label="Контактный телефон"
        type="tel"
        maxLength={40}
        value={phone}
        onChange={(e) => setPhone(e.target.value)}
        hint="Необязательно"
      />
      <button type="submit" className="button primary" disabled={busy}>
        {saveLabel()}
      </button>
      <p className="hint">
        Реквизиты указываете вы. Сервис не подтверждает регистрацию компании или
        полномочия представителя.
      </p>
    </form>
  );
}
function InviteForm({
  busy,
  onInvite,
}: Readonly<{
  busy: boolean;
  onInvite: (body: unknown) => Promise<boolean>;
}>) {
  const [email, setEmail] = useState(""),
    [position, setPosition] = useState("");
  const key = useRef({ payload: "", id: "" });
  async function submit(e: SubmitEvent) {
    e.preventDefault();
    const body = { recipientEmail: email.trim().toLowerCase(), position };
    const value = JSON.stringify(body);
    if (key.current.payload !== value)
      key.current = { payload: value, id: crypto.randomUUID() };
    if (await onInvite({ ...body, idempotencyKey: key.current.id })) {
      setEmail("");
      setPosition("");
    }
  }
  return (
    <details className="invite-form">
      <summary>Пригласить сотрудника</summary>
      <form onSubmit={submit} aria-busy={busy}>
        <p>
          Сотрудник сначала регистрируется с ролью «Подрядчик», затем принимает
          приглашение в своём профиле. Письмо отправлять не будем.
        </p>
        <Field
          id="invite-email"
          label="Email сотрудника"
          type="email"
          required
          value={email}
          onChange={(e) => setEmail(e.target.value)}
        />
        <Field
          id="invite-position"
          label="Должность"
          required
          maxLength={120}
          value={position}
          onChange={(e) => setPosition(e.target.value)}
          hint="Например, Юрист или Прораб. Доступы к стройке выдаются отдельно."
        />
        <button type="submit" className="button primary" disabled={busy}>
          Пригласить в организацию
        </button>
      </form>
    </details>
  );
}
