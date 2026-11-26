import { CompletionPanel } from "./Completion";
import { ControlGates } from "./ControlGates";
import { useEffect, useState, type SubmitEvent } from "react";

import {
  api,
  errorMessage,
  type Account,
  type Project,
  request,
} from "./api/client";

import { Field, Notice, PageTitle } from "./components";
import { ContractorCard } from "./ContractorCard";
import { ActorLabel } from "./Participants";

import type { components } from "./api/schema";
import { Preparation } from "./Preparation";
import { StageList } from "./Stages";
const date = (value: string) =>
  new Intl.DateTimeFormat("ru-RU", {
    day: "numeric",
    month: "long",
    year: "numeric",
  }).format(new Date(value));

export function ProjectList({ account }: Readonly<{ account: Account }>) {
  const [projects, setProjects] = useState<Project[] | null>(null);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let current = true;
    setError("");
    api
      .list()
      .then((data) => {
        if (current) setProjects(data);
      })
      .catch((error_) => {
        if (current) setError(errorMessage(error_));
      });
    return () => {
      current = false;
    };
  }, [attempt]);
  const customer = account.role === "CUSTOMER";
  function renderProjects() {
    if (error) {
      return (
        <Notice error>
          {error}{" "}
          <button
            type="button"
            className="text-button"
            onClick={() => setAttempt(attempt + 1)}
          >
            Повторить загрузку
          </button>
        </Notice>
      );
    }
    if (projects === null) {
      return <Notice>Загружаем объекты…</Notice>;
    }
    if (projects.length === 0) {
      return (
        <section className="panel empty-state">
          <span className="empty-icon" aria-hidden="true">
            ⌂
          </span>
          <h2>
            {customer
              ? "У каждого дома есть начало"
              : "Вас пока не назначили на объект"}
          </h2>
          <p>
            {customer
              ? "Создайте первую карточку: укажите название и адрес. Затем сможете дать подрядчику доступ."
              : `Попросите заказчика назначить вас по email ${account.email}. После отправки заявки карточка появится здесь.`}
          </p>
          {customer && (
            <a className="button primary" href="#/projects/new">
              Создать первую карточку
            </a>
          )}
        </section>
      );
    }
    return (
      <>
        <p className="list-count">Объектов: {projects.length}</p>
        <div className="project-grid">
          {projects.map((project) => {
            function contractorStatus() {
              if (project.preparationClosed) {
                return "Предложение отклонено";
              }
              if (project.contractorId) {
                return "Подрядчик назначен";
              }
              return "Без подрядчика";
            }
            return (
              <a
                className="panel project-card"
                href={`#/projects/${project.id}`}
                key={project.id}
              >
                <div className="card-top">
                  <span className="object-icon" aria-hidden="true">
                    ⌂
                  </span>
                  <span
                    className={`badge ${project.contractorId ? "assigned" : ""}`}
                  >
                    {contractorStatus()}
                  </span>
                </div>
                <h2>{project.name}</h2>
                <p>{project.address}</p>
                <div className="card-footer">
                  <span>Создан {date(project.createdAt)}</span>
                  <span aria-hidden="true">↗</span>
                </div>
              </a>
            );
          })}
        </div>
      </>
    );
  }
  return (
    <>
      <div className="heading-row">
        <PageTitle
          eyebrow={customer ? "Кабинет заказчика" : "Кабинет подрядчика"}
          description={
            customer
              ? "Ваши дома и участники строительства. Начните с карточки объекта."
              : "Здесь собраны объекты, на которые вас назначили заказчики."
          }
        >
          Мои объекты
        </PageTitle>
        {customer && (
          <a href="#/projects/new" className="button primary">
            + Создать карточку
          </a>
        )}
      </div>
      {renderProjects()}
    </>
  );
}

export function CreateProject() {
  const [name, setName] = useState("");
  const [address, setAddress] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [invalid, setInvalid] = useState<Record<string, string>>({});
  async function submit(event: SubmitEvent) {
    event.preventDefault();
    const fields: Record<string, string> = {};
    if (!name.trim()) fields.name = "Укажите название дома";
    if (!address.trim()) fields.address = "Укажите адрес дома";
    setInvalid(fields);
    if (Object.keys(fields).length) return;
    setBusy(true);
    setError("");
    try {
      const project = await api.create({ name, address });
      window.location.hash = `/projects/${project.id}?created`;
    } catch (error_) {
      setError(errorMessage(error_));
    } finally {
      setBusy(false);
    }
  }
  return (
    <>
      <a className="back-link" href="#/projects">
        ← Мои объекты
      </a>
      <PageTitle
        eyebrow="Новый объект"
        description="Дайте дому понятное название и укажите адрес. Подрядчика можно назначить после создания."
      >
        Создать карточку дома
      </PageTitle>
      <section className="panel form-panel">
        {error && <Notice error>{error}</Notice>}
        <form onSubmit={submit} aria-busy={busy}>
          <Field
            id="name"
            label="Название дома"
            hint="Например, Дом в Берёзовке"
            required
            maxLength={120}
            value={name}
            onChange={(event) => setName(event.target.value)}
            error={invalid.name}
          />
          <Field
            id="address"
            label="Адрес дома"
            hint="Населённый пункт, улица и номер дома или участка"
            required
            maxLength={300}
            value={address}
            onChange={(event) => setAddress(event.target.value)}
            error={invalid.address}
          />
          <div className="form-actions">
            <button type="submit" className="button primary" disabled={busy}>
              {busy ? "Создаём…" : "Создать карточку"}
            </button>
            <a className="button secondary" href="#/projects">
              Отмена
            </a>
          </div>
        </form>
      </section>
    </>
  );
}

export function ProjectDetail({
  id,
  account,
  created,
}: Readonly<{
  id: string;
  account: Account;
  created: boolean;
}>) {
  const [project, setProject] = useState<Project | null>(null);
  const [error, setError] = useState("");
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    let current = true;
    setProject(null);
    setError("");
    api
      .get(id)
      .then((data) => {
        if (current) setProject(data);
      })
      .catch((error_) => {
        if (current) setError(errorMessage(error_));
      });
    return () => {
      current = false;
    };
  }, [id, attempt]);
  function renderProject() {
    if (error) {
      return (
        <>
          <PageTitle
            eyebrow="Карточка дома"
            description="Проверьте ссылку или вернитесь к своим объектам."
          >
            Не удалось открыть карточку
          </PageTitle>
          <Notice error>
            {error}{" "}
            <button
              type="button"
              className="text-button"
              onClick={() => setAttempt(attempt + 1)}
            >
              Повторить загрузку
            </button>
          </Notice>
        </>
      );
    }
    if (!project) {
      return <Notice>Загружаем карточку…</Notice>;
    }
    return (
      <>
        <PageTitle eyebrow="Карточка дома" description={project.address}>
          {project.name}
        </PageTitle>
        {created && (
          <Notice>
            {project.contractorId
              ? "Карточка создана."
              : "Карточка создана. Теперь можно назначить подрядчика."}
          </Notice>
        )}
        <div className="detail-grid participant-overview">
          <section className="panel object-overview">
            <p className="eyebrow">Дом и заказчик</p>
            <h2>Об объекте</h2>
            <ActorLabel snapshot={project.ownerPresentation} role="CUSTOMER" />
            <dl>
              <div>
                <dt>Адрес</dt>
                <dd>{project.address}</dd>
              </div>
              <div>
                <dt>Дата создания</dt>
                <dd>{date(project.createdAt)}</dd>
              </div>
            </dl>
            <p className="privacy-note">
              Данные видны заказчику и назначенной команде подрядчика.
            </p>
          </section>
          {project.houseProfile && (
            <section className="panel house-profile" aria-label="Паспорт дома">
              <p className="eyebrow">Паспорт дома</p>
              <h2>Параметры объекта</h2>
              <dl>
                <div>
                  <dt>Площадь</dt>
                  <dd>{project.houseProfile.areaSqm} м²</dd>
                </div>
                <div>
                  <dt>Этажность</dt>
                  <dd>{project.houseProfile.floors}</dd>
                </div>
                <div>
                  <dt>Стены</dt>
                  <dd>{project.houseProfile.wallMaterial}</dd>
                </div>
                <div>
                  <dt>Фундамент</dt>
                  <dd>{project.houseProfile.foundationType}</dd>
                </div>
                <div>
                  <dt>Кровля</dt>
                  <dd>{project.houseProfile.roofType}</dd>
                </div>
                <div>
                  <dt>Плановый срок</dt>
                  <dd>{project.houseProfile.plannedCompletion}</dd>
                </div>
              </dl>
              <p className="hint">Демонстрационные параметры объекта.</p>
            </section>
          )}
          {project.contractorId ? (
            <ContractorCard
              project={project}
              account={account}
              onChanged={setProject}
            />
          ) : (
            <section className="panel">
              <h2>Подрядчик</h2>
              {account.id === project.customerId ? (
                <AssignContractor id={project.id} onAssigned={setProject} />
              ) : (
                <p>Подрядчик пока не назначен.</p>
              )}
            </section>
          )}
        </div>
        {project.processMode === "PROPOSAL_REQUIRED" && (
          <Preparation
            project={project}
            account={account}
            onChanged={setProject}
          />
        )}
        {(project.processMode !== "PROPOSAL_REQUIRED" ||
          project.approvedProposalId) && (
          <>
            <ControlGates projectId={project.id} account={account} />
            <StageList
              key={project.approvedProposalId ?? "legacy"}
              project={project}
              account={account}
            />
            <CompletionPanel
              project={project}
              account={account}
              onChanged={setProject}
            />
          </>
        )}
      </>
    );
  }
  return (
    <>
      <a className="back-link" href="#/projects">
        ← Мои объекты
      </a>
      {renderProject()}
    </>
  );
}

function AssignContractor({
  id,
  onAssigned,
}: Readonly<{
  id: string;
  onAssigned: (project: Project) => void;
}>) {
  const [email, setEmail] = useState("");
  const [confirm, setConfirm] = useState(false);
  const [preview, setPreview] = useState<
    components["schemas"]["ContractorPreview"] | null
  >(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  async function submit(event: SubmitEvent) {
    event.preventDefault();
    if (!confirm) {
      setBusy(true);
      setError("");
      try {
        setPreview(
          await request<components["schemas"]["ContractorPreview"]>(
            `/projects/${id}/contractor-preview?email=${encodeURIComponent(email.trim())}`,
          ),
        );
        setConfirm(true);
      } catch (error_) {
        setError(errorMessage(error_));
      } finally {
        setBusy(false);
      }
      return;
    }
    setBusy(true);
    setError("");
    try {
      onAssigned(await api.assign(id, { email }));
    } catch (error_) {
      setError(errorMessage(error_));
      setConfirm(false);
    } finally {
      setBusy(false);
    }
  }
  function assignLabel() {
    if (busy) {
      return "Назначаем…";
    }
    if (confirm) {
      return "Подтвердить назначение";
    }
    return "Назначить подрядчика";
  }
  return (
    <>
      <p>
        Дайте подрядчику доступ по email его зарегистрированного аккаунта. В
        карточке может быть один подрядчик.
      </p>
      {error && <Notice error>{error}</Notice>}
      <form onSubmit={submit} aria-busy={busy}>
        <Field
          id="contractor-email"
          label="Email подрядчика"
          type="email"
          required
          maxLength={320}
          value={email}
          readOnly={confirm}
          onChange={(event) => setEmail(event.target.value)}
          error={error || undefined}
        />
        {confirm && (
          <Notice>
            Открыть доступ для{" "}
            <strong>{preview?.organization.displayName}</strong>?{" "}
            {preview && (
              <>
                <span>ИНН {preview.organization.inn}</span>
                <ActorLabel snapshot={preview.primaryContact} />
              </>
            )}
            Основной контакт: {email.trim().toLowerCase()}. Проверьте реквизиты:
            заменить подрядчика в этой карточке нельзя.
          </Notice>
        )}
        <div className="form-actions">
          <button type="submit" className="button primary" disabled={busy}>
            {assignLabel()}
          </button>
          {confirm && (
            <button
              className="button secondary"
              type="button"
              disabled={busy}
              onClick={() => setConfirm(false)}
            >
              Изменить email
            </button>
          )}
        </div>
      </form>
    </>
  );
}
