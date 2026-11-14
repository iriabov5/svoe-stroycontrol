package ru.rshb.stroycontrol.projects;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import ru.rshb.stroycontrol.accounts.AccountRole;
import ru.rshb.stroycontrol.accounts.AccountView;
import ru.rshb.stroycontrol.common.ApiError;
import ru.rshb.stroycontrol.common.ApiException;
import ru.rshb.stroycontrol.common.Input;

import java.util.List;
import java.util.UUID;

/** Управляет карточками и публичной транзакционной границей доступа других модулей. */
@Service
@RequiredArgsConstructor
class ProjectService implements Projects {
    private final ProjectAuthorization authorization;
    private final RequestService requests;

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public void requireOpen(UUID projectId, UUID actorId) {
        if (authorization.project(projectId, actorId, false).getCompletedAt() != null)
            throw new ApiException(ApiError.CONSTRUCTION_COMPLETED);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void touchCompletion(UUID projectId, UUID actorId) {
        var p = authorization.project(projectId, actorId, true);
        p.setCompletionVersion(p.getCompletionVersion() + 1);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Info info(UUID projectId, UUID actorId) {
        var p = authorization.project(projectId, actorId, false);
        return new Info(p.getId(), p.getName(), p.getAddress(), requests.financing(projectId));
    }

    /**
     * Создаёт карточку дома и её исходную заявку одной транзакцией владельца.
     *
     * @param actor заказчик
     * @param name название
     * @param address участок
     * @return созданная карточка с черновиком заявки
     * @throws ApiException при неверной роли или полях
     */
    @Transactional
    ProjectView create(AccountView actor, String name, String address) {
        if (actor.role() != AccountRole.CUSTOMER) throw new ApiException(ApiError.FORBIDDEN);
        var p =
                authorization.repository.saveAndFlush(
                        HouseProject.builder()
                                .name(Input.text(name, 120))
                                .address(Input.text(address, 300))
                                .customerId(actor.id())
                                .build());
        requests.create(p.getId(), p.getAddress());
        return authorization.view(p, actor.id());
    }

    /**
     * @param actor участник
     * @return только доступные карточки
     */
    @Transactional(readOnly = true)
    List<ProjectView> list(AccountView actor) {
        return authorization.list(actor);
    }

    /**
     * @param actor участник
     * @param id карточка
     * @return актуальная модель
     * @throws ApiException с PROJECT_NOT_FOUND при отсутствии доступа
     */
    @Transactional(readOnly = true)
    ProjectView get(AccountView actor, UUID id) {
        return authorization.view(authorization.project(id, actor.id(), false), actor.id());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public ProjectAccess requireParticipant(UUID projectId, UUID accountId) {
        return authorization.access(authorization.project(projectId, accountId, false), accountId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public ProjectAccess lockParticipant(UUID projectId, UUID accountId) {
        return authorization.access(authorization.project(projectId, accountId, true), accountId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void approveProposal(UUID projectId, UUID actorId, UUID proposalId) {
        var p = authorization.project(projectId, actorId, true);
        owner(p, actorId);
        p.approveProposal(proposalId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void approveContract(UUID projectId, UUID actorId, UUID contractId) {
        var p = authorization.project(projectId, actorId, true);
        owner(p, actorId);
        p.approveContract(contractId);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void closePreparation(UUID projectId, UUID actorId) {
        var p = authorization.project(projectId, actorId, true);
        owner(p, actorId);
        if (!"PROPOSAL_REQUIRED".equals(p.getProcessMode()) || p.getApprovedProposalId() != null)
            throw new ApiException(ApiError.INVALID_PROPOSAL_STATE);
        p.closePreparation();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public UUID submittedRequest(UUID projectId, UUID actorId) {
        authorization.project(projectId, actorId, false);
        return requests.submittedRequest(projectId);
    }

    /**
     * Назначает заполненную организацию по основному контакту, сериализуя назначение.
     *
     * @param actor владелец
     * @param id карточка
     * @param email email руководителя
     * @return карточка с реквизитами
     * @throws ApiException при другом назначенном подрядчике или неполном профиле
     */
    @Transactional
    ProjectView assign(AccountView actor, UUID id, String email) {
        var route = authorization.repository.routing(id);
        if (route.isEmpty()) throw new ApiException(ApiError.PROJECT_NOT_FOUND);
        if (!route.get().getCustomerId().equals(actor.id())) {
            authorization.project(id, actor.id(), false);
            throw new ApiException(ApiError.FORBIDDEN);
        }
        var person =
                authorization
                        .accounts
                        .findContractor(email)
                        .orElseThrow(() -> new ApiException(ApiError.CONTRACTOR_NOT_FOUND));
        if (route.get().getContractorId() != null
                && !route.get().getContractorId().equals(person.id()))
            throw new ApiException(ApiError.CONTRACTOR_ALREADY_ASSIGNED);
        var org = authorization.organizations.assignable(person.id());
        authorization.organizations.lock(org.id());
        var p = authorization.project(id, actor.id(), true);
        owner(p, actor.id());
        p.assign(person.id());
        p.setContractorOrganizationId(org.id());
        authorization.repository.flush();
        return authorization.view(p, actor.id());
    }

    private void owner(HouseProject p, UUID actor) {
        if (!p.getCustomerId().equals(actor)) throw new ApiException(ApiError.FORBIDDEN);
    }
}
