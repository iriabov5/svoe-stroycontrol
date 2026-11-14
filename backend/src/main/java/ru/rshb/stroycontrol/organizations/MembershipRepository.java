package ru.rshb.stroycontrol.organizations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Хранит действующие и прежние членства без удаления истории. */
interface MembershipRepository extends JpaRepository<ContractorMembership, UUID> {
    /**
     * @param accountId аккаунт
     * @param state состояние
     * @return действующее членство либо пустой результат
     */
    Optional<ContractorMembership> findByAccountIdAndState(UUID accountId, String state);

    /**
     * @param organizationId организация
     * @param state состояние
     * @return собственные сотрудники по времени вступления
     */
    List<ContractorMembership> findByOrganizationIdAndStateOrderByCreatedAtAsc(
            UUID organizationId, String state);
}
