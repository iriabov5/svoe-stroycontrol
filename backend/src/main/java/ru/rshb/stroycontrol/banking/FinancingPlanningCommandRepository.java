package ru.rshb.stroycontrol.banking;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/** Читает результат ключа только в области владельца и карточки. */
interface FinancingPlanningCommandRepository extends JpaRepository<FinancingPlanningCommand, UUID> {
    /**
     * Находит успешно зафиксированный повтор.
     *
     * @param projectId карточка
     * @param ownerId владелец
     * @param idempotencyKey ключ команды
     * @return исходный ответ либо пустое значение
     */
    Optional<FinancingPlanningCommand> findByProjectIdAndOwnerIdAndIdempotencyKey(
            UUID projectId, UUID ownerId, UUID idempotencyKey);
}
