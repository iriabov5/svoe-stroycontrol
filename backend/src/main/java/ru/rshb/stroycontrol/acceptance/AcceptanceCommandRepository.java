package ru.rshb.stroycontrol.acceptance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Хранит ключи приёмки внутри actor/project/operation. */
interface AcceptanceCommandRepository extends JpaRepository<AcceptanceCommand, UUID> {
    /**
     * Ищет исходный результат команды.
     *
     * @param actorId аккаунт
     * @param projectId проект
     * @param operation операция со stage/submission ID
     * @param idempotencyKey ключ
     * @return прежний результат
     */
    Optional<AcceptanceCommand> findByActorIdAndProjectIdAndOperationAndIdempotencyKey(
            UUID actorId, UUID projectId, String operation, UUID idempotencyKey);
}
