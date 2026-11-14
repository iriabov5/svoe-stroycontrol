package ru.rshb.stroycontrol.projects;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Component;

import ru.rshb.stroycontrol.accounts.AccountRole;
import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.accounts.Accounts;
import ru.rshb.stroycontrol.common.ActorSnapshot;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.organizations.Organizations;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Единая проверка карточки и проектных прав внутри projects, общая для заявок и публичного
 * Projects.
 *
 * <p>При записи блокирует организацию до проекта; команда и членство читаются после блокировки.
 */
@Component
@RequiredArgsConstructor
class ProjectAuthorization {
    final ProjectRepository repository;
    final Accounts accounts;
    final Organizations organizations;
    final ProjectTeamRepository team;

    /**
     * @param id карточка
     * @param actor личный аккаунт
     * @param lock нужна общая блокировка записи
     * @return доступная карточка
     * @throws ApiException с PROJECT_NOT_FOUND для чужого объекта либо STALE_TEAM_VERSION при смене
     *     привязки
     */
    HouseProject project(UUID id, UUID actor, boolean lock) {
        HouseProject project;
        if (lock) {
            var routing = repository.routing(id);
            if (routing.isEmpty()) throw new ApiException(ApiError.PROJECT_NOT_FOUND);
            else {
                var organization = routing.get().getOrganizationId();
                if (organization != null) organizations.lock(organization);
                project =
                        repository
                                .lockById(id)
                                .orElseThrow(() -> new ApiException(ApiError.PROJECT_NOT_FOUND));
                if (!Objects.equals(organization, project.getContractorOrganizationId()))
                    throw new ApiException(ApiError.STALE_TEAM_VERSION);
            }
        } else
            project =
                    repository
                            .findById(id)
                            .orElseThrow(() -> new ApiException(ApiError.PROJECT_NOT_FOUND));
        access(project, actor);
        return project;
    }

    /**
     * Определяет актуальные прикладные полномочия участника конкретной стройки.
     *
     * @param project карточка
     * @param actor аккаунт
     * @return текущие возможности участника
     * @throws ApiException с PROJECT_NOT_FOUND для неучастника
     */
    ProjectAccess access(HouseProject project, UUID actor) {
        boolean owner = project.getCustomerId().equals(actor);
        boolean contractor = false;
        boolean documents = false;
        boolean works = false;
        boolean manage = false;
        if (!owner && (project.isRequestSubmitted() || "LEGACY".equals(project.getProcessMode()))) {
            if (project.getContractorOrganizationId() == null) {
                contractor = actor.equals(project.getContractorId());
                documents = contractor;
                works = contractor;
                manage = false;
            } else {
                var membership = organizations.membership(actor);
                if (membership != null
                        && membership
                                .organizationId()
                                .equals(project.getContractorOrganizationId())) {
                    manage = membership.role().equals("OWNER");
                    var grant =
                            team.findByProjectIdAndMembershipId(project.getId(), membership.id());
                    contractor = manage || grant.isPresent();
                    documents = manage || grant.map(ProjectTeamMember::isDocuments).orElse(false);
                    works = manage || grant.map(ProjectTeamMember::isWorks).orElse(false);
                }
            }
        }
        if (!owner && !contractor) throw new ApiException(ApiError.PROJECT_NOT_FOUND);
        return new ProjectAccess(
                project.getId(),
                owner,
                contractor,
                project.getProcessMode(),
                project.getApprovedProposalId(),
                project.getApprovedContractId(),
                project.isPreparationClosed(),
                documents,
                works,
                works,
                manage,
                project.getContractorOrganizationId());
    }

    /**
     * Собирает безопасное представление карточки после проверки участия.
     *
     * @param project карточка
     * @param actor участник
     * @return общая модель с актуальными подписями и возможностями
     */
    ProjectView view(HouseProject project, UUID actor) {
        var a = access(project, actor);
        return new ProjectView(
                project.getId(),
                project.getName(),
                project.getAddress(),
                project.getCustomerId(),
                project.getContractorId(),
                project.getCreatedAt(),
                project.getProcessMode(),
                project.isRequestSubmitted(),
                project.getApprovedProposalId(),
                project.getApprovedContractId(),
                project.isPreparationClosed(),
                project.getContractorOrganizationId(),
                project.getContractorOrganizationId() == null
                        ? null
                        : organizations.profile(project.getContractorOrganizationId()),
                presentation(project.getCustomerId()),
                project.getContractorId() == null ? null : presentation(project.getContractorId()),
                new ProjectView.Permissions(
                        a.canManageTeam(),
                        a.canPrepareDocuments(),
                        a.canPublishJournal(),
                        a.canRecordWork(),
                        a.owner(),
                        a.owner()),
                project.getCompletedAt());
    }

    /**
     * Возвращает только карточки, доступные аккаунту по актуальному участию.
     *
     * @param actor личный аккаунт
     * @return лишь видимые ему карточки, включая явную команду
     */
    List<ProjectView> list(AccountView actor) {
        var all =
                new ArrayList<>(
                        actor.role() == AccountRole.CUSTOMER
                                ? repository.findByCustomerIdOrderByCreatedAtDescIdDesc(actor.id())
                                : repository.findByContractorIdOrderByCreatedAtDescIdDesc(
                                        actor.id()));
        if (actor.role() == AccountRole.CONTRACTOR) {
            var m = organizations.membership(actor.id());
            if (m != null)
                for (var grant : team.findByMembershipId(m.id()))
                    repository
                            .findById(grant.getProjectId())
                            .ifPresent(p -> {
                                if (all.stream().noneMatch(old -> old.getId().equals(p.getId())))
                                    all.add(p);
                            });
        }
        return all.stream()
                .filter(p -> {
                    try {
                        access(p, actor.id());
                        return true;
                    } catch (ApiException _) {
                        return false;
                    }
                })
                .sorted(
                        Comparator.comparing(HouseProject::getCreatedAt)
                                .thenComparing(p -> p.getId().toString())
                                .reversed())
                .map(p -> view(p, actor.id()))
                .toList();
    }

    private ActorSnapshot presentation(UUID id) {
        if (accounts.profile(id) == null) return null;
        var snapshot = organizations.snapshot(id);
        return snapshot == null
                ? null
                : new ActorSnapshot(
                        snapshot.accountId(),
                        snapshot.displayName(),
                        snapshot.globalRole(),
                        snapshot.organizationId(),
                        snapshot.organizationName(),
                        snapshot.position(),
                        null,
                        false);
    }
}
