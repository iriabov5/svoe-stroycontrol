package ru.rshb.stroycontrol.guidance;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Число доступных объектов с персональными действиями, а не число точек.
 *
 * @param computedAt момент чтения
 * @param projectCount число объектов
 * @param projects уникальные проекты в стабильном порядке
 */
record ConstructionActionsView(Instant computedAt, int projectCount, List<Item> projects) {
    /**
     * Безопасный переход к первому собственному действию проекта.
     *
     * @param projectId доступная карточка
     * @param projectName название
     * @param primaryPointId точка
     * @param target существующий экран
     */
    record Item(
            UUID projectId,
            String projectName,
            String primaryPointId,
            GuidanceView.Target target) {}
}
