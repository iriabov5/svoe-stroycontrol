package ru.rshb.stroycontrol.organizations;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Хранит приглашения и стабильные ключи их создания. */
interface InvitationRepository extends JpaRepository<ContractorInvitation, UUID> {
    /**
     * @param createdBy автор
     * @param idempotencyKey ключ команды
     * @return прежняя команда либо пустой результат
     */
    Optional<ContractorInvitation> findByCreatedByAndIdempotencyKey(
            UUID createdBy, UUID idempotencyKey);

    /**
     * @param organizationId организация
     * @param recipientAccountId адресат
     * @param state состояние
     * @return открытое приглашение либо пустой результат
     */
    Optional<ContractorInvitation> findByOrganizationIdAndRecipientAccountIdAndState(
            UUID organizationId, UUID recipientAccountId, String state);

    /**
     * @param recipientAccountId адресат
     * @param state состояние
     * @return личные приглашения от новых к старым
     */
    List<ContractorInvitation> findByRecipientAccountIdAndStateOrderByCreatedAtDesc(
            UUID recipientAccountId, String state);

    /**
     * @param organizationId организация
     * @return приглашения руководителя от новых к старым
     */
    List<ContractorInvitation> findByOrganizationIdOrderByCreatedAtDesc(UUID organizationId);

    /** Адрес приглашения без загрузки изменяемой сущности в persistence context. */
    interface Address {
        /**
         * @return организация адресата
         */
        UUID getOrganizationId();

        /**
         * @return личный аккаунт адресата
         */
        UUID getRecipientAccountId();
    }

    /**
     * @param id приглашение
     * @return его адрес для выбора порядка блокировок
     */
    @Query(
"""
    select i.organizationId as organizationId,i.recipientAccountId as recipientAccountId
    from ContractorInvitation i
    where i.id=:id
""")
    Optional<Address> address(UUID id);
}
