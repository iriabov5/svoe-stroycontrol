import { useEffect, useState, type SubmitEvent } from "react";

import {
  api,
  request,
  errorMessage,
  type Account,
  type Project,
} from "./api/client";

import type { components } from "./api/schema";
import { Notice } from "./components";
import { ActorLabel } from "./Participants";
type Team = components["schemas"]["ProjectTeam"];
type Member = components["schemas"]["OrganizationMember"];
type Mine = components["schemas"]["MyOrganization"];
type Assignment = components["schemas"]["TeamAssignment"];
export function ContractorCard({
  project,
  account,
  onChanged,
}: Readonly<{
  project: Project;
  account: Account;
  onChanged: (p: Project) => void;
}>) {
  const org = project.contractorOrganization;
  const [mine, setMine] = useState<Mine | null>(null),
    [error, setError] = useState(""),
    [busy, setBusy] = useState(false);
  useEffect(() => {
    if (!org && account.id === project.contractorId)
      request<Mine>("/organizations/me")
        .then(setMine)
        .catch((error_) => setError(errorMessage(error_)));
  }, [project.id, org, account.id, project.contractorId]);
  async function connect() {
    setBusy(true);
    setError("");
    try {
      onChanged(
        await request<Project>(
          `/projects/${project.id}/contractor-organization`,
          "POST",
          { organizationId: mine?.organization?.id },
        ),
      );
    } catch (error_) {
      setError(errorMessage(error_));
    } finally {
      setBusy(false);
    }
  }
  return (
    <section className="panel contractor-card">
      <p className="eyebrow">Участник строительства</p>
      <h2>Подрядчик</h2>
      <span className="badge assigned">Подрядчик назначен</span>
      <ActorLabel snapshot={project.primaryContact} />
      {!project.primaryContact?.displayName && (
        <p className="hint">Основной контакт пока не заполнил имя.</p>
      )}
      <p className="hint">
        {project.processMode === "PROPOSAL_REQUIRED" &&
        !project.requestSubmitted
          ? "Доступ откроется после отправки заявки."
          : "Доступ открыт назначенным участникам стройки."}
      </p>
      {error && <Notice error>{error}</Notice>}
      {!org ? (
        <>
          <p className="hint">
            Организация к этой карточке пока не подключена.
          </p>
          {account.id === project.contractorId &&
            (mine?.organization && mine.membership?.role === "OWNER" ? (
              <button
                type="button"
                className="button secondary"
                disabled={busy}
                onClick={() => connect()}
              >
                Подключить команду
              </button>
            ) : (
              <a className="button secondary" href="#/profile">
                Заполнить профиль организации
              </a>
            ))}
        </>
      ) : (
        <details className="contractor-details">
          <summary>Подробнее об организации</summary>
          <dl className="organization-details">
            <div>
              <dt>Полное название</dt>
              <dd>{org.legalName}</dd>
            </div>
            <div>
              <dt>ИНН</dt>
              <dd>{org.inn}</dd>
            </div>
            <div>
              <dt>Адрес</dt>
              <dd>{org.address}</dd>
            </div>
            {org.phone && (
              <div>
                <dt>Телефон</dt>
                <dd>{org.phone}</dd>
              </div>
            )}
          </dl>
          <p className="hint">
            Реквизиты указаны подрядчиком, без проверки ФНС.
          </p>
          <TeamPanel project={project} onChanged={onChanged} />
        </details>
      )}
    </section>
  );
}
function TeamPanel({
  project,
  onChanged,
}: Readonly<{
  project: Project;
  onChanged: (p: Project) => void;
}>) {
  const [team, setTeam] = useState<Team | null>(null),
    [members, setMembers] = useState<Member[]>([]),
    [editing, setEditing] = useState(false),
    [grants, setGrants] = useState<Record<string, Assignment>>({}),
    [error, setError] = useState(""),
    [notice, setNotice] = useState(""),
    [busy, setBusy] = useState(false),
    [attempt, setAttempt] = useState(0);
  const manage = project.permissions?.canManageTeam ?? false;
  const root = `/projects/${project.id}/contractor-team`;
  useEffect(() => {
    let active = true;
    request<Team>(root)
      .then((data) => {
        if (active) setTeam(data);
      })
      .catch((error_) => {
        if (active) setError(errorMessage(error_));
      });
    if (manage && project.contractorOrganizationId)
      request<Member[]>(
        `/organizations/${project.contractorOrganizationId}/members`,
      )
        .then((data) => {
          if (active) setMembers(data);
        })
        .catch((error_) => {
          if (active) setError(errorMessage(error_));
        });
    return () => {
      active = false;
    };
  }, [root, manage, project.contractorOrganizationId, attempt]);
  function edit() {
    setGrants(
      Object.fromEntries(
        (team?.members ?? [])
          .filter((m) => m.role !== "OWNER")
          .map((m) => [
            m.id,
            { membershipId: m.id, documents: m.documents, works: m.works },
          ]),
      ),
    );
    setEditing(true);
    setError("");
    setNotice("");
  }
  function change(id: string, field: "documents" | "works", value: boolean) {
    setGrants((old) => ({
      ...old,
      [id]: {
        ...(old[id] ?? { membershipId: id, documents: false, works: false }),
        [field]: value,
      },
    }));
  }
  async function save(e: SubmitEvent) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      const next = await request<Team>(root, "PUT", {
        version: team?.version ?? 0,
        members: Object.values(grants).filter((m) => m.documents || m.works),
      });
      setTeam(next);
      setEditing(false);
      setNotice("Команда стройки сохранена");
      onChanged(await api.get(project.id));
    } catch (error_) {
      setError(errorMessage(error_));
    } finally {
      setBusy(false);
    }
  }
  function renderTeam() {
    if (!team && !error) {
      return <Notice>Загружаем команду…</Notice>;
    }
    if (editing) {
      return (
        <form onSubmit={save} aria-busy={busy}>
          <p className="hint">
            Сотрудник с обоими снятыми флажками не увидит объект. Должность сама
            по себе не даёт доступ.
          </p>
          {members.filter((m) => m.role !== "OWNER").length === 0 && (
            <p>
              Сначала пригласите коллег в{" "}
              <a href="#/profile">профиле организации</a>.
            </p>
          )}
          {members
            .filter((m) => m.role !== "OWNER")
            .map((m) => (
              <fieldset className="team-grant" key={m.id} disabled={busy}>
                <legend>
                  {m.displayName || "Имя не заполнено"} · {m.position}
                </legend>
                <label>
                  <input
                    type="checkbox"
                    checked={grants[m.id]?.documents ?? false}
                    onChange={(e) =>
                      change(m.id, "documents", e.target.checked)
                    }
                  />{" "}
                  <span>Документы</span>{" "}
                </label>
                <label>
                  <input
                    type="checkbox"
                    checked={grants[m.id]?.works ?? false}
                    onChange={(e) => change(m.id, "works", e.target.checked)}
                  />{" "}
                  <span>Работы</span>{" "}
                </label>
              </fieldset>
            ))}
          <div className="form-actions">
            <button type="submit" className="button primary" disabled={busy}>
              Сохранить команду
            </button>
            <button
              type="button"
              className="button secondary"
              onClick={() => setEditing(false)}
              disabled={busy}
            >
              Отмена
            </button>
          </div>
        </form>
      );
    }
    return (
      <>
        <ul className="member-list">
          {team?.members.map((m) => (
            <li key={m.id}>
              <strong>{m.displayName || "Имя не заполнено"}</strong>
              <span>{m.position}</span>
              <span className="team-accesses">
                {m.documents && <small className="badge">Документы</small>}
                {m.works && <small className="badge">Работы</small>}
              </span>
              <details>
                <summary>Контакт</summary>
                <p className="hint">{m.email}</p>
              </details>
            </li>
          ))}
        </ul>
        {manage && (
          <button type="button" className="button secondary" onClick={edit}>
            Настроить команду стройки
          </button>
        )}
      </>
    );
  }
  return (
    <section className="project-team" aria-label="Команда стройки">
      <h3>Команда этой стройки</h3>
      {error && (
        <Notice error>
          {error}{" "}
          <button
            type="button"
            className="text-button"
            onClick={() => setAttempt((a) => a + 1)}
          >
            Обновить команду
          </button>
        </Notice>
      )}
      {notice && <Notice>{notice}</Notice>}
      {renderTeam()}
    </section>
  );
}
