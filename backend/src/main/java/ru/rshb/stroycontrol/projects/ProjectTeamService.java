package ru.rshb.stroycontrol.projects;

import static ru.rshb.stroycontrol.common.PreparationInput.keys;
import static ru.rshb.stroycontrol.common.PreparationInput.require;
import static ru.rshb.stroycontrol.common.PreparationInput.uuid;
import static ru.rshb.stroycontrol.common.PreparationInput.version;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.common.ActorSnapshot;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Управляет двумя доступами в конкретной стройке после проверки руководителя организации. */
@Service
@RequiredArgsConstructor
class ProjectTeamService {
    private static final String FIELD_MEMBERS = "members";
    private static final String FIELD_DOCUMENTS = "documents";
    private static final String FIELD_WORKS = "works";

    private final ProjectAuthorization authorization;

    /**
     * Участник одной стройки с понятной должностью и двумя независимыми доступами.
     *
     * @param id членство
     * @param organizationId организация
     * @param accountId личный аккаунт
     * @param displayName имя
     * @param email вторичный контакт
     * @param position должность
     * @param role OWNER либо MEMBER
     * @param state ACTIVE
     * @param version версия членства
     * @param documents доступ к документам
     * @param works доступ к выполнению работ
     */
    record Person(
            UUID id,
            UUID organizationId,
            UUID accountId,
            String displayName,
            String email,
            String position,
            String role,
            String state,
            long version,
            boolean documents,
            boolean works) {}

    /**
     * Версия и команда карточки.
     *
     * @param version версия полного состава
     * @param members только участники данной стройки
     */
    record Team(long version, List<Person> members) {}

    /**
     * Предпросмотр назначения, без адреса и телефона до подтверждения.
     *
     * @param organization краткие реквизиты
     * @param primaryContact основной контакт
     */
    record Preview(Map<String, Object> organization, ActorSnapshot primaryContact) {}

    /**
     * @param actor участник
     * @param id карточка
     * @return состав только своей стройки
     * @throws ApiException при отсутствии доступа
     */
    @Transactional(readOnly = true)
    Team get(AccountView actor, UUID id) {
        return view(authorization.project(id, actor.id(), false));
    }

    /**
     * Заменяет полный состав с проверкой версии под блокировкой организации и проекта.
     *
     * @param actor руководитель
     * @param id карточка
     * @param body версия и список действующих членств
     * @return обновлённая команда
     * @throws ApiException при неверном составе, отсутствии прав или устаревшей версии
     */
    @Transactional
    Team save(AccountView actor, UUID id, Map<String, Object> body) {
        var project = authorization.project(id, actor.id(), true);
        var access = authorization.access(project, actor.id());
        if (!access.canManageTeam()) throw new ApiException(ApiError.FORBIDDEN);
        keys(body, "version", FIELD_MEMBERS);
        if (project.getTeamVersion() != version(body.get("version")))
            throw new ApiException(ApiError.STALE_TEAM_VERSION);
        require(body.get(FIELD_MEMBERS) instanceof List<?>);
        var ids = new HashSet<UUID>();
        var next = new ArrayList<ProjectTeamMember>();
        for (var raw : (List<?>) body.get(FIELD_MEMBERS)) {
            require(raw instanceof Map<?, ?>);
            var item = (Map<?, ?>) raw;
            require(
                    item.keySet().stream()
                            .allMatch(k ->
                                    List.of("membershipId", FIELD_DOCUMENTS, FIELD_WORKS)
                                            .contains(k)));
            UUID memberId = uuid(item.get("membershipId"));
            require(ids.add(memberId));
            require(
                    item.get(FIELD_DOCUMENTS) instanceof Boolean
                            && item.get(FIELD_WORKS) instanceof Boolean);
            boolean docs = (Boolean) item.get(FIELD_DOCUMENTS);
            boolean works = (Boolean) item.get(FIELD_WORKS);
            require(docs || works);
            var member =
                    authorization.organizations.member(
                            project.getContractorOrganizationId(), memberId);
            if (member == null || !member.role().equals("MEMBER"))
                throw new ApiException(ApiError.INVALID_TEAM_MEMBER);
            next.add(
                    ProjectTeamMember.builder()
                            .projectId(id)
                            .membershipId(memberId)
                            .documents(docs)
                            .works(works)
                            .build());
        }
        authorization.team.deleteAll(authorization.team.findByProjectId(id));
        authorization.team.flush();
        authorization.team.saveAll(next);
        project.setTeamVersion(project.getTeamVersion() + 1);
        authorization.repository.flush();
        return view(project);
    }

    /**
     * @param actor прежний основной контакт
     * @param id карточка
     * @param body собственная организация
     * @return карточка с командной привязкой
     * @throws ApiException при попытке заменить подрядчика
     */
    @Transactional
    ProjectView connect(AccountView actor, UUID id, Map<String, Object> body) {
        keys(body, "organizationId");
        var org = authorization.organizations.assignable(actor.id());
        if (!org.id().equals(uuid(body.get("organizationId"))))
            throw new ApiException(ApiError.FORBIDDEN);
        authorization.organizations.lock(org.id());
        var project = authorization.project(id, actor.id(), true);
        if (!actor.id().equals(project.getContractorId()))
            throw new ApiException(ApiError.FORBIDDEN);
        if (project.getContractorOrganizationId() != null
                && !project.getContractorOrganizationId().equals(org.id()))
            throw new ApiException(ApiError.CONTRACTOR_ALREADY_ASSIGNED);
        project.setContractorOrganizationId(org.id());
        authorization.repository.flush();
        return authorization.view(project, actor.id());
    }

    /**
     * @param actor владелец
     * @param id своя карточка
     * @param email основной контакт
     * @return название, ИНН и имя без закрытых реквизитов
     */
    @Transactional(readOnly = true)
    Preview preview(AccountView actor, UUID id, String email) {
        var project = authorization.project(id, actor.id(), false);
        if (!project.getCustomerId().equals(actor.id())) throw new ApiException(ApiError.FORBIDDEN);
        var person =
                authorization
                        .accounts
                        .findContractor(email)
                        .orElseThrow(() -> new ApiException(ApiError.CONTRACTOR_NOT_FOUND));
        var org = authorization.organizations.assignable(person.id());
        return new Preview(
                Map.of("id", org.id(), "displayName", org.displayName(), "inn", org.inn()),
                authorization.organizations.snapshot(person.id()));
    }

    private Team view(HouseProject project) {
        if (project.getContractorOrganizationId() == null)
            return new Team(project.getTeamVersion(), List.of());
        var people = new ArrayList<Person>();
        var grants = authorization.team.findByProjectId(project.getId());
        for (var member :
                authorization.organizations.members(project.getContractorOrganizationId())) {
            var grant =
                    grants.stream()
                            .filter(g -> g.getMembershipId().equals(member.id()))
                            .findFirst();
            boolean owner = member.role().equals("OWNER");
            if (owner || grant.isPresent())
                people.add(
                        new Person(
                                member.id(),
                                member.organizationId(),
                                member.accountId(),
                                member.displayName(),
                                member.email(),
                                member.position(),
                                member.role(),
                                member.state(),
                                member.version(),
                                owner || grant.get().isDocuments(),
                                owner || grant.get().isWorks()));
        }
        return new Team(project.getTeamVersion(), people);
    }
}
