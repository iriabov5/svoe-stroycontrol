package ru.rshb.stroycontrol.guidance;

import ru.rshb.stroycontrol.accounts.AccountRole;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Ответ навигатора из единого снимка; точки не являются разрешением или техническим заключением.
 *
 * @param projectId доступная карточка
 * @param computedAt время чтения
 * @param completedAt подтверждённое завершение либо null
 * @param groups три группы в стабильном порядке
 * @param primaryPointId персональный следующий шаг
 * @param actionablePointCount число разрешённых текущему участнику действий
 */
record GuidanceView(
        UUID projectId,
        Instant computedAt,
        Instant completedAt,
        List<Group> groups,
        String primaryPointId,
        int actionablePointCount) {
    /**
     * Группа точек всех этапов вида; пустой список не означает отсутствующий проект.
     *
     * @param key вид группы
     * @param title заголовок
     * @param points точки в порядке плана
     */
    record Group(String key, String title, List<Point> points) {}

    /**
     * Безопасная навигация к существующей секции; переход сам по себе ничего не записывает.
     *
     * @param projectId доступная карточка
     * @param stageId этап либо null
     * @param section секция существующего экрана
     * @param submissionId последний цикл либо null
     */
    record Target(UUID projectId, UUID stageId, String section, UUID submissionId) {}

    /**
     * Разрешённый следующий шаг, который повторно проверяет права при выполнении.
     *
     * @param code код перехода
     * @param label подпись действия
     */
    record Action(String code, String label) {}

    /**
     * Вычисленное состояние точки, не хранящееся отдельной строкой БД.
     *
     * @param id стабильная идентичность
     * @param stageId этап либо null у передачи дома
     * @param title заголовок
     * @param status состояние
     * @param reasonCode причина
     * @param detail пояснение
     * @param responsibleRole ответственная сторона либо null
     * @param canAct разрешён ли шаг текущему участнику
     * @param action действие либо null
     * @param target навигация чтения
     * @param acceptedStageCount принято этапов у передачи либо null
     * @param totalStageCount всего этапов у передачи либо null
     */
    record Point(
            String id,
            UUID stageId,
            String title,
            String status,
            String reasonCode,
            String detail,
            AccountRole responsibleRole,
            boolean canAct,
            Action action,
            Target target,
            Integer acceptedStageCount,
            Integer totalStageCount) {}
}
