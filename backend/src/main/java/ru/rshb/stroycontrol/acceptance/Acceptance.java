package ru.rshb.stroycontrol.acceptance;

import java.util.Set;
import java.util.UUID;

/**
 * Публичная граница принятого результата и готовности проекта; сущности остаются внутри acceptance.
 */
public interface Acceptance {
    /**
     * Находит принятую сдачу после проверки доступа вызывающей банковской операцией.
     *
     * @param projectId доступный проект
     * @param stageId этап
     * @return принятая сдача либо ошибка состояния
     */
    SubmissionView accepted(UUID projectId, UUID stageId);

    /**
     * Возвращает ID принятых этапов для уже авторизованной сводки завершения.
     *
     * @param projectId проект
     * @return неизменяемое множество ID
     */
    Set<UUID> acceptedStages(UUID projectId);
}
