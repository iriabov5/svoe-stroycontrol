package ru.rshb.stroycontrol.acceptance;

import java.util.Collection;
import java.util.Map;
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

    /**
     * Читает только последний цикл каждого этапа авторизованного набора.
     *
     * @param projects проверенные карточки
     * @return циклы по идентификатору этапа
     */
    Map<UUID, Cycle> latestCycles(Set<UUID> projects);

    /**
     * Последний цикл без текстов и банковских сведений.
     *
     * @param id цикл
     * @param projectId карточка
     * @param stageId этап
     * @param number номер цикла
     * @param state OPEN, RETURNED или ACCEPTED
     */
    record Cycle(UUID id, UUID projectId, UUID stageId, int number, String state) {}

    /**
     * Итог приёмки, общий для навигатора и существующего завершения.
     *
     * @param total этапов в плане
     * @param accepted принятых этапов
     * @param ready все этапы приняты и открытых циклов нет
     */
    record Readiness(int total, int accepted, boolean ready) {
        /**
         * Вычисляет готовность по последним циклам, не меняя историю.
         *
         * @param stages идентификаторы полного плана
         * @param cycles последние циклы только данной карточки
         * @return единый итог приёмки
         */
        public static Readiness from(Set<UUID> stages, Collection<Cycle> cycles) {
            int count =
                    (int)
                            cycles.stream()
                                    .filter(c ->
                                            stages.contains(c.stageId())
                                                    && c.state().equals("ACCEPTED"))
                                    .count();
            return new Readiness(
                    stages.size(),
                    count,
                    !stages.isEmpty()
                            && count == stages.size()
                            && cycles.stream().noneMatch(c -> c.state().equals("OPEN")));
        }
    }
}
