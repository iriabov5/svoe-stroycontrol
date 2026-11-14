package ru.rshb.stroycontrol.acceptance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/** Хранит циклы и выбирает последние решения; запись сериализуется проектом. */
interface SubmissionRepository extends JpaRepository<AcceptanceSubmission, UUID> {
    /**
     * Возвращает историю этапа от последнего цикла к первому.
     *
     * @param projectId проект
     * @param stageId этап
     * @return полная история
     */
    List<AcceptanceSubmission> findByProjectIdAndStageIdOrderByCycleNumberDesc(
            UUID projectId, UUID stageId);

    /**
     * Выбирает принятые этапы для сводки завершения.
     *
     * @param projectId проект
     * @param state ACCEPTED
     * @return принятые циклы
     */
    List<AcceptanceSubmission> findByProjectIdAndState(UUID projectId, String state);
}
