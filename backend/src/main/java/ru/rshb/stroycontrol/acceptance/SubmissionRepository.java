package ru.rshb.stroycontrol.acceptance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Set;
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

    /**
     * Выбирает последние циклы одним запросом, а не по одному на этап.
     *
     * @param projects авторизованные карточки
     * @return только последний цикл каждого этапа
     */
    @Query(
            """
            select s from AcceptanceSubmission s where s.projectId in :projects
            and not exists(select n.id from AcceptanceSubmission n where n.projectId=s.projectId
            and n.stageId=s.stageId and n.cycleNumber>s.cycleNumber)
            """)
    List<AcceptanceSubmission> latest(Set<UUID> projects);
}
