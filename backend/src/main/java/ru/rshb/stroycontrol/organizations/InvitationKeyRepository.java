package ru.rshb.stroycontrol.organizations;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Хранит все ключи приглашения, в том числе повторные команды с новым ключом к открытому
 * приглашению.
 */
interface InvitationKeyRepository extends JpaRepository<InvitationKey, UUID> {
    /**
     * @param actorId инициатор
     * @param idempotencyKey ключ
     * @return прежняя команда либо пустой результат
     */
    Optional<InvitationKey> findByActorIdAndIdempotencyKey(UUID actorId, UUID idempotencyKey);
}
