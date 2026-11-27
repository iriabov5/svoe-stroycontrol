package ru.rshb.stroycontrol.projects;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.accounts.AccountRole;
import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Читает источники паспорта только после авторизации, не отдавая заявку другому модулю. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY, readOnly = true)
class ConstructionContextService implements ConstructionContexts {
    private final ProjectAuthorization authorization;
    private final RequestRepository requests;
    private final ConstructionProfileRepository profiles;

    /** {@inheritDoc} */
    @Override
    public Context get(UUID project, UUID actor) {
        var p = authorization.project(project, actor, false);
        return contexts(List.of(p), actor).getFirst();
    }

    /** {@inheritDoc} */
    @Override
    public List<Context> all(AccountView actor) {
        var repository = authorization.repository;
        var candidates = new HashMap<UUID, HouseProject>();
        var direct =
                actor.role() == AccountRole.CUSTOMER
                        ? repository.findByCustomerIdOrderByCreatedAtDescIdDesc(actor.id())
                        : repository.findByContractorIdOrderByCreatedAtDescIdDesc(actor.id());
        direct.forEach(p -> candidates.put(p.getId(), p));
        if (actor.role() == AccountRole.CONTRACTOR) {
            var member = authorization.organizations.membership(actor.id());
            if (member != null) {
                var extra =
                        member.role().equals("OWNER")
                                ? repository.findByContractorOrganizationId(member.organizationId())
                                : repository.findAllById(
                                        authorization.team.findByMembershipId(member.id()).stream()
                                                .map(ProjectTeamMember::getProjectId)
                                                .toList());
                extra.forEach(p -> candidates.put(p.getId(), p));
            }
        }
        var allowed = new ArrayList<HouseProject>();
        for (var p : candidates.values()) {
            try {
                authorization.access(p, actor.id());
                allowed.add(p);
            } catch (ApiException e) {
                if (e.getError() != ApiError.PROJECT_NOT_FOUND) throw e;
            }
        }
        return contexts(allowed, actor.id());
    }

    /**
     * Читает зависимые таблицы наборами; вызывается только для проверенного набора.
     *
     * @param projects авторизованные карточки
     * @param actor участник
     * @return безопасные проекции
     */
    private List<Context> contexts(List<HouseProject> projects, UUID actor) {
        if (projects.isEmpty()) return List.of();
        Set<UUID> ids = projects.stream().map(HouseProject::getId).collect(Collectors.toSet());
        Map<UUID, ConstructionRequest> rs =
                requests.findByProjectIdIn(ids).stream()
                        .collect(
                                Collectors.toMap(
                                        ConstructionRequest::getProjectId, Function.identity()));
        Map<UUID, ConstructionProfileData> ps =
                profiles.findAllById(ids).stream()
                        .collect(
                                Collectors.toMap(
                                        ConstructionProfileData::getProjectId,
                                        Function.identity()));
        return projects.stream()
                .map(p -> context(p, actor, rs.get(p.getId()), ps.get(p.getId())))
                .toList();
    }

    /**
     * Собирает одну авторизованную карточку; неизвестные характеристики не подменяет значениями.
     */
    private Context context(
            HouseProject project,
            UUID actor,
            ConstructionRequest request,
            ConstructionProfileData profile) {
        return new Context(
                project.getId(),
                project.getName(),
                authorization.access(project, actor),
                project.getCompletedAt(),
                request == null ? null : request.getAreaM2(),
                request == null ? null : request.getFloors(),
                request == null ? null : request.getDesiredFinish(),
                requestSource(request),
                profile == null ? null : profile.getWallMaterial(),
                profile == null ? null : profile.getFoundationType(),
                profile == null ? null : profile.getRoofType());
    }

    /** Различает отправленную заявку и изменяемые пожелания; без заявки источник отсутствует. */
    private String requestSource(ConstructionRequest request) {
        if (request == null) return null;
        return "SUBMITTED".equals(request.getState()) ? "REQUEST_SUBMITTED" : "REQUEST_DRAFT";
    }
}
